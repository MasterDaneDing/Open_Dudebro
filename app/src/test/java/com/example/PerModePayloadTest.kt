package com.example

import com.example.data.openclaw.OpenClawClient
import com.example.model.CaptainContextDocument
import com.example.model.ContextDocStatus
import com.example.model.FileLinkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the per-mode payload caps added in the per-mode efficiency pass. */
class PerModePayloadTest {

  private val client = OpenClawClient()

  private fun doc(i: Int, text: String, ref: String = "content://doc/$i") = CaptainContextDocument(
    id = "d$i",
    displayName = "Doc_$i.pdf",
    uriOrPath = ref,
    linkType = FileLinkType.ANDROID_ATTACHMENT,
    extractedText = text,
    status = ContextDocStatus.READY,
    isPdf = true,
  )

  @Test
  fun captainDeck_isBoundedEvenWithManyLargeDocs() {
    // 40 x 50k chars used to be ~40 x 1.8k floor = 72k+ chars (the floor broke the 38k "total").
    val docs = (1..40).map { doc(it, "x".repeat(50_000)) }
    val deck = client.buildCaptainContextPromptDeck(docs)
    // Text budget 38k + per-doc headers/notes for 40 entries.
    assertTrue("deck was ${deck.length} chars", deck.length < 38_000 + 40 * 260)
    // Every doc is still listed by name so the model can say what's loaded.
    (1..40).forEach { assertTrue(deck.contains("Doc_$it.pdf")) }
    assertTrue(deck.contains("Text not inlined this turn"))
  }

  @Test
  fun captainDeck_shortDocsKeepAllText_longDocsGetLeftover() {
    val short = doc(1, "Clause 7: notice due November 15, 2026.")
    val long = doc(2, "y".repeat(60_000))
    val deck = client.buildCaptainContextPromptDeck(listOf(short, long), maxTotalChars = 10_000)
    assertTrue(deck.contains("Clause 7: notice due November 15, 2026."))
    val budgets = client.fairShareBudgets(listOf(short.extractedText.length, 60_000), 10_000)
    assertEquals(short.extractedText.length, budgets[0])
    assertEquals(10_000 - short.extractedText.length, budgets[1])
  }

  @Test
  fun captainDeck_dedupesSameSource() {
    val a = doc(1, "Budget Phase 2 = 18500", ref = "https://docs.google.com/d/X")
    val dupRef = a.copy(id = "d1b")
    val dupContent = a.copy(id = "d1c", uriOrPath = "content://other/copy")
    val deck = client.buildCaptainContextPromptDeck(listOf(a, dupRef, dupContent))
    assertEquals(1, Regex("Budget Phase 2 = 18500").findAll(deck).count())
    assertTrue(deck.contains("(1 item(s))"))
  }

  @Test
  fun fairShareBudgets_neverExceedTotal() {
    val lens = listOf(10, 500, 20_000, 3, 90_000, 7_000)
    val b = client.fairShareBudgets(lens, 25_000)
    assertTrue(b.sum() <= 25_000)
    lens.indices.forEach { assertTrue(b[it] <= lens[it]) }
  }

  @Test
  fun compactHistoryText_stripsInlineBase64_andCaps() {
    val blob = "data:image/png;base64," + "A".repeat(200_000)
    val out = client.compactHistoryText("Here you go:\n![shot]($blob)\nDone.", 2_000)
    assertFalse(out.contains("AAAA"))
    assertTrue(out.contains("[inline image omitted]"))
    assertTrue(out.contains("Done."))
    assertTrue(client.compactHistoryText("z".repeat(5_000), 2_000).length <= 2_002)
    assertEquals("plain", client.compactHistoryText("  plain  ", 2_000))
  }
}
