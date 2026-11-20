package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.openclaw.OpenClawPingResult
import com.example.model.AiRoutingMode
import com.example.model.HostStatus
import com.example.model.OpenClawHost
import com.example.model.OpenClawLlmProvider
import com.example.model.VeniceModelCatalog
import com.example.model.VeniceModelInfo
import com.example.ui.components.VeniceModelPickerPanel
import com.example.viewmodel.UiState

@Composable
fun HostSettingsScreen(
  hostsState: UiState<List<OpenClawHost>>,
  currentUserId: String,
  userEmail: String?,
  pingResult: OpenClawPingResult?,
  savedVeniceApiKey: String = "",
  savedGoogleApiKey: String = "",
  savedGlobalVeniceModel: String = VeniceModelCatalog.defaultModelId,
  veniceLiveModels: List<VeniceModelInfo> = VeniceModelCatalog.popularPresets,
  isFetchingVeniceModels: Boolean = false,
  onFetchVeniceModels: (String) -> Unit = {},
  onBack: () -> Unit = {},
  onSaveHost: (OpenClawHost, String, String, String) -> Unit,
  onDeleteHost: (String) -> Unit,
  onPingHost: (OpenClawHost) -> Unit,
  onSignOut: () -> Unit,
) {
  val hosts = (hostsState as? UiState.Success)?.data.orEmpty()
  val primaryHost = hosts.firstOrNull()

  var editingId by remember(primaryHost?.id) { mutableStateOf(primaryHost?.id.orEmpty()) }
  var label by remember(primaryHost?.id) { mutableStateOf(primaryHost?.label ?: "My OpenClaw PC") }
  var baseUrl by remember(primaryHost?.id) { mutableStateOf(primaryHost?.baseUrl ?: "http://192.168.1.50:18789") }
  var controlUrl by remember(primaryHost?.id) { mutableStateOf(primaryHost?.controlUrl ?: "http://192.168.1.50:18789/control") }
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
  var autoParseCasual by remember(primaryHost?.id) { mutableStateOf(primaryHost?.autoParseCasual ?: true) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
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
            baseUrl = host.baseUrl
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
              Text(host.baseUrl, style = MaterialTheme.typography.labelSmall)
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
          baseUrl = "http://192.168.1.100:18789"
          controlUrl = "http://192.168.1.100:18789/control"
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
        OutlinedTextField(
          value = baseUrl,
          onValueChange = { baseUrl = it.take(300) },
          label = { Text("OpenClaw Gateway URL") },
          placeholder = { Text("http://192.168.1.50:18789 or https://...") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("host_url_input"),
        )
        OutlinedTextField(
          value = controlUrl,
          onValueChange = { controlUrl = it.take(300) },
          label = { Text("Take Over Computer Control URL (Web/noVNC)") },
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

        if (defaultLlmProvider != OpenClawLlmProvider.OPENCLAW_AUTO &&
          defaultLlmProvider != OpenClawLlmProvider.VENICE_AI
        ) {
          OutlinedTextField(
            value = defaultLlmModel,
            onValueChange = { defaultLlmModel = it.take(120) },
            label = { Text("Default Model ID for OpenClaw") },
            placeholder = { Text(defaultLlmProvider.defaultModel.ifBlank { "provider/model-name" }) },
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

        val draftHost = OpenClawHost(
          id = editingId,
          userId = currentUserId,
          label = label,
          baseUrl = baseUrl,
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
            Text("Test Ping")
          }
          Button(
            onClick = { onSaveHost(draftHost, veniceApiKey, googleApiKey, globalVeniceModel) },
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

    Spacer(Modifier.height(20.dp))
  }
}
