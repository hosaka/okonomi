package cc.hosaka.okonomi.feature.search

import kotlin.test.Test
import kotlin.test.assertEquals

class GlossHighlightsTest {
    @Test
    fun `a token at the start of the line is highlighted`() {
        val line = "eat a meal"

        assertEquals(listOf(0 until 3), glossHighlights(line, listOf("eat")))
    }

    @Test
    fun `a token at the end of the line is highlighted`() {
        val line = "to eat"

        assertEquals(listOf(3 until 6), glossHighlights(line, listOf("eat")))
        assertEquals("eat", line.substring(3, 6))
    }

    @Test
    fun `every occurrence of a repeated token is highlighted`() {
        val line = "to eat, to eat well"

        assertEquals(listOf(3 until 6, 11 until 14), glossHighlights(line, listOf("eat")))
    }

    @Test
    fun `a token inside a longer word is not highlighted`() {
        // FTS matches whole tokens, so a partial highlight would
        // promise a match the search never made.
        assertEquals(emptyList(), glossHighlights("a creature of habit", listOf("eat")))
        assertEquals(emptyList(), glossHighlights("eating while walking", listOf("eat")))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf(0 until 3), glossHighlights("Eat well", listOf("eat")))
        assertEquals(listOf(0 until 3), glossHighlights("eat well", listOf("EAT")))
    }

    @Test
    fun `every query token is highlighted`() {
        val line = "to eat"

        assertEquals(listOf(0 until 2, 3 until 6), glossHighlights(line, listOf("to", "eat")))
    }

    @Test
    fun `punctuation and hyphens bound a word`() {
        assertEquals(listOf(0 until 3), glossHighlights("eat-in only", listOf("eat")))
        assertEquals(listOf(6 until 9), glossHighlights("food (eat)", listOf("eat")))
    }

    @Test
    fun `no tokens means no highlighting`() {
        // The Japanese path shows its match in the title line instead.
        assertEquals(emptyList(), glossHighlights("to eat", emptyList()))
    }

    @Test
    fun `a line carrying only some of the tokens is not highlighted`() {
        // Ranking refuses to count such a gloss (FTS matches a row only
        // when every token is in it), so highlighting must refuse too —
        // otherwise 遣る's "to do" comes back with "to" highlighted for
        // a search that never matched it.
        assertEquals(emptyList(), glossHighlights("to do", listOf("to", "eat")))
        assertEquals(emptyList(), glossHighlights("eat", listOf("to", "eat")))
        assertEquals(listOf(0 until 2, 3 until 6), glossHighlights("to eat", listOf("to", "eat")))
    }

    @Test
    fun `the bullet offset shifts every range`() {
        val line = senseLine("to eat", listOf("eat"))

        assertEquals("- to eat", line.text)
        // "eat" sits at 3 in the raw gloss and at 5 behind the bullet.
        assertEquals(listOf(5..7), line.highlights)
        assertEquals("eat", line.text.substring(5, 8))
    }

    @Test
    fun `a sense line without tokens keeps the bullet and no highlights`() {
        val line = senseLine("食べ物", emptyList())

        assertEquals("- 食べ物", line.text)
        assertEquals(emptyList(), line.highlights)
    }

    @Test
    fun `every occurrence shifts, not just the first`() {
        val line = senseLine("eat, eat", listOf("eat"))

        assertEquals("- eat, eat", line.text)
        assertEquals(listOf(2..4, 7..9), line.highlights)
        assertEquals("eat", line.text.substring(7, 10))
    }
}
