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
 * nothing: renaming `ENTRY_ID_PREFIX` — which is exactly what a
 * tappable-words increment would reach for — would leave the writer and
 * the reader disagreeing, every breakdown parsing to nothing, the line
 * quietly absent from the screen, and every test on both sides green.
 */
class BreakdownTest {

    private companion object {
        /**
         * Every shape the column carries: a kanji word with a reading
         * and an entry, a kana word with an entry and no reading, a
         * kanji word the source gave a reading but no entry matched,
         * and a kanji word with neither.
         */
        const val CANONICAL =
            "学校(がっこう)#1301230 で#2028980 どんな#1009040 三日間(みっかかん) 早く 食べる(たべる)#1358280"
    }

    @Test
    fun `parses the canonical line dictgen writes`() {
        val words = Breakdown.words(CANONICAL)

        assertEquals(
            listOf("学校", "で", "どんな", "三日間", "早く", "食べる"),
            words.map { it.text },
        )
        assertEquals(
            listOf("がっこう", null, null, "みっかかん", null, "たべる"),
            words.map { it.reading },
        )
    }

    @Test
    fun `an entry id is never mistaken for part of the word or its reading`() {
        val word = Breakdown.words("食べる#1358280").single()

        assertEquals("食べる", word.text)
        assertNull(word.reading)
    }

    @Test
    fun `empty parentheses mean no reading rather than an empty one`() {
        // dictgen does not write this, but this parser is an
        // independent reader of the column and must not depend on that:
        // an empty reading would render as a bare "語 ()".
        val word = Breakdown.words("語()#1").single()

        assertEquals("語", word.text)
        assertNull(word.reading)
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
