package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.anki.AnkiSendResult
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.user.UserList
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.favourites_options
import okonomi.shared.generated.resources.favourites_send_to_anki
import org.jetbrains.compose.resources.stringResource

/**
 * The Send to AnkiDroid menu item and the dialog a send ends in. When
 * the item is offered, and when it is disabled, is the producer's
 * decision (`FavouritesAnkiTest`); this is that the screen draws what
 * the state says. The dialog texts are spelled out here rather than read
 * from the resources, so a message that lost its count would show.
 */
@OptIn(ExperimentalTestApi::class)
class FavouritesAnkiUiTest : ComposeUiTestBase() {

    private val hit = SearchHit(
        entryId = 1L,
        titleSegments = listOf(TitleSegment("食べる")),
        traceLabels = emptyList(),
        senseLines = listOf("to eat"),
        isCommon = true,
    )

    private fun favourites(
        showSendToAnki: Boolean = true,
        onSendToAnki: (() -> Unit)? = {},
        ankiPrompt: FavouritesAnkiPrompt? = null,
    ) = FavouritesState(
        content = FavouritesContentState.Ready(listOf(hit)),
        list = UserList.Favourites,
        showSendToAnki = showSendToAnki,
        onSendToAnki = onSendToAnki,
        ankiPrompt = ankiPrompt,
    )

    @Test
    fun `the menu offers Send to AnkiDroid and reports the tap`() = runComposeUiTest {
        var sends = 0
        lateinit var options: String
        lateinit var send: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            send = stringResource(Res.string.favourites_send_to_anki)
            ScreenHost {
                FavouritesScreen(state = favourites(onSendToAnki = { sends++ }), onExportClick = null, onImportClick = null)
            }
        }

        // The only item with a callback: the menu button must open for it.
        onNodeWithContentDescription(options).assertIsEnabled().performClick()
        waitForIdle()
        onNodeWithText(send).assertIsEnabled().performClick()
        waitForIdle()

        assertEquals(1, sends)
    }

    @Test
    fun `where the send is not offered the item is absent rather than disabled`() = runComposeUiTest {
        lateinit var options: String
        lateinit var send: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            send = stringResource(Res.string.favourites_send_to_anki)
            ScreenHost {
                FavouritesScreen(state = favourites(showSendToAnki = false), onExportClick = {}, onImportClick = {})
            }
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()

        onNodeWithText(send).assertDoesNotExist()
    }

    @Test
    fun `with nothing to send or a send under way the item is there and disabled`() = runComposeUiTest {
        lateinit var options: String
        lateinit var send: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            send = stringResource(Res.string.favourites_send_to_anki)
            ScreenHost {
                FavouritesScreen(state = favourites(onSendToAnki = null), onExportClick = {}, onImportClick = {})
            }
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()

        onNodeWithText(send).assertIsNotEnabled()
    }

    @Test
    fun `every outcome has its own dialog that says what happened`() = runComposeUiTest {
        var dismissals = 0
        var result by mutableStateOf<AnkiSendResult>(AnkiSendResult.NothingNew)
        setContent {
            ScreenHost {
                FavouritesScreen(
                    state = favourites(ankiPrompt = FavouritesAnkiPrompt(result, onDismiss = { dismissals++ })),
                    onExportClick = {},
                    onImportClick = {},
                )
            }
        }
        val expected = listOf(
            AnkiSendResult.Sent(added = 10, alreadyThere = 0) to
                "Sent 10 words to the deck “Okonomi Favourites”.",
            AnkiSendResult.Sent(added = 1, alreadyThere = 0) to
                "Sent 1 word to the deck “Okonomi Favourites”.",
            AnkiSendResult.Sent(added = 3, alreadyThere = 10) to
                "Sent 3 new words to the deck “Okonomi Favourites”. 10 were already in AnkiDroid.",
            AnkiSendResult.NothingNew to "Every word in Favourites is already in AnkiDroid.",
            AnkiSendResult.Unavailable to
                "Install AnkiDroid to send your Favourites to it. If it is already installed, " +
                "turn on Settings › Advanced › Enable AnkiDroid API in AnkiDroid, then try again.",
            AnkiSendResult.PermissionDenied to
                "okonomi can only add cards to AnkiDroid with your permission. Nothing was sent.",
            AnkiSendResult.NoCollection to
                "AnkiDroid has not been set up on this device yet. Open it once, then try again. Nothing was sent.",
            AnkiSendResult.Failed(added = 0, attempted = 0) to "Something went wrong and nothing was sent.",
            AnkiSendResult.Failed(added = 400, attempted = 1_000) to
                "Something went wrong: 400 of 1000 words reached the deck “Okonomi Favourites” before it stopped.",
        )

        expected.forEach { (outcome, message) ->
            result = outcome
            waitForIdle()
            onNodeWithText(message).assertIsDisplayed()
        }

        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals(1, dismissals)
    }
}
