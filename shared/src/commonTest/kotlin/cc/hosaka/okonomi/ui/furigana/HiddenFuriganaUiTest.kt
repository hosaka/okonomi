package cc.hosaka.okonomi.ui.furigana

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [FuriganaText] with [LocalFuriganaShown] false: the characters alone,
 * in the caller's own line box, keeping only the highlights that lit the
 * characters themselves.
 *
 * That no ruby is drawn is not asserted here — at this SDK a ruby never
 * reaches the semantics tree, so its absence would pass for the wrong
 * reason. `RubyRenderingTest` holds that, on the SDK where it shows.
 */
@OptIn(ExperimentalTestApi::class)
class HiddenFuriganaUiTest : ComposeUiTestBase() {

    /**
     * displayMedium's own 52sp box, not the 66.6sp floor a 45sp reading
     * would need above the line: with nothing drawn there, the floor is a
     * gap.
     */
    @Test
    fun `a hidden reading reserves no room above the line`() = runComposeUiTest {
        var requested = TextStyle.Default
        setContent {
            requested = MaterialTheme.typography.displayMedium
            Hidden {
                FuriganaText(
                    segments = listOf(FuriganaSegment("葡萄棚", "ぶどうだな")),
                    style = requested,
                )
            }
        }
        waitForIdle()

        val style = onNodeWithText("葡萄棚").layout().layoutInput.style
        assertEquals(requested.lineHeight, style.lineHeight)
    }

    /**
     * そうさい lit part of 相殺関税's undivided reading. Moved onto the
     * characters below it, the highlight would claim 相殺関 — or any four
     * of them — took that reading, which is the division nobody made.
     */
    @Test
    fun `a highlight on a hidden reading is dropped`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            Hidden {
                FuriganaText(
                    segments = listOf(
                        FuriganaSegment(
                            text = "相殺関税",
                            reading = "そうさいかんぜい",
                            highlight = FuriganaSegment.Highlight.PartOfReading(0..3),
                        ),
                    ),
                    highlightStyle = SpanStyle(color = primary),
                )
            }
        }
        waitForIdle()

        val text = onNodeWithText("相殺関税").annotatedText()
        assertTrue(text.spanStyles.none { it.item.color == primary }, "${text.spanStyles}")
    }

    /** What lit the characters themselves is still on screen, and still lit. */
    @Test
    fun `a highlight on the characters survives`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            Hidden {
                FuriganaText(
                    segments = listOf(
                        FuriganaSegment("食", "た", FuriganaSegment.Highlight.Whole),
                        FuriganaSegment("べ", highlight = FuriganaSegment.Highlight.Whole),
                        FuriganaSegment("る"),
                    ),
                    highlightStyle = SpanStyle(color = primary),
                )
            }
        }
        waitForIdle()

        val text = onNodeWithText("食べる").annotatedText()
        val lit = text.spanStyles.filter { it.item.color == primary }
        assertEquals("食べ", lit.joinToString("") { text.text.substring(it.start, it.end) })
    }
}

@Composable
private fun Hidden(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFuriganaShown provides false, content = content)
}

private fun SemanticsNodeInteraction.annotatedText(): AnnotatedString =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.first()
        ?: error("node carries no text")

private fun SemanticsNodeInteraction.layout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
    assertTrue(action?.action?.invoke(results) == true, "the node reports no text layout")
    return results.first()
}
