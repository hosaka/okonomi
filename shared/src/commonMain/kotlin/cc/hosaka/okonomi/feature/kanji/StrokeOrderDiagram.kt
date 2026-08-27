package cc.hosaka.okonomi.feature.kanji

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_kanji_stroke_order_of
import okonomi.shared.generated.resources.entry_kanji_stroke_order_play
import org.jetbrains.compose.resources.stringResource

/**
 * Side of the square KanjiVG draws every character inside. It is fixed
 * across the whole corpus, so it is a constant rather than something
 * read out of the file, and the diagram scales it to the slot instead of
 * scaling the stored path data.
 */
private const val VIEW_BOX = 109f

/**
 * Ink width in viewBox units. Deliberately NOT KanjiVG's own
 * `stroke-width:3`: at the 88.dp this slot is, three units scales down
 * to a stroke that reads as grey rather than black on a 1x screen, and
 * the strokes of a 20-stroke character run together. 3.2 is the width
 * that stayed legible. Do not "correct" either this or the comment to
 * match the source file - the difference is the point.
 */
private const val STROKE_WIDTH = 3.2f

/**
 * The not-yet-drawn strokes. Faint enough to read as construction lines
 * rather than as ink, but never absent: see the KDoc on
 * [StrokeOrderDiagram] for why.
 */
private const val GHOST_ALPHA = 0.22f

/** How long one stroke takes to draw itself, whatever its length. */
private const val MILLIS_PER_STROKE = 320

/**
 * How much of stroke [index] is drawn when playback has reached
 * [progress], where progress runs `0f` to the stroke count and each
 * whole number is one finished stroke.
 *
 * The entire animation follows from this: `0f` draws nothing, `1f`
 * draws the whole stroke, and anything between draws that share of the
 * stroke's own length. It is pure on purpose — a host test can hold it
 * to account, and nothing else about the drawing can be checked there.
 */
internal fun strokeFractionAt(progress: Float, index: Int): Float =
    (progress - index).coerceIn(0f, 1f)

/**
 * The Kanji tab's stroke-order slot, filled.
 *
 * At rest it shows the finished character over a dashed practice grid.
 * A tap replays it: each stroke is drawn along its own length, in
 * KanjiVG's order, and a tap during playback restarts from the first
 * stroke.
 *
 * The strokes that have not been reached yet stay drawn, ghosted. A
 * stroke revealed against an empty square reads as a disconnected mark;
 * against a faint whole character it reads as construction. It also
 * means the resting state and the playing state differ only in how much
 * ink is solid, so there is no second "finished" render path that could
 * drift out of agreement with this one.
 *
 * **None of the drawing above is observable in a host test.** Robolectric
 * has no canvas, lays glyphs out to zero width and shadows the graphics
 * APIs without running them, so stroke geometry, timing and ink are
 * invisible there. What a host test can see is the semantics: that this
 * slot exposes a click action and the placeholder does not, and that
 * [strokeFractionAt] computes the playback rule correctly. Everything
 * else about this composable is checked on a device or not at all.
 */
@Composable
internal fun StrokeOrderDiagram(
    strokes: List<StrokeGeometry>,
    literal: String,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val ghost = ink.copy(alpha = GHOST_ALPHA)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val borderPx = with(density) { PLACEHOLDER_BORDER.toPx() }
    val cornerPx = with(density) { PLACEHOLDER_CORNER.toPx() }
    val dashPx = with(density) { PLACEHOLDER_DASH.toPx() }

    // Resting fully drawn, so the slot shows the character rather than an
    // empty grid until someone taps it.
    val progress = remember(strokes) { Animatable(strokes.size.toFloat()) }
    var playCount by remember(strokes) { mutableIntStateOf(0) }
    // playCount is part of the key, so a tap during playback cancels the
    // running animation and starts a new one from zero rather than
    // resuming. Count zero is the resting state and starts nothing.
    LaunchedEffect(strokes, playCount) {
        if (playCount == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = strokes.size.toFloat(),
            // Linear, because the pacing that matters is per stroke and
            // the rule that splits progress into strokes is linear too.
            animationSpec = tween(
                durationMillis = strokes.size * MILLIS_PER_STROKE,
                easing = LinearEasing,
            ),
        )
    }

    // The character is named here because the card no longer prints it
    // as text beside the diagram: this description is now the only thing
    // that tells a screen reader WHICH character the slot draws, and
    // "Stroke order" alone would leave the card anonymous.
    val description = stringResource(Res.string.entry_kanji_stroke_order_of, literal)
    val playLabel = stringResource(Res.string.entry_kanji_stroke_order_play)
    Box(
        modifier = modifier
            .size(STROKE_ORDER_SIZE)
            .clip(RoundedCornerShape(PLACEHOLDER_CORNER))
            .clickable(onClickLabel = playLabel, role = Role.Button) { playCount++ }
            .semantics { contentDescription = description }
            .drawBehind {
                drawGuideGrid(grid, cornerPx, borderPx, dashPx)
                // Read inside the draw lambda, the house idiom (see
                // ScrollIndicator): the animation invalidates drawing
                // only, never recomposition or layout.
                drawStrokes(strokes, progress.value, ink, ghost)
            },
    )
}

/**
 * A copybook square: the slot's outline plus the centre cross a learner
 * places the character against. Drawn in slot pixels rather than viewBox
 * units so its dashes stay the same size as the empty state's.
 */
private fun DrawScope.drawGuideGrid(
    color: Color,
    cornerPx: Float,
    borderPx: Float,
    dashPx: Float,
) {
    val dashes = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx))
    // Inset by half the border: a stroke is centred on its rectangle, and
    // the slot is clipped to its own bounds so the ripple stays rounded,
    // which would otherwise shave the outline to half the width the empty
    // state draws it at.
    val inset = borderPx / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - borderPx, size.height - borderPx),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(width = borderPx, pathEffect = dashes),
    )
    drawLine(
        color = color,
        start = Offset(size.width / 2f, 0f),
        end = Offset(size.width / 2f, size.height),
        strokeWidth = borderPx,
        pathEffect = dashes,
    )
    drawLine(
        color = color,
        start = Offset(0f, size.height / 2f),
        end = Offset(size.width, size.height / 2f),
        strokeWidth = borderPx,
        pathEffect = dashes,
    )
}

/**
 * Ghost layer first, then everything [progress] has reached: finished
 * strokes whole, and the one in flight cut to its share of its own
 * length, which is what makes it read as being written rather than
 * appearing.
 */
private fun DrawScope.drawStrokes(
    strokes: List<StrokeGeometry>,
    progress: Float,
    ink: Color,
    ghost: Color,
) {
    val style = Stroke(width = STROKE_WIDTH, cap = StrokeCap.Round, join = StrokeJoin.Round)
    // The paths are in KanjiVG's own coordinates; the canvas is scaled to
    // the slot instead, so the stored data is never rewritten and the ink
    // keeps the source's proportions at any size.
    val factor = size.minDimension / VIEW_BOX
    scale(factor, factor, pivot = Offset.Zero) {
        strokes.forEach { drawPath(it.path, ghost, style = style) }
        strokes.forEachIndexed { index, stroke ->
            val fraction = strokeFractionAt(progress, index)
            when {
                fraction <= 0f -> Unit
                fraction >= 1f -> drawPath(stroke.path, ink, style = style)
                else -> {
                    stroke.segment.reset()
                    val drawn = stroke.measure.getSegment(
                        startDistance = 0f,
                        stopDistance = fraction * stroke.length,
                        destination = stroke.segment,
                        startWithMoveTo = true,
                    )
                    if (drawn) drawPath(stroke.segment, ink, style = style)
                }
            }
        }
    }
}

/**
 * One stroke, measured once. [segment] is scratch space for the partial
 * stroke: only one stroke is ever in flight, but keeping it per stroke
 * costs nothing and avoids a shared buffer being reset under a draw.
 */
internal class StrokeGeometry(
    val path: Path,
    val measure: PathMeasure,
    val length: Float,
) {
    val segment = Path()
}

/**
 * Null when any of [strokePaths] is not path data this build of
 * [PathParser] accepts, so the caller can fall back for that character
 * alone. The parse is per character and happens once, in a `remember`:
 * a malformed row must never be able to take the Kanji tab down.
 */
@Composable
internal fun rememberStrokeGeometry(strokePaths: List<String>): List<StrokeGeometry>? =
    remember(strokePaths) { parseStrokes(strokePaths)?.takeIf { it.isNotEmpty() } }

private fun parseStrokes(strokePaths: List<String>): List<StrokeGeometry>? = try {
    strokePaths.map { data ->
        val path = PathParser().parsePathString(data).toPath()
        val measure = PathMeasure().apply { setPath(path, false) }
        StrokeGeometry(path, measure, measure.length)
    }
} catch (_: Exception) {
    null
}
