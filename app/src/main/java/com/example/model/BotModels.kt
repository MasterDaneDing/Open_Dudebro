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
    defaultModel = "venice/llama-3.3-70b",
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
    defaultModel = "ollama/qwen2.5:14b",
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
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val senderEnum: MessageSender get() = MessageSender.safeValueOf(sender)
  val expressionEnum: BotExpression get() = BotExpression.safeValueOf(expression)
  val statusEnum: MessageStatus get() = MessageStatus.safeValueOf(status)

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
  val baseUrl: String = "http://192.168.1.50:18789",
  val controlUrl: String = "http://192.168.1.50:18789/control",
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

  fun toCreateMap(ownerId: String): Map<String, Any> = mapOf(
    "userId" to ownerId,
    "label" to label.trim().take(50).ifBlank { "My OpenClaw PC" },
    "baseUrl" to baseUrl.trim().take(300).ifBlank { "http://192.168.1.50:18789" },
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

  fun toUpdateMap(): Map<String, Any> = mapOf(
    "label" to label.trim().take(50).ifBlank { "My OpenClaw PC" },
    "baseUrl" to baseUrl.trim().take(300).ifBlank { "http://192.168.1.50:18789" },
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
    "mimeOrExt" to mimeOrExt.trim().take(60),
    "sizeBytes" to sizeBytes.coerceAtLeast(0L),
    "notes" to notes.trim().take(250),
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
  val lastMessagePreview: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(ownerId: String): Map<String, Any> = mapOf(
    "userId" to ownerId,
    "title" to title.trim().take(60).ifBlank { "Cafe Squad" },
    "topic" to topic.trim().take(160),
    "botIds" to botIds.filter { it.isNotBlank() }.distinct().take(8),
    "lastMessagePreview" to lastMessagePreview.trim().take(300),
    "createdAt" to FieldValue.serverTimestamp(),
    "updatedAt" to FieldValue.serverTimestamp(),
  )

  fun toUpdateMap(): Map<String, Any> = mapOf(
    "title" to title.trim().take(60).ifBlank { "Cafe Squad" },
    "topic" to topic.trim().take(160),
    "botIds" to botIds.filter { it.isNotBlank() }.distinct().take(8),
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
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val senderTypeEnum: MessageSender get() = MessageSender.safeValueOf(senderType)
  val expressionEnum: BotExpression get() = BotExpression.safeValueOf(expression)

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
