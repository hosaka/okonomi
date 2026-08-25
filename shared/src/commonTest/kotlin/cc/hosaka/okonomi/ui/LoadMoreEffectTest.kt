package cc.hosaka.okonomi.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ROWS = 40

private const val ROW_HEIGHT = 40

/**
 * The paging trigger itself, rather than one of the two screens that use
 * it.
 *
 * Both screen-level tests could only say "something was asked for", and
 * passed whether the effect fired once or forty times — while the
 * effect's own kdoc makes idempotency a *requirement* it places on
 * producers. That requirement is only worth stating if the number of
 * calls is bounded and pinned somewhere, which is here.
 */
@OptIn(ExperimentalTestApi::class)
class LoadMoreEffectTest : ComposeUiTestBase() {

    /**
     * The threshold, from the far side: a list opened at the top must
     * not fetch a page nobody has reached.
     */
    @Test
    fun `a list nobody has scrolled asks for nothing`() = runComposeUiTest {
        var asked = 0
        setContent {
            ListUnderTest(onLoadMore = { asked++ })
        }
        waitForIdle()

        assertEquals(0, asked)
    }

    /** And from the near side: within a few rows of the end it fires. */
    @Test
    fun `reaching the end of the content asks for the next page`() = runComposeUiTest {
        var asked = 0
        setContent {
            ListUnderTest(onLoadMore = { asked++ })
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(ROWS - 1)
        waitForIdle()

        assertTrue(asked > 0)
    }

    /**
     * A null callback is the project's "disabled action", and the effect
     * has to keep watching the layout while it is null: the callback
     * becomes non-null again the moment there is another page. The
     * recorder is the same one in both halves, so the zero is a measured
     * zero rather than an unwired one.
     */
    @Test
    fun `a null callback is asked for nothing and does not disable the watcher`() =
        runComposeUiTest {
            var asked = 0
            val record: () -> Unit = { asked++ }
            val enabled = mutableStateOf(false)
            setContent {
                ListUnderTest(onLoadMore = record.takeIf { enabled.value })
            }

            onNode(hasScrollToIndexAction()).performScrollToIndex(ROWS - 1)
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            onNode(hasScrollToIndexAction()).performScrollToIndex(ROWS - 1)
            waitForIdle()

            assertEquals(0, asked, "a null callback must never be invoked")

            runOnIdle { enabled.value = true }
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            onNode(hasScrollToIndexAction()).performScrollToIndex(ROWS - 1)
            waitForIdle()

            assertTrue(asked > 0, "the watcher must still be running after the callback returns")
        }

    /**
     * The bound the kdoc's idempotency rule exists to make safe. The
     * effect re-asks on every change to the last visible index while the
     * end is in reach, so scrolling back and forth over the threshold
     * has to stay proportionate to the scrolling — a runaway watcher
     * would turn one flick into dozens of searches.
     */
    @Test
    fun `scrolling over the threshold repeatedly does not run away`() = runComposeUiTest {
        var asked = 0
        setContent {
            ListUnderTest(onLoadMore = { asked++ })
        }

        val trips = 3
        repeat(trips) {
            onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            onNode(hasScrollToIndexAction()).performScrollToIndex(ROWS - 1)
        }
        waitForIdle()

        // One ask per arrival at the end is the design; a few more from
        // intermediate layout positions inside the threshold band is
        // expected. Anything near the row count means the effect is
        // firing per frame rather than per layout change.
        assertTrue(asked > 0, "the effect must fire at all")
        assertTrue(
            asked <= trips * LOAD_MORE_THRESHOLD_ROWS,
            "one trip to the end must not produce more asks than the threshold band has rows, " +
                "got $asked after $trips trips",
        )
    }
}

/**
 * The effect's own threshold, restated here rather than exposed from
 * production code: the test is about the order of magnitude, and a
 * constant the test can read from the code under test would let both
 * move together silently.
 */
private const val LOAD_MORE_THRESHOLD_ROWS = 5

@Composable
private fun ListUnderTest(onLoadMore: (() -> Unit)?) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState = listState, onLoadMore = onLoadMore)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        state = listState,
    ) {
        items(
            items = (1..ROWS).toList(),
            key = { it },
        ) { index ->
            Text(
                text = "row $index",
                modifier = Modifier
                    .height(ROW_HEIGHT.dp),
            )
        }
    }
}
