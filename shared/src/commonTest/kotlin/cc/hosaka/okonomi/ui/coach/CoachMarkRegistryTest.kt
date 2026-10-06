package cc.hosaka.okonomi.ui.coach

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Two Search screens are composed at once while one is pushed over the
 * other or popped off it. These pin that neither can take the other's
 * report or request with it when it leaves.
 */
class CoachMarkRegistryTest {

    private val root = Any()
    private val pushed = Any()
    private val rootBounds = Rect(10f, 20f, 110f, 76f)
    private val pushedBounds = Rect(30f, 20f, 130f, 76f)

    @Test
    fun `the newest live report of a target wins`() {
        val registry = CoachMarkRegistry()
        registry.report(CoachTarget.Filters, root, rootBounds)
        registry.report(CoachTarget.Filters, pushed, pushedBounds)

        assertEquals(pushedBounds, registry.targets[CoachTarget.Filters])
    }

    @Test
    fun `the first reporter leaving keeps the second's report`() {
        val registry = CoachMarkRegistry()
        registry.report(CoachTarget.Filters, root, rootBounds)
        registry.report(CoachTarget.Filters, pushed, pushedBounds)

        registry.remove(CoachTarget.Filters, root)

        assertEquals(pushedBounds, registry.targets[CoachTarget.Filters])
    }

    /**
     * The race the per-owner reports exist for: the screen that stays does
     * not move, so it never reports again, and must not lose its target
     * when the one that came and went withdraws.
     */
    @Test
    fun `the second reporter leaving restores the first's report`() {
        val registry = CoachMarkRegistry()
        registry.report(CoachTarget.Filters, root, rootBounds)
        registry.report(CoachTarget.Filters, pushed, pushedBounds)

        registry.remove(CoachTarget.Filters, pushed)

        assertEquals(rootBounds, registry.targets[CoachTarget.Filters])
    }

    @Test
    fun `the last reporter leaving forgets the target`() {
        val registry = CoachMarkRegistry()
        registry.report(CoachTarget.Filters, root, rootBounds)

        registry.remove(CoachTarget.Filters, root)

        assertNull(registry.targets[CoachTarget.Filters])
    }

    @Test
    fun `a request lasts until every owner has withdrawn its own`() {
        val registry = CoachMarkRegistry()
        registry.want(root)
        registry.want(pushed)

        registry.unwant(pushed)
        assertTrue(registry.wanted, "the root's request must survive the pushed screen leaving")

        registry.unwant(root)
        assertFalse(registry.wanted)
    }

    @Test
    fun `withdrawing a request that was never made clears nothing`() {
        val registry = CoachMarkRegistry()
        registry.want(root)

        registry.unwant(pushed)
        registry.unwant(pushed)

        assertTrue(registry.wanted)
    }

    @Test
    fun `asking twice is asking once`() {
        val registry = CoachMarkRegistry()
        registry.want(root)
        registry.want(root)

        registry.unwant(root)

        assertFalse(registry.wanted)
    }

    @Test
    fun `targets are handed back relative to the host`() {
        val registry = CoachMarkRegistry()
        registry.report(CoachTarget.SearchField, root, rootBounds)

        registry.moveOrigin(Offset(10f, 15f))

        assertEquals(Rect(0f, 5f, 100f, 61f), registry.targets[CoachTarget.SearchField])
    }
}
