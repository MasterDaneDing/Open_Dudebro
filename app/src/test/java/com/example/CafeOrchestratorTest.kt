package com.example

import com.example.data.openclaw.CafeOrchestrator
import com.example.data.openclaw.OpenClawDispatchResult
import com.example.model.BotDude
import com.example.model.BotExpression
import com.example.model.BotFunctionPreset
import com.example.model.CafeSessionStatus
import com.example.model.CafeSpeakerRole
import com.example.model.CafeTranscriptEntry
import com.example.model.ChatRoom
import com.example.model.CompanionBot
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CafeOrchestratorTest {

  private fun makeParticipants(): List<BotDude> {
    val botA = CompanionBot(
      id = "bot_a",
      name = "Translator",
      openClawAgentId = "translator",
      functionPreset = BotFunctionPreset.TRANSLATION.name,
    )
    val botB = CompanionBot(
      id = "bot_b",
      name = "Critic",
      openClawAgentId = "critic",
      functionPreset = BotFunctionPreset.CRITIQUING.name,
    )
    return listOf(
      botA.toBotDude(resolvedModelId = "ollama/translategemma"),
      botB.toBotDude(resolvedModelId = "venice/claude-sonnet-4-6"),
    )
  }

  @Test
  fun chooseNextSpeaker_routesMentions_explicitTargets_andRoundRobinAlternation() {
    val orchestrator = CafeOrchestrator(turnDelayMs = 0L)
    val participants = makeParticipants()

    val transcript1 = listOf(
      CafeTranscriptEntry("1", "u1", "You", CafeSpeakerRole.USER, content = "Hey @Critic check the tone"),
    )
    val (s1, replyTo1) = orchestrator.chooseNextSpeaker(participants, transcript1)
    assertEquals("bot_b", s1.id)
    assertEquals(null, replyTo1)

    // Explicit UI target overrides @mention in previous text
    val (sExplicit, replyToExplicit) = orchestrator.chooseNextSpeaker(
      participants = participants,
      transcript = transcript1,
      explicitTargetBotId = "bot_a",
    )
    assertEquals("bot_a", sExplicit.id)
    assertEquals(null, replyToExplicit)

    // Subsequent turn rotates to Critic replying to Translator
    val transcript2 = transcript1 + CafeTranscriptEntry(
      id = "2",
      speakerId = "bot_a",
      speakerName = "Translator",
      role = CafeSpeakerRole.BOT,
      content = "Here is the Japanese draft.",
    )
    val (s2, replyTo2) = orchestrator.chooseNextSpeaker(participants, transcript2)
    assertEquals("bot_b", s2.id)
    assertEquals("Translator", replyTo2)
  }

  @Test
  fun startOrInterjectDiscussion_enforcesMaxPingPongTurnsLoopPrevention() = runTest {
    val orchestrator = CafeOrchestrator(turnDelayMs = 0L)
    val participants = makeParticipants()
    val room = ChatRoom(
      id = "room_loop",
      title = "Tea Lounge",
      topic = "Review translation",
      botIds = listOf("bot_a", "bot_b"),
      maxPingPongTurns = 3,
    )
    val speakersOrder = CopyOnWriteArrayList<String>()
    val committedCount = CopyOnWriteArrayList<String>()
    val completedSignal = CompletableDeferred<Unit>()

    orchestrator.startOrInterjectDiscussion(
      scope = backgroundScope,
      room = room,
      participants = participants,
      existingTranscript = emptyList(),
      userEntry = CafeTranscriptEntry("u1", "user", "You", CafeSpeakerRole.USER, content = "Translate 'Welcome!'"),
      dispatchBotTurn = { speaker, _, _, onPartial ->
        speakersOrder.add(speaker.name)
        onPartial("${speaker.name} drafting...")
        Result.success(
          OpenClawDispatchResult(
            replyText = "${speaker.name} turn",
            parsedInstruction = null,
            expression = BotExpression.UH_HUH,
            takeoverSuggested = false,
          )
        )
      },
      onCommitBotTurn = { _, speaker, _, _ ->
        committedCount.add(speaker.name)
        if (committedCount.size == 3) {
          completedSignal.complete(Unit)
        }
      },
    )

    completedSignal.await()
    // Let orchestrator finish post-loop status update
    delay(30L)

    assertEquals(listOf("Translator", "Critic", "Translator"), speakersOrder.toList())
    val session = orchestrator.sessions.value[room.id]!!
    assertEquals(CafeSessionStatus.COMPLETED, session.status)
    assertEquals(3, session.completedBotTurnsInRound)
  }

  @Test
  fun userInterjection_abortsInFlightTurnAndResetsRoundBudget() = runTest {
    val orchestrator = CafeOrchestrator(turnDelayMs = 0L)
    val participants = makeParticipants()
    val room = ChatRoom(
      id = "room_interject",
      title = "Interject Room",
      topic = "Iterate copy",
      botIds = listOf("bot_a", "bot_b"),
      maxPingPongTurns = 2,
    )
    val abortedSessions = CopyOnWriteArrayList<String>()
    val slowStarted = CompletableDeferred<Unit>()
    val slowBlocker = CompletableDeferred<Unit>()
    val secondRoundDone = CompletableDeferred<Unit>()
    val secondRoundSpeakers = CopyOnWriteArrayList<String>()

    orchestrator.startOrInterjectDiscussion(
      scope = backgroundScope,
      room = room,
      participants = participants,
      existingTranscript = emptyList(),
      userEntry = CafeTranscriptEntry("u1", "user", "You", CafeSpeakerRole.USER, content = "Start drafting"),
      dispatchBotTurn = { _, _, _, _ ->
        slowStarted.complete(Unit)
        slowBlocker.await()
        Result.success(OpenClawDispatchResult("late", null, BotExpression.HAPPY, false))
      },
      onCommitBotTurn = { _, _, _, _ -> },
    )

    slowStarted.await()

    // Now user interjects targeting @Critic while Translator is still mid-turn
    orchestrator.startOrInterjectDiscussion(
      scope = backgroundScope,
      room = room,
      participants = participants,
      existingTranscript = listOf(
        CafeTranscriptEntry("u1", "user", "You", CafeSpeakerRole.USER, content = "Start drafting")
      ),
      userEntry = CafeTranscriptEntry("u2", "user", "You", CafeSpeakerRole.USER, content = "Switch focus @Critic"),
      targetBotId = "bot_b",
      onAbortSession = { sessionKey -> abortedSessions.add(sessionKey) },
      dispatchBotTurn = { speaker, _, _, _ ->
        secondRoundSpeakers.add(speaker.name)
        Result.success(OpenClawDispatchResult("${speaker.name} updated", null, BotExpression.UH_HUH, false))
      },
      onCommitBotTurn = { _, _, _, _ ->
        if (secondRoundSpeakers.size == 2) {
          secondRoundDone.complete(Unit)
        }
      },
    )

    secondRoundDone.await()
    delay(30L)

    assertEquals("agent:translator_bot_a:cafe_room_interject", participants[0].sourceBot?.effectiveCafeSessionKey("room_interject"))
    assertTrue(abortedSessions.contains("agent:translator_bot_a:cafe_room_interject"))
    assertEquals(listOf("Critic", "Translator"), secondRoundSpeakers.toList())
    assertEquals(CafeSessionStatus.COMPLETED, orchestrator.sessions.value[room.id]?.status)
  }

  @Test
  fun pauseAndStop_haltSubsequentTurnsCleanly() = runTest {
    val orchestrator = CafeOrchestrator(turnDelayMs = 20L)
    val participants = makeParticipants()
    val room = ChatRoom(
      id = "room_ctrl",
      title = "Control Room",
      topic = "Pause & Stop",
      botIds = listOf("bot_a", "bot_b"),
      maxPingPongTurns = 4,
    )
    val abortedSessions = CopyOnWriteArrayList<String>()
    val firstCommitted = CompletableDeferred<Unit>()
    val secondTurnStarted = CompletableDeferred<Unit>()
    val secondTurnBlocker = CompletableDeferred<Unit>()

    orchestrator.startOrInterjectDiscussion(
      scope = backgroundScope,
      room = room,
      participants = participants,
      existingTranscript = emptyList(),
      userEntry = CafeTranscriptEntry("u1", "user", "You", CafeSpeakerRole.USER, content = "Go"),
      dispatchBotTurn = { speaker, _, _, _ ->
        if (speaker.id == "bot_b") {
          secondTurnStarted.complete(Unit)
          secondTurnBlocker.await()
        }
        Result.success(OpenClawDispatchResult("${speaker.name} reply", null, BotExpression.HAPPY, false))
      },
      onCommitBotTurn = { _, speaker, _, _ ->
        if (speaker.id == "bot_a") firstCommitted.complete(Unit)
      },
    )

    firstCommitted.await()
    secondTurnStarted.await()

    orchestrator.pause(room.id) { abortedSessions.add(it) }
    val pausedSession = orchestrator.sessions.value[room.id]!!
    assertEquals(CafeSessionStatus.PAUSED, pausedSession.status)
    assertEquals(1, pausedSession.completedBotTurnsInRound)
    assertTrue(abortedSessions.contains("agent:critic_bot_b:cafe_room_ctrl"))

    orchestrator.stop(room.id) { abortedSessions.add(it) }
    val stoppedSession = orchestrator.sessions.value[room.id]!!
    assertEquals(CafeSessionStatus.STOPPED, stoppedSession.status)
    assertEquals(0, stoppedSession.completedBotTurnsInRound)
  }
}
