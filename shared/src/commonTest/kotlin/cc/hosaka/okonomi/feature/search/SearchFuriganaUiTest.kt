package cc.hosaka.okonomi.feature.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The search row through the real screen: the reading is set over the
 * kanji instead of spelled out beside it, and the match still shows.
 *
 * The ruby itself is drawn inside inline content — on Android that is a
 * platform `TextView`, invisible to the semantics tree — so what is
 * asserted here is what the text layer can see: the title's own text,
 * which proves the reading was folded in rather than appended, and the
 * span the highlight puts on the plain runs beside the ruby.
 * [TitleFuriganaTest] holds the rest of the mapping, including the
 * whole-unit highlight the inline box takes.
 */
@OptIn(ExperimentalTestApi::class)
class SearchFuriganaUiTest : ComposeUiTestBase() {

    @Test
    fun `a row titles the written form alone with its reading above it`() = runComposeUiTest {
        setContent {
            SearchRowUnderTest(hit = taberuHit())
        }

        // Not "食べる, たべる": the reading is ruby now, and the title
        // line is the word itself.
        onNodeWithText(WORD).assertIsDisplayed()
        onNodeWithText("$WORD, $READING").assertDoesNotExist()
    }

    @Test
    fun `the matched kana keeps its highlight through the fold`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            SearchRowUnderTest(hit = taberuHit(readingHighlight = 0..1))
        }

        val title = onNodeWithText(WORD).annotatedText()
        // 食 took た as ruby and is highlighted as a unit inside its
        // inline box; べ is ordinary text and is highlighted exactly,
        // which is the span the text layer carries.
        val spans = title.spanStyles.filter { it.item.color == primary }
        assertEquals(1, spans.size, "expected one highlighted run, got ${title.spanStyles}")
        assertEquals("べ", title.text.substring(spans.single().start, spans.single().end))
    }

    @Test
    fun `an unmatched row carries no highlight`() = runComposeUiTest {
        var primary = Color.Unspecified
        setContent {
            primary = MaterialTheme.colorScheme.primary
            SearchRowUnderTest(hit = taberuHit())
        }

        val title = onNodeWithText(WORD).annotatedText()
        assertTrue(title.spanStyles.none { it.item.color == primary }, "${title.spanStyles}")
    }

    /**
     * The breadcrumb used to be a second colour inside the title's own
     * annotated string, which a furigana title has no room for: its runs
     * are laid out against the readings above them.
     */
    @Test
    fun `a deinflected hit shows its breadcrumb beside the title rather than inside it`() = runComposeUiTest {
        setContent {
            SearchRowUnderTest(hit = taberuHit(traceLabels = listOf("continuative")))
        }

        onNodeWithText("‹ continuative").assertIsDisplayed()
        assertEquals(WORD, onNodeWithText(WORD).annotatedText().text)
    }
}

private fun SemanticsNodeInteraction.annotatedText(): AnnotatedString =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.first()
        ?: error("node carries no text")

private const val WORD = "食べる"

private const val READING = "たべる"

private fun taberuHit(
    readingHighlight: IntRange? = null,
    traceLabels: List<String> = emptyList(),
) = SearchHit(
    entryId = 1_386_640L,
    titleSegments = listOf(
        TitleSegment(text = WORD),
        TitleSegment(text = READING, highlight = readingHighlight, readsPreviousSegment = true),
    ),
    traceLabels = traceLabels,
    senseLines = listOf("to eat"),
    isCommon = false,
)

@Composable
private fun SearchRowUnderTest(hit: SearchHit) {
    CompositionLocalProvider(LocalNavigationController provides RecordingNavigationController()) {
        SearchScreen(
            SearchState(
                query = READING,
                results = SearchResultsState.Results(
                    query = READING,
                    hits = listOf(hit),
                    isFallback = false,
                ),
            ),
        )
    }
}
