package cc.hosaka.okonomi.feature.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull

class TextLookupsTest {

    @Test
    fun `selected Japanese text is looked up as it is`() {
        assertEquals("食べた", selectionQuery("食べた"))
    }

    @Test
    fun `each whitespace run becomes one space and the ends are trimmed`() {
        // A line break, a tab and the ideographic space among them.
        assertEquals("eat food", selectionQuery("\n eat \t　food \n"))
    }

    @Test
    fun `whitespace between two Japanese characters is removed`() {
        // A line break inside a sentence, and an ideographic space.
        assertEquals("食べた", selectionQuery("食べ\nた"))
        assertEquals("食べた", selectionQuery("食べ　た"))
        // Katakana and a supplementary-plane kanji count as Japanese too.
        assertEquals("テスト𠮷野家", selectionQuery("テスト\r\n𠮷野家"))
    }

    @Test
    fun `whitespace beside anything that is not Japanese stays one space`() {
        assertEquals("eat 食べた", selectionQuery("eat\n食べた"))
        assertEquals("食べた food", selectionQuery("食べた \t food"))
        // Japanese punctuation is not kana or han, as Search counts them.
        assertEquals("食べた。 飲んだ", selectionQuery("食べた。\n飲んだ"))
    }

    @Test
    fun `format characters are dropped wherever they are`() {
        // Zero-width space, soft hyphen, byte order mark, bidi marks.
        assertEquals("食べた", selectionQuery("\uFEFF食\u200Bべ\u00ADた\u200E"))
        assertEquals("eat food", selectionQuery("\u200Feat\u00AD \u200B food\u202C"))
        // Inside a whitespace run between Japanese, the run is still removed.
        assertEquals("食べた", selectionQuery("食べ\n\u200Bた"))
        assertNull(selectionQuery("\u200B\uFEFF"))
    }

    @Test
    fun `blank text is nothing to look up`() {
        listOf("", "  \n", "\t", "　").forEach { text ->
            assertNull(selectionQuery(text), "\"$text\"")
        }
    }

    @Test
    fun `a huge selection is cut to the first characters of the query`() {
        val text = "食べた".repeat(3_334).take(10_000)

        assertEquals(text.take(SELECTION_QUERY_MAX_LENGTH), selectionQuery(text))
    }

    @Test
    fun `the cap counts the tidied query rather than the raw selection`() {
        val text = " \n".repeat(500) + "あ".repeat(300)

        assertEquals("あ".repeat(SELECTION_QUERY_MAX_LENGTH), selectionQuery(text))
    }

    @Test
    fun `the cap never splits a surrogate pair`() {
        // 𠮷 is one character in two UTF-16 units; the pair starting at the
        // last unit the cap allows is dropped whole.
        val text = "a".repeat(SELECTION_QUERY_MAX_LENGTH - 1) + "𠮷".repeat(10)

        assertEquals("a".repeat(SELECTION_QUERY_MAX_LENGTH - 1), selectionQuery(text))
    }

    @Test
    fun `a pair that fits whole is kept`() {
        val text = "a".repeat(SELECTION_QUERY_MAX_LENGTH - 2) + "𠮷".repeat(10)

        assertEquals("a".repeat(SELECTION_QUERY_MAX_LENGTH - 2) + "𠮷", selectionQuery(text))
    }

    @Test
    fun `a cut landing on a space leaves no trailing space`() {
        val text = "a".repeat(SELECTION_QUERY_MAX_LENGTH - 1) + " " + "b".repeat(10)

        assertEquals("a".repeat(SELECTION_QUERY_MAX_LENGTH - 1), selectionQuery(text))
    }

    @Test
    fun `text offered before anything listens is delivered once something does`() = runTest {
        val lookups = TextLookups()

        assertTrue(lookups.lookUp(" 食べた\n"))

        assertEquals(TextLookup("食べた", fromOkonomi = false), lookups.requests.first())
    }

    @Test
    fun `a lookup from okonomi itself is queued as one`() = runTest {
        val lookups = TextLookups()

        assertTrue(lookups.lookUp("食べた", fromOkonomi = true))

        assertEquals(TextLookup("食べた", fromOkonomi = true), lookups.requests.first())
    }

    @Test
    fun `of two lookups queued before anything listens only the newer is delivered`() = runTest {
        val lookups = TextLookups()

        lookups.lookUp("飲む")
        lookups.lookUp("食べた")

        assertEquals(TextLookup("食べた", fromOkonomi = false), lookups.requests.first())
        assertNull(withTimeoutOrNull(1.seconds) { lookups.requests.first() })
    }

    @Test
    fun `blank text is refused and queues nothing`() = runTest {
        val lookups = TextLookups()

        assertFalse(lookups.lookUp("  \n"))

        // Virtual time: the wait costs nothing, and the queue is conflated,
        // so a blank queued here would be the one thing first() could see.
        assertNull(withTimeoutOrNull(1.seconds) { lookups.requests.first() })
    }
}
