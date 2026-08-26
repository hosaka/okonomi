package cc.hosaka.okonomi.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reading end of the stored breakdown format.
 *
 * [CANONICAL] is the contract between this module and dictgen, which
 * writes the column. dictgen's `StoredBreakdownTest` asserts that its
 * encoder PRODUCES this exact line from the same words this test
 * asserts it PARSES back — the two literals are the same string typed
 * twice, deliberately, in the spirit of `FormRowLabelsTest`.
 *
 * Without that pairing the format is defined twice and held together by
 * nothing: renaming `ENTRY_ID_PREFIX` on one side alone would leave the
 * writer and the reader disagreeing, every breakdown parsing to
 * nothing, the line quietly absent from the screen, and every test on
 * both sides green.
 */
class BreakdownTest {

    private companion object {
        /**
         * Every shape the column carries: a kanji word with a reading
         * and an entry, a kana word with an entry and no reading, a
         * kanji word the source gave a reading but no entry matched, a
         * kanji word with neither, a word the sentence inflects, and one
         * the sentence spells differently without inflecting it.
         */
        const val CANONICAL =
            "学校(がっこう)#1301230 で#2028980 どんな#1009040 三日間(みっかかん) 早く " +
                "食べる(たべる){食べない}#1358280 のです{んです}#2681000"
    }

    @Test
    fun `parses the canonical line dictgen writes`() {
        val words = Breakdown.words(CANONICAL)

        assertEquals(
            listOf("学校", "で", "どんな", "三日間", "早く", "食べる", "のです"),
            words.map { it.text },
        )
        assertEquals(
            listOf("がっこう", null, null, "みっかかん", null, "たべる", null),
            words.map { it.reading },
        )
        assertEquals(
            listOf(null, null, null, null, null, "食べない", "んです"),
            words.map { it.surface },
        )
        assertEquals(
            listOf(1301230L, 2028980L, 1009040L, null, null, 1358280L, 2681000L),
            words.map { it.entryId },
        )
    }

    @Test
    fun `a word the sentence writes as the dictionary does is written by that word alone`() {
        val word = Breakdown.words("学校(がっこう)#1301230").single()

        assertNull(word.surface, "an absent surface is not an empty one")
        assertEquals("学校", word.written, "the sentence writes the headword")
    }

    @Test
    fun `an inflected word is written by its surface`() {
        val word = Breakdown.words("食べる(たべる){食べない}#1358280").single()

        assertEquals("食べる", word.text, "a tap searches the dictionary form")
        assertEquals("食べない", word.written, "the sentence is located by the surface")
    }

    @Test
    fun `an entry id is never mistaken for part of the word or its reading`() {
        val word = Breakdown.words("食べる#1358280").single()

        assertEquals("食べる", word.text)
        assertNull(word.reading)
        // Read rather than discarded: it is what decides whether the
        // word can be tapped.
        assertEquals(1358280L, word.entryId)
    }

    @Test
    fun `empty braces mean no surface rather than an empty one`() {
        // dictgen does not write this either; a zero-length surface
        // would be found at every position of every sentence.
        val word = Breakdown.words("語{}#1").single()

        assertEquals("語", word.text)
        assertNull(word.surface)
        assertEquals("語", word.written)
    }

    @Test
    fun `empty parentheses mean no reading rather than an empty one`() {
        // dictgen does not write this, but this parser is an
        // independent reader of the column and must not depend on that:
        // an empty reading would render as a bare "語 ()".
        val word = Breakdown.words("語()#1").single()

        assertEquals("語", word.text)
        assertNull(word.reading)
        assertEquals(1L, word.entryId)
    }

    @Test
    fun `a word the format does not accept is skipped without dropping the sentence`() {
        val words = Breakdown.words("何(なに)#1 食(べ 食べる(たべる)#2")

        assertEquals(listOf("何", "食べる"), words.map { it.text })
    }

    @Test
    fun `an empty column yields no words`() {
        assertTrue(Breakdown.words("").isEmpty())
    }
}
