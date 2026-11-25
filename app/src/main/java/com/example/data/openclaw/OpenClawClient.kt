package com.example.data.openclaw

import android.util.Log
import com.example.data.ai.ChatRoomRoundResult
import com.example.data.ai.ChatRoomTurn
import com.example.data.files.MediaSaveHelper
import com.example.model.BotDude
import com.example.model.BotExpression
import com.example.model.BotMessage
import com.example.model.CafeEntryKind
import com.example.model.CafeSpeakerRole
import com.example.model.CafeTranscriptEntry
import com.example.model.CaptainContextDocument
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.ContextChatMessage
import com.example.model.ContextDocStatus
import com.example.model.FileLinkType
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
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.SocketFactory
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Dispatcher
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
  val roomGoalSatisfied: Boolean = false,
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
  val deviceId: String = MediaSaveHelper.DEFAULT_DEVICE_ID,
  val bridgeEnabled: Boolean = false,
  val bridgeFolderUri: String = "",
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
  OPEN_URL("browser.open", "Open URL in Browser"),
}

data class HostControlResponse(
  val accepted: Boolean,
  val summary: String,
  val endpointTried: String,
)

class OpenClawFormattedException(
  override val message: String,
  val isFastConnectFailure: Boolean = false,
) : IllegalStateException(message)

class OpenClawClient(
  private val appFilesDir: File? = null,
  private val driveBridgeManager: DriveBridgeManager? = null,
) {

  private val httpDispatcher = Dispatcher().apply {
    maxRequests = 32
    maxRequestsPerHost = 16
  }

  private val keepAliveSocketFactory = object : SocketFactory() {
    private val delegate = getDefault()
    private fun Socket.enableKeepAlive(): Socket = apply {
      runCatching { keepAlive = true }
    }
    override fun createSocket(): Socket = delegate.createSocket().enableKeepAlive()
    override fun createSocket(host: String?, port: Int): Socket =
      delegate.createSocket(host, port).enableKeepAlive()
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
      delegate.createSocket(host, port, localHost, localPort).enableKeepAlive()
    override fun createSocket(host: InetAddress?, port: Int): Socket =
      delegate.createSocket(host, port).enableKeepAlive()
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
      delegate.createSocket(address, port, localAddress, localPort).enableKeepAlive()
  }

  private val client = OkHttpClient.Builder()
    .dispatcher(httpDispatcher)
    .socketFactory(keepAliveSocketFactory)
    .connectTimeout(SSE_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .readTimeout(SSE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .callTimeout(0, TimeUnit.MILLISECONDS)
    .retryOnConnectionFailure(true)
    .build()

  private val pingClient = client.newBuilder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  /** Shuts down the OkHttp dispatcher so test processes can exit cleanly. */
  fun shutdown() {
    runCatching { httpDispatcher.executorService.shutdown() }
    runCatching { client.connectionPool.evictAll() }
  }

  internal val sseConnectTimeoutMillis: Long
    get() = client.connectTimeoutMillis.toLong()

  internal val sseReadTimeoutMillis: Long
    get() = client.readTimeoutMillis.toLong()

  internal val sseCallTimeoutMillis: Long
    get() = client.callTimeoutMillis.toLong()

  private val discoveredTargetsByHost = ConcurrentHashMap<String, Set<String>>()
  private val activeCallsBySession = ConcurrentHashMap<String, Call>()

  fun abortSession(sessionKey: String) {
    val call = activeCallsBySession.remove(sessionKey)
    try {
      call?.cancel()
    } catch (_: Exception) {
    }
  }

  fun abortActiveRequest() {
    val keys = activeCallsBySession.keys().toList()
    keys.forEach { key ->
      val call = activeCallsBySession.remove(key)
      try {
        call?.cancel()
      } catch (_: Exception) {
      }
    }
  }

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
      WheelLlmOption.GEMINI_FLASH -> OpenClawLlmProvider.GOOGLE
    }
    val modelId = when {
      wheel == WheelLlmOption.GEMINI_FLASH -> "google/gemini-3.8-flash"
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
            OpenClawFormattedException(
              formatHttpErrorMessage(resp.code, raw, "Venice.ai"),
              isFastConnectFailure = false,
            )
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
      Result.failure(formatNetworkThrowable(e, "https://api.venice.ai", 0L))
    }
  }

  /**
   * Builds the context window for a cafe turn under a bounded budget instead of a flat "last N".
   *
   * Always pins the original user task (the first USER entry) and the most recent bot deliverable
   * (the last BOT entry that is not the speaker's own), so a critic never loses the translation it
   * is reviewing and the translator never loses the brief. The remaining budget is filled with the
   * newest turns, keeping chronological order.
   */
  fun selectTranscriptWindow(
    transcript: List<CafeTranscriptEntry>,
    selfId: String,
    selfName: String,
    maxEntries: Int = 14,
    maxChars: Int = 14000,
  ): List<CafeTranscriptEntry> {
    if (transcript.isEmpty()) return emptyList()
    val isSelf: (CafeTranscriptEntry) -> Boolean = { e ->
      e.role == CafeSpeakerRole.BOT &&
        (e.speakerId == selfId || e.speakerName.equals(selfName, ignoreCase = true))
    }
    val pinned = LinkedHashSet<CafeTranscriptEntry>()
    transcript.firstOrNull { it.role == CafeSpeakerRole.USER }?.let(pinned::add)
    val peerBot = { e: CafeTranscriptEntry -> e.role == CafeSpeakerRole.BOT && !isSelf(e) }
    // Prefer the newest real artefact, then the newest review, then any newest bot turn.
    transcript.lastOrNull { peerBot(it) && it.kind == CafeEntryKind.DELIVERABLE }?.let(pinned::add)
      ?: transcript.lastOrNull { peerBot(it) && it.kind == CafeEntryKind.REVIEW }?.let(pinned::add)
      ?: transcript.lastOrNull { peerBot(it) }?.let(pinned::add)

    var chars = pinned.sumOf { it.content.length }
    val filled = ArrayList<CafeTranscriptEntry>()
    for (entry in transcript.asReversed()) {
      if (pinned.size + filled.size >= maxEntries) break
      if (entry in pinned) continue
      if (chars + entry.content.length > maxChars && filled.isNotEmpty()) break
      chars += entry.content.length
      filled.add(entry)
    }
    val keep = (pinned + filled).toSet()
    return transcript.filter { it in keep }.takeLast(maxEntries)
  }

  fun buildBotSystemPrompt(bot: CompanionBot): String {
    val fn = bot.functionPresetEnum
    val perms = bot.effectiveToolPermissions.joinToString(", ")
    return buildString {
      appendLine("You are ${bot.name} (OpenClaw Agent ID: ${bot.openClawAgentId.ifBlank { "main" }}).")
      appendLine("Isolated Session Key: ${bot.effectivePrimarySessionKey}")
      appendLine("Primary Function: ${fn.displayName} — ${fn.openClawDirective}")
      appendLine("User-Assigned Mission: ${bot.displayMission}")
      appendLine("Specialty Role: ${bot.roleTitle}")
      appendLine("Allowed Tools / Capabilities (HARD allowlist — do not use anything outside this): $perms")
      if (bot.workspacePath.isNotBlank()) {
        appendLine("Isolated Workspace Path: ${bot.workspacePath.trim()}")
      }
      if (bot.systemPersona.isNotBlank()) {
        appendLine("Persona & Style: ${bot.systemPersona}")
      }
      appendLine("Always perform your assigned function (${fn.displayName}) faithfully. If a needed tool is not on the allowlist, say so instead of improvising.")
      append(MEDIA_SYSTEM_INSTRUCTION)
    }
  }

  suspend fun pingHost(
    host: OpenClawHost,
    deviceId: String = MediaSaveHelper.DEFAULT_DEVICE_ID,
  ): OpenClawPingResult = withContext(Dispatchers.IO) {
    val primaryBase = host.effectiveBaseUrl
    if (primaryBase.isBlank()) {
      return@withContext OpenClawPingResult(false, 0, 0L, "Host URL is empty.")
    }
    val basesToTry = buildList {
      add(primaryBase to false)
      val fallback = host.fallbackBaseUrl
      if (fallback.isNotBlank()) {
        add(fallback to true)
      }
    }
    val start = System.currentTimeMillis()
    var lastErr = "Host unreachable"
    var lastStatus = 0
    for ((base, isFallback) in basesToTry) {
      val modelsUrl = "$base/v1/models"
      if (FAST_PROBE_ENABLED && !isFallback) {
        val probeErr = probeTcpReachable(base, FAST_TCP_PROBE_TIMEOUT_MS)
        if (probeErr != null) {
          Log.w(TAG, "pingHost TCP probe failed for $base (${probeErr.message})")
          val fallback = host.fallbackBaseUrl
          if (fallback.isNotBlank() && probeTcpReachable(fallback, FAST_TCP_PROBE_TIMEOUT_MS) == null) {
            val elapsed = System.currentTimeMillis() - start
            lastErr = formatNetworkThrowable(probeErr, base, elapsed).message
            continue
          }
        }
      }
      try {
        val reqBuilder = Request.Builder().url(modelsUrl).get()
          .addHeader("x-openclaw-device-id", deviceId.trim().ifBlank { MediaSaveHelper.DEFAULT_DEVICE_ID })
          .addHeader("x-openclaw-client", MediaSaveHelper.DEFAULT_DEVICE_ID)
        if (host.gatewayToken.isNotBlank()) {
          reqBuilder.addHeader("Authorization", "Bearer ${host.gatewayToken.trim()}")
        }
        pingClient.newCall(reqBuilder.build()).execute().use { resp ->
          val latency = System.currentTimeMillis() - start
          val bodyStr = resp.body?.string().orEmpty()
          lastStatus = resp.code
          if (resp.isSuccessful) {
            val parsedTargets = parseDiscoveredModelTargets(bodyStr)
            if (parsedTargets.isNotEmpty()) {
              discoveredTargetsByHost[base] = parsedTargets
            }
            val count = parsedTargets.size
            val suffix = if (count > 0) " • $count model target(s)" else ""
            val failoverNote = if (isFallback) " (via fallback host)" else ""
            return@withContext OpenClawPingResult(
              reachable = true,
              statusCode = resp.code,
              latencyMs = latency,
              detail = "Connected HTTP ${resp.code} at $modelsUrl (${latency}ms)$suffix$failoverNote",
            )
          }
          if (resp.code == 401 || resp.code == 403) {
            return@withContext OpenClawPingResult(
              reachable = false,
              statusCode = resp.code,
              latencyMs = latency,
              detail = formatHttpErrorMessage(resp.code, bodyStr, base),
            )
          }
          lastErr = formatHttpErrorMessage(resp.code, bodyStr, base)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val elapsed = System.currentTimeMillis() - start
        lastErr = formatNetworkThrowable(e, base, elapsed).message
      }
    }
    val latency = System.currentTimeMillis() - start
    OpenClawPingResult(false, lastStatus, latency, lastErr)
  }

  private fun parseDiscoveredModelTargets(rawJson: String): Set<String> {
    return try {
      val root = JSONObject(rawJson)
      val arr = root.optJSONArray("data") ?: return emptySet()
      buildSet {
        for (i in 0 until arr.length()) {
          val id = arr.optJSONObject(i)?.optString("id")?.trim().orEmpty()
          if (id.isNotBlank()) add(id)
        }
      }
    } catch (_: Exception) {
      emptySet()
    }
  }

  suspend fun syncBotProfile(
    host: OpenClawHost,
    bot: CompanionBot,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
  ): Result<OpenClawSyncResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.effectiveBaseUrl
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(OpenClawFormattedException("OpenClaw host URL is not configured."))
    }

    val llm = resolveEffectiveLlm(bot, host, providerKeys = providerKeys)
    val modelLabel = resolveXOpenClawModelHeader(llm) ?: llm.modelId.ifBlank { llm.provider.displayName }
    val rawAgentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    val wireModel = resolveWireModel(rawAgentId, cleanBase)
    val userSessionId = sanitizeUserSessionId("conv:bot_${bot.resolvedBotDudeSlug()}_sync")
    val systemPrompt = buildBotSystemPrompt(bot)
    val chatEndpoint = "$cleanBase/v1/chat/completions"
    val startMs = System.currentTimeMillis()

    try {
      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        put(
          JSONObject()
            .put("role", "user")
            .put(
              "content",
              "[OPENCLAW BOT PROFILE SYNC] Confirm in 1 short sentence that you are configured as '${bot.name}' (role:$rawAgentId, session:$userSessionId) with function '${bot.functionPresetEnum.displayName}' and mission '${bot.displayMission}' using $modelLabel."
            )
        )
      }
      val payload = JSONObject().apply {
        put("model", wireModel)
        put("stream", true)
        put("messages", messagesArray)
        put("user", userSessionId)
      }
      val req = Request.Builder()
        .url(chatEndpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .addHeader("Accept", "text/event-stream, application/json")
        .applyCommonHeaders(host, bot, llm, providerKeys)
        .build()

      executeChatCompletionsWithSseFallback(
        req = req,
        cleanBase = cleanBase,
        refinedInstruction = null,
        host = host,
        fallbackModel = modelLabel,
        fallbackPrompt = bot.displayMission,
        sessionKey = userSessionId,
        agentId = MEDIA_ROUTE_AGENT_ID,
        deviceId = providerKeys.deviceId,
        callTrackingKey = userSessionId,
        onPartialText = {},
      ).map { parsed ->
        OpenClawSyncResult(
          synced = true,
          endpointUsed = chatEndpoint,
          summary = parsed.replyText.take(180),
          resolvedModelLabel = modelLabel,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startMs
      Log.w(TAG, "OpenClaw bot sync failed: ${e.message}", e)
      Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
    }
  }

  suspend fun unregisterBotProfile(
    host: OpenClawHost,
    bot: CompanionBot,
  ): Result<Unit> = withContext(Dispatchers.IO) {
    // OpenClaw 2026.11.6 manages companion metadata app-side without requiring /hooks/agent delete calls.
    Result.success(Unit)
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
    history: List<BotMessage> = emptyList(),
    onPartialText: (String) -> Unit = {},
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.effectiveBaseUrl
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(OpenClawFormattedException("OpenClaw host URL is not configured."))
    }

    val llm = resolveEffectiveLlm(bot, host, wheelOverride, providerKeys)
    val rawAgentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    val wireModel = resolveWireModel(rawAgentId, cleanBase)
    val backendModelLabel = resolveXOpenClawModelHeader(llm) ?: wireModel
    val userSessionId = sanitizeUserSessionId("conv:bot_${bot.resolvedBotDudeSlug()}")
    val trackingKey = bot.effectivePrimarySessionKey
    val endpoint = "$cleanBase/v1/chat/completions"
    val startMs = System.currentTimeMillis()

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
        history.takeLast(8).forEach { msg ->
          val text = msg.rawText.trim()
          if (text.isNotBlank() &&
            msg.statusEnum != com.example.model.MessageStatus.PARSING &&
            msg.statusEnum != com.example.model.MessageStatus.DISPATCHED
          ) {
            val role = if (msg.senderEnum == MessageSender.USER) "user" else "assistant"
            put(JSONObject().put("role", role).put("content", text.take(2500)))
          }
        }
        put(JSONObject().put("role", "user").put("content", buildUserMessageContent(contentText, attachmentPayloads)))
      }

      val promptForPayload = refinedInstruction ?: casualMessage
      if (providerKeys.bridgeEnabled && providerKeys.bridgeFolderUri.isNotBlank() && driveBridgeManager != null) {
        return@withContext driveBridgeManager.dispatchTaskAndAwaitResult(
          folderUriOrPath = providerKeys.bridgeFolderUri,
          host = host,
          wireModel = wireModel,
          backendModel = backendModelLabel,
          sessionKey = userSessionId,
          agentId = MEDIA_ROUTE_AGENT_ID,
          deviceId = providerKeys.deviceId,
          userPrompt = promptForPayload,
          messagesArray = messagesArray,
          refinedInstruction = refinedInstruction,
          onPartialText = onPartialText,
        )
      }

      val payload = JSONObject().apply {
        put("model", wireModel)
        put("stream", true)
        put("messages", messagesArray)
        put("user", userSessionId)
      }

      val req = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .addHeader("Accept", "text/event-stream, application/json")
        .applyCommonHeaders(host, bot, llm, providerKeys)
        .build()

      executeChatCompletionsWithSseFallback(
        req = req,
        cleanBase = cleanBase,
        refinedInstruction = refinedInstruction,
        host = host,
        fallbackModel = backendModelLabel,
        fallbackPrompt = promptForPayload,
        sessionKey = userSessionId,
        agentId = MEDIA_ROUTE_AGENT_ID,
        deviceId = providerKeys.deviceId,
        callTrackingKey = trackingKey,
        onPartialText = onPartialText,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startMs
      Log.w(TAG, "OpenClaw dispatch failed: ${e.message}", e)
      Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
    }
  }

  /**
   * Spawns an isolated background sub-agent lane (`agent:<bot_dude_id>:spawn_<taskId>`) so a Bot Dude
   * can process long-running work without blocking its own interactive thread or other Bot Dudes.
   */
  suspend fun spawnBackgroundTask(
    host: OpenClawHost,
    bot: CompanionBot,
    taskId: String,
    prompt: String,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
    wheelOverride: WheelLlmOption? = null,
    attachmentPayloads: List<MediaAttachmentPayload> = emptyList(),
    onPartialText: (String) -> Unit = {},
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.effectiveBaseUrl
    if (cleanBase.isBlank() && !(providerKeys.bridgeEnabled && providerKeys.bridgeFolderUri.isNotBlank())) {
      return@withContext Result.failure(OpenClawFormattedException("OpenClaw host URL is not configured."))
    }

    val llm = resolveEffectiveLlm(bot, host, wheelOverride, providerKeys)
    val rawAgentId = bot.openClawAgentId.ifBlank { bot.functionPresetEnum.suggestedAgentId }
    val wireModel = resolveWireModel(rawAgentId, cleanBase)
    val backendModelLabel = resolveXOpenClawModelHeader(llm) ?: wireModel
    val cleanTask = taskId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "task" }
    val parentUserSession = sanitizeUserSessionId("conv:bot_${bot.resolvedBotDudeSlug()}")
    val subUserSession = sanitizeUserSessionId("conv:bot_${bot.resolvedBotDudeSlug()}_spawn_${cleanTask.take(28)}")
    val trackingKey = bot.effectiveSpawnSessionKey(taskId)
    val endpoint = "$cleanBase/v1/chat/completions"
    val startMs = System.currentTimeMillis()

    try {
      val contentText = buildString {
        appendLine("[ISOLATED BACKGROUND SUB-AGENT TASK | ID: $taskId | PARENT: $parentUserSession]")
        appendLine("[BOT FUNCTION: ${bot.functionPresetEnum.displayName} | MISSION: ${bot.displayMission}]")
        appendLine(prompt)
        appendAttachmentContextBlocks(attachmentPayloads)
      }
      val systemPrompt = buildString {
        appendLine(buildBotSystemPrompt(bot))
        appendLine("You are running as an isolated background sub-agent ($subUserSession) spawned from $parentUserSession.")
        append("Complete the background task thoroughly and return a clear, self-contained summary.")
      }
      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        put(JSONObject().put("role", "user").put("content", buildUserMessageContent(contentText, attachmentPayloads)))
      }
      if (providerKeys.bridgeEnabled && providerKeys.bridgeFolderUri.isNotBlank() && driveBridgeManager != null) {
        return@withContext driveBridgeManager.dispatchTaskAndAwaitResult(
          folderUriOrPath = providerKeys.bridgeFolderUri,
          host = host,
          wireModel = wireModel,
          backendModel = backendModelLabel,
          sessionKey = subUserSession,
          agentId = MEDIA_ROUTE_AGENT_ID,
          deviceId = providerKeys.deviceId,
          userPrompt = prompt,
          messagesArray = messagesArray,
          refinedInstruction = null,
          onPartialText = onPartialText,
        )
      }
      val payload = JSONObject().apply {
        put("model", wireModel)
        put("stream", true)
        put("messages", messagesArray)
        put("user", subUserSession)
      }
      val req = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .addHeader("Accept", "text/event-stream, application/json")
        .applyCommonHeaders(
          host = host,
          bot = bot,
          llm = llm,
          providerKeys = providerKeys,
          extraHeaders = mapOf(
            "x-openclaw-spawn-subagent" to "true",
            "x-openclaw-parent-session" to parentUserSession,
            "x-openclaw-subagent-task-id" to taskId,
          ),
        )
        .build()

      executeChatCompletionsWithSseFallback(
        req = req,
        cleanBase = cleanBase,
        refinedInstruction = null,
        host = host,
        fallbackModel = backendModelLabel,
        fallbackPrompt = prompt,
        sessionKey = subUserSession,
        agentId = MEDIA_ROUTE_AGENT_ID,
        deviceId = providerKeys.deviceId,
        callTrackingKey = trackingKey,
        onPartialText = onPartialText,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startMs
      Log.w(TAG, "Background sub-agent spawn failed: ${e.message}", e)
      Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
    }
  }

  suspend fun sendGeneralChat(
    host: OpenClawHost,
    userId: String,
    conversationId: String = "default",
    history: List<GeneralChatMessage>,
    userPrompt: String,
    wheelOption: WheelLlmOption,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
    attachmentPayloads: List<MediaAttachmentPayload> = emptyList(),
    onPartialText: (String) -> Unit = {},
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.effectiveBaseUrl
    if (cleanBase.isBlank() && !(providerKeys.bridgeEnabled && providerKeys.bridgeFolderUri.isNotBlank())) {
      return@withContext Result.failure(OpenClawFormattedException("OpenClaw host URL is not configured."))
    }
    val llm = resolveWheelLlm(wheelOption, host, providerKeys)
    val wireModel = resolveWireModel(APK_AGENT_ID, cleanBase)
    val backendModelLabel = resolveXOpenClawModelHeader(llm) ?: wireModel
    val threadSlug = conversationId.ifBlank { userId.ifBlank { "default" } }
    val userSessionId = sanitizeUserSessionId("conv:gen_$threadSlug")
    val endpoint = "$cleanBase/v1/chat/completions"
    val startMs = System.currentTimeMillis()

    try {
      val enrichedPrompt = buildString {
        append(userPrompt)
        appendAttachmentContextBlocks(attachmentPayloads)
      }
      val messagesArray = JSONArray().apply {
        // Keep the OpenClaw agent's native identity/SOUL; only provide outbound media formatting hint.
        put(
          JSONObject()
            .put("role", "system")
            .put("content", MEDIA_SYSTEM_INSTRUCTION)
        )
        history.takeLast(12).forEach { msg ->
          val role = if (msg.senderEnum == MessageSender.USER) "user" else "assistant"
          if (msg.rawText.isNotBlank()) {
            put(JSONObject().put("role", role).put("content", msg.rawText))
          }
        }
        put(JSONObject().put("role", "user").put("content", buildUserMessageContent(enrichedPrompt, attachmentPayloads)))
      }
      if (providerKeys.bridgeEnabled && providerKeys.bridgeFolderUri.isNotBlank() && driveBridgeManager != null) {
        return@withContext driveBridgeManager.dispatchTaskAndAwaitResult(
          folderUriOrPath = providerKeys.bridgeFolderUri,
          host = host,
          wireModel = wireModel,
          backendModel = backendModelLabel,
          sessionKey = userSessionId,
          agentId = MEDIA_ROUTE_AGENT_ID,
          deviceId = providerKeys.deviceId,
          userPrompt = userPrompt,
          messagesArray = messagesArray,
          refinedInstruction = null,
          onPartialText = onPartialText,
        )
      }
      val payload = JSONObject().apply {
        put("model", wireModel)
        put("stream", true)
        put("messages", messagesArray)
        put("user", userSessionId)
      }
      val req = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .addHeader("Accept", "text/event-stream, application/json")
        .applyWheelHeaders(
          host = host,
          llm = llm,
          providerKeys = providerKeys,
        )
        .build()

      executeChatCompletionsWithSseFallback(
        req = req,
        cleanBase = cleanBase,
        refinedInstruction = null,
        host = host,
        fallbackModel = backendModelLabel,
        fallbackPrompt = userPrompt,
        sessionKey = userSessionId,
        agentId = MEDIA_ROUTE_AGENT_ID,
        deviceId = providerKeys.deviceId,
        callTrackingKey = userSessionId,
        onPartialText = onPartialText,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startMs
      Log.w(TAG, "General chat OpenClaw dispatch failed: ${e.message}", e)
      Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
    }
  }

  /**
   * Builds the strict grounding system prompt for Captain Context so every reply is anchored
   * strictly in the user's loaded PDFs, documents, and Google Drive files.
   */
  fun buildCaptainContextSystemPrompt(docs: List<CaptainContextDocument>): String {
    val readyNames = docs.filter { it.status == ContextDocStatus.READY }.map { it.displayName }
    return buildString {
      appendLine("You are Captain Context — a sharp, helpful comic-book superhero whose sole superpower is total recall over the user's loaded documents.")
      appendLine("=== STRICT DOCUMENT GROUNDING RULES ===")
      appendLine("1. Base your answers EXCLUSIVELY on the loaded Document Vault provided below (text extracts, attached page images, or host-path references).")
      appendLine("2. Whenever you state a fact, quote a figure, summarize a clause, or trace a detail, cite the exact file inline using [Source: <filename>].")
      appendLine("3. NEVER invent facts, policy numbers, dates, quotes, or assumptions that are not present in the loaded documents.")
      appendLine("4. If the answer is NOT found in the loaded documents—or if a document is marked NEEDS_ACCESS or ERROR—state that clearly and honestly, name which document(s) were checked, and suggest what document or sharing setting is needed.")
      appendLine("5. Only inspect a Host PC Path on the host filesystem if a document entry is explicitly marked HOST_PATH.")
      if (readyNames.isNotEmpty()) {
        appendLine("Loaded Documents (${readyNames.size}): ${readyNames.joinToString(", ")}")
      } else {
        appendLine("Loaded Documents: None ready yet.")
      }
      append(MEDIA_SYSTEM_INSTRUCTION)
    }
  }

  /**
   * Fairly budgets document text across all READY context documents and explicitly flags
   * any inaccessible / failed Google Drive links so the model stays 100% honest.
   */
  fun buildCaptainContextPromptDeck(
    docs: List<CaptainContextDocument>,
    maxTotalChars: Int = 38_000,
  ): String {
    if (docs.isEmpty()) {
      return "[CONTEXT VAULT STATUS: EMPTY — No documents are currently loaded.]"
    }
    val readyDocs = docs.filter { it.status == ContextDocStatus.READY }
    val perDocBudget = if (readyDocs.isNotEmpty()) {
      maxOf(1_800, maxTotalChars / readyDocs.size)
    } else {
      maxTotalChars
    }

    return buildString {
      appendLine("=== CAPTAIN CONTEXT DOCUMENT VAULT (${docs.size} item(s)) ===")
      docs.forEachIndexed { idx, doc ->
        val num = idx + 1
        val typeTag = when {
          doc.isGoogleDriveUrl -> "GOOGLE_DRIVE"
          doc.isPdf -> "PDF"
          doc.isDocx -> "DOCX"
          doc.isImage -> "IMAGE"
          doc.linkType == FileLinkType.HOST_PATH -> "HOST_PATH"
          else -> doc.linkType.name
        }
        appendLine("[DOCUMENT #$num | Source: ${doc.displayName} | Type: $typeTag | Ref: ${doc.uriOrPath}]")
        when (doc.status) {
          ContextDocStatus.READY -> {
            val raw = doc.extractedText.trim()
            if (raw.isNotEmpty()) {
              val snippet = if (raw.length > perDocBudget) {
                raw.take(perDocBudget) + "\n...[truncated after $perDocBudget chars of ${raw.length}; ask for specific sections if needed]"
              } else {
                raw
              }
              appendLine(snippet)
            }
            if (doc.imageDataUrl.isNotBlank()) {
              appendLine("(Attached inline image: ${doc.displayName})")
            }
            if (doc.pageImageDataUrls.isNotEmpty()) {
              appendLine("(Attached ${doc.pageImageDataUrls.size} rendered scanned PDF page image(s) from ${doc.displayName})")
            }
          }
          ContextDocStatus.LOADING -> {
            appendLine("[STATUS: Still reading/downloading ${doc.displayName}.]")
          }
          ContextDocStatus.NEEDS_ACCESS -> {
            appendLine("[STATUS: NEEDS_ACCESS — ${doc.statusDetail.ifBlank { "Private Google Drive link requires 'Anyone with the link' sharing or Android Drive file picker." }}]")
          }
          ContextDocStatus.ERROR -> {
            appendLine("[STATUS: ERROR — ${doc.statusDetail.ifBlank { "Could not read document." }}]")
          }
        }
        appendLine("[END DOCUMENT #$num]")
        appendLine()
      }
      append("=== END CAPTAIN CONTEXT DOCUMENT VAULT ===")
    }.trim()
  }

  /**
   * Dispatches a document-grounded turn for Captain Context through the OpenClaw `/v1/chat/completions`
   * SSE stream (`model: "openclaw/apk"`, isolated `user: "conv:ctx_<chatId>"`), injecting the active
   * Document Vault deck + multimodal attachments on every turn so multi-turn Q&A never forgets sources.
   */
  suspend fun sendCaptainContextChat(
    host: OpenClawHost,
    userId: String,
    conversationId: String = "default",
    contextDocs: List<CaptainContextDocument>,
    history: List<ContextChatMessage>,
    userPrompt: String,
    wheelOption: WheelLlmOption,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
    onPartialText: (String) -> Unit = {},
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.effectiveBaseUrl
    if (cleanBase.isBlank() && !(providerKeys.bridgeEnabled && providerKeys.bridgeFolderUri.isNotBlank())) {
      return@withContext Result.failure(OpenClawFormattedException("OpenClaw host URL is not configured."))
    }
    val llm = resolveWheelLlm(wheelOption, host, providerKeys)
    val wireModel = resolveWireModel(APK_AGENT_ID, cleanBase)
    val backendModelLabel = resolveXOpenClawModelHeader(llm) ?: wireModel
    val threadSlug = conversationId.ifBlank { userId.ifBlank { "default" } }
    val userSessionId = sanitizeUserSessionId("conv:ctx_$threadSlug")
    val endpoint = "$cleanBase/v1/chat/completions"
    val startMs = System.currentTimeMillis()

    try {
      val systemPrompt = buildCaptainContextSystemPrompt(contextDocs)
      val contextDeck = buildCaptainContextPromptDeck(contextDocs)
      val payloads = contextDocs
        .filter { it.status == ContextDocStatus.READY }
        .map { it.toMediaAttachmentPayload() }

      val enrichedPrompt = buildString {
        appendLine(contextDeck)
        appendLine()
        appendLine("[USER QUESTION DRIVEN BY CONTEXT ABOVE]")
        append(userPrompt.trim())
      }

      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.takeLast(12).forEach { msg ->
          val txt = msg.rawText.trim()
          if (txt.isNotBlank() &&
            msg.statusEnum != com.example.model.MessageStatus.PARSING &&
            msg.statusEnum != com.example.model.MessageStatus.DISPATCHED
          ) {
            val role = if (msg.senderEnum == MessageSender.USER) "user" else "assistant"
            put(JSONObject().put("role", role).put("content", txt.take(3000)))
          }
        }
        put(JSONObject().put("role", "user").put("content", buildUserMessageContent(enrichedPrompt, payloads)))
      }

      if (providerKeys.bridgeEnabled && providerKeys.bridgeFolderUri.isNotBlank() && driveBridgeManager != null) {
        return@withContext driveBridgeManager.dispatchTaskAndAwaitResult(
          folderUriOrPath = providerKeys.bridgeFolderUri,
          host = host,
          wireModel = wireModel,
          backendModel = backendModelLabel,
          sessionKey = userSessionId,
          agentId = MEDIA_ROUTE_AGENT_ID,
          deviceId = providerKeys.deviceId,
          userPrompt = userPrompt,
          messagesArray = messagesArray,
          refinedInstruction = null,
          onPartialText = onPartialText,
        )
      }

      val payload = JSONObject().apply {
        put("model", wireModel)
        put("stream", true)
        put("messages", messagesArray)
        put("user", userSessionId)
      }
      val req = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .addHeader("Accept", "text/event-stream, application/json")
        .applyWheelHeaders(
          host = host,
          llm = llm,
          providerKeys = providerKeys,
        )
        .build()

      executeChatCompletionsWithSseFallback(
        req = req,
        cleanBase = cleanBase,
        refinedInstruction = null,
        host = host,
        fallbackModel = backendModelLabel,
        fallbackPrompt = userPrompt,
        sessionKey = userSessionId,
        agentId = MEDIA_ROUTE_AGENT_ID,
        deviceId = providerKeys.deviceId,
        callTrackingKey = userSessionId,
        onPartialText = onPartialText,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startMs
      Log.w(TAG, "Captain Context OpenClaw dispatch failed: ${e.message}", e)
      Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
    }
  }

  private suspend fun executeChatCompletionsWithSseFallback(
    req: Request,
    cleanBase: String,
    refinedInstruction: String?,
    host: OpenClawHost,
    fallbackModel: String,
    fallbackPrompt: String,
    sessionKey: String,
    agentId: String,
    deviceId: String = MediaSaveHelper.DEFAULT_DEVICE_ID,
    callTrackingKey: String = sessionKey,
    onPartialText: (String) -> Unit,
  ): Result<OpenClawDispatchResult> {
    var activeReq = req
    var activeBase = cleanBase
    var activeHost = host
    var triedFallbackHost = false
    var serverRetriesLeft = 1

    // Best-effort pre-flight TCP check: only switches early when a reachable fallback exists;
    // otherwise lets the real OkHttp request (60s connect timeout) proceed.
    if (FAST_PROBE_ENABLED) {
      val primaryTcpErr = probeTcpReachable(activeBase, FAST_TCP_PROBE_TIMEOUT_MS)
      if (primaryTcpErr != null) {
        val fallbackBase = host.fallbackBaseUrl
        if (fallbackBase.isNotBlank() && probeTcpReachable(fallbackBase, FAST_TCP_PROBE_TIMEOUT_MS) == null) {
          triedFallbackHost = true
          val oldUrl = activeReq.url.toString()
          val newUrl = if (oldUrl.startsWith(activeBase, ignoreCase = true)) {
            fallbackBase + oldUrl.substring(activeBase.length)
          } else {
            "$fallbackBase/v1/chat/completions"
          }
          activeBase = fallbackBase
          activeHost = host.copy(
            baseUrl = fallbackBase,
            lanUrl = fallbackBase,
            tailscaleUrl = host.effectiveTailscaleUrl,
          )
          activeReq = activeReq.newBuilder().url(newUrl).build()
        } else {
          Log.w(TAG, "Pre-flight TCP probe failed for $activeBase (${primaryTcpErr.message}); proceeding with OkHttp request")
        }
      }
    }

    while (true) {
      val attemptStartMs = System.currentTimeMillis()
      val call = client.newCall(activeReq)
      activeCallsBySession[callTrackingKey] = call
      var streamedAnyText = false

      try {
        val attemptResult: Result<OpenClawDispatchResult>? = call.execute().use { resp ->
          if (!resp.isSuccessful) {
            val errBody = resp.body?.string().orEmpty()
            val formattedErr = formatHttpErrorMessage(resp.code, errBody, activeBase)
            // Section 9: HTTP 500 -> retry once, then surface structured error.message
            if (resp.code == 500 && serverRetriesLeft > 0) {
              serverRetriesLeft--
              return@use null
            }
            return@use Result.failure(
              OpenClawFormattedException(
                formattedErr,
                isFastConnectFailure = false,
              )
            )
          }

          val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.US)
          val body = resp.body
            ?: return@use Result.failure(OpenClawFormattedException("Empty response from OpenClaw gateway."))

          if (contentType.contains("text/event-stream")) {
            val source = body.source()
            // OpenClaw /v1/chat/completions sends zero keepalive pings during tool execution;
            // allow long silent mid-stream gaps (>= 180s idle per read, no total call deadline).
            source.timeout().clearDeadline()
            source.timeout().timeout(SSE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val textBuilder = StringBuilder()
            var structuredMediaUrl: String? = null
            var structuredMediaType: GeneratedMediaType? = null
            var streamModel = fallbackModel

            while (true) {
              val line = source.readUtf8Line() ?: break
              val trimmed = line.trim()
              if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
              if (!trimmed.startsWith("data:")) continue
              val dataStr = trimmed.removePrefix("data:").trim()
              if (dataStr == "[DONE]") break

              val chunkJson = runCatching { JSONObject(dataStr) }.getOrNull() ?: continue
              chunkJson.optJSONObject("error")?.let { errObj ->
                val errMsg = errObj.optString("message").ifBlank { errObj.optString("code") }
                val errType = errObj.optString("type").lowercase(Locale.US)
                if (errMsg.isNotBlank()) {
                  val isRetryableServerErr = !streamedAnyText && serverRetriesLeft > 0 &&
                    (errMsg.contains("internal error", ignoreCase = true) ||
                      errType.contains("api_error") ||
                      errType.contains("server_error"))
                  if (isRetryableServerErr) {
                    serverRetriesLeft--
                    return@use null
                  }
                  return@use Result.failure(
                    OpenClawFormattedException(
                      cleanRawErrorText(errMsg, 0, activeBase),
                      isFastConnectFailure = false,
                    )
                  )
                }
              }
              chunkJson.optString("model").takeIf { it.isNotBlank() }?.let { streamModel = it }
              val choices = chunkJson.optJSONArray("choices")
              if (choices != null && choices.length() > 0) {
                val c0 = choices.getJSONObject(0)
                val delta = c0.optJSONObject("delta") ?: c0.optJSONObject("message")
                val deltaText = if (delta != null && !delta.isNull("content")) {
                  delta.optString("content", "")
                } else {
                  ""
                }
                if (deltaText.isNotEmpty()) {
                  streamedAnyText = true
                  textBuilder.append(deltaText)
                  onPartialText(stripInlineBase64MarkdownForPreview(textBuilder.toString()))
                }
                val chunkMediaUrl = delta?.optString("image_url")?.takeIf { it.isNotBlank() }
                  ?: delta?.optString("media_url")?.takeIf { it.isNotBlank() }
                  ?: chunkJson.optString("media_url").takeIf { it.isNotBlank() }
                if (structuredMediaUrl == null && chunkMediaUrl != null) {
                  structuredMediaUrl = chunkMediaUrl
                }
              }
            }

            val fullStreamed = textBuilder.toString()
            val syntheticJson = JSONObject().apply {
              put("model", streamModel)
              put(
                "choices",
                JSONArray().put(
                  JSONObject().put(
                    "message",
                    JSONObject().put("content", fullStreamed)
                  )
                )
              )
              if (!structuredMediaUrl.isNullOrBlank()) {
                put("media_url", structuredMediaUrl)
              }
              if (structuredMediaType != null) {
                put("media_type", structuredMediaType.name)
              }
            }
            val parsed = extractReplyFromOpenAiCompatibleJson(
              rawJson = syntheticJson.toString(),
              refinedInstruction = refinedInstruction,
              host = activeHost,
              fallbackModel = streamModel,
              fallbackPrompt = fallbackPrompt,
              sessionKey = sessionKey,
              agentId = agentId,
              deviceId = deviceId,
            )
            Result.success(parsed)
          } else {
            val bodyStr = body.string()
            if (bodyStr.trimStart().startsWith("data:")) {
              // Gateway returned SSE chunks without text/event-stream Content-Type header
              val (extracted, sseError) = parseSsePayloadString(bodyStr)
              if (sseError != null) {
                if (serverRetriesLeft > 0 && sseError.contains("internal error", ignoreCase = true)) {
                  serverRetriesLeft--
                  return@use null
                }
                return@use Result.failure(
                  OpenClawFormattedException(
                    cleanRawErrorText(sseError, 0, activeBase),
                    isFastConnectFailure = false,
                  )
                )
              }
              val parsed = extractReplyFromOpenAiCompatibleJson(
                rawJson = JSONObject().put(
                  "choices",
                  JSONArray().put(JSONObject().put("message", JSONObject().put("content", extracted)))
                ).toString(),
                refinedInstruction = refinedInstruction,
                host = activeHost,
                fallbackModel = fallbackModel,
                fallbackPrompt = fallbackPrompt,
                sessionKey = sessionKey,
                agentId = agentId,
                deviceId = deviceId,
              )
              Result.success(parsed)
            } else {
              val parsed = extractReplyFromOpenAiCompatibleJson(
                rawJson = bodyStr,
                refinedInstruction = refinedInstruction,
                host = activeHost,
                fallbackModel = fallbackModel,
                fallbackPrompt = fallbackPrompt,
                sessionKey = sessionKey,
                agentId = agentId,
                deviceId = deviceId,
              )
              Result.success(parsed)
            }
          }
        }

        if (attemptResult != null) {
          return attemptResult
        }
        // Retry once after brief backoff (HTTP 500 or retryable SSE server error)
        delay(350L)
      } catch (e: CancellationException) {
        throw e
      } catch (e: IOException) {
        val elapsed = System.currentTimeMillis() - attemptStartMs
        val isUserCanceled = call.isCanceled() || e.message?.contains("Canceled", ignoreCase = true) == true
        if (isUserCanceled) {
          return Result.failure(formatNetworkThrowable(e, activeBase, elapsed))
        }

        val connectTimeoutThresholdMs = SSE_CONNECT_TIMEOUT_SECONDS * 1000L + 2_000L
        val isConnectTimeout = e is SocketTimeoutException &&
          !streamedAnyText &&
          (e.message?.contains("connect", ignoreCase = true) == true || elapsed <= connectTimeoutThresholdMs)
        val isConnectUnreachable = e is ConnectException ||
          e is NoRouteToHostException ||
          e is UnknownHostException ||
          isConnectTimeout

        // Section 1: Automatic LAN -> Tailscale (or secondary) failover if primary host is unreachable
        val fallbackBase = host.fallbackBaseUrl
        if (isConnectUnreachable && !triedFallbackHost && fallbackBase.isNotBlank() &&
          (!FAST_PROBE_ENABLED || probeTcpReachable(fallbackBase, FAST_TCP_PROBE_TIMEOUT_MS) == null)
        ) {
          triedFallbackHost = true
          val oldUrl = activeReq.url.toString()
          val newUrl = if (oldUrl.startsWith(activeBase, ignoreCase = true)) {
            fallbackBase + oldUrl.substring(activeBase.length)
          } else {
            "$fallbackBase/v1/chat/completions"
          }
          activeBase = fallbackBase
          activeHost = host.copy(
            baseUrl = fallbackBase,
            lanUrl = fallbackBase,
            tailscaleUrl = host.effectiveTailscaleUrl,
          )
          activeReq = activeReq.newBuilder().url(newUrl).build()
          continue
        }

        // Section 10: On ClientDisconnectError / socket abort (not a full 180s read timeout) -> evict stale pool & retry once
        val isLongReadTimeout = e is SocketTimeoutException && !isConnectTimeout && elapsed > connectTimeoutThresholdMs
        if (!isLongReadTimeout && !streamedAnyText && serverRetriesLeft > 0) {
          serverRetriesLeft--
          runCatching { client.connectionPool.evictAll() }
          delay(350L)
          continue
        }

        return Result.failure(formatNetworkThrowable(e, activeBase, elapsed))
      } finally {
        activeCallsBySession.remove(callTrackingKey, call)
      }
    }
  }

  private fun parseSsePayloadString(rawSse: String): Pair<String, String?> {
    val sb = StringBuilder()
    var sseError: String? = null
    rawSse.lineSequence().forEach { line ->
      val trimmed = line.trim()
      if (!trimmed.startsWith("data:")) return@forEach
      val dataStr = trimmed.removePrefix("data:").trim()
      if (dataStr == "[DONE]") return@forEach
      val chunkJson = runCatching { JSONObject(dataStr) }.getOrNull() ?: return@forEach
      chunkJson.optJSONObject("error")?.let { errObj ->
        val msg = errObj.optString("message").ifBlank { errObj.optString("code") }
        if (msg.isNotBlank() && sseError == null) {
          sseError = msg
        }
      }
      val c0 = chunkJson.optJSONArray("choices")?.optJSONObject(0) ?: return@forEach
      val delta = c0.optJSONObject("delta") ?: c0.optJSONObject("message")
      val text = if (delta != null && !delta.isNull("content")) {
        delta.optString("content", "")
      } else {
        ""
      }
      if (text.isNotEmpty()) {
        sb.append(text)
      }
    }
    return sb.toString() to sseError
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
    val cleanBase = host.effectiveBaseUrl
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(OpenClawFormattedException("OpenClaw host URL is not configured."))
    }
    val llm = resolveWheelLlm(wheelOption, host, providerKeys)
    val wireModel = resolveWireModel(APK_AGENT_ID, cleanBase)
    val backendModelLabel = resolveXOpenClawModelHeader(llm) ?: wireModel
    val userSessionId = sanitizeUserSessionId("conv:room_${room.id.ifBlank { "default" }}")
    val endpoint = "$cleanBase/v1/chat/completions"
    val startMs = System.currentTimeMillis()
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
        put("model", wireModel)
        put("stream", true)
        put("messages", messagesArray)
        put("user", userSessionId)
      }
      val req = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .addHeader("Accept", "text/event-stream, application/json")
        .applyWheelHeaders(host, llm, providerKeys)
        .build()

      executeChatCompletionsWithSseFallback(
        req = req,
        cleanBase = cleanBase,
        refinedInstruction = null,
        host = host,
        fallbackModel = backendModelLabel,
        fallbackPrompt = userPrompt,
        sessionKey = userSessionId,
        agentId = MEDIA_ROUTE_AGENT_ID,
        deviceId = providerKeys.deviceId,
        callTrackingKey = userSessionId,
        onPartialText = {},
      ).map { parsed ->
        val turns = parseChatRoomTurnsFromText(parsed.replyText.trim(), participants)
        ChatRoomRoundResult(turns)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startMs
      Log.w(TAG, "ChatRoom OpenClaw turn failed: ${e.message}", e)
      Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
    }
  }

  /**
   * Executes ONE structured A2A turn in Cafe Mode for a single Bot Dude (`speakerBot`),
   * preserving its own isolated session key (`agent:<slug>:cafe_<roomId>`), persona,
   * tool permissions, and model override (`x-openclaw-model`, e.g., `ollama/translategemma` vs `venice/...`).
   */
  suspend fun dispatchCafeBotTurn(
    host: OpenClawHost,
    room: ChatRoom,
    speakerBot: CompanionBot,
    participants: List<CompanionBot>,
    transcript: List<CafeTranscriptEntry>,
    replyToBotName: String? = null,
    wheelOption: WheelLlmOption? = null,
    providerKeys: LocalProviderKeys = LocalProviderKeys(),
    onPartialText: (String) -> Unit = {},
  ): Result<OpenClawDispatchResult> = withContext(Dispatchers.IO) {
    val cleanBase = host.effectiveBaseUrl
    if (cleanBase.isBlank()) {
      return@withContext Result.failure(OpenClawFormattedException("OpenClaw host URL is not configured."))
    }

    val llm = resolveEffectiveLlm(speakerBot, host, wheelOption, providerKeys)
    val rawAgentId = speakerBot.openClawAgentId.ifBlank { speakerBot.functionPresetEnum.suggestedAgentId }
    val wireModel = resolveWireModel(rawAgentId, cleanBase)
    val backendModelLabel = resolveXOpenClawModelHeader(llm) ?: wireModel
    val cleanRoom = room.id.trim().replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "default" }
    val userSessionId = sanitizeUserSessionId("conv:bot_${speakerBot.resolvedBotDudeSlug()}_cafe_${cleanRoom.take(28)}")
    val trackingKey = speakerBot.effectiveCafeSessionKey(room.id)
    val endpoint = "$cleanBase/v1/chat/completions"
    val startMs = System.currentTimeMillis()

    try {
      val peerRoster = participants.joinToString("\n") { peer ->
        val roleDesc = peer.functionPresetEnum.displayName
        val tag = if (peer.id == speakerBot.id) " (YOU)" else ""
        "- ${peer.name}$tag — $roleDesc: \"${peer.displayMission}\""
      }
      val systemPrompt = buildString {
        appendLine(buildBotSystemPrompt(speakerBot))
        appendLine()
        appendLine("=== CAFE MODE MULTI-AGENT ROOM ===")
        appendLine("Room: ${room.title}")
        if (room.topic.isNotBlank()) appendLine("Focus: ${room.topic}")
        appendLine("Room Crew:")
        appendLine(peerRoster)
        appendLine("Rules for your turn:")
        appendLine("1. Speak ONLY as ${speakerBot.name} in natural prose (never return JSON arrays or write dialogue for other bots).")
        appendLine("2. Read prior attributed turns ([Name]: message), build directly on them according to your role (${speakerBot.functionPresetEnum.displayName}), and keep your turn crisp and helpful.")
        if (!replyToBotName.isNullOrBlank()) {
          appendLine("3. Address @$replyToBotName's latest point where relevant.")
        }
        appendLine("4. When you judge the room goal is fully satisfied (nothing left to fix or translate), end your reply with the exact token [[DONE]] on its own final line. Otherwise omit it.")
      }

      val messagesArray = JSONArray().apply {
        put(JSONObject().put("role", "system").put("content", systemPrompt))
        val window = selectTranscriptWindow(transcript, speakerBot.id, speakerBot.name)
        if (window.isEmpty()) {
          put(
            JSONObject()
              .put("role", "user")
              .put("content", "[User]: Start collaborating on room focus: ${room.topic.ifBlank { room.title }}")
          )
        } else {
          window.forEach { entry ->
            val isSelf = entry.role == CafeSpeakerRole.BOT &&
              (entry.speakerId == speakerBot.id || entry.speakerName.equals(speakerBot.name, ignoreCase = true))
            val role = if (isSelf) "assistant" else "user"
            val formatted = if (isSelf) {
              entry.content
            } else {
              val targetTag = entry.replyToBotName?.let { " -> @$it" } ?: ""
              "[${entry.speakerName}$targetTag]: ${entry.content}"
            }
            put(JSONObject().put("role", role).put("content", formatted.take(3000)))
          }
          val last = window.last()
          val lastWasSelf = last.role == CafeSpeakerRole.BOT &&
            (last.speakerId == speakerBot.id || last.speakerName.equals(speakerBot.name, ignoreCase = true))
          if (lastWasSelf) {
            put(
              JSONObject()
                .put("role", "user")
                .put("content", "[Room Moderator]: Your turn again, ${speakerBot.name}. Continue or summarize your contribution.")
            )
          }
        }
      }

      if (providerKeys.bridgeEnabled && providerKeys.bridgeFolderUri.isNotBlank() && driveBridgeManager != null) {
        return@withContext driveBridgeManager.dispatchTaskAndAwaitResult(
          folderUriOrPath = providerKeys.bridgeFolderUri,
          host = host,
          wireModel = wireModel,
          backendModel = backendModelLabel,
          sessionKey = userSessionId,
          agentId = MEDIA_ROUTE_AGENT_ID,
          deviceId = providerKeys.deviceId,
          userPrompt = transcript.lastOrNull()?.content ?: room.topic,
          messagesArray = messagesArray,
          refinedInstruction = null,
          onPartialText = onPartialText,
        ).map { parsed ->
          val prefixPattern = Regex("^\\[${Regex.escape(speakerBot.name)}(?:\\s*->[^\\]]+)?\\]\\s*:?\\s*", RegexOption.IGNORE_CASE)
          val cleaned = parsed.replyText.replaceFirst(prefixPattern, "").trim().ifBlank { parsed.replyText }
          parsed.copy(replyText = cleaned, mediaModel = backendModelLabel)
        }
      }

      val payload = JSONObject().apply {
        put("model", wireModel)
        put("stream", true)
        put("messages", messagesArray)
        put("user", userSessionId)
      }

      val extraHeaders = buildMap {
        put("x-openclaw-a2a-room", room.id.ifBlank { "default" })
        if (!replyToBotName.isNullOrBlank()) {
          put("x-openclaw-a2a-reply-to", replyToBotName)
        }
      }

      val req = Request.Builder()
        .url(endpoint)
        .post(payload.toString().toRequestBody(jsonMediaType))
        .addHeader("Accept", "text/event-stream, application/json")
        .applyCommonHeaders(
          host = host,
          bot = speakerBot,
          llm = llm,
          providerKeys = providerKeys,
          extraHeaders = extraHeaders,
        )
        .build()

      executeChatCompletionsWithSseFallback(
        req = req,
        cleanBase = cleanBase,
        refinedInstruction = null,
        host = host,
        fallbackModel = backendModelLabel,
        fallbackPrompt = transcript.lastOrNull()?.content ?: room.topic,
        sessionKey = userSessionId,
        agentId = MEDIA_ROUTE_AGENT_ID,
        deviceId = providerKeys.deviceId,
        callTrackingKey = trackingKey,
        onPartialText = onPartialText,
      ).map { parsed ->
        // Strip leading "[BotName]: " if an LLM echoes the attribution prefix
        val prefixPattern = Regex("^\\[${Regex.escape(speakerBot.name)}(?:\\s*->[^\\]]+)?\\]\\s*:?\\s*", RegexOption.IGNORE_CASE)
        val hadDone = Regex("\\[\\[DONE\\]\\]", RegexOption.IGNORE_CASE).containsMatchIn(parsed.replyText)
        val stripped = parsed.replyText.replace(Regex("\\s*\\[\\[DONE\\]\\]\\s*", RegexOption.IGNORE_CASE), " ")
        val cleaned = stripped.replaceFirst(prefixPattern, "").trim().ifBlank { parsed.replyText }
        parsed.copy(
          replyText = cleaned,
          mediaModel = backendModelLabel,
          roomGoalSatisfied = hadDone,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val elapsed = System.currentTimeMillis() - startMs
      Log.w(TAG, "Cafe turn dispatch failed for ${speakerBot.name}: ${e.message}", e)
      Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
    }
  }

  /**
   * OpenClaw 2026.11.6 control dashboard runs on root (`/?token=...`), and `/hooks/agent` does not
   * expose built-in `takeover.*`, `agent.pause`, or `task.abort` routes (they HTTP 404).
   * - Take Over / Hand Back / Pause / Resume update local session & queue state cleanly without 404s.
   * - Abort immediately cancels any active OkHttp Call and coroutine job.
   * - Direct commands/keys/snapshots route through `/v1/chat/completions`.
   */
  suspend fun sendControlAction(
    host: OpenClawHost,
    action: HostControlAction,
    payloadValue: String = "",
  ): Result<HostControlResponse> = withContext(Dispatchers.IO) {
    val cleanBase = host.effectiveBaseUrl
    val dashboardUrl = host.resolvedDashboardUrl.ifBlank { cleanBase }

    when (action) {
      HostControlAction.ABORT_TASK -> {
        abortActiveRequest()
        return@withContext Result.success(
          HostControlResponse(
            accepted = true,
            summary = "Cancelled in-flight OpenClaw stream & reset queue cleanly.",
            endpointTried = cleanBase,
          )
        )
      }
      HostControlAction.PAUSE_AGENT -> {
        return@withContext Result.success(
          HostControlResponse(
            accepted = true,
            summary = "Queue paused — outgoing automated dispatches held until Resumed.",
            endpointTried = cleanBase,
          )
        )
      }
      HostControlAction.RESUME_AGENT -> {
        return@withContext Result.success(
          HostControlResponse(
            accepted = true,
            summary = "Queue resumed — bots & General Chat ready to dispatch.",
            endpointTried = cleanBase,
          )
        )
      }
      HostControlAction.TAKEOVER_START,
      HostControlAction.TAKEOVER_RELEASE -> {
        if (cleanBase.isBlank()) {
          return@withContext Result.failure(OpenClawFormattedException("Host URL is empty."))
        }
        val isStart = action == HostControlAction.TAKEOVER_START
        val prompt = if (isStart) {
          "Enter host takeover mode: stop starting any new automated work, finish nothing new, and wait for direct input. " +
            "Reply with one short line confirming you have paused and are awaiting manual control."
        } else {
          "Leave host takeover mode: resume normal automated operation from the current screen state. " +
            "Reply with one short line confirming you have resumed."
        }
        val endpoint = "$cleanBase/v1/chat/completions"
        val wireModel = resolveWireModel(APK_AGENT_ID, cleanBase)
        val userSessionId = sanitizeUserSessionId("conv:takeover_${host.id.ifBlank { "main" }}")
        val llm = resolveWheelLlm(WheelLlmOption.OPENCLAW_DEFAULT, host)
        val startMs = System.currentTimeMillis()
        try {
          val messagesArray = JSONArray().apply {
            put(
              JSONObject()
                .put("role", "system")
                .put("content", "You are OpenClaw host control assistant. Execute or report cleanly.")
            )
            put(JSONObject().put("role", "user").put("content", prompt))
          }
          val bodyJson = JSONObject().apply {
            put("model", wireModel)
            put("stream", true)
            put("user", userSessionId)
            put("messages", messagesArray)
          }
          val req = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .addHeader("Accept", "text/event-stream, application/json")
            .applyWheelHeaders(host, llm, LocalProviderKeys())
            .build()
          executeChatCompletionsWithSseFallback(
            req = req,
            cleanBase = cleanBase,
            refinedInstruction = null,
            host = host,
            fallbackModel = resolveXOpenClawModelHeader(llm) ?: wireModel,
            fallbackPrompt = prompt,
            sessionKey = userSessionId,
            agentId = MEDIA_ROUTE_AGENT_ID,
            callTrackingKey = userSessionId,
            onPartialText = {},
          ).map { parsed ->
            HostControlResponse(
              accepted = true,
              summary = parsed.replyText.take(240).ifBlank {
                if (isStart) "Host takeover engaged." else "Control handed back."
              },
              endpointTried = endpoint,
            )
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          val elapsed = System.currentTimeMillis() - startMs
          Log.w(TAG, "Host takeover toggle failed: ${e.message}", e)
          Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
        }
      }
      HostControlAction.SEND_KEYS,
      HostControlAction.SCREEN_SNAPSHOT,
      HostControlAction.OPEN_URL,
      HostControlAction.RUN_COMMAND -> {
        if (cleanBase.isBlank()) {
          return@withContext Result.failure(OpenClawFormattedException("Host URL is empty."))
        }
        val prompt = when (action) {
          HostControlAction.SEND_KEYS ->
            "On the active host session, send/type the following input or command: $payloadValue"
          HostControlAction.SCREEN_SNAPSHOT ->
            "Capture a fresh screenshot of the current host screen and output it as ![screenshot](data:image/png;base64,...) or MEDIA:/absolute/path/to/file.png on its own line."
          HostControlAction.RUN_COMMAND ->
            payloadValue.ifBlank { "Report current host screen and active process status." }
          HostControlAction.OPEN_URL ->
            "Open this URL in the active Chrome window: $payloadValue . Leave the existing tab, " +
              "open it in a new tab, and reply with one short line confirming the page loaded."
        }
        val endpoint = "$cleanBase/v1/chat/completions"
        val wireModel = resolveWireModel(APK_AGENT_ID, cleanBase)
        val userSessionId = sanitizeUserSessionId("conv:ctrl_${host.id.ifBlank { "main" }}")
        val llm = resolveWheelLlm(WheelLlmOption.OPENCLAW_DEFAULT, host)
        val startMs = System.currentTimeMillis()
        try {
          val messagesArray = JSONArray().apply {
            put(
              JSONObject()
                .put("role", "system")
                .put("content", "You are OpenClaw host control assistant. Execute or report cleanly.\n$MEDIA_SYSTEM_INSTRUCTION")
            )
            put(JSONObject().put("role", "user").put("content", prompt))
          }
          val bodyJson = JSONObject().apply {
            put("model", wireModel)
            put("stream", true)
            put("user", userSessionId)
            put("messages", messagesArray)
          }
          val req = Request.Builder()
            .url(endpoint)
            .post(bodyJson.toString().toRequestBody(jsonMediaType))
            .addHeader("Accept", "text/event-stream, application/json")
            .applyWheelHeaders(host, llm, LocalProviderKeys())
            .build()
          executeChatCompletionsWithSseFallback(
            req = req,
            cleanBase = cleanBase,
            refinedInstruction = null,
            host = host,
            fallbackModel = resolveXOpenClawModelHeader(llm) ?: wireModel,
            fallbackPrompt = prompt,
            sessionKey = userSessionId,
            agentId = MEDIA_ROUTE_AGENT_ID,
            callTrackingKey = userSessionId,
            onPartialText = {},
          ).map { parsed ->
            HostControlResponse(
              accepted = true,
              summary = parsed.replyText.take(240),
              endpointTried = endpoint,
            )
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          val elapsed = System.currentTimeMillis() - startMs
          Log.w(TAG, "Host control command failed: ${e.message}", e)
          Result.failure(formatNetworkThrowable(e, cleanBase, elapsed))
        }
      }
    }
  }

  private fun buildUserMessageContent(
    text: String,
    payloads: List<MediaAttachmentPayload>,
  ): Any {
    val allImageDataUrls = buildList {
      payloads.forEach { att ->
        if (att.imageDataUrl.isNotBlank()) add(att.imageDataUrl)
        att.pageImageDataUrls.forEach { if (it.isNotBlank()) add(it) }
      }
    }.take(8)
    if (allImageDataUrls.isEmpty()) return text
    return JSONArray().apply {
      put(JSONObject().put("type", "text").put("text", text))
      allImageDataUrls.forEach { url ->
        put(
          JSONObject()
            .put("type", "image_url")
            .put("image_url", JSONObject().put("url", url))
        )
      }
    }
  }

  private fun StringBuilder.appendAttachmentContextBlocks(payloads: List<MediaAttachmentPayload>) {
    payloads.forEach { att ->
      appendLine()
      val isDocxFile = att.isDocx || att.displayName.endsWith(".docx", ignoreCase = true)
      if (isDocxFile) {
        appendLine("[Attached DOCX: ${att.displayName}]")
      } else {
        appendLine("[Attached File: ${att.displayName}] (${att.mimeType.ifBlank { "file" }})")
      }
      if (att.uploadedHostId.isNotBlank()) {
        appendLine("Host Upload Path / Reference: ${att.uploadedHostId}")
        if (isDocxFile) {
          appendLine("Note: Uploaded DOCX is available on the host filesystem for inspection/editing with python-docx or pandoc.")
        }
      }
      if (att.imageDataUrl.isNotBlank()) {
        appendLine("Inline Image Attachment: Sent as multimodal image_url payload.")
      }
      if (att.pageImageDataUrls.isNotEmpty()) {
        appendLine("Scanned PDF Pages: Rendered ${att.pageImageDataUrls.size} page image(s) as multimodal image_url payloads.")
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
        val kind = when {
          att.isImage -> "image"
          att.isPdf -> "pdf"
          att.isDocx -> "docx"
          else -> att.linkType.name.lowercase(Locale.US)
        }
        put(
          JSONObject().apply {
            put("name", att.displayName)
            put("uriOrPath", att.uriOrPath)
            put("mimeType", att.mimeType)
            put("kind", kind)
            if (att.isImage) put("isImage", true)
            if (att.uploadedHostId.isNotBlank()) {
              put("hostFileId", att.uploadedHostId)
              put("hostPath", att.uploadedHostId)
            }
            if (att.extractedContent.isNotBlank()) put("content", att.extractedContent)
            if (att.imageDataUrl.isNotBlank()) put("dataUrl", att.imageDataUrl)
            if (att.fileBase64.isNotBlank()) put("base64", att.fileBase64)
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

  /**
   * Section 2: The JSON `model` field MUST ALWAYS be `"openclaw/apk"` so the gateway routes
   * cleanly to the dedicated `apk` agent without HTTP 400 multi-agent ambiguity or invalid model errors.
   */
  private fun resolveWireModel(
    @Suppress("UNUSED_PARAMETER") agentId: String,
    @Suppress("UNUSED_PARAMETER") cleanBase: String,
  ): String = APK_WIRE_MODEL

  /**
   * Section 3 (Option A): Never send reserved namespaces (`cron:`, `subagent:`, `acp:`, `agent:main:`)
   * as the `user` session key. Always produce a clean `conv:...` identifier.
   */
  private fun sanitizeUserSessionId(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "conv:default"
    val lower = trimmed.lowercase(Locale.US)
    val isReserved = lower.startsWith("cron:") ||
      lower.startsWith("subagent:") ||
      lower.startsWith("acp:") ||
      lower.startsWith("agent:")
    if (isReserved) {
      val clean = trimmed.replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_').ifBlank { "default" }
      return "conv:$clean"
    }
    return trimmed
  }

  /**
   * Section 2 & 11: Steer the backend provider/model using `x-openclaw-model: <provider/model>`.
   */
  private fun resolveXOpenClawModelHeader(llm: EffectiveLlmConfig): String? {
    val raw = llm.modelId.trim()
    if (raw.isBlank() ||
      raw.equals("local-default", ignoreCase = true) ||
      raw.equals("openclaw", ignoreCase = true) ||
      raw.equals(APK_WIRE_MODEL, ignoreCase = true)
    ) {
      return null
    }
    if (raw.contains('/')) return raw
    return when (llm.provider) {
      OpenClawLlmProvider.VENICE_AI -> "venice/$raw"
      OpenClawLlmProvider.GOOGLE -> "google/$raw"
      OpenClawLlmProvider.LOCAL_LLM -> "ollama/$raw"
      OpenClawLlmProvider.OPENCLAW_AUTO,
      OpenClawLlmProvider.HOST_DEFAULT -> raw
    }
  }

  /**
   * Section 3 (Option A — CRITICAL): Do NOT send `x-openclaw-session-key` header.
   * Session continuity is handled exclusively via the JSON body `"user": "conv:<thread-id>"` field,
   * preventing HTTP 500 `internal error` on multi-agent OpenClaw gateways.
   */
  private fun Request.Builder.applyWheelHeaders(
    host: OpenClawHost,
    llm: EffectiveLlmConfig,
    providerKeys: LocalProviderKeys,
    extraHeaders: Map<String, String> = emptyMap(),
  ): Request.Builder {
    addHeader("x-openclaw-llm-provider", llm.provider.wireKey)
    val cleanDeviceId = providerKeys.deviceId.trim().ifBlank { MediaSaveHelper.DEFAULT_DEVICE_ID }
    addHeader("x-openclaw-device-id", cleanDeviceId)
    addHeader("x-openclaw-client", MediaSaveHelper.DEFAULT_DEVICE_ID)
    resolveXOpenClawModelHeader(llm)?.let { backendModel ->
      addHeader("x-openclaw-model", backendModel)
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
    extraHeaders.forEach { (k, v) ->
      if (k.isNotBlank() && v.isNotBlank() &&
        !k.equals("x-openclaw-session-key", ignoreCase = true) &&
        !k.equals("x-openclaw-agent-id", ignoreCase = true)
      ) {
        addHeader(k, v)
      }
    }
    return this
  }

  private fun Request.Builder.applyCommonHeaders(
    host: OpenClawHost,
    bot: CompanionBot,
    llm: EffectiveLlmConfig,
    providerKeys: LocalProviderKeys,
    extraHeaders: Map<String, String> = emptyMap(),
  ): Request.Builder {
    if (bot.id.isNotBlank()) {
      addHeader("x-openclaw-bot-id", bot.id)
    }
    addHeader("x-openclaw-bot-function", bot.functionPresetEnum.name.lowercase())
    val perms = bot.effectiveToolPermissions
    if (perms.isNotEmpty()) {
      addHeader("x-openclaw-tool-permissions", perms.joinToString(","))
    }
    addHeader("x-openclaw-tool-scope", "enforced")
    val wsPath = bot.workspacePath.trim()
    if (wsPath.isNotBlank()) {
      addHeader("x-openclaw-workspace", wsPath)
    }
    return applyWheelHeaders(host, llm, providerKeys, extraHeaders = extraHeaders)
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

  private fun extractReplyFromOpenAiCompatibleJson(
    rawJson: String,
    refinedInstruction: String?,
    host: OpenClawHost? = null,
    fallbackModel: String = "",
    fallbackPrompt: String = "",
    sessionKey: String = "conv:main",
    agentId: String = MEDIA_ROUTE_AGENT_ID,
    deviceId: String = MediaSaveHelper.DEFAULT_DEVICE_ID,
  ): OpenClawDispatchResult {
    return try {
      val json = JSONObject(rawJson)
      // Check if server returned an embedded JSON error inside a 200 payload
      json.optJSONObject("error")?.let { errObj ->
        val errMsg = errObj.optString("message").ifBlank { errObj.optString("code") }
        if (errMsg.isNotBlank()) {
          return OpenClawDispatchResult(
            replyText = cleanRawErrorText(errMsg, 0, host?.effectiveBaseUrl.orEmpty()),
            parsedInstruction = refinedInstruction,
            expression = BotExpression.WORRIED,
            takeoverSuggested = false,
          )
        }
      }

      val choices = json.optJSONArray("choices")
      val msgObj = if (choices != null && choices.length() > 0) {
        choices.getJSONObject(0).optJSONObject("message")
      } else null

      val contentPartsArray = msgObj?.optJSONArray("content")
      var arrayImageUrl: String? = null
      val content = if (contentPartsArray != null) {
        buildString {
          for (i in 0 until contentPartsArray.length()) {
            val part = contentPartsArray.optJSONObject(i) ?: continue
            val partType = part.optString("type").lowercase(Locale.US)
            if (partType == "text" || partType == "output_text") {
              val partText = part.optString("text").ifBlank { part.optString("content") }
              if (partText.isNotBlank()) {
                if (isNotEmpty()) appendLine()
                append(partText)
              }
            } else if (partType == "image_url" || partType == "image" || partType == "output_image") {
              val candidate = part.optJSONObject("image_url")?.optString("url")
                ?.takeIf { it.isNotBlank() }
                ?: part.optString("url").takeIf { it.isNotBlank() }
                ?: part.optString("image_url").takeIf { it.isNotBlank() }
              if (arrayImageUrl == null && candidate != null) {
                arrayImageUrl = candidate
              }
            }
          }
        }
      } else {
        msgObj?.optString("content").orEmpty()
          .ifBlank { json.optString("reply", json.optString("output", "")) }
      }

      val mediaObj = json.optJSONObject("media") ?: msgObj?.optJSONObject("media")
      val firstAttach = (json.optJSONArray("attachments") ?: msgObj?.optJSONArray("attachments"))
        ?.optJSONObject(0)
      val firstData = json.optJSONArray("data")?.optJSONObject(0)
      val imagesArr = msgObj?.optJSONArray("images") ?: json.optJSONArray("images")
      val imageFromImagesArray = imagesArr?.let { arr ->
        (0 until arr.length()).firstNotNullOfOrNull { idx ->
          val itemObj = arr.optJSONObject(idx)
          itemObj?.optJSONObject("image_url")?.optString("url")?.takeIf { it.isNotBlank() }
            ?: itemObj?.optString("url")?.takeIf { it.isNotBlank() }
            ?: arr.optString(idx).takeIf { it.isNotBlank() && !it.startsWith("{") }
        }
      }

      val rawVideoUrl = listOfNotNull(
        json.optString("video_url").takeIf { it.isNotBlank() },
        json.optString("videoUrl").takeIf { it.isNotBlank() },
        msgObj?.optString("video_url")?.takeIf { it.isNotBlank() },
        msgObj?.optString("videoUrl")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("video_url")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("videoUrl")?.takeIf { it.isNotBlank() },
      ).firstOrNull()

      val rawImageUrl = listOfNotNull(
        arrayImageUrl,
        imageFromImagesArray,
        json.optString("image_url").takeIf { it.isNotBlank() },
        json.optString("imageUrl").takeIf { it.isNotBlank() },
        msgObj?.optString("image_url")?.takeIf { it.isNotBlank() },
        msgObj?.optString("imageUrl")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("image_url")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("imageUrl")?.takeIf { it.isNotBlank() },
        firstData?.optString("url")?.takeIf { it.isNotBlank() },
      ).firstOrNull()

      val rawAudioUrl = listOfNotNull(
        json.optString("audio_url").takeIf { it.isNotBlank() },
        json.optString("audioUrl").takeIf { it.isNotBlank() },
        msgObj?.optString("audio_url")?.takeIf { it.isNotBlank() },
        msgObj?.optString("audioUrl")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("audio_url")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("audioUrl")?.takeIf { it.isNotBlank() },
      ).firstOrNull()

      val rawDocumentUrl = listOfNotNull(
        json.optString("document_url").takeIf { it.isNotBlank() },
        json.optString("documentUrl").takeIf { it.isNotBlank() },
        msgObj?.optString("document_url")?.takeIf { it.isNotBlank() },
        msgObj?.optString("documentUrl")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("document_url")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("documentUrl")?.takeIf { it.isNotBlank() },
      ).firstOrNull()

      val rawMediaString = if (mediaObj == null) {
        (json.optString("media").takeIf { it.isNotBlank() }
          ?: msgObj?.optString("media")?.takeIf { it.isNotBlank() })
      } else null

      val genericMediaUrl = listOfNotNull(
        rawVideoUrl,
        rawImageUrl,
        rawAudioUrl,
        rawDocumentUrl,
        json.optString("media_url").takeIf { it.isNotBlank() },
        json.optString("mediaUrl").takeIf { it.isNotBlank() },
        json.optString("file_path").takeIf { it.isNotBlank() },
        json.optString("filePath").takeIf { it.isNotBlank() },
        json.optString("output_url").takeIf { it.isNotBlank() },
        msgObj?.optString("media_url")?.takeIf { it.isNotBlank() },
        msgObj?.optString("mediaUrl")?.takeIf { it.isNotBlank() },
        msgObj?.optString("file_path")?.takeIf { it.isNotBlank() },
        msgObj?.optString("filePath")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("url")?.takeIf { it.isNotBlank() },
        mediaObj?.optString("path")?.takeIf { it.isNotBlank() },
        rawMediaString,
        firstAttach?.optString("url")?.takeIf { it.isNotBlank() },
        firstAttach?.optString("file_path")?.takeIf { it.isNotBlank() },
        firstAttach?.optString("filePath")?.takeIf { it.isNotBlank() },
        firstAttach?.optString("path")?.takeIf { it.isNotBlank() },
      ).firstOrNull()

      val textDetectedMedia = if (genericMediaUrl == null) {
        detectMediaUrlInText(content)
      } else null

      val rawMediaCandidate = genericMediaUrl ?: textDetectedMedia
      val b64Fallback = firstData?.optString("b64_json")?.takeIf { it.isNotBlank() }
        ?.let { "data:image/png;base64,$it" }

      val resolvedOrCachedMediaUrl = persistOrResolveMediaUrl(
        rawMedia = rawMediaCandidate ?: b64Fallback,
        host = host,
        sessionKey = sessionKey,
        agentId = agentId,
        deviceId = deviceId,
      )

      val explicitType = (json.optString("media_type")
        .ifBlank { json.optString("mediaType") }
        .ifBlank { mediaObj?.optString("type").orEmpty() }
        .ifBlank { firstAttach?.optString("type").orEmpty() })
        .lowercase(Locale.US)

      val detectedType = when {
        resolvedOrCachedMediaUrl == null -> null
        rawVideoUrl != null || explicitType.contains("video") || isVideoUrl(resolvedOrCachedMediaUrl) ->
          GeneratedMediaType.VIDEO
        rawAudioUrl != null || explicitType.contains("audio") || isAudioUrl(resolvedOrCachedMediaUrl) ->
          GeneratedMediaType.AUDIO
        rawDocumentUrl != null || explicitType.contains("doc") || explicitType.contains("pdf") || isDocumentUrl(resolvedOrCachedMediaUrl) ->
          GeneratedMediaType.DOCUMENT
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

      val cleanedContent = stripMediaDirectives(content)
      val displayContent = cleanedContent.ifBlank {
        when (detectedType) {
          GeneratedMediaType.VIDEO -> "Generated your video clip via OpenClaw!"
          GeneratedMediaType.AUDIO -> "Generated your audio clip via OpenClaw!"
          GeneratedMediaType.DOCUMENT -> "Attached document from OpenClaw!"
          GeneratedMediaType.IMAGE -> "Attached image from OpenClaw!"
          null -> "No response from OpenClaw."
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
        mediaUrl = resolvedOrCachedMediaUrl,
        mediaType = detectedType,
        mediaPrompt = mediaPrompt.takeIf { resolvedOrCachedMediaUrl != null && it.isNotBlank() },
        mediaModel = mediaModel.takeIf { resolvedOrCachedMediaUrl != null && it.isNotBlank() },
        mediaDurationSec = durationSec.takeIf { resolvedOrCachedMediaUrl != null },
      )
    } catch (_: Exception) {
      val fallbackCandidate = detectMediaUrlInText(rawJson)
      val fallbackUrl = persistOrResolveMediaUrl(fallbackCandidate, host, sessionKey, agentId, deviceId)
      val detectedType = fallbackUrl?.let { classifyMediaType(it) }
      val cleanedRaw = stripMediaDirectives(rawJson)
      OpenClawDispatchResult(
        replyText = cleanedRaw.ifBlank { "No response from OpenClaw." },
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

  /**
   * Decodes inline `data:image/...;base64,...` URIs to a short `file:///...` cache path, or resolves/caches
   * `MEDIA:/path/to/file` / `PATH=/path/to/file` strings via OpenClaw `/__openclaw__/assistant-media`.
   */
  private fun persistOrResolveMediaUrl(
    rawMedia: String?,
    host: OpenClawHost?,
    sessionKey: String,
    agentId: String,
    deviceId: String = MediaSaveHelper.DEFAULT_DEVICE_ID,
  ): String? {
    val trimmed = rawMedia?.trim()?.trim('"', '\'', '`')?.takeIf { it.isNotBlank() } ?: return null
    if (trimmed.startsWith("data:image/", ignoreCase = true)) {
      return MediaSaveHelper.cacheBase64ImageToFile(appFilesDir, trimmed) ?: trimmed.take(1500)
    }
    if (!MediaSaveHelper.isDeviceLocalFileUri(trimmed) &&
      (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true) ||
        trimmed.contains("/__openclaw__/assistant-media", ignoreCase = true))
    ) {
      val cachedLocal = MediaSaveHelper.tryDownloadHostMediaToLocalFile(
        filesDir = appFilesDir,
        rawPathOrUrl = trimmed,
        host = host,
        sessionKey = sessionKey,
        agentId = agentId,
        deviceId = deviceId,
      )
      if (cachedLocal != null) return cachedLocal
    }
    return MediaSaveHelper.resolveDownloadUrl(trimmed, host, sessionKey, agentId)?.take(1500)
  }

  private fun detectMediaUrlInText(text: String): String? {
    // 1. OpenClaw explicit MEDIA:<path_or_url> directive (supports raw lines and markdown-wrapped `MEDIA:...`)
    val mediaDirective = Regex(
      """(?mi)(?:^|[\s`(*_\[])MEDIA:\s*(?:"([^"\r\n]+)"|`([^`\r\n]+)`|'([^'\r\n]+)'|([^\s"'`\r\n)\]*]+))""",
    ).find(text)
    if (mediaDirective != null) {
      val captured = (1..4).firstNotNullOfOrNull { idx ->
        mediaDirective.groupValues.getOrNull(idx)?.takeIf { it.isNotBlank() }
      }
      if (captured != null) return captured.trim()
    }

    // 2. OpenClaw explicit PATH=/abs/path.ext directive (avoid matching shell $PATH lists)
    val pathDirective = Regex(
      """(?mi)(?:^|[\s`(*_\[])PATH=\s*(?:"([^"\r\n]+\.[a-zA-Z0-9]{2,5})"|`([^`\r\n]+\.[a-zA-Z0-9]{2,5})`|'([^'\r\n]+\.[a-zA-Z0-9]{2,5})'|((?:file://)?(?:[a-zA-Z]:[/\\]|/|~/|\./)[^\s"'`\r\n)\]*]+\.[a-zA-Z0-9]{2,5}))""",
    ).find(text)
    if (pathDirective != null) {
      val captured = (1..4).firstNotNullOfOrNull { idx ->
        pathDirective.groupValues.getOrNull(idx)?.takeIf { it.isNotBlank() }
      }
      if (captured != null) return captured.trim()
    }

    // 3. Markdown image/attachment syntax ![alt](data:image/...) OR ![alt](url)
    val mdDataUri = Regex("!\\[[^\\]]*\\]\\((data:image/[^)\\s]+)\\)", RegexOption.IGNORE_CASE).find(text)
    if (mdDataUri != null) return mdDataUri.groupValues[1]

    val mdMatch = Regex("!\\[[^\\]]*\\]\\(([^)\\s]+)\\)").find(text)
    if (mdMatch != null) return mdMatch.groupValues[1]

    // 4. Bare data:image/... URI
    val bareDataUri = Regex("(data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\\r\\n]+)", RegexOption.IGNORE_CASE).find(text)
    if (bareDataUri != null) return bareDataUri.groupValues[1]

    // 5. Direct http(s) media/audio/document links
    val urlRegex = Regex(
      "(https?://[^\\s)\"'>]+\\.(?:png|jpg|jpeg|webp|gif|mp4|webm|mov|mp3|wav|m4a|ogg|pdf|docx|doc)(?:\\?[^\\s)\"'>]*)?)",
      RegexOption.IGNORE_CASE,
    )
    urlRegex.find(text)?.let { return it.groupValues[1] }

    // 6. Host paths like file:///home/..., /media/foo.jpg, /tmp/out.docx, ~/out.png, C:\...
    val relRegex = Regex(
      "((?:file://)?(?:/(?:media|files|outputs|static|tmp|home|Users|var)/|media/|files/|outputs/|static/|~/|C:\\\\)[^\\s)\"'>]+\\.(?:png|jpg|jpeg|webp|gif|mp4|webm|mov|mp3|wav|m4a|ogg|pdf|docx|doc))",
      RegexOption.IGNORE_CASE,
    )
    return relRegex.find(text)?.groupValues?.getOrNull(1)
  }

  private fun stripMediaDirectives(text: String): String {
    return text
      .replace(Regex("!\\[[^\\]]*\\]\\(\\s*data:[^)]+\\)", RegexOption.IGNORE_CASE), "")
      .replace(Regex("data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\\r\\n]+", RegexOption.IGNORE_CASE), "")
      .replace(Regex("""(?mi)(?:^|(?<=[ \t]))`?MEDIA:\s*(?:"[^"\r\n]+"|`[^`\r\n]+`|'[^'\r\n]+'|[^\s"'`\r\n]+)`?[ \t]*\r?\n?"""), "")
      .replace(Regex("""(?mi)(?:^|(?<=[ \t]))`?PATH=\s*(?:"[^"\r\n]+\.[a-zA-Z0-9]{2,5}"|`[^`\r\n]+\.[a-zA-Z0-9]{2,5}`|'[^'\r\n]+\.[a-zA-Z0-9]{2,5}'|(?:file://)?(?:[a-zA-Z]:[/\\]|/|~/|\./)[^\s"'`\r\n]+\.[a-zA-Z0-9]{2,5})`?[ \t]*\r?\n?"""), "")
      .replace(Regex("\\n{3,}"), "\n\n")
      .trim()
  }

  private fun stripInlineBase64MarkdownForPreview(text: String): String {
    return text
      .replace(Regex("!\\[[^\\]]*\\]\\(\\s*data:[^)]*\\)?", RegexOption.IGNORE_CASE), "[Receiving image...]")
      .replace(Regex("data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\\r\\n]*", RegexOption.IGNORE_CASE), "[Receiving image...]")
      .replace(Regex("""(?mi)(?:^|(?<=[ \t]))(?:MEDIA:|PATH=)\s*[^\r\n]+"""), "[Receiving file...]")
  }

  private fun classifyMediaType(url: String): GeneratedMediaType = when {
    isVideoUrl(url) -> GeneratedMediaType.VIDEO
    isAudioUrl(url) -> GeneratedMediaType.AUDIO
    isDocumentUrl(url) -> GeneratedMediaType.DOCUMENT
    else -> GeneratedMediaType.IMAGE
  }

  private fun isVideoUrl(url: String): Boolean {
    val clean = url.substringBefore('?').lowercase(Locale.US)
    return clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mov") || clean.endsWith(".mkv")
  }

  private fun isAudioUrl(url: String): Boolean {
    val clean = url.substringBefore('?').lowercase(Locale.US)
    return clean.endsWith(".mp3") || clean.endsWith(".wav") || clean.endsWith(".m4a") || clean.endsWith(".ogg") || clean.endsWith(".aac")
  }

  private fun isDocumentUrl(url: String): Boolean {
    val clean = url.substringBefore('?').lowercase(Locale.US)
    return clean.endsWith(".pdf") || clean.endsWith(".docx") || clean.endsWith(".doc")
  }

  internal var tcpProbeOverride: ((String, Int) -> Exception?)? = null

  private fun probeTcpReachable(baseUrl: String, timeoutMs: Int = FAST_TCP_PROBE_TIMEOUT_MS): Exception? {
    tcpProbeOverride?.let { return it(baseUrl, timeoutMs) }
    return try {
      val uri = java.net.URI(baseUrl)
      val host = uri.host?.takeIf { it.isNotBlank() } ?: return UnknownHostException(baseUrl)
      val port = when {
        uri.port > 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
      }
      Socket().use { socket ->
        socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
      }
      null
    } catch (e: Exception) {
      e
    }
  }

  companion object {
    private const val TAG = "OpenClawClient"
    const val APK_AGENT_ID = "apk"
    const val APK_WIRE_MODEL = "openclaw/apk"
    const val MEDIA_ROUTE_AGENT_ID = "main"
    const val FAST_PROBE_ENABLED = true
    const val FAST_TCP_PROBE_TIMEOUT_MS = 10_000
    const val SSE_CONNECT_TIMEOUT_SECONDS = 60L
    const val SSE_READ_TIMEOUT_SECONDS = 180L

    private const val MEDIA_SYSTEM_INSTRUCTION =
      "When sharing, finding, or creating an image or document for the user, " +
        "output MEDIA:/absolute/path/to/file (or PATH=/absolute/path/to/file) on its own line so the mobile client can fetch it from /__openclaw__/assistant-media."

    fun formatHttpErrorMessage(code: Int, rawBody: String, hostLabel: String): String {
      val parsed = extractCleanJsonErrorMessage(rawBody)
      val safeDetail = parsed ?: sanitizeNonJsonErrorFragment(rawBody)
      return when (code) {
        400 -> if (safeDetail.isNotBlank()) {
          "HTTP 400 Bad Request: $safeDetail"
        } else {
          "HTTP 400 Bad Request from $hostLabel (check model or session configuration)."
        }
        401 -> if (safeDetail.isNotBlank()) {
          "HTTP 401 Unauthorized — check Gateway Bearer Token in Settings ($safeDetail)."
        } else {
          "HTTP 401 Unauthorized — check Gateway Bearer Token in Settings."
        }
        403 -> if (safeDetail.isNotBlank()) {
          "HTTP 403 Forbidden: $safeDetail"
        } else {
          "HTTP 403 Forbidden — insufficient gateway scope or authority."
        }
        404 -> "OpenClaw endpoint returned HTTP 404 at $hostLabel${if (safeDetail.isNotBlank()) ": $safeDetail" else "."}"
        409 -> if (safeDetail.isNotBlank()) {
          "HTTP 409: Session archived — restore in OpenClaw Control UI ($safeDetail)."
        } else {
          "HTTP 409: Session archived — restore in OpenClaw Control UI."
        }
        500 -> if (safeDetail.isNotBlank()) {
          "HTTP 500 Server Error: $safeDetail"
        } else {
          "HTTP 500 Server Error from $hostLabel."
        }
        else -> if (safeDetail.isNotBlank()) {
          "OpenClaw returned HTTP $code: $safeDetail"
        } else {
          "OpenClaw returned HTTP $code from $hostLabel."
        }
      }
    }

    fun cleanRawErrorText(raw: String?, code: Int = 0, hostUrl: String = ""): String {
      val trimmed = raw.orEmpty().trim()
      if (code == 401 || (trimmed.contains("401") && trimmed.contains("Unauthorized", ignoreCase = true))) {
        val detail = extractCleanJsonErrorMessage(trimmed)
        return if (!detail.isNullOrBlank()) {
          "HTTP 401 Unauthorized — check Gateway Bearer Token in Settings ($detail)."
        } else {
          "HTTP 401 Unauthorized — check Gateway Bearer Token in Settings."
        }
      }
      if (code == 409) {
        val detail = extractCleanJsonErrorMessage(trimmed)
        return if (!detail.isNullOrBlank()) {
          "HTTP 409: Session archived — restore in OpenClaw Control UI ($detail)."
        } else {
          "HTTP 409: Session archived — restore in OpenClaw Control UI."
        }
      }
      extractCleanJsonErrorMessage(trimmed)?.let { return it }
      val cleaned = sanitizeNonJsonErrorFragment(trimmed)
      return cleaned.ifBlank {
        if (hostUrl.isNotBlank()) "Could not get a clean response from $hostUrl."
        else "OpenClaw gateway returned an unexpected error."
      }
    }

    private fun extractCleanJsonErrorMessage(raw: String): String? {
      val trimmed = raw.trim()
      if (!trimmed.startsWith("{")) return null
      return try {
        val json = JSONObject(trimmed)
        val errObj = json.optJSONObject("error")
        val msg = errObj?.optString("message")?.takeIf { it.isNotBlank() }
          ?: json.optString("message").takeIf { it.isNotBlank() }
          ?: json.optString("detail").takeIf { it.isNotBlank() }
          ?: errObj?.optString("type")?.takeIf { it.isNotBlank() }
        msg?.trim()?.take(220)
      } catch (_: Exception) {
        // Truncated or partial JSON: never dump broken tail like `st_error"}}`
        val match = Regex("\"message\"\\s*:\\s*\"([^\"]{3,200})").find(trimmed)
        match?.groupValues?.getOrNull(1)?.trim()
      }
    }

    private fun sanitizeNonJsonErrorFragment(raw: String): String {
      val trimmed = raw.trim()
      if (trimmed.isBlank()) return ""
      if (trimmed.contains("\"}}") || trimmed.contains("{\"error\"") || trimmed.startsWith("<!DOCTYPE", true) || trimmed.startsWith("<html", true)) {
        return "Gateway returned an unexpected response payload."
      }
      return trimmed.replace(Regex("\\s+"), " ").take(200)
    }

    fun formatNetworkThrowable(e: Throwable, hostUrl: String, elapsedMs: Long): OpenClawFormattedException {
      if (e is OpenClawFormattedException) return e
      val connectWindowMs = SSE_CONNECT_TIMEOUT_SECONDS * 1000L + 2_000L
      val isConnectClass = e is ConnectException ||
        e is UnknownHostException ||
        e is NoRouteToHostException ||
        (e is SocketTimeoutException && (e.message?.contains("connect", ignoreCase = true) == true || elapsedMs <= connectWindowMs))
      val isAbortMidStream = e is SocketException ||
        e.message?.contains("connection abort", ignoreCase = true) == true ||
        e.message?.contains("unexpected end of stream", ignoreCase = true) == true ||
        e is java.io.EOFException
      val msg = when {
        e is ConnectException || e is NoRouteToHostException ->
          "Could not connect to OpenClaw at $hostUrl (check LAN vs Tailscale mode or host power)."
        e is UnknownHostException ->
          "Could not resolve host address $hostUrl."
        e is IOException && e.message?.contains("Canceled", ignoreCase = true) == true ->
          "Aborted by user."
        isAbortMidStream ->
          "Connection dropped while streaming from $hostUrl (network interrupted). Check Wi-Fi/Tailscale and retry."
        e is SocketTimeoutException ->
          if (e.message?.contains("connect", ignoreCase = true) == true || elapsedMs <= connectWindowMs) {
            "Connection timed out reaching $hostUrl after ${maxOf(1L, elapsedMs / 1000L)}s."
          } else {
            "OpenClaw model stream timed out after ${elapsedMs / 1000L}s."
          }
        else -> {
          val rawMsg = e.localizedMessage.orEmpty()
          cleanRawErrorText(rawMsg, 0, hostUrl)
        }
      }
      return OpenClawFormattedException(
        message = msg,
        isFastConnectFailure = isConnectClass && elapsedMs <= 16_500L,
      )
    }
  }
}
