package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GeneralChatMessage
import com.example.model.GeneralChatThread
import com.example.model.MessageSender
import com.example.model.MessageStatus
import com.example.model.OpenClawHost
import com.example.model.WheelLlmOption
import com.example.ui.components.LlmRotaryWheelSelector
import com.example.ui.components.SectionSettingsIconButton
import com.example.viewmodel.UiState
import java.util.Locale

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
  selectedWheelModel: WheelLlmOption,
  isBusy: Boolean,
  onSelectChat: (String) -> Unit,
  onCreateNewChat: (WheelLlmOption) -> Unit,
  onTogglePinChat: (String, Boolean) -> Unit,
  onDeleteChat: (String) -> Unit,
  onSelectWheelModel: (WheelLlmOption, String?) -> Unit,
  onSendMessage: (GeneralChatThread?, String, WheelLlmOption, OpenClawHost?) -> Unit,
  onOpenHostSettings: () -> Unit,
) {
  val threads = (generalChatsState as? UiState.Success)?.data.orEmpty()
  val messages = (messagesState as? UiState.Success)?.data.orEmpty()
  val activeHost = (hostsState as? UiState.Success)?.data?.firstOrNull()
  val activeThread = threads.firstOrNull { it.id == selectedChatId }

  var phoneShowingThread by remember { mutableStateOf(false) }
  var filter by remember { mutableStateOf(GeneralFilter.ALL) }
  var searchQuery by remember { mutableStateOf("") }
  var composerText by remember { mutableStateOf("") }
  var showSettingsDialog by remember { mutableStateOf(false) }

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
            selectedWheelModel = selectedWheelModel,
            composerText = composerText,
            isBusy = isBusy,
            showBackArrow = false,
            onBackToList = {},
            onTogglePin = {
              if (activeThread != null) onTogglePinChat(activeThread.id, !activeThread.pinned)
            },
            onOpenSettings = { showSettingsDialog = true },
            onSelectWheelModel = { option -> onSelectWheelModel(option, activeThread?.id) },
            onComposerChange = { composerText = it },
            onSend = {
              val prompt = composerText
              composerText = ""
              onSendMessage(activeThread, prompt, selectedWheelModel, activeHost)
            },
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
          selectedWheelModel = selectedWheelModel,
          composerText = composerText,
          isBusy = isBusy,
          showBackArrow = true,
          onBackToList = { phoneShowingThread = false },
          onTogglePin = {
            if (activeThread != null) onTogglePinChat(activeThread.id, !activeThread.pinned)
          },
          onOpenSettings = { showSettingsDialog = true },
          onSelectWheelModel = { option -> onSelectWheelModel(option, activeThread?.id) },
          onComposerChange = { composerText = it },
          onSend = {
            val prompt = composerText
            composerText = ""
            onSendMessage(activeThread, prompt, selectedWheelModel, activeHost)
          },
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Active Model Wheel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
          WheelLlmOption.entries.forEach { option ->
            val picked = option == selectedWheelModel
            Surface(
              onClick = { onSelectWheelModel(option, activeThread?.id) },
              shape = RoundedCornerShape(12.dp),
              color = if (picked) Color(0xFFFF6D00).copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
              border = BorderStroke(1.dp, if (picked) Color(0xFFFF6D00) else Color.Transparent),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = if (picked) "✓ ${option.fullLabel}" else option.fullLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              )
            }
          }

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
            text = activeHost?.let { "Gateway: ${it.label} (${it.baseUrl})" } ?: "No OpenClaw Host PC configured yet",
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
  selectedWheelModel: WheelLlmOption,
  composerText: String,
  isBusy: Boolean,
  showBackArrow: Boolean,
  onBackToList: () -> Unit,
  onTogglePin: () -> Unit,
  onOpenSettings: () -> Unit,
  onSelectWheelModel: (WheelLlmOption) -> Unit,
  onComposerChange: (String) -> Unit,
  onSend: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  // Vibrant Neon Orange backdrop with toga cartoon glowing gently underneath
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            Color(0xFFFF6D00),
            Color(0xFFFF8F00),
            Color(0xFFFF3D00),
          )
        )
      ),
  ) {
    Image(
      painter = painterResource(id = R.drawable.img_greek_forum_togas),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .fillMaxSize()
        .alpha(0.30f),
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
    ) {
      // Slim, clean top bar with small corner Settings icon
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
          modifier = Modifier.weight(1f),
        )

        if (activeThread != null) {
          IconButton(
            onClick = onTogglePin,
            modifier = Modifier.size(48.dp),
          ) {
            Icon(
              imageVector = if (activeThread.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
              contentDescription = if (activeThread.pinned) "Unpin chat" else "Pin chat",
              tint = if (activeThread.pinned) Color(0xFFFFEB3B) else Color.White,
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

      // Rotating LLM Wheel
      LlmRotaryWheelSelector(
        selectedOption = selectedWheelModel,
        onSelectOption = onSelectWheelModel,
        accentColor = Color(0xFFFF5500),
        modifier = Modifier.padding(bottom = 6.dp),
      )

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
              LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
              ) {
                items(messages, key = { it.id }) { msg ->
                  val isUser = msg.senderEnum == MessageSender.USER
                  val isErr = msg.statusEnum == MessageStatus.FAILED || msg.statusEnum == MessageStatus.WARNING
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                  ) {
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
                        color = if (isUser) Color(0xAAFF9CE6) else Color(0x55FF6D00),
                      ),
                      modifier = Modifier.widthIn(max = 560.dp),
                    ) {
                      Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                      ) {
                        if (!isUser && msg.modelLabel.isNotBlank()) {
                          Text(
                            text = msg.modelLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isErr) MaterialTheme.colorScheme.error else Color(0xFFE65100),
                          )
                        }
                        Text(
                          text = msg.rawText,
                          style = MaterialTheme.typography.bodyMedium,
                          color = if (isUser) Color.White else Color(0xFF2D0F00),
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
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.Bottom,
        ) {
          OutlinedTextField(
            value = composerText,
            onValueChange = onComposerChange,
            placeholder = {
              Text(
                text = "Ask ${selectedWheelModel.wheelLabel} a question...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8C4A27),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedTextColor = Color(0xFF2E0D00),
              unfocusedTextColor = Color(0xFF2E0D00),
              focusedBorderColor = Color(0xFFFF6D00),
              unfocusedBorderColor = Color(0x66FF6D00),
            ),
            maxLines = 4,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("general_chat_input"),
          )
          Spacer(Modifier.width(8.dp))
          FilledIconButton(
            onClick = onSend,
            enabled = composerText.isNotBlank() && !isBusy,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color(0xFFFF5500),
              contentColor = Color.White,
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
