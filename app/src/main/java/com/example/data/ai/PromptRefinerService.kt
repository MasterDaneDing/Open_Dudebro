package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.BotExpression
import com.example.model.BotFunctionPreset
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.RoomMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
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

data class TranslatedBotProfileResult(
  val inferredFunction: BotFunctionPreset,
  val suggestedAgentId: String,
  val translatedInstructions: String,
  val summary: String,
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

  private val visionChatModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
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

  private val botProfileSchema = Schema.obj(
    properties = mapOf(
      "functionPreset" to Schema.enumeration(
        values = BotFunctionPreset.entries.map { it.name },
        description = "Best matching OpenClaw functional role inferred from the bot's casual description/mission (e.g., 'I translate stuff!' -> TRANSLATION).",
      ),
      "suggestedAgentId" to Schema.string(
        description = "Short URL-safe lowercase OpenClaw agent ID slug (e.g., translator, proofreader, critic, coder, researcher, main)."
      ),
      "translatedInstructions" to Schema.string(
        description = "Clear, explicit, execution-ready system instructions for OpenClaw (under 460 characters) translating the user's casual description into exact operational behavior."
      ),
      "summary" to Schema.string(
        description = "One-line confirmation describing the translated OpenClaw role."
      ),
    ),
  )

  private val botProfileModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = botProfileSchema
      },
    )
  }

  suspend fun parseCasualRequest(
    bot: CompanionBot,
    casualMessage: String,
    attachedFiles: List<String>,
    linkedPaths: List<String>,
    imageBitmaps: List<Bitmap> = emptyList(),
  ): Result<ParsedInstructionResult> {
    return try {
      val prompt = buildString {
        appendLine("You are ${bot.name} (${bot.roleTitle}), a cute companion front-end for OpenClaw.")
        appendLine("Bot function preset: ${bot.functionPresetEnum.displayName} — ${bot.functionPresetEnum.openClawDirective}")
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
        if (imageBitmaps.isNotEmpty()) {
          appendLine("The user attached ${imageBitmaps.size} image(s) for visual inspection. Incorporate what you observe in the image(s) into clearInstruction and friendlyBotReply.")
        }
        appendLine()
        appendLine("User's casual message:")
        appendLine(casualMessage)
      }

      val requestContent = content {
        imageBitmaps.take(4).forEach { bmp -> image(bmp) }
        text(prompt)
      }

      val response = withTimeout(60_000L) {
        model.generateContent(requestContent)
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

  /**
   * Streams a direct multimodal response from Gemini 3.8 Flash (`Firebase.ai`) accepting text,
   * conversation history, optional document/system context, and up to 4 attached images/pages.
   */
  suspend fun streamGeminiChat(
    userPrompt: String,
    history: List<Pair<String, String>> = emptyList(),
    imageBitmaps: List<Bitmap> = emptyList(),
    systemDirective: String? = null,
    onPartialText: (String) -> Unit = {},
  ): Result<String> {
    return try {
      val fullPrompt = buildString {
        if (!systemDirective.isNullOrBlank()) {
          appendLine(systemDirective.trim())
          appendLine()
        }
        if (history.isNotEmpty()) {
          appendLine("Recent conversation:")
          history.takeLast(10).forEach { (role, text) ->
            if (text.isNotBlank()) {
              appendLine("[$role]: ${text.take(2000)}")
            }
          }
          appendLine()
        }
        if (imageBitmaps.isNotEmpty()) {
          appendLine("[${imageBitmaps.size} image(s) attached — inspect visual details carefully.]")
        }
        append(userPrompt.trim())
      }

      val requestContent = content(role = "user") {
        imageBitmaps.take(4).forEach { bmp -> image(bmp) }
        text(fullPrompt)
      }

      val sb = StringBuilder()
      withTimeout(90_000L) {
        visionChatModel.generateContentStream(requestContent).collect { chunk ->
          val piece = chunk.text.orEmpty()
          if (piece.isNotEmpty()) {
            sb.append(piece)
            onPartialText(sb.toString())
          }
        }
      }
      val finalText = sb.toString().trim()
      if (finalText.isBlank()) {
        Result.failure(IllegalStateException("Gemini 3.8 Flash returned an empty response."))
      } else {
        Result.success(finalText)
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Gemini 3.8 Flash stream timed out", e)
      Result.failure(IllegalStateException("Gemini 3.8 Flash timed out after 90s. Please retry.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini 3.8 Flash multimodal chat failed", e)
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
          appendLine("- ${bot.name} | Function: ${bot.functionPresetEnum.displayName} | Mission: \"${bot.displayMission}\" | Role: ${bot.roleTitle} | Persona: ${bot.systemPersona}")
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

  /**
   * Translates a user's casual bot description/mission (e.g. "I translate stuff!") into
   * an explicit OpenClaw function preset + clear execution-ready OpenClaw instructions.
   */
  suspend fun compileBotProfileForOpenClaw(bot: CompanionBot): TranslatedBotProfileResult {
    val missionText = bot.displayMission.trim()
    val heuristic = heuristicBotProfile(bot, missionText)
    if (missionText.isBlank()) return heuristic

    return try {
      val prompt = buildString {
        appendLine("Translate this casual companion bot description into structured OpenClaw agent instructions.")
        appendLine("Bot Name: ${bot.name}")
        appendLine("User's Casual Description / Mission: \"$missionText\"")
        appendLine("Current Selected Function Preset: ${bot.functionPresetEnum.name} (${bot.functionPresetEnum.displayName})")
        appendLine("Current Role Title: ${bot.roleTitle}")
        if (bot.systemPersona.isNotBlank()) {
          appendLine("Existing Persona/Notes: ${bot.systemPersona}")
        }
        appendLine()
        appendLine("Rules:")
        appendLine("1. Infer the best BotFunctionPreset (GENERAL, TRANSLATION, PROOFREADING, CRITIQUING, CODING, RESEARCH) from the user's mission/description. For example, 'I translate stuff!' -> TRANSLATION.")
        appendLine("2. Write clear, actionable OpenClaw instructions (<= 450 characters) telling OpenClaw exactly what this agent does, how to handle inputs/files, and how to format output.")
        appendLine("3. Provide a clean lowercase suggestedAgentId slug matching the role (e.g. translator, proofreader, critic, coder, researcher, main).")
      }

      val response = withTimeout(25_000L) {
        botProfileModel.generateContent(prompt)
      }
      val jsonText = response.text?.takeIf { it.isNotBlank() } ?: return heuristic
      val json = JSONObject(jsonText)
      val fn = BotFunctionPreset.safeValueOf(json.optString("functionPreset", heuristic.inferredFunction.name))
      val rawSlug = json.optString("suggestedAgentId", fn.suggestedAgentId)
        .lowercase()
        .replace(Regex("[^a-z0-9_-]"), "")
        .take(40)
        .ifBlank { fn.suggestedAgentId }
      val instructions = json.optString("translatedInstructions", heuristic.translatedInstructions)
        .trim()
        .take(490)
        .ifBlank { heuristic.translatedInstructions }
      val summary = json.optString("summary", "Translated as ${fn.displayName} for OpenClaw.")
        .trim()
        .take(160)

      TranslatedBotProfileResult(
        inferredFunction = fn,
        suggestedAgentId = rawSlug,
        translatedInstructions = instructions,
        summary = summary,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Falling back to heuristic bot instruction translation: ${e.message}")
      heuristic
    }
  }

  private fun heuristicBotProfile(bot: CompanionBot, missionText: String): TranslatedBotProfileResult {
    val combined = "${missionText.lowercase()} ${bot.roleTitle.lowercase()} ${bot.systemPersona.lowercase()}"
    val inferred = when {
      combined.contains("translat") || combined.contains("japanese") || combined.contains("spanish") ||
        combined.contains("language") || combined.contains("bilingual") -> BotFunctionPreset.TRANSLATION
      combined.contains("proofread") || combined.contains("grammar") || combined.contains("spell") ||
        combined.contains("typo") || combined.contains("polish") || combined.contains("edit text") -> BotFunctionPreset.PROOFREADING
      combined.contains("critiqu") || combined.contains("review") || combined.contains("roast") ||
        combined.contains("feedback") || combined.contains("audit") -> BotFunctionPreset.CRITIQUING
      combined.contains("code") || combined.contains("debug") || combined.contains("script") ||
        combined.contains("program") || combined.contains("shell") || combined.contains("terminal") -> BotFunctionPreset.CODING
      combined.contains("research") || combined.contains("summar") || combined.contains("analy") ||
        combined.contains("report") || combined.contains("investigat") -> BotFunctionPreset.RESEARCH
      else -> bot.functionPresetEnum
    }
    val instructions = buildString {
      append(inferred.openClawDirective)
      if (missionText.isNotBlank()) {
        append(" Specific user mission: \"")
        append(missionText)
        append("\".")
      }
    }.take(490)

    return TranslatedBotProfileResult(
      inferredFunction = inferred,
      suggestedAgentId = inferred.suggestedAgentId,
      translatedInstructions = instructions,
      summary = "Translated mission into ${inferred.displayName} instructions.",
    )
  }

  private companion object {
    const val TAG = "PromptRefinerService"
  }
}
