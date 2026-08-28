package cc.hosaka.okonomi.feature.kanji

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.radical.RadicalRoute
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.theme.OkonomiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_kanji_detail_open
import okonomi.shared.generated.resources.entry_kanji_detail_title
import okonomi.shared.generated.resources.entry_kanji_radical_search
import okonomi.shared.generated.resources.entry_kanji_stroke_order_play
import okonomi.shared.generated.resources.list_card_copy
import org.jetbrains.compose.resources.stringResource

/**
 * The overlay as a reader reaches it: a tap on the card, a tap on a
 * radical, a long press that must still copy, and the cards that offer
 * no overlay at all.
 *
 * **Two rows of the spec's matrix are deliberately not here.** Dismissal
 * by a tap outside the surface and by a system back press are
 * `DialogProperties` defaults — `dismissOnClickOutside` and
 * `dismissOnBackPress`, both on and both left at their default in
 * [KanjiDetailDialog]. Nothing in this repo can dispatch a system back
 * press, and a dialog scrim carries no semantics node to aim
 * `performClick` at, so an assertion written for either would pass
 * whether the properties held or not. What they do once fired —
 * `onDismissRequest` clearing the selection — is covered by
 * [KanjiDetailDialogStateTest]; the gestures themselves are a device
 * check, listed in the spec's manual steps.
 *
 * Controls are scoped by their click LABEL, since the card, the
 * stroke-order slot and every radical are all clickable in one tree and
 * a count of clickable nodes would say nothing about which was found.
 * Each of them is its own node in the merged tree despite the card
 * merging its descendants, because a `clickable` is a merging boundary.
 * Content, on the other hand, is asserted as TEXT: what this change did
 * was move two sections off the card, and only their text can say
 * whether they moved. Asserting the absence of a radical's *click
 * label* would pass with the old unclickable radical line restored,
 * which is a proxy for the claim rather than the claim.
 */
@OptIn(ExperimentalTestApi::class)
class KanjiDetailDialogUiTest : ComposeUiTestBase() {

    /**
     * The character carries stroke data here on purpose: the card then
     * draws the diagram instead of printing its literal, so the literal
     * on screen after the tap can only be the overlay's own heading.
     */
    @Test
    fun `tapping a card opens the overlay with its literal nanori and radicals`() =
        runComposeUiTest {
            val labels = Labels()
            setContent {
                labels.read()
                KanjiListUnderTest(listOf(shoku(strokePaths = STROKES)))
            }

            // The move itself: neither section is on the card any more,
            // and the overlay's heading is not on screen either.
            onAllNodesWithText(LITERAL).assertCountEquals(0)
            onAllNodesWithText(NANORI).assertCountEquals(0)
            onAllNodesWithText(RADICAL).assertCountEquals(0)

            onNode(hasClickLabel(labels.open)).performClick()

            onNodeWithText(LITERAL).assertIsDisplayed()
            onNodeWithText(NANORI).assertIsDisplayed()
            onNodeWithText(RADICAL).assertIsDisplayed()
            onNode(hasClickLabel(labels.radical)).assertIsDisplayed()
        }

    /**
     * What a screen reader is told when the overlay arrives. The card
     * that named the character is behind the scrim by then, so the pane
     * title and the heading are the only two things left saying which
     * kanji this is.
     *
     * The diagram fixture again, so the only literal in the tree is the
     * overlay's heading rather than the card's own.
     */
    @Test
    fun `the overlay announces itself as a pane and heads itself with the literal`() =
        runComposeUiTest {
            val labels = Labels()
            setContent {
                labels.read()
                KanjiListUnderTest(listOf(shoku(strokePaths = STROKES)))
            }

            onNode(hasClickLabel(labels.open)).performClick()

            onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, labels.title))
                .assertExists()
            onNodeWithText(LITERAL).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }

    @Test
    fun `tapping a radical opens its kanji screen and closes the overlay`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku()), navigation = navigation)
        }
        onNode(hasClickLabel(labels.open)).performClick()

        onNode(hasClickLabel(labels.radical)).performClick()

        // radkfile's own value, not a CJK Radical Supplement rewrite of
        // it: the lookup has to be given a character the dictionary
        // actually carries.
        //
        // A RadicalRoute rather than a SearchRoute. A radical tap asks
        // which kanji are built from it, which a word search answers
        // badly at best and not at all for the 61 radicals JMdict has no
        // entry for.
        assertEquals<List<Route>>(listOf(RadicalRoute(RADICAL)), navigation.navigated)
        onNodeWithText(NANORI).assertDoesNotExist()
    }

    /**
     * The spec's Always constraint, and this change is what put it at
     * risk: the card went from a placeholder `onClick` to a real one, so
     * `combinedClickable` now has two callbacks to resolve between and a
     * long press could plausibly arrive as a tap.
     */
    @Suppress("DEPRECATION")
    @Test
    fun `long-pressing a card copies its character without opening the overlay`() =
        runComposeUiTest {
            val labels = Labels()
            val clipboard = RecordingClipboard()
            setContent {
                labels.read()
                CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                    KanjiListUnderTest(listOf(shoku()))
                }
            }

            onNode(hasLongClickLabel(labels.copy)).performTouchInput { longClick() }

            assertEquals(LITERAL, clipboard.copied?.text)
            onNodeWithText(NANORI).assertDoesNotExist()
        }

    /**
     * And the card that offers no overlay still copies. This is the one
     * `ListCard` resolves to `onClick ?: {}`, so it is the card whose
     * gesture handling the change could most easily have hollowed out.
     */
    @Suppress("DEPRECATION")
    @Test
    fun `long-pressing a card with no overlay still copies its character`() = runComposeUiTest {
        val labels = Labels()
        val clipboard = RecordingClipboard()
        setContent {
            labels.read()
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                KanjiListUnderTest(listOf(shoku(nameReadings = emptyList(), radicals = emptyList())))
            }
        }

        onNode(hasLongClickLabel(labels.copy)).performTouchInput { longClick() }

        assertEquals(LITERAL, clipboard.copied?.text)
    }

    /**
     * The overlay holds nanori and radicals and nothing else, so a
     * character with neither would open an empty surface. It offers no
     * way to try.
     *
     * The card still takes a bare `onClick` from `ListCard` — a card
     * that only copies answers a tap with a ripple — so this asserts on
     * the label, which is what an accessibility service would announce
     * and what is actually absent.
     */
    @Test
    fun `a character with neither nanori nor radicals offers no way to open the overlay`() =
        runComposeUiTest {
            val labels = Labels()
            setContent {
                labels.read()
                KanjiListUnderTest(listOf(shoku(nameReadings = emptyList(), radicals = emptyList())))
            }

            onAllNodes(hasClickLabel(labels.open)).assertCountEquals(0)
            // And the card itself is still drawn, so this cannot pass by
            // the list having rendered nothing at all.
            onNodeWithText(MEANING).assertIsDisplayed()
        }

    /** Radicals alone are worth opening for, nanori being the rarer field. */
    @Test
    fun `a character with radicals but no nanori still opens`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku(nameReadings = emptyList())))
        }

        onNode(hasClickLabel(labels.open)).performClick()

        onNodeWithText(RADICAL).assertIsDisplayed()
        // The matrix row is "omits the Nanori line entirely", so the
        // absence is half the claim: without this the test would pass
        // with the line rendered above the radicals.
        onAllNodesWithText(NANORI).assertCountEquals(0)
    }

    /** And nanori alone opens an overlay with no radicals line in it. */
    @Test
    fun `a character with nanori but no radicals still opens`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku(radicals = emptyList())))
        }

        onNode(hasClickLabel(labels.open)).performClick()

        onNodeWithText(NANORI).assertIsDisplayed()
        onAllNodesWithText(RADICAL).assertCountEquals(0)
    }

    /**
     * The stroke-order slot is a clickable child of a now-clickable
     * card. Pointer input is consumed by the innermost handler that
     * takes it, so the diagram's tap stops at the diagram and replaying
     * the animation must not also open the overlay.
     *
     * Semantics merging is a separate question and does not soften
     * this: the card's `combinedClickable` DOES merge its descendants
     * (it reports `MergeDescendants = true`), but a child that is itself
     * a merging boundary — every `clickable`, so the slot and each
     * radical chip — stays its own node in the merged tree, which is why
     * the play label is still addressable here.
     *
     * The animation itself is invisible to Robolectric, which renders no
     * canvas; what this checks is that the tap went to the slot and no
     * further.
     */
    @Test
    fun `tapping the stroke diagram replays it without opening the overlay`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku(strokePaths = STROKES)))
        }

        onNode(hasClickLabel(labels.play)).performClick()

        onNodeWithText(NANORI).assertDoesNotExist()
    }

    /**
     * Composing the list is not a tap. The two positive assertions are
     * what stop this passing on a render of nothing at all: the card is
     * on screen and reporting the action that opens the overlay, so the
     * absences below are absences of something genuinely reachable.
     */
    @Test
    fun `a rendered card offers the tap without opening or navigating on its own`() =
        runComposeUiTest {
            val navigation = RecordingNavigationController()
            val labels = Labels()
            setContent {
                labels.read()
                KanjiListUnderTest(listOf(shoku()), navigation = navigation)
            }

            onNodeWithText(MEANING).assertIsDisplayed()
            onNode(hasClickLabel(labels.open)).assertIsDisplayed()

            onAllNodesWithText(NANORI).assertCountEquals(0)
            assertEquals<List<Route>>(emptyList(), navigation.navigated)
        }
}

/**
 * The labels and titles under test, read inside composition because they
 * come from resources. Held on one object so a test names them rather
 * than repeating five `lateinit` declarations.
 */
private class Labels {
    lateinit var open: String
    lateinit var radical: String
    lateinit var play: String
    lateinit var copy: String
    lateinit var title: String

    @Composable
    fun read() {
        open = stringResource(Res.string.entry_kanji_detail_open, LITERAL)
        radical = stringResource(Res.string.entry_kanji_radical_search, RADICAL)
        play = stringResource(Res.string.entry_kanji_stroke_order_play)
        copy = stringResource(Res.string.list_card_copy)
        title = stringResource(Res.string.entry_kanji_detail_title, LITERAL)
    }
}

/**
 * Matches a node by the label on its click action. `hasClickAction()`
 * would match the card, the stroke slot and every radical alike; the
 * label says which of them was found.
 */
private fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("click action labelled \"$label\"") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

private fun hasLongClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("long click action labelled \"$label\"") { node ->
        node.config.getOrNull(SemanticsActions.OnLongClick)?.label == label
    }

/**
 * Reads back what the card put on the clipboard. Built on the
 * deprecated `ClipboardManager` because that is what `rememberClipboardCopy`
 * is built on, and that file records why the replacement is unreachable
 * from common code at Compose Multiplatform 1.11.1.
 */
@Suppress("DEPRECATION")
private class RecordingClipboard : ClipboardManager {
    // Not named `text`: its getter would clash with the interface's own
    // `getText()` on the JVM.
    var copied: AnnotatedString? = null
        private set

    override fun setText(annotatedString: AnnotatedString) {
        copied = annotatedString
    }

    override fun getText(): AnnotatedString? = copied
}

private const val LITERAL = "食"

private const val NANORI = "ぐい"

private const val MEANING = "eat"

/**
 * 人 rather than 食: radkfile lists 食 itself among 食's radicals, and a
 * radical equal to the literal would let the radical assertions pass by
 * finding the overlay's own heading instead of the chip.
 */
private const val RADICAL = "人"

private val STROKES = listOf(
    "M52.75,10.5c0.11,0.98-0.19,2.67-0.97,3.93",
    "M52.75,16.25c5.09,4.8,25.71,19.61,33.7,24.9",
)

private fun shoku(
    nameReadings: List<String> = listOf(NANORI),
    radicals: List<String> = listOf(RADICAL),
    strokePaths: List<String> = emptyList(),
) = KanjiCharacter(
    literal = LITERAL,
    strokeCount = 9,
    grade = 2,
    jlpt = 4,
    freq = 382,
    onReadings = listOf("ショク"),
    kunReadings = listOf("た.べる"),
    nameReadings = nameReadings,
    meanings = listOf(MEANING),
    radicals = radicals,
    strokePaths = strokePaths,
)

@Composable
private fun KanjiListUnderTest(
    characters: List<KanjiCharacter>,
    navigation: RecordingNavigationController = RecordingNavigationController(),
) {
    ScreenHost(navigation = navigation) {
        OkonomiTheme {
            Surface {
                KanjiTabContent(
                    state = KanjiTabState(content = KanjiTabContentState.Ready(characters)),
                    contentPadding = PaddingValues(),
                )
            }
        }
    }
}
