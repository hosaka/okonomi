package cc.hosaka.okonomi.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a sentence's words are.
 *
 * The breakdown holds dictionary forms and the sentence holds inflected
 * ones, so nothing in the stored data says which characters of
 * 父はそんなに果物を食べないんです。 are the 食べる it names — except the
 * surface Tatoeba states for each word, scanned for in order. This is
 * that scan, and it is what every reading and every tap target on the
 * Phrases tab hangs off: locate nothing and the sentence renders as
 * bare text with the readings still sitting unused in the column.
 */
class SentenceTokensTest {

    private fun word(text: String, surface: String? = null) =
        BreakdownWord(text = text, reading = null, surface = surface)

    private fun spans(japanese: String, words: List<BreakdownWord>): List<String> =
        locateTokens(japanese, words).map { japanese.substring(it.start, it.end) }

    @Test
    fun `a word the sentence writes unchanged is found where it stands`() {
        val japanese = "もっと果物を食べるべきです。"
        val tokens = locateTokens(japanese, listOf(word("果物"), word("を")))

        assertEquals(listOf(3, 5), tokens.map { it.start })
        assertEquals(listOf(5, 6), tokens.map { it.end })
        assertEquals(listOf("果物", "を"), tokens.map { japanese.substring(it.start, it.end) })
    }

    @Test
    fun `an inflected word is found by its surface and not by its headword`() {
        // 食べる is nowhere in this sentence. Searching for the headword
        // would lose the word and its reading with it.
        assertEquals(
            listOf("食べない"),
            spans("父は果物を食べない。", listOf(word("食べる", surface = "食べない"))),
        )
    }

    @Test
    fun `the same word twice is two spans rather than one found twice`() {
        val japanese = "食べる食べる。"
        val tokens = locateTokens(japanese, listOf(word("食べる"), word("食べる")))

        assertEquals(listOf(0 to 3, 3 to 6), tokens.map { it.start to it.end })
    }

    @Test
    fun `a word the sentence does not contain costs the sentence nothing`() {
        // Seventeen words of the shipped 978,002 end up here: an index
        // row naming a word the sentence does not carry, a variant
        // kanji, a joined form. Width differences are not among them —
        // those are folded, above.
        val located = locateTokens(
            "水を食べた。",
            listOf(word("水"), word("犬"), word("食べる", surface = "食べた")),
        )

        assertEquals(listOf("水", "食べた"), located.map { it.word.written })
        assertEquals(listOf(0, 2), located.map { it.start })
    }

    @Test
    fun `a word the scan has already passed is not searched for backwards`() {
        // The words arrive in sentence order, so a later word matching
        // earlier text is the index disagreeing with the sentence, not a
        // span. Taking it would put two words on the same characters.
        val located = locateTokens("水を水に。", listOf(word("を"), word("水")))

        assertEquals(listOf(1 to 2, 2 to 3), located.map { it.start to it.end })
    }

    /**
     * The index writes ２月 where the sentence writes 2月. Folding the
     * two widths together is what keeps the word — and, more to the
     * point, what keeps its characters claimed: left unlocated, the 月
     * and 日 inside it are free for a later one-character word to take,
     * which is a reading on the wrong characters rather than a missing
     * one.
     */
    @Test
    fun `a word the index writes full-width is found in a half-width sentence`() {
        val japanese = "2006年2月23日に生まれた。"
        val tokens = locateTokens(japanese, listOf(word("二月", surface = "２月")))

        assertEquals(listOf("2月"), tokens.map { japanese.substring(it.start, it.end) })
        assertEquals("２月", tokens.single().word.written, "the word keeps its own spelling")
    }

    @Test
    fun `folding never moves a span off the characters it names`() {
        // The fold is one character for one character, so an offset in
        // the folded sentence is an offset in the sentence itself.
        val japanese = "ＡＬＳにかかった。"
        val tokens = locateTokens(japanese, listOf(word("ALS", surface = "ALS")))

        assertEquals("ＡＬＳ", japanese.substring(tokens.single().start, tokens.single().end))
    }

    /**
     * The failure mode that matters. A word that is absent from the
     * sentence altogether takes no characters with it, so the words
     * after it are unaffected — including a repeated one, which must
     * still find its own occurrence rather than the earlier one.
     */
    @Test
    fun `a word lost between two occurrences of another does not move them`() {
        val japanese = "六日に食べて七日にも食べた。"
        val tokens = locateTokens(
            japanese,
            listOf(
                word("六日", surface = "６日"),
                word("食べる", surface = "食べて"),
                word("七日", surface = "７日"),
                word("食べる", surface = "食べた"),
            ),
        )

        // ６日 and ７日 are written 六日 and 七日 here, so neither folds
        // into anything: they are simply not in the sentence.
        assertEquals(
            listOf("食べて", "食べた"),
            tokens.map { japanese.substring(it.start, it.end) },
        )
        assertEquals(listOf(3 to 6, 10 to 13), tokens.map { it.start to it.end })
    }

    @Test
    fun `a sentence with no words at all locates nothing and still stands`() {
        assertTrue(locateTokens("ふうん。", emptyList()).isEmpty())
    }

    @Test
    fun `spans never overlap and never run backwards`() {
        val japanese = "彼は毎日学校で日本語を勉強しています。"
        val tokens = locateTokens(
            japanese,
            listOf(
                word("彼"), word("は"), word("毎日"), word("学校"), word("で"),
                word("日本語"), word("を"), word("勉強"), word("為る", surface = "して"),
                word("居る", surface = "います"),
            ),
        )

        assertEquals(10, tokens.size, "every word of this sentence is in it")
        tokens.zipWithNext { left, right ->
            assertTrue(left.end <= right.start, "${left.word.written} overruns ${right.word.written}")
        }
    }
}
