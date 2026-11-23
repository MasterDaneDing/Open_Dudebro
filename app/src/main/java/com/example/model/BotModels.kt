package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class BotArchetype(val displayName: String, val tagline: String) {
  GLASSES_FELLOW("Glasses Fellow", "Chubby pal with glasses perched on top"),
  PROFESSOR("Prof. Maru", "Cozy round scholar with fluffy cloud mustache"),
  RETRO_ROBOT("Volt-50", "1950s cute rounded tin robot with antenna"),
  DINOSAUR("Dino Pal", "Chubby little dinosaur with cute dorsal spikes"),
  GOBLIN("Goblin", "Cheeky round goblin with pointy ears & tiny horns"),
  YANKI_BIKER("Yanki", "80s Japanese biker with tall regent pompadour"),
  CHINESE_MASTER("Wise Master", "Serene martial & tea master with flowing whiskers"),
  PIGTAIL_GIRL("Pigtail Girl", "Sweet little companion with bouncy twin pigtails"),
  ;

  companion object {
    fun safeValueOf(raw: String?): BotArchetype =
      entries.firstOrNull { it.name == raw } ?: GLASSES_FELLOW
  }
}

enum class HatStyle(val displayName: String) {
  NONE("No Hat"),
  BERET("Artist Beret"),
  CAP("Courier Cap"),
  PROPELLER("Propeller Beanie"),
  MORTARBOARD("Scholar Cap"),
  CROWN("Tiny Crown"),
  HEADBAND("Headband"),
  ANTENNA_DISH("Radar Dish"),
  ;

  companion object {
    fun safeValueOf(raw: String?): HatStyle =
      entries.firstOrNull { it.name == raw } ?: NONE
  }
}

enum class AccessoryStyle(val displayName: String) {
  NONE("None"),
  FOREHEAD_GLASSES("Forehead Glasses"),
  ROUND_GLASSES("Round Spectacles"),
  MONOCLE("Gold Monocle"),
  BOWTIE("Dapper Bowtie"),
  STUBBLE("Chin Stubble"),
  SCARF("Cozy Scarf"),
  SPARK_BADGE("Claw Badge"),
  ;

  companion object {
    fun safeValueOf(raw: String?): AccessoryStyle =
      entries.firstOrNull { it.name == raw } ?: FOREHEAD_GLASSES
  }
}

enum class BotExpression(val label: String, val bubbleHint: String) {
  HAPPY("Cheerful", "Ready to help!"),
  THINKING("Thinking...", "Parsing casual chat into OpenClaw steps..."),
  UH_HUH("Uh-huh!", "Got a clear plan & solution!"),
  WORRIED("Worried", "Checking an ongoing issue..."),
  SORRY("Sorry!", "Oops, hit a snag—need a hand?"),
  WORKING("Working", "Running instructions on OpenClaw..."),
  ALERT("Heads Up!", "Host takeover might help here!"),
  ;

  companion object {
    fun safeValueOf(raw: String?): BotExpression =
      entries.firstOrNull { it.name == raw } ?: HAPPY
  }
}

enum class MessageSender {
  USER,
  BOT,
  SYSTEM,
  ;

  companion object {
    fun safeValueOf(raw: String?): MessageSender =
      entries.firstOrNull { it.name == raw } ?: USER
  }
}

enum class MessageStatus(val label: String) {
  IDLE("Ready"),
  PARSING("Refining"),
  DISPATCHED("Sent to OpenClaw"),
  COMPLETED("Done"),
  WARNING("Needs Attention"),
  FAILED("Failed"),
  ;

  companion object {
    fun safeValueOf(raw: String?): MessageStatus =
      entries.firstOrNull { it.name == raw } ?: IDLE
  }
}

enum class AiRoutingMode(val displayName: String, val description: String) {
  HYBRID_GEMINI_OPENCLAW(
    "Hybrid (Gemini Parser + OpenClaw)",
    "Gemini turns casual chat into crisp OpenClaw instructions, then sends to your host.",
  ),
  OPENCLAW_ONLY(
    "Direct OpenClaw LLM",
    "Sends casual chat directly to your OpenClaw gateway's configured LLM.",
  ),
  GEMINI_PARSER_PREVIEW(
    "Gemini Refiner Only",
    "Refines casual requests into clear OpenClaw instructions locally without dispatching.",
  ),
  ;

  companion object {
    fun safeValueOf(raw: String?): AiRoutingMode =
      entries.firstOrNull { it.name == raw } ?: HYBRID_GEMINI_OPENCLAW
  }
}

enum class BotFunctionPreset(
  val displayName: String,
  val suggestedAgentId: String,
  val defaultMission: String,
  val openClawDirective: String,
) {
  GENERAL(
    displayName = "General Helper",
    suggestedAgentId = "main",
    defaultMission = "I organize your daily OpenClaw tasks!",
    openClawDirective = "Handle everyday OpenClaw computer tasks, file organization, and clear instruction execution.",
  ),
  TRANSLATION(
    displayName = "Translation",
    suggestedAgentId = "translator",
    defaultMission = "I help translate documents accurately!",
    openClawDirective = "Specialize in accurate, natural translation across languages while preserving tone, formatting, and technical terms.",
  ),
  PROOFREADING(
    displayName = "Proofreading",
    suggestedAgentId = "proofreader",
    defaultMission = "I proofread grammar, spelling & clarity!",
    openClawDirective = "Specialize in proofreading text for grammar, spelling, punctuation, consistency, and crisp phrasing.",
  ),
  CRITIQUING(
    displayName = "Critiquing",
    suggestedAgentId = "critic",
    defaultMission = "I critique drafts, translations & ideas!",
    openClawDirective = "Specialize in structured critique: evaluate accuracy, tone, edge cases, risks, and actionable improvements.",
  ),
  CODING(
    displayName = "Coding & Ops",
    suggestedAgentId = "coder",
    defaultMission = "I inspect code, builds & host scripts!",
    openClawDirective = "Specialize in code analysis, debugging, build checks, shell automation, and host operations.",
  ),
  RESEARCH(
    displayName = "Research & Notes",
    suggestedAgentId = "researcher",
    defaultMission = "I summarize documents, links & notes!",
    openClawDirective = "Specialize in summarizing files, extracting key points, and structuring clear research briefs.",
  ),
  ;

  companion object {
    fun safeValueOf(raw: String?): BotFunctionPreset =
      entries.firstOrNull { it.name == raw } ?: GENERAL
  }
}

enum class OpenClawLlmProvider(
  val displayName: String,
  val wireKey: String,
  val defaultModel: String,
  val description: String,
  val isBotOnly: Boolean = false,
) {
  HOST_DEFAULT(
    displayName = "Use Host Default",
    wireKey = "host_default",
    defaultModel = "",
    description = "Inherit the default LLM configured in OpenClaw Host Settings.",
    isBotOnly = true,
  ),
  OPENCLAW_AUTO(
    displayName = "OpenClaw Gateway Default",
    wireKey = "openclaw",
    defaultModel = "",
    description = "Let the OpenClaw gateway use whichever default model is active in its config.",
  ),
  VENICE_AI(
    displayName = "Venice.ai",
    wireKey = "venice",
    defaultModel = "deepseek-v4-1-flash-uncensored",
    description = "Route OpenClaw inference through your Venice.ai API key & model.",
  ),
  GOOGLE(
    displayName = "Google (Gemini)",
    wireKey = "google",
    defaultModel = "google/gemini-2.5-flash",
    description = "Route OpenClaw inference through your Google Gemini API key.",
  ),
  LOCAL_LLM(
    displayName = "Local LLM (Ollama / LM Studio)",
    wireKey = "local",
    defaultModel = "ollama/qwen3.5-fast",
    description = "Route OpenClaw inference to a local model running on your host PC.",
  ),
  ;

  companion object {
    val hostOptions: List<OpenClawLlmProvider>
      get() = entries.filter { !it.isBotOnly }

    fun safeForBot(raw: String?): OpenClawLlmProvider =
      entries.firstOrNull { it.name == raw } ?: HOST_DEFAULT

    fun safeForHost(raw: String?): OpenClawLlmProvider =
      entries.firstOrNull { it.name == raw && !it.isBotOnly } ?: OPENCLAW_AUTO
  }
}

data class VeniceModelInfo(
  val id: String,
  val label: String = id,
  val category: String = "Text",
)

object VeniceModelCatalog {
  val popularPresets: List<VeniceModelInfo> = listOf(
    VeniceModelInfo("deepseek-v4-1-flash-uncensored", "deepseek-v4-1-flash-uncensored", "Fast Cloud"),
    VeniceModelInfo("claude-sonnet-4-6", "claude-sonnet-4-6", "Coding"),
    VeniceModelInfo("claude-opus-4-8", "claude-opus-4-8", "Reasoning"),
    VeniceModelInfo("claude-haiku-5-5", "claude-haiku-5-5", "Fast"),
    VeniceModelInfo("deepseek-v4-1", "deepseek-v4-1", "Coding"),
    VeniceModelInfo("qwen-2.5-coder-32b", "qwen-2.5-coder-32b", "Coding"),
  )

  val defaultModelId: String = "deepseek-v4-1-flash-uncensored"
}

enum class WheelLlmOption(
  val wheelLabel: String,
  val fullLabel: String,
  val provider: OpenClawLlmProvider,
) {
  OPENCLAW_DEFAULT("Gateway", "OpenClaw Default", OpenClawLlmProvider.OPENCLAW_AUTO),
  OPENCLAW_LOCAL("Local LLM", "OpenClaw Local", OpenClawLlmProvider.LOCAL_LLM),
  OPENCLAW_THIRD_PARTY("3rd Party", "OpenClaw 3rd Party", OpenClawLlmProvider.VENICE_AI),
  ;

  companion object {
    fun safeValueOf(raw: String?): WheelLlmOption =
      entries.firstOrNull { it.name == raw } ?: OPENCLAW_DEFAULT
  }
}

enum class HostStatus(val label: String) {
  ONLINE("Online"),
  OFFLINE("Offline"),
  UNVERIFIED("Not Checked"),
  ;

  companion object {
    fun safeValueOf(raw: String?): HostStatus =
      entries.firstOrNull { it.name == raw } ?: UNVERIFIED
  }
}

enum class FileLinkType(val label: String) {
  ANDROID_ATTACHMENT("Device File"),
  HOST_PATH("Host PC Path"),
  CLOUD_URL("Link / URL"),
  ;

  companion object {
    fun safeValueOf(raw: String?): FileLinkType =
      entries.firstOrNull { it.name == raw } ?: HOST_PATH
  }
}

enum class GeneratedMediaType(val label: String) {
  IMAGE("Image"),
  VIDEO("Video"),
  AUDIO("Audio"),
  DOCUMENT("Document"),
  ;

  companion object {
    fun safeValueOf(raw: String?): GeneratedMediaType =
      entries.firstOrNull { it.name == raw } ?: IMAGE
  }
}

data class MediaAttachmentPayload(
  val displayName: String,
  val uriOrPath: String,
  val mimeType: String,
  val linkType: FileLinkType,
  val extractedContent: String = "",
  val uploadedHostId: String = "",
  val isPdf: Boolean = false,
  val isDocx: Boolean = false,
  val isImage: Boolean = false,
  val imageDataUrl: String = "",
  val fileBase64: String = "",
  val pageImageDataUrls: List<String> = emptyList(),
)

enum class HostConnectionMode(val displayName: String, val shortPill: String) {
  LAN("Home Mode (LAN)", "\uD83C\uDFE0 LAN"),
  TAILSCALE("Outside Mode (Tailscale)", "\uD83C\uDF10 Tailscale"),
  ;

  companion object {
    fun safeValueOf(raw: String?): HostConnectionMode =
      entries.firstOrNull { it.name == raw } ?: LAN
  }
}

@Keep
@IgnoreExtraProperties
data class CompanionBot(
  @DocumentId val id: String = "",
  val userId: String = "",
  val name: String = "Bimbo",
  val roleTitle: String = "Daily Claw Pal",
  val archetype: String = BotArchetype.GLASSES_FELLOW.name,
  val hatStyle: String = HatStyle.NONE.name,
  val accessoryStyle: String = AccessoryStyle.FOREHEAD_GLASSES.name,
  val primaryColorHex: String = "#EEA05E",
  val accentColorHex: String = "#DC4D21",
  val systemPersona: String = "Warm, concise OpenClaw helper that turns casual chat into crisp steps.",
  val mission: String = "",
  val lastMessagePreview: String = "",
  val openClawAgentId: String = "main",
  val sessionKey: String = "",
  val toolPermissions: List<String> = emptyList(),
  val workspacePath: String = "",
  val functionPreset: String = BotFunctionPreset.GENERAL.name,
  val llmProvider: String = OpenClawLlmProvider.HOST_DEFAULT.name,
  val llmModel: String = "",
  val openClawSynced: Boolean = false,
  val pinnedDefault: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val archetypeEnum: BotArchetype get() = BotArchetype.safeValueOf(archetype)
  val hatEnum: HatStyle get() = HatStyle.safeValueOf(hatStyle)
  val accessoryEnum: AccessoryStyle get() = AccessoryStyle.safeValueOf(accessoryStyle)
  val functionPresetEnum: BotFunctionPreset get() = BotFunctionPreset.safeValueOf(functionPreset)
  val llmProviderEnum: OpenClawLlmProvider get() = OpenClawLlmProvider.safeForBot(llmProvider)
  val displayMission: String get() = mission.ifBlank { roleTitle }

  val effectiveToolPermissions: List<String>
    get() = AgentToolPermission.normalizeKeys(toolPermissions).ifEmpty {
      AgentToolPermission.defaultKeys()
    }

  /**
   * Produces a persistent, sanitized unique agent slug so each Bot Dude runs on an isolated
   * OpenClaw session lane instead of colliding on the shared global `main` session.
   */
  fun resolvedBotDudeSlug(): String {
    val preferred = openClawAgentId.trim()
      .takeIf { it.isNotBlank() && !it.equals("main", ignoreCase = true) }
      ?: name.trim().ifBlank { functionPresetEnum.suggestedAgentId }
    val cleanBase = preferred.lowercase(java.util.Locale.US)
      .replace(Regex("[^a-z0-9_-]+"), "_")
      .trim('_', '-')
      .take(28)
      .ifBlank { "bot_dude" }
    val cleanId = id.trim().replace(Regex("[^a-zA-Z0-9_-]"), "")
    return when {
      cleanId.isBlank() -> cleanBase
      cleanBase.contains(cleanId, ignoreCase = true) -> cleanBase
      else -> "${cleanBase}_${cleanId.takeLast(8)}".take(48)
    }
  }

  val effectivePrimarySessionKey: String
    get() = sessionKey.trim().takeIf { it.isNotBlank() }
      ?: "agent:${resolvedBotDudeSlug()}:main"

  fun effectiveCafeSessionKey(roomId: String): String {
    val cleanRoom = roomId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "default" }
    return "agent:${resolvedBotDudeSlug()}:cafe_${cleanRoom.take(28)}"
  }

  fun effectiveSpawnSessionKey(taskId: String): String {
    val cleanTask = taskId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "task" }
    return "agent:${resolvedBotDudeSlug()}:spawn_${cleanTask.take(28)}"
  }

  fun toBotDude(
    resolvedModelId: String = llmModel,
    resolvedProvider: OpenClawLlmProvider = llmProviderEnum,
  ): BotDude = BotDude(
    id = id.ifBlank { resolvedBotDudeSlug() },
    name = name,
    systemPrompt = systemPersona,
    modelId = resolvedModelId,
    sessionKey = effectivePrimarySessionKey,
    agentId = openClawAgentId.ifBlank { functionPresetEnum.suggestedAgentId },
    provider = resolvedProvider,
    functionPreset = functionPresetEnum,
    mission = displayMission,
    toolPermissions = effectiveToolPermissions,
    workspacePath = workspacePath.trim(),
    sourceBot = this,
  )

  fun toCreateMap(ownerId: String): Map<String, Any> = mapOf(
    "userId" to ownerId,
    "name" to name.trim().take(40).ifBlank { "Claw Dudebro" },
    "roleTitle" to roleTitle.trim().take(60).ifBlank { "OpenClaw Companion" },
    "archetype" to archetypeEnum.name,
    "hatStyle" to hatEnum.name,
    "accessoryStyle" to accessoryEnum.name,
    "primaryColorHex" to normalizeHex(primaryColorHex, "#EEA05E"),
    "accentColorHex" to normalizeHex(accentColorHex, "#DC4D21"),
    "systemPersona" to systemPersona.trim().take(500),
    "mission" to mission.trim().take(160),
    "lastMessagePreview" to lastMessagePreview.trim().take(300),
    "openClawAgentId" to openClawAgentId.trim().take(80).ifBlank { "main" },
    "sessionKey" to effectivePrimarySessionKey.take(160),
    "toolPermissions" to effectiveToolPermissions,
    "workspacePath" to workspacePath.trim().take(300),
    "functionPreset" to functionPresetEnum.name,
    "llmProvider" to llmProviderEnum.name,
    "llmModel" to llmModel.trim().take(120),
    "openClawSynced" to openClawSynced,
    "pinnedDefault" to pinnedDefault,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

  fun toUpdateMap(): Map<String, Any> = mapOf(
    "name" to name.trim().take(40).ifBlank { "Claw Dudebro" },
    "roleTitle" to roleTitle.trim().take(60).ifBlank { "OpenClaw Companion" },
    "archetype" to archetypeEnum.name,
    "hatStyle" to hatEnum.name,
    "accessoryStyle" to accessoryEnum.name,
    "primaryColorHex" to normalizeHex(primaryColorHex, "#EEA05E"),
    "accentColorHex" to normalizeHex(accentColorHex, "#DC4D21"),
    "systemPersona" to systemPersona.trim().take(500),
    "mission" to mission.trim().take(160),
    "lastMessagePreview" to lastMessagePreview.trim().take(300),
    "openClawAgentId" to openClawAgentId.trim().take(80).ifBlank { "main" },
    "sessionKey" to effectivePrimarySessionKey.take(160),
    "toolPermissions" to effectiveToolPermissions,
    "workspacePath" to workspacePath.trim().take(300),
    "functionPreset" to functionPresetEnum.name,
    "llmProvider" to llmProviderEnum.name,
    "llmModel" to llmModel.trim().take(120),
    "openClawSynced" to openClawSynced,
    "pinnedDefault" to pinnedDefault,
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@Keep
@IgnoreExtraProperties
data class BotMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val botId: String = "",
  val sender: String = MessageSender.USER.name,
  val rawText: String = "",
  val parsedInstruction: String? = null,
  val instructionGoal: String? = null,
  val instructionSteps: List<String> = emptyList(),
  val expression: String = BotExpression.HAPPY.name,
  val status: String = MessageStatus.IDLE.name,
  val attachedFiles: List<String> = emptyList(),
  val linkedPaths: List<String> = emptyList(),
  val takeoverSuggested: Boolean = false,
  val mediaUrl: String? = null,
  val mediaType: String? = null,
  val mediaPrompt: String? = null,
  val mediaModel: String? = null,
  val mediaDurationSec: Int? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val senderEnum: MessageSender get() = MessageSender.safeValueOf(sender)
  val expressionEnum: BotExpression get() = BotExpression.safeValueOf(expression)
  val statusEnum: MessageStatus get() = MessageStatus.safeValueOf(status)
  val hasMedia: Boolean get() = !mediaUrl.isNullOrBlank()
  val mediaTypeEnum: GeneratedMediaType get() = GeneratedMediaType.safeValueOf(mediaType)

  fun toCreateMap(ownerId: String, targetBotId: String): Map<String, Any> {
    val base = mutableMapOf<String, Any?>(
      "userId" to ownerId,
      "botId" to targetBotId,
      "sender" to senderEnum.name,
      "rawText" to rawText.trim().take(4000).ifBlank { "..." },
      "parsedInstruction" to parsedInstruction?.trim()?.take(4000)?.takeIf { it.isNotEmpty() },
      "instructionGoal" to instructionGoal?.trim()?.take(200)?.takeIf { it.isNotEmpty() },
      "instructionSteps" to instructionSteps.take(12).map { it.take(300) },
      "expression" to expressionEnum.name,
      "status" to statusEnum.name,
      "attachedFiles" to attachedFiles.take(10).map { it.take(200) },
      "linkedPaths" to linkedPaths.take(10).map { it.take(500) },
      "takeoverSuggested" to takeoverSuggested,
      "mediaUrl" to mediaUrl?.trim()?.take(1500)?.takeIf { it.isNotEmpty() },
      "mediaType" to mediaType?.takeIf { !mediaUrl.isNullOrBlank() }?.let { GeneratedMediaType.safeValueOf(it).name },
      "mediaPrompt" to mediaPrompt?.trim()?.take(1000)?.takeIf { it.isNotEmpty() },
      "mediaModel" to mediaModel?.trim()?.take(120)?.takeIf { it.isNotEmpty() },
      "mediaDurationSec" to mediaDurationSec?.coerceIn(0, 600),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    @Suppress("UNCHECKED_CAST")
    return base.filterValues { it != null } as Map<String, Any>
  }
}

@Keep
@IgnoreExtraProperties
data class OpenClawHost(
  @DocumentId val id: String = "",
  val userId: String = "",
  val label: String = "My OpenClaw PC",
  val baseUrl: String = DEFAULT_LAN_URL,
  val lanUrl: String = "",
  val tailscaleUrl: String = "",
  val connectionMode: String = HostConnectionMode.LAN.name,
  val controlUrl: String = "",
  val gatewayToken: String = "",
  val aiMode: String = AiRoutingMode.HYBRID_GEMINI_OPENCLAW.name,
  val defaultLlmProvider: String = OpenClawLlmProvider.OPENCLAW_AUTO.name,
  val defaultLlmModel: String = "",
  val localLlmEndpoint: String = "",
  val autoParseCasual: Boolean = true,
  val lastStatus: String = HostStatus.UNVERIFIED.name,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val aiModeEnum: AiRoutingMode get() = AiRoutingMode.safeValueOf(aiMode)
  val defaultLlmProviderEnum: OpenClawLlmProvider get() = OpenClawLlmProvider.safeForHost(defaultLlmProvider)
  val lastStatusEnum: HostStatus get() = HostStatus.safeValueOf(lastStatus)
  val connectionModeEnum: HostConnectionMode get() = HostConnectionMode.safeValueOf(connectionMode)

  val effectiveLanUrl: String
    get() = normalizeGatewayBaseUrl(lanUrl.trim().ifBlank { baseUrl.trim() })

  val effectiveTailscaleUrl: String
    get() = normalizeGatewayBaseUrl(tailscaleUrl.trim())

  val effectiveBaseUrl: String
    get() = when (connectionModeEnum) {
      HostConnectionMode.TAILSCALE -> effectiveTailscaleUrl.ifBlank { effectiveLanUrl }
      HostConnectionMode.LAN -> effectiveLanUrl.ifBlank { effectiveTailscaleUrl }
    }.trim().trimEnd('/')

  /**
   * Secondary gateway URL for automatic LAN <-> Tailscale failover when the primary URL is unreachable.
   */
  val fallbackBaseUrl: String
    get() {
      val primary = effectiveBaseUrl
      val secondary = when (connectionModeEnum) {
        HostConnectionMode.LAN -> effectiveTailscaleUrl
        HostConnectionMode.TAILSCALE -> effectiveLanUrl
      }.trim().trimEnd('/')
      return if (secondary.isNotBlank() && !secondary.equals(primary, ignoreCase = true)) {
        secondary
      } else {
        ""
      }
    }

  companion object {
    const val DEFAULT_PORT = 18789
    const val DEFAULT_LAN_URL = "http://192.168.68.53:18789"
    const val DEFAULT_TAILSCALE_URL = "http://100.91.98.120:18789"

    /**
     * Normalizes a user-entered gateway host/URL:
     * - Tolerates whitespace and trailing slashes
     * - Prepends `http://` if scheme is omitted (e.g. `192.168.68.53:18789` or `192.168.68.53`)
     * - Appends `:18789` if bare host/IP without port or path is provided
     */
    fun normalizeGatewayBaseUrl(raw: String, defaultPort: Int = DEFAULT_PORT): String {
      val trimmed = raw.trim().trimEnd('/')
      if (trimmed.isBlank()) return ""
      val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
      ) {
        trimmed
      } else {
        "http://$trimmed"
      }
      val schemeIdx = withScheme.indexOf("://")
      val afterScheme = if (schemeIdx >= 0) withScheme.substring(schemeIdx + 3) else withScheme
      val hostPortPart = afterScheme.substringBefore('/')
      val hasPath = afterScheme.contains('/')
      val hasPort = hostPortPart.contains(':')
      return if (!hasPath && !hasPort && hostPortPart.isNotBlank()) {
        "$withScheme:$defaultPort"
      } else {
        withScheme.trimEnd('/')
      }
    }
  }

  /**
   * OpenClaw 2026.11.6 serves its web control dashboard at root (`/?token=...`), NOT `/control`.
   * If controlUrl is blank or points to the legacy `/control` path, auto-resolve the root dashboard
   * with the bearer token attached as query parameter. Otherwise respect explicit custom URLs (e.g. noVNC).
   */
  val resolvedDashboardUrl: String
    get() {
      val trimmedControl = controlUrl.trim()
      val token = gatewayToken.trim()
      val activeBase = effectiveBaseUrl
      val isLegacyControlPath = trimmedControl.isBlank() ||
        trimmedControl.endsWith("/control", ignoreCase = true) ||
        trimmedControl.endsWith("/control/", ignoreCase = true)
      if (!isLegacyControlPath) {
        if (token.isNotBlank() && trimmedControl.startsWith(activeBase) && !trimmedControl.contains("token=")) {
          val encoded = java.net.URLEncoder.encode(token, "UTF-8")
          val sep = if (trimmedControl.contains('?')) "&" else "?"
          return "$trimmedControl${sep}token=$encoded"
        }
        return trimmedControl
      }
      if (activeBase.isBlank()) return ""
      return if (token.isNotBlank()) {
        val encoded = java.net.URLEncoder.encode(token, "UTF-8")
        "$activeBase/?token=$encoded"
      } else {
        "$activeBase/"
      }
    }

  fun toCreateMap(ownerId: String): Map<String, Any> {
    val activeBase = effectiveBaseUrl.ifBlank { DEFAULT_LAN_URL }
    val normalizedLan = effectiveLanUrl.ifBlank { activeBase }
    return mapOf(
      "userId" to ownerId,
      "label" to label.trim().take(50).ifBlank { "My OpenClaw PC" },
      "baseUrl" to activeBase.take(300),
      "lanUrl" to normalizedLan.take(300),
      "tailscaleUrl" to effectiveTailscaleUrl.take(300),
      "connectionMode" to connectionModeEnum.name,
      "controlUrl" to controlUrl.trim().take(300),
      "gatewayToken" to gatewayToken.trim().take(256),
      "aiMode" to aiModeEnum.name,
      "defaultLlmProvider" to defaultLlmProviderEnum.name,
      "defaultLlmModel" to defaultLlmModel.trim().take(120),
      "localLlmEndpoint" to localLlmEndpoint.trim().take(300),
      "autoParseCasual" to autoParseCasual,
      "lastStatus" to lastStatusEnum.name,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
  }

  fun toUpdateMap(): Map<String, Any> {
    val activeBase = effectiveBaseUrl.ifBlank { DEFAULT_LAN_URL }
    val normalizedLan = effectiveLanUrl.ifBlank { activeBase }
    return mapOf(
      "label" to label.trim().take(50).ifBlank { "My OpenClaw PC" },
      "baseUrl" to activeBase.take(300),
      "lanUrl" to normalizedLan.take(300),
      "tailscaleUrl" to effectiveTailscaleUrl.take(300),
      "connectionMode" to connectionModeEnum.name,
      "controlUrl" to controlUrl.trim().take(300),
      "gatewayToken" to gatewayToken.trim().take(256),
      "aiMode" to aiModeEnum.name,
      "defaultLlmProvider" to defaultLlmProviderEnum.name,
      "defaultLlmModel" to defaultLlmModel.trim().take(120),
      "localLlmEndpoint" to localLlmEndpoint.trim().take(300),
      "autoParseCasual" to autoParseCasual,
      "lastStatus" to lastStatusEnum.name,
      "updatedAt" to FieldValue.serverTimestamp(),
    )
  }
}

@Keep
@IgnoreExtraProperties
data class LinkedFile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val uriOrPath: String = "",
  val linkType: String = FileLinkType.HOST_PATH.name,
  val mimeOrExt: String = "",
  val sizeBytes: Long = 0L,
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val linkTypeEnum: FileLinkType get() = FileLinkType.safeValueOf(linkType)

  fun toCreateMap(ownerId: String): Map<String, Any> = mapOf(
    "userId" to ownerId,
    "displayName" to displayName.trim().take(120).ifBlank { "Untitled File" },
    "uriOrPath" to uriOrPath.trim().take(500).ifBlank { "~/" },
    "linkType" to linkTypeEnum.name,
    "mimeOrExt" to mimeOrExt.trim().take(120),
    "sizeBytes" to sizeBytes.coerceAtLeast(0L),
    "notes" to notes.trim().take(250),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@Keep
@IgnoreExtraProperties
data class GeneratedMediaItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val mediaUrl: String = "",
  val mediaType: String = GeneratedMediaType.IMAGE.name,
  val prompt: String = "",
  val modelUsed: String = "",
  val durationSec: Int = 0,
  val sourceBotName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val mediaTypeEnum: GeneratedMediaType get() = GeneratedMediaType.safeValueOf(mediaType)

  fun toCreateMap(ownerId: String): Map<String, Any> = mapOf(
    "userId" to ownerId,
    "mediaUrl" to mediaUrl.trim().take(1500),
    "mediaType" to mediaTypeEnum.name,
    "prompt" to prompt.trim().take(1000).ifBlank { "Generated via OpenClaw" },
    "modelUsed" to modelUsed.trim().take(120),
    "durationSec" to durationSec.coerceIn(0, 600),
    "sourceBotName" to sourceBotName.trim().take(60),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@Keep
@IgnoreExtraProperties
data class UserVenicePreferences(
  @DocumentId val id: String = "",
  val userId: String = "",
  val globalVeniceModel: String = VeniceModelCatalog.defaultModelId,
  val veniceApiKeyOverride: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(ownerId: String): Map<String, Any> = mapOf(
    "userId" to ownerId,
    "globalVeniceModel" to globalVeniceModel.trim().take(120).ifBlank { VeniceModelCatalog.defaultModelId },
    "veniceApiKeyOverride" to veniceApiKeyOverride.trim().take(256),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@Keep
@IgnoreExtraProperties
data class ChatRoom(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "Cafe Squad",
  val topic: String = "Collaborate on tasks & feedback",
  val botIds: List<String> = emptyList(),
  val maxPingPongTurns: Int = 3,
  val lastMessagePreview: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val effectiveMaxPingPongTurns: Int get() = maxPingPongTurns.coerceIn(1, 6)

  fun toCreateMap(ownerId: String): Map<String, Any> = mapOf(
    "userId" to ownerId,
    "title" to title.trim().take(60).ifBlank { "Cafe Squad" },
    "topic" to topic.trim().take(160),
    "botIds" to botIds.filter { it.isNotBlank() }.distinct().take(8),
    "maxPingPongTurns" to effectiveMaxPingPongTurns,
    "lastMessagePreview" to lastMessagePreview.trim().take(300),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

  fun toUpdateMap(): Map<String, Any> = mapOf(
    "title" to title.trim().take(60).ifBlank { "Cafe Squad" },
    "topic" to topic.trim().take(160),
    "botIds" to botIds.filter { it.isNotBlank() }.distinct().take(8),
    "maxPingPongTurns" to effectiveMaxPingPongTurns,
    "lastMessagePreview" to lastMessagePreview.trim().take(300),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@Keep
@IgnoreExtraProperties
data class RoomMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val roomId: String = "",
  val senderType: String = MessageSender.USER.name,
  val senderBotId: String? = null,
  val senderBotName: String = "You",
  val replyToBotName: String? = null,
  val rawText: String = "",
  val parsedInstruction: String? = null,
  val expression: String = BotExpression.HAPPY.name,
  val mediaUrl: String? = null,
  val mediaType: String? = null,
  val mediaPrompt: String? = null,
  val mediaModel: String? = null,
  val mediaDurationSec: Int? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val senderTypeEnum: MessageSender get() = MessageSender.safeValueOf(senderType)
  val expressionEnum: BotExpression get() = BotExpression.safeValueOf(expression)
  val hasMedia: Boolean get() = !mediaUrl.isNullOrBlank()
  val mediaTypeEnum: GeneratedMediaType get() = GeneratedMediaType.safeValueOf(mediaType)

  fun toCreateMap(ownerId: String, targetRoomId: String): Map<String, Any> {
    val base = mutableMapOf<String, Any?>(
      "userId" to ownerId,
      "roomId" to targetRoomId,
      "senderType" to senderTypeEnum.name,
      "senderBotId" to senderBotId?.takeIf { it.isNotBlank() }?.take(128),
      "senderBotName" to senderBotName.trim().take(50).ifBlank { "You" },
      "replyToBotName" to replyToBotName?.trim()?.take(50)?.takeIf { it.isNotEmpty() },
      "rawText" to rawText.trim().take(4000).ifBlank { "..." },
      "parsedInstruction" to parsedInstruction?.trim()?.take(4000)?.takeIf { it.isNotEmpty() },
      "expression" to expressionEnum.name,
      "mediaUrl" to mediaUrl?.trim()?.take(1500)?.takeIf { it.isNotEmpty() },
      "mediaType" to mediaType?.takeIf { !mediaUrl.isNullOrBlank() }?.let { GeneratedMediaType.safeValueOf(it).name },
      "mediaPrompt" to mediaPrompt?.trim()?.take(1000)?.takeIf { it.isNotEmpty() },
      "mediaModel" to mediaModel?.trim()?.take(120)?.takeIf { it.isNotEmpty() },
      "mediaDurationSec" to mediaDurationSec?.coerceIn(0, 600),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    @Suppress("UNCHECKED_CAST")
    return base.filterValues { it != null } as Map<String, Any>
  }
}

@Keep
@IgnoreExtraProperties
data class GeneralChatThread(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "New General Chat",
  val pinned: Boolean = false,
  val lastMessagePreview: String = "",
  val wheelModel: String = WheelLlmOption.OPENCLAW_DEFAULT.name,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val wheelModelEnum: WheelLlmOption get() = WheelLlmOption.safeValueOf(wheelModel)

  fun toCreateMap(ownerId: String): Map<String, Any> = mapOf(
    "userId" to ownerId,
    "title" to title.trim().take(80).ifBlank { "New General Chat" },
    "pinned" to pinned,
    "lastMessagePreview" to lastMessagePreview.trim().take(300),
    "wheelModel" to wheelModelEnum.name,
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )
}

@Keep
@IgnoreExtraProperties
data class GeneralChatMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val chatId: String = "",
  val sender: String = MessageSender.USER.name,
  val rawText: String = "",
  val modelLabel: String = "",
  val status: String = MessageStatus.IDLE.name,
  val mediaUrl: String? = null,
  val mediaType: String? = null,
  val mediaPrompt: String? = null,
  val mediaModel: String? = null,
  val mediaDurationSec: Int? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val senderEnum: MessageSender get() = MessageSender.safeValueOf(sender)
  val statusEnum: MessageStatus get() = MessageStatus.safeValueOf(status)
  val hasMedia: Boolean get() = !mediaUrl.isNullOrBlank()
  val mediaTypeEnum: GeneratedMediaType get() = GeneratedMediaType.safeValueOf(mediaType)

  fun toCreateMap(ownerId: String, targetChatId: String): Map<String, Any> {
    val base = mutableMapOf<String, Any?>(
      "userId" to ownerId,
      "chatId" to targetChatId,
      "sender" to senderEnum.name,
      "rawText" to rawText.trim().take(4000).ifBlank { "..." },
      "modelLabel" to modelLabel.trim().take(80),
      "status" to statusEnum.name,
      "mediaUrl" to mediaUrl?.trim()?.take(1500)?.takeIf { it.isNotEmpty() },
      "mediaType" to mediaType?.takeIf { !mediaUrl.isNullOrBlank() }?.let { GeneratedMediaType.safeValueOf(it).name },
      "mediaPrompt" to mediaPrompt?.trim()?.take(1000)?.takeIf { it.isNotEmpty() },
      "mediaModel" to mediaModel?.trim()?.take(120)?.takeIf { it.isNotEmpty() },
      "mediaDurationSec" to mediaDurationSec?.coerceIn(0, 600),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    @Suppress("UNCHECKED_CAST")
    return base.filterValues { it != null } as Map<String, Any>
  }
}

fun normalizeHex(raw: String, fallback: String): String {
  val cleaned = raw.trim()
  return if (Regex("^#[0-9A-Fa-f]{6}$").matches(cleaned)) cleaned.uppercase() else fallback
}
