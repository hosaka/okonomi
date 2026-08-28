package cc.hosaka.okonomi.feature.word

import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.ui.test.entryDetail
import cc.hosaka.okonomi.ui.test.entrySense
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which tabs an entry gets.
 *
 * A tab used to be drawn whatever the entry, holding a line saying it
 * had nothing — so a reader swiping through 本 met two dead screens.
 * Now the bar carries only what is there.
 *
 * Every case below is a real entry shape from the shipped dictionary,
 * not a hypothetical: a kana verb has no kanji, a noun does not
 * conjugate, and a rare word has no example sentences.
 */
class EntryTabAvailabilityTest {

    private fun verb(headword: String, reading: String, hasSentences: Boolean = true) = entryDetail(
        headword = headword,
        forms = listOf(EntryForm(text = headword, isCommon = true)),
        readings = listOf(EntryReading(text = reading, restrictions = emptyList(), isCommon = true)),
        senses = listOf(entrySense(posCodes = listOf("v1"), glosses = listOf("to eat"))),
        hasSentences = hasSentences,
    )

    private fun noun(headword: String, reading: String, hasSentences: Boolean = true) = entryDetail(
        headword = headword,
        forms = listOf(EntryForm(text = headword, isCommon = true)),
        readings = listOf(EntryReading(text = reading, restrictions = emptyList(), isCommon = true)),
        senses = listOf(entrySense(posCodes = listOf("n"))),
        hasSentences = hasSentences,
    )

    @Test
    fun `a kanji verb with sentences gets every tab`() {
        assertEquals(
            listOf(EntryTab.Word, EntryTab.Kanji, EntryTab.Forms, EntryTab.Phrases),
            availableTabs(verb("食べる", "たべる")),
        )
    }

    @Test
    fun `a word written in kana alone has no Kanji tab`() {
        assertEquals(
            listOf(EntryTab.Word, EntryTab.Forms, EntryTab.Phrases),
            availableTabs(verb("たべる", "たべる")),
        )
    }

    @Test
    fun `a noun has no Forms tab`() {
        assertEquals(
            listOf(EntryTab.Word, EntryTab.Kanji, EntryTab.Phrases),
            availableTabs(noun("本", "ほん")),
        )
    }

    @Test
    fun `an entry the corpus never uses has no Phrases tab`() {
        assertEquals(
            listOf(EntryTab.Word, EntryTab.Kanji, EntryTab.Forms),
            availableTabs(verb("食べる", "たべる", hasSentences = false)),
        )
    }

    /**
     * The floor. Word is unconditional, so there is always a tab to
     * show and the pager never has zero pages — which it cannot survive.
     */
    @Test
    fun `a kana noun with no sentences is left with the Word tab alone`() {
        assertEquals(
            listOf(EntryTab.Word),
            availableTabs(noun("あれ", "あれ", hasSentences = false)),
        )
    }

    /** Order is swipe order, and must not depend on which tabs survive. */
    @Test
    fun `the tabs that survive keep their declared order`() {
        val tabs = availableTabs(noun("本", "ほん"))
        assertEquals(tabs.sortedBy { it.ordinal }, tabs)
    }
}
