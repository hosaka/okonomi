package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.PagingFooterState
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.paging_more_failed
import okonomi.shared.generated.resources.paging_more_loading
import okonomi.shared.generated.resources.paging_retry
import org.jetbrains.compose.resources.stringResource

private const val PAGE = 50

/**
 * Search used to stop dead at fifty hits. It pages now, and the paging
 * is driven by where the list is scrolled — which is exactly the part a
 * producer test cannot see.
 */
@OptIn(ExperimentalTestApi::class)
class SearchPagingUiTest : ComposeUiTestBase() {
    @Test
    fun `scrolling to the end of the results asks for the next page`() = runComposeUiTest {
        var asked = 0
        setContent {
            SearchUnderTest(onShowMore = { asked++ })
        }

        assertEquals(0, asked, "a list nobody has scrolled must not fetch a page")

        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE - 1)
        waitForIdle()

        assertTrue(asked > 0, "reaching the end of the page must ask for the next one")
    }

    /**
     * A null callback is the producer saying the match set is exhausted,
     * or that paging has reached the ranking pool it stops honestly at.
     *
     * The recorder is installed either way and the same scroll is
     * performed twice, once with paging off and once with it on: a zero
     * that only holds because nothing was ever wired up would be no
     * assertion at all, and the second half is what rules that out.
     */
    @Test
    fun `a result set with nothing more to show asks for nothing`() = runComposeUiTest {
        var asked = 0
        val record: () -> Unit = { asked++ }
        val pageable = mutableStateOf(false)
        setContent {
            SearchUnderTest(onShowMore = record.takeIf { pageable.value })
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE - 1)
        waitForIdle()

        assertEquals(0, asked, "a list with no next page must never ask for one")

        runOnIdle { pageable.value = true }
        onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE - 1)
        waitForIdle()

        assertTrue(asked > 0, "the same scroll on a pageable list must ask, or the zero is empty")
    }

    /**
     * The reader's place in the list, which nothing asserted: a page
     * arriving is a longer list under the same query, and the whole
     * point of paging by prefix is that it appends. A list that jumped
     * back to the top would undo the scroll that asked for the page.
     */
    @Test
    fun `a page arriving leaves the reader where they were`() = runComposeUiTest {
        val hits = mutableStateOf(pageOfHits())
        setContent {
            SearchUnderTest(onShowMore = {}, hits = hits.value)
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE - 1)
        waitForIdle()
        onNodeWithText("語$PAGE").assertIsDisplayed()

        // The same query, a longer prefix of the same ranking.
        runOnIdle { hits.value = pageOfHits(PAGE * 2) }
        waitForIdle()

        onNodeWithText("語$PAGE").assertIsDisplayed()
        // The top of the list is where a reset would have put the
        // reader, and it is nowhere near the rows they were reading.
        onNodeWithText("語1").assertIsNotDisplayed()
    }

    /** Item K: a page in flight has to be visible. */
    @Test
    fun `a page in flight says so under the last row`() = runComposeUiTest {
        lateinit var loading: String
        setContent {
            loading = stringResource(Res.string.paging_more_loading)
            SearchUnderTest(onShowMore = null, footer = PagingFooterState.Loading)
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE)
        waitForIdle()

        onNodeWithText(loading).assertIsDisplayed()
    }

    /** Item K: and a page that failed has to say so, with a way back. */
    @Test
    fun `a failed page says so and its retry asks again`() = runComposeUiTest {
        var retries = 0
        lateinit var failed: String
        lateinit var retry: String
        setContent {
            failed = stringResource(Res.string.paging_more_failed)
            retry = stringResource(Res.string.paging_retry)
            SearchUnderTest(
                onShowMore = null,
                footer = PagingFooterState.Failed(onRetry = { retries++ }),
            )
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE)
        waitForIdle()

        onNodeWithText(failed).assertIsDisplayed()
        onNodeWithText(retry).performClick()
        waitForIdle()

        assertEquals(1, retries, "the footer's retry must ask for the page again")
    }

    /** The matrix guarantee: nothing further, nothing drawn. */
    @Test
    fun `a result set with nothing more draws no footer at all`() = runComposeUiTest {
        lateinit var loading: String
        lateinit var failed: String
        setContent {
            loading = stringResource(Res.string.paging_more_loading)
            failed = stringResource(Res.string.paging_more_failed)
            SearchUnderTest(onShowMore = null)
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE - 1)
        waitForIdle()

        onNodeWithText(loading).assertDoesNotExist()
        onNodeWithText(failed).assertDoesNotExist()
    }
}

private fun pageOfHits(count: Int = PAGE): List<SearchHit> = (1..count).map { index ->
    SearchHit(
        entryId = index.toLong(),
        titleSegments = listOf(TitleSegment(text = "語$index")),
        traceLabels = emptyList(),
        senseLines = listOf("word $index"),
        isCommon = false,
    )
}

@Composable
private fun SearchUnderTest(
    onShowMore: (() -> Unit)?,
    hits: List<SearchHit> = pageOfHits(),
    footer: PagingFooterState = PagingFooterState.None,
) {
    val query = "word"
    CompositionLocalProvider(
        LocalNavigationController provides RecordingNavigationController(),
    ) {
        SearchScreen(
            SearchState(
                query = query,
                results = SearchResultsState.Results(
                    query = query,
                    hits = hits,
                    isFallback = false,
                    onShowMore = onShowMore,
                    footer = footer,
                ),
            ),
        )
    }
}
