package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.model.BotExpression
import com.example.model.BotMessage
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.GeneralChatMessage
import com.example.model.GeneralChatThread
import com.example.model.GeneratedMediaItem
import com.example.model.GeneratedMediaType
import com.example.model.HostConnectionMode
import com.example.model.HostStatus
import com.example.model.LinkedFile
import com.example.model.MessageSender
import com.example.model.MessageStatus
import com.example.model.OpenClawHost
import com.example.model.RoomMessage
import com.example.model.StarterBots
import com.example.model.UserVenicePreferences
import com.example.model.VeniceModelCatalog
import com.example.model.WheelLlmOption
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ClawPalRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(expectedUserId: String): String {
    val uid = auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user write." }
    return uid
  }

  fun observeBots(userId: String): Flow<List<CompanionBot>> {
    val path = "users/$userId/bots"
    return db.collection("users").document(userId).collection("bots")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(CompanionBot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  fun observeMessages(userId: String, botId: String): Flow<List<BotMessage>> {
    val path = "users/$userId/bots/$botId/messages"
    return db.collection("users").document(userId)
      .collection("bots").document(botId)
      .collection("messages")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(BotMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  fun observeHosts(userId: String): Flow<List<OpenClawHost>> {
    val path = "users/$userId/hosts"
    return db.collection("users").document(userId).collection("hosts")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(OpenClawHost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  fun observeLinkedFiles(userId: String): Flow<List<LinkedFile>> {
    val path = "users/$userId/linkedFiles"
    return db.collection("users").document(userId).collection("linkedFiles")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(LinkedFile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  fun observeGeneratedMedia(userId: String): Flow<List<GeneratedMediaItem>> {
    val path = "users/$userId/generatedMedia"
    return db.collection("users").document(userId).collection("generatedMedia")
      .orderBy("createdAt", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(GeneratedMediaItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  fun observeUserVenicePreferences(userId: String): Flow<UserVenicePreferences?> {
    val path = "users/$userId/settings/venice"
    return db.collection("users").document(userId).collection("settings").document("venice")
      .snapshots()
      .map { snapshot ->
        if (!snapshot.exists()) null
        else snapshot.toObject(UserVenicePreferences::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.GET, path, userId)
        throw error
      }
  }

  fun observeChatRooms(userId: String): Flow<List<ChatRoom>> {
    val path = "users/$userId/chatRooms"
    return db.collection("users").document(userId).collection("chatRooms")
      .orderBy("updatedAt", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  fun observeRoomMessages(userId: String, roomId: String): Flow<List<RoomMessage>> {
    val path = "users/$userId/chatRooms/$roomId/messages"
    return db.collection("users").document(userId)
      .collection("chatRooms").document(roomId)
      .collection("messages")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(RoomMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  fun observeGeneralChats(userId: String): Flow<List<GeneralChatThread>> {
    val path = "users/$userId/generalChats"
    return db.collection("users").document(userId).collection("generalChats")
      .orderBy("updatedAt", Query.Direction.DESCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(GeneralChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  fun observeGeneralMessages(userId: String, chatId: String): Flow<List<GeneralChatMessage>> {
    val path = "users/$userId/generalChats/$chatId/messages"
    return db.collection("users").document(userId)
      .collection("generalChats").document(chatId)
      .collection("messages")
      .orderBy("createdAt", Query.Direction.ASCENDING)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(GeneralChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logFlowErrorIfActive(error, OperationType.LIST, path, userId)
        throw error
      }
  }

  suspend fun seedStarterWorkspaceIfEmpty(userId: String): Result<Unit> {
    val path = "users/$userId/bots"
    return try {
      val uid = requireUserId(userId)
      val botsCol = db.collection("users").document(uid).collection("bots")
      val botsSnap = botsCol.limit(1).get().await()
      if (botsSnap.isEmpty) {
        StarterBots.defaultCrew(uid).forEach { bot ->
          val doc = botsCol.document()
          doc.set(bot.toCreateMap(uid)).await()
        }
      }
      val hostsSnap = db.collection("users").document(uid).collection("hosts").limit(1).get().await()
      if (hostsSnap.isEmpty) {
        val hostDoc = db.collection("users").document(uid).collection("hosts").document()
        hostDoc.set(StarterBots.defaultHost(uid).toCreateMap(uid)).await()
      }
      val roomsCol = db.collection("users").document(uid).collection("chatRooms")
      val roomsSnap = roomsCol.limit(1).get().await()
      if (roomsSnap.isEmpty) {
        val existingBots = botsCol.orderBy("createdAt", Query.Direction.ASCENDING).get().await()
          .toObjects(CompanionBot::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (existingBots.size >= 2) {
          val selected = existingBots.take(3)
          val roomDoc = roomsCol.document()
          val namesLine = selected.joinToString(" & ") { it.name }
          val starterRoom = ChatRoom(
            userId = uid,
            title = "Cafe Translation Squad",
            topic = "Collaborate: translate & critique together",
            botIds = selected.map { it.id },
            lastMessagePreview = "$namesLine are chatting at the cafe!",
          )
          roomDoc.set(starterRoom.toCreateMap(uid)).await()
          val firstBot = selected.first()
          val secondBot = selected[1]
          roomDoc.collection("messages").document().set(
            RoomMessage(
              userId = uid,
              roomId = roomDoc.id,
              senderType = MessageSender.BOT.name,
              senderBotId = firstBot.id,
              senderBotName = firstBot.name,
              rawText = "Welcome to the cafe! Drop text or a task here — ${firstBot.displayMission}",
              expression = BotExpression.HAPPY.name,
            ).toCreateMap(uid, roomDoc.id)
          ).await()
          roomDoc.collection("messages").document().set(
            RoomMessage(
              userId = uid,
              roomId = roomDoc.id,
              senderType = MessageSender.BOT.name,
              senderBotId = secondBot.id,
              senderBotName = secondBot.name,
              replyToBotName = firstBot.name,
              rawText = "And I'll review every draft! (${secondBot.displayMission})",
              expression = BotExpression.UH_HUH.name,
            ).toCreateMap(uid, roomDoc.id)
          ).await()
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun createBot(userId: String, bot: CompanionBot): Result<String> {
    val colRef = db.collection("users").document(userId).collection("bots")
    var docPath = "users/$userId/bots"
    return try {
      val uid = requireUserId(userId)
      val docRef = colRef.document()
      docPath = docRef.path
      docRef.set(bot.toCreateMap(uid)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateBot(userId: String, bot: CompanionBot): Result<Unit> {
    val docPath = "users/$userId/bots/${bot.id}"
    return try {
      val uid = requireUserId(userId)
      require(bot.id.isNotBlank()) { "Bot ID must not be blank for update" }
      db.collection("users").document(uid).collection("bots").document(bot.id)
        .update(bot.toUpdateMap())
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteBot(userId: String, botId: String): Result<Unit> {
    val docPath = "users/$userId/bots/$botId"
    return try {
      val uid = requireUserId(userId)
      require(botId.isNotBlank()) { "Bot ID must not be blank for delete" }
      val botDoc = db.collection("users").document(uid).collection("bots").document(botId)
      val messagesSnap = botDoc.collection("messages").get().await()
      messagesSnap.documents.chunked(400).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { batch.delete(it.reference) }
        batch.commit().await()
      }
      botDoc.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateBotMission(userId: String, botId: String, mission: String): Result<Unit> {
    val docPath = "users/$userId/bots/$botId"
    return try {
      val uid = requireUserId(userId)
      require(botId.isNotBlank()) { "Bot ID must not be blank" }
      db.collection("users").document(uid).collection("bots").document(botId)
        .update(
          mapOf(
            "mission" to mission.trim().take(160),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateBotSyncStatus(userId: String, botId: String, synced: Boolean): Result<Unit> {
    val docPath = "users/$userId/bots/$botId"
    return try {
      val uid = requireUserId(userId)
      require(botId.isNotBlank()) { "Bot ID must not be blank" }
      db.collection("users").document(uid).collection("bots").document(botId)
        .update(
          mapOf(
            "openClawSynced" to synced,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateBotPreview(userId: String, botId: String, preview: String): Result<Unit> {
    val docPath = "users/$userId/bots/$botId"
    return try {
      val uid = requireUserId(userId)
      require(botId.isNotBlank()) { "Bot ID must not be blank" }
      db.collection("users").document(uid).collection("bots").document(botId)
        .update(
          mapOf(
            "lastMessagePreview" to preview.trim().take(300),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun createChatRoom(userId: String, room: ChatRoom): Result<String> {
    val colRef = db.collection("users").document(userId).collection("chatRooms")
    var docPath = "users/$userId/chatRooms"
    return try {
      val uid = requireUserId(userId)
      val docRef = colRef.document()
      docPath = docRef.path
      docRef.set(room.toCreateMap(uid)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateChatRoom(userId: String, room: ChatRoom): Result<Unit> {
    val docPath = "users/$userId/chatRooms/${room.id}"
    return try {
      val uid = requireUserId(userId)
      require(room.id.isNotBlank()) { "Room ID must not be blank" }
      db.collection("users").document(uid).collection("chatRooms").document(room.id)
        .update(room.toUpdateMap())
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteChatRoom(userId: String, roomId: String): Result<Unit> {
    val docPath = "users/$userId/chatRooms/$roomId"
    return try {
      val uid = requireUserId(userId)
      require(roomId.isNotBlank()) { "Room ID must not be blank" }
      val roomDoc = db.collection("users").document(uid).collection("chatRooms").document(roomId)
      val msgsSnap = roomDoc.collection("messages").get().await()
      msgsSnap.documents.chunked(400).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { batch.delete(it.reference) }
        batch.commit().await()
      }
      roomDoc.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun addRoomMessage(
    userId: String,
    roomId: String,
    message: RoomMessage,
    previewText: String? = null,
  ): Result<String> {
    var docPath = "users/$userId/chatRooms/$roomId/messages"
    return try {
      val uid = requireUserId(userId)
      require(roomId.isNotBlank()) { "Room ID must not be blank" }
      val roomDoc = db.collection("users").document(uid).collection("chatRooms").document(roomId)
      val msgRef = roomDoc.collection("messages").document()
      docPath = msgRef.path
      msgRef.set(message.toCreateMap(uid, roomId)).await()
      val snippet = (previewText ?: "${message.senderBotName}: ${message.rawText}")
        .replace('\n', ' ')
        .trim()
        .take(300)
      roomDoc.update(
        mapOf(
          "lastMessagePreview" to snippet,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(msgRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun addMessage(userId: String, botId: String, message: BotMessage): Result<String> {
    var docPath = "users/$userId/bots/$botId/messages"
    return try {
      val uid = requireUserId(userId)
      require(botId.isNotBlank()) { "Bot ID must not be blank" }
      val docRef = db.collection("users").document(uid)
        .collection("bots").document(botId)
        .collection("messages").document()
      docPath = docRef.path
      docRef.set(message.toCreateMap(uid, botId)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateMessageExecution(
    userId: String,
    botId: String,
    messageId: String,
    rawText: String,
    parsedInstruction: String?,
    instructionGoal: String?,
    instructionSteps: List<String>,
    expression: BotExpression,
    status: MessageStatus,
    takeoverSuggested: Boolean,
    mediaUrl: String? = null,
    mediaType: GeneratedMediaType? = null,
    mediaPrompt: String? = null,
    mediaModel: String? = null,
    mediaDurationSec: Int? = null,
  ): Result<Unit> {
    val docPath = "users/$userId/bots/$botId/messages/$messageId"
    return try {
      val uid = requireUserId(userId)
      require(botId.isNotBlank() && messageId.isNotBlank()) { "IDs must not be blank" }
      val updates = mutableMapOf<String, Any?>(
        "rawText" to rawText.trim().take(4000).ifBlank { "..." },
        "parsedInstruction" to parsedInstruction?.trim()?.take(4000)?.takeIf { it.isNotEmpty() },
        "instructionGoal" to instructionGoal?.trim()?.take(200)?.takeIf { it.isNotEmpty() },
        "instructionSteps" to instructionSteps.take(12).map { it.take(300) },
        "expression" to expression.name,
        "status" to status.name,
        "takeoverSuggested" to takeoverSuggested,
        "mediaUrl" to mediaUrl?.trim()?.take(1500)?.takeIf { it.isNotEmpty() },
        "mediaType" to mediaType?.takeIf { !mediaUrl.isNullOrBlank() }?.name,
        "mediaPrompt" to mediaPrompt?.trim()?.take(1000)?.takeIf { it.isNotEmpty() },
        "mediaModel" to mediaModel?.trim()?.take(120)?.takeIf { it.isNotEmpty() },
        "mediaDurationSec" to mediaDurationSec?.takeIf { !mediaUrl.isNullOrBlank() }?.coerceIn(0, 600),
        "updatedAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }
      db.collection("users").document(uid)
        .collection("bots").document(botId)
        .collection("messages").document(messageId)
        .update(updates)
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun saveHost(userId: String, host: OpenClawHost): Result<String> {
    var docPath = "users/$userId/hosts"
    return try {
      val uid = requireUserId(userId)
      val hostsCol = db.collection("users").document(uid).collection("hosts")
      if (host.id.isBlank()) {
        val docRef = hostsCol.document()
        docPath = docRef.path
        docRef.set(host.toCreateMap(uid)).await()
        Result.success(docRef.id)
      } else {
        val docRef = hostsCol.document(host.id)
        docPath = docRef.path
        docRef.update(host.toUpdateMap()).await()
        Result.success(host.id)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateHostStatus(userId: String, hostId: String, status: HostStatus): Result<Unit> {
    val docPath = "users/$userId/hosts/$hostId"
    return try {
      val uid = requireUserId(userId)
      require(hostId.isNotBlank()) { "Host ID must not be blank" }
      db.collection("users").document(uid).collection("hosts").document(hostId)
        .update(
          mapOf(
            "lastStatus" to status.name,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateHostConnectionMode(
    userId: String,
    host: OpenClawHost,
    newMode: HostConnectionMode,
  ): Result<OpenClawHost> {
    val docPath = "users/$userId/hosts/${host.id}"
    return try {
      val uid = requireUserId(userId)
      require(host.id.isNotBlank()) { "Host ID must not be blank" }
      val updatedHost = host.copy(
        connectionMode = newMode.name,
        lanUrl = host.effectiveLanUrl,
      )
      val activeBase = updatedHost.effectiveBaseUrl.ifBlank { host.baseUrl }
      val finalHost = updatedHost.copy(baseUrl = activeBase)
      db.collection("users").document(uid).collection("hosts").document(host.id)
        .update(
          mapOf(
            "connectionMode" to newMode.name,
            "baseUrl" to activeBase.take(300),
            "lanUrl" to finalHost.effectiveLanUrl.take(300),
            "tailscaleUrl" to finalHost.effectiveTailscaleUrl.take(300),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(finalHost)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteHost(userId: String, hostId: String): Result<Unit> {
    val docPath = "users/$userId/hosts/$hostId"
    return try {
      val uid = requireUserId(userId)
      require(hostId.isNotBlank()) { "Host ID must not be blank" }
      db.collection("users").document(uid).collection("hosts").document(hostId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun addLinkedFile(userId: String, file: LinkedFile): Result<String> {
    var docPath = "users/$userId/linkedFiles"
    return try {
      val uid = requireUserId(userId)
      val docRef = db.collection("users").document(uid).collection("linkedFiles").document()
      docPath = docRef.path
      docRef.set(file.toCreateMap(uid)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteLinkedFile(userId: String, fileId: String): Result<Unit> {
    val docPath = "users/$userId/linkedFiles/$fileId"
    return try {
      val uid = requireUserId(userId)
      require(fileId.isNotBlank()) { "File ID must not be blank" }
      db.collection("users").document(uid).collection("linkedFiles").document(fileId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun addGeneratedMedia(userId: String, item: GeneratedMediaItem): Result<String> {
    var docPath = "users/$userId/generatedMedia"
    return try {
      val uid = requireUserId(userId)
      if (item.mediaUrl.isBlank()) {
        return Result.failure(IllegalArgumentException("Generated media URL must not be blank"))
      }
      val docRef = db.collection("users").document(uid).collection("generatedMedia").document()
      docPath = docRef.path
      docRef.set(item.toCreateMap(uid)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteGeneratedMedia(userId: String, mediaId: String): Result<Unit> {
    val docPath = "users/$userId/generatedMedia/$mediaId"
    return try {
      val uid = requireUserId(userId)
      require(mediaId.isNotBlank()) { "Media ID must not be blank" }
      db.collection("users").document(uid).collection("generatedMedia").document(mediaId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun saveUserVenicePreferences(
    userId: String,
    globalVeniceModel: String,
    veniceApiKeyOverride: String,
  ): Result<Unit> {
    val docPath = "users/$userId/settings/venice"
    return try {
      val uid = requireUserId(userId)
      val cleanModel = globalVeniceModel.trim().take(120).ifBlank { VeniceModelCatalog.defaultModelId }
      val cleanKey = veniceApiKeyOverride.trim().take(256)
      val docRef = db.collection("users").document(uid).collection("settings").document("venice")
      val existingSnap = docRef.get().await()
      if (!existingSnap.exists()) {
        docRef.set(
          UserVenicePreferences(
            userId = uid,
            globalVeniceModel = cleanModel,
            veniceApiKeyOverride = cleanKey,
          ).toCreateMap(uid)
        ).await()
      } else {
        docRef.update(
          mapOf(
            "globalVeniceModel" to cleanModel,
            "veniceApiKeyOverride" to cleanKey,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docPath)
      Result.failure(e)
    }
  }

  suspend fun createGeneralChat(userId: String, thread: GeneralChatThread): Result<String> {
    val colRef = db.collection("users").document(userId).collection("generalChats")
    var docPath = "users/$userId/generalChats"
    return try {
      val uid = requireUserId(userId)
      val docRef = colRef.document()
      docPath = docRef.path
      docRef.set(thread.toCreateMap(uid)).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun togglePinGeneralChat(userId: String, chatId: String, pinned: Boolean): Result<Unit> {
    val docPath = "users/$userId/generalChats/$chatId"
    return try {
      val uid = requireUserId(userId)
      require(chatId.isNotBlank()) { "Chat ID must not be blank" }
      db.collection("users").document(uid).collection("generalChats").document(chatId)
        .update(mapOf("pinned" to pinned, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateGeneralChatWheel(userId: String, chatId: String, wheelModel: WheelLlmOption): Result<Unit> {
    val docPath = "users/$userId/generalChats/$chatId"
    return try {
      val uid = requireUserId(userId)
      require(chatId.isNotBlank()) { "Chat ID must not be blank" }
      db.collection("users").document(uid).collection("generalChats").document(chatId)
        .update(mapOf("wheelModel" to wheelModel.name, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun deleteGeneralChat(userId: String, chatId: String): Result<Unit> {
    val docPath = "users/$userId/generalChats/$chatId"
    return try {
      val uid = requireUserId(userId)
      require(chatId.isNotBlank()) { "Chat ID must not be blank" }
      val chatDoc = db.collection("users").document(uid).collection("generalChats").document(chatId)
      val msgsSnap = chatDoc.collection("messages").get().await()
      msgsSnap.documents.chunked(400).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { batch.delete(it.reference) }
        batch.commit().await()
      }
      chatDoc.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docPath)
      Result.failure(e)
    }
  }

  suspend fun addGeneralMessage(
    userId: String,
    chatId: String,
    message: GeneralChatMessage,
    newTitleIfDefault: String? = null,
  ): Result<String> {
    var docPath = "users/$userId/generalChats/$chatId/messages"
    return try {
      val uid = requireUserId(userId)
      require(chatId.isNotBlank()) { "Chat ID must not be blank" }
      val chatDoc = db.collection("users").document(uid).collection("generalChats").document(chatId)
      val msgRef = chatDoc.collection("messages").document()
      docPath = msgRef.path
      msgRef.set(message.toCreateMap(uid, chatId)).await()
      val updates = mutableMapOf<String, Any>(
        "lastMessagePreview" to message.rawText.replace('\n', ' ').trim().take(300),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (!newTitleIfDefault.isNullOrBlank()) {
        updates["title"] = newTitleIfDefault.trim().take(80)
      }
      chatDoc.update(updates).await()
      Result.success(msgRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docPath)
      Result.failure(e)
    }
  }

  suspend fun updateGeneralMessage(
    userId: String,
    chatId: String,
    messageId: String,
    rawText: String,
    modelLabel: String,
    status: MessageStatus,
    mediaUrl: String? = null,
    mediaType: GeneratedMediaType? = null,
    mediaPrompt: String? = null,
    mediaModel: String? = null,
    mediaDurationSec: Int? = null,
  ): Result<Unit> {
    val docPath = "users/$userId/generalChats/$chatId/messages/$messageId"
    return try {
      val uid = requireUserId(userId)
      require(chatId.isNotBlank() && messageId.isNotBlank()) { "IDs must not be blank" }
      val chatDoc = db.collection("users").document(uid).collection("generalChats").document(chatId)
      val updates = mutableMapOf<String, Any?>(
        "rawText" to rawText.trim().take(4000).ifBlank { "..." },
        "modelLabel" to modelLabel.trim().take(80),
        "status" to status.name,
        "mediaUrl" to mediaUrl?.trim()?.take(1500)?.takeIf { it.isNotEmpty() },
        "mediaType" to mediaType?.takeIf { !mediaUrl.isNullOrBlank() }?.name,
        "mediaPrompt" to mediaPrompt?.trim()?.take(1000)?.takeIf { it.isNotEmpty() },
        "mediaModel" to mediaModel?.trim()?.take(120)?.takeIf { it.isNotEmpty() },
        "mediaDurationSec" to mediaDurationSec?.takeIf { !mediaUrl.isNullOrBlank() }?.coerceIn(0, 600),
        "updatedAt" to FieldValue.serverTimestamp(),
      ).filterValues { it != null }
      chatDoc.collection("messages").document(messageId)
        .update(updates)
        .await()
      chatDoc.update(
        mapOf(
          "lastMessagePreview" to rawText.replace('\n', ' ').trim().take(300),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docPath)
      Result.failure(e)
    }
  }

  private fun logFlowErrorIfActive(
    error: Throwable,
    op: OperationType,
    path: String,
    expectedUid: String,
  ) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
      handleFirestoreError(exceptionToLog, op, path)
    }
  }
}
