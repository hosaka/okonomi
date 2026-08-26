package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.ScreenHost
import kotlin.test.Test
import kotlin.test.assertEquals

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

}

@Composable
private fun PhrasesUnderTest(navigation: RecordingNavigationController) {
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
                ),
            ),
            contentPadding = PaddingValues(),
        )
    }
}
