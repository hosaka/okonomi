package cc.hosaka.okonomi.feature.search

import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment.Highlight
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a result row's title is made of, and where the match survives in
 * it. The row used to spell the form and the reading out side by side
 * (`食べる, たべる`) with the matched substring coloured; it now folds
 * them into one furigana title, and the highlight has to come through
 * that fold intact.
 *
 * The offsets are the whole risk: a highlight is stated against either
 * the written form or the reading, and both have to land on the right
 * runs of a title that is neither.
 */
class TitleFuriganaTest {

    private fun highlighted(segments: List<FuriganaSegment>) =
        segments.filter { it.highlight != null }.map { it.text }

    @Test
    fun `a form and its reading become one furigana title`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("食べる"),
                TitleSegment("たべる", readsPreviousSegment = true),
            ),
        )

        assertEquals(
            listOf(
                FuriganaSegment("食", "た"),
                FuriganaSegment("べる"),
            ),
            segments,
        )
    }

    /**
     * The kana query case, and the reason the fold cannot just drop the
     * reading segment: たべ matched the *reading*, and its first
     * character is now ruby over 食. Highlighting the unit is the finest
     * granularity an inline ruby box has; べ beside it stays exact.
     */
    @Test
    fun `a match on the reading highlights the ruby unit and the kana exactly`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("食べる"),
                TitleSegment("たべる", highlight = 0..1, readsPreviousSegment = true),
            ),
        )

        assertEquals(
            listOf(
                FuriganaSegment("食", "た", Highlight.Whole),
                FuriganaSegment("べ", highlight = Highlight.Whole),
                FuriganaSegment("る"),
            ),
            segments,
        )
    }

    @Test
    fun `a match on the written form highlights the kanji it covers`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("食べる", highlight = 0..0),
                TitleSegment("たべる", readsPreviousSegment = true),
            ),
        )

        assertEquals(listOf("食"), highlighted(segments))
    }

    @Test
    fun `a match that stops inside a kana run splits it`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("食べる", highlight = 0..1),
                TitleSegment("たべる", readsPreviousSegment = true),
            ),
        )

        assertEquals(
            listOf(
                FuriganaSegment("食", "た", Highlight.Whole),
                FuriganaSegment("べ", highlight = Highlight.Whole),
                FuriganaSegment("る"),
            ),
            segments,
        )
    }

    @Test
    fun `an entry with no kanji form is plain text with its match`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("たべる", highlight = 0..1, readsPreviousSegment = true),
            ),
        )

        assertEquals(
            listOf(
                FuriganaSegment("たべ", highlight = Highlight.Whole),
                FuriganaSegment("る"),
            ),
            segments,
        )
    }

    /**
     * A match that covers a whole half of a run has matched the run:
     * 大人 reads おとな and nothing else, so both halves light.
     */
    @Test
    fun `a match covering a whole-word reading lights the unit`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("大人"),
                TitleSegment("おとな", highlight = 0..2, readsPreviousSegment = true),
            ),
        )

        assertEquals(listOf(FuriganaSegment("大人", "おとな", Highlight.Whole)), segments)
    }

    /**
     * Alex's report, and the reason a run's highlight is not a boolean.
     * Searching そうさい returned 相殺関税 with 関税 and かんぜい lit as
     * well, because the whole word is one undivided run. Only the kana
     * that matched may light: which kanji take そうさい is exactly what
     * [cc.hosaka.okonomi.ui.furigana.alignReading] declined to guess,
     * and lighting 相殺 would be that guess wearing a different hat.
     */
    @Test
    fun `a match on part of a whole-word reading lights only that part of the ruby`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("相殺関税"),
                TitleSegment("そうさいかんぜい", highlight = 0..3, readsPreviousSegment = true),
            ),
        )

        assertEquals(
            listOf(FuriganaSegment("相殺関税", "そうさいかんぜい", Highlight.PartOfReading(0..3))),
            segments,
        )
    }

    /**
     * The same rule from the other side: a kanji query that reaches only
     * part of an undivided run lights those characters and leaves the
     * ruby alone, because nothing says which of the reading they take.
     */
    @Test
    fun `a match on part of the written form lights only those characters`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("相殺関税", highlight = 0..1),
                TitleSegment("そうさいかんぜい", readsPreviousSegment = true),
            ),
        )

        assertEquals(
            listOf(FuriganaSegment("相殺関税", "そうさいかんぜい", Highlight.PartOfText(0..1))),
            segments,
        )
    }

    @Test
    fun `a deinflected hit carries no highlight at all`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("食べる"),
                TitleSegment("たべる", readsPreviousSegment = true),
            ),
        )

        assertEquals(emptyList(), highlighted(segments))
    }

    /**
     * A reading the entry does not state for the form beside it is
     * shown beside it, exactly as the row always showed two texts it
     * could not pair. 空オケ's カラオケ is `re_nokanji`: set over the
     * kanji it would read 空 as カラ, which the entry denies — the
     * kanji form reads からオケ.
     */
    @Test
    fun `a reading the entry does not claim for the form stands beside it`() {
        val segments = titleFurigana(
            listOf(
                TitleSegment("空オケ"),
                TitleSegment("カラオケ", highlight = 0..3),
            ),
        )

        assertEquals(
            listOf(
                FuriganaSegment("空オケ"),
                FuriganaSegment(", "),
                FuriganaSegment("カラオケ", highlight = Highlight.Whole),
            ),
            segments,
        )
    }

    @Test
    fun `a row with nothing to title has no segments`() {
        assertEquals(emptyList(), titleFurigana(emptyList()))
    }
}
