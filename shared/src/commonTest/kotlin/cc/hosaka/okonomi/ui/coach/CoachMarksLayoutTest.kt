package cc.hosaka.okonomi.ui.coach

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import kotlin.math.max
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the marks go, as numbers. At density 1 a pixel is a dp, so every
 * figure below reads as dp. The target rects are the shell's real shapes:
 * a full-width pill under the top edge, the 56dp button 16dp in from the
 * bottom-end of the content, and the middle of three tabs in an 64dp bar
 * (portrait) or the second item of a 96dp rail (landscape).
 */
class CoachMarksLayoutTest {

    private val density = Density(1f)

    private val portrait = Size(360f, 640f)
    private val portraitTargets = mapOf(
        CoachTarget.SearchField to Rect(0f, 4f, 360f, 60f),
        CoachTarget.Filters to Rect(288f, 504f, 344f, 560f),
        // The heart icon, not the whole tab item: that is what Favourites reports.
        CoachTarget.Favourites to Rect(168f, 592f, 192f, 616f),
    )

    private val landscape = Size(640f, 360f)
    private val landscapeTargets = mapOf(
        CoachTarget.SearchField to Rect(96f, 4f, 640f, 60f),
        CoachTarget.Filters to Rect(568f, 288f, 624f, 344f),
        CoachTarget.Favourites to Rect(36f, 128f, 60f, 152f),
    )

    @Test
    fun `the marks are visible only on the selected search root with the keyboard down`() {
        for (searchSelected in listOf(true, false)) {
            for (atRoot in listOf(true, false)) {
                for (wanted in listOf(true, false)) {
                    for (imeVisible in listOf(true, false)) {
                        assertEquals(
                            searchSelected && atRoot && wanted && !imeVisible,
                            coachMarksVisible(searchSelected, atRoot, wanted, imeVisible),
                            "selected=$searchSelected root=$atRoot wanted=$wanted ime=$imeVisible",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `search wants the marks only empty and idle at its root with the menu closed`() {
        for (empty in listOf(true, false)) {
            for (idle in listOf(true, false)) {
                for (isRoot in listOf(true, false)) {
                    for (filtersOpen in listOf(true, false)) {
                        assertEquals(
                            empty && idle && isRoot && !filtersOpen,
                            coachMarksWanted(empty, idle, isRoot, filtersOpen),
                            "empty=$empty idle=$idle root=$isRoot open=$filtersOpen",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `portrait arrows land on each target`() {
        val layout = assertNotNull(
            layoutCoachMarks(portraitTargets, portrait, density, CoachNavigation.BottomBar),
        )

        assertEquals(CoachNoteStyle.Large, layout.style)
        assertEquals(CoachTarget.entries.toSet(), layout.marks.map { it.target }.toSet())
        layout.marks.forEach { mark ->
            assertLandsOn(mark, portraitTargets.getValue(mark.target))
        }
    }

    @Test
    fun `landscape arrows land on each target`() {
        val layout = assertNotNull(
            layoutCoachMarks(landscapeTargets, landscape, density, CoachNavigation.Rail),
        )

        assertEquals(CoachNoteStyle.Large, layout.style)
        assertEquals(CoachTarget.entries.toSet(), layout.marks.map { it.target }.toSet())
        layout.marks.forEach { mark ->
            assertLandsOn(mark, landscapeTargets.getValue(mark.target))
        }
    }

    @Test
    fun `the landscape favourites arrow points left onto the rail item`() {
        val layout = assertNotNull(
            layoutCoachMarks(landscapeTargets, landscape, density, CoachNavigation.Rail),
        )
        val item = landscapeTargets.getValue(CoachTarget.Favourites)
        val arrow = layout.marks.single { it.target == CoachTarget.Favourites }.arrow

        assertTrue(arrow.end.x in (item.right + 6f)..(item.right + 8f), "arrow ends at ${arrow.end}, rail item $item")
        assertTrue(arrow.end.y in item.top..item.bottom, "arrow ends at ${arrow.end}, beside nothing on $item")
        assertTrue(arrow.start.x > arrow.end.x, "arrow from ${arrow.start} to ${arrow.end} does not point left")
    }

    @Test
    fun `portrait arrows point toward their targets`() {
        val layout = assertNotNull(
            layoutCoachMarks(portraitTargets, portrait, density, CoachNavigation.BottomBar),
        )
        val byTarget = layout.marks.associateBy { it.target }

        val field = byTarget.getValue(CoachTarget.SearchField).arrow
        assertTrue(field.end.y < field.start.y, "the field's arrow must point up: $field")
        assertEquals(360f * 0.35f, field.end.x, 0.01f, "the field's arrow lands about a third of the way across")
        val filters = byTarget.getValue(CoachTarget.Filters).arrow
        assertTrue(filters.end.y > filters.start.y, "the filters arrow must point down: $filters")
        val favourites = byTarget.getValue(CoachTarget.Favourites).arrow
        assertTrue(favourites.end.y > favourites.start.y, "the favourites arrow must point down: $favourites")
    }

    /**
     * The shaft's last stretch and the chevron both run along the direction
     * the arrow arrives from, so the head sits on the line instead of
     * tilting off it with the bow (seen on a Pixel 6: a bent filters head,
     * and a Favourites arrow sliding sideways along the bar).
     */
    @Test
    fun `every arrow arrives straight at its target with the head on the line`() {
        val expected = mapOf(
            CoachNavigation.BottomBar to mapOf(
                CoachTarget.SearchField to Offset(0f, -1f),
                CoachTarget.Filters to Offset(0f, 1f),
                CoachTarget.Favourites to Offset(0f, 1f),
            ),
            CoachNavigation.Rail to mapOf(
                CoachTarget.SearchField to Offset(0f, -1f),
                CoachTarget.Filters to Offset(1f, 0f),
                CoachTarget.Favourites to Offset(-1f, 0f),
            ),
        )
        for ((navigation, approaches) in expected) {
            val (size, targets) = when (navigation) {
                CoachNavigation.BottomBar -> portrait to portraitTargets
                CoachNavigation.Rail -> landscape to landscapeTargets
            }
            val layout = assertNotNull(layoutCoachMarks(targets, size, density, navigation))
            assertEquals(3, layout.marks.size, "$navigation keeps all three marks")
            for (mark in layout.marks) {
                val approach = approaches.getValue(mark.target)
                val arrow = mark.arrow
                val lastStretch = unit(arrow.end - arrow.control2)
                assertEquals(approach.x, lastStretch.x, 0.001f, "$navigation ${mark.target} arrives along $approach")
                assertEquals(approach.y, lastStretch.y, 0.001f, "$navigation ${mark.target} arrives along $approach")
                val head = unit((arrow.end - arrow.headLeft) + (arrow.end - arrow.headRight))
                assertEquals(approach.x, head.x, 0.001f, "$navigation ${mark.target} head points along $approach")
                assertEquals(approach.y, head.y, 0.001f, "$navigation ${mark.target} head points along $approach")
            }
        }
    }

    @Test
    fun `no two notes overlap and every note is on screen`() {
        for ((targets, size, navigation) in listOf(
            Triple(portraitTargets, portrait, CoachNavigation.BottomBar),
            Triple(landscapeTargets, landscape, CoachNavigation.Rail),
        )) {
            val layout = assertNotNull(layoutCoachMarks(targets, size, density, navigation))
            assertNoOverlap(layout, targets, size, "$navigation")
        }
    }

    @Test
    fun `notes are rotated by their fixed tilts`() {
        val layout = assertNotNull(
            layoutCoachMarks(portraitTargets, portrait, density, CoachNavigation.BottomBar),
        )
        val rotations = layout.marks.associate { it.target to it.rotation }

        assertEquals(
            mapOf(CoachTarget.SearchField to -3f, CoachTarget.Filters to 4f, CoachTarget.Favourites to -2f),
            rotations,
        )
    }

    /**
     * The first step down is the type size, not a mark: a Favourites note
     * that fits only at the medium size keeps all three marks.
     */
    @Test
    fun `marks that do not fit at the large size step down before any is dropped`() {
        val layout = assertNotNull(
            layoutCoachMarks(portraitTargets, portrait, density, CoachNavigation.BottomBar) { target, style, width ->
                if (target == CoachTarget.Favourites && style == CoachNoteStyle.Large) {
                    Size(width, 2_000f)
                } else {
                    Size(width, 60f)
                }
            },
        )

        assertEquals(CoachNoteStyle.Medium, layout.style)
        assertEquals(CoachTarget.entries.toSet(), layout.marks.map { it.target }.toSet())
    }

    /**
     * A Favourites note this tall pushes the filters note up into the
     * field's: every note is still on screen, so only the overlap rule
     * can refuse it.
     */
    @Test
    fun `when three marks overlap the favourites mark goes first`() {
        val layout = assertNotNull(
            layoutCoachMarks(portraitTargets, portrait, density, CoachNavigation.BottomBar) { target, _, width ->
                Size(width, if (target == CoachTarget.Favourites) 340f else 60f)
            },
        )

        assertEquals(CoachNoteStyle.Medium, layout.style)
        assertEquals(listOf(CoachTarget.Filters, CoachTarget.SearchField), layout.marks.map { it.target })
        assertNoOverlap(layout, portraitTargets, portrait, "favourites dropped")
    }

    @Test
    fun `when two marks still overlap the filters mark goes next`() {
        val layout = assertNotNull(
            layoutCoachMarks(portraitTargets, portrait, density, CoachNavigation.BottomBar) { target, _, width ->
                Size(
                    width,
                    when (target) {
                        CoachTarget.SearchField -> 60f
                        CoachTarget.Filters -> 400f
                        CoachTarget.Favourites -> 300f
                    },
                )
            },
        )

        assertEquals(CoachNoteStyle.Medium, layout.style)
        assertEquals(listOf(CoachTarget.SearchField), layout.marks.map { it.target })
    }

    @Test
    fun `a screen too small for even the field's note draws nothing`() {
        assertNull(
            layoutCoachMarks(portraitTargets, Size(360f, 100f), density, CoachNavigation.BottomBar),
        )
    }

    @Test
    fun `a tiny screen only ever drops marks in order and never overlaps`() {
        val size = Size(240f, 360f)
        val targets = mapOf(
            CoachTarget.SearchField to Rect(0f, 4f, 240f, 60f),
            CoachTarget.Filters to Rect(168f, 224f, 224f, 280f),
            CoachTarget.Favourites to Rect(80f, 296f, 160f, 360f),
        )
        val layout = assertNotNull(layoutCoachMarks(targets, size, density, CoachNavigation.BottomBar))
        val shown = layout.marks.map { it.target }.toSet()

        assertTrue(CoachTarget.SearchField in shown, "the field's mark is the last to go: $shown")
        assertTrue(CoachTarget.Favourites !in shown || CoachTarget.Filters in shown, "filters dropped before favourites: $shown")
        assertTrue(shown.size < 3, "this screen is meant to be too small for all three: $shown")
        assertNoOverlap(layout, targets, size, "tiny")
    }

    /**
     * Mid-transition a target can report a width smaller than the arrow's
     * tip gap on both sides, which once made the landscape field arrow's
     * clamp throw.
     */
    @Test
    fun `a field narrower than the arrow tip gap does not throw`() {
        for (width in listOf(0f, 5f, 13f)) {
            val targets = landscapeTargets + (CoachTarget.SearchField to Rect(96f, 4f, 96f + width, 60f))
            for (navigation in CoachNavigation.entries) {
                layoutCoachMarks(targets, landscape, density, navigation)
            }
        }
    }

    /**
     * A landscape screen too narrow for the field's note beside the
     * Favourites note, but tall enough for it below. Before the note had
     * anywhere else to go, this lost the Favourites mark.
     */
    @Test
    fun `a landscape field note with no room beside favourites goes below it and all three survive`() {
        val size = Size(540f, 420f)
        val targets = mapOf(
            CoachTarget.SearchField to Rect(96f, 4f, 540f, 60f),
            CoachTarget.Filters to Rect(468f, 348f, 524f, 404f),
            CoachTarget.Favourites to Rect(0f, 108f, 96f, 172f),
        )
        val layout = assertNotNull(
            layoutCoachMarks(targets, size, density, CoachNavigation.Rail) { _, _, _ -> Size(200f, 60f) },
        )

        assertEquals(CoachNoteStyle.Large, layout.style)
        assertEquals(CoachTarget.entries.toSet(), layout.marks.map { it.target }.toSet())
        assertNoOverlap(layout, targets, size, "field below favourites")
        val byTarget = layout.marks.associateBy { it.target }
        val field = byTarget.getValue(CoachTarget.SearchField)
        val favourites = byTarget.getValue(CoachTarget.Favourites)
        assertTrue(field.noteBounds.top >= favourites.noteBounds.bottom, "field note ${field.noteBounds} is not below ${favourites.noteBounds}")
        layout.marks.forEach { assertLandsOn(it, targets.getValue(it.target)) }
    }

    /**
     * Only the "no note covers a target" rule refuses the large size here:
     * the button sits just under where the large field note ends and just
     * clear of where the medium one does, and nothing else collides.
     */
    @Test
    fun `a note that would cover a target steps the notes down`() {
        val size = Size(400f, 640f)
        val targets = mapOf(
            CoachTarget.SearchField to Rect(0f, 4f, 400f, 60f),
            CoachTarget.Filters to Rect(296f, 182f, 352f, 238f),
        )
        val layout = assertNotNull(
            layoutCoachMarks(targets, size, density, CoachNavigation.BottomBar) { target, style, width ->
                if (target == CoachTarget.Filters) Size(40f, 20f) else worstCaseNoteSize(style, width, density)
            },
        )

        assertEquals(CoachNoteStyle.Medium, layout.style)
        assertEquals(setOf(CoachTarget.SearchField, CoachTarget.Filters), layout.marks.map { it.target }.toSet())
        assertNoOverlap(layout, targets, size, "covered target")
    }

    /**
     * Only the "no arrow crosses another note" rule refuses both sizes
     * here: the filters note sits level with the field's note but clear of
     * it, and its arrow runs straight through the field's note to a button
     * below and to the start.
     */
    @Test
    fun `an arrow that would cross another note drops its mark`() {
        val size = Size(400f, 640f)
        val targets = mapOf(
            CoachTarget.SearchField to Rect(0f, 4f, 400f, 60f),
            CoachTarget.Filters to Rect(120f, 185f, 176f, 241f),
        )
        val layout = assertNotNull(
            layoutCoachMarks(targets, size, density, CoachNavigation.BottomBar) { target, style, width ->
                if (target == CoachTarget.Filters) Size(40f, 20f) else worstCaseNoteSize(style, width, density)
            },
        )

        assertEquals(listOf(CoachTarget.SearchField), layout.marks.map { it.target })
    }

    /**
     * The Favourites arrow stops just short of its icon, which already puts
     * its tip over the bar; the field and filters arrows reach into their
     * targets, 10dp and 5dp, so they overlap them the same way.
     */
    private fun assertLandsOn(mark: CoachMark, target: Rect) {
        val end = mark.arrow.end
        if (mark.target == CoachTarget.Favourites) {
            val gap = distance(end, target)
            assertTrue(gap in 6f..8f, "Favourites' arrow ends ${gap}dp from its icon, at $end for $target")
        } else {
            assertTrue(target.contains(end), "${mark.target}'s arrow ends outside its target, at $end for $target")
            val depth = minOf(end.x - target.left, target.right - end.x, end.y - target.top, target.bottom - end.y)
            val expected = if (mark.target == CoachTarget.SearchField) 10f else 5f
            assertEquals(expected, depth, 0.5f, "${mark.target}'s arrow reaches ${depth}dp into its target, at $end for $target")
        }
    }

    private fun assertNoOverlap(layout: CoachMarksLayout, targets: Map<CoachTarget, Rect>, size: Size, case: String) {
        val screen = Rect(Offset.Zero, size)
        layout.marks.forEach { mark ->
            val note = mark.noteBounds
            assertTrue(
                note.left >= screen.left - 0.5f && note.top >= -0.5f &&
                    note.right <= screen.right + 0.5f && note.bottom <= screen.bottom + 0.5f,
                "$case: ${mark.target}'s note $note leaves the screen",
            )
            targets.forEach { (target, rect) ->
                assertTrue(!note.overlaps(rect), "$case: ${mark.target}'s note $note covers $target at $rect")
            }
            layout.marks.filter { it !== mark }.forEach { other ->
                assertTrue(
                    !note.overlaps(other.noteBounds),
                    "$case: ${mark.target}'s note $note overlaps ${other.target}'s ${other.noteBounds}",
                )
                assertTrue(
                    !note.overlaps(other.arrow.bounds),
                    "$case: ${other.target}'s arrow ${other.arrow.bounds} crosses ${mark.target}'s note $note",
                )
            }
        }
    }

    private fun distance(point: Offset, rect: Rect): Float {
        val dx = max(max(rect.left - point.x, 0f), point.x - rect.right)
        val dy = max(max(rect.top - point.y, 0f), point.y - rect.bottom)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

private fun unit(offset: Offset): Offset {
    val length = hypot(offset.x, offset.y)
    return Offset(offset.x / length, offset.y / length)
}
