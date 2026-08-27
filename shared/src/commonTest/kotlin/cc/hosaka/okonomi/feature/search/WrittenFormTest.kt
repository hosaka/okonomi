package cc.hosaka.okonomi.feature.search

import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a long press on a result puts on the clipboard.
 *
 * The title is a list of segments, and some of them are readings that
 * belong ABOVE the segment before them rather than beside it. Joining
 * the list blindly pastes 食たべる, which looks almost right and is the
 * kind of thing nobody notices until it is in their notes.
 */
class WrittenFormTest {

    private fun hit(vararg segments: TitleSegment) = SearchHit(
        entryId = 1L,
        titleSegments = segments.toList(),
        traceLabels = emptyList(),
        senseLines = emptyList(),
        isCommon = true,
    )

    @Test
    fun `a reading set over the word is not part of what is copied`() {
        val taberu = hit(
            TitleSegment("食"),
            TitleSegment("た", readsPreviousSegment = true),
            TitleSegment("べる"),
        )
        assertEquals("食べる", taberu.writtenForm())
    }

    @Test
    fun `a word written entirely in kanji copies without its reading`() {
        val zoo = hit(
            TitleSegment("動物園"),
            TitleSegment("どうぶつえん", readsPreviousSegment = true),
        )
        assertEquals("動物園", zoo.writtenForm())
    }

    /**
     * A kana entry's own reading is NOT marked as reading-of-previous —
     * it is the word itself — so it must survive. This is the case that
     * a "drop every kana segment" shortcut would get wrong.
     */
    @Test
    fun `a kana word is copied whole`() {
        assertEquals("ひらがな", hit(TitleSegment("ひらがな")).writtenForm())
    }

    /**
     * A reading the entry does not claim for the form beside it is shown
     * NEXT TO the word rather than over it, and is not part of the word.
     * It is distinguished only by the flag, never by position.
     */
    @Test
    fun `a trailing reading segment shown beside the word is still dropped`() {
        val hit = hit(
            TitleSegment("上手"),
            TitleSegment("じょうず", readsPreviousSegment = true),
        )
        assertEquals("上手", hit.writtenForm())
    }
}
