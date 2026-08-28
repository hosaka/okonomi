package cc.hosaka.okonomi.feature.radical

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.KanjiHit
import cc.hosaka.okonomi.feature.navigation.state.LoadState
import cc.hosaka.okonomi.ui.SEARCH_FIELD_ICON_TAG
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.theme.OkonomiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.radical_back
import okonomi.shared.generated.resources.radical_empty
import okonomi.shared.generated.resources.radical_error
import okonomi.shared.generated.resources.radical_kanji_search
import okonomi.shared.generated.resources.radical_retry
import okonomi.shared.generated.resources.radical_title
import okonomi.shared.generated.resources.search_clear
import okonomi.shared.generated.resources.search_options
import org.jetbrains.compose.resources.stringResource

private const val RADICAL = "阝"

private val HITS = listOf(
    KanjiHit(literal = "院", strokeCount = 10, freq = 382),
    KanjiHit(literal = "部", strokeCount = 11, freq = 115),
    KanjiHit(literal = "陛", strokeCount = 10, freq = null),
)

/**
 * The radical screen as a reader meets it: a grid of characters, and a
 * bar above it that must not look or behave like the search field it
 * sits in place of.
 *
 * The bar's claims are asserted three different ways because they are
 * three different facts. Not editable is a semantics fact (`hasSetText`,
 * and the field reporting itself disabled); no clear and no overflow are
 * absences of nodes carrying known content descriptions; no search icon
 * is a test tag, because the icon is decorative and puts nothing else in
 * the tree — see `SearchFieldIcon`.
 *
 * All three are absence assertions, and an absence passes just as
 * happily when the thing was never built. `SearchTextFieldUiTest` holds
 * the other side of each: that the icon IS drawn by default and carries
 * that tag, and that the clear action IS drawn on a field that can be
 * cleared.
 *
 * **The recessed surface tone is deliberately not asserted.** It is a
 * background colour drawn by a modifier, which reaches no semantics node,
 * and an assertion written for it here would pass whether the tone
 * changed or not. It is in the spec's manual checks instead.
 *
 * Navigation is likewise not asserted here: the screen is a pure
 * renderer and chooses no routes. This covers that a tap reaches
 * `onKanjiClick` with the tapped cell's literal;
 * [RadicalStateProducerTest] covers that the callback pushes
 * `SearchRoute(literal)`.
 */
@OptIn(ExperimentalTestApi::class)
class RadicalScreenUiTest : ComposeUiTestBase() {

    private class Labels {
        var title = ""
        var back = ""
        var clear = ""
        var options = ""
        var retry = ""
        var empty = ""
        var error = ""

        @Composable
        fun read() {
            title = stringResource(Res.string.radical_title, RADICAL)
            back = stringResource(Res.string.radical_back)
            clear = stringResource(Res.string.search_clear)
            options = stringResource(Res.string.search_options)
            retry = stringResource(Res.string.radical_retry)
            empty = stringResource(Res.string.radical_empty)
            error = stringResource(Res.string.radical_error)
        }

        @Composable
        fun kanji(literal: String) = stringResource(Res.string.radical_kanji_search, literal)
    }

    @Composable
    private fun ScreenUnderTest(state: RadicalState) {
        ScreenHost {
            OkonomiTheme {
                RadicalScreen(state = state)
            }
        }
    }

    private fun readyState(
        hits: List<KanjiHit> = HITS,
        onKanjiClick: (String) -> Unit = {},
        onBack: () -> Unit = {},
    ) = RadicalState(
        radical = RADICAL,
        kanji = LoadState.Ready(hits),
        onBack = onBack,
        onKanjiClick = onKanjiClick,
    )

    @Test
    fun `the grid shows every kanji of the radical`() = runComposeUiTest {
        setContent {
            ScreenUnderTest(readyState())
        }

        HITS.forEach { hit ->
            onNodeWithText(hit.literal).assertIsDisplayed()
        }
    }

    @Test
    fun `tapping a kanji reports that character`() = runComposeUiTest {
        val tapped = mutableListOf<String>()
        val labels = Labels()
        var label = ""
        setContent {
            labels.read()
            label = labels.kanji("院")
            ScreenUnderTest(readyState(onKanjiClick = { tapped += it }))
        }

        onNode(hasClickLabel(label)).performClick()

        assertEquals(listOf("院"), tapped)
    }

    /**
     * Not merely a text field that refuses input — no text field at all.
     * `EditableText` is what a `BasicTextField` puts on its node whether
     * it is enabled or not, so its absence is the claim; asserting only
     * that the node reports itself disabled would pass on a bar that
     * still announced itself as an edit box.
     */
    @Test
    fun `the bar names the radical and is not a text field at all`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            ScreenUnderTest(readyState())
        }

        onNodeWithText(labels.title).assertIsDisplayed()
        onNodeWithText(labels.title)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText))
        onNode(hasSetTextAction()).assertDoesNotExist()
    }

    /**
     * What a screen reader is told. The pill is a disabled text field,
     * which announces itself as an edit box and nothing more, so without
     * these two a reader arriving from the overlay is told neither which
     * radical the screen is about nor that the bar is its title.
     */
    @Test
    fun `the screen announces itself by the radical and heads the bar`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            ScreenUnderTest(readyState())
        }

        onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, labels.title))
            .assertExists()
        onNodeWithText(labels.title)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `the bar carries no search icon no clear action and no overflow menu`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            ScreenUnderTest(readyState())
        }

        onNodeWithTag(SEARCH_FIELD_ICON_TAG, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithContentDescription(labels.clear).assertDoesNotExist()
        onNodeWithContentDescription(labels.options).assertDoesNotExist()
        // And the bar itself is drawn, so none of the three can pass by
        // the screen having rendered nothing at all.
        onNodeWithText(labels.title).assertIsDisplayed()
    }

    @Test
    fun `the back control leaves the screen`() = runComposeUiTest {
        var backs = 0
        val labels = Labels()
        setContent {
            labels.read()
            ScreenUnderTest(readyState(onBack = { backs++ }))
        }

        onNodeWithContentDescription(labels.back).performClick()

        assertEquals(1, backs)
    }

    /**
     * A failed lookup leaves the screen standing with a way to try
     * again, rather than an empty grid the reader cannot tell from a
     * radical with no kanji.
     */
    @Test
    fun `a failed lookup offers a retry rather than an empty grid`() = runComposeUiTest {
        var retries = 0
        val labels = Labels()
        setContent {
            labels.read()
            ScreenUnderTest(
                RadicalState(
                    radical = RADICAL,
                    onBack = {},
                    onKanjiClick = {},
                    kanji = LoadState.Error(onRetry = { retries++ }),
                ),
            )
        }

        onNodeWithText(labels.error).assertIsDisplayed()
        onNodeWithText(labels.retry).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `a radical with no kanji says so rather than showing a blank grid`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            ScreenUnderTest(readyState(hits = emptyList()))
        }

        onNodeWithText(labels.empty).assertIsDisplayed()
        onNodeWithText(labels.error).assertDoesNotExist()
    }
}

/**
 * Matches a node by the label on its click action. `hasClickAction()`
 * would match the back control and every character alike; the label says
 * which of them was found.
 */
private fun hasClickLabel(label: String): SemanticsMatcher =
    SemanticsMatcher("click action labelled \"$label\"") { node ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }
