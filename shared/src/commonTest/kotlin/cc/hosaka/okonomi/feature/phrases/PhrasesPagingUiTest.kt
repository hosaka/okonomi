package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.ui.PagingFooterState
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.paging_more_failed
import okonomi.shared.generated.resources.paging_more_loading
import okonomi.shared.generated.resources.paging_retry
import org.jetbrains.compose.resources.stringResource

private const val PAGE = 30

/**
 * dictgen keeps up to fifty sentences per entry and the tab shows thirty
 * of them, so the reader scrolling to the bottom of the page is what
 * asks for the rest.
 */
@OptIn(ExperimentalTestApi::class)
class PhrasesPagingUiTest : ComposeUiTestBase() {
    @Test
    fun `scrolling to the end of the page asks for the next one`() = runComposeUiTest {
        var asked = 0
        setContent {
            PhrasesUnderTest(onShowMore = { asked++ })
        }

        assertEquals(0, asked, "a list nobody has scrolled must not fetch a page")

        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE - 1)
        waitForIdle()

        assertTrue(asked > 0, "reaching the end of the page must ask for the next one")
    }

    /**
     * A sparse entry's two sentences are the whole set and they all fit
     * on screen, so the end of the list is in reach from the moment it
     * composes — which is exactly where an unguarded effect would fire.
     *
     * The second half turns the same recorder on against a pageable
     * entry: a zero that only held because nothing was ever wired up
     * would be no assertion at all.
     */
    @Test
    fun `an entry whose examples all fit asks for nothing`() = runComposeUiTest {
        var asked = 0
        val record: () -> Unit = { asked++ }
        val pageable = mutableStateOf(false)
        setContent {
            PhrasesUnderTest(
                count = if (pageable.value) PAGE else 2,
                onShowMore = record.takeIf { pageable.value },
            )
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        waitForIdle()

        assertEquals(0, asked, "an entry with nothing further must never ask for a page")

        runOnIdle { pageable.value = true }
        onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE - 1)
        waitForIdle()

        assertTrue(asked > 0, "a pageable entry must ask, or the zero above is an empty assertion")
    }

    /** The matrix guarantee: an entry whose examples all fit shows no footer. */
    @Test
    fun `an entry whose examples all fit draws no footer`() = runComposeUiTest {
        lateinit var loading: String
        lateinit var failed: String
        setContent {
            loading = stringResource(Res.string.paging_more_loading)
            failed = stringResource(Res.string.paging_more_failed)
            PhrasesUnderTest(count = 2, onShowMore = null)
        }
        waitForIdle()

        onNodeWithText(loading).assertDoesNotExist()
        onNodeWithText(failed).assertDoesNotExist()
    }

    @Test
    fun `a page in flight says so under the last sentence`() = runComposeUiTest {
        lateinit var loading: String
        setContent {
            loading = stringResource(Res.string.paging_more_loading)
            PhrasesUnderTest(count = 2, onShowMore = null, footer = PagingFooterState.Loading)
        }
        waitForIdle()

        onNodeWithText(loading).assertIsDisplayed()
    }

    @Test
    fun `a failed page says so and its retry asks again`() = runComposeUiTest {
        var retries = 0
        lateinit var failed: String
        lateinit var retry: String
        setContent {
            failed = stringResource(Res.string.paging_more_failed)
            retry = stringResource(Res.string.paging_retry)
            PhrasesUnderTest(
                count = 2,
                onShowMore = null,
                footer = PagingFooterState.Failed(onRetry = { retries++ }),
            )
        }
        waitForIdle()

        onNodeWithText(failed).assertIsDisplayed()
        onNodeWithText(retry).performClick()
        waitForIdle()

        assertEquals(1, retries)
    }
}

private fun sentences(count: Int): List<ExampleSentence> = (1..count).map { index ->
    ExampleSentence(
        id = index.toLong(),
        japanese = "例文$index です。",
        english = "Example sentence $index.",
        words = listOf(
            BreakdownWord(text = "例文", reading = "れいぶん"),
            BreakdownWord(text = "です", reading = null),
        ),
    )
}

@Composable
private fun PhrasesUnderTest(
    count: Int = PAGE,
    onShowMore: (() -> Unit)?,
    footer: PagingFooterState = PagingFooterState.None,
) {
    ScreenHost {
        PhrasesTabContent(
            state = PhrasesTabState(
                content = PhrasesTabContentState.Ready(
                    sentences = sentences(count),
                    onShowMore = onShowMore,
                    footer = footer,
                ),
            ),
            contentPadding = PaddingValues(),
        )
    }
}
