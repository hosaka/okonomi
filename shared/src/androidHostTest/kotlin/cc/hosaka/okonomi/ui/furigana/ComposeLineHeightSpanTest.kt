package cc.hosaka.okonomi.ui.furigana

import android.graphics.Paint
import androidx.compose.ui.text.style.LineHeightStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The arithmetic that puts a `TextView` line where Compose would have
 * put it. It is where a ruby unit's two halves get their vertical
 * placement on Android, it is plain integer maths over font metrics,
 * and it had no assertions on it at all.
 *
 * The font's own extent below is 20px tall (ascent -16, descent 4), so a
 * 30px line has 10px of spare height to distribute — which is the only
 * thing this span decides.
 */
class ComposeLineHeightSpanTest {

    private val lineHeight = 30

    private fun metrics() = Paint.FontMetricsInt().apply {
        ascent = -16
        top = -18
        descent = 4
        bottom = 6
    }

    /**
     * [start] and [end] against a text of [length] are how the span is
     * told which line it is on: the first line starts at 0, the last one
     * ends at the length.
     */
    private fun apply(
        alignment: LineHeightStyle.Alignment,
        trim: LineHeightStyle.Trim,
        start: Int = 5,
        end: Int = 6,
        length: Int = 10,
    ): Paint.FontMetricsInt {
        val metrics = metrics()
        ComposeLineHeightSpan(lineHeight, LineHeightStyle(alignment, trim))
            .chooseHeight("x".repeat(length), start, end, 0, 0, metrics)
        return metrics
    }

    @Test
    fun `centered alignment splits the spare height evenly`() {
        val metrics = apply(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

        assertEquals(-21, metrics.ascent, "5px above")
        assertEquals(9, metrics.descent, "5px below")
        assertEquals(30, metrics.descent - metrics.ascent, "the line is the height asked for")
    }

    /**
     * Proportional gives the top the share the font's own ascent has of
     * its extent: 16 of 20, so 8 of the 10 spare pixels, rounded up.
     */
    @Test
    fun `proportional alignment splits it in the ratio of the font's ascent`() {
        val metrics = apply(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None)

        assertEquals(-24, metrics.ascent)
        assertEquals(6, metrics.descent)
        assertEquals(30, metrics.descent - metrics.ascent)
    }

    @Test
    fun `top alignment puts every spare pixel below the text`() {
        val metrics = apply(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None)

        assertEquals(-16, metrics.ascent, "the text keeps its own ascent")
        assertEquals(14, metrics.descent)
    }

    @Test
    fun `bottom alignment puts every spare pixel above the text`() {
        val metrics = apply(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None)

        assertEquals(-26, metrics.ascent)
        assertEquals(4, metrics.descent, "the text keeps its own descent")
    }

    /**
     * Trimming is what keeps a line of text from carrying half a line of
     * air above the first line and below the last — the reason a ruby
     * unit sits on the same baseline as the text around it.
     */
    @Test
    fun `trimming takes the padding off the first line's top and the last line's bottom`() {
        val first = apply(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, start = 0, end = 4)
        assertEquals(-16, first.ascent, "nothing added above the first line")
        assertEquals(9, first.descent)

        val last = apply(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, start = 6, end = 10)
        assertEquals(-21, last.ascent)
        assertEquals(4, last.descent, "nothing added below the last line")
    }

    @Test
    fun `each trim mode touches only its own end`() {
        val firstLineTop = apply(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.FirstLineTop, start = 0, end = 10)
        assertEquals(-16, firstLineTop.ascent)
        assertEquals(9, firstLineTop.descent, "the last line keeps its padding")

        val lastLineBottom =
            apply(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.LastLineBottom, start = 0, end = 10)
        assertEquals(-21, lastLineBottom.ascent, "the first line keeps its padding")
        assertEquals(4, lastLineBottom.descent)
    }

    /**
     * A line already taller than the height asked for is left alone
     * rather than squeezed: the spare height is negative and nothing is
     * distributed.
     */
    @Test
    fun `a line with no spare height is left as the font drew it`() {
        val metrics = metrics()
        ComposeLineHeightSpan(lineHeight = 20, style = null)
            .chooseHeight("xxxxx", 1, 2, 0, 0, metrics)

        assertEquals(-16, metrics.ascent)
        assertEquals(4, metrics.descent)
    }

    @Test
    fun `a font with no extent at all is left untouched`() {
        val metrics = Paint.FontMetricsInt()

        ComposeLineHeightSpan(lineHeight, style = null).chooseHeight("xxxxx", 1, 2, 0, 0, metrics)

        assertEquals(0, metrics.ascent)
        assertEquals(0, metrics.descent)
    }
}
