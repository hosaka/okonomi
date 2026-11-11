package cc.hosaka.okonomi.feature.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.lang.LEARNING_LANGUAGE_PREFERENCE
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.lang.ProvideLearningLanguage
import cc.hosaka.okonomi.lang.setLearningLanguage
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.theme.atJapaneseReadingSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.common_word_badge
import org.jetbrains.compose.resources.stringResource

/**
 * The search row for a reader learning English, through the real screen
 * and the real provider: titled by the English, the Japanese beneath it.
 *
 * The order of the two lines is asserted by where they sit. Robolectric
 * lays glyphs out to no width, but a line still has its height, so which
 * of two lines in one column is higher up is visible here even though
 * how wide either is is not.
 */
@OptIn(ExperimentalTestApi::class)
class SearchEnglishRowUiTest : ComposeUiTestBase() {

    @Test
    fun `the english gloss titles the row above the japanese with no bullet`() = runComposeUiTest {
        setContent {
            RowUnderTest(hit = okonauHit())
        }
        waitForIdle()

        // Exact text: a bulleted "- to do" would not match, and neither
        // does the sense line, which reads "- to perform, to do".
        val title = onNodeWithText("to do", useUnmergedTree = true)
        val japanese = onNodeWithText("行う", useUnmergedTree = true)
        title.assertIsDisplayed()
        assertTrue(
            title.getBoundsInRoot().top < japanese.getBoundsInRoot().top,
            "the English is the title and the Japanese the line beneath it",
        )
        onNodeWithText("- to perform, to do", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `the title lights the query's words the way a sense line does`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            RowUnderTest(hit = okonauHit(), glossTokens = listOf("do"))
        }
        waitForIdle()

        val title = onNodeWithText("to do", useUnmergedTree = true).annotatedText()
        val lit = title.spanStyles.filter { it.item.color == primary }
        assertEquals(listOf("do"), lit.map { title.text.substring(it.start, it.end) })
    }

    /**
     * たべる matched 食べる through its reading, and the reading is not
     * drawn for this reader. Lighting 食 with it, or べる, would say the
     * reader typed what they can see.
     */
    @Test
    fun `a match through the hidden reading lights nothing`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            RowUnderTest(
                hit = SearchHit(
                    entryId = 2L,
                    titleSegments = listOf(
                        TitleSegment("食べる"),
                        TitleSegment("たべる", highlight = 0..2, readsPreviousSegment = true),
                    ),
                    traceLabels = emptyList(),
                    senseLines = listOf("to eat"),
                    isCommon = true,
                    titleGloss = "to eat",
                ),
            )
        }
        waitForIdle()

        val japanese = onNodeWithText("食べる", useUnmergedTree = true).annotatedText()
        assertTrue(japanese.spanStyles.none { it.item.color == primary }, "${japanese.spanStyles}")
    }

    @Test
    fun `a long press copies the english title`() = runComposeUiTest {
        val clipboard = RecordingClipboard()
        setContent {
            @Suppress("DEPRECATION")
            CompositionLocalProvider(LocalClipboardManager provides clipboard) {
                RowUnderTest(hit = okonauHit())
            }
        }
        waitForIdle()

        onNodeWithText("to do").performTouchInput { longClick() }

        assertEquals("to do", clipboard.copied?.text)
    }

    /** The row opens its entry under the gloss it was titled by. */
    @Test
    fun `tapping the row opens the entry under its gloss`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            RowUnderTest(hit = okonauHit(), navigation = navigation)
        }
        waitForIdle()

        onNodeWithText("to do").performClick()

        assertEquals(listOf<Route>(EntryRoute(OKONAU_ID, titleGloss = "to do")), navigation.navigated)
    }

    /**
     * An entry with no English keeps the Japanese title, at the size and
     * in the line box it has always had — not the smaller line the
     * English layout puts the Japanese on. The box is the caller's own
     * 44sp either way: hidden furigana adds no floor, and the shown one's
     * floor sits below it.
     */
    @Test
    fun `an entry with no gloss keeps its japanese title`() = runComposeUiTest {
        var expected = TextStyle.Default
        setContent {
            expected = MaterialTheme.typography.titleMedium.atJapaneseReadingSize()
            RowUnderTest(hit = okonauHit().copy(titleGloss = null))
        }
        waitForIdle()

        val style = onNodeWithText("行う", useUnmergedTree = true).layout().layoutInput.style
        assertEquals(expected.fontSize, style.fontSize)
        assertEquals(expected.lineHeight, style.lineHeight)
    }

    /**
     * What sits beside each line: the common badge beside the English,
     * which it is a fact about, and the breadcrumb beside the Japanese,
     * which is what the deinflection rules explain.
     */
    @Test
    fun `the badge sits beside the english and the breadcrumb beside the japanese`() = runComposeUiTest {
        var badge = ""
        setContent {
            badge = stringResource(Res.string.common_word_badge)
            RowUnderTest(
                hit = SearchHit(
                    entryId = 2L,
                    titleSegments = listOf(
                        TitleSegment("食べる"),
                        TitleSegment("たべる", readsPreviousSegment = true),
                    ),
                    traceLabels = listOf("past"),
                    senseLines = listOf("to eat"),
                    isCommon = true,
                    titleGloss = "to eat",
                ),
            )
        }
        waitForIdle()

        val title = onNodeWithText("to eat", useUnmergedTree = true).getBoundsInRoot()
        val japanese = onNodeWithText("食べる", useUnmergedTree = true).getBoundsInRoot()
        val chip = onNodeWithText(badge, useUnmergedTree = true)
        val breadcrumb = onNodeWithText("‹ past", useUnmergedTree = true)
        chip.assertIsDisplayed()
        breadcrumb.assertIsDisplayed()
        assertTrue(chip.getBoundsInRoot().bottom <= japanese.top, "the badge is on the English line")
        assertTrue(breadcrumb.getBoundsInRoot().top >= title.bottom, "the breadcrumb is on the Japanese line")
    }

    /**
     * A kana word's kana is the word itself, not a reading of it, so
     * hiding readings must leave it on the Japanese line. The fixture is
     * the shape `buildHits` gives an entry with no kanji form: its one
     * reading, standing alone rather than over anything.
     */
    @Test
    fun `a kana entry keeps its kana as the japanese line`() = runComposeUiTest {
        setContent {
            RowUnderTest(
                hit = SearchHit(
                    entryId = 3L,
                    titleSegments = listOf(TitleSegment("ありがとう")),
                    traceLabels = emptyList(),
                    senseLines = listOf("thank you, thanks"),
                    isCommon = true,
                    titleGloss = "thank you",
                ),
            )
        }
        waitForIdle()

        val title = onNodeWithText("thank you", useUnmergedTree = true)
        val japanese = onNodeWithText("ありがとう", useUnmergedTree = true)
        japanese.assertIsDisplayed()
        assertTrue(
            title.getBoundsInRoot().top < japanese.getBoundsInRoot().top,
            "the kana is the line beneath the English",
        )
    }

    /**
     * The acceptance case: rows on screen re-lay out when the setting
     * changes, with no restart and no new search.
     */
    @Test
    fun `the rows follow the setting as it changes`() = runComposeUiTest {
        val preferences = FakePreferenceStore()
        setContent {
            RowUnderTest(hit = okonauHit(), preferences = preferences)
        }
        waitForIdle()
        onNodeWithText("to do", useUnmergedTree = true).assertDoesNotExist()

        preferences.setLearningLanguage(LearningLanguage.English)
        waitForIdle()
        onNodeWithText("to do", useUnmergedTree = true).assertIsDisplayed()

        preferences.setLearningLanguage(LearningLanguage.Japanese)
        waitForIdle()
        onNodeWithText("to do", useUnmergedTree = true).assertDoesNotExist()
    }
}

private const val OKONAU_ID = 1_000_000L

/** 行う as an English search for "do" finds it: titled by the gloss "do" matched. */
private fun okonauHit() = SearchHit(
    entryId = OKONAU_ID,
    titleSegments = listOf(
        TitleSegment("行う"),
        TitleSegment("おこなう", readsPreviousSegment = true),
    ),
    traceLabels = emptyList(),
    senseLines = listOf("to perform, to do", "to carry out"),
    isCommon = true,
    titleGloss = "to do",
)

/**
 * One row through the real Search screen, under the real provider. The
 * device is not Japanese, so English here is the reader's stored choice
 * rather than a default.
 */
@Composable
private fun RowUnderTest(
    hit: SearchHit,
    glossTokens: List<String> = emptyList(),
    navigation: RecordingNavigationController = RecordingNavigationController(),
    preferences: PreferenceStore = FakePreferenceStore(strings = mapOf(LEARNING_LANGUAGE_PREFERENCE to "english")),
) {
    ScreenHost(navigation = navigation) {
        ProvideLearningLanguage(preferences = preferences, deviceLanguage = "en") {
            SearchScreen(
                SearchState(
                    query = "do",
                    results = SearchResultsState.Results(
                        query = "do",
                        hits = listOf(hit),
                        isFallback = false,
                        glossTokens = glossTokens,
                    ),
                ),
            )
        }
    }
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
