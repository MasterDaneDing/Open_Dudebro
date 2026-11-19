package com.example.data.openclaw

import android.util.Log
import com.example.model.BotExpression
import com.example.model.CompanionBot
import com.example.model.OpenClawHost
import com.example.model.OpenClawLlmProvider
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

data class OpenClawSyncResult(
  val synced: Boolean,
  val endpointUsed: String,
  val summary: String,
  val resolvedModelLabel: String,
  val createdHostAgentRecord: Boolean = false,
)

data class LocalProviderKeys(
  val veniceApiKey: String = "",
  val googleApiKey: String = "",
)

data class EffectiveLlmConfig(
  val provider: OpenClawLlmProvider,
  val modelId: String,
  val localEndpoint: String,
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

  fun resolveEffectiveLlm(bot: CompanionBot, host: OpenClawHost): EffectiveLlmConfig {
    val botProvider = bot.llmProviderEnum
    val provider = if (botProvider == OpenClawLlmProvider.HOST_DEFAULT) {
      host.defaultLlmProviderEnum
    } else {
      botProvider
    }
    val modelId = when {
      botProvider != OpenClawLlmProvider.HOST_DEFAULT && bot.llmModel.isNotBlank() -> bot.llmModel.trim()
      botProvider == OpenClawLlmProvider.HOST_DEFAULT && host.defaultLlmModel.isNotBlank() -> host.defaultLlmModel.trim()
      else -> provider.defaultModel
    }
    return EffectiveLlmConfig(
      provider = provider,
      modelId = modelId,
      localEndpoint = host.localLlmEndpoint.trim(),
    )
  }

  fun buildBotSystemPrompt(bot: CompanionBot): String {
    val fn = bot.functionPresetEnum
    return buildString {
      appendLine("You are ${bot.name} (OpenClaw Agent ID: ${bot.openClawAgentId.ifBlank { "main" }}).")
      appendLine("Primary Function: ${fn.displayName} — ${fn.openClawDirective}")
      appendLine("User-Assigned Mission: ${bot.displayMission}")
      appendLine("Specialty Role: ${bot.roleTitle}")
      if (bot.systemPersona.isNotBlank()) {
        appendLine("Persona & Style: ${bot.systemPersona}")
      }
      append("Always perform your assigned function (${fn.displayName}) faithfully. If a user message is casual, clearly confirm the structured OpenClaw steps and deliver the result.")
    }
  }

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

  /**
   * Registers or updates a companion bot's function, mission, and default OpenClaw LLM on the host
   * so OpenClaw explicitly understands what the bot does before conversations start.
   */
  suspend fun syncBotProfile(
    host: OpenClawHost,
    bot: CompanionBot,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
  ): Result<OpenClawSyncResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("OpenClaw host URL is not configured."))
    }

    val llm = resolveEffectiveLlm(bot, host)
    val modelLabel = llm.modelId.ifBlank { llm.provider.displayName }
    val agentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    val systemPrompt = buildBotSystemPrompt(bot)

    // 1. Try OpenClaw agent hook registration endpoint (/hooks/agent)
    val hookEndpoint = "$cleanBase/hooks/agent"
    try {
      val registerJson = JSONObject().apply {
        put("action", "agent.register")
        put("agentId", agentId)
        put("botId", bot.id)
        put("name", bot.name)
        put("function", bot.functionPresetEnum.displayName)
        put("mission", bot.displayMission)
        put("roleTitle", bot.roleTitle)
        put("systemPrompt", systemPrompt)
        put(
          "llm",
          JSONObject().apply {
            put("provider", llm.provider.wireKey)
            if (llm.modelId.isNotBlank()) put("model", llm.modelId)
            if (llm.provider == OpenClawLlmProvider.LOCAL_LLM && llm.localEndpoint.isNotBlank()) {
              put("localEndpoint", llm.localEndpoint)
            }
          }
        )
      }
      val hookReq = Request.Builder()
        .url(hookEndpoint)
        .post(registerJson.toString().toRequestBody(jsonMediaType))
        .applyCommonHeaders(host, bot, llm, providerKeys)
        .build()

      client.newCall(hookReq).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (resp.isSuccessful) {
          return@withContext Result.success(
            OpenClawSyncResult(
              synced = true,
              endpointUsed = hookEndpoint,
              summary = body.ifBlank { "${bot.name} registered for ${bot.functionPresetEnum.displayName} ($modelLabel)." },
              resolvedModelLabel = modelLabel,
              createdHostAgentRecord = true,
            )
          )
        }
        // If /hooks/agent doesn't exist on this OpenClaw gateway (404/405), fall through to chat session priming.
        if (resp.code != 404 && resp.code != 405 && resp.code != 501) {
          return@withContext Result.failure(
            IllegalStateException("OpenClaw registration returned HTTP ${resp.code}: ${body.take(200)}")
          )
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // Fall through to /v1/chat/completions briefing attempt
    }

    // 2. Fallback: prime the OpenClaw agent session via /v1/chat/completions
    val chatEndpoint = "$cleanBase/v1/chat/completions"
    try {
      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        put(
          JSONObject()
            .put("role", "user")
            .put(
              "content",
              "[OPENCLAW BOT PROFILE SYNC] Confirm in 1 short sentence that you are configured as '${bot.name}' (agent:$agentId) with function '${bot.functionPresetEnum.displayName}' and mission '${bot.displayMission}' using $modelLabel."
            )
        )
      }
      val payload = JSONObject().apply {
        put("model", resolveWireModel(agentId, llm))
        put("messages", messagesArray)
        put("user", bot.userId)
      }
      val req = Request.Builder()
        .url(chatEndpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .applyCommonHeaders(host, bot, llm, providerKeys)
        .build()

      client.newCall(req).execute().use { resp ->
        val bodyStr = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          return@withContext Result.failure(
            IllegalStateException("OpenClaw gateway returned HTTP ${resp.code}: ${bodyStr.take(200)}")
          )
        }
        val parsed = extractReplyFromOpenAiCompatibleJson(bodyStr, null)
        Result.success(
          OpenClawSyncResult(
            synced = true,
            endpointUsed = chatEndpoint,
            summary = parsed.replyText.take(180),
            resolvedModelLabel = modelLabel,
          )
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "OpenClaw bot sync failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun unregisterBotProfile(
    host: OpenClawHost,
    bot: CompanionBot,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("Host URL is empty."))
    }
    val endpoint = "$cleanBase/hooks/agent"
    val agentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    try {
      val json = JSONObject().apply {
        put("action", "agent.delete")
        put("agentId", agentId)
        put("botId", bot.id)
        put("name", bot.name)
      }
      val reqBuilder = Request.Builder()
        .url(endpoint)
        .post(json.toString().toRequestBody(jsonMediaType))
      if (host.gatewayToken.isNotBlank()) {
        reqBuilder.addHeader("Authorization", "Bearer ${host.gatewayToken.trim()}")
      }
      client.newCall(reqBuilder.build()).execute().use { resp ->
        if (resp.isSuccessful || resp.code == 404 || resp.code == 405) {
          Result.success(Unit)
        } else {
          Result.failure(IllegalStateException("Host returned HTTP ${resp.code}"))
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun dispatchInstruction(
    host: OpenClawHost,
    bot: CompanionBot,
    casualMessage: String,
    refinedInstruction: String?,
    attachedFiles: List<String>,
    linkedPaths: List<String>,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("OpenClaw host URL is not configured."))
    }

    val llm = resolveEffectiveLlm(bot, host)
    val agentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    val endpoint = "$cleanBase/v1/chat/completions"
    try {
      val contentText = buildString {
        appendLine("[BOT FUNCTION: ${bot.functionPresetEnum.displayName} | MISSION: ${bot.displayMission}]")
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

      val systemPrompt = buildBotSystemPrompt(bot)

      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        put(JSONObject().put("role", "user").put("content", contentText))
      }

      val payload = JSONObject().apply {
        put("model", resolveWireModel(agentId, llm))
        put("messages", messagesArray)
        put("user", bot.userId)
      }

      val req = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .applyCommonHeaders(host, bot, llm, providerKeys)
        .build()

      client.newCall(req).execute().use { resp ->
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

  private fun resolveWireModel(agentId: String, llm: EffectiveLlmConfig): String {
    return if (llm.provider == OpenClawLlmProvider.OPENCLAW_AUTO || llm.modelId.isBlank()) {
      "openclaw:$agentId"
    } else {
      llm.modelId
    }
  }

  private fun Request.Builder.applyCommonHeaders(
    host: OpenClawHost,
    bot: CompanionBot,
    llm: EffectiveLlmConfig,
    providerKeys: LocalProviderKeys,
  ): Request.Builder {
    val agentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    addHeader("x-openclaw-agent-id", agentId)
    addHeader("x-openclaw-bot-function", bot.functionPresetEnum.name.lowercase())
    addHeader("x-openclaw-llm-provider", llm.provider.wireKey)
    if (llm.modelId.isNotBlank()) {
      addHeader("x-openclaw-llm-model", llm.modelId)
    }
    if (llm.provider == OpenClawLlmProvider.LOCAL_LLM && llm.localEndpoint.isNotBlank()) {
      addHeader("x-openclaw-local-llm-url", llm.localEndpoint)
    }
    val providerKey = when (llm.provider) {
      OpenClawLlmProvider.VENICE_AI -> providerKeys.veniceApiKey.trim()
      OpenClawLlmProvider.GOOGLE -> providerKeys.googleApiKey.trim()
      else -> ""
    }
    if (providerKey.isNotBlank()) {
      addHeader("x-openclaw-provider-api-key", providerKey)
    }
    if (host.gatewayToken.isNotBlank()) {
      addHeader("Authorization", "Bearer ${host.gatewayToken.trim()}")
    }
    return this
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
