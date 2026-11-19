package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BotExpression
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.MessageSender
import com.example.model.OpenClawHost
import com.example.model.RoomMessage
import com.example.model.WheelLlmOption
import com.example.ui.avatar.BotVectorAvatar
import com.example.ui.components.LlmRotaryWheelSelector
import com.example.ui.util.ReplyHaptics
import com.example.viewmodel.UiState

@Composable
fun ChatRoomScreen(
  room: ChatRoom?,
  allBots: List<CompanionBot>,
  roomMessagesState: UiState<List<RoomMessage>>,
  hostsState: UiState<List<OpenClawHost>>,
  activeExpressions: Map<String, BotExpression>,
  selectedWheelModel: WheelLlmOption,
  isBusy: Boolean,
  onBack: () -> Unit,
  onSelectWheelModel: (WheelLlmOption) -> Unit,
  onSendRoomMessage: (ChatRoom, List<CompanionBot>, List<RoomMessage>, String, Boolean, WheelLlmOption) -> Unit,
  onUpdateRoom: (ChatRoom) -> Unit,
  onUpdateBotMission: (CompanionBot, String) -> Unit,
  onDispatchToOpenClaw: (CompanionBot, OpenClawHost?, String) -> Unit,
) {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val participants = remember(room, allBots) {
    if (room == null) emptyList()
    else {
      val byId = allBots.associateBy { it.id }
      room.botIds.mapNotNull { byId[it] }.ifEmpty { allBots.take(2) }
    }
  }
  val messages = (roomMessagesState as? UiState.Success)?.data.orEmpty()
  val activeHost = (hostsState as? UiState.Success)?.data?.firstOrNull()

  var composerText by remember { mutableStateOf("") }
  var showRoomSettingsDialog by remember { mutableStateOf(false) }
  var missionEditingBot by remember { mutableStateOf<CompanionBot?>(null) }

  val roomKey = room?.id.orEmpty()
  var roomHapticsReady by remember(roomKey) { mutableStateOf(false) }
  var seenBotMsgIds by remember(roomKey) { mutableStateOf<Set<String>>(emptySet()) }
  LaunchedEffect(roomKey, roomMessagesState) {
    val loaded = (roomMessagesState as? UiState.Success)?.data ?: return@LaunchedEffect
    val botIds = loaded.filter { it.senderTypeEnum == MessageSender.BOT }.map { it.id }.toSet()
    if (!roomHapticsReady) {
      seenBotMsgIds = botIds
      roomHapticsReady = true
      return@LaunchedEffect
    }
    if ((botIds - seenBotMsgIds).isNotEmpty()) {
      ReplyHaptics.triggerReplyPulse(context, haptic)
    }
    seenBotMsgIds = botIds
  }

  LaunchedEffect(isBusy) {
    if (isBusy) {
      ReplyHaptics.triggerSoftTick(context, haptic)
    }
  }

  val listState = rememberLazyListState()
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // Full-bleed sunlit Greek Forum characters in togas chat background — crystal clear!
    Image(
      painter = painterResource(id = R.drawable.img_greek_forum_togas),
      contentDescription = "Greek Forum with mascot characters wearing togas",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxSize(),
    )
    // Gentle top-only gradient behind header text; middle & bottom stay bright so toga pals shine through
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            0.0f to Color(0x881F0533),
            0.22f to Color.Transparent,
            0.82f to Color.Transparent,
            1.0f to Color(0x441B0426),
          )
        )
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
    ) {
      // 1. Top Room Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledIconButton(
          onClick = onBack,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0xFAFFF8FC),
            contentColor = Color(0xFF280842),
          ),
          modifier = Modifier
            .size(48.dp)
            .testTag("room_back_button"),
        ) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Bot Dudes")
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = room?.title ?: "Greek Forum Room",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = room?.topic?.ifBlank { "${participants.size} bots debating in the forum" } ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFF3C4),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        FilledIconButton(
          onClick = { showRoomSettingsDialog = true },
          enabled = room != null,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0xFAFFF8FC),
            contentColor = Color(0xFF280842),
          ),
          modifier = Modifier
            .size(48.dp)
            .testTag("room_settings_button"),
        ) {
          Icon(Icons.Rounded.Settings, contentDescription = "Edit room crew & topic")
        }
      }

      // 2. Airy translucent Forum Participant Strip letting the Greek Forum Togas art shine behind
      Surface(
        shape = MaterialTheme.shapes.large,
        color = Color(0x281D0430),
        border = BorderStroke(1.8.dp, Color(0xAAFF9CE0)),
        modifier = Modifier
          .fillMaxWidth()
          .height(166.dp)
          .testTag("cafe_stage_card"),
      ) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.Bottom,
          horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
          participants.forEach { bot ->
            val expr = activeExpressions[bot.id] ?: BotExpression.HAPPY
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable { missionEditingBot = bot }
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("cafe_bot_${bot.name}"),
            ) {
              Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xEE2B0B42),
                border = BorderStroke(1.dp, Color(0xFFFF79CE)),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                  Text(
                    text = bot.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                  )
                  Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Edit ${bot.name}'s mission",
                    tint = Color(0xFFFF9CE0),
                    modifier = Modifier.size(11.dp),
                  )
                }
              }
              Spacer(Modifier.height(2.dp))
              BotVectorAvatar(
                bot = bot,
                expression = expr,
                size = 92.dp,
                animate = true,
              )
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xEEFFFBF4),
              ) {
                Text(
                  text = bot.displayMission,
                  style = MaterialTheme.typography.labelSmall,
                  color = Color(0xFF261913),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier
                    .widthIn(max = 112.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
        }
      }

    Spacer(Modifier.height(8.dp))

    // 3. Multi-Bot Dialogue Feed
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
    ) {
      when (roomMessagesState) {
        is UiState.Loading -> {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        is UiState.Error -> {
          Surface(
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = roomMessagesState.message,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
        is UiState.Success -> {
          if (messages.isEmpty()) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Text(
                text = "Start a cafe conversation!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
              Spacer(Modifier.height(6.dp))
              listOf(
                "Translate 'Welcome to our cozy automation cafe!' into Japanese and critique the tone.",
                "Brainstorm a quick folder cleanup script and check for edge cases.",
              ).forEach { sample ->
                Surface(
                  onClick = { composerText = sample },
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
          } else {
            val botsById = remember(allBots) { allBots.associateBy { it.id } }
            val botsByName = remember(allBots) { allBots.associateBy { it.name.lowercase() } }
            LazyColumn(
              state = listState,
              contentPadding = PaddingValues(vertical = 6.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(messages, key = { it.id }) { msg ->
                val isUser = msg.senderTypeEnum == MessageSender.USER
                val speakerBot = msg.senderBotId?.let { botsById[it] }
                  ?: botsByName[msg.senderBotName.lowercase()]
                RoomMessageBubble(
                  message = msg,
                  speakerBot = speakerBot,
                  isUser = isUser,
                  onRunOnOpenClaw = { instruction ->
                    val targetBot = speakerBot ?: participants.firstOrNull()
                    if (targetBot != null) {
                      onDispatchToOpenClaw(targetBot, activeHost, instruction)
                    }
                  },
                )
              }
            }
          }
        }
      }
    }

    // 4. Rotating LLM Wheel + Quick Continue Discussion & Composer
    LlmRotaryWheelSelector(
      selectedOption = selectedWheelModel,
      onSelectOption = onSelectWheelModel,
      darkSurface = false,
      accentColor = Color(0xFF7B00FF),
      modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Button(
        enabled = room != null && participants.isNotEmpty() && !isBusy,
        onClick = {
          if (room != null) {
            onSendRoomMessage(
              room,
              participants,
              messages,
              "Continue the discussion: respond to the previous bot's points and refine the final result.",
              false,
              selectedWheelModel,
            )
          }
        },
        border = BorderStroke(1.8.dp, Color(0xFF280842)),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFFFFD54F),
          contentColor = Color(0xFF280842),
        ),
        modifier = Modifier
          .height(42.dp)
          .testTag("room_continue_discussion_button"),
      ) {
        Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Let Bots Discuss More", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
      }
    }

    Surface(
      shape = RoundedCornerShape(24.dp),
      color = Color(0xFAFFF9F2),
      tonalElevation = 4.dp,
      border = BorderStroke(2.dp, Color(0xFF280842)),
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
          onValueChange = { composerText = it },
          placeholder = {
            Text(
              "Ask the forum (${selectedWheelModel.wheelLabel})...",
              style = MaterialTheme.typography.bodyMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          maxLines = 4,
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("room_chat_input"),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
          onClick = {
            if (room != null && composerText.isNotBlank() && !isBusy) {
              val prompt = composerText
              composerText = ""
              onSendRoomMessage(room, participants, messages, prompt, true, selectedWheelModel)
            }
          },
          enabled = room != null && composerText.isNotBlank() && !isBusy,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
          ),
          modifier = Modifier
            .size(48.dp)
            .testTag("room_send_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
          } else {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send to room")
          }
        }
      }
    }
    }
  }

  if (showRoomSettingsDialog && room != null) {
    var draftTitle by remember(room.id) { mutableStateOf(room.title) }
    var draftTopic by remember(room.id) { mutableStateOf(room.topic) }
    var chosenIds by remember(room.id) { mutableStateOf(room.botIds.toSet()) }
    AlertDialog(
      onDismissRequest = { showRoomSettingsDialog = false },
      title = { Text("Forum Room Settings") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = draftTitle,
            onValueChange = { draftTitle = it.take(60) },
            label = { Text("Room Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = draftTopic,
            onValueChange = { draftTopic = it.take(160) },
            label = { Text("Room Focus") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Text("Bots in Room:", style = MaterialTheme.typography.labelLarge)
          allBots.forEach { bot ->
            val checked = bot.id in chosenIds
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  chosenIds = if (checked && chosenIds.size > 1) chosenIds - bot.id else chosenIds + bot.id
                }
                .padding(vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = { isChecked ->
                  chosenIds = if (!isChecked && chosenIds.size > 1) chosenIds - bot.id else chosenIds + bot.id
                },
              )
              Text("${bot.name} • ${bot.displayMission}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onUpdateRoom(
              room.copy(
                title = draftTitle.ifBlank { room.title },
                topic = draftTopic,
                botIds = chosenIds.toList(),
              )
            )
            showRoomSettingsDialog = false
          },
        ) { Text("Save Room") }
      },
      dismissButton = {
        TextButton(onClick = { showRoomSettingsDialog = false }) { Text("Cancel") }
      },
    )
  }

  missionEditingBot?.let { bot ->
    var draftMission by remember(bot.id) { mutableStateOf(bot.displayMission) }
    AlertDialog(
      onDismissRequest = { missionEditingBot = null },
      title = { Text("Change ${bot.name}'s Mission") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Customize what ${bot.name} does in this chat room and 1-on-1 chats:",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = draftMission,
            onValueChange = { draftMission = it.take(160) },
            label = { Text("Mission") },
            placeholder = { Text("e.g., I help translate documents!") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("room_edit_mission_input"),
          )
        }
      },
      confirmButton = {
        Button(
          enabled = draftMission.trim().isNotEmpty(),
          onClick = {
            onUpdateBotMission(bot, draftMission)
            missionEditingBot = null
          },
        ) { Text("Save Mission") }
      },
      dismissButton = {
        TextButton(onClick = { missionEditingBot = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun RoomMessageBubble(
  message: RoomMessage,
  speakerBot: CompanionBot?,
  isUser: Boolean,
  onRunOnOpenClaw: (String) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    verticalAlignment = Alignment.Top,
  ) {
    if (!isUser && speakerBot != null) {
      val haloColor = when (message.expressionEnum) {
        BotExpression.THINKING -> Color(0xFFFF2E93)
        BotExpression.WORKING -> Color(0xFFFF9800)
        BotExpression.UH_HUH -> Color(0xFF2E9D5B)
        BotExpression.WORRIED -> Color(0xFF56B4F7)
        BotExpression.SORRY, BotExpression.ALERT -> Color(0xFFE53935)
        BotExpression.HAPPY -> Color(0xFFCFA6FF)
      }
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .padding(top = 2.dp, end = 8.dp)
              .size(80.dp)
              .clip(CircleShape)
              .background(
                Brush.radialGradient(
                  colors = listOf(Color.White.copy(alpha = 0.34f), haloColor.copy(alpha = 0.2f), Color.Transparent)
                )
              )
              .testTag("room_message_chat_head_${message.id}"),
          ) {
            BotVectorAvatar(
              bot = speakerBot,
              expression = message.expressionEnum,
              size = 76.dp,
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
          color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          tonalElevation = if (isUser) 0.dp else 2.dp,
          modifier = if (isUser) {
            Modifier.widthIn(max = 520.dp)
          } else {
            Modifier
              .weight(1f, fill = false)
              .widthIn(max = 520.dp)
          },
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (!isUser) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = message.senderBotName,
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                  fontWeight = FontWeight.Bold,
                )
                if (!message.replyToBotName.isNullOrBlank()) {
                  Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0x26FF2E93),
                  ) {
                    Text(
                      text = "@${message.replyToBotName}",
                      style = MaterialTheme.typography.labelSmall,
                      color = Color(0xFFD62583),
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                  }
                }
              }
            }

        Text(
          text = message.rawText,
          style = MaterialTheme.typography.bodyMedium,
        )

        if (!message.parsedInstruction.isNullOrBlank()) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(14.dp),
                )
                Text(
                  text = "OpenClaw Action",
                  style = MaterialTheme.typography.labelMedium,
                )
              }
              Text(
                text = message.parsedInstruction,
                style = MaterialTheme.typography.bodySmall,
              )
              TextButton(onClick = { onRunOnOpenClaw(message.parsedInstruction) }) {
                Icon(Icons.Rounded.Computer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run on OpenClaw")
              }
            }
          }
        }
      }
    }
  }
}
