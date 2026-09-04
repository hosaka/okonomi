package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
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
import okonomi.shared.generated.resources.favourites_export
import okonomi.shared.generated.resources.favourites_import
import okonomi.shared.generated.resources.favourites_import_replace_cancel
import okonomi.shared.generated.resources.favourites_import_replace_confirm
import okonomi.shared.generated.resources.favourites_import_replace_title
import okonomi.shared.generated.resources.favourites_import_unreadable_dismiss
import okonomi.shared.generated.resources.favourites_import_unreadable_title
import okonomi.shared.generated.resources.favourites_options
import okonomi.shared.generated.resources.favourites_retry
import org.jetbrains.compose.resources.stringResource

/**
 * The Favourites tab as the reader sees it. The rows themselves are the
 * search screen's, tested there; what is new here is the tab's own empty
 * state, its error body, that a saved row opens the entry, and the
 * export and import the toolbar menu offers.
 *
 * The file dialogs are not here and cannot be: they live in
 * `FavouritesRoute`, which is what keeps this screen hostable. What is
 * testable here is everything either side of them — which menu items are
 * offered and which are disabled, and what each dialog says and reports.
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
    fun `the overflow menu holds the export and the import`() = runComposeUiTest {
        lateinit var options: String
        lateinit var export: String
        lateinit var import: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            export = stringResource(Res.string.favourites_export)
            import = stringResource(Res.string.favourites_import)
            FavouritesUnderTest(FavouritesContentState.Ready(listOf(hit(1_358_280L))))
        }

        onNodeWithText(export).assertDoesNotExist()

        onNodeWithContentDescription(options).performClick()
        waitForIdle()

        onNodeWithText(export).assertIsDisplayed()
        onNodeWithText(import).assertIsDisplayed()
    }

    @Test
    fun `picking export in the menu reports the tap`() = runComposeUiTest {
        var exports = 0
        lateinit var options: String
        lateinit var export: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            export = stringResource(Res.string.favourites_export)
            FavouritesUnderTest(
                content = FavouritesContentState.Ready(listOf(hit(1_358_280L))),
                onExportClick = { exports++ },
            )
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()
        onNodeWithText(export).performClick()
        waitForIdle()

        assertEquals(1, exports)
    }

    /**
     * The matrix's "export unavailable" row. A null callback is how this
     * app spells disabled, and the row has to stay visible while it is:
     * a menu that hid the item would leave the reader with no answer to
     * where export went.
     */
    @Test
    fun `with nothing to export the menu item is there and disabled`() = runComposeUiTest {
        lateinit var options: String
        lateinit var export: String
        lateinit var import: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            export = stringResource(Res.string.favourites_export)
            import = stringResource(Res.string.favourites_import)
            FavouritesUnderTest(
                content = FavouritesContentState.Ready(emptyList()),
                onExportClick = null,
            )
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()

        onNodeWithText(export).assertIsNotEnabled()
        onNodeWithText(import).assertIsEnabled()
    }

    @Test
    fun `on the seeded first frame the menu button itself is disabled`() = runComposeUiTest {
        // FavouritesState() carries neither callback, and that is the
        // state produceScreenState seeds the tab with before the
        // producer emits. A menu button that opens onto two dead rows
        // is worse than one that is plainly not ready yet.
        lateinit var options: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            FavouritesUnderTest(
                content = FavouritesContentState.Loading,
                onExportClick = null,
                onImportClick = null,
            )
        }

        onNodeWithContentDescription(options).assertIsNotEnabled()
    }

    @Test
    fun `the overwrite warning names both answers and reports the one that was picked`() =
        runComposeUiTest {
            var confirms = 0
            var cancels = 0
            lateinit var title: String
            lateinit var confirm: String
            lateinit var cancel: String
            setContent {
                title = stringResource(Res.string.favourites_import_replace_title)
                confirm = stringResource(Res.string.favourites_import_replace_confirm)
                cancel = stringResource(Res.string.favourites_import_replace_cancel)
                FavouritesUnderTest(
                    content = FavouritesContentState.Ready(listOf(hit(1_358_280L))),
                    importPrompt = FavouritesImportPrompt.ConfirmOverwrite(
                        onConfirm = { confirms++ },
                        onCancel = { cancels++ },
                    ),
                )
            }

            onNodeWithText(title).assertIsDisplayed()
            onNodeWithText(cancel).assertIsDisplayed()

            onNodeWithText(confirm).performClick()
            waitForIdle()

            assertEquals(1, confirms)
            assertEquals(0, cancels)
        }

    @Test
    fun `a refused file says so and can be dismissed`() = runComposeUiTest {
        var dismissals = 0
        lateinit var title: String
        lateinit var dismiss: String
        setContent {
            title = stringResource(Res.string.favourites_import_unreadable_title)
            dismiss = stringResource(Res.string.favourites_import_unreadable_dismiss)
            FavouritesUnderTest(
                content = FavouritesContentState.Ready(listOf(hit(1_358_280L))),
                importPrompt = FavouritesImportPrompt.Unreadable(onDismiss = { dismissals++ }),
            )
        }

        onNodeWithText(title).assertIsDisplayed()

        onNodeWithText(dismiss).performClick()
        waitForIdle()

        assertEquals(1, dismissals)
    }

    @Test
    fun `no prompt means no dialog over the list`() = runComposeUiTest {
        lateinit var replace: String
        lateinit var unreadable: String
        setContent {
            replace = stringResource(Res.string.favourites_import_replace_title)
            unreadable = stringResource(Res.string.favourites_import_unreadable_title)
            FavouritesUnderTest(FavouritesContentState.Ready(listOf(hit(1_358_280L))))
        }

        onNodeWithText(replace).assertDoesNotExist()
        onNodeWithText(unreadable).assertDoesNotExist()
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
    importPrompt: FavouritesImportPrompt? = null,
    onExportClick: (() -> Unit)? = {},
    onImportClick: (() -> Unit)? = {},
) {
    ScreenHost(navigation = navigation) {
        FavouritesScreen(
            state = FavouritesState(
                content = content,
                importPrompt = importPrompt,
            ),
            onExportClick = onExportClick,
            onImportClick = onImportClick,
        )
    }
}
