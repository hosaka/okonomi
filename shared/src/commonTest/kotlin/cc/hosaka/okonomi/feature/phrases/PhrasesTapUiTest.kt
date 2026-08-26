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

private val SURU = BreakdownWord(text = "為る", reading = null, entryId = 1_157_170L)

private val WO = BreakdownWord(text = "を", reading = null, entryId = 2_029_010L)

private val HANASHI = BreakdownWord(text = "話", reading = null, entryId = 1_589_580L)

/**
 * The same word as [HANASHI] but carrying its reading, so the label the
 * row draws is `話 (はなし)` while the word itself is still 話. Every
 * other fixture here reads as itself, which makes the label and the
 * query the same string and hides the difference between them.
 */
private val GAKKOU = BreakdownWord(text = "学校", reading = "がっこう", entryId = 1_301_230L)

/** The shipped `entry_phrases_word_reading` template, applied. */
private const val GAKKOU_LABEL = "学校 (がっこう)"

/**
 * Tapping a breakdown word lives inside a composable, so no producer
 * test can reach it: the `navigate(...)` call could be deleted with the
 * rule's own tests still green. These drive the real row and read back
 * what the navigation controller was asked to do.
 *
 * The tap opens a *search*, not the linked entry, and it pushes rather
 * than pops — the breakdown's link can be wrong, and back has to return
 * the reader to the sentence they were reading.
 */
@OptIn(ExperimentalTestApi::class)
class PhrasesTapUiTest : ComposeUiTestBase() {
    @Test
    fun `tapping a content word searches for that word`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }

        onNodeWithText(SURU.text).performClick()

        // The word's own text, which the breakdown already stores in
        // dictionary form: 為る, never the inflected surface form.
        assertEquals<List<Route>>(listOf(SearchRoute("為る")), navigation.navigated)
    }

    @Test
    fun `the word searched for is the one that was tapped`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }

        onNodeWithText(HANASHI.text).performClick()

        assertEquals<List<Route>>(listOf(SearchRoute("話")), navigation.navigated)
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

    /**
     * The search term is the word, not the label. A word with a reading
     * is drawn as `学校 (がっこう)`, and navigating with what is on
     * screen instead of with `word.text` would search for that whole
     * string — which finds nothing, and which every fixture that reads
     * as itself is blind to.
     */
    @Test
    fun `a word shown with its reading is searched for without it`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }
        // The reading template is a resource, and a resource resolves a
        // frame late; clicking before it lands would click the bare word.
        waitForIdle()

        onNodeWithText(GAKKOU_LABEL).performClick()

        assertEquals<List<Route>>(listOf(SearchRoute("学校")), navigation.navigated)
    }

    /** Several words can be looked up from one sentence, one after another. */
    @Test
    fun `a second word can be tapped after the first`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            PhrasesUnderTest(navigation = navigation)
        }

        onNodeWithText(HANASHI.text).performClick()
        onNodeWithText(SURU.text).performClick()

        assertEquals<List<Route>>(
            listOf(SearchRoute("話"), SearchRoute("為る")),
            navigation.navigated,
        )
    }

    @Test
    fun `rendering a breakdown navigates nowhere on its own`() = runComposeUiTest {
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
                            japanese = "学校で話を為る。",
                            english = "Have a talk at school.",
                            words = listOf(GAKKOU, HANASHI, WO, SURU),
                        ),
                    ),
                    tappableWords = setOf(GAKKOU, HANASHI, SURU),
                ),
            ),
            contentPadding = PaddingValues(),
        )
    }
}
