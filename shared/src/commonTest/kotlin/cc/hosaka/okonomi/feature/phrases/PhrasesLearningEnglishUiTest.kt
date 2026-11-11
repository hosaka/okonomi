package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.lang.LEARNING_LANGUAGE_PREFERENCE
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.lang.ProvideLearningLanguage
import cc.hosaka.okonomi.lang.setLearningLanguage
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.ScreenHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private val GAKKOU = BreakdownWord(text = "学校", reading = "がっこう", entryId = 1_301_230L)

private val HANASHI = BreakdownWord(text = "話", reading = "はなし", entryId = 1_589_580L)

private val SURU = BreakdownWord(text = "為る", reading = "する", surface = "します", entryId = 1_157_170L)

private const val ENGLISH = "I have a talk at school."

/**
 * The Phrases card for a reader learning English, through the real
 * provider: the English sentence on top, the Japanese under it, and the
 * Japanese words still a way into the dictionary.
 *
 * Which line is on top is read off where the lines sit: Robolectric lays
 * glyphs out to no width, but a line keeps its height, so of two lines in
 * one card the higher one is visible here.
 */
@OptIn(ExperimentalTestApi::class)
class PhrasesLearningEnglishUiTest : ComposeUiTestBase() {

    /**
     * Both orders in one test, through one store, so neither can be the
     * card's only layout: a card that always put the English first passes
     * the first half and fails the second.
     */
    @Test
    fun `the line on top follows the language being learned`() = runComposeUiTest {
        val preferences = FakePreferenceStore(strings = mapOf(LEARNING_LANGUAGE_PREFERENCE to "english"))
        setContent { PhrasesUnderTest(preferences = preferences) }
        waitForIdle()

        assertTrue(top(ENGLISH) < top(GAKKOU.text), "learning English, the English sentence is the line on top")

        preferences.setLearningLanguage(LearningLanguage.Japanese)
        waitForIdle()

        assertTrue(top(GAKKOU.text) < top(ENGLISH), "learning Japanese, the Japanese sentence is the line on top")
    }

    /** The reason the Japanese stays word by word: tapping one still looks it up. */
    @Test
    fun `learning english a japanese word still opens a search`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent { PhrasesUnderTest(navigation = navigation) }
        waitForIdle()

        onNodeWithText(HANASHI.text).performClick()
        onNodeWithText(SURU.surface!!).performClick()

        assertEquals<List<Route>>(listOf(SearchRoute("話"), SearchRoute("為る")), navigation.navigated)
    }

    /**
     * The colour is the tap's affordance, so it has to survive the move
     * to the muted line: a tappable word in the primary colour, a
     * particle in the line's own.
     */
    @Test
    fun `learning english a tappable word keeps the primary colour`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            PhrasesUnderTest()
        }
        waitForIdle()

        // Unmerged: the card merges every piece that is not itself
        // clickable, and a merged を answers with the layout of the
        // card's first line, the English, whatever colour を is drawn in.
        assertEquals(primary, onNodeWithText(GAKKOU.text, useUnmergedTree = true).styleColor())
        assertNotEquals(
            primary,
            onNodeWithText("を", useUnmergedTree = true).styleColor(),
            "a particle opens nothing and is not coloured",
        )
    }

    @Test
    fun `learning english a long press copies the english sentence`() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        setContent {
            @Suppress("DEPRECATION")
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                PhrasesUnderTest()
            }
        }
        waitForIdle()

        onNodeWithText(ENGLISH).performTouchInput { longClick() }

        assertEquals(ENGLISH, clipboard.copied?.text)
    }

    /**
     * The unmerged tree, because the card merges every line that is not
     * itself clickable — the English among them — into one node, and the
     * merged lookup would report the card's top for both. With ruby drawn
     * the unmerged tree also holds the ruby unit's private copy of a word,
     * on the same line as the word, so the highest copy is the line's.
     */
    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.top(text: String): Float =
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes()
            .also { assertTrue(it.isNotEmpty(), "$text is not on screen") }
            .minOf { it.boundsInRoot.top }
}

/**
 * The tab's content for one sentence under the real provider. The device
 * is not Japanese, so English here is the reader's stored choice rather
 * than a default.
 */
@Composable
private fun PhrasesUnderTest(
    navigation: RecordingNavigationController = RecordingNavigationController(),
    preferences: PreferenceStore = FakePreferenceStore(strings = mapOf(LEARNING_LANGUAGE_PREFERENCE to "english")),
) {
    ScreenHost(navigation = navigation) {
        ProvideLearningLanguage(preferences = preferences, deviceLanguage = "en") {
            PhrasesTabContent(
                state = PhrasesTabState(
                    content = PhrasesTabContentState.Ready(
                        sentences = listOf(
                            ExampleSentence(
                                id = 1L,
                                japanese = "学校で話をします。",
                                english = ENGLISH,
                                words = listOf(
                                    GAKKOU,
                                    BreakdownWord("で", null),
                                    HANASHI,
                                    BreakdownWord("を", null),
                                    SURU,
                                ),
                            ),
                        ),
                        tappableWords = setOf(GAKKOU, HANASHI, SURU),
                    ),
                ),
                contentPadding = PaddingValues(),
            )
        }
    }
}

/** The colour the node's text is laid out in; see `PhrasesSentenceUiTest` for why the layout. */
private fun SemanticsNodeInteraction.styleColor(): Color {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
    assertTrue(action?.action?.invoke(results) == true, "the node reports no text layout")
    return results.first().layoutInput.style.color
}

/** See `KanjiDetailDialogUiTest`'s twin for why this is the deprecated interface. */
@Suppress("DEPRECATION")
private class RecordingClipboard : ClipboardManager {
    var copied: AnnotatedString? = null
        private set

    override fun setText(annotatedString: AnnotatedString) {
        copied = annotatedString
    }

    override fun getText(): AnnotatedString? = copied
}
