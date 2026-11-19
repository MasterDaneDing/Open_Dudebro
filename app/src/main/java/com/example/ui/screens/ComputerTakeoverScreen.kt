package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.openclaw.HostControlAction
import com.example.data.openclaw.OpenClawPingResult
import com.example.model.BotExpression
import com.example.model.CompanionBot
import com.example.model.HostStatus
import com.example.model.OpenClawHost
import com.example.ui.avatar.BotVectorAvatar
import com.example.viewmodel.UiState

@Composable
fun ComputerTakeoverScreen(
  hostsState: UiState<List<OpenClawHost>>,
  activeBot: CompanionBot?,
  takeoverActive: Boolean,
  pingResult: OpenClawPingResult?,
  controlLog: List<String>,
  onPingHost: (OpenClawHost) -> Unit,
  onSendControl: (OpenClawHost?, HostControlAction, String) -> Unit,
  onOpenHostSettings: () -> Unit,
) {
  val context = LocalContext.current
  val hosts = (hostsState as? UiState.Success)?.data.orEmpty()
  val activeHost = hosts.firstOrNull()

  var showLiveWebConsole by remember { mutableStateOf(false) }
  var remoteCommandInput by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (activeBot != null) {
        BotVectorAvatar(
          bot = activeBot,
          expression = if (takeoverActive) BotExpression.ALERT else BotExpression.WORKING,
          size = 64.dp,
          animate = true,
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text("Take Over Host PC", style = MaterialTheme.typography.headlineSmall)
        Text(
          text = activeHost?.let { "${it.label} (${it.baseUrl})" } ?: "No OpenClaw host configured",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Status & Takeover Toggle Card
    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(
        containerColor = if (takeoverActive) {
          MaterialTheme.colorScheme.tertiaryContainer
        } else {
          MaterialTheme.colorScheme.surface
        },
      ),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Surface(
            shape = RoundedCornerShape(50),
            color = when (activeHost?.lastStatusEnum) {
              HostStatus.ONLINE -> MaterialTheme.colorScheme.secondaryContainer
              HostStatus.OFFLINE -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
              else -> MaterialTheme.colorScheme.surfaceVariant
            },
          ) {
            Text(
              text = "Host: ${activeHost?.lastStatusEnum?.label ?: "Unconfigured"}",
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (activeHost != null) {
              OutlinedButton(
                onClick = { onPingHost(activeHost) },
                modifier = Modifier.testTag("takeover_ping_button"),
              ) {
                Icon(Icons.Rounded.WifiTethering, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ping")
              }
            }
            OutlinedButton(onClick = onOpenHostSettings) {
              Text("Edit Host")
            }
          }
        }

        if (pingResult != null) {
          Text(
            text = pingResult.detail,
            style = MaterialTheme.typography.labelSmall,
            color = if (pingResult.reachable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
          )
        }

        Button(
          onClick = {
            onSendControl(
              activeHost,
              if (takeoverActive) HostControlAction.TAKEOVER_RELEASE else HostControlAction.TAKEOVER_START,
              "",
            )
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (takeoverActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("takeover_toggle_button"),
        ) {
          Icon(
            imageVector = if (takeoverActive) Icons.Rounded.PlayArrow else Icons.Rounded.PanTool,
            contentDescription = null,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (takeoverActive) "Hand Control Back to OpenClaw" else "Take Over Host Computer Now",
            style = MaterialTheme.typography.titleSmall,
          )
        }

        // Quick Host Control Actions
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { onSendControl(activeHost, HostControlAction.PAUSE_AGENT, "") },
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            Icon(Icons.Rounded.Pause, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Pause", maxLines = 1)
          }
          OutlinedButton(
            onClick = { onSendControl(activeHost, HostControlAction.RESUME_AGENT, "") },
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Resume", maxLines = 1)
          }
          OutlinedButton(
            onClick = { onSendControl(activeHost, HostControlAction.ABORT_TASK, "") },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
              .weight(1f)
              .height(48.dp),
          ) {
            Icon(Icons.Rounded.Stop, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Abort", maxLines = 1)
          }
        }
      }
    }

    // Remote Desktop / Control Web Console
    val controlUrl = activeHost?.controlUrl?.trim().orEmpty()
    if (controlUrl.isNotBlank()) {
      Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text("Live Host Console", style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedButton(
                onClick = { showLiveWebConsole = !showLiveWebConsole },
                modifier = Modifier.testTag("toggle_live_webview_button"),
              ) {
                Text(if (showLiveWebConsole) "Hide View" else "Open View")
              }
              OutlinedButton(
                onClick = {
                  runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(controlUrl)))
                  }
                },
              ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open in browser", Modifier.size(16.dp))
              }
            }
          }

          Text(
            text = "Control URL: $controlUrl",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          AnimatedVisibility(visible = showLiveWebConsole) {
            AndroidView(
              factory = { ctx ->
                WebView(ctx).apply {
                  settings.javaScriptEnabled = true
                  settings.domStorageEnabled = true
                  webViewClient = WebViewClient()
                  loadUrl(controlUrl)
                }
              },
              update = { view ->
                if (view.url != controlUrl) view.loadUrl(controlUrl)
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            )
          }
        }
      }
    }

    // Remote Keystroke / Direct Command Injection
    Card(
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
          Text("Direct Host Input / Action", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(
          value = remoteCommandInput,
          onValueChange = { remoteCommandInput = it },
          label = { Text("Send keystrokes, 2FA code, or host prompt") },
          placeholder = { Text("e.g., Press Enter or type confirmation") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("remote_input_field"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              if (remoteCommandInput.isNotBlank()) {
                onSendControl(activeHost, HostControlAction.SEND_KEYS, remoteCommandInput.trim())
                remoteCommandInput = ""
              }
            },
            enabled = remoteCommandInput.isNotBlank(),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Send Input")
          }
          OutlinedButton(
            onClick = { onSendControl(activeHost, HostControlAction.SCREEN_SNAPSHOT, "") },
          ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Snapshot")
          }
        }
      }
    }

    if (controlLog.isNotEmpty()) {
      Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Host Takeover Log", style = MaterialTheme.typography.titleSmall)
          controlLog.forEach { entry ->
            Text(entry, style = MaterialTheme.typography.labelSmall)
          }
        }
      }
    }
  }
}
