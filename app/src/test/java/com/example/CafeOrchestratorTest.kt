package com.example

import com.example.data.openclaw.CafeOrchestrator
import com.example.data.openclaw.OpenClawDispatchResult
import com.example.data.openclaw.OpenClawClient
import com.example.model.AgentToolPermission
import com.example.model.CafeEntryKind
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
import org.junit.Assert.assertFalse
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
  fun selectTranscriptWindow_pinsUserTaskAndLatestDeliverable_underBudget() {
    val client = OpenClawClient()
    val entries = buildList {
      add(CafeTranscriptEntry("u0", "user", "You", CafeSpeakerRole.USER, content = "Translate this 2000-word notice"))
      for (i in 1..40) {
        val isCritic = i % 2 == 0
        add(
          CafeTranscriptEntry(
            id = "e$i",
            speakerId = if (isCritic) "critic" else "translator",
            speakerName = if (isCritic) "Critic" else "Translator",
            role = CafeSpeakerRole.BOT,
            content = "turn $i content",
          )
        )
      }
    }
    // Translator speaking: must retain the user brief and the latest critic deliverable
    val forTranslator = client.selectTranscriptWindow(entries, "translator", "Translator", maxEntries = 14)
    assertEquals("u0", forTranslator.first().id)
    assertTrue("latest critic reply must be pinned", forTranslator.any { it.id == "e40" })
    assertTrue(forTranslator.size <= 14)
    // Newest turn is always last and order is chronological
    assertEquals("e40", forTranslator.last().id)
    assertEquals(forTranslator.sortedBy { it.id.removePrefix("e").removePrefix("u").toIntOrNull() ?: 0 }.map { it.id }, forTranslator.map { it.id })

    // Character budget is respected once something is filled
    val big = entries.map { it.copy(content = "x".repeat(2000)) }
    val budgeted = client.selectTranscriptWindow(big, "translator", "Translator", maxEntries = 14, maxChars = 5000)
    assertTrue(budgeted.sumOf { it.content.length } <= 6000)
  }

  @Test
  fun selectTranscriptWindow_pinsDeliverableOverLaterChat_andFallsBackToReview() {
    val client = OpenClawClient()
    val entries = listOf(
      CafeTranscriptEntry("u0", "user", "You", CafeSpeakerRole.USER, content = "Translate the notice"),
      CafeTranscriptEntry("d1", "translator", "Translator", CafeSpeakerRole.BOT, content = "THE TRANSLATION", kind = CafeEntryKind.DELIVERABLE),
      CafeTranscriptEntry("c1", "critic", "Critic", CafeSpeakerRole.BOT, content = "minor notes", kind = CafeEntryKind.REVIEW),
      CafeTranscriptEntry("p1", "proof", "Proofreader", CafeSpeakerRole.BOT, content = "chatter", kind = CafeEntryKind.CHAT),
    )
    val forCritic = client.selectTranscriptWindow(entries, "critic", "Critic", maxEntries = 14)
    assertTrue("deliverable must outrank later chat", forCritic.any { it.id == "d1" })

    // With no deliverable, the newest review is pinned
    val noDeliverable = entries.filter { it.kind != CafeEntryKind.DELIVERABLE }
    val forTrans = client.selectTranscriptWindow(noDeliverable, "translator", "Translator", maxEntries = 14)
    assertTrue("review must be pinned as fallback", forTrans.any { it.id == "c1" })
  }

  @Test
  fun transcriptExport_filenamesAreSafe() {
    val title = "Spirit & Idiom: 中文 / test"
    val safe = title.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').take(40).ifBlank { "room" }
    assertEquals("Spirit_Idiom_test", safe)
    assertEquals("room", "   ".replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "room" })
  }

  @Test
  fun presetReviewRooms_pairLeadWithCritic() {
    val proof = com.example.model.StarterBots.proofreadingRoom("u", "p1", "c1")
    assertEquals(listOf("p1", "c1"), proof.botIds)
    assertEquals(4, proof.maxPingPongTurns)
    assertTrue(proof.topic.contains("grammar"))

    val code = com.example.model.StarterBots.codingReviewRoom("u", "d1", "c1")
    assertEquals(listOf("d1", "c1"), code.botIds)
    assertTrue(code.topic.contains("edge cases"))
  }

  @Test
  fun bookmark_httpCheckKeepsOnlyWebUrls() {
    val urls = listOf("https://x.dev", "ftp://y", "http://z.io", "mailto:a@b.c")
    val kept = urls.filter { it.startsWith("http", ignoreCase = true) }
    assertEquals(listOf("https://x.dev", "http://z.io"), kept)
  }

  @Test
  fun openUrl_isARoutedBrowserCommand() {
    assertEquals("browser.open", com.example.data.openclaw.HostControlAction.OPEN_URL.command)
  }

  @Test
  fun takeoverActions_areDispatchable_controlCommands() {
    // Takeover must no longer short-circuit locally: both map to real control commands.
    assertEquals("takeover.start", com.example.data.openclaw.HostControlAction.TAKEOVER_START.command)
    assertEquals("takeover.release", com.example.data.openclaw.HostControlAction.TAKEOVER_RELEASE.command)
  }

  @Test
  fun translationRoom_pairsTranslatorWithCritic_underTurnBudget() {
    val room = com.example.model.StarterBots.translationRoom("u1", "bot_trans", "bot_crit")
    assertEquals(listOf("bot_trans", "bot_crit"), room.botIds)
    assertTrue(room.maxPingPongTurns in 3..6)
    assertTrue(room.topic.contains("idiomatic", ignoreCase = true))
    assertTrue(room.topic.contains("spirit", ignoreCase = true))
  }

  @Test
  fun toolScope_clampsTranslatorOutOfShell_andKeepsCodingShell() {
    val translatorCeiling = AgentToolPermission.resolveEnforced(
      BotFunctionPreset.TRANSLATION,
      AgentToolPermission.allKeys(),
    )
    assertFalse(AgentToolPermission.SHELL_EXEC.wireKey in translatorCeiling)
    assertTrue(AgentToolPermission.WORKSPACE_READ.wireKey in translatorCeiling)

    val coderCeiling = AgentToolPermission.resolveEnforced(
      BotFunctionPreset.CODING,
      AgentToolPermission.allKeys(),
    )
    assertTrue(AgentToolPermission.SHELL_EXEC.wireKey in coderCeiling)

    // User narrowing is respected
    val narrowed = AgentToolPermission.resolveEnforced(
      BotFunctionPreset.GENERAL,
      listOf(AgentToolPermission.WORKSPACE_READ.wireKey),
    )
    assertEquals(listOf(AgentToolPermission.WORKSPACE_READ.wireKey), narrowed)

    // Requesting only tools outside the ceiling still yields a usable set
    val outside = AgentToolPermission.resolveEnforced(
      BotFunctionPreset.TRANSLATION,
      listOf(AgentToolPermission.SHELL_EXEC.wireKey),
    )
    assertTrue(outside.isNotEmpty())
    assertFalse(AgentToolPermission.SHELL_EXEC.wireKey in outside)
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
  fun goalSatisfiedSentinel_stopsRoundEarlyWithCompletedStatus() = runTest {
    val orchestrator = CafeOrchestrator(turnDelayMs = 0L)
    val participants = makeParticipants()
    val room = ChatRoom(
      id = "room_done",
      title = "Tea Lounge",
      topic = "Translate and critique",
      botIds = listOf("bot_a", "bot_b"),
      maxPingPongTurns = 6,
    )
    val speakersOrder = CopyOnWriteArrayList<String>()
    val completedSignal = CompletableDeferred<Unit>()

    orchestrator.startOrInterjectDiscussion(
      scope = backgroundScope,
      room = room,
      participants = participants,
      existingTranscript = emptyList(),
      userEntry = CafeTranscriptEntry("u1", "user", "You", CafeSpeakerRole.USER, content = "Translate 'Hello'"),
      dispatchBotTurn = { speaker, _, _, _ ->
        speakersOrder.add(speaker.name)
        Result.success(
          OpenClawDispatchResult(
            replyText = "${speaker.name} final",
            parsedInstruction = null,
            expression = BotExpression.UH_HUH,
            takeoverSuggested = false,
            roomGoalSatisfied = speakersOrder.size == 2,
          )
        )
      },
      onCommitBotTurn = { _, _, _, _ -> completedSignal.complete(Unit) },
    )

    completedSignal.await()
    delay(30L)

    assertEquals(listOf("Translator", "Critic"), speakersOrder.toList())
    val session = orchestrator.sessions.value[room.id]!!
    assertEquals(CafeSessionStatus.COMPLETED, session.status)
    assertTrue(session.statusNote.contains("satisfied", ignoreCase = true))
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
