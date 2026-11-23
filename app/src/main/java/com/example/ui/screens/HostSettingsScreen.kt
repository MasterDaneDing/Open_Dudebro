package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.openclaw.BridgeFolderStatus
import com.example.data.openclaw.OpenClawPingResult
import com.example.model.AiRoutingMode
import com.example.model.HostConnectionMode
import com.example.model.HostStatus
import com.example.model.OpenClawHost
import com.example.model.OpenClawLlmProvider
import com.example.model.VeniceModelCatalog
import com.example.model.VeniceModelInfo
import com.example.ui.components.VeniceModelPickerPanel
import com.example.viewmodel.UiState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun HostSettingsScreen(
  hostsState: UiState<List<OpenClawHost>>,
  currentUserId: String,
  userEmail: String?,
  pingResult: OpenClawPingResult?,
  savedVeniceApiKey: String = "",
  savedGoogleApiKey: String = "",
  savedGlobalVeniceModel: String = VeniceModelCatalog.defaultModelId,
  savedDevicePairingId: String = "claw-dudebro-apk",
  savedBridgeEnabled: Boolean = false,
  savedBridgeFolderUri: String = "",
  bridgeStatus: BridgeFolderStatus? = null,
  onInspectBridgeFolder: (String) -> Unit = {},
  veniceLiveModels: List<VeniceModelInfo> = VeniceModelCatalog.popularPresets,
  isFetchingVeniceModels: Boolean = false,
  onFetchVeniceModels: (String) -> Unit = {},
  onBack: () -> Unit = {},
  onSaveHost: (
    host: OpenClawHost,
    veniceKey: String,
    googleKey: String,
    globalVeniceModel: String,
    devicePairingId: String,
    bridgeEnabled: Boolean,
    bridgeFolderUri: String,
  ) -> Unit,
  onDeleteHost: (String) -> Unit,
  onPingHost: (OpenClawHost) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val hosts = (hostsState as? UiState.Success)?.data.orEmpty()
  val primaryHost = hosts.firstOrNull()

  var editingId by remember(primaryHost?.id) { mutableStateOf(primaryHost?.id.orEmpty()) }
  var label by remember(primaryHost?.id) { mutableStateOf(primaryHost?.label ?: "My OpenClaw PC") }
  var connectionMode by remember(primaryHost?.id) { mutableStateOf(primaryHost?.connectionModeEnum ?: HostConnectionMode.LAN) }
  var lanUrl by remember(primaryHost?.id) {
    mutableStateOf(
      primaryHost?.effectiveLanUrl?.ifBlank { OpenClawHost.DEFAULT_LAN_URL } ?: OpenClawHost.DEFAULT_LAN_URL
    )
  }
  var tailscaleUrl by remember(primaryHost?.id) {
    mutableStateOf(
      primaryHost?.effectiveTailscaleUrl?.ifBlank { OpenClawHost.DEFAULT_TAILSCALE_URL } ?: OpenClawHost.DEFAULT_TAILSCALE_URL
    )
  }
  var controlUrl by remember(primaryHost?.id) { mutableStateOf(primaryHost?.controlUrl.orEmpty()) }
  var gatewayToken by remember(primaryHost?.id) { mutableStateOf(primaryHost?.gatewayToken.orEmpty()) }
  var aiMode by remember(primaryHost?.id) { mutableStateOf(primaryHost?.aiModeEnum ?: AiRoutingMode.HYBRID_GEMINI_OPENCLAW) }
  var defaultLlmProvider by remember(primaryHost?.id) {
    mutableStateOf(primaryHost?.defaultLlmProviderEnum ?: OpenClawLlmProvider.OPENCLAW_AUTO)
  }
  var defaultLlmModel by remember(primaryHost?.id) {
    mutableStateOf(primaryHost?.defaultLlmModel.orEmpty())
  }
  var globalVeniceModel by remember(savedGlobalVeniceModel, primaryHost?.id) {
    mutableStateOf(
      if (primaryHost?.defaultLlmProviderEnum == OpenClawLlmProvider.VENICE_AI &&
        primaryHost.defaultLlmModel.isNotBlank()
      ) {
        primaryHost.defaultLlmModel
      } else {
        savedGlobalVeniceModel.ifBlank { VeniceModelCatalog.defaultModelId }
      }
    )
  }
  var localLlmEndpoint by remember(primaryHost?.id) {
    mutableStateOf(primaryHost?.localLlmEndpoint?.ifBlank { "http://127.0.0.1:11434/v1" } ?: "http://127.0.0.1:11434/v1")
  }
  var veniceApiKey by remember(savedVeniceApiKey) { mutableStateOf(savedVeniceApiKey) }
  var googleApiKey by remember(savedGoogleApiKey) { mutableStateOf(savedGoogleApiKey) }
  var devicePairingId by remember(savedDevicePairingId) {
    mutableStateOf(savedDevicePairingId.ifBlank { "claw-dudebro-apk" })
  }
  var bridgeEnabled by remember(savedBridgeEnabled) { mutableStateOf(savedBridgeEnabled) }
  var bridgeFolderUri by remember(savedBridgeFolderUri) { mutableStateOf(savedBridgeFolderUri) }
  var autoParseCasual by remember(primaryHost?.id) { mutableStateOf(primaryHost?.autoParseCasual ?: true) }

  val bridgeFolderPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    if (uri != null) {
      try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
      } catch (_: Exception) {
      }
      bridgeFolderUri = uri.toString()
      bridgeEnabled = true
      onInspectBridgeFolder(uri.toString())
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .size(48.dp)
          .testTag("host_back_to_chats_button"),
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = "Back to Bot Dudes",
          tint = Color.White,
        )
      }
      Icon(
        painter = painterResource(id = R.drawable.ic_min_host),
        contentDescription = null,
        tint = Color(0xFFFFB2EB),
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = "OpenClaw Settings",
        style = MaterialTheme.typography.titleLarge.copy(
          shadow = Shadow(color = Color(0x881A0033), offset = Offset(0f, 2f), blurRadius = 4f)
        ),
        color = Color.White,
        fontWeight = FontWeight.ExtraBold,
      )
    }

    if (hosts.isNotEmpty()) {
      Text(
        text = "Saved Host Computers",
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFFFFE57F),
        fontWeight = FontWeight.ExtraBold,
      )
      hosts.forEach { host ->
        val selected = host.id == editingId
        Surface(
          onClick = {
            editingId = host.id
            label = host.label
            connectionMode = host.connectionModeEnum
            lanUrl = host.effectiveLanUrl
            tailscaleUrl = host.effectiveTailscaleUrl
            controlUrl = host.controlUrl
            gatewayToken = host.gatewayToken
            aiMode = host.aiModeEnum
            defaultLlmProvider = host.defaultLlmProviderEnum
            defaultLlmModel = host.defaultLlmModel
            localLlmEndpoint = host.localLlmEndpoint.ifBlank { "http://127.0.0.1:11434/v1" }
            autoParseCasual = host.autoParseCasual
          },
          shape = MaterialTheme.shapes.medium,
          color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(Icons.Rounded.DesktopWindows, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
              Text(host.label, style = MaterialTheme.typography.titleSmall)
              Text(
                text = "${host.connectionModeEnum.shortPill} • ${host.effectiveBaseUrl}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Surface(
              shape = RoundedCornerShape(50),
              color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
              Text(
                text = host.lastStatusEnum.label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
            if (hosts.size > 1) {
              IconButton(
                onClick = { onDeleteHost(host.id) },
                modifier = Modifier.size(48.dp),
              ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete host")
              }
            }
          }
        }
      }
      OutlinedButton(
        onClick = {
          editingId = ""
          label = "New OpenClaw PC"
          connectionMode = HostConnectionMode.LAN
          lanUrl = OpenClawHost.DEFAULT_LAN_URL
          tailscaleUrl = OpenClawHost.DEFAULT_TAILSCALE_URL
          controlUrl = ""
          gatewayToken = ""
          aiMode = AiRoutingMode.HYBRID_GEMINI_OPENCLAW
          defaultLlmProvider = OpenClawLlmProvider.OPENCLAW_AUTO
          defaultLlmModel = ""
          localLlmEndpoint = "http://127.0.0.1:11434/v1"
          autoParseCasual = true
        },
      ) {
        Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Add Another Host")
      }
    }

    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = label,
          onValueChange = { label = it.take(50) },
          label = { Text("Host Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        Text("Active Connection Mode", style = MaterialTheme.typography.titleSmall)
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            HostConnectionMode.entries.forEach { modeOption ->
              val isSelected = connectionMode == modeOption
              Surface(
                onClick = { connectionMode = modeOption },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier
                  .weight(1f)
                  .testTag("host_mode_toggle_${modeOption.name}"),
              ) {
                Text(
                  text = modeOption.displayName,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                )
              }
            }
          }
        }

        OutlinedTextField(
          value = lanUrl,
          onValueChange = { lanUrl = it.take(300) },
          label = { Text("Home Mode (LAN) Host / URL") },
          placeholder = { Text("http://192.168.68.53:18789") },
          supportingText = if (connectionMode == HostConnectionMode.LAN) {
            { Text("Primary route (default port 18789). Auto-falls back to Tailscale if LAN is unreachable.") }
          } else null,
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("host_url_input"),
        )
        OutlinedTextField(
          value = tailscaleUrl,
          onValueChange = { tailscaleUrl = it.take(300) },
          label = { Text("Outside Mode (Tailscale) Host / URL") },
          placeholder = { Text("http://100.91.98.120:18789") },
          supportingText = if (connectionMode == HostConnectionMode.TAILSCALE) {
            { Text("Active right now — remote Tailscale route (default port 18789).") }
          } else {
            { Text("Automatic fallback if LAN is unreachable. Shares the same Gateway Bearer Token.") }
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("host_tailscale_url_input"),
        )
        OutlinedTextField(
          value = controlUrl,
          onValueChange = { controlUrl = it.take(300) },
          label = { Text("Take Over Dashboard URL Override (Optional)") },
          placeholder = { Text("Optional override (blank = OpenClaw root /?token=...)") },
          supportingText = { Text("Leave blank to open OpenClaw's built-in root web dashboard automatically.") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        var showToken by remember { mutableStateOf(false) }
        OutlinedTextField(
          value = gatewayToken,
          onValueChange = { gatewayToken = it.take(256) },
          label = { Text("Gateway Bearer Token") },
          placeholder = { Text("Paste OpenClaw auth token (required on LAN / 0.0.0.0 / Tailscale)") },
          supportingText = {
            Text("Bind OpenClaw on your mini PC to 0.0.0.0, LAN IP, or Tailscale and paste its auth token here.")
          },
          visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            IconButton(onClick = { showToken = !showToken }) {
              Icon(
                imageVector = if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (showToken) "Hide token" else "Show token",
              )
            }
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("host_gateway_token_input"),
        )

        OutlinedTextField(
          value = devicePairingId,
          onValueChange = { devicePairingId = it.take(120) },
          label = { Text("Device Pairing ID") },
          placeholder = { Text("claw-dudebro-apk") },
          supportingText = {
            Text("Attached as x-openclaw-device-id alongside Authorization: Bearer on both chat and /__openclaw__/assistant-media requests.")
          },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("host_device_pairing_id_input"),
        )

        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Drive Bridge Mode (APK-Bridge)", style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "Write task JSONs to instructions/ and watch processed/ & outbox/ instead of direct HTTP.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = bridgeEnabled,
                onCheckedChange = { bridgeEnabled = it },
                modifier = Modifier.testTag("host_drive_bridge_switch"),
              )
            }
            AnimatedVisibility(visible = bridgeEnabled) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = bridgeFolderUri,
                  onValueChange = { bridgeFolderUri = it.take(500) },
                  label = { Text("APK-Bridge Folder URI or Synced Path") },
                  placeholder = { Text("Pick My Drive/OpenClaw/APK-Bridge or enter local path") },
                  singleLine = true,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("host_bridge_folder_input"),
                )
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  OutlinedButton(
                    onClick = { bridgeFolderPicker.launch(null) },
                    modifier = Modifier
                      .weight(1f)
                      .height(48.dp)
                      .testTag("host_pick_bridge_folder_button"),
                  ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pick Folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                  OutlinedButton(
                    onClick = { onInspectBridgeFolder(bridgeFolderUri) },
                    enabled = bridgeFolderUri.isNotBlank(),
                    modifier = Modifier
                      .height(48.dp)
                      .testTag("host_check_bridge_button"),
                  ) {
                    Text("Check Bridge")
                  }
                }
                if (bridgeStatus != null) {
                  Text(
                    text = bridgeStatus.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bridgeStatus.valid) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                  )
                }
              }
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Auto-Parse Casual Chat", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Rewrite casual prompts into structured OpenClaw instructions.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = autoParseCasual,
            onCheckedChange = { autoParseCasual = it },
          )
        }

        Text("AI & Instruction Routing", style = MaterialTheme.typography.titleSmall)
        AiRoutingMode.entries.forEach { mode ->
          Surface(
            onClick = { aiMode = mode },
            shape = MaterialTheme.shapes.small,
            color = if (aiMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = aiMode == mode, onClick = { aiMode = mode })
              Column(modifier = Modifier.weight(1f)) {
                Text(mode.displayName, style = MaterialTheme.typography.labelLarge)
                Text(mode.description, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }

        Text("Default OpenClaw LLM Provider", style = MaterialTheme.typography.titleSmall)
        Text(
          text = "Pick which LLM OpenClaw defaults to for bots unless overridden per bot.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val defaultModels = remember { OpenClawLlmProvider.hostOptions.map { it.defaultModel }.toSet() }
        val selectHostProvider: (OpenClawLlmProvider) -> Unit = { provider ->
          defaultLlmProvider = provider
          if (provider == OpenClawLlmProvider.VENICE_AI) {
            defaultLlmModel = globalVeniceModel.ifBlank { VeniceModelCatalog.defaultModelId }
          } else if (defaultLlmModel.isBlank() || defaultLlmModel in defaultModels) {
            defaultLlmModel = provider.defaultModel
          }
        }
        OpenClawLlmProvider.hostOptions.forEach { provider ->
          val isVenice = provider == OpenClawLlmProvider.VENICE_AI
          val isSelected = defaultLlmProvider == provider
          Surface(
            onClick = { selectHostProvider(provider) },
            shape = MaterialTheme.shapes.small,
            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            border = if (isSelected) BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("host_llm_provider_${provider.name}"),
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = isSelected,
                  onClick = { selectHostProvider(provider) },
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(provider.displayName, style = MaterialTheme.typography.labelLarge)
                  Text(provider.description, style = MaterialTheme.typography.bodySmall)
                }
              }

              if (isVenice) {
                AnimatedVisibility(visible = isSelected) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    HorizontalDivider()
                    VeniceModelPickerPanel(
                      selectedModelId = globalVeniceModel,
                      onSelectModelId = { chosen ->
                        globalVeniceModel = chosen
                        if (defaultLlmProvider == OpenClawLlmProvider.VENICE_AI) {
                          defaultLlmModel = chosen
                        }
                      },
                      availableModels = veniceLiveModels,
                      isFetchingModels = isFetchingVeniceModels,
                      onFetchLiveModels = { onFetchVeniceModels(veniceApiKey) },
                      showApiKeyOverride = true,
                      apiKeyValue = veniceApiKey,
                      onApiKeyChange = { veniceApiKey = it },
                      testTagPrefix = "host_venice",
                    )
                  }
                }
              }
            }
          }
        }

        if (defaultLlmProvider != OpenClawLlmProvider.VENICE_AI) {
          OutlinedTextField(
            value = defaultLlmModel,
            onValueChange = { defaultLlmModel = it.take(120) },
            label = {
              Text(
                if (defaultLlmProvider == OpenClawLlmProvider.OPENCLAW_AUTO) {
                  "Advanced: x-openclaw-model Override (Optional)"
                } else {
                  "Default Model ID for OpenClaw"
                }
              )
            },
            placeholder = {
              Text(
                defaultLlmProvider.defaultModel.ifBlank { "venice/deepseek-v4-1-flash-uncensored" }
              )
            },
            supportingText = if (defaultLlmProvider == OpenClawLlmProvider.OPENCLAW_AUTO) {
              { Text("All chat requests target model=\"openclaw/apk\". Leave blank for agent default or enter e.g. venice/deepseek-v4-1-flash-uncensored.") }
            } else null,
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("host_default_model_input"),
          )
        }

        if (defaultLlmProvider == OpenClawLlmProvider.GOOGLE) {
          OutlinedTextField(
            value = googleApiKey,
            onValueChange = { googleApiKey = it.take(256) },
            label = { Text("Google API Key for OpenClaw (optional, device-only)") },
            placeholder = { Text("Leave blank if already set in OpenClaw host config") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("host_google_key_input"),
          )
        }

        if (defaultLlmProvider == OpenClawLlmProvider.LOCAL_LLM) {
          OutlinedTextField(
            value = localLlmEndpoint,
            onValueChange = { localLlmEndpoint = it.take(300) },
            label = { Text("Local LLM Endpoint (Ollama / LM Studio / vLLM)") },
            placeholder = { Text("http://127.0.0.1:11434/v1") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("host_local_llm_url_input"),
          )
        }

        if (pingResult != null) {
          Text(
            text = pingResult.detail,
            style = MaterialTheme.typography.labelSmall,
            color = if (pingResult.reachable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
          )
        }

        val resolvedDefaultModel = if (defaultLlmProvider == OpenClawLlmProvider.VENICE_AI) {
          globalVeniceModel.trim().ifBlank { VeniceModelCatalog.defaultModelId }
        } else {
          defaultLlmModel
        }

        val resolvedActiveUrl = when (connectionMode) {
          HostConnectionMode.TAILSCALE -> tailscaleUrl.trim().ifBlank { lanUrl.trim() }
          HostConnectionMode.LAN -> lanUrl.trim()
        }

        val draftHost = OpenClawHost(
          id = editingId,
          userId = currentUserId,
          label = label,
          baseUrl = resolvedActiveUrl,
          lanUrl = lanUrl,
          tailscaleUrl = tailscaleUrl,
          connectionMode = connectionMode.name,
          controlUrl = controlUrl,
          gatewayToken = gatewayToken,
          aiMode = aiMode.name,
          defaultLlmProvider = defaultLlmProvider.name,
          defaultLlmModel = resolvedDefaultModel,
          localLlmEndpoint = localLlmEndpoint,
          autoParseCasual = autoParseCasual,
          lastStatus = primaryHost?.lastStatus ?: HostStatus.UNVERIFIED.name,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = { onPingHost(draftHost) },
            modifier = Modifier
              .height(48.dp)
              .testTag("test_host_connection_button"),
          ) {
            Icon(Icons.Rounded.WifiTethering, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Test Connection")
          }
          Button(
            onClick = {
              onSaveHost(
                draftHost,
                veniceApiKey,
                googleApiKey,
                globalVeniceModel,
                devicePairingId,
                bridgeEnabled,
                bridgeFolderUri,
              )
            },
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("save_host_button"),
          ) {
            Icon(Icons.Rounded.Save, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Save Host")
          }
        }
      }
    }

    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Account & Cross-Device Sync", style = MaterialTheme.typography.titleMedium)
        Text(
          text = "Signed in as ${userEmail ?: "Google User"}. Your bots, SVG customizations, linked files, and host profiles sync via Firestore—ready for a future Windows PC Claw Dudebro companion.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
          onClick = onSignOut,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("sign_out_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Sign Out")
        }
      }
    }

    Spacer(Modifier.height(32.dp))
  }
}
