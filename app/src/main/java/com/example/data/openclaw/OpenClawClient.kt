package com.example.data.openclaw

import android.util.Log
import com.example.model.BotExpression
import com.example.model.CompanionBot
import com.example.model.OpenClawHost
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class OpenClawPingResult(
  val reachable: Boolean,
  val statusCode: Int,
  val latencyMs: Long,
  val detail: String,
)

data class OpenClawDispatchResult(
  val replyText: String,
  val parsedInstruction: String?,
  val expression: BotExpression,
  val takeoverSuggested: Boolean,
)

enum class HostControlAction(val command: String, val label: String) {
  TAKEOVER_START("takeover.start", "Take Over Host"),
  TAKEOVER_RELEASE("takeover.release", "Hand Back to OpenClaw"),
  PAUSE_AGENT("agent.pause", "Pause Agent"),
  RESUME_AGENT("agent.resume", "Resume Agent"),
  SCREEN_SNAPSHOT("screen.snapshot", "Refresh Host Screen"),
  ABORT_TASK("task.abort", "Stop Current Run"),
  SEND_KEYS("input.keys", "Send Keystrokes"),
  RUN_COMMAND("shell.run", "Send Host Action"),
}

data class HostControlResponse(
  val accepted: Boolean,
  val summary: String,
  val endpointTried: String,
)

class OpenClawClient {
  private val client = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  suspend fun pingHost(host: OpenClawHost): OpenClawPingResult = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext OpenClawPingResult(false, 0, 0L, "Host URL is empty.")
    }
    val probes = listOf("$cleanBase/health", "$cleanBase/v1/models", cleanBase)
    val start = System.currentTimeMillis()
    var lastErr = "Host unreachable"
    for (url in probes) {
      try {
        val reqBuilder = Request.Builder().url(url).get()
        if (host.gatewayToken.isNotBlank()) {
          reqBuilder.addHeader("Authorization", "Bearer ${host.gatewayToken.trim()}")
        }
        client.newCall(reqBuilder.build()).execute().use { resp ->
          val latency = System.currentTimeMillis() - start
          if (resp.isSuccessful || resp.code in 400..499) {
            return@withContext OpenClawPingResult(
              reachable = resp.isSuccessful || resp.code == 401 || resp.code == 404,
              statusCode = resp.code,
              latencyMs = latency,
              detail = "Gateway responded HTTP ${resp.code} at $url (${latency}ms)",
            )
          }
          lastErr = "HTTP ${resp.code} from $url"
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        lastErr = e.localizedMessage ?: e.javaClass.simpleName
      }
    }
    val latency = System.currentTimeMillis() - start
    OpenClawPingResult(false, 0, latency, lastErr)
  }

  suspend fun dispatchInstruction(
    host: OpenClawHost,
    bot: CompanionBot,
    casualMessage: String,
    refinedInstruction: String?,
    attachedFiles: List<String>,
    linkedPaths: List<String>,
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("OpenClaw host URL is not configured."))
    }

    val endpoint = "$cleanBase/v1/chat/completions"
    try {
      val contentText = buildString {
        if (!refinedInstruction.isNullOrBlank()) {
          appendLine("[PARSED OPENCLAW INSTRUCTION]")
          appendLine(refinedInstruction)
          appendLine()
          appendLine("[ORIGINAL CASUAL REQUEST]")
          appendLine(casualMessage)
        } else {
          appendLine(casualMessage)
        }
        if (attachedFiles.isNotEmpty()) {
          appendLine("\nAttached Files: ${attachedFiles.joinToString(", ")}")
        }
        if (linkedPaths.isNotEmpty()) {
          appendLine("Linked Paths: ${linkedPaths.joinToString(", ")}")
        }
      }

      val systemPrompt = buildString {
        append("You are ${bot.name} (${bot.roleTitle}), an OpenClaw companion agent. ")
        append(bot.systemPersona)
        append(" If the user's message is casual, first summarize clear OpenClaw instructions, then execute or answer concisely.")
      }

      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        put(JSONObject().put("role", "user").put("content", contentText))
      }

      val payload = JSONObject().apply {
        put("model", "openclaw:${bot.openClawAgentId.ifBlank { "main" }}")
        put("messages", messagesArray)
        put("user", bot.userId)
      }

      val reqBuilder = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .addHeader("x-openclaw-agent-id", bot.openClawAgentId.ifBlank { "main" })

      if (host.gatewayToken.isNotBlank()) {
        reqBuilder.addHeader("Authorization", "Bearer ${host.gatewayToken.trim()}")
      }

      client.newCall(reqBuilder.build()).execute().use { resp ->
        val bodyStr = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          return@withContext Result.failure(
            IllegalStateException("OpenClaw gateway returned HTTP ${resp.code}: ${bodyStr.take(240)}")
          )
        }
        val parsed = extractReplyFromOpenAiCompatibleJson(bodyStr, refinedInstruction)
        Result.success(parsed)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "OpenClaw dispatch failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun sendControlAction(
    host: OpenClawHost,
    action: HostControlAction,
    payloadValue: String = "",
  ): Result<HostControlResponse> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("Host URL is empty."))
    }

    val endpoint = "$cleanBase/hooks/agent"
    try {
      val json = JSONObject().apply {
        put("action", action.command)
        put("label", action.label)
        if (payloadValue.isNotBlank()) {
          put("input", payloadValue)
        }
      }
      val reqBuilder = Request.Builder()
        .url(endpoint)
        .post(json.toString().toRequestBody(jsonMediaType))
      if (host.gatewayToken.isNotBlank()) {
        reqBuilder.addHeader("Authorization", "Bearer ${host.gatewayToken.trim()}")
      }

      client.newCall(reqBuilder.build()).execute().use { resp ->
        val body = resp.body?.string().orEmpty().take(300)
        if (resp.isSuccessful) {
          Result.success(
            HostControlResponse(
              accepted = true,
              summary = body.ifBlank { "${action.label} acknowledged (HTTP ${resp.code})." },
              endpointTried = endpoint,
            )
          )
        } else {
          Result.failure(
            IllegalStateException("Host control returned HTTP ${resp.code}: ${body.ifBlank { resp.message }}")
          )
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Host control action failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  private fun extractReplyFromOpenAiCompatibleJson(
    rawJson: String,
    refinedInstruction: String?,
  ): OpenClawDispatchResult {
    return try {
      val json = JSONObject(rawJson)
      val choices = json.optJSONArray("choices")
      val content = if (choices != null && choices.length() > 0) {
        choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty()
      } else {
        json.optString("reply", json.optString("output", rawJson))
      }
      val lowered = content.lowercase()
      val takeover = lowered.contains("take over") ||
        lowered.contains("captcha") ||
        lowered.contains("2fa") ||
        lowered.contains("login required")
      val expr = when {
        takeover -> BotExpression.ALERT
        lowered.contains("error") || lowered.contains("unable") || lowered.contains("failed") -> BotExpression.WORRIED
        else -> BotExpression.UH_HUH
      }
      OpenClawDispatchResult(
        replyText = content.ifBlank { "OpenClaw acknowledged your task." },
        parsedInstruction = refinedInstruction,
        expression = expr,
        takeoverSuggested = takeover,
      )
    } catch (_: Exception) {
      OpenClawDispatchResult(
        replyText = rawJson.ifBlank { "OpenClaw responded." },
        parsedInstruction = refinedInstruction,
        expression = BotExpression.UH_HUH,
        takeoverSuggested = false,
      )
    }
  }

  private companion object {
    const val TAG = "OpenClawClient"
  }
}
