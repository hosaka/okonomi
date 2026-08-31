package cc.hosaka.okonomi.feature.kanji

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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
import okonomi.shared.generated.resources.entry_kanji_detail_close
import okonomi.shared.generated.resources.entry_kanji_detail_open
import okonomi.shared.generated.resources.entry_kanji_detail_title
import okonomi.shared.generated.resources.entry_kanji_radical_search
import okonomi.shared.generated.resources.entry_kanji_stroke_order_play
import okonomi.shared.generated.resources.list_card_copy
import org.jetbrains.compose.resources.stringResource

/**
 * The overlay as a reader reaches it: a tap on the card, a tap on a
 * radical, a tap outside the surface, a long press that must still copy,
 * and the cards that offer no overlay at all.
 *
 * **Only system back is missing from the spec's matrix now.** Nothing in
 * this repo can dispatch the press, so an assertion written for it would
 * pass whether `dismissOnBackPress` held or not; what it does once fired
 * — `onDismissRequest` clearing the selection — is covered by
 * [KanjiDetailDialogStateTest], and the press itself is a device check
 * in the spec's manual steps.
 *
 * The outside tap used to be listed here as untestable alongside it, on
 * the grounds that a platform scrim carries no semantics node to aim at.
 * That stopped being true when [KanjiDetailDialog] took the dismissal
 * off `dismissOnClickOutside` — which `usePlatformDefaultWidth = false`
 * had made inert — and onto a layer of its own.
 *
 * The gesture and the screen reader's route to it are separate claims
 * and are tested separately. Three tests inject at a COORDINATE, so what
 * they exercise is the hit testing: a corner and a margin strip reach
 * the layer, and a point inside the surface does not. A fourth fires the
 * layer's `OnClick` semantics action instead, because a service performs
 * the action and never touches the screen — a coordinate test passes
 * with that action body emptied.
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

            onNode(hasPaneTitle(labels.title)).assertExists()
            onNodeWithText(LITERAL).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }

    /**
     * A chip is inside the surface, so this is also the matrix row that
     * says the dismiss layer must not steal it: exactly one navigation,
     * from the chip's own `onClick`, and not a dismissal that swallowed
     * the tap on its way past.
     */
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
     * The row the overlay shipped without. The tap is injected at a
     * COORDINATE inside the dismiss layer rather than through its click
     * action: the layer's semantics action would fire from anywhere and
     * says nothing about where the touch landed.
     *
     * 1.dp in from the layer's own top-left corner. The surface is
     * centred and wraps its content, so that point is outside it on
     * every window this runs in.
     *
     * This is NOT evidence for the margin claim the next test makes, and
     * should not be read as covering it. With [DIALOG_MARGIN] moved back
     * onto the Box, `matchParentSize` insets the layer with it and this
     * corner tap still dismisses — the two that go red are the margin
     * test and `the overlay fills the window less a margin either side`,
     * measured. The rows are separate because the regression is.
     */
    @Test
    fun `tapping a corner outside the surface closes the overlay`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku()))
        }
        onNode(hasClickLabel(labels.open)).performClick()
        onNodeWithText(NANORI).assertIsDisplayed()

        onNode(hasContentDescription(labels.close)).performTouchInput {
            click(Offset(1.dp.toPx(), 1.dp.toPx()))
        }

        onNodeWithText(NANORI).assertDoesNotExist()
    }

    /**
     * The strip beside the surface, which is the half of this a layout
     * change would silently take away: [DIALOG_MARGIN] is padding on the
     * `Surface` rather than on the Box around it, so the strip belongs
     * to the layer. Moved back onto the Box the overlay looks identical
     * and the strip stops dismissing, and this is the only test that
     * notices.
     *
     * The x comes from the surface's own left bound rather than from
     * half of [DIALOG_MARGIN], which would be a second copy of a
     * constant with no way to keep the two in step, and would stop being
     * the strip at all on a window wider than `screenMaxWidth` plus the
     * margins. 2.dp outside the surface is the strip wherever the
     * surface ends up.
     *
     * That this reads a bound OUTSIDE the surface is itself the answer
     * to a question the diff raised: the pane's semantics node sits
     * INSIDE the padding in the modifier chain, so its bounds are the
     * surface proper and exclude the strips. Were it the other way the
     * offset here would be negative and this test would not run.
     *
     * Vertically centred, level with the surface, so the horizontal
     * margin is the only thing that can make the point miss it.
     */
    @Test
    fun `tapping the margin beside the surface closes the overlay`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku()))
        }
        onNode(hasClickLabel(labels.open)).performClick()
        onNodeWithText(NANORI).assertIsDisplayed()

        val surfaceLeft = onNode(hasPaneTitle(labels.title)).getUnclippedBoundsInRoot().left
        onNode(hasContentDescription(labels.close)).performTouchInput {
            click(Offset((surfaceLeft - 2.dp).toPx(), centerY))
        }

        onNodeWithText(NANORI).assertDoesNotExist()
    }

    /**
     * The other side of it: a point the surface covers must stop AT the
     * surface rather than reaching the layer beneath. Material3 gives a
     * `Surface` an empty `pointerInput` even with no `onClick` (checked
     * at Compose Multiplatform 1.11.1, material3 1.11.0-alpha07), and
     * that is the only thing standing between the reader and an overlay
     * that closes whenever it is touched.
     *
     * Anchored on the surface's own pane node, not on the dismiss layer:
     * aimed at the layer this would redden when the layer was deleted,
     * which is another test's job and not this one's.
     *
     * 2.dp inside the surface's left edge, at its vertical centre. That
     * is inside the content padding rather than on any control, which is
     * the spec's "the surface, its padding, or a control inside it" and
     * the band a fall-through would show up in first — the centre of the
     * window would instead land on a radical chip in this fixture, where
     * the chip's own handler would mask a layer that had taken the tap.
     * `navigation.navigated` separates those two: the surface swallowing
     * the touch leaves it empty, the chip handling it does not.
     */
    @Test
    fun `tapping the surface leaves the overlay open`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku()), navigation = navigation)
        }
        onNode(hasClickLabel(labels.open)).performClick()

        onNode(hasPaneTitle(labels.title)).performTouchInput {
            click(Offset(2.dp.toPx(), centerY))
        }

        onNodeWithText(NANORI).assertIsDisplayed()
        assertEquals<List<Route>>(emptyList(), navigation.navigated)
    }

    /**
     * The screen reader's route out, which is a different claim from the
     * gesture: a service performs the node's action and never touches
     * the screen, so emptying that action body leaves all three
     * coordinate tests above green.
     *
     * The traversal index is asserted here because nothing else would
     * notice the line going. What it is FOR — the layer being offered
     * after the details rather than before — is not what this checks:
     * Material3's `Surface` sets the deprecated `IsContainer` key rather
     * than `IsTraversalGroup`, and iOS orders its accessibility elements
     * through another implementation again, so the resulting order is a
     * device check on both platforms. This pins the property, and says
     * so rather than implying the order was observed.
     */
    @Test
    fun `the close action dismisses the overlay for a screen reader`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku()))
        }
        onNode(hasClickLabel(labels.open)).performClick()
        onNodeWithText(NANORI).assertIsDisplayed()

        onNode(hasContentDescription(labels.close))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.TraversalIndex, 1f))
            .performSemanticsAction(SemanticsActions.OnClick)

        onNodeWithText(NANORI).assertDoesNotExist()
    }

    /**
     * The layout the dismiss fix was required not to move: this change
     * took the margin that sets the surface's width off the Box and put
     * it on the `Surface`, which is arithmetically the same, and this is
     * what says so rather than the argument that it is. Removing the
     * padding shows up here, and so does moving it back onto the Box.
     *
     * The window is read off the dismiss layer, which is
     * `matchParentSize` on the Box that fills the dialog window, so its
     * bounds ARE the window's. That deliberately ties this test to the
     * layer's existence — the alternative, an `isRoot()` node, belongs
     * to whichever of the two windows the matcher happened to return
     * first, and comparing a bound from the app's window against one
     * from the dialog's is not a measurement of anything.
     *
     * **Three things it does NOT pin, measured rather than assumed:**
     *
     * - The value of [DIALOG_MARGIN]. The constant is on both sides of
     *   the assertion, so changing it to 32.dp keeps this green. What is
     *   pinned is the relationship, which is the part a later edit
     *   breaks by accident; the value is an Ask First change in the spec.
     * - The `widthIn`-before-`fillMaxWidth` ordering the surface's own
     *   comment warns about. Swapping the two keeps this green, because
     *   the cap only bites on a window wider than `screenMaxWidth` and
     *   this one is not. On a tablet it would matter; here it cannot.
     * - `usePlatformDefaultWidth = false`, the `686bd9c` fix itself.
     *   Turning it back on changes nothing measurable here: Robolectric's
     *   window is 320.dp and the platform gives a dialog all of it, so
     *   both roots stay one width and the surface keeps its margins.
     *   That revert is caught by review, not by this suite.
     * - `動`'s six radicals staying on one row, which is what the widths
     *   were FOR. Robolectric lays every glyph out to zero width, so
     *   chip sizes and the wrap they cause are invisible here and stay a
     *   device check in the spec's manual steps.
     *
     * What is left is the margin arithmetic, which is the part a later
     * edit breaks without meaning to.
     */
    @Test
    fun `the overlay fills the window less a margin either side`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            KanjiListUnderTest(listOf(shoku()))
        }
        onNode(hasClickLabel(labels.open)).performClick()

        val window = onNode(hasContentDescription(labels.close)).getUnclippedBoundsInRoot()
        val surface = onNode(hasPaneTitle(labels.title)).getUnclippedBoundsInRoot()

        assertEquals(DIALOG_MARGIN, surface.left - window.left)
        assertEquals(DIALOG_MARGIN, window.right - surface.right)
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
    lateinit var close: String

    @Composable
    fun read() {
        open = stringResource(Res.string.entry_kanji_detail_open, LITERAL)
        radical = stringResource(Res.string.entry_kanji_radical_search, RADICAL)
        play = stringResource(Res.string.entry_kanji_stroke_order_play)
        copy = stringResource(Res.string.list_card_copy)
        title = stringResource(Res.string.entry_kanji_detail_title, LITERAL)
        close = stringResource(Res.string.entry_kanji_detail_close, LITERAL)
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

/**
 * Matches the overlay's surface, which is the only node carrying a pane
 * title.
 *
 * Its bounds are the surface PROPER and exclude the [DIALOG_MARGIN]
 * strips, which is what lets the tests above aim relative to its edges.
 * The caller's `padding` is a layout modifier outside the semantics node
 * in the chain, so the node reports the padded-in size rather than the
 * outer one: measured at 24.dp..296.dp in a 320.dp window, not
 * 0.dp..320.dp.
 */
private fun hasPaneTitle(title: String): SemanticsMatcher =
    SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, title)

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
