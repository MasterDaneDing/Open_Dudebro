package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import com.example.R
import com.example.data.files.MediaSaveHelper
import com.example.model.BotExpression
import com.example.model.CompanionBot
import com.example.model.FileLinkType
import com.example.model.GeneralChatMessage
import com.example.model.GeneralChatThread
import com.example.model.GeneratedMediaType
import com.example.model.LinkedFile
import com.example.model.MessageSender
import com.example.model.MessageStatus
import com.example.model.OpenClawHost
import com.example.model.WheelLlmOption
import com.example.ui.avatar.BotVectorAvatar
import com.example.ui.components.InlineMediaCard
import com.example.ui.components.LlmRotaryWheelSelector
import com.example.ui.components.MediaLightboxDialog
import com.example.ui.components.SectionSettingsIconButton
import com.example.viewmodel.ConnectionFailureBanner
import com.example.viewmodel.UiState
import java.util.Locale
import kotlinx.coroutines.launch

private enum class GeneralFilter(val label: String) {
  ALL("All"),
  PINNED("Pinned"),
}

@Composable
fun GeneralChatScreen(
  generalChatsState: UiState<List<GeneralChatThread>>,
  selectedChatId: String,
  messagesState: UiState<List<GeneralChatMessage>>,
  hostsState: UiState<List<OpenClawHost>>,
  linkedFilesState: UiState<List<LinkedFile>> = UiState.Success(emptyList()),
  pendingAttachments: List<LinkedFile> = emptyList(),
  selectedWheelModel: WheelLlmOption,
  isBusy: Boolean,
  activeElapsedSec: Int = 0,
  streamingPreviewText: String? = null,
  connectionFailureBanner: ConnectionFailureBanner? = null,
  onSelectChat: (String) -> Unit,
  onCreateNewChat: (WheelLlmOption) -> Unit,
  onTogglePinChat: (String, Boolean) -> Unit,
  onDeleteChat: (String) -> Unit,
  onSelectWheelModel: (WheelLlmOption, String?) -> Unit,
  onToggleHostConnectionMode: (OpenClawHost) -> Unit = {},
  onSwitchModeAndRetry: (ConnectionFailureBanner) -> Unit = {},
  onDismissFailureBanner: () -> Unit = {},
  onAbortRequest: () -> Unit = {},
  onSendMessage: (GeneralChatThread?, String, WheelLlmOption, OpenClawHost?) -> Unit,
  onAttachDeviceFile: (String, String, String, Long) -> Unit = { _, _, _, _ -> },
  onToggleAttachment: (LinkedFile) -> Unit = {},
  onShowBanner: (String) -> Unit = {},
  onOpenHostSettings: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current
  val threads = (generalChatsState as? UiState.Success)?.data.orEmpty()
  val messages = (messagesState as? UiState.Success)?.data.orEmpty()
  val linkedFiles = (linkedFilesState as? UiState.Success)?.data.orEmpty()
  val activeHost = (hostsState as? UiState.Success)?.data?.firstOrNull()
  val activeThread = threads.firstOrNull { it.id == selectedChatId }

  var phoneShowingThread by remember { mutableStateOf(false) }
  var filter by remember { mutableStateOf(GeneralFilter.ALL) }
  var searchQuery by remember { mutableStateOf("") }
  var composerText by remember { mutableStateOf("") }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showAttachSheet by remember { mutableStateOf(false) }
  var lightboxMessage by remember { mutableStateOf<GeneralChatMessage?>(null) }

  val documentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    uris.forEach { uri ->
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val meta = resolveGeneralUriMeta(context, uri)
      onAttachDeviceFile(meta.name, uri.toString(), meta.mime, meta.size)
    }
  }

  val photoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      val meta = resolveGeneralUriMeta(context, uri)
      onAttachDeviceFile(meta.name, uri.toString(), meta.mime.ifBlank { "image/*" }, meta.size)
    }
  }

  LaunchedEffect(selectedChatId) {
    if (selectedChatId.isNotBlank()) {
      phoneShowingThread = true
    }
  }

  val sortedFilteredThreads = remember(threads, filter, searchQuery) {
    val q = searchQuery.trim().lowercase(Locale.US)
    threads
      .filter { thread ->
        (filter == GeneralFilter.ALL || thread.pinned) &&
          (q.isEmpty() ||
            thread.title.lowercase(Locale.US).contains(q) ||
            thread.lastMessagePreview.lowercase(Locale.US).contains(q))
      }
      .sortedWith(
        compareByDescending<GeneralChatThread> { it.pinned }
          .thenByDescending { it.updatedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L }
      )
  }

  Box(modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val isTwoPaneTablet = maxWidth >= 600.dp

      if (!isTwoPaneTablet && phoneShowingThread) {
        BackHandler {
          phoneShowingThread = false
        }
      }

      if (isTwoPaneTablet) {
        Row(modifier = Modifier.fillMaxSize()) {
          GeneralChatsSidebar(
            threads = sortedFilteredThreads,
            selectedChatId = activeThread?.id.orEmpty(),
            filter = filter,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onToggleFilter = {
              filter = if (filter == GeneralFilter.ALL) GeneralFilter.PINNED else GeneralFilter.ALL
            },
            onNewChat = { onCreateNewChat(selectedWheelModel) },
            onSelectThread = { id -> onSelectChat(id) },
            onTogglePin = onTogglePinChat,
            onDeleteThread = onDeleteChat,
            onOpenSectionSettings = { showSettingsDialog = true },
            showSettingsIconOnSidebar = false,
            modifier = Modifier
              .width(292.dp)
              .fillMaxHeight(),
          )

          GeneralChatConversationPane(
            activeThread = activeThread,
            messagesState = messagesState,
            messages = messages,
            activeHost = activeHost,
            pendingAttachments = pendingAttachments,
            selectedWheelModel = selectedWheelModel,
            composerText = composerText,
            isBusy = isBusy,
            activeElapsedSec = activeElapsedSec,
            streamingPreviewText = streamingPreviewText,
            connectionFailureBanner = connectionFailureBanner,
            showBackArrow = false,
            onBackToList = {},
            onTogglePin = {
              if (activeThread != null) onTogglePinChat(activeThread.id, !activeThread.pinned)
            },
            onToggleHostConnectionMode = onToggleHostConnectionMode,
            onSwitchModeAndRetry = onSwitchModeAndRetry,
            onDismissFailureBanner = onDismissFailureBanner,
            onAbortRequest = onAbortRequest,
            onOpenHostSettings = onOpenHostSettings,
            onOpenSettings = { showSettingsDialog = true },
            onOpenAttach = { showAttachSheet = true },
            onToggleAttachment = onToggleAttachment,
            onComposerChange = { composerText = it },
            onSend = {
              val prompt = composerText
              composerText = ""
              onSendMessage(activeThread, prompt, selectedWheelModel, activeHost)
            },
            onAnimateImage = { url ->
              composerText = "Animate this image with lively cinematic motion: $url"
            },
            onSaveMedia = { url, type ->
              scope.launch {
                val res = if (type == GeneratedMediaType.DOCUMENT) {
                  MediaSaveHelper.saveAndOpenDocument(context, url, activeHost)
                } else {
                  MediaSaveHelper.saveMediaToPhone(context, url, type, activeHost)
                }
                res
                  .onSuccess { onShowBanner(it) }
                  .onFailure { onShowBanner(it.localizedMessage ?: "Failed to save media") }
              }
            },
            onCopyPrompt = { promptText ->
              clipboard.setText(AnnotatedString(promptText))
              onShowBanner("Prompt copied to clipboard!")
            },
            onOpenLightbox = { msg -> lightboxMessage = msg },
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
          )
        }
      } else if (!phoneShowingThread) {
        GeneralChatsSidebar(
          threads = sortedFilteredThreads,
          selectedChatId = activeThread?.id.orEmpty(),
          filter = filter,
          searchQuery = searchQuery,
          onSearchChange = { searchQuery = it },
          onToggleFilter = {
            filter = if (filter == GeneralFilter.ALL) GeneralFilter.PINNED else GeneralFilter.ALL
          },
          onNewChat = {
            phoneShowingThread = true
            onCreateNewChat(selectedWheelModel)
          },
          onSelectThread = { id ->
            onSelectChat(id)
            phoneShowingThread = true
          },
          onTogglePin = onTogglePinChat,
          onDeleteThread = onDeleteChat,
          onOpenSectionSettings = { showSettingsDialog = true },
          showSettingsIconOnSidebar = true,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        GeneralChatConversationPane(
          activeThread = activeThread,
          messagesState = messagesState,
          messages = messages,
          activeHost = activeHost,
          pendingAttachments = pendingAttachments,
          selectedWheelModel = selectedWheelModel,
          composerText = composerText,
          isBusy = isBusy,
          activeElapsedSec = activeElapsedSec,
          streamingPreviewText = streamingPreviewText,
          connectionFailureBanner = connectionFailureBanner,
          showBackArrow = true,
          onBackToList = { phoneShowingThread = false },
          onTogglePin = {
            if (activeThread != null) onTogglePinChat(activeThread.id, !activeThread.pinned)
          },
          onToggleHostConnectionMode = onToggleHostConnectionMode,
          onSwitchModeAndRetry = onSwitchModeAndRetry,
          onDismissFailureBanner = onDismissFailureBanner,
          onAbortRequest = onAbortRequest,
          onOpenHostSettings = onOpenHostSettings,
          onOpenSettings = { showSettingsDialog = true },
          onOpenAttach = { showAttachSheet = true },
          onToggleAttachment = onToggleAttachment,
          onComposerChange = { composerText = it },
          onSend = {
            val prompt = composerText
            composerText = ""
            onSendMessage(activeThread, prompt, selectedWheelModel, activeHost)
          },
          onAnimateImage = { url ->
            composerText = "Animate this image with lively cinematic motion: $url"
          },
          onSaveMedia = { url, type ->
            scope.launch {
              val res = if (type == GeneratedMediaType.DOCUMENT) {
                MediaSaveHelper.saveAndOpenDocument(context, url, activeHost)
              } else {
                MediaSaveHelper.saveMediaToPhone(context, url, type, activeHost)
              }
              res
                .onSuccess { onShowBanner(it) }
                .onFailure { onShowBanner(it.localizedMessage ?: "Failed to save media") }
            }
          },
          onCopyPrompt = { promptText ->
            clipboard.setText(AnnotatedString(promptText))
            onShowBanner("Prompt copied to clipboard!")
          },
          onOpenLightbox = { msg -> lightboxMessage = msg },
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }

  if (showSettingsDialog) {
    AlertDialog(
      onDismissRequest = { showSettingsDialog = false },
      title = { Text("General Chat Settings") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("Spin Model Wheel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
          LlmRotaryWheelSelector(
            selectedOption = selectedWheelModel,
            onSelectOption = { option -> onSelectWheelModel(option, activeThread?.id) },
            darkSurface = false,
            accentColor = Color(0xFFFF5500),
          )

          if (activeThread != null) {
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(
                onClick = {
                  onTogglePinChat(activeThread.id, !activeThread.pinned)
                },
                modifier = Modifier.weight(1f),
              ) {
                Text(if (activeThread.pinned) "Unpin Chat" else "Pin Chat")
              }
              OutlinedButton(
                onClick = {
                  onDeleteChat(activeThread.id)
                  showSettingsDialog = false
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
              ) {
                Text("Delete Chat")
              }
            }
          }

          HorizontalDivider()
          Text("General & OpenClaw Settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
          Text(
            text = "Routes to OpenClaw's APK agent (openclaw/apk) with per-thread sessions.",
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
              showSettingsDialog = false
              onOpenHostSettings()
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Open OpenClaw & Host Settings")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showSettingsDialog = false }) { Text("Done") }
      },
    )
  }

  if (showAttachSheet) {
    AlertDialog(
      onDismissRequest = { showAttachSheet = false },
      title = { Text("Attach File to General Chat") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val attachPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
          OutlinedButton(
            onClick = {
              showAttachSheet = false
              documentLauncher.launch(arrayOf("*/*"))
            },
            contentPadding = attachPadding,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("general_pick_device_file_button"),
          ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Attach PDF, Code, or Document")
          }
          OutlinedButton(
            onClick = {
              showAttachSheet = false
              photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            contentPadding = attachPadding,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("general_pick_photo_button"),
          ) {
            Icon(Icons.Rounded.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Attach Screenshot / Photo")
          }
          if (linkedFiles.isNotEmpty()) {
            Text(
              text = "Recent Vault Files (tap to attach):",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            linkedFiles.take(4).forEach { file ->
              val selected = pendingAttachments.any { it.uriOrPath == file.uriOrPath }
              Surface(
                onClick = { onToggleAttachment(file) },
                shape = MaterialTheme.shapes.small,
                color = if (selected) {
                  MaterialTheme.colorScheme.secondaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "${if (selected) "✓ " else ""}${file.displayName}",
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(10.dp),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showAttachSheet = false }) { Text("Done") }
      },
    )
  }

  lightboxMessage?.let { targetMsg ->
    val url = targetMsg.mediaUrl
    if (!url.isNullOrBlank()) {
      MediaLightboxDialog(
        mediaUrl = url,
        mediaType = targetMsg.mediaTypeEnum,
        prompt = targetMsg.mediaPrompt ?: targetMsg.rawText,
        modelUsed = targetMsg.mediaModel ?: targetMsg.modelLabel,
        durationSec = targetMsg.mediaDurationSec,
        activeHost = activeHost,
        onDismiss = { lightboxMessage = null },
        onSaveToPhone = {
          scope.launch {
            val res = if (targetMsg.mediaTypeEnum == GeneratedMediaType.DOCUMENT) {
              MediaSaveHelper.saveAndOpenDocument(context, url, activeHost)
            } else {
              MediaSaveHelper.saveMediaToPhone(context, url, targetMsg.mediaTypeEnum, activeHost)
            }
            res
              .onSuccess { onShowBanner(it) }
              .onFailure { onShowBanner(it.localizedMessage ?: "Failed to save media") }
          }
        },
        onSendToChat = if (targetMsg.mediaTypeEnum == GeneratedMediaType.IMAGE) {
          {
            composerText = "Animate this image with lively cinematic motion: $url"
            lightboxMessage = null
          }
        } else null,
      )
    }
  }
}

@Composable
private fun GeneralChatsSidebar(
  threads: List<GeneralChatThread>,
  selectedChatId: String,
  filter: GeneralFilter,
  searchQuery: String,
  onSearchChange: (String) -> Unit,
  onToggleFilter: () -> Unit,
  onNewChat: () -> Unit,
  onSelectThread: (String) -> Unit,
  onTogglePin: (String, Boolean) -> Unit,
  onDeleteThread: (String) -> Unit,
  onOpenSectionSettings: () -> Unit,
  showSettingsIconOnSidebar: Boolean,
  modifier: Modifier = Modifier,
) {
  // Rich Neon Purple Left Panel
  Box(
    modifier = modifier
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF7B00FF),
            Color(0xFF9C27FF),
            Color(0xFF5600BA),
          )
        )
      )
      .background(
        Brush.radialGradient(
          colors = listOf(Color(0x44FF58D9), Color.Transparent),
          center = Offset(240f, 180f),
          radius = 500f,
        )
      )
      .testTag("general_chats_sidebar"),
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
          Icon(
            painter = painterResource(id = R.drawable.ic_min_general),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
          )
          Text(
            text = "General",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Button(
            onClick = onNewChat,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFFFF6D00),
              contentColor = Color.White,
            ),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
            modifier = Modifier
              .defaultMinSize(minHeight = 40.dp)
              .testTag("new_general_chat_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("New Chat", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
          }

          if (showSettingsIconOnSidebar) {
            SectionSettingsIconButton(
              contentDescription = "General Chat Settings",
              onClick = onOpenSectionSettings,
              tint = Color(0xFFFFB2EB),
              modifier = Modifier.testTag("general_section_settings_button"),
            )
          }
        }
      }

      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = {
          Text("Search normal questions...", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6C8FF))
        },
        leadingIcon = {
          Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFFFFC1E3), modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchChange("") }) {
              Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Color(0xFFFFC1E3))
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = Color.White,
          unfocusedTextColor = Color.White,
          focusedContainerColor = Color(0x553B0080),
          unfocusedContainerColor = Color(0x443B0080),
          focusedBorderColor = Color(0xFFFF9CE6),
          unfocusedBorderColor = Color(0x77DF9BFF),
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
          color = Color(0xFFFFD8F3),
          fontWeight = FontWeight.SemiBold,
        )
        IconButton(
          onClick = onToggleFilter,
          modifier = Modifier
            .size(44.dp)
            .testTag("general_filter_button"),
        ) {
          Icon(
            Icons.Rounded.FilterList,
            contentDescription = "Toggle filter between All and Pinned chats",
            tint = if (filter == GeneralFilter.PINNED) Color(0xFFFF9E40) else Color(0xFFFFC1E3),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
          ) {
            Text(
              text = if (filter == GeneralFilter.PINNED) "No pinned general chats yet" else "Your everyday Q&A zone",
              style = MaterialTheme.typography.titleSmall,
              color = Color.White,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )
            Text(
              text = "Tap New Chat for normal questions — separate from your Bot Dudes.",
              style = MaterialTheme.typography.bodySmall,
              color = Color(0xFFF3DCFF),
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
              color = if (selected) Color(0xFFFF6D00) else Color(0x40320070),
              border = BorderStroke(
                width = 1.dp,
                color = if (selected) Color(0xFFFFD180) else Color(0x55E2A4FF),
              ),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { onSelectThread(thread.id) }
                .testTag("general_chat_row_${thread.id}"),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = thread.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  if (thread.lastMessagePreview.isNotBlank()) {
                    Text(
                      text = thread.lastMessagePreview,
                      style = MaterialTheme.typography.labelSmall,
                      color = if (selected) Color(0xFFFFF3E0) else Color(0xFFE7CAFF),
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }

                IconButton(
                  onClick = { onTogglePin(thread.id, !thread.pinned) },
                  modifier = Modifier
                    .size(40.dp)
                    .testTag("pin_general_chat_${thread.id}"),
                ) {
                  Icon(
                    imageVector = if (thread.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (thread.pinned) "Unpin chat" else "Pin chat",
                    tint = if (thread.pinned) Color(0xFFFFEB3B) else Color(0xFFE0BAFF),
                    modifier = Modifier.size(18.dp),
                  )
                }

                IconButton(
                  onClick = { onDeleteThread(thread.id) },
                  modifier = Modifier
                    .size(40.dp)
                    .testTag("delete_general_chat_${thread.id}"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete chat",
                    tint = Color(0xFFE0BAFF),
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
private fun GeneralChatConversationPane(
  activeThread: GeneralChatThread?,
  messagesState: UiState<List<GeneralChatMessage>>,
  messages: List<GeneralChatMessage>,
  activeHost: OpenClawHost?,
  pendingAttachments: List<LinkedFile>,
  selectedWheelModel: WheelLlmOption,
  composerText: String,
  isBusy: Boolean,
  activeElapsedSec: Int,
  streamingPreviewText: String?,
  connectionFailureBanner: ConnectionFailureBanner?,
  showBackArrow: Boolean,
  onBackToList: () -> Unit,
  onTogglePin: () -> Unit,
  onToggleHostConnectionMode: (OpenClawHost) -> Unit,
  onSwitchModeAndRetry: (ConnectionFailureBanner) -> Unit,
  onDismissFailureBanner: () -> Unit,
  onAbortRequest: () -> Unit,
  onOpenHostSettings: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAttach: () -> Unit,
  onToggleAttachment: (LinkedFile) -> Unit,
  onComposerChange: (String) -> Unit,
  onSend: () -> Unit,
  onAnimateImage: (String) -> Unit,
  onSaveMedia: (String, GeneratedMediaType) -> Unit,
  onCopyPrompt: (String) -> Unit,
  onOpenLightbox: (GeneralChatMessage) -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()
  var pinnedToBottom by remember(activeThread?.id) { mutableStateOf(true) }
  var unreadBelow by remember(activeThread?.id) { mutableIntStateOf(0) }
  var didInitialScroll by remember(activeThread?.id) { mutableStateOf(false) }

  LaunchedEffect(listState, activeThread?.id) {
    snapshotFlow {
      val info = listState.layoutInfo
      val last = info.visibleItemsInfo.lastOrNull()
      last != null && last.index >= info.totalItemsCount - 1
    }.collect { atBottom ->
      pinnedToBottom = atBottom
      if (atBottom) {
        unreadBelow = 0
      } else {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        unreadBelow = (info.totalItemsCount - 1 - lastVisible).coerceIn(0, 99)
      }
    }
  }

  LaunchedEffect(activeThread?.id, messages.size) {
    if (messages.isEmpty()) return@LaunchedEffect
    if (!didInitialScroll) {
      listState.scrollToItem(messages.lastIndex)
      didInitialScroll = true
      unreadBelow = 0
    } else if (pinnedToBottom) {
      listState.animateScrollToItem(messages.lastIndex)
      unreadBelow = 0
    }
  }

  // Vibrant Neon Orange backdrop with Cha Chaan Teng atmosphere scrim (no offscreen alpha layer)
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFFF6D00)),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_cha_chaan_teng),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xB8FF6D00),
              Color(0xCCFF8F00),
              Color(0xC8FF3D00),
            )
          )
        ),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
    ) {
      // Slim, clean top bar giving title generous width without truncating to 3 chars
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        if (showBackArrow) {
          IconButton(
            onClick = onBackToList,
            modifier = Modifier
              .size(48.dp)
              .testTag("general_back_to_list_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "All General Chats", tint = Color.White)
          }
        }

        Text(
          text = activeThread?.title ?: "General Chat",
          style = MaterialTheme.typography.titleMedium.copy(
            shadow = Shadow(color = Color(0x663E1000), offset = Offset(0f, 2f), blurRadius = 4f)
          ),
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1f)
            .padding(end = 4.dp),
        )

        if (activeHost != null) {
          Surface(
            onClick = { onToggleHostConnectionMode(activeHost) },
            shape = RoundedCornerShape(50),
            color = Color(0xCC5600BA),
            border = BorderStroke(1.dp, Color(0xFFFFD180)),
            modifier = Modifier
              .defaultMinSize(minHeight = 36.dp)
              .testTag("general_connection_mode_pill"),
          ) {
            Text(
              text = activeHost.connectionModeEnum.shortPill,
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
          }
        }

        if (activeThread != null) {
          IconButton(
            onClick = onTogglePin,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = if (activeThread.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
              contentDescription = if (activeThread.pinned) "Unpin chat" else "Pin chat",
              tint = if (activeThread.pinned) Color(0xFFFFEB3B) else Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        SectionSettingsIconButton(
          contentDescription = "General Chat Settings",
          onClick = onOpenSettings,
          tint = Color.White,
          modifier = Modifier.testTag("general_section_settings_button"),
        )
      }

      // Messages or Simple Clean Prompt Chips (No Welcome to Toga Q&A Forum banner)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        when (messagesState) {
          is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = Color.White)
            }
          }
          is UiState.Error -> {
            Surface(
              color = Color(0xF5FFF8F2),
              shape = MaterialTheme.shapes.medium,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = messagesState.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(14.dp),
              )
            }
          }
          is UiState.Success -> {
            if (messages.isEmpty()) {
              Column(
                modifier = Modifier
                  .fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                Text(
                  text = "Ask anything normal here!",
                  style = MaterialTheme.typography.titleMedium.copy(
                    shadow = Shadow(color = Color(0x663E1000), offset = Offset(0f, 2f), blurRadius = 4f)
                  ),
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                )
                Spacer(Modifier.height(10.dp))

                listOf(
                  "Explain how Tailscale connects my Android phone to my home OpenClaw PC.",
                  "Compare running a local Ollama model vs a 3rd-party cloud LLM.",
                  "Help me brainstorm 5 quick dinner ideas using eggs, spinach, and rice.",
                ).forEach { sample ->
                  Surface(
                    onClick = { onComposerChange(sample) },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFAFFF9F0),
                    border = BorderStroke(1.2.dp, Color(0x55FF6D00)),
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                  ) {
                    Text(
                      text = "\"$sample\"",
                      style = MaterialTheme.typography.bodySmall,
                      color = Color(0xFF321100),
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    )
                  }
                }
              }
            } else {
              val latestPendingAssistantId = messages.lastOrNull {
                it.senderEnum != MessageSender.USER &&
                  (it.statusEnum == MessageStatus.DISPATCHED || it.statusEnum == MessageStatus.PARSING)
              }?.id
              val dudebroCompanion = remember { CompanionBot() }
              LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                items(messages, key = { it.id }) { msg ->
                  val isUser = msg.senderEnum == MessageSender.USER
                  val isErr = msg.statusEnum == MessageStatus.FAILED || msg.statusEnum == MessageStatus.WARNING
                  val isLivePending = !isUser && isBusy && msg.id == latestPendingAssistantId
                  val bubbleExpression = when (msg.statusEnum) {
                    MessageStatus.DISPATCHED, MessageStatus.PARSING -> BotExpression.THINKING
                    MessageStatus.FAILED -> BotExpression.SORRY
                    MessageStatus.WARNING -> BotExpression.WORRIED
                    else -> BotExpression.UH_HUH
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Top,
                  ) {
                    if (!isUser) {
                      Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                          .padding(top = 2.dp, end = 8.dp)
                          .size(48.dp)
                          .clip(CircleShape)
                          .background(
                            Brush.radialGradient(
                              colors = listOf(
                                Color.White.copy(alpha = 0.34f),
                                Color(0xFFFF6D00).copy(alpha = 0.22f),
                                Color.Transparent,
                              )
                            )
                          )
                          .testTag("general_chat_floating_head_${msg.id}"),
                      ) {
                        BotVectorAvatar(
                          bot = dudebroCompanion,
                          expression = bubbleExpression,
                          size = 44.dp,
                          animate = msg.statusEnum == MessageStatus.DISPATCHED || msg.statusEnum == MessageStatus.PARSING,
                        )
                      }
                    }

                    Surface(
                      shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isUser) 20.dp else 6.dp,
                        bottomEnd = if (isUser) 6.dp else 20.dp,
                      ),
                      color = if (isUser) Color(0xFF6A00F4) else Color(0xFAFFF8EE),
                      border = BorderStroke(
                        width = 1.2.dp,
                        color = when {
                          isErr -> MaterialTheme.colorScheme.error
                          isUser -> Color(0xAAFF9CE6)
                          else -> Color(0x55FF6D00)
                        },
                      ),
                      modifier = if (isUser) {
                        Modifier.widthIn(max = 540.dp)
                      } else {
                        Modifier
                          .weight(1f, fill = false)
                          .widthIn(max = 540.dp)
                      },
                    ) {
                      Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                      ) {
                        val cleanedText = remember(msg.rawText) { sanitizeGeneralDisplayText(msg.rawText) }
                        if (isLivePending) {
                          val displayText = streamingPreviewText?.takeIf { it.isNotBlank() } ?: cleanedText
                          Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2D0F00),
                          )
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                          ) {
                            Text(
                              text = "Thinking / running tools on host... (${activeElapsedSec}s)",
                              style = MaterialTheme.typography.labelSmall,
                              color = Color(0xFFFF5500),
                              fontWeight = FontWeight.SemiBold,
                              modifier = Modifier.weight(1f),
                            )
                            TextButton(
                              onClick = onAbortRequest,
                              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                              colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                              modifier = Modifier.testTag("general_abort_request_button"),
                            ) {
                              Text("Abort", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                          }
                        } else {
                          Text(
                            text = cleanedText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) {
                              Color.White
                            } else if (isErr) {
                              MaterialTheme.colorScheme.error
                            } else {
                              Color(0xFF2D0F00)
                            },
                          )
                        }

                        val mediaUrl = msg.mediaUrl
                        if (!mediaUrl.isNullOrBlank()) {
                          InlineMediaCard(
                            mediaUrl = mediaUrl,
                            mediaType = msg.mediaTypeEnum,
                            prompt = msg.mediaPrompt ?: msg.rawText,
                            modelUsed = msg.mediaModel ?: msg.modelLabel,
                            durationSec = msg.mediaDurationSec,
                            activeHost = activeHost,
                            onAnimateThis = if (msg.mediaTypeEnum == GeneratedMediaType.IMAGE) onAnimateImage else null,
                            onSaveToPhone = { onSaveMedia(mediaUrl, msg.mediaTypeEnum) },
                            onCopyPrompt = {
                              onCopyPrompt(msg.mediaPrompt?.takeIf { it.isNotBlank() } ?: msg.rawText)
                            },
                            onOpenLightbox = { onOpenLightbox(msg) },
                          )
                        }
                      }
                    }
                  }
                }
              }
              if (unreadBelow > 0) {
                FilledIconButton(
                  onClick = {
                    scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                    unreadBelow = 0
                  },
                  colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFF6A00F4),
                    contentColor = Color.White,
                  ),
                  modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .testTag("general_jump_to_latest_button"),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                  ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Jump to latest", Modifier.size(16.dp))
                    Text(
                      text = if (unreadBelow >= 99) "99+" else unreadBelow.toString(),
                      style = MaterialTheme.typography.labelSmall,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Fast-fail Mode Switch & Retry Banner
      AnimatedVisibility(visible = connectionFailureBanner != null) {
        connectionFailureBanner?.let { banner ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFCFFF3E0),
            border = BorderStroke(1.5.dp, Color(0xFFFF5500)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp)
              .testTag("general_connection_failure_banner"),
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = banner.cleanError,
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF4A1800),
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = onDismissFailureBanner,
                  modifier = Modifier.size(30.dp),
                ) {
                  Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = Color(0xFF4A1800), modifier = Modifier.size(16.dp))
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (banner.alternativeHasUrl) {
                  Button(
                    onClick = { onSwitchModeAndRetry(banner) },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = Color(0xFFFF5500),
                      contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                  ) {
                    Text(
                      text = "Switch to ${banner.alternativeMode.shortPill} & Retry",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                } else {
                  OutlinedButton(
                    onClick = onOpenHostSettings,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp),
                  ) {
                    Text(
                      text = "Set ${banner.alternativeMode.shortPill} URL",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Pending Document / Image Attachments
      AnimatedVisibility(visible = pendingAttachments.isNotEmpty()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          pendingAttachments.forEach { file ->
            val isImage = isGeneralImageAttachment(file)
            Surface(
              shape = RoundedCornerShape(18.dp),
              color = Color(0xFF6A00F4),
              border = BorderStroke(1.dp, Color(0xFFFFB2EB)),
            ) {
              Row(
                modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (isImage && file.uriOrPath.isNotBlank()) {
                  AsyncImage(
                    model = file.uriOrPath,
                    contentDescription = file.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .size(32.dp)
                      .clip(RoundedCornerShape(8.dp)),
                  )
                } else {
                  Icon(
                    imageVector = if (file.linkTypeEnum == FileLinkType.ANDROID_ATTACHMENT) {
                      Icons.Rounded.AttachFile
                    } else {
                      Icons.Rounded.Link
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White,
                  )
                }
                Text(
                  text = file.displayName,
                  style = MaterialTheme.typography.labelMedium,
                  color = Color.White,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.widthIn(max = 140.dp),
                )
                IconButton(
                  onClick = { onToggleAttachment(file) },
                  modifier = Modifier.size(24.dp),
                ) {
                  Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
          }
        }
      }

      // Composer
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFAFFF9F2),
        tonalElevation = 4.dp,
        border = BorderStroke(1.2.dp, Color(0x55FF6D00)),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 10.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          IconButton(
            onClick = onOpenAttach,
            modifier = Modifier
              .size(48.dp)
              .testTag("general_attach_button"),
          ) {
            Icon(
              Icons.Rounded.AttachFile,
              contentDescription = "Attach PDF, document, or image",
              tint = Color(0xFFFF5500),
            )
          }

          Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 44.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(Color(0xFFFFF4E6))
              .padding(horizontal = 12.dp, vertical = 10.dp),
          ) {
            if (composerText.isEmpty()) {
              Text(
                text = "Ask ${selectedWheelModel.wheelLabel}...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8C4A27),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = composerText,
              onValueChange = onComposerChange,
              textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF2E0D00)),
              cursorBrush = SolidColor(Color(0xFFFF6D00)),
              maxLines = 4,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("general_chat_input"),
            )
          }
          Spacer(Modifier.width(2.dp))
          com.example.ui.components.VoiceInputButton(
            onPartial = onComposerChange,
            onResult = onComposerChange,
            idleTint = Color(0xFF8C4A27),
          )
          Spacer(Modifier.width(2.dp))
          FilledIconButton(
            onClick = onSend,
            enabled = (composerText.isNotBlank() || pendingAttachments.isNotEmpty()) && !isBusy,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color(0xFFFF5500),
              contentColor = Color.White,
              disabledContainerColor = Color(0x44FF5500),
              disabledContentColor = Color(0xAA8C4A27),
            ),
            modifier = Modifier
              .size(48.dp)
              .testTag("general_send_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
              )
            } else {
              Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send general question")
            }
          }
        }
      }
    }
  }
}

private data class GeneralResolvedMeta(
  val name: String,
  val mime: String,
  val size: Long,
)

private fun resolveGeneralUriMeta(context: Context, uri: Uri): GeneralResolvedMeta {
  var name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
  var size = 0L
  val mime = context.contentResolver.getType(uri).orEmpty()
  try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
        if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
      }
    }
  } catch (_: Exception) {
    // Fallback
  }
  return GeneralResolvedMeta(name = name, mime = mime, size = size.coerceAtLeast(0L))
}

private val GENERAL_INLINE_DATA_IMAGE_REGEX = Regex(
  """!?\[[^\]]*\]\(data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\r\n]+\)|data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\r\n]+"""
)

private fun sanitizeGeneralDisplayText(raw: String): String {
  if (!raw.contains("data:image/", ignoreCase = true)) return raw
  val cleaned = GENERAL_INLINE_DATA_IMAGE_REGEX.replace(raw, "").trim()
  return cleaned.ifBlank { "Image attached below." }
}

private fun isGeneralImageAttachment(file: LinkedFile): Boolean {
  val mime = file.mimeOrExt.lowercase(Locale.US)
  val name = file.displayName.lowercase(Locale.US)
  return mime.startsWith("image/") ||
    name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
    name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".heic")
}
