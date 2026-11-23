package com.example.model

data class ArchetypePreset(
  val defaultName: String,
  val defaultRole: String,
  val defaultMission: String,
  val primaryHex: String,
  val accentHex: String,
  val hat: HatStyle,
  val accessory: AccessoryStyle,
  val persona: String,
)

object StarterBots {
  fun presetFor(archetype: BotArchetype): ArchetypePreset = when (archetype) {
    BotArchetype.GLASSES_FELLOW -> ArchetypePreset(
      defaultName = "Bimbo",
      defaultRole = "Daily Claw Organizer",
      defaultMission = "I organize your daily OpenClaw tasks!",
      primaryHex = "#EEA05E",
      accentHex = "#DC4D21",
      hat = HatStyle.NONE,
      accessory = AccessoryStyle.FOREHEAD_GLASSES,
      persona = "Friendly, down-to-earth helper who turns casual requests into crisp OpenClaw desktop & file instructions.",
    )
    BotArchetype.PROFESSOR -> ArchetypePreset(
      defaultName = "Prof. Maru",
      defaultRole = "Code & Docs Scholar",
      defaultMission = "I critique translations & review code!",
      primaryHex = "#F5C69A",
      accentHex = "#D94F2A",
      hat = HatStyle.MORTARBOARD,
      accessory = AccessoryStyle.ROUND_GLASSES,
      persona = "Thoughtful researcher who critiques drafts carefully and breaks messy prompts into rigorous OpenClaw plans.",
    )
    BotArchetype.RETRO_ROBOT -> ArchetypePreset(
      defaultName = "Volt-50",
      defaultRole = "1950s PC Operator",
      defaultMission = "I help translate documents!",
      primaryHex = "#8EC9C5",
      accentHex = "#E88B28",
      hat = HatStyle.NONE,
      accessory = AccessoryStyle.SPARK_BADGE,
      persona = "Retro-futuristic 1950s tin-bot specialist focused on document translation, shell automation, and host computer tasks.",
    )
    BotArchetype.DINOSAUR -> ArchetypePreset(
      defaultName = "Rexy",
      defaultRole = "Heavy-Lifting Runner",
      defaultMission = "I handle big file cleanups & builds!",
      primaryHex = "#8CCF8A",
      accentHex = "#E86E34",
      hat = HatStyle.NONE,
      accessory = AccessoryStyle.SCARF,
      persona = "Cheerful chubby dinosaur who stomps through big file cleanups, batch tasks, and build checks.",
    )
    BotArchetype.GOBLIN -> ArchetypePreset(
      defaultName = "Pip",
      defaultRole = "Shortcut Tinkerer",
      defaultMission = "I hunt clever shortcuts & edge cases!",
      primaryHex = "#A7D47D",
      accentHex = "#DF5A38",
      hat = HatStyle.NONE,
      accessory = AccessoryStyle.MONOCLE,
      persona = "Clever little goblin tinkerer who finds quick automation shortcuts and clever fixes on the host PC.",
    )
    BotArchetype.YANKI_BIKER -> ArchetypePreset(
      defaultName = "Kenji",
      defaultRole = "80s Road Enforcer",
      defaultMission = "I tackle stubborn bugs head-on!",
      primaryHex = "#F2B680",
      accentHex = "#D9431E",
      hat = HatStyle.NONE,
      accessory = AccessoryStyle.NONE,
      persona = "Bold 1980s Japanese biker with a heart of gold who tackles stubborn errors head-on with crisp instructions.",
    )
    BotArchetype.CHINESE_MASTER -> ArchetypePreset(
      defaultName = "Master Lin",
      defaultRole = "Strategy & Calm Guide",
      defaultMission = "I refine nuance, tone & strategy!",
      primaryHex = "#F5C99C",
      accentHex = "#D94F2A",
      hat = HatStyle.NONE,
      accessory = AccessoryStyle.NONE,
      persona = "Wise master who distills chaotic casual chat into calm, balanced, precision-ordered OpenClaw steps.",
    )
    BotArchetype.PIGTAIL_GIRL -> ArchetypePreset(
      defaultName = "Momo",
      defaultRole = "Bright Note & Media Pal",
      defaultMission = "I polish notes, wording & summaries!",
      primaryHex = "#FAD2B4",
      accentHex = "#E0557A",
      hat = HatStyle.NONE,
      accessory = AccessoryStyle.BOWTIE,
      persona = "Sweet, energetic companion who keeps notes, photos, documents, and daily tasks neatly arranged.",
    )
  }

  fun defaultCrew(userId: String): List<CompanionBot> = listOf(
    CompanionBot(
      userId = userId,
      name = "Bimbo",
      roleTitle = "Daily Claw Organizer",
      mission = "I organize your daily OpenClaw tasks!",
      lastMessagePreview = "Ready whenever you need a hand with OpenClaw.",
      archetype = BotArchetype.GLASSES_FELLOW.name,
      hatStyle = HatStyle.NONE.name,
      accessoryStyle = AccessoryStyle.FOREHEAD_GLASSES.name,
      primaryColorHex = "#EEA05E",
      accentColorHex = "#DC4D21",
      systemPersona = presetFor(BotArchetype.GLASSES_FELLOW).persona,
      openClawAgentId = "main",
      functionPreset = BotFunctionPreset.GENERAL.name,
      pinnedDefault = true,
    ),
    CompanionBot(
      userId = userId,
      name = "Volt-50",
      roleTitle = "1950s Document & PC Bot",
      mission = "I help translate documents!",
      lastMessagePreview = "Beep-boop! Drop any document or text to translate.",
      archetype = BotArchetype.RETRO_ROBOT.name,
      hatStyle = HatStyle.ANTENNA_DISH.name,
      accessoryStyle = AccessoryStyle.SPARK_BADGE.name,
      primaryColorHex = "#8EC9C5",
      accentColorHex = "#E88B28",
      systemPersona = presetFor(BotArchetype.RETRO_ROBOT).persona,
      openClawAgentId = "translator",
      functionPreset = BotFunctionPreset.TRANSLATION.name,
      pinnedDefault = true,
    ),
    CompanionBot(
      userId = userId,
      name = "Prof. Maru",
      roleTitle = "Critique & Docs Scholar",
      mission = "I critique translations & review code!",
      lastMessagePreview = "Ready to double-check accuracy and tone!",
      archetype = BotArchetype.PROFESSOR.name,
      hatStyle = HatStyle.MORTARBOARD.name,
      accessoryStyle = AccessoryStyle.ROUND_GLASSES.name,
      primaryColorHex = "#F5C69A",
      accentColorHex = "#D94F2A",
      systemPersona = presetFor(BotArchetype.PROFESSOR).persona,
      openClawAgentId = "critic",
      functionPreset = BotFunctionPreset.CRITIQUING.name,
      pinnedDefault = true,
    ),
  )

  fun defaultHost(userId: String): OpenClawHost = OpenClawHost(
    userId = userId,
    label = "Home OpenClaw PC",
    baseUrl = OpenClawHost.DEFAULT_LAN_URL,
    lanUrl = OpenClawHost.DEFAULT_LAN_URL,
    tailscaleUrl = OpenClawHost.DEFAULT_TAILSCALE_URL,
    connectionMode = HostConnectionMode.LAN.name,
    controlUrl = "",
    gatewayToken = "",
    aiMode = AiRoutingMode.HYBRID_GEMINI_OPENCLAW.name,
    defaultLlmProvider = OpenClawLlmProvider.OPENCLAW_AUTO.name,
    defaultLlmModel = "",
    localLlmEndpoint = "http://127.0.0.1:11434/v1",
    autoParseCasual = true,
    lastStatus = HostStatus.UNVERIFIED.name,
  )

  val primaryColorSwatches = listOf(
    "#EEA05E" to "Warm Tan",
    "#F5C69A" to "Cream Bun",
    "#FAD2B4" to "Peach Milk",
    "#8EC9C5" to "1950s Mint",
    "#8CCF8A" to "Dino Green",
    "#A7D47D" to "Goblin Moss",
    "#9CBCE8" to "Sky Tin",
    "#F3D27A" to "Butter Gold",
  )

  val accentColorSwatches = listOf(
    "#DC4D21" to "Rosy Red",
    "#E86E34" to "Tangerine",
    "#E88B28" to "Amber Glow",
    "#E0557A" to "Berry Blush",
    "#3B8B88" to "Retro Teal",
    "#4B7BE5" to "Cobalt",
  )
}
