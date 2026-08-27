package cc.hosaka.okonomi.feature.phrases

import cc.hosaka.okonomi.db.BreakdownWord
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where a tap on a word of an example sentence goes, which is a
 * different question from whether the word is worth looking up
 * ([isBreakdownWordTappable], and [BreakdownWordTappableTest]).
 *
 * The one word with nowhere to go is the word the entry is about: a tap
 * searches for the dictionary form, so on 話's own examples 話 would
 * search 話 and return the reader to the page they are reading.
 */
class OpensSearchTest {

    private val hanashi = BreakdownWord(text = "話", reading = "はなし", entryId = 1_589_580L)

    private val gakkou = BreakdownWord(text = "学校", reading = "がっこう", entryId = 1_301_230L)

    private val words = setOf(hanashi, gakkou)

    @Test
    fun `a content word opens a search for its dictionary form`() {
        assertTrue(opensSearch(gakkou, words, wordBeingRead = "話"))
    }

    @Test
    fun `the word the entry is about opens nothing`() {
        assertFalse(opensSearch(hanashi, words, wordBeingRead = "話"))
    }

    /**
     * A word the dictionary rule already refused is refused here too,
     * whatever entry is on screen: を is a particle before it is
     * anything else.
     */
    @Test
    fun `a word the dictionary rule refused stays refused`() {
        val wo = BreakdownWord(text = "を", reading = null, entryId = 2_029_010L)

        assertFalse(opensSearch(wo, words, wordBeingRead = "話"))
    }

    /**
     * The whole point of deciding by spelling. Tatoeba links one 話 to
     * the entry on screen and, in the same sentence, another to some
     * other entry; they are the same characters in the same colour and
     * must behave the same way. Under an entry-id rule one of them taps
     * and the other does not, with nothing on screen to explain it.
     */
    @Test
    fun `two identical spellings behave alike however they were linked`() {
        val elsewhere = hanashi.copy(entryId = 2_222_222L)
        val both = setOf(hanashi, elsewhere)

        assertFalse(opensSearch(hanashi, both, wordBeingRead = "話"))
        assertFalse(opensSearch(elsewhere, both, wordBeingRead = "話"))
    }

    /**
     * The surface is not what a tap searches for, so it is not what
     * suppression compares either: 話し is 話's business and searching
     * it would land the reader back on this entry just the same.
     */
    @Test
    fun `an inflected spelling of the word being read opens nothing`() {
        val inflected = hanashi.copy(surface = "話し")
        val both = words + inflected

        assertFalse(opensSearch(inflected, both, wordBeingRead = "話"))
    }

    /**
     * A caller with no entry in hand — the tab's own default — suppresses
     * nothing rather than everything. No breakdown word is spelled with
     * the empty string, but stating it beats relying on that.
     */
    @Test
    fun `an absent word being read suppresses nothing`() {
        assertTrue(opensSearch(hanashi, words, wordBeingRead = ""))
        assertTrue(opensSearch(gakkou, words, wordBeingRead = ""))
    }
}
