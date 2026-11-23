package com.example.model

enum class AgentToolPermission(
  val wireKey: String,
  val label: String,
  val description: String,
) {
  WORKSPACE_READ("read", "Read Files", "Inspect files and folders in workspace"),
  WORKSPACE_WRITE("write", "Write Files", "Create and edit files on host"),
  SHELL_EXEC("shell", "Shell / Terminal", "Run terminal commands and scripts"),
  WEB_SEARCH("web", "Web Lookup", "Fetch web pages and search online"),
  MEDIA_GEN("media", "Image / Video Gen", "Generate and attach visual media"),
  A2A_SPAWN("spawn", "Background Sub-Agent", "Spawn isolated background sub-tasks"),
  ;

  companion object {
    fun defaultKeys(): List<String> = listOf(
      WORKSPACE_READ.wireKey,
      WEB_SEARCH.wireKey,
      MEDIA_GEN.wireKey,
      A2A_SPAWN.wireKey,
    )

    fun allKeys(): List<String> = entries.map { it.wireKey }

    fun fromWireKey(raw: String): AgentToolPermission? =
      entries.firstOrNull { it.wireKey.equals(raw.trim(), ignoreCase = true) || it.name.equals(raw.trim(), ignoreCase = true) }

    fun normalizeKeys(rawKeys: List<String>): List<String> =
      rawKeys.mapNotNull { fromWireKey(it)?.wireKey }.distinct().take(12)

    /**
     * Hard ceiling of tools a bot's function preset is allowed to use. A translator should not be
     * running shell commands even if its stored permission list is broad. The effective set is
     * the intersection of this ceiling and the bot's own permissions, so users can narrow further
     * but never widen past the preset.
     */
    fun ceilingForPreset(preset: BotFunctionPreset): List<String> = when (preset) {
      BotFunctionPreset.TRANSLATION,
      BotFunctionPreset.PROOFREADING,
      BotFunctionPreset.CRITIQUING,
      -> listOf(WORKSPACE_READ.wireKey, WEB_SEARCH.wireKey, MEDIA_GEN.wireKey)
      BotFunctionPreset.RESEARCH -> listOf(WORKSPACE_READ.wireKey, WEB_SEARCH.wireKey)
      BotFunctionPreset.CODING -> listOf(
        WORKSPACE_READ.wireKey,
        WORKSPACE_WRITE.wireKey,
        SHELL_EXEC.wireKey,
        WEB_SEARCH.wireKey,
      )
      BotFunctionPreset.GENERAL -> allKeys()
    }

    /** Resolves the enforced allowlist: preset ceiling ∩ requested, never empty. */
    fun resolveEnforced(preset: BotFunctionPreset, requested: List<String>): List<String> {
      val ceiling = ceilingForPreset(preset).toSet()
      val wanted = normalizeKeys(requested)
      val enforced = wanted.filter { it in ceiling }
      return enforced.ifEmpty { ceilingForPreset(preset).take(3) }
    }
  }
}

/**
 * Strongly-typed domain representation of a Bot Dude with isolated agent/session routing,
 * persona prompt, designated model override, and workspace/tool permissions.
 */
data class BotDude(
  val id: String,
  val name: String,
  val systemPrompt: String,
  val modelId: String,
  val sessionKey: String,
  val agentId: String = "main",
  val provider: OpenClawLlmProvider = OpenClawLlmProvider.HOST_DEFAULT,
  val functionPreset: BotFunctionPreset = BotFunctionPreset.GENERAL,
  val mission: String = "",
  val toolPermissions: List<String> = AgentToolPermission.defaultKeys(),
  val workspacePath: String = "",
  val sourceBot: CompanionBot? = null,
)

enum class CafeSpeakerRole {
  USER,
  BOT,
  SYSTEM,
}

/** What kind of contribution an entry is, used to pin real artefacts into context. */
enum class CafeEntryKind {
  CHAT,
  DELIVERABLE,
  REVIEW,
  ;

  companion object {
    fun safeValueOf(raw: String?): CafeEntryKind =
      entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: CHAT

    fun forPreset(preset: BotFunctionPreset): CafeEntryKind = when (preset) {
      BotFunctionPreset.TRANSLATION, BotFunctionPreset.CODING -> DELIVERABLE
      BotFunctionPreset.CRITIQUING, BotFunctionPreset.PROOFREADING -> REVIEW
      BotFunctionPreset.GENERAL, BotFunctionPreset.RESEARCH -> CHAT
    }
  }
}

data class CafeTranscriptEntry(
  val id: String,
  val speakerId: String,
  val speakerName: String,
  val role: CafeSpeakerRole,
  val replyToBotName: String? = null,
  val content: String,
  val modelUsed: String = "",
  val expression: BotExpression = BotExpression.HAPPY,
  val parsedInstruction: String? = null,
  val mediaUrl: String? = null,
  val mediaType: GeneratedMediaType? = null,
  val createdAtMs: Long = System.currentTimeMillis(),
  val kind: CafeEntryKind = CafeEntryKind.CHAT,
)

enum class CafeSessionStatus(val label: String) {
  IDLE("Ready"),
  RUNNING("Discussing"),
  PAUSED("Paused"),
  STOPPED("Stopped"),
  COMPLETED("Round Complete"),
}

data class CafeSession(
  val roomId: String,
  val roomTitle: String,
  val topic: String,
  val activeBotDudes: List<BotDude>,
  val transcript: List<CafeTranscriptEntry> = emptyList(),
  val maxPingPongTurns: Int = 3,
  val completedBotTurnsInRound: Int = 0,
  val activeTurnIndex: Int = 0,
  val activeSpeakerBotId: String? = null,
  val activeSpeakerName: String? = null,
  val activeSpeakerModel: String? = null,
  val streamingPreview: String? = null,
  val status: CafeSessionStatus = CafeSessionStatus.IDLE,
  val statusNote: String = "",
)

enum class BackgroundTaskStatus(val label: String) {
  QUEUED("Queued"),
  RUNNING("Running"),
  COMPLETED("Done"),
  FAILED("Failed"),
  ABORTED("Aborted"),
}

data class BackgroundAgentTask(
  val id: String,
  val botId: String,
  val botName: String,
  val parentSessionKey: String,
  val subSessionKey: String,
  val prompt: String,
  val modelLabel: String = "",
  val status: BackgroundTaskStatus = BackgroundTaskStatus.RUNNING,
  val elapsedSec: Int = 0,
  val previewText: String = "",
  val resultText: String = "",
  val createdAtMs: Long = System.currentTimeMillis(),
)
