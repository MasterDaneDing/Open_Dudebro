package com.example.ui.avatar

import com.example.model.AccessoryStyle
import com.example.model.BotArchetype
import com.example.model.BotExpression
import com.example.model.CompanionBot
import com.example.model.HatStyle

object BotSvgGenerator {
  fun generateSvg(bot: CompanionBot, expression: BotExpression): String {
    val skin = bot.primaryColorHex
    val accent = bot.accentColorHex
    val ink = "#20150F"
    val cream = "#FDE6B3"

    val backTraitsSvg = when (bot.archetypeEnum) {
      BotArchetype.DINOSAUR -> """
        <polygon points="68,64 50,38 86,52" fill="$accent" stroke="$ink" stroke-width="4.2" stroke-linejoin="round"/>
        <polygon points="44,92 22,76 52,70" fill="$accent" stroke="$ink" stroke-width="4.2" stroke-linejoin="round"/>
        <polygon points="38,124 18,118 42,104" fill="$accent" stroke="$ink" stroke-width="4.2" stroke-linejoin="round"/>
      """.trimIndent()
      BotArchetype.PIGTAIL_GIRL -> """
        <path d="M44 98 C12 80 8 132 28 148 C40 142 46 118 44 98 Z" fill="#4A2E20" stroke="$ink" stroke-width="4.2"/>
        <path d="M196 98 C228 80 232 132 212 148 C200 142 194 118 196 98 Z" fill="#4A2E20" stroke="$ink" stroke-width="4.2"/>
        <circle cx="43" cy="97" r="7.5" fill="$accent" stroke="$ink" stroke-width="3.2"/>
        <circle cx="197" cy="97" r="7.5" fill="$accent" stroke="$ink" stroke-width="3.2"/>
      """.trimIndent()
      BotArchetype.CHINESE_MASTER -> """
        <circle cx="120" cy="43" r="16" fill="#231F20" stroke="$ink" stroke-width="4.2"/>
        <line x1="96" y1="42" x2="144" y2="42" stroke="#FFC93C" stroke-width="4" stroke-linecap="round"/>
      """.trimIndent()
      else -> ""
    }

    val earsSvg = when (bot.archetypeEnum) {
      BotArchetype.RETRO_ROBOT -> """
        <rect x="26" y="114" width="18" height="42" rx="8" fill="#D6E2E8" stroke="$ink" stroke-width="4.5"/>
        <rect x="196" y="114" width="18" height="42" rx="8" fill="#D6E2E8" stroke="$ink" stroke-width="4.5"/>
      """.trimIndent()
      BotArchetype.GOBLIN -> """
        <path d="M42 112 L10 96 C18 120 28 136 42 140 Z" fill="$skin" stroke="$ink" stroke-width="4.5" stroke-linejoin="round"/>
        <path d="M198 112 L230 96 C222 120 212 136 198 140 Z" fill="$skin" stroke="$ink" stroke-width="4.5" stroke-linejoin="round"/>
      """.trimIndent()
      BotArchetype.DINOSAUR -> ""
      else -> """
        <ellipse cx="39" cy="132" rx="14" ry="18" fill="$skin" stroke="$ink" stroke-width="4.5"/>
        <ellipse cx="200" cy="134" rx="12" ry="16" fill="$skin" stroke="$ink" stroke-width="4.5"/>
      """.trimIndent()
    }

    val headSvg = when (bot.archetypeEnum) {
      BotArchetype.RETRO_ROBOT -> """
        <rect x="38" y="52" width="164" height="154" rx="68" fill="$skin" stroke="$ink" stroke-width="4.6"/>
      """.trimIndent()
      else -> """
        <ellipse cx="120" cy="129" rx="82" ry="77" fill="$skin" stroke="$ink" stroke-width="4.6"/>
      """.trimIndent()
    }

    val archetypeFaceTraitsSvg = when (bot.archetypeEnum) {
      BotArchetype.GOBLIN -> """
        <path d="M80 64 C74 48 82 38 90 44 C92 54 90 62 80 64 Z" fill="#FFF3D6" stroke="$ink" stroke-width="3.2"/>
        <path d="M160 64 C166 48 158 38 150 44 C148 54 150 62 160 64 Z" fill="#FFF3D6" stroke="$ink" stroke-width="3.2"/>
      """.trimIndent()
      BotArchetype.YANKI_BIKER -> """
        <path d="M42 106 C36 62 64 24 118 18 C164 14 198 34 190 62 C180 82 146 78 118 74 C88 70 58 84 42 106 Z" fill="#1F2433" stroke="$ink" stroke-width="4.5"/>
      """.trimIndent()
      BotArchetype.PIGTAIL_GIRL -> """
        <path d="M42 98 C46 52 92 48 120 48 C148 48 194 52 198 98 C180 76 154 74 136 84 C126 74 114 74 104 84 C86 74 60 76 42 98 Z" fill="#4A2E20" stroke="$ink" stroke-width="4.2"/>
      """.trimIndent()
      BotArchetype.CHINESE_MASTER -> """
        <path d="M110 190 C112 208 116 220 120 224 C124 220 128 208 130 190 Z" fill="#F8F4EC" stroke="$ink" stroke-width="3.2"/>
        <path d="M110 148 C92 148 78 166 76 192 C86 184 94 166 112 156 Z" fill="#F8F4EC" stroke="$ink" stroke-width="3.2"/>
        <path d="M130 148 C148 148 162 166 164 192 C154 184 146 166 128 156 Z" fill="#F8F4EC" stroke="$ink" stroke-width="3.2"/>
      """.trimIndent()
      else -> ""
    }

    val eyesSvg = when (expression) {
      BotExpression.UH_HUH -> """
        <path d="M85 114 Q94 103 103 114" fill="none" stroke="$ink" stroke-width="4.5" stroke-linecap="round"/>
        <path d="M137 114 Q146 103 155 114" fill="none" stroke="$ink" stroke-width="4.5" stroke-linecap="round"/>
      """.trimIndent()
      BotExpression.SORRY -> """
        <path d="M87 109 L99 114 L87 119" fill="none" stroke="$ink" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round"/>
        <path d="M153 109 L141 114 L153 119" fill="none" stroke="$ink" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round"/>
      """.trimIndent()
      else -> """
        <ellipse cx="94" cy="114" rx="7" ry="9" fill="$ink"/>
        <ellipse cx="146" cy="114" rx="7" ry="9" fill="$ink"/>
      """.trimIndent()
    }

    val mouthSvg = when (expression) {
      BotExpression.UH_HUH -> """
        <path d="M104 168 L136 168 C136 189 104 189 104 168 Z" fill="#9E2B25" stroke="$ink" stroke-width="4.2"/>
      """.trimIndent()
      BotExpression.WORRIED, BotExpression.SORRY -> """
        <path d="M104 177 C110 170 115 183 120 176 C125 169 130 183 136 176" fill="none" stroke="$ink" stroke-width="4.2" stroke-linecap="round"/>
      """.trimIndent()
      else -> """
        <path d="M113 165 C127 161 128 175 116 176 C129 177 129 192 112 188" fill="none" stroke="$ink" stroke-width="4.2" stroke-linecap="round"/>
      """.trimIndent()
    }

    val accessorySvg = when (bot.accessoryEnum) {
      AccessoryStyle.FOREHEAD_GLASSES -> """
        <path d="M78 70 C64 72 56 80 48 92" fill="none" stroke="$ink" stroke-width="4.5" stroke-linecap="round"/>
        <rect x="78" y="60" width="34" height="21" rx="10" fill="rgba(255,232,196,0.3)" stroke="$ink" stroke-width="4"/>
        <rect x="126" y="60" width="34" height="21" rx="10" fill="rgba(255,232,196,0.3)" stroke="$ink" stroke-width="4"/>
        <line x1="88" y1="64" x2="96" y2="76" stroke="$cream" stroke-width="4" stroke-linecap="round"/>
        <line x1="136" y1="64" x2="144" y2="76" stroke="$cream" stroke-width="4" stroke-linecap="round"/>
        <path d="M112 69 Q119 64 126 69" fill="none" stroke="$ink" stroke-width="4" stroke-linecap="round"/>
      """.trimIndent()
      AccessoryStyle.ROUND_GLASSES -> """
        <circle cx="94" cy="114" r="16" fill="rgba(255,232,196,0.28)" stroke="$ink" stroke-width="4"/>
        <circle cx="146" cy="114" r="16" fill="rgba(255,232,196,0.28)" stroke="$ink" stroke-width="4"/>
        <line x1="110" y1="114" x2="130" y2="114" stroke="$ink" stroke-width="4"/>
      """.trimIndent()
      AccessoryStyle.BOWTIE -> """
        <polygon points="120,204 95,193 95,215" fill="$accent" stroke="$ink" stroke-width="3.8"/>
        <polygon points="120,204 145,193 145,215" fill="$accent" stroke="$ink" stroke-width="3.8"/>
        <circle cx="120" cy="204" r="6" fill="#FFC93C" stroke="$ink" stroke-width="3.8"/>
      """.trimIndent()
      else -> ""
    }

    val hatSvg = when (bot.hatEnum) {
      HatStyle.BERET -> """
        <ellipse cx="122" cy="45" rx="58" ry="19" fill="$accent" stroke="$ink" stroke-width="4.5"/>
        <line x1="122" y1="26" x2="126" y2="17" stroke="$ink" stroke-width="4.5" stroke-linecap="round"/>
      """.trimIndent()
      HatStyle.CROWN -> """
        <polygon points="86,52 82,24 104,38 120,18 136,38 158,24 154,52" fill="#FFC93C" stroke="$ink" stroke-width="4.5" stroke-linejoin="round"/>
      """.trimIndent()
      HatStyle.MORTARBOARD -> """
        <polygon points="120,18 182,36 120,52 58,36" fill="#33313B" stroke="$ink" stroke-width="4.5" stroke-linejoin="round"/>
      """.trimIndent()
      else -> ""
    }

    return """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
        <circle cx="120" cy="134" r="98" fill="$accent" fill-opacity="0.12"/>
        $backTraitsSvg
        $earsSvg
        $headSvg
        <circle cx="76" cy="147" r="23" fill="$accent" fill-opacity="0.92"/>
        <circle cx="164" cy="147" r="23" fill="$accent" fill-opacity="0.92"/>
        <line x1="79" y1="92" x2="106" y2="99" stroke="$ink" stroke-width="6.5" stroke-linecap="round"/>
        <line x1="134" y1="99" x2="161" y2="92" stroke="$ink" stroke-width="6.5" stroke-linecap="round"/>
        $eyesSvg
        $archetypeFaceTraitsSvg
        <circle cx="120" cy="138" r="21" fill="$accent" stroke="$ink" stroke-width="4.6"/>
        <circle cx="114" cy="130" r="5.8" fill="$cream"/>
        $mouthSvg
        $accessorySvg
        $hatSvg
      </svg>
    """.trimIndent()
  }
}
