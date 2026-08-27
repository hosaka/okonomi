package cc.hosaka.okonomi.feature.kanji

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.theme.OkonomiTheme
import kotlin.test.Test
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_kanji_stroke_order_of
import okonomi.shared.generated.resources.entry_kanji_stroke_order_play
import org.jetbrains.compose.resources.stringResource

/**
 * What a host test can see of the stroke-order slot.
 *
 * Not the ink: Robolectric renders no canvas, so the strokes, their
 * order and their timing are invisible here and are checked on a device
 * or not at all (see [StrokeOrderDiagram]). What it can see is the
 * semantics, and the semantics carry the promise: a slot that draws
 * something exposes a click action labelled with what the tap does, and
 * a slot with nothing in it must not, or a screen-reader user is offered
 * a control that does nothing.
 *
 * Every assertion here is scoped by that click LABEL rather than by
 * counting clickable nodes in the card. A count would pass today only
 * because nothing else in [KanjiCard] happens to be clickable, and would
 * start lying the moment something was — while the label belongs to this
 * slot and to nothing else on the screen.
 */
@OptIn(ExperimentalTestApi::class)
class StrokeOrderSlotUiTest : ComposeUiTestBase() {

    @Test
    fun `a character with stroke data gets a slot the reader can tap`() = runComposeUiTest {
        lateinit var description: String
        lateinit var playLabel: String
        setContent {
            description = stringResource(Res.string.entry_kanji_stroke_order_of, LITERAL)
            playLabel = stringResource(Res.string.entry_kanji_stroke_order_play)
            CardUnderTest(listOf(FIRST_STROKE, SECOND_STROKE))
        }

        onNodeWithContentDescription(description).assertIsDisplayed().assertHasClickAction()
        // The diagram draws the character, so the card must not also
        // print it: that duplication is what this slot replaced.
        onNodeWithText(LITERAL).assertDoesNotExist()
        // The label is not decoration: it is the only thing that tells a
        // screen reader what tapping the diagram does, and a click action
        // with no label announces nothing but "button".
        onNode(hasClickLabel(playLabel)).assertIsDisplayed()
    }

    @Test
    fun `a character KanjiVG does not carry offers no way to replay it`() = runComposeUiTest {
        lateinit var description: String
        lateinit var playLabel: String
        setContent {
            description = stringResource(Res.string.entry_kanji_stroke_order_of, LITERAL)
            playLabel = stringResource(Res.string.entry_kanji_stroke_order_play)
            CardUnderTest(emptyList())
        }

        onAllNodes(hasClickLabel(playLabel)).assertCountEquals(0)
        onNodeWithContentDescription(description).assertDoesNotExist()
        // With no diagram the literal is the only thing identifying the
        // card, so it comes back rather than leaving a nameless square.
        onNodeWithText(LITERAL).assertIsDisplayed()
    }

    /**
     * The runtime half of the malformed-data row in the spec's matrix: a
     * `d` string PathParser rejects falls back to the same empty slot,
     * rather than presenting a tap that would replay nothing.
     */
    @Test
    fun `a character whose stored path data will not parse falls back to the empty slot`() = runComposeUiTest {
        lateinit var description: String
        lateinit var playLabel: String
        setContent {
            description = stringResource(Res.string.entry_kanji_stroke_order_of, LITERAL)
            playLabel = stringResource(Res.string.entry_kanji_stroke_order_play)
            CardUnderTest(listOf(FIRST_STROKE, "this is not path data"))
        }

        onAllNodes(hasClickLabel(playLabel)).assertCountEquals(0)
        onNodeWithContentDescription(description).assertDoesNotExist()
        // With no diagram the literal is the only thing identifying the
        // card, so it comes back rather than leaving a nameless square.
        onNodeWithText(LITERAL).assertIsDisplayed()
    }
}

/**
 * Matches the slot by the label on its click action. `hasClickAction()`
 * alone would match any clickable node the card grew later; this matches
 * the one whose tap is announced as replaying the stroke order, which is
 * the thing under test.
 */
private fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("click action labelled \"$label\"") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

private const val LITERAL = "食"

private const val FIRST_STROKE = "M52.75,10.5c0.11,0.98-0.19,2.67-0.97,3.93"

private const val SECOND_STROKE = "M52.75,16.25c5.09,4.8,25.71,19.61,33.7,24.9"

@Composable
private fun CardUnderTest(strokePaths: List<String>) {
    OkonomiTheme {
        Surface {
            KanjiCard(
                KanjiCharacter(
                    literal = LITERAL,
                    strokeCount = 9,
                    grade = 2,
                    jlpt = 4,
                    freq = 382,
                    onReadings = listOf("ショク"),
                    kunReadings = listOf("た.べる"),
                    nameReadings = emptyList(),
                    meanings = listOf("eat"),
                    radicals = listOf("人"),
                    strokePaths = strokePaths,
                ),
            )
        }
    }
}
