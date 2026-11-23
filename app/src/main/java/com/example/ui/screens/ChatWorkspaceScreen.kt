package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.files.MediaSaveHelper
import com.example.model.BackgroundAgentTask
import com.example.model.BackgroundTaskStatus
import com.example.model.BotExpression
import com.example.model.BotMessage
import com.example.model.CompanionBot
import com.example.model.FileLinkType
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
import com.example.ui.util.ReplyHaptics
import com.google.firebase.Timestamp
import com.example.viewmodel.ConnectionFailureBanner
import com.example.viewmodel.UiState
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatWorkspaceScreen(
  botsState: UiState<List<CompanionBot>>,
  selectedBotId: String,
  messagesState: UiState<List<BotMessage>>,
  hostsState: UiState<List<OpenClawHost>>,
  linkedFilesState: UiState<List<LinkedFile>>,
  liveExpression: BotExpression,
  pendingAttachments: List<LinkedFile>,
  selectedWheelModel: WheelLlmOption,
  isBusy: Boolean,
  busyBotIds: Set<String> = emptySet(),
  backgroundTasks: List<BackgroundAgentTask> = emptyList(),
  activeElapsedSec: Int = 0,
  streamingPreviewText: String? = null,
  connectionFailureBanner: ConnectionFailureBanner? = null,
  pendingChatDraft: String? = null,
  savedChatDraft: String? = null,
  onChatDraftChanged: (String, String) -> Unit = { _, _ -> },
  onConsumePendingChatDraft: () -> Unit = {},
  onShowBanner: (String) -> Unit = {},
  onSelectBot: (String) -> Unit,
  onSelectWheelModel: (WheelLlmOption) -> Unit,
  onSwitchModeAndRetry: (ConnectionFailureBanner) -> Unit = {},
  onDismissFailureBanner: () -> Unit = {},
  onAbortRequest: () -> Unit = {},
  onSpawnBackgroundTask: (String, CompanionBot, OpenClawHost?, WheelLlmOption) -> Unit = { _, _, _, _ -> },
  onAbortBackgroundTask: (String) -> Unit = {},
  onBack: () -> Unit,
  onAddBotClick: () -> Unit,
  onEditBotClick: (CompanionBot) -> Unit,
  onRenameBot: (CompanionBot, String) -> Unit,
  onUpdateMission: (CompanionBot, String) -> Unit,
  onDeleteBotClick: (String) -> Unit,
  onSendMessage: (String, CompanionBot, OpenClawHost?, WheelLlmOption) -> Unit,
  onRetryMessage: (BotMessage, CompanionBot, OpenClawHost?) -> Unit,
  onAttachDeviceFile: (String, String, String, Long) -> Unit,
  onAddHostPathLink: (String, String) -> Unit,
  onToggleAttachment: (LinkedFile) -> Unit,
  onPreviewExpression: (BotExpression) -> Unit,
  onOpenTakeover: () -> Unit,
  onOpenHostSettings: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val haptic = LocalHapticFeedback.current
  val clipboard = LocalClipboardManager.current
  val bots = (botsState as? UiState.Success)?.data.orEmpty()
  val activeBot = bots.firstOrNull { it.id == selectedBotId } ?: bots.firstOrNull()
  val hosts = (hostsState as? UiState.Success)?.data.orEmpty()
  val activeHost = hosts.firstOrNull()
  val linkedFiles = (linkedFilesState as? UiState.Success)?.data.orEmpty()
  val messages = (messagesState as? UiState.Success)?.data.orEmpty()

  var minusMode by remember { mutableStateOf(false) }
  var spawnInBackground by remember { mutableStateOf(false) }
  var composerText by remember { mutableStateOf("") }
  var showLinkDialog by remember { mutableStateOf(false) }
  var showAttachSheet by remember { mutableStateOf(false) }
  var showBotDetailsDialog by remember { mutableStateOf(false) }
  var botPendingDelete by remember { mutableStateOf<CompanionBot?>(null) }
  var botPendingRename by remember { mutableStateOf<CompanionBot?>(null) }
  var botPendingMissionEdit by remember { mutableStateOf<CompanionBot?>(null) }
  var lightboxMessage by remember { mutableStateOf<BotMessage?>(null) }

  // Restore any draft typed for this bot in an earlier visit.
  LaunchedEffect(selectedBotId) {
    val draft = savedChatDraft.orEmpty()
    if (draft.isNotBlank() && composerText.isBlank()) composerText = draft
  }

  // Persist as the user types, debounced so we are not writing on every keystroke.
  LaunchedEffect(composerText, selectedBotId) {
    if (selectedBotId.isBlank()) return@LaunchedEffect
    delay(400)
    onChatDraftChanged(selectedBotId, composerText)
  }

  LaunchedEffect(pendingChatDraft) {
    if (!pendingChatDraft.isNullOrBlank()) {
      composerText = pendingChatDraft
      onConsumePendingChatDraft()
    }
  }

  // Subtle Grok-like vibration when a bot starts thinking or finishes replying
  var hapticsBaselineReady by remember(selectedBotId) { mutableStateOf(false) }
  var seenBotStatuses by remember(selectedBotId) { mutableStateOf<Map<String, MessageStatus>>(emptyMap()) }
  LaunchedEffect(selectedBotId, messagesState) {
    val loaded = (messagesState as? UiState.Success)?.data ?: return@LaunchedEffect
    val currentBotStatuses = loaded
      .filter { it.senderEnum == MessageSender.BOT }
      .associate { it.id to it.statusEnum }

    if (!hapticsBaselineReady) {
      seenBotStatuses = currentBotStatuses
      hapticsBaselineReady = true
      return@LaunchedEffect
    }

    var shouldPulseReply = false
    var shouldTickThinking = false
    currentBotStatuses.forEach { (id, status) ->
      val prevStatus = seenBotStatuses[id]
      if (prevStatus == null) {
        if (status == MessageStatus.COMPLETED || status == MessageStatus.WARNING || status == MessageStatus.FAILED) {
          shouldPulseReply = true
        } else if (status == MessageStatus.PARSING || status == MessageStatus.DISPATCHED) {
          shouldTickThinking = true
        }
      } else if (prevStatus != status) {
        if (status == MessageStatus.COMPLETED || status == MessageStatus.WARNING || status == MessageStatus.FAILED) {
          shouldPulseReply = true
        } else if (status == MessageStatus.DISPATCHED) {
          shouldTickThinking = true
        }
      }
    }

    if (shouldPulseReply) {
      ReplyHaptics.triggerReplyPulse(context, haptic)
    } else if (shouldTickThinking) {
      ReplyHaptics.triggerSoftTick(context, haptic)
    }
    seenBotStatuses = currentBotStatuses
  }

  val listState = rememberLazyListState()
  // Only follow new messages when the user is already at the bottom; otherwise they get
  // yanked away from the history they are reading. A pill offers the jump back.
  var pinnedToBottom by remember { mutableStateOf(true) }
  var unreadBelow by remember { mutableIntStateOf(0) }
  LaunchedEffect(listState) {
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
  LaunchedEffect(messages.size) {
    if (messages.isEmpty()) return@LaunchedEffect
    if (pinnedToBottom) {
      listState.animateScrollToItem(messages.lastIndex)
      unreadBelow = 0
    }
    // When unpinned the counter is recomputed on scroll below, so it reflects how many
    // messages actually sit under the viewport rather than how many arrived.
  }

  val documentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    uris.forEach { uri ->
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val meta = resolveUriMetadata(context, uri)
      onAttachDeviceFile(meta.name, uri.toString(), meta.mime, meta.size)
    }
  }

  val photoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      val meta = resolveUriMetadata(context, uri)
      onAttachDeviceFile(meta.name, uri.toString(), meta.mime.ifBlank { "image/*" }, meta.size)
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFFFF1A8C),
            Color(0xFFFF3DA8),
            Color(0xFFE6007A),
          )
        )
      ),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_cha_chaan_teng),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxSize()
        .alpha(0.24f),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(
              Color(0x44FF9CE6),
              Color.Transparent,
            ),
            center = Offset(540f, 380f),
            radius = 850f,
          )
        )
        .padding(horizontal = 12.dp),
    ) {
    // 1. Back + Bot Crew Strip (+ Add Bot / - Remove Bot)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      FilledIconButton(
        onClick = onBack,
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = Color(0xEBFFF8FC),
          contentColor = Color(0xFF280842),
        ),
        modifier = Modifier
          .size(48.dp)
          .testTag("chat_back_home_button"),
      ) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Bot Dudes")
      }
      Box(modifier = Modifier.weight(1f)) {
        BotCrewBar(
          bots = bots,
          activeBotId = activeBot?.id.orEmpty(),
          busyBotIds = busyBotIds,
          minusMode = minusMode,
          onToggleMinusMode = { minusMode = !minusMode },
          onSelectBot = onSelectBot,
          onAddBot = onAddBotClick,
          onRequestDeleteBot = { botPendingDelete = it },
        )
      }
      SectionSettingsIconButton(
        contentDescription = "Bot & OpenClaw Settings",
        onClick = { showBotDetailsDialog = true },
        tint = Color.White,
        modifier = Modifier.testTag("chat_section_settings_button"),
      )
    }

    Spacer(Modifier.height(6.dp))

    // 2 + 3. Adaptive Chat Window:
    // On wider layouts (>= 580.dp), pin a dedicated large animated Chat Head dock on the left side of the chat window.
    // On compact phones, feature a big 132.dp animated left-hand Chat Head stage above the feed AND 82.dp animated heads beside bot messages.
    BoxWithConstraints(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
    ) {
      val useSideChatHeadPane = maxWidth >= 580.dp && activeBot != null

      @Composable
      fun ChatFeedBox(modifier: Modifier) {
        Box(modifier = modifier) {
          when (messagesState) {
            is UiState.Loading -> {
              Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }
            }
            is UiState.Error -> {
              Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(8.dp),
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
                EmptyChatPrompt(
                  botName = activeBot?.name ?: "your bot",
                  onSampleClick = { sample -> composerText = sample },
                )
              } else {
                val latestPendingBotId = messages.lastOrNull {
                  it.senderEnum == MessageSender.BOT &&
                    (it.statusEnum == MessageStatus.DISPATCHED || it.statusEnum == MessageStatus.PARSING)
                }?.id
                LazyColumn(
                  state = listState,
                  contentPadding = PaddingValues(vertical = 8.dp),
                  verticalArrangement = Arrangement.spacedBy(12.dp),
                  modifier = Modifier.fillMaxSize(),
                ) {
                  itemsIndexed(messages, key = { _, msg -> msg.id }) { index, msg ->
                    val dayLabel = remember(messages.getOrNull(index - 1)?.createdAt, msg.createdAt) {
                      daySeparatorLabel(messages.getOrNull(index - 1)?.createdAt, msg.createdAt)
                    }
                    if (dayLabel != null) {
                      DaySeparator(dayLabel)
                    }
                    val isLiveStreaming = isBusy && msg.id == latestPendingBotId
                    MessageBubbleItem(
                      message = msg,
                      bot = activeBot,
                      activeHost = activeHost,
                      isLiveStreaming = isLiveStreaming,
                      liveElapsedSec = activeElapsedSec,
                      streamingPreviewText = if (isLiveStreaming) streamingPreviewText else null,
                      onAbortRequest = onAbortRequest,
                      onCopyInstruction = { text -> clipboard.setText(AnnotatedString(text)) },
                      onRetry = {
                        if (activeBot != null) onRetryMessage(msg, activeBot, activeHost)
                      },
                      onOpenTakeover = onOpenTakeover,
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
                      onCopyMediaPrompt = { promptText ->
                        clipboard.setText(AnnotatedString(promptText))
                        onShowBanner("Prompt copied to clipboard!")
                      },
                      onOpenLightbox = { lightboxMessage = msg },
                    )
                  }
                }
                if (unreadBelow > 0) {
                  FilledIconButton(
                    onClick = {
                      scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                      unreadBelow = 0
                    },
                    modifier = Modifier
                      .align(Alignment.BottomCenter)
                      .padding(bottom = 12.dp)
                      .testTag("jump_to_latest_button"),
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
      }

      if (useSideChatHeadPane && activeBot != null) {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          LeftHandChatHeadDock(
            bot = activeBot,
            expression = liveExpression,
            isBusy = isBusy,
            onOpenBotDetails = { showBotDetailsDialog = true },
            onOpenTakeover = onOpenTakeover,
            modifier = Modifier
              .width(210.dp)
              .fillMaxHeight(),
          )
          ChatFeedBox(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
          )
        }
      } else {
        Column(modifier = Modifier.fillMaxSize()) {
          if (activeBot != null) {
            LiveBotStageCard(
              bot = activeBot,
              expression = liveExpression,
              isBusy = isBusy,
              onOpenBotDetails = { showBotDetailsDialog = true },
              onOpenTakeover = onOpenTakeover,
            )
          } else if (botsState is UiState.Loading) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
              contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          } else {
            EmptyBotCrewCard(onAddBotClick = onAddBotClick)
          }

          Spacer(Modifier.height(6.dp))

          ChatFeedBox(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
          )
        }
      }
    }

    // Fast-fail Mode Switch & Retry Banner
    AnimatedVisibility(visible = connectionFailureBanner != null) {
      connectionFailureBanner?.let { banner ->
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xFCFFF3E0),
          border = BorderStroke(1.5.dp, Color(0xFFFF2E93)),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .testTag("bot_connection_failure_banner"),
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
                    containerColor = Color(0xFFFF2E93),
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

    // 4. Pending File & Path Chips
    AnimatedVisibility(visible = pendingAttachments.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        pendingAttachments.forEach { file ->
          val isImage = isBotImageAttachment(file)
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
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
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
              }
              Text(
                text = file.displayName,
                style = MaterialTheme.typography.labelMedium,
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
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }
      }
    }

    // Active Background Sub-Agents strip
    val activeOrRecentTasks = remember(backgroundTasks, activeBot?.id) {
      backgroundTasks.filter {
        it.status == BackgroundTaskStatus.RUNNING || it.botId == activeBot?.id
      }.take(4)
    }
    AnimatedVisibility(visible = activeOrRecentTasks.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        activeOrRecentTasks.forEach { task ->
          val isRunning = task.status == BackgroundTaskStatus.RUNNING
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isRunning) Color(0xEE2B084D) else Color(0xCC240B38),
            border = BorderStroke(1.dp, if (isRunning) Color(0xFFFF80D5) else Color(0x66FFFFFF)),
            modifier = Modifier.testTag("background_task_chip_${task.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              if (isRunning) {
                CircularProgressIndicator(
                  modifier = Modifier.size(14.dp),
                  color = Color(0xFFFF80D5),
                  strokeWidth = 2.dp,
                )
              }
              Column {
                Text(
                  text = "${task.botName} • ${task.status.label} (${task.elapsedSec}s)",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                )
                Text(
                  text = task.prompt,
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFFE4C2FA),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.widthIn(max = 170.dp),
                )
              }
              if (isRunning) {
                IconButton(
                  onClick = { onAbortBackgroundTask(task.id) },
                  modifier = Modifier
                    .size(28.dp)
                    .testTag("abort_bg_task_${task.id}"),
                ) {
                  Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Cancel sub-agent",
                    tint = Color(0xFFFF8A80),
                    modifier = Modifier.size(16.dp),
                  )
                }
              }
            }
          }
        }
      }
    }

    // Sub-Agent toggle row above composer
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val subAgentActive = spawnInBackground || isBusy
      Surface(
        onClick = { spawnInBackground = !spawnInBackground },
        shape = RoundedCornerShape(50),
        color = if (subAgentActive) Color(0xFF4A008F) else Color(0x772E064E),
        border = BorderStroke(
          width = 1.2.dp,
          color = if (subAgentActive) Color(0xFFFF85D4) else Color(0x88FFFFFF),
        ),
        modifier = Modifier
          .defaultMinSize(minHeight = 36.dp)
          .testTag("spawn_background_toggle"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFB2EB),
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = when {
              isBusy -> "Primary lane active • Sending spawns Sub-Agent"
              spawnInBackground -> "Sub-Agent Mode ON (isolated lane)"
              else -> "Spawn Sub-Agent"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = if (subAgentActive) FontWeight.Bold else FontWeight.Medium,
          )
        }
      }
    }

    // 5. Casual Message Composer
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color(0xFF7B00FF),
      tonalElevation = 4.dp,
      border = BorderStroke(2.dp, Color(0xFFFF9CE6)),
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
      ) {
        IconButton(
          onClick = { showAttachSheet = true },
          modifier = Modifier
            .size(48.dp)
            .testTag("attach_menu_button"),
        ) {
          Icon(
            Icons.Rounded.AttachFile,
            contentDescription = "Attach or link files",
            tint = Color.White,
          )
        }

        OutlinedTextField(
          value = composerText,
          onValueChange = { composerText = it },
          placeholder = {
            Text(
              text = if (spawnInBackground || isBusy) {
                "Spawn isolated sub-agent for ${activeBot?.name ?: "bot"}..."
              } else {
                "Task ${activeBot?.name ?: "bot"} (${selectedWheelModel.wheelLabel})..."
              },
              style = MaterialTheme.typography.bodyMedium,
              color = Color(0xFFE6C8FF),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF5D00C9),
            unfocusedContainerColor = Color(0xFF5D00C9),
            focusedBorderColor = Color(0xFFFF9CE6),
            unfocusedBorderColor = Color(0x99E2A4FF),
            cursorColor = Color(0xFFFF9CE6),
          ),
          maxLines = 4,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("casual_chat_input"),
        )

        Spacer(Modifier.width(6.dp))

        com.example.ui.components.VoiceInputButton(
          onPartial = { composerText = it },
          onResult = { composerText = it },
          onError = { /* soft-fail; user can still type */ },
        )

        Spacer(Modifier.width(2.dp))

        val canSend = activeBot != null && (composerText.isNotBlank() || pendingAttachments.isNotEmpty())
        FilledIconButton(
          onClick = {
            if (activeBot != null && (composerText.isNotBlank() || pendingAttachments.isNotEmpty())) {
              val textToSend = composerText
              composerText = ""
              onChatDraftChanged(activeBot.id, "")
              if (spawnInBackground || isBusy) {
                onSpawnBackgroundTask(textToSend, activeBot, activeHost, selectedWheelModel)
                spawnInBackground = false
              } else {
                onSendMessage(textToSend, activeBot, activeHost, selectedWheelModel)
              }
            }
          },
          enabled = canSend,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (spawnInBackground || isBusy) Color(0xFF9C27B0) else Color(0xFFFF2E93),
            contentColor = Color.White,
            disabledContainerColor = Color(0x66FF2E93),
            disabledContentColor = Color(0xAAFFFFFF),
          ),
          modifier = Modifier
            .size(48.dp)
            .testTag("send_chat_button"),
        ) {
          Icon(
            if (spawnInBackground || isBusy) Icons.Rounded.AutoAwesome else Icons.AutoMirrored.Rounded.Send,
            contentDescription = if (spawnInBackground || isBusy) "Spawn background sub-agent" else "Send to bot",
          )
        }
      }
    }
  }
  }

  if (showAttachSheet) {
    AlertDialog(
      onDismissRequest = { showAttachSheet = false },
      title = { Text("Attach or Link Files") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val attachBtnPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
          OutlinedButton(
            onClick = {
              showAttachSheet = false
              documentLauncher.launch(arrayOf("*/*"))
            },
            contentPadding = attachBtnPadding,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("pick_device_file_button"),
          ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Attach Device File")
          }
          OutlinedButton(
            onClick = {
              showAttachSheet = false
              photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            contentPadding = attachBtnPadding,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("pick_photo_button"),
          ) {
            Icon(Icons.Rounded.Image, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Attach Screenshot / Photo")
          }
          OutlinedButton(
            onClick = {
              showAttachSheet = false
              showLinkDialog = true
            },
            contentPadding = attachBtnPadding,
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("link_host_path_button"),
          ) {
            Icon(Icons.Rounded.Link, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Link Host PC Path or URL")
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

  if (showLinkDialog) {
    var pathLabel by remember { mutableStateOf("") }
    var pathValue by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showLinkDialog = false },
      title = { Text("Link Host File or URL") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = pathLabel,
            onValueChange = { pathLabel = it },
            label = { Text("Friendly Name") },
            placeholder = { Text("e.g., Invoices Folder") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = pathValue,
            onValueChange = { pathValue = it },
            label = { Text("Host Path or URL") },
            placeholder = { Text("e.g., C:\\Users\\Me\\Downloads or https://...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (pathValue.isNotBlank()) {
              onAddHostPathLink(
                pathLabel.ifBlank { pathValue.substringAfterLast('/').substringAfterLast('\\').ifBlank { "Linked File" } },
                pathValue.trim(),
              )
              showLinkDialog = false
            }
          },
        ) {
          Text("Link")
        }
      },
      dismissButton = {
        TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") }
      },
    )
  }

  botPendingDelete?.let { target ->
    AlertDialog(
      onDismissRequest = { botPendingDelete = null },
      title = { Text("Remove ${target.name}?") },
      text = { Text("This will remove ${target.name} and its saved message thread from your crew.") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            onDeleteBotClick(target.id)
            botPendingDelete = null
          },
        ) {
          Text("Remove Bot")
        }
      },
      dismissButton = {
        TextButton(onClick = { botPendingDelete = null }) { Text("Cancel") }
      },
    )
  }

  botPendingRename?.let { target ->
    var draftName by remember(target.id) { mutableStateOf(target.name) }
    AlertDialog(
      onDismissRequest = { botPendingRename = null },
      title = { Text("Rename Character") },
      text = {
        OutlinedTextField(
          value = draftName,
          onValueChange = { draftName = it.take(40) },
          label = { Text("Character Name") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("rename_bot_dialog_input"),
        )
      },
      confirmButton = {
        Button(
          enabled = draftName.trim().isNotEmpty(),
          onClick = {
            onRenameBot(target, draftName)
            botPendingRename = null
          },
          modifier = Modifier.testTag("confirm_rename_bot_button"),
        ) {
          Text("Save Name")
        }
      },
      dismissButton = {
        TextButton(onClick = { botPendingRename = null }) { Text("Cancel") }
      },
    )
  }

  botPendingMissionEdit?.let { target ->
    var draftMission by remember(target.id) { mutableStateOf(target.displayMission) }
    AlertDialog(
      onDismissRequest = { botPendingMissionEdit = null },
      title = { Text("${target.name}'s Mission") },
      text = {
        OutlinedTextField(
          value = draftMission,
          onValueChange = { draftMission = it.take(160) },
          label = { Text("Mission") },
          placeholder = { Text("e.g., I help translate documents!") },
          minLines = 2,
          maxLines = 3,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_edit_mission_input"),
        )
      },
      confirmButton = {
        Button(
          enabled = draftMission.trim().isNotEmpty(),
          onClick = {
            onUpdateMission(target, draftMission)
            botPendingMissionEdit = null
          },
          modifier = Modifier.testTag("confirm_edit_mission_button"),
        ) {
          Text("Save Mission")
        }
      },
      dismissButton = {
        TextButton(onClick = { botPendingMissionEdit = null }) { Text("Cancel") }
      },
    )
  }

  if (showBotDetailsDialog && activeBot != null) {
    AlertDialog(
      onDismissRequest = { showBotDetailsDialog = false },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          BotVectorAvatar(
            bot = activeBot,
            expression = liveExpression,
            size = 74.dp,
            animate = true,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = activeBot.name,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${activeBot.functionPresetEnum.displayName} • ${activeBot.roleTitle}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
      },
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
            modifier = Modifier.fillMaxWidth(),
          )

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Mission",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Text(
                text = activeBot.displayMission,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "OpenClaw Connection",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "Host: ${activeHost?.label ?: "Not connected"}",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Agent ID: ${activeBot.openClawAgentId} • LLM: ${activeBot.llmProviderEnum.displayName}",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Session: ${activeBot.effectivePrimarySessionKey}",
                style = MaterialTheme.typography.bodySmall,
              )
              Text(
                text = "Tools: ${activeBot.effectiveToolPermissions.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
              )
              if (activeBot.workspacePath.isNotBlank()) {
                Text(
                  text = "Workspace: ${activeBot.workspacePath}",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = {
                showBotDetailsDialog = false
                botPendingRename = activeBot
              },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("quick_rename_bot_button"),
            ) {
              Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Rename", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
              onClick = {
                showBotDetailsDialog = false
                botPendingMissionEdit = activeBot
              },
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("edit_mission_button"),
            ) {
              Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Mission", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
          }

          Button(
            onClick = {
              showBotDetailsDialog = false
              onEditBotClick(activeBot)
            },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp)
              .testTag("edit_bot_appearance_button"),
          ) {
            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Edit Appearance & OpenClaw Brain")
          }
          OutlinedButton(
            onClick = {
              showBotDetailsDialog = false
              onOpenHostSettings()
            },
            modifier = Modifier
              .fillMaxWidth()
              .defaultMinSize(minHeight = 48.dp),
          ) {
            Text("Open OpenClaw & Host Settings")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showBotDetailsDialog = false }) {
          Text("Done")
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showBotDetailsDialog = false
            onOpenTakeover()
          },
        ) {
          Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Take Over PC")
        }
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
        modelUsed = targetMsg.mediaModel.orEmpty(),
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
private fun BotCrewBar(
  bots: List<CompanionBot>,
  activeBotId: String,
  busyBotIds: Set<String> = emptySet(),
  minusMode: Boolean,
  onToggleMinusMode: () -> Unit,
  onSelectBot: (String) -> Unit,
  onAddBot: () -> Unit,
  onRequestDeleteBot: (CompanionBot) -> Unit,
) {
  LazyRow(
    contentPadding = PaddingValues(vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items(bots, key = { it.id }) { bot ->
      val selected = bot.id == activeBotId
      val botBusy = bot.id in busyBotIds
      Surface(
        onClick = {
          if (minusMode) onRequestDeleteBot(bot) else onSelectBot(bot.id)
        },
        shape = RoundedCornerShape(18.dp),
        color = when {
          minusMode -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
          selected -> MaterialTheme.colorScheme.primaryContainer
          else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
          width = if (selected || botBusy) 2.dp else 1.dp,
          color = when {
            minusMode -> MaterialTheme.colorScheme.error
            selected -> MaterialTheme.colorScheme.primary
            botBusy -> Color(0xFFFF2E93)
            else -> MaterialTheme.colorScheme.outlineVariant
          },
        ),
        modifier = Modifier.testTag("crew_bot_chip_${bot.name}"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          BotVectorAvatar(
            bot = bot,
            expression = if (botBusy) BotExpression.WORKING else BotExpression.HAPPY,
            size = 34.dp,
            animate = false,
          )
          Text(
            text = bot.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
          )
          if (botBusy && !minusMode) {
            CircularProgressIndicator(
              modifier = Modifier.size(14.dp),
              color = Color(0xFFFF2E93),
              strokeWidth = 2.dp,
            )
          }
          if (minusMode) {
            Icon(
              Icons.Rounded.DeleteOutline,
              contentDescription = "Remove ${bot.name}",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    item {
      FilledIconButton(
        onClick = onAddBot,
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier
          .size(48.dp)
          .testTag("add_bot_button"),
      ) {
        Icon(Icons.Rounded.Add, contentDescription = "Add new bot")
      }
    }

    item {
      FilledIconButton(
        onClick = onToggleMinusMode,
        colors = IconButtonDefaults.filledIconButtonColors(
          containerColor = if (minusMode) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.surfaceVariant
          },
          contentColor = if (minusMode) {
            MaterialTheme.colorScheme.onError
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        ),
        modifier = Modifier
          .size(48.dp)
          .testTag("minus_bot_button"),
      ) {
        Icon(Icons.Rounded.Remove, contentDescription = "Toggle remove bot mode")
      }
    }
  }
}

@Composable
private fun expressionHaloColor(expression: BotExpression): Color {
  val target = when (expression) {
    BotExpression.THINKING -> Color(0xFFFF2E93)
    BotExpression.WORKING -> Color(0xFFFF9800)
    BotExpression.UH_HUH -> Color(0xFF2E9D5B)
    BotExpression.WORRIED -> Color(0xFF56B4F7)
    BotExpression.SORRY, BotExpression.ALERT -> Color(0xFFE53935)
    BotExpression.HAPPY -> Color(0xFFCFA6FF)
  }
  val animated by animateColorAsState(targetValue = target, label = "expr_halo")
  return animated
}

@Composable
private fun InteractiveChatHeadStage(
  bot: CompanionBot,
  expression: BotExpression,
  isBusy: Boolean,
  headSize: androidx.compose.ui.unit.Dp,
  onTapHead: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val haloColor = expressionHaloColor(expression)

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .size(headSize + 14.dp)
      .clip(CircleShape)
      .background(
        Brush.radialGradient(
          colors = listOf(
            Color.White.copy(alpha = if (isBusy || expression != BotExpression.HAPPY) 0.34f else 0.24f),
            haloColor.copy(alpha = 0.22f),
            Color.Transparent,
          )
        )
      )
      .clickable {
        ReplyHaptics.triggerSoftTick(context, haptic)
        onTapHead()
      }
      .testTag("live_bot_avatar"),
  ) {
    BotVectorAvatar(
      bot = bot,
      expression = expression,
      size = headSize,
      animate = true,
    )
  }
}

@Composable
private fun LeftHandChatHeadDock(
  bot: CompanionBot,
  expression: BotExpression,
  isBusy: Boolean,
  onOpenBotDetails: () -> Unit,
  onOpenTakeover: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.padding(vertical = 8.dp, horizontal = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    InteractiveChatHeadStage(
      bot = bot,
      expression = expression,
      isBusy = isBusy,
      headSize = 176.dp,
      onTapHead = onOpenBotDetails,
    )

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .defaultMinSize(minHeight = 48.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onOpenBotDetails)
        .padding(horizontal = 10.dp, vertical = 4.dp)
        .testTag("bot_name_header_button"),
    ) {
      Text(
        text = bot.name,
        style = MaterialTheme.typography.headlineSmall.copy(
          shadow = Shadow(
            color = Color(0x66220018),
            offset = Offset(0f, 3f),
            blurRadius = 6f,
          )
        ),
        color = Color.White,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Button(
      onClick = onOpenTakeover,
      colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xEBFFF8FC),
        contentColor = Color(0xFF280842),
      ),
      shape = RoundedCornerShape(50),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
      modifier = Modifier
        .height(48.dp)
        .testTag("stage_takeover_button"),
    ) {
      Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text("Take Over PC", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
  }
}

@Composable
private fun LiveBotStageCard(
  bot: CompanionBot,
  expression: BotExpression,
  isBusy: Boolean,
  onOpenBotDetails: () -> Unit,
  onOpenTakeover: () -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
  ) {
    // Borderless, extra-large animating Chat Head directly on the Neon Pink canvas
    InteractiveChatHeadStage(
      bot = bot,
      expression = expression,
      isBusy = isBusy,
      headSize = 160.dp,
      onTapHead = onOpenBotDetails,
    )

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalAlignment = Alignment.Start,
    ) {
      Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .clip(RoundedCornerShape(14.dp))
          .clickable(onClick = onOpenBotDetails)
          .padding(horizontal = 6.dp, vertical = 4.dp)
          .testTag("bot_name_header_button"),
      ) {
        Text(
          text = bot.name,
          style = MaterialTheme.typography.headlineSmall.copy(
            shadow = Shadow(
              color = Color(0x66220018),
              offset = Offset(0f, 3f),
              blurRadius = 6f,
            )
          ),
          color = Color.White,
          fontWeight = FontWeight.ExtraBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Button(
        onClick = onOpenTakeover,
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xEBFFF8FC),
          contentColor = Color(0xFF280842),
        ),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier
          .height(48.dp)
          .testTag("stage_takeover_button"),
      ) {
        Icon(
          Icons.Rounded.Computer,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
          text = "Take Over PC",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun MessageBubbleItem(
  message: BotMessage,
  bot: CompanionBot?,
  activeHost: OpenClawHost?,
  isLiveStreaming: Boolean = false,
  liveElapsedSec: Int = 0,
  streamingPreviewText: String? = null,
  onAbortRequest: () -> Unit = {},
  onCopyInstruction: (String) -> Unit,
  onRetry: () -> Unit,
  onOpenTakeover: () -> Unit,
  onAnimateImage: (String) -> Unit,
  onSaveMedia: (String, GeneratedMediaType) -> Unit,
  onCopyMediaPrompt: (String) -> Unit,
  onOpenLightbox: () -> Unit,
) {
  val isUser = message.senderEnum == MessageSender.USER
  var expanded by remember(message.id) { mutableStateOf(true) }
  val isFailedOrWarning = message.statusEnum == MessageStatus.WARNING || message.statusEnum == MessageStatus.FAILED
  val haloColor = expressionHaloColor(message.expressionEnum)

  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
  ) {
    // Head sits above the bubble, large and left-aligned with it, so the bot reads as the
    // author of the text rather than a small badge beside it.
    if (!isUser && bot != null) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .padding(bottom = 4.dp)
          .size(124.dp)
          .clip(CircleShape)
          .background(
            Brush.radialGradient(
              colors = listOf(
                Color.White.copy(alpha = 0.34f),
                haloColor.copy(alpha = 0.2f),
                Color.Transparent,
              )
            )
          )
          .testTag("message_bot_chat_head_${message.id}"),
      ) {
        BotVectorAvatar(
          bot = bot,
          expression = message.expressionEnum,
          size = 116.dp,
          animate = true,
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
      color = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surface
      },
      tonalElevation = if (isUser) 0.dp else 2.dp,
      border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.widthIn(max = 520.dp),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (!isUser && bot != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = bot.name,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Surface(
              shape = RoundedCornerShape(50),
              color = if (isFailedOrWarning) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
              } else {
                MaterialTheme.colorScheme.surfaceVariant
              },
            ) {
              Text(
                text = message.statusEnum.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isFailedOrWarning) {
                  MaterialTheme.colorScheme.error
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
        }

        val cleanedText = remember(message.rawText) { sanitizeBotDisplayText(message.rawText) }
        if (isLiveStreaming) {
          val liveText = streamingPreviewText?.takeIf { it.isNotBlank() } ?: cleanedText
          Text(
            text = liveText,
            style = MaterialTheme.typography.bodyMedium,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Thinking / running tools on host... (${liveElapsedSec}s)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = onAbortRequest,
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
              colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.testTag("bot_abort_request_button"),
            ) {
              Text("Abort", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
          }
        } else {
          Text(
            text = cleanedText,
            style = MaterialTheme.typography.bodyMedium,
          )
        }

        val mediaUrl = message.mediaUrl
        if (!mediaUrl.isNullOrBlank()) {
          InlineMediaCard(
            mediaUrl = mediaUrl,
            mediaType = message.mediaTypeEnum,
            prompt = message.mediaPrompt ?: message.rawText,
            modelUsed = message.mediaModel.orEmpty(),
            durationSec = message.mediaDurationSec,
            activeHost = activeHost,
            onAnimateThis = if (message.mediaTypeEnum == GeneratedMediaType.IMAGE) onAnimateImage else null,
            onSaveToPhone = { onSaveMedia(mediaUrl, message.mediaTypeEnum) },
            onCopyPrompt = {
              onCopyMediaPrompt(message.mediaPrompt?.takeIf { it.isNotBlank() } ?: message.rawText)
            },
            onOpenLightbox = onOpenLightbox,
          )
        }

        message.createdAt?.toDate()?.let { sentAt ->
          Text(
            text = remember(sentAt) {
              java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(sentAt)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
              .fillMaxWidth()
              .wrapContentWidth(if (isUser) Alignment.End else Alignment.Start),
          )
        }

        if (!isUser && isFailedOrWarning && message.parsedInstruction.isNullOrBlank()) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
              onClick = onRetry,
              modifier = Modifier.testTag("retry_parse_button_${message.id}"),
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Retry")
            }
          }
        }

        if (message.attachedFiles.isNotEmpty() || message.linkedPaths.isNotEmpty()) {
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            message.attachedFiles.forEach { f ->
              AssistChip(
                onClick = {},
                label = { Text(f, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.AttachFile, contentDescription = null, Modifier.size(14.dp)) },
              )
            }
            message.linkedPaths.forEach { p ->
              AssistChip(
                onClick = {},
                label = { Text(p, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, Modifier.size(14.dp)) },
              )
            }
          }
        }

        if (!message.parsedInstruction.isNullOrBlank()) {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { expanded = !expanded },
              ) {
                Icon(
                  Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                  text = message.instructionGoal ?: "Parsed OpenClaw Instruction",
                  style = MaterialTheme.typography.titleSmall,
                  modifier = Modifier.weight(1f),
                )
                Icon(
                  if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                  contentDescription = "Toggle instruction details",
                )
              }

              AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = message.parsedInstruction,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  message.instructionSteps.forEachIndexed { idx, step ->
                    Text(
                      text = "${idx + 1}. $step",
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Medium,
                    )
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onCopyInstruction(message.parsedInstruction) }) {
                      Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(16.dp))
                      Spacer(Modifier.width(4.dp))
                      Text("Copy")
                    }
                    if (isFailedOrWarning) {
                      TextButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry Host")
                      }
                    }
                    if (message.takeoverSuggested) {
                      TextButton(onClick = onOpenTakeover) {
                        Icon(Icons.Rounded.Computer, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Take Over PC")
                      }
                    }
                  }
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
private fun EmptyBotCrewCard(onAddBotClick: () -> Unit) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier
        .padding(20.dp)
        .fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text("No bots in your crew yet!", style = MaterialTheme.typography.titleMedium)
      Button(onClick = onAddBotClick) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Create First Bot")
      }
    }
  }
}

@Composable
private fun EmptyChatPrompt(
  botName: String,
  onSampleClick: (String) -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = "Say anything casually to $botName!",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = Color.White,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = "$botName translates casual requests into clear step-by-step instructions for OpenClaw.",
      style = MaterialTheme.typography.bodySmall,
      color = Color(0xFFFFE5F5),
    )
    Spacer(Modifier.height(14.dp))
    listOf(
      "Hey buddy, check my Downloads folder and tidy up last week's PDFs into Invoices",
      "Can you open my project repo on the PC, run the build, and tell me if anything broke?",
      "Help me log into my dashboard on the host PC—I'll take over if there's 2FA",
    ).forEach { sample ->
      Surface(
        onClick = { onSampleClick(sample) },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
      ) {
        Text(
          text = "\"$sample\"",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.padding(12.dp),
        )
      }
    }
  }
}

private data class ResolvedFileMeta(
  val name: String,
  val mime: String,
  val size: Long,
)

private fun resolveUriMetadata(context: Context, uri: Uri): ResolvedFileMeta {
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
    // Fallback to URI segment
  }
  return ResolvedFileMeta(name = name, mime = mime, size = size.coerceAtLeast(0L))
}

private val BOT_INLINE_DATA_IMAGE_REGEX = Regex(
  """!?\[[^\]]*\]\(data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\r\n]+\)|data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\r\n]+"""
)

/** Returns a day label when [current] starts a new calendar day vs [previous], else null. */
private fun daySeparatorLabel(previous: Timestamp?, current: Timestamp?): String? {
  val curDate = current?.toDate() ?: return null
  val curCal = Calendar.getInstance().apply { time = curDate }
  if (previous != null) {
    val prevCal = Calendar.getInstance().apply { time = previous.toDate() }
    val sameDay = curCal.get(Calendar.YEAR) == prevCal.get(Calendar.YEAR) &&
      curCal.get(Calendar.DAY_OF_YEAR) == prevCal.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return null
  }
  val now = Calendar.getInstance()
  val sameYear = now.get(Calendar.YEAR) == curCal.get(Calendar.YEAR)
  val dayDelta = if (sameYear) now.get(Calendar.DAY_OF_YEAR) - curCal.get(Calendar.DAY_OF_YEAR) else -1
  return when {
    dayDelta == 0 -> "Today"
    dayDelta == 1 -> "Yesterday"
    dayDelta in 2..6 -> java.text.SimpleDateFormat("EEEE", Locale.getDefault()).format(curDate)
    else -> java.text.SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(curDate)
  }
}

@Composable
private fun DaySeparator(label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
      shape = RoundedCornerShape(50),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
      )
    }
  }
}

private fun sanitizeBotDisplayText(raw: String): String {
  if (!raw.contains("data:image/", ignoreCase = true)) return raw
  val cleaned = BOT_INLINE_DATA_IMAGE_REGEX.replace(raw, "").trim()
  return cleaned.ifBlank { "Image attached below." }
}

private fun isBotImageAttachment(file: LinkedFile): Boolean {
  val mime = file.mimeOrExt.lowercase(Locale.US)
  val name = file.displayName.lowercase(Locale.US)
  return mime.startsWith("image/") ||
    name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
    name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".heic")
}
