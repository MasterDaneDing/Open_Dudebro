package com.example.data.openclaw

import android.util.Log
import com.example.data.ai.ChatRoomRoundResult
import com.example.data.ai.ChatRoomTurn
import com.example.model.BotExpression
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.GeneralChatMessage
import com.example.model.GeneratedMediaType
import com.example.model.MediaAttachmentPayload
import com.example.model.MessageSender
import com.example.model.OpenClawHost
import com.example.model.OpenClawLlmProvider
import com.example.model.RoomMessage
import com.example.model.VeniceModelCatalog
import com.example.model.VeniceModelInfo
import com.example.model.WheelLlmOption
import java.util.Locale
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
  val mediaUrl: String? = null,
  val mediaType: GeneratedMediaType? = null,
  val mediaPrompt: String? = null,
  val mediaModel: String? = null,
  val mediaDurationSec: Int? = null,
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
  val globalVeniceModel: String = VeniceModelCatalog.defaultModelId,
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
    .writeTimeout(20, TimeUnit.SECONDS)
    .build()

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  fun resolveWheelLlm(
    wheel: WheelLlmOption,
    host: OpenClawHost,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
  ): EffectiveLlmConfig {
    val provider = when (wheel) {
      WheelLlmOption.OPENCLAW_LOCAL -> OpenClawLlmProvider.LOCAL_LLM
      WheelLlmOption.OPENCLAW_THIRD_PARTY -> {
        if (host.defaultLlmProviderEnum == OpenClawLlmProvider.GOOGLE &&
          providerKeys.googleApiKey.isNotBlank() &&
          providerKeys.veniceApiKey.isBlank()
        ) {
          OpenClawLlmProvider.GOOGLE
        } else {
          OpenClawLlmProvider.VENICE_AI
        }
      }
      WheelLlmOption.OPENCLAW_DEFAULT -> {
        if (host.defaultLlmProviderEnum == OpenClawLlmProvider.LOCAL_LLM) {
          OpenClawLlmProvider.OPENCLAW_AUTO
        } else {
          host.defaultLlmProviderEnum
        }
      }
    }
    val modelId = when {
      wheel == WheelLlmOption.OPENCLAW_DEFAULT && host.defaultLlmModel.isNotBlank() ->
        host.defaultLlmModel.trim()
      wheel == WheelLlmOption.OPENCLAW_LOCAL &&
        host.defaultLlmProviderEnum == OpenClawLlmProvider.LOCAL_LLM &&
        host.defaultLlmModel.isNotBlank() -> host.defaultLlmModel.trim()
      provider == OpenClawLlmProvider.VENICE_AI -> {
        host.defaultLlmModel.takeIf { host.defaultLlmProviderEnum == OpenClawLlmProvider.VENICE_AI && it.isNotBlank() }
          ?: providerKeys.globalVeniceModel.takeIf { it.isNotBlank() }
          ?: provider.defaultModel
      }
      else -> provider.defaultModel
    }
    return EffectiveLlmConfig(
      provider = provider,
      modelId = modelId,
      localEndpoint = host.localLlmEndpoint.trim(),
    )
  }

  fun resolveEffectiveLlm(
    bot: CompanionBot,
    host: OpenClawHost,
    wheelOverride: WheelLlmOption? = null,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
  ): EffectiveLlmConfig {
    // Per-bot override takes priority when explicitly configured on the bot
    val botProvider = bot.llmProviderEnum
    if (botProvider != OpenClawLlmProvider.HOST_DEFAULT &&
      (wheelOverride == null || wheelOverride == WheelLlmOption.OPENCLAW_DEFAULT)
    ) {
      val modelId = bot.llmModel.trim().ifBlank {
        if (botProvider == OpenClawLlmProvider.VENICE_AI) {
          providerKeys.globalVeniceModel.ifBlank { botProvider.defaultModel }
        } else {
          botProvider.defaultModel
        }
      }
      return EffectiveLlmConfig(
        provider = botProvider,
        modelId = modelId,
        localEndpoint = host.localLlmEndpoint.trim(),
      )
    }

    if (wheelOverride != null) {
      return resolveWheelLlm(wheelOverride, host, providerKeys)
    }

    val hostProvider = host.defaultLlmProviderEnum
    val modelId = when {
      host.defaultLlmModel.isNotBlank() -> host.defaultLlmModel.trim()
      hostProvider == OpenClawLlmProvider.VENICE_AI ->
        providerKeys.globalVeniceModel.ifBlank { hostProvider.defaultModel }
      else -> hostProvider.defaultModel
    }
    return EffectiveLlmConfig(
      provider = hostProvider,
      modelId = modelId,
      localEndpoint = host.localLlmEndpoint.trim(),
    )
  }

  suspend fun fetchVeniceLiveModels(apiKey: String = ""): Result<List<VeniceModelInfo>> = withContext(Dispatchers.IO) {
    val reqBuilder = Request.Builder()
      .url("https://api.venice.ai/api/v1/models")
      .get()
      .addHeader("Accept", "application/json")
    val cleanKey = apiKey.trim()
    if (cleanKey.isNotBlank()) {
      reqBuilder.addHeader("Authorization", "Bearer $cleanKey")
    }

    try {
      client.newCall(reqBuilder.build()).execute().use { resp ->
        val raw = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          return@withContext Result.failure(
            IllegalStateException("Venice.ai returned HTTP ${resp.code}: ${raw.take(180)}")
          )
        }
        val json = JSONObject(raw)
        val arr = json.optJSONArray("data") ?: JSONArray()
        val live = buildList {
          for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            if (id.isEmpty()) continue
            val modelType = item.optString("type", "").lowercase(Locale.US)
            // Keep text & code models (skip pure image/upscale/tts models for the LLM text picker unless unlabeled)
            if (modelType.isNotBlank() && modelType !in setOf("text", "chat", "code", "llm", "")) {
              continue
            }
            val spec = item.optJSONObject("model_spec")
            val name = spec?.optString("name")?.takeIf { it.isNotBlank() } ?: id
            val caps = spec?.optJSONObject("capabilities")
            val isCode = caps?.optBoolean("supportsFunctionCalling", false) == true ||
              id.contains("coder", true) || id.contains("claude", true) || id.contains("deepseek", true)
            val category = if (isCode) "Coding & Chat" else "Text"
            add(VeniceModelInfo(id = id, label = name, category = category))
          }
        }.distinctBy { it.id }

        if (live.isEmpty()) {
          Result.success(VeniceModelCatalog.popularPresets)
        } else {
          Result.success(live)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Failed fetching live Venice models: ${e.message}", e)
      Result.failure(e)
    }
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
      append("Always perform your assigned function (${fn.displayName}) faithfully. If a user asks you to generate an image or video/animation, return the generated image or video URL in your response.")
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

  suspend fun syncBotProfile(
    host: OpenClawHost,
    bot: CompanionBot,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
  ): Result<OpenClawSyncResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("OpenClaw host URL is not configured."))
    }

    val llm = resolveEffectiveLlm(bot, host, providerKeys = providerKeys)
    val wireModel = resolveWireModel("main", llm)
    val modelLabel = llm.modelId.ifBlank { llm.provider.displayName }
    val agentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    val systemPrompt = buildBotSystemPrompt(bot)

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
        put("provider", llm.provider.wireKey)
        put("model", wireModel)
        putLlmCredentials(llm, providerKeys)
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
        put("provider", llm.provider.wireKey)
        put("model", resolveWireModel(agentId, llm))
        putLlmCredentials(llm, providerKeys)
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
        val parsed = extractReplyFromOpenAiCompatibleJson(bodyStr, null, host, modelLabel, bot.displayMission)
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
    wheelOverride: WheelLlmOption? = null,
    attachmentPayloads: List<MediaAttachmentPayload> = emptyList(),
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("OpenClaw host URL is not configured."))
    }

    val llm = resolveEffectiveLlm(bot, host, wheelOverride, providerKeys)
    val agentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    val wireModel = resolveWireModel(agentId, llm)
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
        appendAttachmentContextBlocks(attachmentPayloads)
      }

      val systemPrompt = buildBotSystemPrompt(bot)

      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        put(JSONObject().put("role", "user").put("content", contentText))
      }

      val payload = JSONObject().apply {
        put("provider", llm.provider.wireKey)
        put("model", wireModel)
        putLlmCredentials(llm, providerKeys)
        put("messages", messagesArray)
        put("user", bot.userId)
        val attachJson = buildAttachmentsJsonArray(attachmentPayloads)
        if (attachJson.length() > 0) {
          put("attachments", attachJson)
        }
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
        val parsed = extractReplyFromOpenAiCompatibleJson(
          rawJson = bodyStr,
          refinedInstruction = refinedInstruction,
          host = host,
          fallbackModel = wireModel,
          fallbackPrompt = refinedInstruction ?: casualMessage,
        )
        Result.success(parsed)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "OpenClaw dispatch failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun sendGeneralChat(
    host: OpenClawHost,
    userId: String,
    history: List<GeneralChatMessage>,
    userPrompt: String,
    wheelOption: WheelLlmOption,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
    attachmentPayloads: List<MediaAttachmentPayload> = emptyList(),
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("OpenClaw host URL is not configured."))
    }
    val llm = resolveWheelLlm(wheelOption, host, providerKeys)
    val wireModel = resolveWireModel("main", llm)
    val endpoint = "$cleanBase/v1/chat/completions"

    try {
      val enrichedPrompt = buildString {
        append(userPrompt)
        appendAttachmentContextBlocks(attachmentPayloads)
      }
      val messagesArray = JSONArray().apply {
        put(
          JSONObject()
            .put("role", "system")
            .put(
              "content",
              "You are a helpful, clear, general-purpose assistant running through the user's OpenClaw gateway. Answer everyday questions, analyze attached PDF/code files, or return generated image/video URLs cleanly."
            )
        )
        history.takeLast(12).forEach { msg ->
          val role = if (msg.senderEnum == MessageSender.USER) "user" else "assistant"
          if (msg.rawText.isNotBlank()) {
            put(JSONObject().put("role", role).put("content", msg.rawText))
          }
        }
        put(JSONObject().put("role", "user").put("content", enrichedPrompt))
      }
      val payload = JSONObject().apply {
        put("provider", llm.provider.wireKey)
        put("model", wireModel)
        putLlmCredentials(llm, providerKeys)
        put("messages", messagesArray)
        put("user", userId)
        val attachJson = buildAttachmentsJsonArray(attachmentPayloads)
        if (attachJson.length() > 0) {
          put("attachments", attachJson)
        }
      }
      val reqBuilder = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .applyWheelHeaders(host, llm, providerKeys)

      client.newCall(reqBuilder.build()).execute().use { resp ->
        val bodyStr = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          return@withContext Result.failure(
            IllegalStateException("OpenClaw gateway returned HTTP ${resp.code}: ${bodyStr.take(220)}")
          )
        }
        val parsed = extractReplyFromOpenAiCompatibleJson(
          rawJson = bodyStr,
          refinedInstruction = null,
          host = host,
          fallbackModel = wireModel,
          fallbackPrompt = userPrompt,
        )
        Result.success(parsed)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "General chat OpenClaw dispatch failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  suspend fun dispatchChatRoomTurn(
    host: OpenClawHost,
    userId: String,
    room: ChatRoom,
    participants: List<CompanionBot>,
    recentHistory: List<RoomMessage>,
    userPrompt: String,
    wheelOption: WheelLlmOption,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
  ): Result<ChatRoomRoundResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.baseUrl.trim().trimEnd('/')
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(IllegalArgumentException("OpenClaw host URL is not configured."))
    }
    val llm = resolveWheelLlm(wheelOption, host, providerKeys)
    val wireModel = resolveWireModel("main", llm)
    val endpoint = "$cleanBase/v1/chat/completions"
    try {
      val systemPrompt = buildString {
        appendLine("You are orchestrating a lively Cha Chaan Teng multi-bot collaborative lounge titled '${room.title}'.")
        if (room.topic.isNotBlank()) appendLine("Topic: ${room.topic}")
        appendLine("Participating bots:")
        participants.forEach { bot ->
          appendLine("- ${bot.name} | Role: ${bot.functionPresetEnum.displayName} | Mission: \"${bot.displayMission}\"")
        }
        appendLine("Reply strictly as a JSON object: {\"turns\":[{\"botName\":\"...\",\"replyToBotName\":null,\"message\":\"...\",\"expression\":\"UH_HUH\",\"parsedInstruction\":null}]}.")
        append("Produce 2 to 3 concise collaborative turns using exact participant names.")
      }
      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        recentHistory.takeLast(8).forEach { msg ->
          val role = if (msg.senderTypeEnum == MessageSender.USER) "user" else "assistant"
          put(JSONObject().put("role", role).put("content", "[${msg.senderBotName}]: ${msg.rawText}"))
        }
        put(JSONObject().put("role", "user").put("content", userPrompt))
      }
      val payload = JSONObject().apply {
        put("provider", llm.provider.wireKey)
        put("model", wireModel)
        putLlmCredentials(llm, providerKeys)
        put("messages", messagesArray)
        put("user", userId)
      }
      val req = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .applyWheelHeaders(host, llm, providerKeys)
        .build()

      client.newCall(req).execute().use { resp ->
        val bodyStr = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          return@withContext Result.failure(
            IllegalStateException("OpenClaw gateway returned HTTP ${resp.code}: ${bodyStr.take(220)}")
          )
        }
        val contentText = extractReplyFromOpenAiCompatibleJson(bodyStr, null, host, wireModel, userPrompt).replyText.trim()
        val turns = parseChatRoomTurnsFromText(contentText, participants)
        Result.success(ChatRoomRoundResult(turns))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "ChatRoom OpenClaw turn failed: ${e.message}", e)
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

  private fun StringBuilder.appendAttachmentContextBlocks(payloads: List<MediaAttachmentPayload>) {
    payloads.forEach { att ->
      val hasContent = att.extractedContent.isNotBlank() || att.uploadedHostId.isNotBlank()
      if (!hasContent) return@forEach
      appendLine()
      appendLine("[ATTACHED FILE: ${att.displayName} (${att.mimeType.ifBlank { "file" }})]")
      if (att.uploadedHostId.isNotBlank()) {
        appendLine("Host Upload Reference: ${att.uploadedHostId}")
      }
      if (att.extractedContent.isNotBlank()) {
        val ext = att.displayName.substringAfterLast('.', "").lowercase(Locale.US)
        appendLine("```$ext")
        appendLine(att.extractedContent)
        appendLine("```")
      }
    }
  }

  private fun buildAttachmentsJsonArray(payloads: List<MediaAttachmentPayload>): JSONArray {
    return JSONArray().apply {
      payloads.forEach { att ->
        put(
          JSONObject().apply {
            put("name", att.displayName)
            put("uriOrPath", att.uriOrPath)
            put("mimeType", att.mimeType)
            put("kind", if (att.isPdf) "pdf" else att.linkType.name.lowercase(Locale.US))
            if (att.uploadedHostId.isNotBlank()) put("hostFileId", att.uploadedHostId)
            if (att.extractedContent.isNotBlank()) put("content", att.extractedContent)
          }
        )
      }
    }
  }

  private fun JSONObject.putLlmCredentials(
    llm: EffectiveLlmConfig,
    providerKeys: LocalProviderKeys,
  ) {
    val providerKey = when (llm.provider) {
      OpenClawLlmProvider.VENICE_AI -> providerKeys.veniceApiKey.trim()
      OpenClawLlmProvider.GOOGLE -> providerKeys.googleApiKey.trim()
      else -> ""
    }
    if (providerKey.isNotBlank()) {
      put("apiKey", providerKey)
    }
  }

  private fun resolveWireModel(agentId: String, llm: EffectiveLlmConfig): String {
    return if (llm.provider == OpenClawLlmProvider.OPENCLAW_AUTO || llm.modelId.isBlank()) {
      "openclaw:$agentId"
    } else {
      llm.modelId
    }
  }

  private fun Request.Builder.applyWheelHeaders(
    host: OpenClawHost,
    llm: EffectiveLlmConfig,
    providerKeys: LocalProviderKeys,
  ): Request.Builder {
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

  private fun parseChatRoomTurnsFromText(
    raw: String,
    participants: List<CompanionBot>,
  ): List<ChatRoomTurn> {
    val startIdx = raw.indexOf('{')
    val endIdx = raw.lastIndexOf('}')
    if (startIdx >= 0 && endIdx > startIdx) {
      try {
        val json = JSONObject(raw.substring(startIdx, endIdx + 1))
        val arr = json.optJSONArray("turns")
        if (arr != null && arr.length() > 0) {
          val parsed = buildList {
            for (i in 0 until arr.length()) {
              val item = arr.optJSONObject(i) ?: continue
              val botName = item.optString("botName").trim()
              val msg = item.optString("message").trim()
              if (botName.isNotEmpty() && msg.isNotEmpty()) {
                add(
                  ChatRoomTurn(
                    botName = botName,
                    replyToBotName = item.optString("replyToBotName").trim().takeIf { it.isNotEmpty() && it != "null" },
                    message = msg.take(4000),
                    expression = BotExpression.safeValueOf(item.optString("expression", "UH_HUH")),
                    parsedInstruction = item.optString("parsedInstruction").trim().takeIf { it.isNotEmpty() && it != "null" },
                  )
                )
              }
            }
          }
          if (parsed.isNotEmpty()) return parsed
        }
      } catch (_: Exception) {
        // Fallback below
      }
    }
    val speaker = participants.first()
    return listOf(
      ChatRoomTurn(
        botName = speaker.name,
        replyToBotName = null,
        message = raw.take(4000).ifBlank { "Let's chat in the Cha Chaan Teng!" },
        expression = BotExpression.UH_HUH,
        parsedInstruction = null,
      )
    )
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
    return applyWheelHeaders(host, llm, providerKeys)
  }

  private fun extractReplyFromOpenAiCompatibleJson(
    rawJson: String,
    refinedInstruction: String?,
    host: OpenClawHost? = null,
    fallbackModel: String = "",
    fallbackPrompt: String = "",
  ): OpenClawDispatchResult {
    return try {
      val json = JSONObject(rawJson)
      val choices = json.optJSONArray("choices")
      val msgObj = if (choices != null && choices.length() > 0) {
        choices.getJSONObject(0).optJSONObject("message")
      } else null
      val content = msgObj?.optString("content").orEmpty()
        .ifBlank { json.optString("reply", json.optString("output", "")) }

      // Extract structured media URL from root, message object, media/attachments arrays, or data[]
      val mediaObj = json.optJSONObject("media") ?: msgObj?.optJSONObject("media")
      val firstAttach = (json.optJSONArray("attachments") ?: msgObj?.optJSONArray("attachments"))
        ?.optJSONObject(0)
      val firstData = json.optJSONArray("data")?.optJSONObject(0)

      val rawVideoUrl = listOfNotNull(
        json.optString("video_url").takeIf { it.isNotBlank() },
        json.optString("videoUrl").takeIf { it.isNotBlank() },
        msgObj?.optString("video_url")?.takeIf { it.isNotBlank() },
        msgObj?.optString("videoUrl")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("video_url")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("videoUrl")?.takeIf { it.isNotBlank() },
      ).firstOrNull()

      val rawImageUrl = listOfNotNull(
        json.optString("image_url").takeIf { it.isNotBlank() },
        json.optString("imageUrl").takeIf { it.isNotBlank() },
        msgObj?.optString("image_url")?.takeIf { it.isNotBlank() },
        msgObj?.optString("imageUrl")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("image_url")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("imageUrl")?.takeIf { it.isNotBlank() },
        firstData?.optString("url")?.takeIf { it.isNotBlank() },
      ).firstOrNull()

      val genericMediaUrl = listOfNotNull(
        rawVideoUrl,
        rawImageUrl,
        json.optString("media_url").takeIf { it.isNotBlank() },
        json.optString("mediaUrl").takeIf { it.isNotBlank() },
        json.optString("output_url").takeIf { it.isNotBlank() },
        msgObj?.optString("media_url")?.takeIf { it.isNotBlank() },
        msgObj?.optString("mediaUrl")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("url")?.takeIf { it.isNotBlank() },
        firstAttach?.optString("url")?.takeIf { it.isNotBlank() },
        firstAttach?.optString("path")?.takeIf { it.isNotBlank() },
      ).firstOrNull()

      val textDetectedMedia = if (genericMediaUrl == null) {
        detectMediaUrlInText(content)
      } else null

      val rawMediaUrl = genericMediaUrl ?: textDetectedMedia
      val resolvedMediaUrl = resolveMediaUrl(rawMediaUrl, host)
      val b64Fallback = firstData?.optString("b64_json")?.takeIf { it.isNotBlank() }
        ?.let { "data:image/png;base64,$it" }

      val finalMediaUrl = resolvedMediaUrl ?: b64Fallback
      val explicitType = (json.optString("media_type")
        .ifBlank { json.optString("mediaType") }
        .ifBlank { mediaObj?.optString("type").orEmpty() }
        .ifBlank { firstAttach?.optString("type").orEmpty() })
        .lowercase(Locale.US)

      val detectedType = when {
        finalMediaUrl == null -> null
        rawVideoUrl != null || explicitType.contains("video") || isVideoUrl(finalMediaUrl) ->
          GeneratedMediaType.VIDEO
        else -> GeneratedMediaType.IMAGE
      }

      val mediaPrompt = json.optString("prompt")
        .ifBlank { mediaObj?.optString("prompt").orEmpty() }
        .ifBlank { firstData?.optString("revised_prompt").orEmpty() }
        .ifBlank { fallbackPrompt }
        .take(1000)

      val mediaModel = json.optString("model")
        .ifBlank { mediaObj?.optString("model").orEmpty() }
        .ifBlank { fallbackModel }
        .take(120)

      val durationSec = listOf(
        json.optInt("duration_sec", -1),
        json.optInt("duration", -1),
        mediaObj?.optInt("duration_sec", -1) ?: -1,
        mediaObj?.optInt("duration", -1) ?: -1,
      ).firstOrNull { it > 0 } ?: if (detectedType == GeneratedMediaType.VIDEO) 5 else 0

      val displayContent = content.ifBlank {
        when (detectedType) {
          GeneratedMediaType.VIDEO -> "Generated your video clip via OpenClaw!"
          GeneratedMediaType.IMAGE -> "Generated your image via OpenClaw!"
          null -> "OpenClaw acknowledged your task."
        }
      }

      val lowered = displayContent.lowercase()
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
        replyText = displayContent,
        parsedInstruction = refinedInstruction,
        expression = expr,
        takeoverSuggested = takeover,
        mediaUrl = finalMediaUrl,
        mediaType = detectedType,
        mediaPrompt = mediaPrompt.takeIf { finalMediaUrl != null && it.isNotBlank() },
        mediaModel = mediaModel.takeIf { finalMediaUrl != null && it.isNotBlank() },
        mediaDurationSec = durationSec.takeIf { finalMediaUrl != null },
      )
    } catch (_: Exception) {
      val fallbackUrl = resolveMediaUrl(detectMediaUrlInText(rawJson), host)
      val detectedType = fallbackUrl?.let {
        if (isVideoUrl(it)) GeneratedMediaType.VIDEO else GeneratedMediaType.IMAGE
      }
      OpenClawDispatchResult(
        replyText = rawJson.ifBlank { "OpenClaw responded." },
        parsedInstruction = refinedInstruction,
        expression = BotExpression.UH_HUH,
        takeoverSuggested = false,
        mediaUrl = fallbackUrl,
        mediaType = detectedType,
        mediaPrompt = fallbackPrompt.takeIf { fallbackUrl != null && it.isNotBlank() },
        mediaModel = fallbackModel.takeIf { fallbackUrl != null && it.isNotBlank() },
        mediaDurationSec = if (detectedType == GeneratedMediaType.VIDEO) 5 else null,
      )
    }
  }

  private fun detectMediaUrlInText(text: String): String? {
    val mdMatch = Regex("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)").find(text)
    if (mdMatch != null) return mdMatch.groupValues[1]

    val urlRegex = Regex("(https?://[^\\s)\"'>]+\\.(?:png|jpg|jpeg|webp|gif|mp4|webm|mov)(?:\\?[^\\s)\"'>]*)?)", RegexOption.IGNORE_CASE)
    urlRegex.find(text)?.let { return it.groupValues[1] }

    val relRegex = Regex("((?:/media/|/files/|/outputs/|~/|C:\\\\)[^\\s)\"'>]+\\.(?:png|jpg|jpeg|webp|gif|mp4|webm|mov))", RegexOption.IGNORE_CASE)
    return relRegex.find(text)?.groupValues[1]
  }

  private fun isVideoUrl(url: String): Boolean {
    val clean = url.substringBefore('?').lowercase(Locale.US)
    return clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mov")
  }

  private fun resolveMediaUrl(raw: String?, host: OpenClawHost?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    if (trimmed.startsWith("http://", true) ||
      trimmed.startsWith("https://", true) ||
      trimmed.startsWith("data:", true)
    ) {
      return trimmed
    }
    val base = host?.baseUrl?.trim()?.trimEnd('/').orEmpty()
    if (base.isBlank()) return trimmed
    return if (trimmed.startsWith("/")) {
      "$base$trimmed"
    } else {
      "$base/media/$trimmed"
    }
  }

  private companion object {
    const val TAG = "OpenClawClient"
  }
}
