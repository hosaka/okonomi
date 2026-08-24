package cc.hosaka.okonomi.feature.search

import cc.hosaka.okonomi.db.TitleSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class TitleLineTest {
    @Test
    fun `joins segments with a comma separator`() {
        val line = titleLine(
            listOf(
                TitleSegment("食べる"),
                TitleSegment("たべる"),
            ),
        )

        assertEquals("食べる, たべる", line.text)
        assertEquals(emptyList(), line.highlights)
    }

    @Test
    fun `a highlight on the second segment lands after the separator`() {
        val line = titleLine(
            listOf(
                TitleSegment("食べる"),
                TitleSegment("たべる", highlight = 0..1),
            ),
        )

        // 食べる (3 chars) + ", " (2 chars) puts the reading at offset
        // 5; the highlight must cover たべ there, never the separator.
        assertEquals(listOf(5..6), line.highlights)
        assertEquals("たべ", line.text.substring(5, 7))
    }

    @Test
    fun `a partial highlight on the first segment keeps its offsets`() {
        val line = titleLine(
            listOf(
                TitleSegment("食べ物", highlight = 0..1),
                TitleSegment("たべもの"),
            ),
        )

        assertEquals(listOf(0..1), line.highlights)
    }

    @Test
    fun `a single fully highlighted segment covers exactly itself`() {
        val line = titleLine(
            listOf(
                TitleSegment("たべる", highlight = 0..2),
            ),
        )

        assertEquals("たべる", line.text)
        assertEquals(listOf(0..2), line.highlights)
    }

    @Test
    fun `multiple highlighted segments each map into the joined string`() {
        val line = titleLine(
            listOf(
                TitleSegment("食べる", highlight = 0..2),
                TitleSegment("たべる", highlight = 0..2),
            ),
        )

        assertEquals(listOf(0..2, 5..7), line.highlights)
    }
}
