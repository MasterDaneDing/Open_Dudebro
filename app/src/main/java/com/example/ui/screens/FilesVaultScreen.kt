package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.files.MediaSaveHelper
import com.example.model.FileLinkType
import com.example.model.GeneratedMediaItem
import com.example.model.GeneratedMediaType
import com.example.model.LinkedFile
import com.example.model.OpenClawHost
import com.example.ui.components.MediaLightboxDialog
import com.example.ui.components.SectionSettingsIconButton
import com.example.viewmodel.UiState
import kotlinx.coroutines.launch

private enum class FilesTabSection(val label: String) {
  HOST_FILES("Host Files"),
  GENERATED_MEDIA("Generated Media"),
}

private enum class MediaGalleryFilter(val label: String) {
  ALL("All"),
  IMAGES("Images"),
  VIDEOS("Videos"),
  AUDIO("Audio"),
  DOCUMENTS("Docs"),
}

@Composable
fun FilesVaultScreen(
  linkedFilesState: UiState<List<LinkedFile>>,
  generatedMediaState: UiState<List<GeneratedMediaItem>> = UiState.Success(emptyList()),
  activeHost: OpenClawHost? = null,
  onAddFile: (displayName: String, uriOrPath: String, linkType: FileLinkType, mime: String, sizeBytes: Long, notes: String, attachToComposer: Boolean) -> Unit,
  onQueueForChat: (LinkedFile) -> Unit,
  onSendMediaToChat: (GeneratedMediaItem) -> Unit = {},
  onDeleteFile: (String) -> Unit,
  onDeleteMedia: (String) -> Unit = {},
  onShowBanner: (String) -> Unit = {},
  onOpenHostSettings: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var selectedSection by remember { mutableStateOf(FilesTabSection.HOST_FILES) }
  var mediaFilter by remember { mutableStateOf(MediaGalleryFilter.ALL) }
  var lightboxMedia by remember { mutableStateOf<GeneratedMediaItem?>(null) }
  var showHostLinkDialog by remember { mutableStateOf(false) }
  var showSectionSettings by remember { mutableStateOf(false) }

  val docPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      val (name, mime, size) = queryDocMeta(context, uri)
      onAddFile(name, uri.toString(), FileLinkType.ANDROID_ATTACHMENT, mime, size, "Attached from Android", false)
    }
  }

  val photoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      val (name, mime, size) = queryDocMeta(context, uri)
      onAddFile(name, uri.toString(), FileLinkType.ANDROID_ATTACHMENT, mime.ifBlank { "image/*" }, size, "Photo attachment", false)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_min_files),
          contentDescription = null,
          tint = Color(0xFFFFB2EB),
          modifier = Modifier.size(24.dp),
        )
        Text(
          text = "Files & Media",
          style = MaterialTheme.typography.titleLarge.copy(
            shadow = Shadow(color = Color(0x881A0033), offset = Offset(0f, 2f), blurRadius = 4f)
          ),
          color = Color.White,
          fontWeight = FontWeight.ExtraBold,
        )
      }
      SectionSettingsIconButton(
        contentDescription = "Files & OpenClaw Settings",
        onClick = { showSectionSettings = true },
        tint = Color(0xFFFFB2EB),
        modifier = Modifier.testTag("files_section_settings_button"),
      )
    }

    // Top Segmented Toggle: Host Files vs Generated Media
    Surface(
      shape = RoundedCornerShape(18.dp),
      color = Color(0x5522004E),
      border = BorderStroke(1.2.dp, Color(0x88FF9CE6)),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilesTabSection.entries.forEach { tab ->
          val selected = selectedSection == tab
          Surface(
            onClick = { selectedSection = tab },
            shape = RoundedCornerShape(14.dp),
            color = if (selected) Color(0xFFFF2E93) else Color.Transparent,
            modifier = Modifier
              .weight(1f)
              .testTag(
                if (tab == FilesTabSection.HOST_FILES) "files_tab_host_files"
                else "files_tab_generated_media"
              ),
          ) {
            Text(
              text = tab.label,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
              color = if (selected) Color.White else Color(0xFFFFD1F3),
              textAlign = TextAlign.Center,
              maxLines = 1,
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            )
          }
        }
      }
    }

    if (selectedSection == FilesTabSection.HOST_FILES) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = { docPicker.launch(arrayOf("*/*")) },
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.8.dp, Color(0xFF28004A)),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFAFFF9F2),
            contentColor = Color(0xFF42009E),
          ),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("vault_attach_doc_button"),
        ) {
          Icon(Icons.Rounded.FolderOpen, contentDescription = null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("File", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Button(
          onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.8.dp, Color(0xFF28004A)),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFAFFF9F2),
            contentColor = Color(0xFF42009E),
          ),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("vault_attach_photo_button"),
        ) {
          Icon(Icons.Rounded.Image, contentDescription = null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Photo", fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Button(
          onClick = { showHostLinkDialog = true },
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.8.dp, Color(0xFF28004A)),
          colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFF6D00),
            contentColor = Color.White,
          ),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("vault_add_link_button"),
        ) {
          Icon(Icons.Rounded.Link, contentDescription = null, Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Link", fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
      }

      when (linkedFilesState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
        is UiState.Error -> Surface(
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = linkedFilesState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(14.dp),
          )
        }
        is UiState.Success -> {
          val files = linkedFilesState.data
          if (files.isEmpty()) {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier
                  .padding(24.dp)
                  .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Rounded.AttachFile,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(36.dp),
                )
                Text("No files or links yet", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "Tap File, Photo, or Link above to save PDFs, code files, or host paths to hand directly to OpenClaw.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            LazyColumn(
              contentPadding = PaddingValues(vertical = 4.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(files, key = { it.id }) { file ->
                Card(
                  shape = MaterialTheme.shapes.medium,
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                  elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                  ) {
                    Icon(
                      imageVector = when (file.linkTypeEnum) {
                        FileLinkType.ANDROID_ATTACHMENT -> Icons.Rounded.AttachFile
                        else -> Icons.Rounded.Link
                      },
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = file.uriOrPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        text = "${file.linkTypeEnum.label}${if (file.sizeBytes > 0) " • ${file.sizeBytes / 1024} KB" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                      )
                    }
                    IconButton(
                      onClick = { onQueueForChat(file) },
                      modifier = Modifier.size(48.dp),
                    ) {
                      Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Attach to active bot chat",
                        tint = MaterialTheme.colorScheme.primary,
                      )
                    }
                    IconButton(
                      onClick = { onDeleteFile(file.id) },
                      modifier = Modifier.size(48.dp),
                    ) {
                      Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete link",
                        tint = MaterialTheme.colorScheme.error,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    } else {
      // Generated Media Gallery
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        MediaGalleryFilter.entries.forEach { f ->
          val active = mediaFilter == f
          Surface(
            onClick = { mediaFilter = f },
            shape = RoundedCornerShape(50),
            color = if (active) Color(0xFFFF6D00) else Color(0x5522004E),
            border = BorderStroke(1.2.dp, if (active) Color(0xFFFFD180) else Color(0x66FF9CE6)),
            modifier = Modifier.testTag("media_filter_${f.name}"),
          ) {
            Text(
              text = f.label,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            )
          }
        }
      }

      when (generatedMediaState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
        is UiState.Error -> Surface(
          color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = generatedMediaState.message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(14.dp),
          )
        }
        is UiState.Success -> {
          val filtered = remember(generatedMediaState.data, mediaFilter) {
            generatedMediaState.data.filter { item ->
              when (mediaFilter) {
                MediaGalleryFilter.ALL -> true
                MediaGalleryFilter.IMAGES -> item.mediaTypeEnum == GeneratedMediaType.IMAGE
                MediaGalleryFilter.VIDEOS -> item.mediaTypeEnum == GeneratedMediaType.VIDEO
                MediaGalleryFilter.AUDIO -> item.mediaTypeEnum == GeneratedMediaType.AUDIO
                MediaGalleryFilter.DOCUMENTS -> item.mediaTypeEnum == GeneratedMediaType.DOCUMENT
              }
            }
          }

          if (filtered.isEmpty()) {
            Card(
              shape = MaterialTheme.shapes.large,
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier
                  .padding(24.dp)
                  .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  tint = Color(0xFFFF2E93),
                  modifier = Modifier.size(38.dp),
                )
                Text("No generated media yet", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = "Ask a Bot Dude or General Chat to generate an image or animate a clip—your OpenClaw / Venice.ai creations will automatically appear here!",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            LazyVerticalGrid(
              columns = GridCells.Fixed(2),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(bottom = 12.dp),
              modifier = Modifier
                .fillMaxSize()
                .testTag("generated_media_grid"),
            ) {
              items(filtered, key = { it.id }) { item ->
                GeneratedMediaGridCard(
                  item = item,
                  activeHost = activeHost,
                  onClick = { lightboxMedia = item },
                  onDelete = { onDeleteMedia(item.id) },
                )
              }
            }
          }
        }
      }
    }
  }

  lightboxMedia?.let { item ->
    MediaLightboxDialog(
      mediaUrl = item.mediaUrl,
      mediaType = item.mediaTypeEnum,
      prompt = item.prompt,
      modelUsed = item.modelUsed,
      durationSec = item.durationSec,
      activeHost = activeHost,
      onDismiss = { lightboxMedia = null },
      onSaveToPhone = {
        scope.launch {
          val res = if (item.mediaTypeEnum == GeneratedMediaType.DOCUMENT) {
            MediaSaveHelper.saveAndOpenDocument(
              context = context,
              mediaUrl = item.mediaUrl,
              host = activeHost,
              suggestedPrefix = item.sourceBotName.ifBlank { "ClawDudebro_Doc" },
            )
          } else {
            MediaSaveHelper.saveMediaToPhone(
              context = context,
              mediaUrl = item.mediaUrl,
              mediaType = item.mediaTypeEnum,
              host = activeHost,
              suggestedPrefix = item.sourceBotName.ifBlank { "ClawDudebro" },
            )
          }
          onShowBanner(
            res.getOrElse { err -> "Save failed: ${err.localizedMessage ?: "Error"}" }
          )
        }
      },
      onSendToChat = {
        lightboxMedia = null
        onSendMediaToChat(item)
      },
    )
  }

  if (showHostLinkDialog) {
    var name by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isUrl by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showHostLinkDialog = false },
      title = { Text("Add Host File Link") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(120) },
            label = { Text("Display Name") },
            placeholder = { Text("e.g., Project Workspace") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = path,
            onValueChange = {
              path = it.take(500)
              isUrl = it.trim().startsWith("http://") || it.trim().startsWith("https://")
            },
            label = { Text("Host Path or URL") },
            placeholder = { Text("C:\\Users\\Me\\Documents or https://...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = notes,
            onValueChange = { notes = it.take(250) },
            label = { Text("Notes (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (path.isNotBlank()) {
              val cleanName = name.ifBlank {
                path.substringAfterLast('/').substringAfterLast('\\').ifBlank { "Host Path" }
              }
              onAddFile(
                cleanName,
                path.trim(),
                if (isUrl) FileLinkType.CLOUD_URL else FileLinkType.HOST_PATH,
                "",
                0L,
                notes.trim(),
                false,
              )
              showHostLinkDialog = false
            }
          },
        ) {
          Text("Save Link")
        }
      },
      dismissButton = {
        TextButton(onClick = { showHostLinkDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showSectionSettings) {
    AlertDialog(
      onDismissRequest = { showSectionSettings = false },
      title = { Text("Files & OpenClaw Settings") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Attach photos, PDFs, or source files without broad storage permissions, browse generated images & clips, or save Host PC paths.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedButton(
            onClick = {
              showSectionSettings = false
              showHostLinkDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Add New Host Path / URL")
          }
          HorizontalDivider()
          Button(
            onClick = {
              showSectionSettings = false
              onOpenHostSettings()
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Open OpenClaw & Host Settings")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSectionSettings = false }) { Text("Done") }
      },
    )
  }
}

@Composable
private fun GeneratedMediaGridCard(
  item: GeneratedMediaItem,
  activeHost: OpenClawHost?,
  onClick: () -> Unit,
  onDelete: () -> Unit,
) {
  val context = LocalContext.current
  val mediaType = item.mediaTypeEnum
  val isVideo = mediaType == GeneratedMediaType.VIDEO

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, Color(0x44FF9CE6)),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .testTag("gallery_media_card_${item.id}"),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
          .background(Color(0xFF1A072E)),
      ) {
        when (mediaType) {
          GeneratedMediaType.VIDEO -> {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = "Play video clip",
                tint = Color(0xFFFF8CE0),
                modifier = Modifier.size(54.dp),
              )
            }
          }
          GeneratedMediaType.AUDIO -> {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = "Open audio clip",
                tint = Color(0xFFFF8CE0),
                modifier = Modifier.size(52.dp),
              )
            }
          }
          GeneratedMediaType.DOCUMENT -> {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Rounded.Description,
                contentDescription = "Open document",
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(52.dp),
              )
            }
          }
          GeneratedMediaType.IMAGE -> {
            AsyncImage(
              model = MediaSaveHelper.buildAuthorizedImageRequest(context, item.mediaUrl, activeHost),
              imageLoader = remember(context) { MediaSaveHelper.getImageLoader(context) },
              contentDescription = item.prompt,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          }
        }

        // Top-left type + duration badge
        Surface(
          shape = RoundedCornerShape(50),
          color = Color(0xCC1E0634),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = when (mediaType) {
                GeneratedMediaType.VIDEO -> Icons.Rounded.Movie
                GeneratedMediaType.AUDIO -> Icons.Rounded.GraphicEq
                GeneratedMediaType.DOCUMENT -> Icons.Rounded.Description
                GeneratedMediaType.IMAGE -> Icons.Rounded.Image
              },
              contentDescription = null,
              tint = Color(0xFFFF8CE0),
              modifier = Modifier.size(12.dp),
            )
            Text(
              text = if (isVideo && item.durationSec > 0) "Video • ${item.durationSec}s" else mediaType.label,
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(4.dp)
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0x991E0634)),
        ) {
          Icon(
            Icons.Rounded.DeleteOutline,
            contentDescription = "Remove from gallery",
            tint = Color.White,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = item.prompt.ifBlank { "Generated ${item.mediaTypeEnum.label}" },
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = listOf(item.modelUsed, item.sourceBotName).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "OpenClaw" },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun queryDocMeta(context: Context, uri: Uri): Triple<String, String, Long> {
  var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
  var size = 0L
  val mime = context.contentResolver.getType(uri).orEmpty()
  try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (nIdx >= 0) name = cursor.getString(nIdx) ?: name
        if (sIdx >= 0) size = cursor.getLong(sIdx)
      }
    }
  } catch (_: Exception) {
    // ignore
  }
  return Triple(name, mime, size.coerceAtLeast(0L))
}
