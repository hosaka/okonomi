/*
 * Copyright 2026 turtlekazu
 * Copyright 2026 Alex March
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.hosaka.okonomi.ui.furigana

import android.graphics.Paint
import android.text.TextPaint
import android.text.style.LineHeightSpan
import androidx.annotation.Px
import androidx.compose.ui.text.style.LineHeightStyle
import kotlin.math.ceil

/**
 * Compose's line-height rules ([LineHeightStyle]) restated as a
 * `TextView` span: how the spare height between the font's own extent
 * and the requested line height is split above and below the text, and
 * whether the first line's top and the last line's bottom keep their
 * share of it.
 *
 * A `TextView` distributes that spare height its own way, so without
 * this the text a ruby unit draws sits at a different height from the
 * Compose text around it.
 *
 * Vendored from Furiganable; see [FuriganaText] for the provenance.
 */
internal class ComposeLineHeightSpan(
    @Px private val lineHeight: Int,
    private val style: LineHeightStyle?,
) : LineHeightSpan.WithDensity {
    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt,
    ) = apply(text, start, end, fm, null)

    override fun chooseHeight(
        text: CharSequence,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt,
        paint: TextPaint?,
    ) = apply(text, start, end, fm, paint)

    private fun apply(
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt,
        paint: TextPaint?,
    ) {
        val baseMetrics = paint?.fontMetricsInt ?: fm
        val extent = baseMetrics.descent - baseMetrics.ascent
        if (extent <= 0) return

        val alignment = style?.alignment ?: LineHeightStyle.Alignment.Proportional
        val trim = style?.trim ?: LineHeightStyle.Trim.Both

        val (topPad, bottomPad) = when (alignment) {
            LineHeightStyle.Alignment.Center -> centerPadding(extent)
            LineHeightStyle.Alignment.Top -> 0 to (lineHeight - extent)
            LineHeightStyle.Alignment.Bottom -> (lineHeight - extent) to 0
            else -> proportionalPadding(baseMetrics, extent)
        }

        val trimsTop = trim == LineHeightStyle.Trim.FirstLineTop || trim == LineHeightStyle.Trim.Both
        val trimsBottom = trim == LineHeightStyle.Trim.LastLineBottom || trim == LineHeightStyle.Trim.Both
        val top = if (trimsTop && start == 0) 0 else topPad
        val bottom = if (trimsBottom && end == text.length) 0 else bottomPad

        fm.ascent = baseMetrics.ascent - top
        fm.top = baseMetrics.top - top
        fm.descent = baseMetrics.descent + bottom
        fm.bottom = baseMetrics.bottom + bottom
    }

    /** The spare height split in the same ratio the font's own ascent has. */
    private fun proportionalPadding(fm: Paint.FontMetricsInt, extent: Int): Pair<Int, Int> {
        val spare = lineHeight - extent
        if (spare == 0) return 0 to 0
        val top = ceil(spare * (-fm.ascent.toDouble() / extent)).toInt()
        return top to (spare - top)
    }

    private fun centerPadding(extent: Int): Pair<Int, Int> {
        val spare = lineHeight - extent
        if (spare == 0) return 0 to 0
        val top = spare / 2
        return top to (spare - top)
    }
}
