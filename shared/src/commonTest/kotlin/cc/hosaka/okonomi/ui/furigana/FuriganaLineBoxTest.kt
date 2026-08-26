package cc.hosaka.okonomi.ui.furigana

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.theme.atJapaneseReadingSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WITH_RUBY = "羊"

private const val WITHOUT_RUBY = "は"

/**
 * That a line with a reading and a line without one occupy the same box.
 *
 * The Phrases tab draws every word of a sentence as its own
 * [FuriganaText] in a row, so two of them sit side by side and any
 * disagreement about vertical geometry is visible as words on different
 * baselines. 羊は草を食べる。put は and を below 羊 and 草 on a device.
 *
 * The defect had two halves and this file can only see one of them,
 * which is worth saying plainly rather than implying otherwise:
 *
 * - **The box** — the ruby-free branch used to take the caller's line
 *   height unchanged while the ruby branch raised it to the floor a
 *   reading needs. That is what is asserted below, and it fails without
 *   the fix.
 * - **Where the glyph sits inside the box** — two causes, both of them
 *   a disagreement between the primary font's metrics and the CJK
 *   fallback's. The word inside a ruby unit used to be drawn by a
 *   platform text view measuring against the primary font while the
 *   line beside it was measured by Compose against the fallback; and,
 *   larger, the inline placeholder used to rewrite the whole line's
 *   metrics to the primary font's before widening them to the line
 *   height, which moved every ordinary character on a ruby line about
 *   two sp up. **Neither reproduces here**, and the reason is worth
 *   stating so nobody adds an assertion for them: Robolectric lays out
 *   one substitute font with no fallback, and with a single font the
 *   two rules give the same answer *identically* — the placeholder
 *   expands symmetrically about the text centre and the line height
 *   distributes symmetrically about the block, and those coincide
 *   whenever there is only one set of metrics in play. The arithmetic
 *   cancels at any font size and any line height. Those halves are
 *   verified by measuring ink extents off a device screenshot, which
 *   is how the five-pixel offset between 食べる's べ and べき's べ was
 *   found in the first place.
 */
@OptIn(ExperimentalTestApi::class)
class FuriganaLineBoxTest : ComposeUiTestBase() {

    /**
     * The case the tab actually asks for. Both pieces are handed a line
     * height above the ruby floor, so the boxes agree on the size
     * itself; what this pins is that they still agree once the two
     * branches have each resolved it.
     */
    @Test
    fun `a word with a reading occupies the same line as one without`() = runComposeUiTest {
        setContent {
            TwoPieces(MaterialTheme.typography.bodyLarge.atJapaneseReadingSize())
        }
        waitForIdle()

        assertLevel()
    }

    /**
     * A caller asking for less room than a reading needs. The ruby
     * branch has always raised it to the floor; the ruby-free branch
     * used to take the number as given, which is a shorter box beside a
     * taller one — 36 against 35 at this size — and words on different
     * lines the moment two of them are laid out in a row.
     */
    @Test
    fun `a line height below what a reading needs is raised for both`() = runComposeUiTest {
        setContent {
            TwoPieces(MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 24.sp))
        }
        waitForIdle()

        assertLevel()
    }

    /**
     * A style stating no line height at all, which is the shape a
     * caller falls into by using a bare `TextStyle`. Both branches have
     * to reach the same floor from nothing.
     */
    @Test
    fun `a style stating no line height leaves both branches level`() = runComposeUiTest {
        setContent {
            TwoPieces(TextStyle(fontSize = 22.sp))
        }
        waitForIdle()

        assertLevel()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.assertLevel() {
        val withRuby = onNodeWithText(WITH_RUBY).layout()
        val withoutRuby = onNodeWithText(WITHOUT_RUBY).layout()

        assertEquals(
            withRuby.size.height,
            withoutRuby.size.height,
            "a word with a reading and one without must occupy the same line box",
        )
        assertEquals(
            withRuby.getLineBaseline(0),
            withoutRuby.getLineBaseline(0),
            "and put their characters on the same baseline inside it",
        )
        assertEquals(
            withRuby.layoutInput.style.lineHeight,
            withoutRuby.layoutInput.style.lineHeight,
            "both branches must resolve the line height the same way",
        )
        assertEquals(
            withRuby.layoutInput.style.lineHeightStyle,
            withoutRuby.layoutInput.style.lineHeightStyle,
            "and position the line against the same reference",
        )
    }
}

@Composable
private fun TwoPieces(style: TextStyle) {
    ScreenHost {
        Row {
            FuriganaText(segments = listOf(FuriganaSegment(WITH_RUBY, "ひつじ")), style = style)
            FuriganaText(segments = listOf(FuriganaSegment(WITHOUT_RUBY)), style = style)
        }
    }
}

/**
 * The layout the node reports, which is where a line height and a
 * baseline can be read; the semantics tree itself carries only the
 * string.
 */
private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
    assertTrue(action?.action?.invoke(results) == true, "the node reports no text layout")
    return results.first()
}
