package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.ScreenHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val GAKKOU = BreakdownWord(text = "学校", reading = "がっこう", entryId = 1_301_230L)

private val DE = BreakdownWord(text = "で", reading = null, entryId = 2_028_980L)

private val HANASHI = BreakdownWord(text = "話", reading = "はなし", entryId = 1_589_580L)

private val WO = BreakdownWord(text = "を", reading = null, entryId = 2_029_010L)

/**
 * 為る is written します here, which is the case the sentence rework
 * exists for: the word on screen is not the word a tap searches for,
 * and every fixture whose surface equals its headword is blind to the
 * difference between them.
 */
private val SURU = BreakdownWord(
    text = "為る",
    reading = "する",
    surface = "します",
    entryId = 1_157_170L,
)

/**
 * Tapping a word of the sentence lives inside a composable, so no
 * producer test can reach it: the `navigate(...)` call could be deleted
 * with the rule's own tests still green. These drive the real sentence
 * and read back what the navigation controller was asked to do.
 *
 * The tap opens a *search*, not the linked entry, and it pushes rather
 * than pops — the index's link can be wrong, and back has to return the
 * reader to the sentence they were reading.
 */
@OptIn(ExperimentalTestApi::class)
class PhrasesTapUiTest : ComposeUiTestBase() {
    @Test
    fun `tapping a content word searches for that word`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }

        onNodeWithText(HANASHI.text).performClick()

        assertEquals<List<Route>>(listOf(SearchRoute("話")), navigation.navigated)
    }

    /**
     * The matrix row: the surface is what the reader touches and the
     * dictionary form is what is searched for. Navigating with the text
     * on screen would search for します, which finds nothing.
     */
    @Test
    fun `tapping an inflected word searches for its dictionary form`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }

        onNodeWithText("します").performClick()

        assertEquals<List<Route>>(listOf(SearchRoute("為る")), navigation.navigated)
    }

    @Test
    fun `a grammar word offers nothing to tap`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }

        onNodeWithText(WO.text).performClick()

        assertEquals<List<Route>>(emptyList(), navigation.navigated)
    }

    /** Several words can be looked up from one sentence, one after another. */
    @Test
    fun `a second word can be tapped after the first`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }

        onNodeWithText(HANASHI.text).performClick()
        onNodeWithText(GAKKOU.text).performClick()

        assertEquals<List<Route>>(
            listOf(SearchRoute("話"), SearchRoute("学校")),
            navigation.navigated,
        )
    }

    @Test
    fun `rendering a sentence navigates nowhere on its own`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }

        assertEquals<List<Route>>(emptyList(), navigation.navigated)
    }

    /**
     * The reader is on 話's own page, so the 話 in its examples has
     * nowhere to send them and does nothing.
     */
    @Test
    fun `the word the entry is about opens no search for itself`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation, wordBeingRead = "話")
        }

        onNodeWithText(HANASHI.text).performClick()

        assertEquals<List<Route>>(emptyList(), navigation.navigated)
        // And the rest of the sentence is unaffected, so this cannot
        // pass by the tab having gone inert.
        onNodeWithText(GAKKOU.text).performClick()
        assertEquals<List<Route>>(listOf(SearchRoute("学校")), navigation.navigated)
    }

    /**
     * Losing the tap must not cost the word its appearance. 話 is still
     * a noun and is still drawn as one — anything else tells the reader
     * the word they came to study is grammar.
     *
     * Read off the resolved text style rather than off pixels, which is
     * where the colour handed to `FuriganaText` actually lands; the
     * particle is measured too, so "same as the content word" cannot
     * pass by every piece having gone the same neutral colour.
     */
    @Test
    fun `the word the entry is about keeps its content-word colour`() = runComposeUiTest {
        setContent {
            PhrasesUnderTest(
                navigation = RecordingNavigationController(),
                wordBeingRead = "話",
            )
        }

        val suppressed = onNodeWithText(HANASHI.text).textColor()
        val tappable = onNodeWithText(GAKKOU.text).textColor()
        val particle = onNodeWithText(WO.text).textColor()

        assertEquals(tappable, suppressed, "the word being read is a content word and is drawn as one")
        assertNotEquals(particle, suppressed, "and not in the colour a particle takes")
    }
}

@Composable
private fun PhrasesUnderTest(
    navigation: RecordingNavigationController,
    wordBeingRead: String = "",
) {
    ScreenHost(navigation = navigation) {
        PhrasesTabContent(
            state = PhrasesTabState(
                content = PhrasesTabContentState.Ready(
                    sentences = listOf(
                        ExampleSentence(
                            id = 1L,
                            japanese = "学校で話をします。",
                            english = "I have a talk at school.",
                            words = listOf(GAKKOU, DE, HANASHI, WO, SURU),
                        ),
                    ),
                    tappableWords = setOf(GAKKOU, HANASHI, SURU),
                    wordBeingRead = wordBeingRead,
                ),
            ),
            contentPadding = PaddingValues(),
        )
    }
}

/** The colour the piece's line was actually laid out in. */
private fun SemanticsNodeInteraction.textColor(): Color {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
    assertTrue(action?.action?.invoke(results) == true, "the piece reports no text layout")
    return results.first().layoutInput.style.color
}
