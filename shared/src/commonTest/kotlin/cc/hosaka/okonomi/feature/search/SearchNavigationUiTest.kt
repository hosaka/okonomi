package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Forward navigation out of search lives inside a composable, so no producer
 * test could reach it: a reviewer deleted the `navigate(...)` call and the
 * suite stayed green. These drive the real row and read back what the
 * navigation controller was asked to do.
 */
@OptIn(ExperimentalTestApi::class)
class SearchNavigationUiTest : ComposeUiTestBase() {
    @Test
    fun `tapping a result opens that entry`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            SearchUnderTest(navigation = navigation)
        }

        onNodeWithText(TABERU_TITLE).performClick()

        assertEquals<List<Route>>(listOf(EntryRoute(TABERU_ID)), navigation.navigated)
    }

    @Test
    fun `the entry opened is the row that was tapped`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            SearchUnderTest(
                state = searchState(hits = listOf(searchHit(), taberareruHit())),
                navigation = navigation,
            )
        }

        onNodeWithText(TABERARERU_TITLE).performClick()

        assertEquals<List<Route>>(listOf(EntryRoute(TABERARERU_ID)), navigation.navigated)
    }

    @Test
    fun `rendering results navigates nowhere on its own`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            SearchUnderTest(navigation = navigation)
        }

        assertEquals<List<Route>>(emptyList(), navigation.navigated)
    }
}

private const val TABERU_ID = 1_386_640L
private const val TABERU_TITLE = "食べる"
private const val TABERARERU_ID = 1_386_641L
private const val TABERARERU_TITLE = "食べられる"

private fun searchHit(
    entryId: Long = TABERU_ID,
    title: String = TABERU_TITLE,
    traceLabels: List<String> = emptyList(),
    senseLines: List<String> = listOf("to eat"),
    isCommon: Boolean = true,
) = SearchHit(
    entryId = entryId,
    titleSegments = listOf(TitleSegment(text = title)),
    traceLabels = traceLabels,
    senseLines = senseLines,
    isCommon = isCommon,
)

private fun taberareruHit() = searchHit(
    entryId = TABERARERU_ID,
    title = TABERARERU_TITLE,
    senseLines = listOf("to be able to eat"),
)

/**
 * `Results.query` matches `query` on purpose: when they differ the screen reads
 * as still refining and shows a spinner instead of the rows.
 */
private fun searchState(
    query: String = "たべる",
    hits: List<SearchHit> = listOf(searchHit()),
    isFallback: Boolean = false,
) = SearchState(
    query = query,
    results = SearchResultsState.Results(
        query = query,
        hits = hits,
        isFallback = isFallback,
    ),
)

@Composable
private fun SearchUnderTest(
    state: SearchState = searchState(),
    navigation: RecordingNavigationController = RecordingNavigationController(),
) {
    CompositionLocalProvider(LocalNavigationController provides navigation) {
        SearchScreen(state)
    }
}
