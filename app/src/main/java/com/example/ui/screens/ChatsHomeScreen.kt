package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import com.example.R
import com.example.model.BotExpression
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.ui.avatar.BotVectorAvatar
import com.example.ui.avatar.MultiBotRoomIcon
import com.example.ui.avatar.NeonPinkPurpleBrush
import com.example.ui.components.SectionSettingsIconButton
import com.example.viewmodel.UiState
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class HomeFilter(val label: String) {
  ALL("All"),
  ROOMS("Rooms"),
  BOTS("Bot Dudes"),
}

private sealed interface InboxItem {
  val sortMillis: Long

  data class RoomEntry(val room: ChatRoom, val participants: List<CompanionBot>) : InboxItem {
    override val sortMillis: Long = room.updatedAt?.toDate()?.time ?: room.createdAt?.toDate()?.time ?: 0L
  }

  data class BotEntry(val bot: CompanionBot) : InboxItem {
    override val sortMillis: Long = bot.updatedAt?.toDate()?.time ?: bot.createdAt?.toDate()?.time ?: 0L
  }
}

@Composable
fun ChatsHomeScreen(
  botsState: UiState<List<CompanionBot>>,
  chatRoomsState: UiState<List<ChatRoom>>,
  userEmail: String?,
  onOpenBotChat: (CompanionBot) -> Unit,
  onOpenChatRoom: (ChatRoom) -> Unit,
  onAddNewBot: () -> Unit,
  onCreateChatRoom: (title: String, topic: String, botIds: List<String>) -> Unit,
  onUpdateBotMission: (CompanionBot, String) -> Unit,
  onDeleteBot: (String) -> Unit,
  onDeleteRoom: (String) -> Unit,
  onOpenHostSettings: () -> Unit,
) {
  val bots = (botsState as? UiState.Success)?.data.orEmpty()
  val rooms = (chatRoomsState as? UiState.Success)?.data.orEmpty()

  var searchQuery by remember { mutableStateOf("") }
  var filter by remember { mutableStateOf(HomeFilter.ALL) }
  var showCreateMenu by remember { mutableStateOf(false) }
  var showNewRoomDialog by remember { mutableStateOf(false) }
  var showSectionSettings by remember { mutableStateOf(false) }
  var missionEditingBot by remember { mutableStateOf<CompanionBot?>(null) }
  var deleteTargetBot by remember { mutableStateOf<CompanionBot?>(null) }
  var deleteTargetRoom by remember { mutableStateOf<ChatRoom?>(null) }

  val initials = remember(userEmail) {
    val local = userEmail?.substringBefore('@').orEmpty()
    val parts = local.split('.', '_', '-').filter { it.isNotBlank() }
    when {
      parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
      local.length >= 2 -> local.take(2).uppercase(Locale.US)
      local.length == 1 -> local.uppercase(Locale.US)
      else -> "CP"
    }
  }

  val botsById = remember(bots) { bots.associateBy { it.id } }

  val inboxItems = remember(bots, rooms, searchQuery, filter) {
    val q = searchQuery.trim().lowercase(Locale.US)
    val roomEntries = if (filter == HomeFilter.ALL || filter == HomeFilter.ROOMS) {
      rooms.map { room ->
        val participants = room.botIds.mapNotNull { botsById[it] }.ifEmpty { bots.take(2) }
        InboxItem.RoomEntry(room, participants)
      }.filter { entry ->
        q.isEmpty() ||
          entry.room.title.lowercase(Locale.US).contains(q) ||
          entry.room.topic.lowercase(Locale.US).contains(q) ||
          entry.room.lastMessagePreview.lowercase(Locale.US).contains(q) ||
          entry.participants.any { it.name.lowercase(Locale.US).contains(q) }
      }
    } else emptyList()

    val botEntries = if (filter == HomeFilter.ALL || filter == HomeFilter.BOTS) {
      bots.map { InboxItem.BotEntry(it) }.filter { entry ->
        q.isEmpty() ||
          entry.bot.name.lowercase(Locale.US).contains(q) ||
          entry.bot.displayMission.lowercase(Locale.US).contains(q) ||
          entry.bot.lastMessagePreview.lowercase(Locale.US).contains(q)
      }
    } else emptyList()

    (roomEntries + botEntries).sortedByDescending { it.sortMillis }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(NeonPinkPurpleBrush),
  ) {
    // Soft glowing neon-pink accent halo in upper-right and lower-left
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(Color(0x44FF2E9F), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(850f, 180f),
            radius = 780f,
          )
        )
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 14.dp),
    ) {
      Spacer(Modifier.height(8.dp))

      // Slim, uncluttered Top Header with minimalist title + little corner Settings icon
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Surface(
          onClick = onOpenHostSettings,
          shape = CircleShape,
          color = Color(0x663B115E),
          border = BorderStroke(1.2.dp, Color(0xAAFF73CE)),
          modifier = Modifier
            .size(44.dp)
            .testTag("home_profile_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = initials,
              style = MaterialTheme.typography.labelLarge,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_min_bot_dudes),
            contentDescription = null,
            tint = Color(0xFFFFB2EB),
            modifier = Modifier.size(22.dp),
          )
          Text(
            text = when (filter) {
              HomeFilter.ALL -> "Bot Dudes"
              HomeFilter.ROOMS -> "Forum Rooms"
              HomeFilter.BOTS -> "Bot Dudes"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          IconButton(
            onClick = { showCreateMenu = true },
            modifier = Modifier
              .size(44.dp)
              .testTag("home_add_menu_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Add,
              contentDescription = "New Bot or Chat Room",
              tint = Color.White,
            )
          }
          SectionSettingsIconButton(
            contentDescription = "Bot Dudes & OpenClaw Settings",
            onClick = { showSectionSettings = true },
            tint = Color(0xFFFFB2EB),
            modifier = Modifier.testTag("home_section_settings_button"),
          )
        }
      }

      Spacer(Modifier.height(10.dp))

      // Main Inbox List (Live Animated Blinking Bots & Multi-Bot Rooms)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        when {
          botsState is UiState.Loading && bots.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(color = Color(0xFFFF79CE))
            }
          }
          inboxItems.isEmpty() -> {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Text(
                text = if (searchQuery.isBlank()) "No chats yet" else "No matching bots or rooms",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
              )
              Spacer(Modifier.height(8.dp))
              Button(
                onClick = { showCreateMenu = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E93)),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Create Bot or Room")
              }
            }
          }
          else -> {
            LazyColumn(
              contentPadding = PaddingValues(vertical = 6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxSize(),
            ) {
              items(
                items = inboxItems,
                key = { item ->
                  when (item) {
                    is InboxItem.RoomEntry -> "room_${item.room.id}"
                    is InboxItem.BotEntry -> "bot_${item.bot.id}"
                  }
                },
              ) { item ->
                when (item) {
                  is InboxItem.RoomEntry -> {
                    ChatRoomInboxRow(
                      room = item.room,
                      participants = item.participants,
                      onClick = { onOpenChatRoom(item.room) },
                      onRequestDelete = { deleteTargetRoom = item.room },
                    )
                  }
                  is InboxItem.BotEntry -> {
                    BotInboxRow(
                      bot = item.bot,
                      onClick = { onOpenBotChat(item.bot) },
                      onEditMission = { missionEditingBot = item.bot },
                      onRequestDelete = { deleteTargetBot = item.bot },
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Bottom Search & Filter Pill Row (matches screenshot bottom layout)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          onClick = {
            filter = when (filter) {
              HomeFilter.ALL -> HomeFilter.ROOMS
              HomeFilter.ROOMS -> HomeFilter.BOTS
              HomeFilter.BOTS -> HomeFilter.ALL
            }
          },
          shape = CircleShape,
          color = Color(0x88320A4D),
          border = BorderStroke(1.2.dp, Color(0x88FF73CE)),
          modifier = Modifier
            .size(48.dp)
            .testTag("home_filter_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = when (filter) {
                HomeFilter.ALL -> Icons.Rounded.GridView
                HomeFilter.ROOMS -> Icons.Rounded.Groups
                HomeFilter.BOTS -> Icons.Rounded.SmartToy
              },
              contentDescription = "Cycle filter (${filter.label})",
              tint = Color(0xFFFFB5E8),
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(50),
          color = Color(0x8826073D),
          border = BorderStroke(1.2.dp, Color(0x88FF65C2)),
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = null,
              tint = Color(0xFFF3B5E6),
              modifier = Modifier.size(20.dp),
            )
            Box(modifier = Modifier.weight(1f)) {
              if (searchQuery.isEmpty()) {
                Text(
                  text = "Search",
                  color = Color(0xFFD69ED1),
                  style = MaterialTheme.typography.bodyLarge,
                )
              }
              BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(
                  color = Color.White,
                  fontSize = 16.sp,
                ),
                cursorBrush = SolidColor(Color(0xFFFF6EC7)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("home_search_input"),
              )
            }
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { searchQuery = "" },
                modifier = Modifier.size(28.dp),
              ) {
                Icon(
                  Icons.Rounded.Close,
                  contentDescription = "Clear search",
                  tint = Color(0xFFF3B5E6),
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
    }
  }

  if (showSectionSettings) {
    AlertDialog(
      onDismissRequest = { showSectionSettings = false },
      title = { Text("Bot Dudes Settings") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Show in Directory", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
          HomeFilter.entries.forEach { option ->
            val picked = filter == option
            Surface(
              onClick = { filter = option },
              shape = RoundedCornerShape(12.dp),
              color = if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = if (picked) "✓ ${option.label}" else option.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              )
            }
          }
          HorizontalDivider()
          Button(
            onClick = {
              showSectionSettings = false
              onAddNewBot()
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Create New Bot Dude")
          }
          OutlinedButton(
            onClick = {
              showSectionSettings = false
              showNewRoomDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Create Multi-Bot Forum Room")
          }
          HorizontalDivider()
          Text("General & OpenClaw Settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
          OutlinedButton(
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

  if (showCreateMenu) {
    AlertDialog(
      onDismissRequest = { showCreateMenu = false },
      title = { Text("Create New") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(
            onClick = {
              showCreateMenu = false
              showNewRoomDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD62583)),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("create_chat_room_option"),
          ) {
            Icon(Icons.Rounded.Groups, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New Multi-Bot Chat Room")
          }
          OutlinedButton(
            onClick = {
              showCreateMenu = false
              onAddNewBot()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("create_new_bot_option"),
          ) {
            Icon(Icons.Rounded.SmartToy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("New Companion Bot")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showCreateMenu = false }) { Text("Close") }
      },
    )
  }

  if (showNewRoomDialog) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var chosenIds by remember(bots) {
      mutableStateOf(bots.take(2).map { it.id }.toSet())
    }
    AlertDialog(
      onDismissRequest = { showNewRoomDialog = false },
      title = { Text("Open Cafe Chat Room") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(60) },
            label = { Text("Room Name") },
            placeholder = { Text("e.g., Translation & Critique Cafe") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("new_room_title_input"),
          )
          OutlinedTextField(
            value = topic,
            onValueChange = { topic = it.take(160) },
            label = { Text("Room Focus") },
            placeholder = { Text("e.g., Bot 1 translates, Bot 2 critiques") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Text("Invite Bots (${chosenIds.size} selected):", style = MaterialTheme.typography.labelLarge)
          bots.forEach { bot ->
            val checked = bot.id in chosenIds
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  chosenIds = if (checked && chosenIds.size > 1) chosenIds - bot.id else chosenIds + bot.id
                }
                .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Checkbox(
                checked = checked,
                onCheckedChange = { isChecked ->
                  chosenIds = if (!isChecked && chosenIds.size > 1) chosenIds - bot.id else chosenIds + bot.id
                },
              )
              BotVectorAvatar(bot = bot, expression = BotExpression.HAPPY, size = 34.dp, animate = false)
              Column(modifier = Modifier.weight(1f)) {
                Text(bot.name, style = MaterialTheme.typography.labelLarge)
                Text(
                  text = bot.displayMission,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          enabled = chosenIds.isNotEmpty(),
          onClick = {
            onCreateChatRoom(
              title.ifBlank { "Cafe Squad" },
              topic.ifBlank { "Collaborate & review together" },
              chosenIds.toList(),
            )
            showNewRoomDialog = false
          },
          modifier = Modifier.testTag("confirm_create_room_button"),
        ) {
          Text("Create Room")
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewRoomDialog = false }) { Text("Cancel") }
      },
    )
  }

  missionEditingBot?.let { bot ->
    var draftMission by remember(bot.id) { mutableStateOf(bot.displayMission) }
    AlertDialog(
      onDismissRequest = { missionEditingBot = null },
      title = { Text("Mission for ${bot.name}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Tell ${bot.name} what its job is in chats and multi-bot rooms:",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = draftMission,
            onValueChange = { draftMission = it.take(160) },
            label = { Text("Bot Mission") },
            placeholder = { Text("e.g., I help translate documents!") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_bot_mission_input"),
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
          modifier = Modifier.testTag("save_bot_mission_button"),
        ) {
          Text("Save Mission")
        }
      },
      dismissButton = {
        TextButton(onClick = { missionEditingBot = null }) { Text("Cancel") }
      },
    )
  }

  deleteTargetBot?.let { bot ->
    AlertDialog(
      onDismissRequest = { deleteTargetBot = null },
      title = { Text("Remove ${bot.name}?") },
      text = { Text("Remove ${bot.name} and its saved chat messages?") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            onDeleteBot(bot.id)
            deleteTargetBot = null
          },
        ) { Text("Remove") }
      },
      dismissButton = {
        TextButton(onClick = { deleteTargetBot = null }) { Text("Cancel") }
      },
    )
  }

  deleteTargetRoom?.let { room ->
    AlertDialog(
      onDismissRequest = { deleteTargetRoom = null },
      title = { Text("Close ${room.title}?") },
      text = { Text("Remove this cafe chat room and its message history?") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          onClick = {
            onDeleteRoom(room.id)
            deleteTargetRoom = null
          },
        ) { Text("Delete Room") }
      },
      dismissButton = {
        TextButton(onClick = { deleteTargetRoom = null }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun BotInboxRow(
  bot: CompanionBot,
  onClick: () -> Unit,
  onEditMission: () -> Unit,
  onRequestDelete: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = Color(0x4026083E),
    border = BorderStroke(1.dp, Color(0x33FF85D4)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("inbox_bot_row_${bot.name}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Animated blinking bot avatar right on the landing page
      BotVectorAvatar(
        bot = bot,
        expression = BotExpression.HAPPY,
        size = 54.dp,
        animate = true,
      )

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = buildAnnotatedString {
              withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                append(bot.name)
              }
              withStyle(SpanStyle(color = Color(0xFFFFB2E4), fontWeight = FontWeight.Normal)) {
                append(" • ${bot.displayMission}")
              }
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = formatInboxTimestamp(bot.updatedAt ?: bot.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFE3A6D9),
          )
        }

        Text(
          text = bot.lastMessagePreview.ifBlank { "Tap to chat with ${bot.name}..." },
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFFEBD2EA),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onEditMission,
        modifier = Modifier
          .size(48.dp)
          .testTag("edit_mission_icon_${bot.name}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.EditNote,
          contentDescription = "Edit ${bot.name}'s mission",
          tint = Color(0xFFFF9CE0),
        )
      }
    }
  }
}

@Composable
private fun ChatRoomInboxRow(
  room: ChatRoom,
  participants: List<CompanionBot>,
  onClick: () -> Unit,
  onRequestDelete: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = Color(0x55360C54),
    border = BorderStroke(1.2.dp, Color(0x88FF4DB4)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("inbox_room_row_${room.title}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Multi-head animated blinking Chat Room icon
      MultiBotRoomIcon(
        bots = participants,
        size = 54.dp,
        animate = true,
      )

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = buildAnnotatedString {
              withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                append(room.title)
              }
              withStyle(SpanStyle(color = Color(0xFFFF9DE0), fontWeight = FontWeight.Medium)) {
                append(" • ${participants.size} bots")
              }
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = formatInboxTimestamp(room.updatedAt ?: room.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFFFB7E9),
          )
        }

        Text(
          text = room.lastMessagePreview.ifBlank { room.topic },
          style = MaterialTheme.typography.bodyMedium,
          color = Color(0xFFF6D6F5),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onRequestDelete,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.DeleteOutline,
          contentDescription = "Delete ${room.title}",
          tint = Color(0xFFE09AD5),
        )
      }
    }
  }
}

private fun formatInboxTimestamp(ts: Timestamp?): String {
  val date = ts?.toDate() ?: return "Now"
  val nowCal = Calendar.getInstance()
  val msgCal = Calendar.getInstance().apply { time = date }
  return if (
    nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
    nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
  ) {
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
  } else {
    val diffDays = ((nowCal.timeInMillis - msgCal.timeInMillis) / (1000L * 60L * 60L * 24L)).toInt()
    when {
      diffDays == 1 -> "Yesterday"
      diffDays in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
      else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
  }
}
