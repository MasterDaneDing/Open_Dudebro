package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BotExpression
import com.example.model.CaptainContextDocument
import com.example.model.ContextChatThread
import com.example.model.ContextDocStatus
import com.example.model.FileLinkType
import com.example.model.LinkedFile
import com.example.model.OpenClawHost
import com.example.model.WheelLlmOption
import com.example.ui.avatar.CaptainContextAvatar
import com.example.ui.components.LlmRotaryWheelSelector
import com.example.ui.components.SectionSettingsIconButton
import java.util.Locale

internal val HeroComicInk = Color(0xFF20150F)
internal val HeroRoyalBlue = Color(0xFF1E5BFF)
internal val HeroDeepNavy = Color(0xFF0E1B54)
internal val HeroGoldAccent = Color(0xFFFFD54F)
internal val HeroCrimson = Color(0xFFE53935)

internal enum class ContextCaseFilter(val label: String) {
  ALL("All Cases"),
  PINNED("Pinned"),
}

@Composable
internal fun CaptainContextSidebar(
  threads: List<ContextChatThread>,
  selectedChatId: String,
  filter: ContextCaseFilter,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  onToggleFilter: () -> Unit,
  onNewCase: () -> Unit,
  onSelectThread: (String) -> Unit,
  onTogglePin: (String, Boolean) -> Unit,
  onDeleteThread: (String) -> Unit,
  onOpenSectionSettings: () -> Unit,
  showSettingsIconOnSidebar: Boolean,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF152778),
            Color(0xFF1E3AA8),
            Color(0xFF0C1547),
          )
        )
      )
      .background(
        Brush.radialGradient(
          colors = listOf(Color(0x44FFD54F), Color.Transparent),
          center = Offset(220f, 160f),
          radius = 480f,
        )
      )
      .testTag("captain_context_sidebar"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          CaptainContextAvatar(
            expression = BotExpression.UH_HUH,
            size = 38.dp,
            animate = true,
            showCapeAndScroll = false,
          )
          Column {
            Text(
              text = "Captain Context",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.ExtraBold,
              color = Color.White,
              maxLines = 1,
            )
            Text(
              text = "Doc-grounded hero Q&A",
              style = MaterialTheme.typography.labelSmall,
              color = HeroGoldAccent,
              maxLines = 1,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Button(
            onClick = onNewCase,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
              containerColor = HeroGoldAccent,
              contentColor = HeroComicInk,
            ),
            border = BorderStroke(2.dp, HeroComicInk),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
              .defaultMinSize(minHeight = 44.dp)
              .testTag("new_context_case_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("New Case", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
          }

          if (showSettingsIconOnSidebar) {
            SectionSettingsIconButton(
              contentDescription = "Captain Context Settings",
              onClick = onOpenSectionSettings,
              tint = HeroGoldAccent,
              modifier = Modifier.testTag("context_section_settings_button"),
            )
          }
        }
      }

      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = {
          Text("Search case files & docs...", style = MaterialTheme.typography.bodySmall, color = Color(0xFFBFD4FF))
        },
        leadingIcon = {
          Icon(Icons.Rounded.Search, contentDescription = null, tint = HeroGoldAccent, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchChange("") }) {
              Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = HeroGoldAccent)
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = Color.White,
          unfocusedTextColor = Color.White,
          focusedContainerColor = Color(0x550C1547),
          unfocusedContainerColor = Color(0x440C1547),
          focusedBorderColor = HeroGoldAccent,
          unfocusedBorderColor = Color(0x7779A6FF),
        ),
        modifier = Modifier.fillMaxWidth(),
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "${filter.label} • ${threads.size}",
          style = MaterialTheme.typography.labelLarge,
          color = HeroGoldAccent,
          fontWeight = FontWeight.Bold,
        )
        IconButton(
          onClick = onToggleFilter,
          modifier = Modifier
            .size(48.dp)
            .testTag("context_filter_button"),
        ) {
          Icon(
            Icons.Rounded.FilterList,
            contentDescription = "Toggle filter between All Cases and Pinned",
            tint = if (filter == ContextCaseFilter.PINNED) HeroGoldAccent else Color(0xFFBFD4FF),
          )
        }
      }

      if (threads.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
          ) {
            CaptainContextAvatar(
              expression = BotExpression.HAPPY,
              size = 86.dp,
              animate = true,
              showCapeAndScroll = true,
            )
            Text(
              text = if (filter == ContextCaseFilter.PINNED) "No pinned cases yet" else "Load PDFs or Drive Docs!",
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
              fontWeight = FontWeight.ExtraBold,
              textAlign = TextAlign.Center,
            )
            Text(
              text = "Tap New Case to drop PDFs, Google Drive links, or host files — every answer stays strictly grounded in your sources.",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFFD6E4FF),
              textAlign = TextAlign.Center,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          contentPadding = PaddingValues(bottom = 8.dp),
        ) {
          items(threads, key = { it.id }) { thread ->
            val selected = thread.id == selectedChatId
            Surface(
              shape = RoundedCornerShape(18.dp),
              color = if (selected) HeroRoyalBlue else Color(0x550C1547),
              border = BorderStroke(
                width = if (selected) 2.5.dp else 1.4.dp,
                color = if (selected) HeroGoldAccent else Color(0x6679A6FF),
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onSelectThread(thread.id) }
                .testTag("context_case_row_${thread.id}"),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                  ) {
                    Text(
                      text = thread.title,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                      color = Color.White,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f, fill = false),
                    )
                    if (thread.docRefs.isNotEmpty()) {
                      Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = HeroGoldAccent,
                        border = BorderStroke(1.dp, HeroComicInk),
                      ) {
                        Text(
                          text = "${thread.docRefs.size} doc${if (thread.docRefs.size == 1) "" else "s"}",
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.ExtraBold,
                          color = HeroComicInk,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                      }
                    }
                  }
                  if (thread.lastMessagePreview.isNotBlank()) {
                    Text(
                      text = thread.lastMessagePreview,
                      style = MaterialTheme.typography.labelSmall,
                      color = if (selected) Color(0xFFFFF8E1) else Color(0xFFC4D7FF),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }

                IconButton(
                  onClick = { onTogglePin(thread.id, !thread.pinned) },
                  modifier = Modifier
                    .size(44.dp)
                    .testTag("pin_context_case_${thread.id}"),
                ) {
                  Icon(
                    imageVector = if (thread.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (thread.pinned) "Unpin case" else "Pin case",
                    tint = if (thread.pinned) HeroGoldAccent else Color(0xFFB3C7FF),
                    modifier = Modifier.size(18.dp),
                  )
                }

                IconButton(
                  onClick = { onDeleteThread(thread.id) },
                  modifier = Modifier
                    .size(44.dp)
                    .testTag("delete_context_case_${thread.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete case",
                    tint = Color(0xFFB3C7FF),
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun ContextVaultDeck(
  docs: List<CaptainContextDocument>,
  expanded: Boolean,
  onToggleExpand: () -> Unit,
  onPickDeviceDocs: () -> Unit,
  onOpenDriveLinkDialog: () -> Unit,
  onOpenVaultPickerDialog: () -> Unit,
  onInspectDoc: (CaptainContextDocument) -> Unit,
  onRefreshDoc: (CaptainContextDocument) -> Unit,
  onRemoveDoc: (CaptainContextDocument) -> Unit,
  modifier: Modifier = Modifier,
) {
  val readyCount = docs.count { it.status == ContextDocStatus.READY }
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = Color(0xEE10205E),
    border = BorderStroke(2.dp, HeroComicInk),
    modifier = modifier
      .fillMaxWidth()
      .testTag("context_vault_deck"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .clickable(onClick = onToggleExpand)
          .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = HeroGoldAccent,
            border = BorderStroke(1.5.dp, HeroComicInk),
          ) {
            Text(
              text = "DOC VAULT",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.ExtraBold,
              color = HeroComicInk,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            )
          }
          Text(
            text = when {
              docs.isEmpty() -> "No sources loaded yet"
              readyCount == docs.size -> "$readyCount source${if (readyCount == 1) "" else "s"} ready"
              else -> "$readyCount/${docs.size} sources ready"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Icon(
          imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
          contentDescription = if (expanded) "Collapse document vault" else "Expand document vault",
          tint = HeroGoldAccent,
        )
      }

      AnimatedVisibility(visible = expanded || docs.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Button(
              onClick = onPickDeviceDocs,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = HeroGoldAccent,
                contentColor = HeroComicInk,
              ),
              border = BorderStroke(1.8.dp, HeroComicInk),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("context_pick_docs_button"),
            ) {
              Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(5.dp))
              Text("Pick PDF / Docs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }

            Button(
              onClick = onOpenDriveLinkDialog,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = HeroRoyalBlue,
                contentColor = Color.White,
              ),
              border = BorderStroke(1.8.dp, HeroComicInk),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("context_drive_link_button"),
            ) {
              Icon(Icons.Rounded.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(5.dp))
              Text("Drive Link", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }

            OutlinedButton(
              onClick = onOpenVaultPickerDialog,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.6.dp, Color(0xFF9EC1FF)),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
              modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("context_host_vault_button"),
            ) {
              Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(5.dp))
              Text("Vault / Host", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }
          }

          if (docs.isNotEmpty()) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              docs.forEach { doc ->
                ContextDocCard(
                  doc = doc,
                  onInspect = { onInspectDoc(doc) },
                  onRefresh = { onRefreshDoc(doc) },
                  onRemove = { onRemoveDoc(doc) },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ContextDocCard(
  doc: CaptainContextDocument,
  onInspect: () -> Unit,
  onRefresh: () -> Unit,
  onRemove: () -> Unit,
) {
  val typeBadge = remember(doc) {
    val u = doc.uriOrPath.lowercase(Locale.US)
    when {
      u.contains("docs.google.com/spreadsheets") -> "SHEET"
      u.contains("docs.google.com/presentation") -> "SLIDE"
      u.contains("docs.google.com/document") -> "GDOC"
      doc.isGoogleDriveUrl -> "DRIVE"
      doc.isPdf -> "PDF"
      doc.isDocx -> "DOCX"
      doc.isImage -> "IMG"
      doc.linkType == FileLinkType.HOST_PATH -> "HOST"
      else -> "DOC"
    }
  }
  val statusBg = when (doc.status) {
    ContextDocStatus.READY -> Color(0xFF00C853)
    ContextDocStatus.LOADING -> Color(0xFFFFB300)
    ContextDocStatus.NEEDS_ACCESS -> Color(0xFFFF6D00)
    ContextDocStatus.ERROR -> HeroCrimson
  }
  val statusLabel = when (doc.status) {
    ContextDocStatus.READY -> {
      val charsLabel = if (doc.charCount >= 1000) "${doc.charCount / 1000}k chars" else "${doc.charCount} chars"
      "Loaded • $charsLabel"
    }
    ContextDocStatus.LOADING -> "Reading..."
    ContextDocStatus.NEEDS_ACCESS -> "Needs Access"
    ContextDocStatus.ERROR -> "Error"
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color(0xFF1B338C),
    border = BorderStroke(2.dp, HeroComicInk),
    modifier = Modifier
      .widthIn(min = 190.dp, max = 250.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onInspect)
      .testTag("context_doc_card_${doc.id}"),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(7.dp),
          color = HeroGoldAccent,
          border = BorderStroke(1.2.dp, HeroComicInk),
        ) {
          Text(
            text = typeBadge,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = HeroComicInk,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
        Text(
          text = doc.displayName,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Surface(
          shape = RoundedCornerShape(50),
          color = statusBg,
          border = BorderStroke(1.dp, HeroComicInk),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          ) {
            if (doc.status == ContextDocStatus.LOADING) {
              CircularProgressIndicator(
                color = HeroComicInk,
                strokeWidth = 2.dp,
                modifier = Modifier.size(10.dp),
              )
            }
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.ExtraBold,
              color = if (doc.status == ContextDocStatus.LOADING) HeroComicInk else Color.White,
              maxLines = 1,
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onRefresh,
            modifier = Modifier
              .size(32.dp)
              .testTag("refresh_context_doc_${doc.id}"),
          ) {
            Icon(
              Icons.Rounded.Refresh,
              contentDescription = "Reload document",
              tint = HeroGoldAccent,
              modifier = Modifier.size(16.dp),
            )
          }
          IconButton(
            onClick = onRemove,
            modifier = Modifier
              .size(32.dp)
              .testTag("remove_context_doc_${doc.id}"),
          ) {
            Icon(
              Icons.Rounded.Close,
              contentDescription = "Remove document",
              tint = Color(0xFFFFCDD2),
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun CaptainContextDriveUrlDialog(
  onDismiss: () -> Unit,
  onPickViaAndroidDrive: () -> Unit,
  onSubmitUrl: (url: String, customTitle: String) -> Unit,
) {
  var urlText by remember { mutableStateOf("") }
  var titleText by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Google Drive or Cloud Document", fontWeight = FontWeight.ExtraBold) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = "Paste a Google Drive, Google Docs, Sheets, Slides, or direct file link. For private Drive files, use Android's Drive Picker below (no sharing toggle required!).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = urlText,
          onValueChange = { urlText = it },
          label = { Text("Google Drive / Docs / File URL") },
          placeholder = { Text("https://docs.google.com/document/d/...") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("context_drive_url_input"),
        )
        OutlinedTextField(
          value = titleText,
          onValueChange = { titleText = it },
          label = { Text("Optional Label") },
          placeholder = { Text("Q4 Contract / Spec Sheet") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("context_drive_title_input"),
        )
        OutlinedButton(
          onClick = {
            onDismiss()
            onPickViaAndroidDrive()
          },
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("context_open_saf_drive_picker"),
        ) {
          Icon(Icons.Rounded.FolderOpen, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Browse Google Drive via Android Picker")
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSubmitUrl(urlText, titleText)
          onDismiss()
        },
        enabled = urlText.trim().startsWith("http", ignoreCase = true),
        modifier = Modifier.testTag("context_confirm_drive_url_button"),
      ) {
        Text("Load Document")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
internal fun CaptainContextHostVaultDialog(
  linkedFiles: List<LinkedFile>,
  loadedRefs: Set<String>,
  onDismiss: () -> Unit,
  onSelectFile: (LinkedFile) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Load from Vault / Host PC", fontWeight = FontWeight.ExtraBold) },
    text = {
      if (linkedFiles.isEmpty()) {
        Text(
          text = "Your Files Vault is empty. Add files or host PC paths in the Files tab, or use Pick PDF / Docs.",
          style = MaterialTheme.typography.bodySmall,
        )
      } else {
        Column(
          modifier = Modifier
            .heightIn(max = 340.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          linkedFiles.forEach { file ->
            val alreadyLoaded = file.uriOrPath in loadedRefs
            Surface(
              onClick = {
                onSelectFile(file)
                onDismiss()
              },
              shape = RoundedCornerShape(12.dp),
              color = if (alreadyLoaded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("context_vault_pick_${file.id}"),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(file.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    "${file.linkTypeEnum.label} • ${file.uriOrPath}",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                if (alreadyLoaded) {
                  Text("Loaded", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}

@Composable
internal fun CaptainContextDocPreviewDialog(
  doc: CaptainContextDocument,
  onDismiss: () -> Unit,
  onRefresh: () -> Unit,
  onPickViaDrive: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(doc.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 380.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Status: ${doc.status.label}${if (doc.statusDetail.isNotBlank()) " — ${doc.statusDetail}" else ""}",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = if (doc.status == ContextDocStatus.READY) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(
          text = doc.uriOrPath,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (doc.status == ContextDocStatus.NEEDS_ACCESS) {
          OutlinedButton(
            onClick = {
              onDismiss()
              onPickViaDrive()
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Pick Private Drive File via Android Picker")
          }
        }
        HorizontalDivider()
        Text(
          text = doc.extractedText.ifBlank { "No extracted text preview available for this document yet." }.take(6000),
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onRefresh) { Text("Refresh") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Done") }
    },
  )
}

@Composable
internal fun CaptainContextSettingsDialog(
  activeThread: ContextChatThread?,
  activeHost: OpenClawHost?,
  selectedWheelModel: WheelLlmOption,
  onSelectWheelModel: (WheelLlmOption) -> Unit,
  onTogglePin: () -> Unit,
  onDeleteCase: () -> Unit,
  onOpenHostSettings: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Captain Context Settings", fontWeight = FontWeight.ExtraBold) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Spin Model Wheel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LlmRotaryWheelSelector(
          selectedOption = selectedWheelModel,
          onSelectOption = onSelectWheelModel,
          darkSurface = false,
          accentColor = HeroRoyalBlue,
        )
        if (activeThread != null) {
          HorizontalDivider()
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = onTogglePin,
              modifier = Modifier.weight(1f),
            ) {
              Text(if (activeThread.pinned) "Unpin Case" else "Pin Case")
            }
            OutlinedButton(
              onClick = {
                onDeleteCase()
                onDismiss()
              },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.weight(1f),
            ) {
              Text("Delete Case")
            }
          }
        }
        HorizontalDivider()
        Text("OpenClaw Document Grounding", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
          text = "Uses OpenClaw (openclaw/apk) with per-case sessions and injects loaded Vault/Drive sources into every turn.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = activeHost?.let { "Gateway (${it.connectionModeEnum.shortPill}): ${it.label} (${it.effectiveBaseUrl})" }
            ?: "No OpenClaw Host PC configured yet",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
          onClick = {
            onDismiss()
            onOpenHostSettings()
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Open OpenClaw & Host Settings")
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Done") }
    },
  )
}
