package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.ScreenHost
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.favourites_empty
import okonomi.shared.generated.resources.favourites_error
import okonomi.shared.generated.resources.favourites_retry
import org.jetbrains.compose.resources.stringResource

/**
 * The Favourites tab as the reader sees it. The rows themselves are the
 * search screen's, tested there; what is new here is the tab's own empty
 * state, its error body, and that a saved row opens the entry.
 */
@OptIn(ExperimentalTestApi::class)
class FavouritesScreenUiTest : ComposeUiTestBase() {

    @Test
    fun `nothing saved shows the tab's own empty state`() = runComposeUiTest {
        lateinit var empty: String
        setContent {
            empty = stringResource(Res.string.favourites_empty)
            FavouritesUnderTest(FavouritesContentState.Ready(emptyList()))
        }

        onNodeWithText(empty).assertIsDisplayed()
    }

    @Test
    fun `a saved row draws its senses and opens the entry when tapped`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            FavouritesUnderTest(
                content = FavouritesContentState.Ready(listOf(hit(1_358_280L))),
                navigation = navigation,
            )
        }

        // The headword is drawn as furigana, which never reaches the
        // semantics tree; the sense line under it is the row's text.
        onNodeWithText("- to eat").assertIsDisplayed()

        onNodeWithText("- to eat").performClick()
        waitForIdle()

        assertEquals(listOf<Route>(EntryRoute(1_358_280L)), navigation.navigated)
    }

    @Test
    fun `a dictionary failure says so and offers a retry`() = runComposeUiTest {
        var retries = 0
        lateinit var error: String
        lateinit var retry: String
        setContent {
            error = stringResource(Res.string.favourites_error)
            retry = stringResource(Res.string.favourites_retry)
            FavouritesUnderTest(FavouritesContentState.Error(onRetry = { retries++ }))
        }

        onNodeWithText(error).assertIsDisplayed()
        onNodeWithText(retry).performClick()
        waitForIdle()

        assertEquals(1, retries)
    }

    @Test
    fun `an empty list is never mistaken for a failure`() = runComposeUiTest {
        lateinit var error: String
        setContent {
            error = stringResource(Res.string.favourites_error)
            FavouritesUnderTest(FavouritesContentState.Ready(emptyList()))
        }

        onNodeWithText(error).assertDoesNotExist()
    }
}

private fun hit(entryId: Long) = SearchHit(
    entryId = entryId,
    titleSegments = listOf(TitleSegment("食べる")),
    traceLabels = emptyList(),
    senseLines = listOf("to eat"),
    isCommon = true,
)

@Composable
private fun FavouritesUnderTest(
    content: FavouritesContentState,
    navigation: RecordingNavigationController = RecordingNavigationController(),
) {
    ScreenHost(navigation = navigation) {
        FavouritesScreen(FavouritesState(content = content))
    }
}
