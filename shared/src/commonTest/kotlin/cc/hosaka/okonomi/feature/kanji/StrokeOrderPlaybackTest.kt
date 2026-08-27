package cc.hosaka.okonomi.feature.kanji

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The playback rule, which is the one part of the stroke-order animation
 * a host test can genuinely hold to account: Robolectric has no canvas,
 * so the ink itself is unobservable (see [StrokeOrderDiagram]). Every
 * visual decision the diagram makes follows from this function, so the
 * cases below are the cases the animation has.
 */
class StrokeOrderPlaybackTest {

    @Test
    fun `nothing is drawn before playback starts`() {
        assertEquals(0f, strokeFractionAt(progress = 0f, index = 0))
        assertEquals(0f, strokeFractionAt(progress = 0f, index = 4))
    }

    @Test
    fun `the stroke in flight is drawn to its share of its own length`() {
        assertEquals(0.5f, strokeFractionAt(progress = 0.5f, index = 0))
        assertEquals(0.25f, strokeFractionAt(progress = 3.25f, index = 3))
    }

    @Test
    fun `a stroke the playhead has passed stays whole`() {
        assertEquals(1f, strokeFractionAt(progress = 1f, index = 0))
        assertEquals(1f, strokeFractionAt(progress = 3.25f, index = 0))
        assertEquals(1f, strokeFractionAt(progress = 3.25f, index = 2))
    }

    @Test
    fun `a stroke the playhead has not reached is not started`() {
        assertEquals(0f, strokeFractionAt(progress = 3.25f, index = 4))
        assertEquals(0f, strokeFractionAt(progress = 3.25f, index = 9))
    }

    /**
     * The resting state, and the state playback ends in: progress equal
     * to the stroke count leaves every stroke whole, which is why there
     * is no separate "finished" render path.
     */
    @Test
    fun `at the end of playback every stroke of the character is whole`() {
        val strokeCount = 9
        val fractions = List(strokeCount) { strokeFractionAt(strokeCount.toFloat(), it) }
        assertEquals(List(strokeCount) { 1f }, fractions)
    }

    /**
     * Not reachable from the animation, which only ever runs forwards
     * from zero, but the function is what everything else trusts: a
     * negative share would draw a stroke backwards from its end.
     */
    @Test
    fun `progress behind a stroke never yields a negative share`() {
        assertEquals(0f, strokeFractionAt(progress = -2f, index = 0))
    }
}
