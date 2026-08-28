package cc.hosaka.okonomi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.packFloats
import androidx.compose.ui.util.unpackFloat1
import androidx.compose.ui.util.unpackFloat2
import kotlin.jvm.JvmInline
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** Width of the indicator. Thin enough to read as a hint, not a control. */
private val INDICATOR_THICKNESS = 3.dp

/** Gap between the indicator and the end edge of the list. */
private val INDICATOR_MARGIN = 2.dp

/**
 * Shortest the thumb may be drawn. Four hundred search results would
 * otherwise put a two-pixel mark on the edge of the screen.
 */
private val MIN_THUMB_LENGTH = 24.dp

private const val FADE_IN_MILLIS = 120

private const val FADE_OUT_MILLIS = 320

/** How long the indicator lingers after the list stops moving. */
private const val IDLE_BEFORE_FADE_MILLIS = 1_000L

private const val INDICATOR_ALPHA = 0.38f

/**
 * Where the scroll indicator's thumb sits inside its track, in pixels
 * along the track.
 *
 * A value class over two packed floats rather than a data class,
 * because this is computed inside the draw phase on every frame of a
 * flick: a two-field object per frame is exactly the kind of allocation
 * that shows up as jank on the list it is supposed to be helping with.
 * `Hidden` stands in for null for the same reason — a nullable value
 * class boxes.
 */
@JvmInline
value class ScrollThumb internal constructor(private val packed: Long) {
    /** Distance from the start of the track to the top of the thumb. */
    val offset: Float
        get() = unpackFloat1(packed)

    val length: Float
        get() = unpackFloat2(packed)

    /** False when there is nothing to indicate; nothing is drawn. */
    val isVisible: Boolean
        get() = length > 0f

    companion object {
        val Hidden: ScrollThumb = ScrollThumb(packFloats(0f, 0f))
    }
}

internal fun scrollThumbOf(offset: Float, length: Float): ScrollThumb =
    ScrollThumb(packFloats(offset, length))

/**
 * The thumb for a list showing [visibleCount] of [totalCount] items,
 * starting at [firstVisibleIndex], inside a track [trackLength] pixels
 * long.
 *
 * **Index-based, not pixel-based.** The rows these lists hold are of
 * wildly different heights — a search hit is one to four lines and an
 * example sentence's breakdown can run to a dozen words — and a lazy
 * list only knows the height of the rows it has measured, which is the
 * screenful in front of it. An exact proportional thumb is therefore
 * not available at any price. Counting items instead means the thumb
 * moves in even steps that do not correspond exactly to how much text
 * has gone past, which for a position *hint* is the right trade: it is
 * monotonic, it starts at the top and it ends at the bottom.
 *
 * Returns [ScrollThumb.Hidden] when there is nothing to say: an empty
 * list, or one whose items all fit on screen. That is the case the
 * Phrases tab's sparse entries are in, and drawing a full-length thumb
 * there would suggest a list that scrolls when it does not.
 *
 * Pure so the arithmetic is testable: the overlay itself is a draw
 * call with no semantics, and the ends of the track are exactly where
 * an off-by-one would be invisible to review and obvious to a reader.
 */
internal fun scrollThumb(
    firstVisibleIndex: Int,
    visibleCount: Int,
    totalCount: Int,
    trackLength: Float,
    minThumbLength: Float,
): ScrollThumb {
    if (totalCount <= 0 || visibleCount <= 0 || visibleCount >= totalCount) {
        return ScrollThumb.Hidden
    }
    if (trackLength <= 0f) return ScrollThumb.Hidden
    val proportional = trackLength * visibleCount / totalCount
    // A minimum longer than the track itself would push the thumb past
    // the end; the track wins.
    val length = proportional.coerceIn(minThumbLength.coerceAtMost(trackLength), trackLength)
    // The largest first-visible index the list can reach. Dividing by
    // it rather than by totalCount is what puts the thumb exactly at
    // the bottom on the last page instead of a screenful short of it.
    val lastFirstIndex = totalCount - visibleCount
    val progress = (firstVisibleIndex.toFloat() / lastFirstIndex).coerceIn(0f, 1f)
    return scrollThumbOf(offset = progress * (trackLength - length), length = length)
}

/**
 * Draws a scroll position indicator down the end edge of a lazy list:
 * visible while the list is moving, fading out about a second after it
 * stops.
 *
 * An indicator and not a control. It is deliberately not draggable —
 * a hit target this thin is a frustrating one, and neither platform's
 * own lists offer a drag handle at this size.
 *
 * Hand-rolled because there is nothing to reuse: Compose
 * Multiplatform's `VerticalScrollbar` exists only for desktop and web,
 * and no foundation artifact for Android or either iOS target ships a
 * scrollbar at all.
 *
 * It sits in the scroll path, so it is written to cost nothing per
 * frame:
 * - the layout is read *inside* the draw lambda, so a scrolled pixel
 *   invalidates the draw phase and neither composition nor layout.
 *   Hoisting `layoutInfo` into composition would recompose the whole
 *   list on every frame of a flick.
 * - the per-frame path allocates nothing. [ScrollThumb] is a value
 *   class, and `Offset`, `Size`, `CornerRadius` and `Color` are all
 *   value classes over packed primitives.
 * - the fade is an [Animatable] read in the draw lambda, so it too
 *   animates without recomposing anything.
 *
 * [contentPadding] must be the list's own, so the track stays inside
 * the same region the rows do and the indicator never runs under the
 * floating tab bar or a collapsing toolbar.
 */
@Composable
fun Modifier.scrollIndicator(
    listState: LazyListState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): Modifier = scrollIndicator(
    scrollState = listState,
    thumb = remember(listState) {
        ScrollThumbSource { trackLength, minThumbLength ->
            val layout = listState.layoutInfo
            val visible = layout.visibleItemsInfo
            scrollThumb(
                firstVisibleIndex = visible.firstOrNull()?.index ?: 0,
                visibleCount = visible.size,
                totalCount = layout.totalItemsCount,
                trackLength = trackLength,
                minThumbLength = minThumbLength,
            )
        }
    },
    contentPadding = contentPadding,
    color = color,
)

/**
 * The same indicator down the end edge of a lazy grid.
 *
 * The arithmetic is unchanged and needs no grid-specific case: a grid
 * counts cells where a list counts rows, and [scrollThumb] divides the
 * visible count by the total, so the ratio is the same whichever unit
 * both are in. What could not be shared is the state — `LazyListState`
 * and `LazyGridState` carry `layoutInfo` of unrelated types with no
 * common supertype — so the reading of it is what the two overloads
 * differ by, and nothing else.
 */
@Composable
fun Modifier.scrollIndicator(
    gridState: LazyGridState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
): Modifier = scrollIndicator(
    scrollState = gridState,
    thumb = remember(gridState) {
        ScrollThumbSource { trackLength, minThumbLength ->
            val layout = gridState.layoutInfo
            val visible = layout.visibleItemsInfo
            scrollThumb(
                firstVisibleIndex = visible.firstOrNull()?.index ?: 0,
                visibleCount = visible.size,
                totalCount = layout.totalItemsCount,
                trackLength = trackLength,
                minThumbLength = minThumbLength,
            )
        }
    },
    contentPadding = contentPadding,
    color = color,
)

/**
 * Where the thumb goes, read fresh inside the draw phase.
 *
 * A `fun interface` rather than a `(Float, Float) -> ScrollThumb`: a
 * generic function type would box both parameters and the value-class
 * return on every frame of a flick, which is precisely the per-frame
 * allocation [ScrollThumb] exists to avoid.
 */
internal fun interface ScrollThumbSource {
    fun thumb(trackLength: Float, minThumbLength: Float): ScrollThumb
}

@Composable
private fun Modifier.scrollIndicator(
    scrollState: ScrollableState,
    thumb: ScrollThumbSource,
    contentPadding: PaddingValues,
    color: Color,
): Modifier {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(scrollState, alpha) {
        snapshotFlow { scrollState.isScrollInProgress }
            .collectLatest { scrolling ->
                if (scrolling) {
                    alpha.animateTo(1f, tween(FADE_IN_MILLIS))
                } else {
                    // collectLatest cancels this the moment scrolling
                    // resumes, so a flick-pause-flick never blinks.
                    delay(IDLE_BEFORE_FADE_MILLIS)
                    alpha.animateTo(0f, tween(FADE_OUT_MILLIS))
                }
            }
    }
    val layoutDirection = LocalLayoutDirection.current
    val topPadding = contentPadding.calculateTopPadding()
    val bottomPadding = contentPadding.calculateBottomPadding()
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    return this.scrollIndicatorDraw(
        thumb = thumb,
        alpha = alpha,
        color = color,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        endPadding = endPadding,
    )
}

private fun Modifier.scrollIndicatorDraw(
    thumb: ScrollThumbSource,
    alpha: Animatable<Float, *>,
    color: Color,
    topPadding: Dp,
    bottomPadding: Dp,
    endPadding: Dp,
): Modifier = drawWithCache {
    // Everything that depends only on the size and the paddings is
    // resolved once here, not per frame.
    val trackTop = topPadding.toPx()
    val trackLength = size.height - trackTop - bottomPadding.toPx()
    val thickness = INDICATOR_THICKNESS.toPx()
    val left = size.width - endPadding.toPx() - INDICATOR_MARGIN.toPx() - thickness
    val minThumbLength = MIN_THUMB_LENGTH.toPx()
    val corner = CornerRadius(thickness / 2f)
    onDrawWithContent {
        drawContent()
        val fade = alpha.value
        if (fade <= 0f) return@onDrawWithContent
        // Read in the draw scope on purpose: see the kdoc above.
        val position = thumb.thumb(trackLength, minThumbLength)
        if (!position.isVisible) return@onDrawWithContent
        drawRoundRect(
            color = color,
            topLeft = Offset(left, trackTop + position.offset),
            size = Size(thickness, position.length),
            cornerRadius = corner,
            alpha = fade * INDICATOR_ALPHA,
        )
    }
}
