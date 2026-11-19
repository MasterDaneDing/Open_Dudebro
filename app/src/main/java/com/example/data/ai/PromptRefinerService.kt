package com.example.data.ai

import android.util.Log
import com.example.model.BotExpression
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.RoomMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ParsedInstructionResult(
  val goal: String,
  val clearInstruction: String,
  val steps: List<String>,
  val recommendedExpression: BotExpression,
  val needsHostTakeover: Boolean,
  val friendlyBotReply: String,
)

data class ChatRoomTurn(
  val botName: String,
  val replyToBotName: String?,
  val message: String,
  val expression: BotExpression,
  val parsedInstruction: String?,
)

data class ChatRoomRoundResult(
  val turns: List<ChatRoomTurn>,
)

class PromptRefinerService {
  private val instructionSchema = Schema.obj(
    properties = mapOf(
      "goal" to Schema.string(description = "A concise 1-line summary of the user's goal."),
      "clearInstruction" to Schema.string(
        description = "Unambiguous, formal, execution-ready instruction rewritten for the OpenClaw autonomous host agent."
      ),
      "steps" to Schema.array(
        items = Schema.string(),
        description = "Ordered list of 2 to 6 concrete execution steps for OpenClaw.",
      ),
      "recommendedExpression" to Schema.enumeration(
        values = listOf("UH_HUH", "WORRIED", "SORRY", "ALERT", "WORKING"),
        description = "Bot facial expression matching clarity/risk: UH_HUH if clear solution, WORRIED if risky/ambiguous/ongoing issue, ALERT if desktop takeover needed.",
      ),
      "needsHostTakeover" to Schema.boolean(
        description = "True if the task likely needs human screen/mouse takeover on the host computer (e.g. login prompt, 2FA, visual UI click)."
      ),
      "friendlyBotReply" to Schema.string(
        description = "Short warm response in the bot's cute personality confirming how it translated the casual request."
      ),
    ),
  )

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = instructionSchema
      },
    )
  }

  private val roomRoundSchema = Schema.obj(
    properties = mapOf(
      "turns" to Schema.array(
        items = Schema.obj(
          properties = mapOf(
            "botName" to Schema.string(description = "Exact display name of the speaking bot from the participant list."),
            "replyToBotName" to Schema.string(
              description = "Name of the bot being replied to/critiqued/addressed, or empty string if addressing the user.",
              nullable = true,
            ),
            "message" to Schema.string(
              description = "The bot's substantive reply performing its mission (e.g., translating, critiquing, refining, or suggesting OpenClaw steps)."
            ),
            "expression" to Schema.enumeration(
              values = listOf("HAPPY", "THINKING", "UH_HUH", "WORRIED", "SORRY", "WORKING"),
              description = "Facial expression while speaking this turn.",
            ),
            "parsedInstruction" to Schema.string(
              description = "Optional concrete OpenClaw instruction if this bot produced an actionable host task.",
              nullable = true,
            ),
          ),
          optionalProperties = listOf("replyToBotName", "parsedInstruction"),
        ),
        description = "2 to 4 sequential conversational turns where the participating bots collaborate, translate, critique each other, and converge on a strong answer.",
      )
    )
  )

  private val roomModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = roomRoundSchema
      },
    )
  }

  suspend fun parseCasualRequest(
    bot: CompanionBot,
    casualMessage: String,
    attachedFiles: List<String>,
    linkedPaths: List<String>,
  ): Result<ParsedInstructionResult> {
    return try {
      val prompt = buildString {
        appendLine("You are ${bot.name} (${bot.roleTitle}), a cute companion front-end for OpenClaw.")
        appendLine("Bot mission: ${bot.displayMission}")
        appendLine("Bot persona: ${bot.systemPersona}")
        appendLine("Target OpenClaw agent ID: ${bot.openClawAgentId}")
        appendLine()
        appendLine("Task: Translate the user's casual chat-like message into a crisp, unambiguous, actionable instruction block for the OpenClaw agent running on the user's host computer.")
        if (attachedFiles.isNotEmpty()) {
          appendLine("Attached device files: ${attachedFiles.joinToString(", ")}")
        }
        if (linkedPaths.isNotEmpty()) {
          appendLine("Linked host paths/URLs: ${linkedPaths.joinToString(", ")}")
        }
        appendLine()
        appendLine("User's casual message:")
        appendLine(casualMessage)
      }

      val response = withTimeout(60_000L) {
        model.generateContent(prompt)
      }
      val jsonText = response.text?.takeIf { it.isNotBlank() }
        ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

      val json = JSONObject(jsonText)
      val stepsArray = json.optJSONArray("steps")
      val steps = buildList {
        if (stepsArray != null) {
          for (i in 0 until stepsArray.length()) {
            val item = stepsArray.optString(i).trim()
            if (item.isNotEmpty()) add(item)
          }
        }
      }
      val expression = BotExpression.safeValueOf(json.optString("recommendedExpression", "UH_HUH"))

      Result.success(
        ParsedInstructionResult(
          goal = json.optString("goal", "Execute OpenClaw task").take(200),
          clearInstruction = json.optString("clearInstruction", casualMessage).take(4000),
          steps = steps.take(12),
          recommendedExpression = expression,
          needsHostTakeover = json.optBoolean("needsHostTakeover", false),
          friendlyBotReply = json.optString("friendlyBotReply", "Uh-huh! I turned that into a clear plan for OpenClaw!"),
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Prompt refinement timed out", e)
      Result.failure(IllegalStateException("Prompt parsing timed out after 60s. Please retry.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Prompt refinement failed", e)
      Result.failure(e)
    }
  }

  suspend fun runChatRoomTurn(
    room: ChatRoom,
    participants: List<CompanionBot>,
    recentHistory: List<RoomMessage>,
    userPrompt: String,
  ): Result<ChatRoomRoundResult> {
    if (participants.isEmpty()) {
      return Result.failure(IllegalStateException("Add at least one bot to this chat room first."))
    }
    return try {
      val prompt = buildString {
        appendLine("You are orchestrating a lively multi-bot collaborative cafe chat room named '${room.title}'.")
        if (room.topic.isNotBlank()) {
          appendLine("Room topic: ${room.topic}")
        }
        appendLine()
        appendLine("Participating bots (stick strictly to each bot's mission and persona):")
        participants.forEach { bot ->
          appendLine("- ${bot.name} | Mission: \"${bot.displayMission}\" | Role: ${bot.roleTitle} | Persona: ${bot.systemPersona}")
        }
        appendLine()
        if (recentHistory.isNotEmpty()) {
          appendLine("Recent chat room history:")
          recentHistory.takeLast(10).forEach { msg ->
            val target = msg.replyToBotName?.takeIf { it.isNotBlank() }?.let { " -> @$it" } ?: ""
            appendLine("[${msg.senderBotName}$target]: ${msg.rawText}")
          }
          appendLine()
        }
        appendLine("User's latest message / trigger:")
        appendLine(userPrompt)
        appendLine()
        appendLine("Instructions:")
        appendLine("1. Produce 2 to 4 sequential bot turns where the bots visibly interact with EACH OTHER according to their missions (e.g., one bot drafts/translates, another critiques accuracy/nuance addressing the first bot via replyToBotName, and optionally another refines or summarizes).")
        appendLine("2. Use the exact botName from the participant list above.")
        appendLine("3. Keep each turn focused, helpful, and distinctly voiced.")
      }

      val response = withTimeout(60_000L) {
        roomModel.generateContent(prompt)
      }
      val jsonText = response.text?.takeIf { it.isNotBlank() }
        ?: return Result.failure(IllegalStateException("Gemini returned an empty room response."))

      val json = JSONObject(jsonText)
      val turnsArr = json.optJSONArray("turns")
      val turns = buildList {
        if (turnsArr != null) {
          for (i in 0 until turnsArr.length()) {
            val item = turnsArr.optJSONObject(i) ?: continue
            val botName = item.optString("botName").trim()
            val text = item.optString("message").trim()
            if (botName.isNotEmpty() && text.isNotEmpty()) {
              add(
                ChatRoomTurn(
                  botName = botName,
                  replyToBotName = item.optString("replyToBotName").trim().takeIf { it.isNotEmpty() && it != "null" },
                  message = text.take(4000),
                  expression = BotExpression.safeValueOf(item.optString("expression", "UH_HUH")),
                  parsedInstruction = item.optString("parsedInstruction").trim().takeIf { it.isNotEmpty() && it != "null" },
                )
              )
            }
          }
        }
      }
      if (turns.isEmpty()) {
        return Result.failure(IllegalStateException("No bot replies were produced for this turn."))
      }
      Result.success(ChatRoomRoundResult(turns = turns))
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Chat room turn timed out", e)
      Result.failure(IllegalStateException("Chat room round timed out after 60s. Please try again.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Chat room turn failed", e)
      Result.failure(e)
    }
  }

  private companion object {
    const val TAG = "PromptRefinerService"
  }
}
