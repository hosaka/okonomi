package cc.hosaka.okonomi.ui.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.coach_favourites
import okonomi.shared.generated.resources.coach_search_field
import okonomi.shared.generated.resources.coach_search_filters
import org.jetbrains.compose.resources.stringResource

/** Test tag on the overlay while it is drawn. */
internal const val COACH_MARKS_TAG = "coach-marks"

/** The real elements a coach mark can point at. */
internal enum class CoachTarget {
    SearchField,
    Filters,
    Favourites,
}

/** Which typography step the notes are set in; see [layoutCoachMarks]. */
internal enum class CoachNoteStyle {
    Large,
    Medium,
}

/**
 * Where the shell keeps its navigation, which decides how the marks are
 * arranged. The shell's own layout type is mapped onto this rather than
 * imported, so `ui/` does not depend on a feature package.
 */
internal enum class CoachNavigation {
    /** Portrait: a tab bar along the bottom edge. */
    BottomBar,

    /** Landscape: a rail along the start edge. */
    Rail,
}

/**
 * Where the coach marks' targets are, and whether the screen under them
 * wants marks drawn at all.
 *
 * The shell owns one and the Search screen and the tab bar or rail report
 * into it, which is why it is a composition local rather than state on
 * either side: neither the shell nor Search can see the other's elements.
 *
 * Targets report window positions, because that is the one coordinate
 * space every reporter shares whatever it is nested in. The overlay draws
 * in its own space, and the two agree only while the host sits at the
 * window's origin — true of the app today, where the shell fills the
 * window and keeps its insets inside the host, but nothing guarantees it
 * (an embedding view, a harness that offsets the shell). [targets] hands
 * the bounds back relative to the host, so the arrows land on the
 * elements wherever the host is placed.
 *
 * Both reports and requests are kept per owner. During a push or pop two
 * Search screens are composed at once; if a second reporter simply
 * replaced the first, the one that left would take the target with it and
 * the one that stayed — not moving, so never reporting again — would be
 * left with no arrow. Instead the newest live report wins, and an owner
 * leaving only ever withdraws its own.
 */
@Stable
internal class CoachMarkRegistry {
    private class Reported(val owner: Any, val bounds: Rect)

    private val reported = mutableStateMapOf<CoachTarget, List<Reported>>()
    private var origin by mutableStateOf(Offset.Zero)
    private val wantedBy = mutableStateListOf<Any>()

    /**
     * The layout the overlay last drew from, or null while it is not
     * drawn. Read by tests: what the overlay draws is invisible to them,
     * where it decided to draw it is not.
     */
    var lastLayout: CoachMarksLayout? by mutableStateOf(null)
        internal set

    /** True while some screen asked for the marks; see [CoachMarksWanted]. */
    val wanted: Boolean
        get() = wantedBy.isNotEmpty()

    /** Every reported target, in the host's coordinates. */
    val targets: Map<CoachTarget, Rect>
        get() = reported
            .filterValues { it.isNotEmpty() }
            .mapValues { (_, reports) -> reports.last().bounds.translate(-origin) }

    fun report(target: CoachTarget, owner: Any, windowBounds: Rect) {
        val reports = reported[target].orEmpty()
        val current = reports.lastOrNull { it.owner === owner }
        if (current?.bounds == windowBounds) return
        val others = reports.filterNot { it.owner === owner }
        reported[target] = if (current == null) {
            others + Reported(owner, windowBounds)
        } else {
            // Moving keeps an owner's place in line: a report is a
            // position, not a claim to be the newest.
            reports.map { if (it.owner === owner) Reported(owner, windowBounds) else it }
        }
    }

    /** Withdraws [owner]'s report of [target], and nobody else's. */
    fun remove(target: CoachTarget, owner: Any) {
        val reports = reported[target] ?: return
        val remaining = reports.filterNot { it.owner === owner }
        if (remaining.size != reports.size) reported[target] = remaining
    }

    fun moveOrigin(windowPosition: Offset) {
        if (origin != windowPosition) origin = windowPosition
    }

    /** Asks for the marks on [owner]'s behalf; asking twice is asking once. */
    fun want(owner: Any) {
        if (wantedBy.none { it === owner }) wantedBy += owner
    }

    /** Withdraws [owner]'s request, and nobody else's. */
    fun unwant(owner: Any) {
        wantedBy.removeAll { it === owner }
    }
}

/** Null outside [CoachMarksHost], which makes every reporter a no-op. */
internal val LocalCoachMarks = staticCompositionLocalOf<CoachMarkRegistry?> { null }

/**
 * Reports this element's bounds as [target] to the enclosing
 * [CoachMarksHost], and withdraws them when the element leaves.
 */
internal fun Modifier.coachMarkTarget(target: CoachTarget): Modifier = this then CoachMarkTargetElement(target)

private data class CoachMarkTargetElement(
    val target: CoachTarget,
) : ModifierNodeElement<CoachMarkTargetNode>() {
    override fun create() = CoachMarkTargetNode(target)

    override fun update(node: CoachMarkTargetNode) {
        node.retarget(target)
    }
}

private class CoachMarkTargetNode(
    private var target: CoachTarget,
) : Modifier.Node(),
    GlobalPositionAwareModifierNode,
    CompositionLocalConsumerModifierNode {
    private var registry: CoachMarkRegistry? = null

    override fun onAttach() {
        registry = currentValueOf(LocalCoachMarks)
    }

    override fun onDetach() {
        registry?.remove(target, this)
        registry = null
    }

    fun retarget(newTarget: CoachTarget) {
        if (newTarget == target) return
        registry?.remove(target, this)
        target = newTarget
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        registry?.report(
            target = target,
            owner = this,
            windowBounds = Rect(coordinates.positionInWindow(), coordinates.size.toSize()),
        )
    }
}

/**
 * Asks for the marks while [wanted] holds and the caller stays composed.
 * The request is the caller's own, so two screens composed at once during
 * a transition cannot clear each other's.
 */
@Composable
internal fun CoachMarksWanted(wanted: Boolean) {
    val registry = LocalCoachMarks.current ?: return
    val owner = remember { Any() }
    if (wanted) {
        DisposableEffect(registry, owner) {
            registry.want(owner)
            onDispose {
                registry.unwant(owner)
            }
        }
    }
}

/**
 * The Search screen's half of the visibility rule: the field is empty and
 * the results idle, this search is its section's root rather than one
 * pushed over an entry, and the filters menu is closed.
 *
 * The query is checked as well as the results because the results stay
 * idle for a blank query: a field holding only spaces still shows text
 * the marks would be drawn over.
 */
internal fun coachMarksWanted(
    queryEmpty: Boolean,
    resultsIdle: Boolean,
    isRoot: Boolean,
    filtersOpen: Boolean,
): Boolean = queryEmpty && resultsIdle && isRoot && !filtersOpen

/**
 * Persisted key of Appearance's "Show hints on main tab" switch. Off
 * means the marks never show; on leaves every other rule as it was.
 */
internal const val COACH_MARKS_ENABLED_PREFERENCE = "home.coach_marks_enabled"

/** On, so every reader who never touched the switch keeps the marks. */
internal const val COACH_MARKS_ENABLED_DEFAULT = true

/**
 * The shell's half: the reader has not switched the marks off, the Search
 * tab is selected, its section is at its root, Search asked for marks,
 * and the keyboard is down — a reader with the IME up is already doing
 * what the field's note suggests.
 */
internal fun coachMarksVisible(
    enabled: Boolean,
    searchSelected: Boolean,
    atRoot: Boolean,
    wanted: Boolean,
    imeVisible: Boolean,
): Boolean = enabled && searchSelected && atRoot && wanted && !imeVisible

private const val FADE_IN_MILLIS = 220
private const val FADE_IN_DELAY_MILLIS = 120
private const val FADE_OUT_MILLIS = 120

/**
 * Provides a [CoachMarkRegistry] to [content] and draws the coach marks
 * over it whenever [coachMarksVisible] holds.
 *
 * The overlay is drawn last, above everything, and carries no pointer
 * input at all, so every touch falls through to whatever is under it.
 *
 * [enabled] is the reader's "Show hints on main tab" setting; the shell
 * passes false until the stored value has been read, so a reader who
 * turned the marks off never sees them flash in.
 *
 * [imeVisible] defaults to the real keyboard inset; it is a parameter so
 * a test can raise a keyboard the host test runtime never shows.
 */
@Composable
internal fun CoachMarksHost(
    searchSelected: Boolean,
    atRoot: Boolean,
    navigation: CoachNavigation,
    registry: CoachMarkRegistry,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    imeVisible: Boolean = WindowInsets.ime.getBottom(LocalDensity.current) > 0,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { registry.moveOrigin(it.positionInWindow()) },
    ) {
        CompositionLocalProvider(LocalCoachMarks provides registry) {
            content()
        }
        AnimatedVisibility(
            visible = coachMarksVisible(
                enabled = enabled,
                searchSelected = searchSelected,
                atRoot = atRoot,
                wanted = registry.wanted,
                imeVisible = imeVisible,
            ),
            modifier = Modifier
                .matchParentSize(),
            enter = fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_IN_DELAY_MILLIS)),
            exit = fadeOut(tween(FADE_OUT_MILLIS)),
        ) {
            CoachMarksOverlay(registry, navigation)
        }
    }
}

/**
 * The scribbles themselves: arrows and tilted notes, all drawn on one
 * canvas.
 *
 * The notes are drawn text rather than `Text` composables, so they never
 * enter the semantics tree; `clearAndSetSemantics` leaves only the test
 * tag, and a screen reader exploring the idle Search tab finds the real
 * field, button and tabs and nothing else.
 *
 * What it looks like — the curve, the chevrons, the rotated italic — is
 * invisible to the host tests: Robolectric lays text out at zero width
 * and nothing drawn here reaches the semantics tree. Where the marks go
 * is [layoutCoachMarks], which is tested with numbers; how they look is
 * checked on a device.
 */
@Composable
private fun CoachMarksOverlay(registry: CoachMarkRegistry, navigation: CoachNavigation) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    val large = MaterialTheme.typography.bodyLarge.copy(color = color, fontStyle = FontStyle.Italic)
    val medium = MaterialTheme.typography.bodyMedium.copy(color = color, fontStyle = FontStyle.Italic)
    val notes = mapOf(
        CoachTarget.SearchField to stringResource(Res.string.coach_search_field),
        CoachTarget.Filters to stringResource(Res.string.coach_search_filters),
        CoachTarget.Favourites to stringResource(Res.string.coach_favourites),
    )
    fun measure(target: CoachTarget, style: CoachNoteStyle, maxWidth: Float): TextLayoutResult {
        val textStyle = if (style == CoachNoteStyle.Large) large else medium
        return measurer.measure(
            text = notes.getValue(target),
            style = textStyle.copy(textAlign = noteAlignment(target)),
            overflow = TextOverflow.Ellipsis,
            maxLines = NOTE_MAX_LINES,
            constraints = Constraints(maxWidth = maxWidth.toInt().coerceAtLeast(0)),
        )
    }

    var overlaySize by remember { mutableStateOf(Size.Zero) }
    // Laid out in composition rather than in the draw pass, so the result
    // can be handed to the registry without writing state while drawing.
    val marks = if (overlaySize == Size.Zero) {
        null
    } else {
        layoutCoachMarks(
            targets = registry.targets,
            overlaySize = overlaySize,
            density = density,
            navigation = navigation,
            measureNote = { target, style, maxWidth ->
                val text = measure(target, style, maxWidth)
                Size(text.inkWidth(), text.size.height.toFloat())
            },
        )
    }
    SideEffect {
        registry.lastLayout = marks
    }
    DisposableEffect(registry) {
        onDispose {
            registry.lastLayout = null
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it.toSize() }
            .clearAndSetSemantics {
                testTag = COACH_MARKS_TAG
            },
    ) {
        if (marks == null) return@Canvas
        val stroke = Stroke(
            width = ARROW_STROKE.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        marks.marks.forEach { mark ->
            val arrow = mark.arrow
            val path = Path().apply {
                moveTo(arrow.start.x, arrow.start.y)
                cubicTo(
                    arrow.control1.x, arrow.control1.y,
                    arrow.control2.x, arrow.control2.y,
                    arrow.end.x, arrow.end.y,
                )
                moveTo(arrow.headLeft.x, arrow.headLeft.y)
                lineTo(arrow.end.x, arrow.end.y)
                lineTo(arrow.headRight.x, arrow.headRight.y)
            }
            drawPath(path = path, color = color, style = stroke)

            val text = measure(mark.target, marks.style, marks.noteMaxWidth)
            val layoutWidth = text.size.width.toFloat()
            val left = when (noteAlignment(mark.target)) {
                TextAlign.End -> mark.note.right - layoutWidth
                TextAlign.Center -> mark.note.center.x - layoutWidth / 2
                else -> mark.note.left
            }
            rotate(degrees = mark.rotation, pivot = mark.note.center) {
                drawText(textLayoutResult = text, topLeft = Offset(left, mark.note.top))
            }
        }
    }
}

/** The width the laid-out lines actually cover, not the width offered. */
private fun TextLayoutResult.inkWidth(): Float =
    (0 until lineCount).maxOfOrNull { getLineRight(it) - getLineLeft(it) } ?: 0f

private fun noteAlignment(target: CoachTarget): TextAlign = when (target) {
    CoachTarget.SearchField -> TextAlign.Center
    CoachTarget.Filters -> TextAlign.End
    CoachTarget.Favourites -> TextAlign.Start
}

private const val NOTE_MAX_LINES = 3
private val NOTE_MAX_WIDTH = 200.dp
private val ARROW_STROKE = 2.dp

/**
 * How far short of the Favourites icon its arrow stops. The icon sits
 * inside the bar or rail, so that tip already lands a little over the bar.
 */
internal val ARROW_TIP_GAP = 7.dp

/**
 * How far the field and filters arrows reach into their targets, so they
 * overlap them slightly the way the Favourites arrow overlaps the bar
 * (Alex, 2026-10-06, after seeing it on a Pixel 6).
 */
internal val ARROW_OVERLAP = 5.dp

/** The field's arrow reaches further in than the filters one (Alex, 2026-10-06). */
internal val FIELD_ARROW_OVERLAP = 10.dp

/** The gap between a note and the tail of its own arrow. */
private val ARROW_TAIL_GAP = 6.dp

/** The distance kept between a note and the element it points at, which the arrow spans. */
private val ARROW_ROOM = 48.dp

/** Space kept from the overlay's edges. */
private val EDGE_MARGIN = 16.dp

/** Space kept between two notes stacked to avoid each other. */
private val NOTE_GAP = 8.dp

private val ARROW_HEAD_LENGTH = 10.dp
private const val ARROW_HEAD_DEGREES = 28f

/**
 * Line heights of Material 3's default `bodyLarge` and `bodyMedium`,
 * which the worst-case note in [layoutCoachMarks] is sized from. The
 * project does not change the default typography; if it ever does, these
 * only matter where no real measurement is passed in.
 */
private val LARGE_LINE_HEIGHT = 24.sp
private val MEDIUM_LINE_HEIGHT = 20.sp

private fun rotationOf(target: CoachTarget): Float = when (target) {
    CoachTarget.SearchField -> -3f
    CoachTarget.Filters -> 4f
    CoachTarget.Favourites -> -2f
}

/**
 * How far each arrow bows; fixed so it never shimmers between frames. The
 * side is not chosen here but by [arrow], so the curve stays a single arc.
 */
private fun wobbleOf(target: CoachTarget) = when (target) {
    CoachTarget.SearchField -> 9.dp
    CoachTarget.Filters -> (-7).dp
    CoachTarget.Favourites -> 6.dp
}

/**
 * The largest a note can be: [NOTE_MAX_LINES] full lines of [style] at
 * [maxWidth]. What [layoutCoachMarks] assumes when it is given no real
 * measurement.
 */
internal fun worstCaseNoteSize(style: CoachNoteStyle, maxWidth: Float, density: Density): Size = with(density) {
    val lineHeight = if (style == CoachNoteStyle.Large) LARGE_LINE_HEIGHT else MEDIUM_LINE_HEIGHT
    Size(maxWidth, lineHeight.toPx() * NOTE_MAX_LINES)
}

/** One scribbled arrow: a single cubic Bézier and an open chevron at its [end]. */
internal data class CoachArrow(
    val start: Offset,
    val control1: Offset,
    val control2: Offset,
    val end: Offset,
    val headLeft: Offset,
    val headRight: Offset,
) {
    /** A box every drawn point of the arrow falls inside. */
    val bounds: Rect
        get() {
            val points = listOf(start, control1, control2, end, headLeft, headRight)
            return Rect(
                left = points.minOf { it.x },
                top = points.minOf { it.y },
                right = points.maxOf { it.x },
                bottom = points.maxOf { it.y },
            )
        }
}

/**
 * One mark: its note's box before rotation, the rotation drawn around the
 * box's centre, and the arrow from the note to the target.
 */
internal data class CoachMark(
    val target: CoachTarget,
    val note: Rect,
    val rotation: Float,
    val arrow: CoachArrow,
) {
    /** What the rotated note actually covers. */
    val noteBounds: Rect
        get() = note.rotatedBounds(rotation)
}

internal data class CoachMarksLayout(
    val style: CoachNoteStyle,
    val noteMaxWidth: Float,
    val marks: List<CoachMark>,
)

/**
 * Where every coach mark goes, from nothing but where the targets are and
 * how big the overlay is; pure so it can be tested with numbers.
 *
 * Portrait, the screen is read top to bottom: the field's note sits below
 * the field, centred, with its arrow up to the field's bottom edge about a
 * third of the way across; the filters note sits end-aligned above the
 * button with its arrow down to the button's top-start; the Favourites
 * note sits start-aligned above the tab bar with its arrow down to the
 * tab's top edge. When the last two would collide, the filters note moves
 * up rather than either one moving sideways off its target.
 *
 * Landscape, the rail is on the start edge: the Favourites note sits level
 * with the rail item and points left at its end edge, the field's note
 * sits below the field and clear of it — beside the Favourites note when
 * there is room, below it when there is not — and the filters note sits
 * to the start of the button with a horizontal arrow.
 *
 * No two notes overlap, no note covers a target, and no arrow crosses
 * another mark's note. When that cannot hold, the notes step down to
 * [CoachNoteStyle.Medium], then the Favourites mark goes, then the
 * filters mark (Alex's order, 2026-10-06). Null when not even the field's
 * mark fits, or no target has reported yet.
 *
 * [measureNote] gives a note's size for a style and maximum width; the
 * default assumes the worst case, three full lines.
 */
internal fun layoutCoachMarks(
    targets: Map<CoachTarget, Rect>,
    overlaySize: Size,
    density: Density,
    navigation: CoachNavigation,
    measureNote: (CoachTarget, CoachNoteStyle, Float) -> Size = { _, style, maxWidth ->
        worstCaseNoteSize(style, maxWidth, density)
    },
): CoachMarksLayout? {
    val noteMaxWidth = with(density) {
        min(NOTE_MAX_WIDTH.toPx(), overlaySize.width - 2 * EDGE_MARGIN.toPx())
    }
    if (noteMaxWidth <= 0f) return null
    val present = CoachTarget.entries.filter { it in targets }
    val attempts = listOf(
        CoachNoteStyle.Large to present,
        CoachNoteStyle.Medium to present,
        CoachNoteStyle.Medium to present - CoachTarget.Favourites,
        CoachNoteStyle.Medium to present - CoachTarget.Favourites - CoachTarget.Filters,
    )
    for ((style, wanted) in attempts.distinct()) {
        if (wanted.isEmpty()) continue
        val sizes = wanted.associateWith { target ->
            val measured = measureNote(target, style, noteMaxWidth)
            Size(min(measured.width, noteMaxWidth), measured.height)
        }
        val marks = with(density) {
            when (navigation) {
                CoachNavigation.BottomBar -> placeVertical(targets, sizes, overlaySize)
                CoachNavigation.Rail -> placeHorizontal(targets, sizes, overlaySize)
            }
        }
        if (fits(marks, targets, overlaySize)) {
            return CoachMarksLayout(style = style, noteMaxWidth = noteMaxWidth, marks = marks)
        }
    }
    return null
}

private fun Density.placeVertical(
    targets: Map<CoachTarget, Rect>,
    sizes: Map<CoachTarget, Size>,
    overlay: Size,
): List<CoachMark> {
    val margin = EDGE_MARGIN.toPx()
    val room = ARROW_ROOM.toPx()
    val tail = ARROW_TAIL_GAP.toPx()
    val tip = ARROW_TIP_GAP.toPx()
    val overlap = ARROW_OVERLAP.toPx()
    val fieldOverlap = FIELD_ARROW_OVERLAP.toPx()
    val marks = mutableListOf<CoachMark>()

    val favourites = sizes[CoachTarget.Favourites]?.let { size ->
        val tab = targets.getValue(CoachTarget.Favourites)
        val rotated = rotatedSize(size, rotationOf(CoachTarget.Favourites))
        val bounds = rotated.boxAt(left = margin, top = tab.top - room - rotated.height)
        mark(
            target = CoachTarget.Favourites,
            size = size,
            bounds = bounds,
            // From the note's end edge when the icon lies beyond it, so
            // the curve can swing over and drop onto the icon; from its
            // bottom when the note already sits over the icon.
            start = if (bounds.right + tail < tab.left) {
                Offset(bounds.right + tail, bounds.center.y)
            } else {
                Offset(bounds.center.x, bounds.bottom + tail)
            },
            end = Offset(tab.center.x, tab.top - tip),
            approach = DOWN,
        )
    }

    sizes[CoachTarget.Filters]?.let { size ->
        val button = targets.getValue(CoachTarget.Filters)
        val rotated = rotatedSize(size, rotationOf(CoachTarget.Filters))
        val left = overlay.width - margin - rotated.width
        var bottom = button.top - room
        val below = favourites?.noteBounds
        if (below != null && left < below.right && left + rotated.width > below.left) {
            bottom = min(bottom, below.top - NOTE_GAP.toPx())
        }
        val bounds = rotated.boxAt(left = left, top = bottom - rotated.height)
        marks += mark(
            target = CoachTarget.Filters,
            size = size,
            bounds = bounds,
            start = Offset(bounds.center.x + bounds.width * 0.15f, bounds.bottom + tail),
            end = Offset(button.left + button.width * 0.3f, button.top + overlap),
            approach = DOWN,
        )
    }

    sizes[CoachTarget.SearchField]?.let { size ->
        val field = targets.getValue(CoachTarget.SearchField)
        val rotated = rotatedSize(size, rotationOf(CoachTarget.SearchField))
        val bounds = rotated.boxAt(left = (overlay.width - rotated.width) / 2, top = field.bottom + room)
        marks += mark(
            target = CoachTarget.SearchField,
            size = size,
            bounds = bounds,
            start = Offset(bounds.center.x - bounds.width * 0.15f, bounds.top - tail),
            end = Offset(field.left + field.width * 0.35f, field.bottom - fieldOverlap),
            approach = UP,
        )
    }

    if (favourites != null) marks += favourites
    return marks
}

private fun Density.placeHorizontal(
    targets: Map<CoachTarget, Rect>,
    sizes: Map<CoachTarget, Size>,
    overlay: Size,
): List<CoachMark> {
    val margin = EDGE_MARGIN.toPx()
    val room = ARROW_ROOM.toPx()
    val tail = ARROW_TAIL_GAP.toPx()
    val tip = ARROW_TIP_GAP.toPx()
    val overlap = ARROW_OVERLAP.toPx()
    val fieldOverlap = FIELD_ARROW_OVERLAP.toPx()
    val marks = mutableListOf<CoachMark>()

    val favourites = sizes[CoachTarget.Favourites]?.let { size ->
        val item = targets.getValue(CoachTarget.Favourites)
        val rotated = rotatedSize(size, rotationOf(CoachTarget.Favourites))
        val top = clampTop(item.center.y - rotated.height / 2, rotated.height, overlay.height, margin)
        val bounds = rotated.boxAt(left = item.right + room, top = top)
        mark(
            target = CoachTarget.Favourites,
            size = size,
            bounds = bounds,
            start = Offset(bounds.left - tail, item.center.y.coerceIn(bounds.top, bounds.bottom)),
            end = Offset(item.right + tip, item.center.y),
            approach = LEFT,
        )
    }

    sizes[CoachTarget.SearchField]?.let { size ->
        val field = targets.getValue(CoachTarget.SearchField)
        val rotated = rotatedSize(size, rotationOf(CoachTarget.SearchField))
        val maxLeft = max(margin, overlay.width - margin - rotated.width)
        var top = field.bottom + room
        var left = (field.left + field.width * 0.35f - rotated.width / 2).coerceIn(margin, maxLeft)
        val beside = favourites?.noteBounds
        if (beside != null && top < beside.bottom && top + rotated.height > beside.top && left < beside.right) {
            val gap = NOTE_GAP.toPx()
            if (beside.right + gap <= maxLeft) {
                // Beside the Favourites note, on the field's band.
                left = beside.right + gap
            } else {
                // No room beside it: drop below it instead, far enough
                // along that the arrow's tail clears the note above.
                top = beside.bottom + gap
                left = (beside.right + gap - rotated.width * FIELD_ARROW_TAIL_ALONG).coerceIn(margin, maxLeft)
            }
        }
        val bounds = rotated.boxAt(left = left, top = top)
        val tipLow = field.left + tip
        val tipHigh = max(tipLow, field.right - tip)
        marks += mark(
            target = CoachTarget.SearchField,
            size = size,
            bounds = bounds,
            start = Offset(bounds.left + bounds.width * FIELD_ARROW_TAIL_ALONG, bounds.top - tail),
            end = Offset(bounds.center.x.coerceIn(tipLow, tipHigh), field.bottom - fieldOverlap),
            approach = UP,
        )
    }

    sizes[CoachTarget.Filters]?.let { size ->
        val button = targets.getValue(CoachTarget.Filters)
        val rotated = rotatedSize(size, rotationOf(CoachTarget.Filters))
        val top = clampTop(button.center.y - rotated.height / 2, rotated.height, overlay.height, margin)
        val bounds = rotated.boxAt(left = button.left - room - rotated.width, top = top)
        marks += mark(
            target = CoachTarget.Filters,
            size = size,
            bounds = bounds,
            start = Offset(bounds.right + tail, button.center.y.coerceIn(bounds.top, bounds.bottom)),
            end = Offset(button.left + overlap, button.center.y),
            approach = RIGHT,
        )
    }

    if (favourites != null) marks += favourites
    return marks
}

/** How far along its note, from the start edge, the landscape field arrow leaves. */
private const val FIELD_ARROW_TAIL_ALONG = 0.4f

private fun clampTop(top: Float, height: Float, overlayHeight: Float, margin: Float): Float =
    top.coerceIn(margin, max(margin, overlayHeight - margin - height))

/**
 * A mark whose rotated note covers [bounds]: the unrotated note shares
 * that box's centre.
 */
private fun Density.mark(
    target: CoachTarget,
    size: Size,
    bounds: Rect,
    start: Offset,
    end: Offset,
    approach: Offset,
): CoachMark = CoachMark(
    target = target,
    note = Rect(
        offset = Offset(bounds.center.x - size.width / 2, bounds.center.y - size.height / 2),
        size = size,
    ),
    rotation = rotationOf(target),
    arrow = arrow(start, end, approach, wobbleOf(target).toPx(), ARROW_HEAD_LENGTH.toPx()),
)

/** Directions an arrow can arrive at its target from, as unit vectors in screen space. */
private val UP = Offset(0f, -1f)
private val DOWN = Offset(0f, 1f)
private val LEFT = Offset(-1f, 0f)
private val RIGHT = Offset(1f, 0f)

/**
 * A hand-drawn-looking arrow: it leaves [start] bowing by a fixed amount,
 * never more than a sixth of its length, and arrives at
 * [end] travelling along [approach], so the curve's last stretch points
 * straight at the target and the open chevron sits on it.
 *
 * The head follows [approach] rather than the tangent of the drawn curve:
 * the second control point lies on the approach line behind [end], which
 * makes the two the same direction, and it keeps the head from tilting
 * with the bow.
 */
private fun arrow(start: Offset, end: Offset, approach: Offset, wobble: Float, headLength: Float): CoachArrow {
    val delta = end - start
    val length = hypot(delta.x, delta.y)
    val perpendicular = if (length == 0f) Offset.Zero else Offset(-delta.y / length, delta.x / length)
    val control2 = end - approach * (length * 0.4f)
    // The bow goes to the same side the approach already pulls the curve
    // towards, so the arrow is one C-shaped stroke. Bowing the other way
    // drew an S-hook on the short field arrow (seen on a Pixel 6).
    val pulled = (control2 - start).x * perpendicular.x + (control2 - start).y * perpendicular.y
    val side = if (pulled < 0f) -1f else 1f
    val bow = side * abs(wobble).coerceAtMost(length / 6)
    val control1 = start + delta * 0.35f + perpendicular * bow
    return CoachArrow(
        start = start,
        control1 = control1,
        control2 = control2,
        end = end,
        headLeft = end - approach.rotated(ARROW_HEAD_DEGREES) * headLength,
        headRight = end - approach.rotated(-ARROW_HEAD_DEGREES) * headLength,
    )
}

private fun fits(marks: List<CoachMark>, targets: Map<CoachTarget, Rect>, overlay: Size): Boolean {
    val screen = Rect(Offset.Zero, overlay)
    val tolerance = 0.5f
    return marks.all { mark ->
        val note = mark.noteBounds
        note.left >= screen.left - tolerance &&
            note.top >= screen.top - tolerance &&
            note.right <= screen.right + tolerance &&
            note.bottom <= screen.bottom + tolerance &&
            targets.values.none { it.overlapsStrictly(note) } &&
            marks.none { other -> other !== mark && other.noteBounds.overlapsStrictly(note) } &&
            marks.none { other -> other !== mark && other.arrow.bounds.overlapsStrictly(note) }
    }
}

private fun Rect.overlapsStrictly(other: Rect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

private fun rotatedSize(size: Size, degrees: Float): Size {
    val radians = degrees * PI.toFloat() / 180f
    val c = abs(cos(radians))
    val s = abs(sin(radians))
    return Size(size.width * c + size.height * s, size.width * s + size.height * c)
}

private fun Size.boxAt(left: Float, top: Float): Rect = Rect(Offset(left, top), this)

/** The axis-aligned box a rect covers once rotated about its centre. */
internal fun Rect.rotatedBounds(degrees: Float): Rect {
    val rotated = rotatedSize(size, degrees)
    return Rect(
        offset = Offset(center.x - rotated.width / 2, center.y - rotated.height / 2),
        size = rotated,
    )
}

private fun Offset.rotated(degrees: Float): Offset {
    val radians = degrees * PI.toFloat() / 180f
    val c = cos(radians)
    val s = sin(radians)
    return Offset(x * c - y * s, x * s + y * c)
}
