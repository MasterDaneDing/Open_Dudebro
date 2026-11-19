package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.model.BotExpression
import com.example.model.BotMessage
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import com.example.model.HostStatus
import com.example.model.LinkedFile
import com.example.model.MessageSender
import com.example.model.MessageStatus
import com.example.model.OpenClawHost
import com.example.model.RoomMessage
import com.example.model.StarterBots
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
