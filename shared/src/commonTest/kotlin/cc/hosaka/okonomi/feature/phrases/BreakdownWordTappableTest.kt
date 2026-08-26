package cc.hosaka.okonomi.feature.phrases

import cc.hosaka.okonomi.db.BreakdownPos
import cc.hosaka.okonomi.db.BreakdownWord
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a reader can tap in a sentence breakdown.
 *
 * The rule carries no list of words on purpose — Alex: "I don't wanna
 * maintain a list that might change when the dictionary entries are
 * fixed" — so it is made only of JMdict's own part-of-speech names, and
 * everything that could go wrong with it goes wrong over real tags. The
 * codes below are the ones the shipped dictionary actually carries for
 * these words — read off it, never invented — and
 * `BreakdownPosCodesTest` holds the rule's own sets to the same
 * standard. Measured over all 978,002 breakdown tokens the rule leaves
 * 64.9% of them tappable.
 */
class BreakdownWordTappableTest {

    private fun word(text: String, entryId: Long? = null) =
        BreakdownWord(text = text, reading = null, entryId = entryId)

    @Test
    fun `a content word linked to a content entry is tappable`() {
        val pos = BreakdownPos(
            byEntryId = mapOf(1_157_170L to listOf("vs-i", "vt", "vi", "suf", "aux-v")),
            byText = mapOf("為る" to listOf("vs-i", "vt", "vi", "suf", "aux-v")),
        )

        assertTrue(isBreakdownWordTappable(word("為る", 1_157_170L), pos))
    }

    /**
     * 為る and 有る both carry `aux-v` as a secondary sense. A rule that
     * filtered anything auxiliary would take する off every sentence in
     * the corpus — worse than the particles it was meant to fix.
     */
    @Test
    fun `an auxiliary sense does not make a common verb grammar`() {
        val pos = BreakdownPos(
            byEntryId = mapOf(1_296_400L to listOf("v5r-i", "vi", "aux-v")),
            byText = mapOf("有る" to listOf("v5r-i", "vi", "aux-v")),
        )

        assertTrue(isBreakdownWordTappable(word("有る", 1_296_400L), pos))
    }

    @Test
    fun `a particle linked to its own entry is not tappable`() {
        val pos = BreakdownPos(
            byEntryId = mapOf(2_029_010L to listOf("prt")),
            byText = mapOf("を" to listOf("prt")),
        )

        assertFalse(isBreakdownWordTappable(word("を", 2_029_010L), pos))
    }

    /**
     * The clause the linked entry cannot supply: Tatoeba's index sends
     * は to 葉, "leaf". は occurs 89,622 times in the corpus, so getting
     * this wrong is the most visible mistake the feature could make.
     */
    @Test
    fun `a particle mislinked to a homographic noun is caught by its text`() {
        val pos = BreakdownPos(
            byEntryId = mapOf(1_546_550L to listOf("n")),
            byText = mapOf("は" to listOf("n", "prt")),
        )

        assertFalse(isBreakdownWordTappable(word("は", 1_546_550L), pos))
    }

    /**
     * だ's real codes, and the reason this test exists in this shape.
     * It used to feed the rule `cop-da` — a code JMdict retired, which
     * the shipped dictionary carries zero times. It passed, and would
     * have gone on passing with `cop` and `aux-v` both deleted from the
     * rule while だ turned blue on 13,376 sentences. Codes taken from
     * fixtures rather than from the dictionary are how a rule made of
     * tag names rots quietly; `BreakdownPosCodesTest` is the other half
     * of the guard.
     */
    @Test
    fun `the copula is not tappable`() {
        val pos = BreakdownPos(
            byEntryId = mapOf(2_089_020L to listOf("aux-v", "cop")),
            byText = mapOf("だ" to listOf("aux-v", "cop")),
        )

        assertFalse(isBreakdownWordTappable(word("だ", 2_089_020L), pos))
    }

    /**
     * だ reaches the rule through the text clause as well, which is
     * what has to hold when the linked entry is one of the nouns
     * Tatoeba's index sends it to.
     */
    @Test
    fun `the copula is not tappable through its text alone`() {
        val pos = BreakdownPos(
            byEntryId = mapOf(1_234_567L to listOf("n")),
            byText = mapOf("だ" to listOf("n", "aux-v", "cop", "ctr", "pref")),
        )

        assertFalse(isBreakdownWordTappable(word("だ", 1_234_567L), pos))
    }

    /**
     * Nothing is spelled 為さい but the auxiliary, so the text clause
     * cannot see it; the linked entry can, and that is the whole reason
     * the first clause exists.
     */
    @Test
    fun `a grammar word spelled like nothing else is caught by its entry`() {
        val pos = BreakdownPos(
            byEntryId = mapOf(2_192_950L to listOf("aux-v")),
            byText = mapOf("為さい" to listOf("aux-v")),
        )

        assertFalse(isBreakdownWordTappable(word("為さい", 2_192_950L), pos))
    }

    /** 0.4% of the corpus's words resolved to no entry at all. */
    @Test
    fun `a word with no entry is decided by its text alone`() {
        val pos = BreakdownPos(byText = mapOf("三日間" to listOf("n")))

        assertTrue(isBreakdownWordTappable(word("三日間"), pos))
    }

    @Test
    fun `a particle with no entry is still caught by its text`() {
        val pos = BreakdownPos(byText = mapOf("を" to listOf("prt")))

        assertFalse(isBreakdownWordTappable(word("を"), pos))
    }

    /**
     * An entry the dictionary states no part of speech for says nothing
     * either way. Reading "no codes" as "every code is grammatical"
     * would silently make such words inert.
     */
    @Test
    fun `an entry with no stated part of speech does not read as grammar`() {
        val pos = BreakdownPos(byEntryId = mapOf(9L to emptyList()))

        assertTrue(isBreakdownWordTappable(word("謎", 9L), pos))
    }

    /**
     * The text clause is deliberately narrower than the entry clause:
     * asking it about auxiliaries would filter 為る, which carries
     * `aux-v` among its senses.
     */
    @Test
    fun `an auxiliary spelled like the word does not filter it`() {
        val pos = BreakdownPos(byText = mapOf("為る" to listOf("vs-i", "aux-v")))

        assertTrue(isBreakdownWordTappable(word("為る"), pos))
    }

    @Test
    fun `a word the dictionary knows nothing about stays tappable`() {
        assertTrue(isBreakdownWordTappable(word("ホゲホゲ", 7L), BreakdownPos()))
    }
}
