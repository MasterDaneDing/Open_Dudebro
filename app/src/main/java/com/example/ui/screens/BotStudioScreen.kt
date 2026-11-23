package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.model.AccessoryStyle
import com.example.model.AgentToolPermission
import com.example.model.BotArchetype
import com.example.model.BotExpression
import com.example.model.BotFunctionPreset
import com.example.model.CompanionBot
import com.example.model.HatStyle
import com.example.model.OpenClawHost
import com.example.model.OpenClawLlmProvider
import com.example.model.StarterBots
import com.example.model.VeniceModelCatalog
import com.example.model.VeniceModelInfo
import com.example.ui.avatar.BotSvgGenerator
import com.example.ui.avatar.BotVectorAvatar
import com.example.ui.avatar.parseHexColor
import com.example.ui.components.VeniceModelPickerPanel

@Composable
fun BotStudioScreen(
  existingBot: CompanionBot?,
  currentUserId: String,
  activeHost: OpenClawHost? = null,
  globalVeniceModel: String = VeniceModelCatalog.defaultModelId,
  veniceLiveModels: List<VeniceModelInfo> = VeniceModelCatalog.popularPresets,
  isFetchingVeniceModels: Boolean = false,
  onFetchVeniceModels: () -> Unit = {},
  onSaveBot: (CompanionBot) -> Unit,
  onSyncWithOpenClaw: (CompanionBot, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
  onDeleteBot: (String) -> Unit,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  var name by remember(existingBot?.id) { mutableStateOf(existingBot?.name ?: "") }
  var mission by remember(existingBot?.id) {
    mutableStateOf(
      existingBot?.displayMission ?: StarterBots.presetFor(BotArchetype.GLASSES_FELLOW).defaultMission
    )
  }
  var roleTitle by remember(existingBot?.id) { mutableStateOf(existingBot?.roleTitle ?: "OpenClaw Helper") }
  var archetype by remember(existingBot?.id) {
    mutableStateOf(existingBot?.archetypeEnum ?: BotArchetype.GLASSES_FELLOW)
  }
  var hatStyle by remember(existingBot?.id) {
    mutableStateOf(existingBot?.hatEnum ?: HatStyle.NONE)
  }
  var accessoryStyle by remember(existingBot?.id) {
    mutableStateOf(existingBot?.accessoryEnum ?: AccessoryStyle.FOREHEAD_GLASSES)
  }
  var primaryHex by remember(existingBot?.id) {
    mutableStateOf(existingBot?.primaryColorHex ?: "#EEA05E")
  }
  var accentHex by remember(existingBot?.id) {
    mutableStateOf(existingBot?.accentColorHex ?: "#DC4D21")
  }
  var agentId by remember(existingBot?.id) {
    mutableStateOf(existingBot?.openClawAgentId ?: "main")
  }
  var functionPreset by remember(existingBot?.id) {
    mutableStateOf(existingBot?.functionPresetEnum ?: BotFunctionPreset.GENERAL)
  }
  var llmProvider by remember(existingBot?.id) {
    mutableStateOf(existingBot?.llmProviderEnum ?: OpenClawLlmProvider.HOST_DEFAULT)
  }
  var llmModel by remember(existingBot?.id) {
    mutableStateOf(existingBot?.llmModel.orEmpty())
  }
  var sessionKey by remember(existingBot?.id) {
    mutableStateOf(existingBot?.sessionKey.orEmpty())
  }
  var workspacePath by remember(existingBot?.id) {
    mutableStateOf(existingBot?.workspacePath.orEmpty())
  }
  var selectedPermissions by remember(existingBot?.id) {
    mutableStateOf(
      existingBot?.toolPermissions?.takeIf { it.isNotEmpty() }?.toSet()
        ?: AgentToolPermission.defaultKeys().toSet()
    )
  }
  var persona by remember(existingBot?.id) {
    mutableStateOf(
      existingBot?.systemPersona
        ?: "Cheerful companion that translates casual requests into clear OpenClaw instructions."
    )
  }
  var previewExpression by remember { mutableStateOf(BotExpression.HAPPY) }
  var showSvgDialog by remember { mutableStateOf(false) }
  var syncFeedback by remember(existingBot?.id) { mutableStateOf<String?>(null) }

  val draftBot = CompanionBot(
    id = existingBot?.id.orEmpty(),
    userId = currentUserId,
    name = name.ifBlank { "New Pal" },
    roleTitle = roleTitle.ifBlank { "OpenClaw Helper" },
    mission = mission.trim().ifBlank { roleTitle.ifBlank { "OpenClaw Helper" } },
    lastMessagePreview = existingBot?.lastMessagePreview.orEmpty(),
    archetype = archetype.name,
    hatStyle = hatStyle.name,
    accessoryStyle = accessoryStyle.name,
    primaryColorHex = primaryHex,
    accentColorHex = accentHex,
    systemPersona = persona,
    openClawAgentId = agentId.ifBlank { functionPreset.suggestedAgentId },
    sessionKey = sessionKey.trim(),
    toolPermissions = AgentToolPermission.normalizeKeys(selectedPermissions.toList()),
    workspacePath = workspacePath.trim(),
    functionPreset = functionPreset.name,
    llmProvider = llmProvider.name,
    llmModel = llmModel,
    openClawSynced = existingBot?.openClawSynced ?: false,
    pinnedDefault = existingBot?.pinnedDefault ?: false,
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
      }
      Text(
        text = if (existingBot == null) "Create Companion Bot" else "Customize ${existingBot.name}",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.weight(1f),
      )
      OutlinedButton(
        onClick = { showSvgDialog = true },
        modifier = Modifier.testTag("export_svg_button"),
      ) {
        Icon(Icons.Rounded.Code, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("SVG")
      }
    }

    // Live Preview Card
    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .padding(16.dp)
          .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        BotVectorAvatar(
          bot = draftBot,
          expression = previewExpression,
          size = 156.dp,
          animate = true,
          modifier = Modifier.testTag("studio_preview_avatar"),
        )
        Text(
          text = "Expression Preview: ${previewExpression.label}",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          BotExpression.entries.forEach { expr ->
            Surface(
              onClick = { previewExpression = expr },
              shape = RoundedCornerShape(50),
              color = if (previewExpression == expr) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.surfaceVariant
              },
            ) {
              Text(
                text = expr.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (previewExpression == expr) {
                  MaterialTheme.colorScheme.onPrimary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
        }
      }
    }

    // Character Name & Mission (Editable anytime)
    OutlinedTextField(
      value = name,
      onValueChange = { name = it.take(40) },
      label = { Text("Character Name") },
      placeholder = { Text("Give this character a name (e.g., Bimbo, Kenji, Momo)") },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("bot_name_input"),
    )

    OutlinedTextField(
      value = mission,
      onValueChange = { mission = it.take(160) },
      label = { Text("Bot Mission / Description") },
      placeholder = { Text("e.g., I translate stuff! or Critique drafts harshly") },
      supportingText = {
        Text("Casual descriptions like \"I translate stuff!\" are automatically translated into OpenClaw instructions and synced.")
      },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("bot_mission_input"),
    )

    // Archetype Selector (8 Templates)
    Text("Character Template (8 Styles)", style = MaterialTheme.typography.titleMedium)
    val defaultPresetNames = remember {
      BotArchetype.entries.map { StarterBots.presetFor(it).defaultName }.toSet()
    }
    val defaultPresetMissions = remember {
      BotArchetype.entries.map { StarterBots.presetFor(it).defaultMission }.toSet()
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val columns = if (maxWidth >= 560.dp) 4 else 2
      val rows = remember(columns) { BotArchetype.entries.chunked(columns) }
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            rowItems.forEach { style ->
              val selected = archetype == style
              val preset = StarterBots.presetFor(style)
              Surface(
                onClick = {
                  archetype = style
                  primaryHex = preset.primaryHex
                  accentHex = preset.accentHex
                  hatStyle = preset.hat
                  accessoryStyle = preset.accessory
                  if (existingBot == null) {
                    if (name.isBlank() || name in defaultPresetNames) {
                      name = preset.defaultName
                    }
                    if (mission.isBlank() || mission in defaultPresetMissions) {
                      mission = preset.defaultMission
                    }
                    roleTitle = preset.defaultRole
                    persona = preset.persona
                  }
                },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                  width = if (selected) 2.dp else 1.dp,
                  color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("archetype_card_${style.name}"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  BotVectorAvatar(
                    archetype = style,
                    hatStyle = preset.hat,
                    accessoryStyle = preset.accessory,
                    primaryColor = parseHexColor(preset.primaryHex, Color(0xFFEEA05E)),
                    accentColor = parseHexColor(preset.accentHex, Color(0xFFDC4D21)),
                    expression = BotExpression.HAPPY,
                    size = 50.dp,
                    animate = false,
                  )
                  Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                      text = style.displayName,
                      style = MaterialTheme.typography.labelLarge,
                      maxLines = 1,
                    )
                    Text(
                      text = preset.defaultName,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                    )
                  }
                }
              }
            }
            repeat(columns - rowItems.size) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }

    // Hat Picker
    Text("Hat & Headgear", style = MaterialTheme.typography.titleMedium)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      HatStyle.entries.forEach { hat ->
        val selected = hatStyle == hat
        Surface(
          onClick = { hatStyle = hat },
          shape = RoundedCornerShape(50),
          color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
        ) {
          Text(
            text = hat.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }
    }

    // Accessory Picker
    Text("Glasses & Accessories", style = MaterialTheme.typography.titleMedium)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AccessoryStyle.entries.forEach { acc ->
        val selected = accessoryStyle == acc
        Surface(
          onClick = { accessoryStyle = acc },
          shape = RoundedCornerShape(50),
          color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
        ) {
          Text(
            text = acc.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }
    }

    // Colors
    Text("Face / Body Color", style = MaterialTheme.typography.titleMedium)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      StarterBots.primaryColorSwatches.forEach { (hex, _) ->
        val selected = primaryHex.equals(hex, ignoreCase = true)
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(parseHexColor(hex, Color(0xFFEEA05E)))
            .border(
              width = if (selected) 3.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
              shape = CircleShape,
            )
            .clickable { primaryHex = hex },
          contentAlignment = Alignment.Center,
        ) {
          if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.Black)
          }
        }
      }
    }

    Text("Cheeks & Nose Accent", style = MaterialTheme.typography.titleMedium)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      StarterBots.accentColorSwatches.forEach { (hex, _) ->
        val selected = accentHex.equals(hex, ignoreCase = true)
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(parseHexColor(hex, Color(0xFFDC4D21)))
            .border(
              width = if (selected) 3.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
              shape = CircleShape,
            )
            .clickable { accentHex = hex },
          contentAlignment = Alignment.Center,
        ) {
          if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White)
          }
        }
      }
    }

    // OpenClaw Function, LLM Default & Personality Fields
    Text("Bot Function for OpenClaw", style = MaterialTheme.typography.titleMedium)
    val defaultFnAgentIds = remember { BotFunctionPreset.entries.map { it.suggestedAgentId }.toSet() }
    val defaultFnMissions = remember { BotFunctionPreset.entries.map { it.defaultMission }.toSet() }
    val defaultAllMissions = remember {
      defaultFnMissions + BotArchetype.entries.map { StarterBots.presetFor(it).defaultMission }
    }
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      BotFunctionPreset.entries.forEach { fn ->
        val selected = functionPreset == fn
        Surface(
          onClick = {
            functionPreset = fn
            if (agentId.isBlank() || agentId in defaultFnAgentIds || agentId == "ops") {
              agentId = fn.suggestedAgentId
            }
            if (mission.isBlank() || mission in defaultAllMissions) {
              mission = fn.defaultMission
            }
            roleTitle = fn.displayName
            if (existingBot == null) {
              persona = fn.openClawDirective
            }
          },
          shape = RoundedCornerShape(50),
          color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          ),
          modifier = Modifier.testTag("bot_function_chip_${fn.name}"),
        ) {
          Text(
            text = fn.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }
    }
    Text(
      text = "OpenClaw Directive: ${functionPreset.openClawDirective}",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Model Override", style = MaterialTheme.typography.titleMedium)
    Text(
      text = "Defaults to Use Host Default ($globalVeniceModel / Host config), or lock this bot to a specific coding or chat model.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val providerDefaultModels = remember { OpenClawLlmProvider.entries.map { it.defaultModel }.toSet() }
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OpenClawLlmProvider.entries.forEach { provider ->
        val selected = llmProvider == provider
        Surface(
          onClick = {
            llmProvider = provider
            if (provider == OpenClawLlmProvider.HOST_DEFAULT || provider == OpenClawLlmProvider.OPENCLAW_AUTO) {
              llmModel = ""
            } else if (provider == OpenClawLlmProvider.VENICE_AI) {
              if (llmModel.isBlank() || llmModel in providerDefaultModels) {
                llmModel = globalVeniceModel.ifBlank { provider.defaultModel }
              }
            } else if (llmModel.isBlank() || llmModel in providerDefaultModels) {
              llmModel = provider.defaultModel
            }
          },
          shape = RoundedCornerShape(50),
          color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
          border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
          ),
          modifier = Modifier.testTag("bot_llm_chip_${provider.name}"),
        ) {
          Text(
            text = provider.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }
    }

    if (llmProvider == OpenClawLlmProvider.VENICE_AI) {
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("bot_venice_override_panel"),
      ) {
        VeniceModelPickerPanel(
          selectedModelId = llmModel.ifBlank { globalVeniceModel },
          onSelectModelId = { llmModel = it },
          availableModels = veniceLiveModels,
          isFetchingModels = isFetchingVeniceModels,
          onFetchLiveModels = onFetchVeniceModels,
          showApiKeyOverride = false,
          testTagPrefix = "bot_venice",
          modifier = Modifier.padding(12.dp),
        )
      }
    } else if (llmProvider != OpenClawLlmProvider.HOST_DEFAULT && llmProvider != OpenClawLlmProvider.OPENCLAW_AUTO) {
      if (llmProvider == OpenClawLlmProvider.LOCAL_LLM) {
        val localPresets = remember {
          listOf(
            "ollama/translategemma",
            "ollama/qwen3.5-fast",
            "ollama/qwen2.5-coder:32b",
            "ollama/llama3.3",
          )
        }
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          localPresets.forEach { presetModel ->
            val picked = llmModel.equals(presetModel, ignoreCase = true)
            Surface(
              onClick = { llmModel = presetModel },
              shape = RoundedCornerShape(50),
              color = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
              border = BorderStroke(
                width = if (picked) 2.dp else 1.dp,
                color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
              ),
              modifier = Modifier.testTag("local_model_chip_$presetModel"),
            ) {
              Text(
                text = presetModel.removePrefix("ollama/"),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              )
            }
          }
        }
      }
      OutlinedTextField(
        value = llmModel,
        onValueChange = { llmModel = it.take(120) },
        label = { Text("Bot Model ID on OpenClaw") },
        placeholder = { Text(llmProvider.defaultModel.ifBlank { "provider/model-name" }) },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("bot_llm_model_input"),
      )
    }

    val effectiveHostLlm = if (llmProvider == OpenClawLlmProvider.HOST_DEFAULT) {
      val hostProv = activeHost?.defaultLlmProviderEnum ?: OpenClawLlmProvider.OPENCLAW_AUTO
      val hostMod = activeHost?.defaultLlmModel?.takeIf { it.isNotBlank() } ?: hostProv.defaultModel
      if (hostMod.isNotBlank()) "${hostProv.displayName} ($hostMod)" else hostProv.displayName
    } else {
      val mod = llmModel.trim().ifBlank { llmProvider.defaultModel }
      if (mod.isNotBlank()) "${llmProvider.displayName} ($mod)" else llmProvider.displayName
    }

    Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "OpenClaw Connection: ${activeHost?.label ?: "No host configured"} • LLM: $effectiveHostLlm",
          style = MaterialTheme.typography.labelMedium,
        )
        Text(
          text = syncFeedback ?: if (existingBot?.openClawSynced == true) {
            "Synced with OpenClaw host (saving updates OpenClaw automatically)."
          } else {
            "Saving sends agent.register to OpenClaw and attaches function & LLM details to every message."
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val cleanAgentId = draftBot.openClawAgentId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "").ifBlank { "main" }
        TextButton(
          onClick = {
            clipboard.setText(AnnotatedString("openclaw agents add $cleanAgentId"))
            syncFeedback = "Copied 'openclaw agents add $cleanAgentId' to paste on your Host PC if not using a webhook bridge."
          },
          modifier = Modifier.testTag("copy_openclaw_cli_button"),
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Copy PC 'openclaw agents add $cleanAgentId' Command")
        }
      }
    }

    OutlinedTextField(
      value = roleTitle,
      onValueChange = { roleTitle = it.take(60) },
      label = { Text("Specialty Role") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = agentId,
      onValueChange = { agentId = it.take(80) },
      label = { Text("OpenClaw Agent ID") },
      placeholder = { Text("main, translator, proofreader, critic, coder") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = sessionKey,
      onValueChange = { sessionKey = it.take(120) },
      label = { Text("Isolated Session Key (Optional)") },
      placeholder = { Text("agent:${draftBot.resolvedBotDudeSlug()}:main") },
      supportingText = {
        Text("Blank auto-assigns isolated lane: ${draftBot.effectivePrimarySessionKey}")
      },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("bot_session_key_input"),
    )

    OutlinedTextField(
      value = workspacePath,
      onValueChange = { workspacePath = it.take(260) },
      label = { Text("Isolated Workspace Path (Optional)") },
      placeholder = { Text("~/workspaces/${draftBot.resolvedBotDudeSlug()}") },
      singleLine = true,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("bot_workspace_path_input"),
    )

    Text("Tool Permissions", style = MaterialTheme.typography.titleMedium)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AgentToolPermission.entries.forEach { perm ->
        val enabledPerm = perm.wireKey in selectedPermissions
        Surface(
          onClick = {
            selectedPermissions = if (enabledPerm) {
              if (selectedPermissions.size > 1) selectedPermissions - perm.wireKey else selectedPermissions
            } else {
              selectedPermissions + perm.wireKey
            }
          },
          shape = RoundedCornerShape(50),
          color = if (enabledPerm) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          border = BorderStroke(
            width = if (enabledPerm) 2.dp else 1.dp,
            color = if (enabledPerm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
          ),
          modifier = Modifier.testTag("tool_permission_chip_${perm.wireKey}"),
        ) {
          Text(
            text = if (enabledPerm) "✓ ${perm.label}" else perm.label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }
    }

    OutlinedTextField(
      value = persona,
      onValueChange = { persona = it.take(500) },
      label = { Text("Bot Personality & Instruction Style") },
      minLines = 2,
      maxLines = 4,
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedButton(
      onClick = {
        onSyncWithOpenClaw(draftBot) { _, message ->
          syncFeedback = message
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("studio_sync_openclaw_button"),
    ) {
      Icon(Icons.Rounded.Sync, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Test & Connect Bot to OpenClaw")
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (existingBot != null && existingBot.id.isNotBlank()) {
        OutlinedButton(
          onClick = { onDeleteBot(existingBot.id) },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier
            .height(52.dp)
            .testTag("studio_delete_bot_button"),
        ) {
          Icon(Icons.Rounded.Delete, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("Delete")
        }
      }

      Button(
        onClick = { onSaveBot(draftBot) },
        modifier = Modifier
          .weight(1f)
          .height(52.dp)
          .testTag("studio_save_bot_button"),
      ) {
        Icon(Icons.Rounded.Save, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (existingBot == null) "Add & Connect Bot" else "Save & Sync Bot")
      }
    }

    Spacer(Modifier.height(24.dp))
  }

  if (showSvgDialog) {
    val svgCode = remember(draftBot, previewExpression) {
      BotSvgGenerator.generateSvg(draftBot, previewExpression)
    }
    AlertDialog(
      onDismissRequest = { showSvgDialog = false },
      title = { Text("Vector SVG (${draftBot.name})") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Portable SVG markup for Android, web, or your future Windows desktop app:",
            style = MaterialTheme.typography.bodySmall,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .verticalScroll(rememberScrollState()),
          ) {
            Text(
              text = svgCode,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_SUBJECT, "${draftBot.name}.svg")
              putExtra(Intent.EXTRA_TEXT, svgCode)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Bot SVG"))
          },
        ) {
          Icon(Icons.Rounded.Share, contentDescription = null, Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Share SVG")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            clipboard.setText(AnnotatedString(svgCode))
            showSvgDialog = false
          },
        ) {
          Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Copy & Close")
        }
      },
    )
  }
}
