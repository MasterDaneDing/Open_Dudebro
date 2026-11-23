package com.example.data.openclaw

import com.example.model.BotDude
import com.example.model.BotExpression
import com.example.model.CafeSession
import com.example.model.CafeSessionStatus
import com.example.model.CafeSpeakerRole
import com.example.model.CafeTranscriptEntry
import com.example.model.ChatRoom
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Sequential A2A Turn Orchestrator for Cafe Mode.
 *
 * Instead of asking a single LLM call to roleplay multiple bots in one JSON blob, each turn
 * invokes the selected Bot Dude's own isolated OpenClaw session key, system persona, tool permissions,
 * and designated model override while sharing an attributed transcript (`[Speaker]: message`).
 *
 * Includes loop prevention (`maxPingPongTurns`), Pause/Stop controls, `@BotName` targeting,
 * and immediate user interjection that cancels any in-flight bot turn and resets the turn budget.
 */
class CafeOrchestrator(
  private val turnDelayMs: Long = 220L,
) {
  private val _sessions = MutableStateFlow<Map<String, CafeSession>>(emptyMap())
  val sessions: StateFlow<Map<String, CafeSession>> = _sessions.asStateFlow()

  private val activeJobs = ConcurrentHashMap<String, Job>()
  private val activeSessionKeys = ConcurrentHashMap<String, String>()

  fun parseMentionedBot(text: String, participants: List<BotDude>): BotDude? {
    if (text.isBlank() || participants.isEmpty()) return null
    val sorted = participants.sortedByDescending { it.name.length }
    return sorted.firstOrNull { bot ->
      val name = bot.name.trim()
      if (name.isEmpty()) false
      else {
        val pattern = Regex("(^|\\s)@${Regex.escape(name)}(?=$|\\s|[.,!?:;\\)\\]])", RegexOption.IGNORE_CASE)
        pattern.containsMatchIn(text)
      }
    }
  }

  fun chooseNextSpeaker(
    participants: List<BotDude>,
    transcript: List<CafeTranscriptEntry>,
    explicitTargetBotId: String? = null,
  ): Pair<BotDude, String?> {
    require(participants.isNotEmpty()) { "Cafe Mode requires at least one participating Bot Dude." }
    val lastEntry = transcript.lastOrNull()
    val lastBotReplyTo = if (lastEntry?.role == CafeSpeakerRole.BOT) lastEntry.speakerName else null

    if (!explicitTargetBotId.isNullOrBlank()) {
      participants.firstOrNull { it.id == explicitTargetBotId }?.let { target ->
        val replyTo = lastBotReplyTo?.takeIf { !it.equals(target.name, ignoreCase = true) }
        return target to replyTo
      }
    }

    if (lastEntry != null) {
      val mentioned = parseMentionedBot(lastEntry.content, participants)
      if (mentioned != null && (participants.size == 1 || !isSameSpeaker(mentioned, lastEntry))) {
        val replyTo = lastBotReplyTo?.takeIf { !it.equals(mentioned.name, ignoreCase = true) }
        return mentioned to replyTo
      }
    }

    val lastBotEntry = transcript.lastOrNull { it.role == CafeSpeakerRole.BOT }
    if (lastBotEntry == null) {
      return participants.first() to null
    }
    val lastIdx = participants.indexOfFirst { isSameSpeaker(it, lastBotEntry) }
    val nextIdx = if (lastIdx >= 0) (lastIdx + 1) % participants.size else 0
    val nextSpeaker = participants[nextIdx]
    val replyTo = lastBotEntry.speakerName.takeIf { !it.equals(nextSpeaker.name, ignoreCase = true) }
    return nextSpeaker to replyTo
  }

  fun syncInitialTranscript(
    room: ChatRoom,
    participants: List<BotDude>,
    entries: List<CafeTranscriptEntry>,
  ) {
    if (room.id.isBlank()) return
    _sessions.update { map ->
      val current = map[room.id]
      if (current != null && current.status == CafeSessionStatus.RUNNING) {
        map
      } else {
        map + (
          room.id to (
            current?.copy(
              roomTitle = room.title,
              topic = room.topic,
              activeBotDudes = participants,
              transcript = entries,
              maxPingPongTurns = room.effectiveMaxPingPongTurns,
            ) ?: CafeSession(
              roomId = room.id,
              roomTitle = room.title,
              topic = room.topic,
              activeBotDudes = participants,
              transcript = entries,
              maxPingPongTurns = room.effectiveMaxPingPongTurns,
              status = CafeSessionStatus.IDLE,
            )
          )
        )
      }
    }
  }

  fun startOrInterjectDiscussion(
    scope: CoroutineScope,
    room: ChatRoom,
    participants: List<BotDude>,
    existingTranscript: List<CafeTranscriptEntry>,
    userEntry: CafeTranscriptEntry? = null,
    targetBotId: String? = null,
    isResume: Boolean = false,
    dispatchBotTurn: suspend (
      speaker: BotDude,
      transcript: List<CafeTranscriptEntry>,
      replyToName: String?,
      onPartialText: (String) -> Unit,
    ) -> Result<OpenClawDispatchResult>,
    onAbortSession: (String) -> Unit = {},
    onCommitBotTurn: suspend (
      roomId: String,
      speaker: BotDude,
      replyToName: String?,
      result: OpenClawDispatchResult,
    ) -> Unit,
    onError: suspend (roomId: String, speaker: BotDude, error: Throwable) -> Unit = { _, _, _ -> },
  ) {
    if (room.id.isBlank() || participants.isEmpty()) return

    // User interjection or explicit restart immediately halts any in-flight bot turn cleanly.
    cancelActiveTurn(room.id, onAbortSession)

    val prevSession = _sessions.value[room.id]
    val baseTranscript = if (existingTranscript.isNotEmpty()) {
      existingTranscript
    } else {
      prevSession?.transcript.orEmpty()
    }
    var workingTranscript = if (userEntry != null && baseTranscript.none { it.id == userEntry.id }) {
      baseTranscript + userEntry
    } else {
      baseTranscript
    }

    val maxTurns = room.effectiveMaxPingPongTurns
    var completedTurns = if (isResume && userEntry == null && prevSession != null) {
      if (prevSession.completedBotTurnsInRound >= maxTurns) 0 else prevSession.completedBotTurnsInRound
    } else {
      0
    }

    _sessions.update { map ->
      map + (
        room.id to CafeSession(
          roomId = room.id,
          roomTitle = room.title,
          topic = room.topic,
          activeBotDudes = participants,
          transcript = workingTranscript,
          maxPingPongTurns = maxTurns,
          completedBotTurnsInRound = completedTurns,
          activeTurnIndex = completedTurns + 1,
          status = CafeSessionStatus.RUNNING,
          statusNote = "Routing turn ${completedTurns + 1} of $maxTurns...",
        )
      )
    }

    val job = scope.launch {
      try {
        while (isActive && completedTurns < maxTurns) {
          val latestStatus = _sessions.value[room.id]?.status
          if (latestStatus == CafeSessionStatus.PAUSED || latestStatus == CafeSessionStatus.STOPPED) {
            break
          }

          val explicitId = if (completedTurns == 0 || (isResume && completedTurns == prevSession?.completedBotTurnsInRound)) {
            targetBotId
          } else {
            null
          }
          val (speaker, replyToName) = chooseNextSpeaker(participants, workingTranscript, explicitId)
          val cafeSessionKey = speaker.sourceBot?.effectiveCafeSessionKey(room.id)
            ?: "agent:${speaker.id}:cafe_${room.id}"
          activeSessionKeys[room.id] = cafeSessionKey

          _sessions.update { map ->
            val cur = map[room.id] ?: return@update map
            map + (
              room.id to cur.copy(
                activeSpeakerBotId = speaker.id,
                activeSpeakerName = speaker.name,
                activeSpeakerModel = speaker.modelId.ifBlank { speaker.provider.displayName },
                activeTurnIndex = completedTurns + 1,
                completedBotTurnsInRound = completedTurns,
                streamingPreview = "",
                status = CafeSessionStatus.RUNNING,
                statusNote = "Turn ${completedTurns + 1}/$maxTurns • ${speaker.name} is replying...",
              )
            )
          }

          val turnRes = dispatchBotTurn(speaker, workingTranscript, replyToName) { partial ->
            _sessions.update { map ->
              val cur = map[room.id] ?: return@update map
              if (cur.status == CafeSessionStatus.RUNNING && cur.activeSpeakerBotId == speaker.id) {
                map + (room.id to cur.copy(streamingPreview = partial))
              } else {
                map
              }
            }
          }
          activeSessionKeys.remove(room.id, cafeSessionKey)

          val result = turnRes.getOrElse { err ->
            if (err is CancellationException) throw err
            _sessions.update { map ->
              val cur = map[room.id] ?: return@update map
              map + (
                room.id to cur.copy(
                  activeSpeakerBotId = null,
                  activeSpeakerName = null,
                  activeSpeakerModel = null,
                  streamingPreview = null,
                  status = CafeSessionStatus.STOPPED,
                  statusNote = err.localizedMessage ?: "Turn failed for ${speaker.name}.",
                )
              )
            }
            onError(room.id, speaker, err)
            return@launch
          }

          val cleanText = result.replyText.trim().ifBlank { "I've added my review." }
          val botEntry = CafeTranscriptEntry(
            id = "cafe_${room.id}_${completedTurns}_${System.nanoTime()}",
            speakerId = speaker.id,
            speakerName = speaker.name,
            role = CafeSpeakerRole.BOT,
            replyToBotName = replyToName,
            content = cleanText,
            modelUsed = result.mediaModel?.takeIf { it.isNotBlank() } ?: speaker.modelId,
            expression = result.expression,
            parsedInstruction = result.parsedInstruction,
            mediaUrl = result.mediaUrl,
            mediaType = result.mediaType,
          )
          workingTranscript = workingTranscript + botEntry
          completedTurns += 1

          _sessions.update { map ->
            val cur = map[room.id] ?: return@update map
            map + (
              room.id to cur.copy(
                transcript = workingTranscript,
                completedBotTurnsInRound = completedTurns,
                streamingPreview = null,
              )
            )
          }

          onCommitBotTurn(room.id, speaker, replyToName, result)

          if (completedTurns < maxTurns && turnDelayMs > 0L) {
            delay(turnDelayMs)
          }
        }

        _sessions.update { map ->
          val cur = map[room.id] ?: return@update map
          if (cur.status == CafeSessionStatus.RUNNING) {
            map + (
              room.id to cur.copy(
                activeSpeakerBotId = null,
                activeSpeakerName = null,
                activeSpeakerModel = null,
                streamingPreview = null,
                status = CafeSessionStatus.COMPLETED,
                statusNote = "Completed $completedTurns/$maxTurns turns • Send a message or tap Resume for more.",
              )
            )
          } else {
            map
          }
        }
      } catch (e: CancellationException) {
        throw e
      }
    }

    activeJobs[room.id] = job
    job.invokeOnCompletion {
      activeJobs.remove(room.id, job)
    }
  }

  fun pause(roomId: String, onAbortSession: (String) -> Unit = {}) {
    cancelActiveTurn(roomId, onAbortSession)
    _sessions.update { map ->
      val cur = map[roomId] ?: return@update map
      map + (
        roomId to cur.copy(
          activeSpeakerBotId = null,
          activeSpeakerName = null,
          activeSpeakerModel = null,
          streamingPreview = null,
          status = CafeSessionStatus.PAUSED,
          statusNote = "Paused at turn ${cur.completedBotTurnsInRound}/${cur.maxPingPongTurns}.",
        )
      )
    }
  }

  fun stop(roomId: String, onAbortSession: (String) -> Unit = {}) {
    cancelActiveTurn(roomId, onAbortSession)
    _sessions.update { map ->
      val cur = map[roomId] ?: return@update map
      map + (
        roomId to cur.copy(
          activeSpeakerBotId = null,
          activeSpeakerName = null,
          activeSpeakerModel = null,
          streamingPreview = null,
          completedBotTurnsInRound = 0,
          status = CafeSessionStatus.STOPPED,
          statusNote = "Stopped by user.",
        )
      )
    }
  }

  fun abortAll(onAbortSession: (String) -> Unit = {}) {
    activeJobs.keys.toList().forEach { roomId ->
      stop(roomId, onAbortSession)
    }
  }

  private fun cancelActiveTurn(roomId: String, onAbortSession: (String) -> Unit) {
    activeSessionKeys.remove(roomId)?.let { onAbortSession(it) }
    activeJobs.remove(roomId)?.cancel()
  }

  private fun isSameSpeaker(bot: BotDude, entry: CafeTranscriptEntry): Boolean {
    return entry.role == CafeSpeakerRole.BOT &&
      (entry.speakerId == bot.id || entry.speakerName.equals(bot.name, ignoreCase = true))
  }
}
