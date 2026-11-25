package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.files.MediaSaveHelper
import com.example.model.BotExpression
import com.example.model.CaptainContextDocument
import com.example.model.ContextChatMessage
import com.example.model.ContextChatThread
import com.example.model.ContextDocStatus
import com.example.model.GeneratedMediaType
import com.example.model.LinkedFile
import com.example.model.MessageSender
import com.example.model.MessageStatus
import com.example.model.OpenClawHost
import com.example.model.WheelLlmOption
import com.example.ui.avatar.CaptainContextAvatar
import com.example.ui.components.InlineMediaCard
import com.example.ui.components.MediaLightboxDialog
import com.example.ui.components.SectionSettingsIconButton
import com.example.ui.components.VoiceInputButton
import com.example.viewmodel.ConnectionFailureBanner
import com.example.viewmodel.UiState
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CaptainContextScreen(
  contextChatsState: UiState<List<ContextChatThread>>,
  selectedChatId: String,
  messagesState: UiState<List<ContextChatMessage>>,
  activeDocs: List<CaptainContextDocument>,
  hostsState: UiState<List<OpenClawHost>>,
  linkedFilesState: UiState<List<LinkedFile>>,
  selectedWheelModel: WheelLlmOption,
  isBusy: Boolean,
  activeElapsedSec: Int,
  streamingPreviewText: String?,
  connectionFailureBanner: ConnectionFailureBanner?,
  onSelectChat: (String) -> Unit,
  onCreateNewCase: (WheelLlmOption) -> Unit,
  onTogglePinCase: (String, Boolean) -> Unit,
  onDeleteCase: (String) -> Unit,
  onSelectWheelModel: (WheelLlmOption, String?) -> Unit,
  onToggleHostConnectionMode: (OpenClawHost) -> Unit,
  onSwitchModeAndRetry: (ConnectionFailureBanner) -> Unit,
  onDismissFailureBanner: () -> Unit,
  onAbortRequest: () -> Unit,
  onAddDeviceDocs: (chatId: String, items: List<Triple<String, String, String>>) -> Unit,
  onAddDriveUrl: (chatId: String, url: String, title: String) -> Unit,
  onAddVaultFile: (chatId: String, file: LinkedFile) -> Unit,
  onRefreshDoc: (chatId: String, docId: String) -> Unit,
  onRemoveDoc: (chatId: String, docId: String) -> Unit,
  onSendMessage: (ContextChatThread?, String, WheelLlmOption, OpenClawHost?) -> Unit,
  onShowBanner: (String) -> Unit,
  onOpenHostSettings: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current

  val threads = (contextChatsState as? UiState.Success)?.data.orEmpty()
  val messages = (messagesState as? UiState.Success)?.data.orEmpty()
  val linkedFiles = (linkedFilesState as? UiState.Success)?.data.orEmpty()
  val activeHost = (hostsState as? UiState.Success)?.data?.firstOrNull()
  val activeThread = threads.firstOrNull { it.id == selectedChatId }

  var phoneShowingThread by remember { mutableStateOf(false) }
  var filter by remember { mutableStateOf(ContextCaseFilter.ALL) }
  var searchQuery by remember { mutableStateOf("") }
  var composerText by remember { mutableStateOf("") }
  var vaultExpanded by remember { mutableStateOf(true) }
  var showDriveUrlDialog by remember { mutableStateOf(false) }
  var showHostVaultDialog by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  var inspectingDoc by remember { mutableStateOf<CaptainContextDocument?>(null) }
  var lightboxMessage by remember { mutableStateOf<ContextChatMessage?>(null) }

  val multiDocLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    if (uris.isNotEmpty()) {
      val items = uris.map { uri ->
        runCatching {
          context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        resolveContextUriMeta(context, uri)
      }
      onAddDeviceDocs(selectedChatId, items)
      vaultExpanded = true
      phoneShowingThread = true
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
        (filter == ContextCaseFilter.ALL || thread.pinned) &&
          (q.isEmpty() ||
            thread.title.lowercase(Locale.US).contains(q) ||
            thread.docLabels.any { it.lowercase(Locale.US).contains(q) } ||
            thread.lastMessagePreview.lowercase(Locale.US).contains(q))
      }
      .sortedWith(
        compareByDescending<ContextChatThread> { it.pinned }
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
          CaptainContextSidebar(
            threads = sortedFilteredThreads,
            selectedChatId = activeThread?.id.orEmpty(),
            filter = filter,
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            onToggleFilter = {
              filter = if (filter == ContextCaseFilter.ALL) ContextCaseFilter.PINNED else ContextCaseFilter.ALL
            },
            onNewCase = { onCreateNewCase(selectedWheelModel) },
            onSelectThread = { id -> onSelectChat(id) },
            onTogglePin = onTogglePinCase,
            onDeleteThread = onDeleteCase,
            onOpenSectionSettings = { showSettingsDialog = true },
            showSettingsIconOnSidebar = false,
            modifier = Modifier
              .width(296.dp)
              .fillMaxHeight(),
          )

          CaptainContextConversationPane(
            activeThread = activeThread,
            messagesState = messagesState,
            messages = messages,
            activeDocs = activeDocs,
            activeHost = activeHost,
            selectedWheelModel = selectedWheelModel,
            composerText = composerText,
            vaultExpanded = vaultExpanded,
            isBusy = isBusy,
            activeElapsedSec = activeElapsedSec,
            streamingPreviewText = streamingPreviewText,
            connectionFailureBanner = connectionFailureBanner,
            showBackArrow = false,
            onBackToList = {},
            onToggleVaultExpanded = { vaultExpanded = !vaultExpanded },
            onPickDeviceDocs = { multiDocLauncher.launch(arrayOf("*/*")) },
            onOpenDriveLinkDialog = { showDriveUrlDialog = true },
            onOpenVaultPickerDialog = { showHostVaultDialog = true },
            onInspectDoc = { inspectingDoc = it },
            onRefreshDoc = { doc -> onRefreshDoc(selectedChatId, doc.id) },
            onRemoveDoc = { doc -> onRemoveDoc(selectedChatId, doc.id) },
            onTogglePin = {
              if (activeThread != null) onTogglePinCase(activeThread.id, !activeThread.pinned)
            },
            onToggleHostConnectionMode = onToggleHostConnectionMode,
            onSwitchModeAndRetry = onSwitchModeAndRetry,
            onDismissFailureBanner = onDismissFailureBanner,
            onAbortRequest = onAbortRequest,
            onOpenHostSettings = onOpenHostSettings,
            onOpenSettings = { showSettingsDialog = true },
            onComposerChange = { composerText = it },
            onSend = {
              val prompt = composerText
              composerText = ""
              onSendMessage(activeThread, prompt, selectedWheelModel, activeHost)
            },
            onSaveMedia = { url, type ->
              scope.launch {
                val res = if (type == GeneratedMediaType.DOCUMENT) {
                  MediaSaveHelper.saveAndOpenDocument(context, url, activeHost)
                } else {
                  MediaSaveHelper.saveMediaToPhone(context, url, type, activeHost)
                }
                res.onSuccess(onShowBanner).onFailure {
                  onShowBanner(it.localizedMessage ?: "Failed to save media")
                }
              }
            },
            onCopyPrompt = { txt ->
              clipboard.setText(AnnotatedString(txt))
              onShowBanner("Copied to clipboard!")
            },
            onOpenLightbox = { lightboxMessage = it },
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
          )
        }
      } else if (!phoneShowingThread) {
        CaptainContextSidebar(
          threads = sortedFilteredThreads,
          selectedChatId = activeThread?.id.orEmpty(),
          filter = filter,
          searchQuery = searchQuery,
          onSearchChange = { searchQuery = it },
          onToggleFilter = {
            filter = if (filter == ContextCaseFilter.ALL) ContextCaseFilter.PINNED else ContextCaseFilter.ALL
          },
          onNewCase = {
            phoneShowingThread = true
            onCreateNewCase(selectedWheelModel)
          },
          onSelectThread = { id ->
            onSelectChat(id)
            phoneShowingThread = true
          },
          onTogglePin = onTogglePinCase,
          onDeleteThread = onDeleteCase,
          onOpenSectionSettings = { showSettingsDialog = true },
          showSettingsIconOnSidebar = true,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        CaptainContextConversationPane(
          activeThread = activeThread,
          messagesState = messagesState,
          messages = messages,
          activeDocs = activeDocs,
          activeHost = activeHost,
          selectedWheelModel = selectedWheelModel,
          composerText = composerText,
          vaultExpanded = vaultExpanded,
          isBusy = isBusy,
          activeElapsedSec = activeElapsedSec,
          streamingPreviewText = streamingPreviewText,
          connectionFailureBanner = connectionFailureBanner,
          showBackArrow = true,
          onBackToList = { phoneShowingThread = false },
          onToggleVaultExpanded = { vaultExpanded = !vaultExpanded },
          onPickDeviceDocs = { multiDocLauncher.launch(arrayOf("*/*")) },
          onOpenDriveLinkDialog = { showDriveUrlDialog = true },
          onOpenVaultPickerDialog = { showHostVaultDialog = true },
          onInspectDoc = { inspectingDoc = it },
          onRefreshDoc = { doc -> onRefreshDoc(selectedChatId, doc.id) },
          onRemoveDoc = { doc -> onRemoveDoc(selectedChatId, doc.id) },
          onTogglePin = {
            if (activeThread != null) onTogglePinCase(activeThread.id, !activeThread.pinned)
          },
          onToggleHostConnectionMode = onToggleHostConnectionMode,
          onSwitchModeAndRetry = onSwitchModeAndRetry,
          onDismissFailureBanner = onDismissFailureBanner,
          onAbortRequest = onAbortRequest,
          onOpenHostSettings = onOpenHostSettings,
          onOpenSettings = { showSettingsDialog = true },
          onComposerChange = { composerText = it },
          onSend = {
            val prompt = composerText
            composerText = ""
            onSendMessage(activeThread, prompt, selectedWheelModel, activeHost)
          },
          onSaveMedia = { url, type ->
            scope.launch {
              val res = if (type == GeneratedMediaType.DOCUMENT) {
                MediaSaveHelper.saveAndOpenDocument(context, url, activeHost)
              } else {
                MediaSaveHelper.saveMediaToPhone(context, url, type, activeHost)
              }
              res.onSuccess(onShowBanner).onFailure {
                onShowBanner(it.localizedMessage ?: "Failed to save media")
              }
            }
          },
          onCopyPrompt = { txt ->
            clipboard.setText(AnnotatedString(txt))
            onShowBanner("Copied to clipboard!")
          },
          onOpenLightbox = { lightboxMessage = it },
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }

  if (showDriveUrlDialog) {
    CaptainContextDriveUrlDialog(
      onDismiss = { showDriveUrlDialog = false },
      onPickViaAndroidDrive = { multiDocLauncher.launch(arrayOf("*/*")) },
      onSubmitUrl = { url, customTitle ->
        onAddDriveUrl(selectedChatId, url, customTitle)
        vaultExpanded = true
      },
    )
  }

  if (showHostVaultDialog) {
    CaptainContextHostVaultDialog(
      linkedFiles = linkedFiles,
      loadedRefs = activeDocs.map { it.uriOrPath }.toSet(),
      onDismiss = { showHostVaultDialog = false },
      onSelectFile = { file ->
        onAddVaultFile(selectedChatId, file)
        vaultExpanded = true
      },
    )
  }

  inspectingDoc?.let { targetDoc ->
    val liveDoc = activeDocs.firstOrNull { it.id == targetDoc.id } ?: targetDoc
    CaptainContextDocPreviewDialog(
      doc = liveDoc,
      onDismiss = { inspectingDoc = null },
      onRefresh = { onRefreshDoc(selectedChatId, liveDoc.id) },
      onPickViaDrive = { multiDocLauncher.launch(arrayOf("*/*")) },
    )
  }

  if (showSettingsDialog) {
    CaptainContextSettingsDialog(
      activeThread = activeThread,
      activeHost = activeHost,
      selectedWheelModel = selectedWheelModel,
      onSelectWheelModel = { option -> onSelectWheelModel(option, activeThread?.id) },
      onTogglePin = {
        if (activeThread != null) onTogglePinCase(activeThread.id, !activeThread.pinned)
      },
      onDeleteCase = {
        if (activeThread != null) onDeleteCase(activeThread.id)
      },
      onOpenHostSettings = onOpenHostSettings,
      onDismiss = { showSettingsDialog = false },
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
            res.onSuccess(onShowBanner).onFailure {
              onShowBanner(it.localizedMessage ?: "Failed to save media")
            }
          }
        },
        onSendToChat = null,
      )
    }
  }
}

@Composable
private fun CaptainContextConversationPane(
  activeThread: ContextChatThread?,
  messagesState: UiState<List<ContextChatMessage>>,
  messages: List<ContextChatMessage>,
  activeDocs: List<CaptainContextDocument>,
  activeHost: OpenClawHost?,
  selectedWheelModel: WheelLlmOption,
  composerText: String,
  vaultExpanded: Boolean,
  isBusy: Boolean,
  activeElapsedSec: Int,
  streamingPreviewText: String?,
  connectionFailureBanner: ConnectionFailureBanner?,
  showBackArrow: Boolean,
  onBackToList: () -> Unit,
  onToggleVaultExpanded: () -> Unit,
  onPickDeviceDocs: () -> Unit,
  onOpenDriveLinkDialog: () -> Unit,
  onOpenVaultPickerDialog: () -> Unit,
  onInspectDoc: (CaptainContextDocument) -> Unit,
  onRefreshDoc: (CaptainContextDocument) -> Unit,
  onRemoveDoc: (CaptainContextDocument) -> Unit,
  onTogglePin: () -> Unit,
  onToggleHostConnectionMode: (OpenClawHost) -> Unit,
  onSwitchModeAndRetry: (ConnectionFailureBanner) -> Unit,
  onDismissFailureBanner: () -> Unit,
  onAbortRequest: () -> Unit,
  onOpenHostSettings: () -> Unit,
  onOpenSettings: () -> Unit,
  onComposerChange: (String) -> Unit,
  onSend: () -> Unit,
  onSaveMedia: (String, GeneratedMediaType) -> Unit,
  onCopyPrompt: (String) -> Unit,
  onOpenLightbox: (ContextChatMessage) -> Unit,
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

  val heroMood = remember(isBusy, activeDocs, messages) {
    val lastBot = messages.lastOrNull { it.senderEnum != MessageSender.USER }
    when {
      isBusy || activeDocs.any { it.status == ContextDocStatus.LOADING } -> BotExpression.WORKING
      lastBot?.statusEnum == MessageStatus.FAILED -> BotExpression.SORRY
      activeDocs.any { it.status == ContextDocStatus.NEEDS_ACCESS || it.status == ContextDocStatus.ERROR } -> BotExpression.WORRIED
      activeDocs.any { it.status == ContextDocStatus.READY } || lastBot?.statusEnum == MessageStatus.COMPLETED -> BotExpression.UH_HUH
      else -> BotExpression.HAPPY
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFF182978),
            Color(0xFF2744B8),
            Color(0xFF101A52),
          )
        )
      )
      .background(
        Brush.radialGradient(
          colors = listOf(Color(0x33FFD54F), Color.Transparent),
          center = Offset(320f, 180f),
          radius = 650f,
        )
      ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // Top Comic Hero Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (showBackArrow) {
          IconButton(
            onClick = onBackToList,
            modifier = Modifier
              .size(48.dp)
              .testTag("context_back_to_list_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "All Captain Context Cases", tint = Color.White)
          }
        }

        CaptainContextAvatar(
          expression = heroMood,
          size = 44.dp,
          animate = isBusy,
          showCapeAndScroll = true,
        )

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = activeThread?.title ?: "Captain Context",
            style = MaterialTheme.typography.titleMedium.copy(
              shadow = Shadow(color = HeroComicInk, offset = Offset(0f, 2f), blurRadius = 4f)
            ),
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = when {
              isBusy -> "Scanning Vault sources..."
              activeDocs.isNotEmpty() -> "Grounded in ${activeDocs.count { it.status == ContextDocStatus.READY }} doc(s)"
              else -> "Drop PDFs or Drive links below!"
            },
            style = MaterialTheme.typography.labelSmall,
            color = HeroGoldAccent,
            maxLines = 1,
          )
        }

        if (activeHost != null) {
          Surface(
            onClick = { onToggleHostConnectionMode(activeHost) },
            shape = RoundedCornerShape(50),
            color = Color(0xFF0E1B54),
            border = BorderStroke(1.5.dp, HeroGoldAccent),
            modifier = Modifier.testTag("context_connection_mode_pill"),
          ) {
            Text(
              text = activeHost.connectionModeEnum.shortPill,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
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
              contentDescription = if (activeThread.pinned) "Unpin case" else "Pin case",
              tint = if (activeThread.pinned) HeroGoldAccent else Color.White,
            )
          }
        }

        SectionSettingsIconButton(
          contentDescription = "Captain Context Settings",
          onClick = onOpenSettings,
          tint = HeroGoldAccent,
          modifier = Modifier.testTag("context_section_settings_button"),
        )
      }

      // Collapsible Document Vault Deck
      ContextVaultDeck(
        docs = activeDocs,
        expanded = vaultExpanded,
        onToggleExpand = onToggleVaultExpanded,
        onPickDeviceDocs = onPickDeviceDocs,
        onOpenDriveLinkDialog = onOpenDriveLinkDialog,
        onOpenVaultPickerDialog = onOpenVaultPickerDialog,
        onInspectDoc = onInspectDoc,
        onRefreshDoc = onRefreshDoc,
        onRemoveDoc = onRemoveDoc,
      )

      // Message Stream or Document-Driven Empty Hero Stage
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        when (messagesState) {
          is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = HeroGoldAccent)
            }
          }
          is UiState.Error -> {
            Surface(
              color = Color(0xFFFFEBEE),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(2.dp, HeroComicInk),
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
                  .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                CaptainContextAvatar(
                  expression = heroMood,
                  size = 110.dp,
                  animate = isBusy,
                  showCapeAndScroll = true,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                  text = if (activeDocs.any { it.status == ContextDocStatus.READY }) {
                    "Vault locked & loaded!"
                  } else {
                    "CAPTAIN CONTEXT REPORTING IN!"
                  },
                  style = MaterialTheme.typography.titleMedium.copy(
                    shadow = Shadow(color = HeroComicInk, offset = Offset(0f, 2f), blurRadius = 4f)
                  ),
                  fontWeight = FontWeight.ExtraBold,
                  color = HeroGoldAccent,
                )
                Spacer(Modifier.height(6.dp))

                val firstReady = activeDocs.firstOrNull { it.status == ContextDocStatus.READY }?.displayName
                val quickPrompts = if (firstReady != null) {
                  listOf(
                    "Summarize the key takeaways from $firstReady with source citations.",
                    "List every important date, number, and obligation in $firstReady.",
                    "What open questions or risks appear across my loaded documents?",
                  )
                } else {
                  listOf(
                    "Pick a PDF or Google Drive Doc above first, then ask me to summarize it!",
                    "Compare two loaded contracts or specs and highlight differences.",
                    "Verify if a specific policy or deadline is actually written in the docs.",
                  )
                }
                quickPrompts.forEach { sample ->
                  Surface(
                    onClick = {
                      if (firstReady != null) {
                        onComposerChange(sample)
                      } else {
                        onPickDeviceDocs()
                      }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFAFFFDF5),
                    border = BorderStroke(2.dp, HeroComicInk),
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                  ) {
                    Text(
                      text = "\"$sample\"",
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.SemiBold,
                      color = HeroComicInk,
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                  }
                }
              }
            } else {
              val latestPendingAssistantId = messages.lastOrNull {
                it.senderEnum != MessageSender.USER &&
                  (it.statusEnum == MessageStatus.DISPATCHED || it.statusEnum == MessageStatus.PARSING)
              }?.id
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
                  val bubbleExpr = when (msg.statusEnum) {
                    MessageStatus.DISPATCHED, MessageStatus.PARSING -> BotExpression.WORKING
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
                          .background(HeroGoldAccent.copy(alpha = 0.24f))
                          .testTag("context_floating_head_${msg.id}"),
                      ) {
                        CaptainContextAvatar(
                          expression = bubbleExpr,
                          size = 44.dp,
                          animate = msg.statusEnum == MessageStatus.DISPATCHED || msg.statusEnum == MessageStatus.PARSING,
                          showCapeAndScroll = false,
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
                      color = if (isUser) HeroRoyalBlue else Color(0xFAFFFDF5),
                      border = BorderStroke(
                        width = 2.dp,
                        color = if (isErr) HeroCrimson else HeroComicInk,
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                      ) {
                        val cleanedText = remember(msg.rawText) { sanitizeContextDisplayText(msg.rawText) }
                        if (isLivePending) {
                          val displayText = streamingPreviewText?.takeIf { it.isNotBlank() } ?: cleanedText
                          Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = HeroComicInk,
                          )
                          Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                          ) {
                            Text(
                              text = "Consulting Vault docs... (${activeElapsedSec}s)",
                              style = MaterialTheme.typography.labelSmall,
                              color = HeroRoyalBlue,
                              fontWeight = FontWeight.Bold,
                              modifier = Modifier.weight(1f),
                            )
                            TextButton(
                              onClick = onAbortRequest,
                              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                              colors = ButtonDefaults.textButtonColors(contentColor = HeroCrimson),
                              modifier = Modifier.testTag("context_abort_request_button"),
                            ) {
                              Text("Abort", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                            }
                          }
                        } else {
                          Text(
                            text = cleanedText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                              isUser -> Color.White
                              isErr -> HeroCrimson
                              else -> HeroComicInk
                            },
                          )
                        }

                        if (!isUser && msg.citedDocs.isNotEmpty()) {
                          Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                          ) {
                            msg.citedDocs.take(3).forEach { docLabel ->
                              Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = HeroGoldAccent,
                                border = BorderStroke(1.2.dp, HeroComicInk),
                              ) {
                                Text(
                                  text = "Source: $docLabel",
                                  style = MaterialTheme.typography.labelSmall,
                                  fontWeight = FontWeight.ExtraBold,
                                  color = HeroComicInk,
                                  maxLines = 1,
                                  overflow = TextOverflow.Ellipsis,
                                  modifier = Modifier
                                    .widthIn(max = 170.dp)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                              }
                            }
                          }
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
                            onAnimateThis = null,
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
                    containerColor = HeroRoyalBlue,
                    contentColor = Color.White,
                  ),
                  modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .testTag("context_jump_to_latest_button"),
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

      AnimatedVisibility(visible = connectionFailureBanner != null) {
        connectionFailureBanner?.let { banner ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFFF8E1),
            border = BorderStroke(2.dp, HeroComicInk),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("context_connection_failure_banner"),
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
                  color = HeroComicInk,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismissFailureBanner, modifier = Modifier.size(32.dp)) {
                  Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = HeroComicInk, modifier = Modifier.size(16.dp))
                }
              }
              if (banner.alternativeHasUrl) {
                Button(
                  onClick = { onSwitchModeAndRetry(banner) },
                  colors = ButtonDefaults.buttonColors(containerColor = HeroRoyalBlue, contentColor = Color.White),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                  Text("Switch to ${banner.alternativeMode.shortPill} & Retry", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
              } else {
                OutlinedButton(
                  onClick = onOpenHostSettings,
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                  Text("Set ${banner.alternativeMode.shortPill} URL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }

      // Composer Bar
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFAFFFDF5),
        border = BorderStroke(2.2.dp, HeroComicInk),
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 8.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          IconButton(
            onClick = onPickDeviceDocs,
            modifier = Modifier
              .size(48.dp)
              .testTag("context_composer_attach_button"),
          ) {
            Icon(
              Icons.Rounded.AttachFile,
              contentDescription = "Add PDF or document to Captain Context",
              tint = HeroRoyalBlue,
            )
          }

          Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
              .weight(1f)
              .defaultMinSize(minHeight = 44.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(Color(0xFFEFF4FF))
              .padding(horizontal = 12.dp, vertical = 10.dp),
          ) {
            if (composerText.isEmpty()) {
              Text(
                text = if (activeDocs.isEmpty()) "Load docs or ask Captain Context..." else "Ask about your docs...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF3949AB),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            BasicTextField(
              value = composerText,
              onValueChange = onComposerChange,
              textStyle = MaterialTheme.typography.bodyMedium.copy(color = HeroComicInk),
              cursorBrush = SolidColor(HeroRoyalBlue),
              maxLines = 4,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("context_chat_input"),
            )
          }
          Spacer(Modifier.width(2.dp))
          VoiceInputButton(
            onPartial = onComposerChange,
            onResult = onComposerChange,
            idleTint = HeroRoyalBlue,
          )
          Spacer(Modifier.width(2.dp))
          FilledIconButton(
            onClick = onSend,
            enabled = composerText.isNotBlank() && !isBusy,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = HeroRoyalBlue,
              contentColor = Color.White,
              disabledContainerColor = Color(0x441E5BFF),
              disabledContentColor = Color(0xAA3949AB),
            ),
            modifier = Modifier
              .size(48.dp)
              .testTag("context_send_button"),
          ) {
            if (isBusy) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp,
              )
            } else {
              Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask Captain Context")
            }
          }
        }
      }
    }
  }
}

private fun resolveContextUriMeta(context: Context, uri: Uri): Triple<String, String, String> {
  var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
  val mime = context.contentResolver.getType(uri).orEmpty()
  try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
      }
    }
  } catch (_: Exception) {
    // fallback
  }
  return Triple(name, uri.toString(), mime)
}

private val CONTEXT_INLINE_DATA_IMAGE_REGEX = Regex(
  """!?\[[^\]]*\]\(data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\r\n]+\)|data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\r\n]+"""
)

private fun sanitizeContextDisplayText(raw: String): String {
  if (!raw.contains("data:image/", ignoreCase = true)) return raw
  val cleaned = CONTEXT_INLINE_DATA_IMAGE_REGEX.replace(raw, "").trim()
  return cleaned.ifBlank { "Image attached below." }
}
