package cc.hosaka.okonomi.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val TRACK = 600f

private const val MIN_THUMB = 24f

/**
 * The scroll indicator's arithmetic.
 *
 * The overlay itself is a draw call with no semantics, so there is
 * nothing for a UI test to find; the geometry is where every mistake
 * that matters lives, and it is pure precisely so it can be pinned
 * here. The ends of the track are the interesting cases: a thumb that
 * stops a few pixels short of the bottom on the last page tells the
 * reader there is more list when there is not.
 */
class ScrollThumbTest {

    @Test
    fun `an empty list has no thumb`() {
        val thumb = scrollThumb(
            firstVisibleIndex = 0,
            visibleCount = 0,
            totalCount = 0,
            trackLength = TRACK,
            minThumbLength = MIN_THUMB,
        )

        assertFalse(thumb.isVisible)
    }

    /** The Phrases tab's sparse entries: two sentences, both on screen. */
    @Test
    fun `a list whose items all fit has no thumb`() {
        assertFalse(
            scrollThumb(
                firstVisibleIndex = 0,
                visibleCount = 2,
                totalCount = 2,
                trackLength = TRACK,
                minThumbLength = MIN_THUMB,
            ).isVisible,
        )
        // More visible than the list holds cannot happen, but a thumb
        // longer than its track would be the visible consequence.
        assertFalse(
            scrollThumb(
                firstVisibleIndex = 0,
                visibleCount = 5,
                totalCount = 2,
                trackLength = TRACK,
                minThumbLength = MIN_THUMB,
            ).isVisible,
        )
    }

    @Test
    fun `a list with no track has no thumb`() {
        assertFalse(
            scrollThumb(
                firstVisibleIndex = 0,
                visibleCount = 5,
                totalCount = 50,
                trackLength = 0f,
                minThumbLength = MIN_THUMB,
            ).isVisible,
        )
    }

    @Test
    fun `at the top the thumb starts at the top and is proportional`() {
        val thumb = scrollThumb(
            firstVisibleIndex = 0,
            visibleCount = 10,
            totalCount = 40,
            trackLength = TRACK,
            minThumbLength = MIN_THUMB,
        )

        assertEquals(0f, thumb.offset)
        // A quarter of the list is on screen, so a quarter of the track.
        assertEquals(TRACK / 4f, thumb.length)
    }

    /**
     * The case the whole function exists for. On the last page the
     * thumb has to touch the bottom of the track exactly — dividing the
     * position by the item count instead of by the last reachable index
     * leaves it a screenful short, which reads as "there is more".
     */
    @Test
    fun `on the last page the thumb reaches the bottom exactly`() {
        val visible = 10
        val total = 40
        val thumb = scrollThumb(
            firstVisibleIndex = total - visible,
            visibleCount = visible,
            totalCount = total,
            trackLength = TRACK,
            minThumbLength = MIN_THUMB,
        )

        assertEquals(TRACK, thumb.offset + thumb.length)
    }

    @Test
    fun `halfway down the list the thumb is halfway down its travel`() {
        val visible = 10
        val total = 40
        val thumb = scrollThumb(
            firstVisibleIndex = (total - visible) / 2,
            visibleCount = visible,
            totalCount = total,
            trackLength = TRACK,
            minThumbLength = MIN_THUMB,
        )

        assertEquals((TRACK - thumb.length) / 2f, thumb.offset)
    }

    /** Four hundred search results would otherwise draw a speck. */
    @Test
    fun `a very long list still gets a thumb long enough to see`() {
        val thumb = scrollThumb(
            firstVisibleIndex = 0,
            visibleCount = 8,
            totalCount = 400,
            trackLength = TRACK,
            minThumbLength = MIN_THUMB,
        )

        assertEquals(MIN_THUMB, thumb.length)
        assertTrue(thumb.length > TRACK * 8f / 400f, "the minimum must have been applied")
    }

    @Test
    fun `the minimum never pushes the thumb past a short track`() {
        val shortTrack = 10f
        val thumb = scrollThumb(
            firstVisibleIndex = 400,
            visibleCount = 8,
            totalCount = 400,
            trackLength = shortTrack,
            minThumbLength = MIN_THUMB,
        )

        assertEquals(shortTrack, thumb.length)
        assertEquals(0f, thumb.offset)
    }

    /**
     * A lazy list reports its first visible index from a layout that
     * may be a frame behind the item count it is measured against.
     */
    @Test
    fun `an index past the end of its travel is clamped to the bottom`() {
        val thumb = scrollThumb(
            firstVisibleIndex = 10_000,
            visibleCount = 10,
            totalCount = 40,
            trackLength = TRACK,
            minThumbLength = MIN_THUMB,
        )

        assertEquals(TRACK, thumb.offset + thumb.length)
    }

    @Test
    fun `a negative index is clamped to the top`() {
        val thumb = scrollThumb(
            firstVisibleIndex = -3,
            visibleCount = 10,
            totalCount = 40,
            trackLength = TRACK,
            minThumbLength = MIN_THUMB,
        )

        assertEquals(0f, thumb.offset)
    }
}
