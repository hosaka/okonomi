package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.user.UserList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.favourites_clear
import okonomi.shared.generated.resources.favourites_clear_cancel
import okonomi.shared.generated.resources.favourites_clear_confirm
import okonomi.shared.generated.resources.favourites_clear_title
import okonomi.shared.generated.resources.favourites_empty
import okonomi.shared.generated.resources.favourites_error
import okonomi.shared.generated.resources.favourites_export
import okonomi.shared.generated.resources.favourites_import
import okonomi.shared.generated.resources.favourites_import_replace_cancel
import okonomi.shared.generated.resources.favourites_import_replace_confirm
import okonomi.shared.generated.resources.favourites_import_replace_message
import okonomi.shared.generated.resources.favourites_import_replace_title
import okonomi.shared.generated.resources.favourites_import_unreadable_dismiss
import okonomi.shared.generated.resources.favourites_import_unreadable_message
import okonomi.shared.generated.resources.favourites_import_unreadable_title
import okonomi.shared.generated.resources.favourites_options
import okonomi.shared.generated.resources.favourites_remove
import okonomi.shared.generated.resources.favourites_retry
import okonomi.shared.generated.resources.favourites_title
import okonomi.shared.generated.resources.history_empty
import okonomi.shared.generated.resources.history_title
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
            lateinit var message: String
            lateinit var confirm: String
            lateinit var cancel: String
            setContent {
                val history = stringResource(Res.string.history_title)
                title = stringResource(Res.string.favourites_import_replace_title, history)
                message = stringResource(Res.string.favourites_import_replace_message, history)
                confirm = stringResource(Res.string.favourites_import_replace_confirm)
                cancel = stringResource(Res.string.favourites_import_replace_cancel)
                FavouritesUnderTest(
                    content = FavouritesContentState.Ready(listOf(hit(1_358_280L))),
                    importPrompt = FavouritesImportPrompt.ConfirmOverwrite(
                        list = UserList.History,
                        onConfirm = { confirms++ },
                        onCancel = { cancels++ },
                    ),
                )
            }

            // Named for the list being replaced, which here is History.
            onNodeWithText(title).assertIsDisplayed()
            onNodeWithText(message).assertIsDisplayed()
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
        lateinit var message: String
        lateinit var dismiss: String
        setContent {
            title = stringResource(Res.string.favourites_import_unreadable_title)
            message = stringResource(
                Res.string.favourites_import_unreadable_message,
                stringResource(Res.string.history_title),
            )
            dismiss = stringResource(Res.string.favourites_import_unreadable_dismiss)
            FavouritesUnderTest(
                content = FavouritesContentState.Ready(listOf(hit(1_358_280L))),
                importPrompt = FavouritesImportPrompt.Unreadable(
                    list = UserList.History,
                    onDismiss = { dismissals++ },
                ),
            )
        }

        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(message).assertIsDisplayed()

        onNodeWithText(dismiss).performClick()
        waitForIdle()

        assertEquals(1, dismissals)
    }

    @Test
    fun `no prompt means no dialog over the list`() = runComposeUiTest {
        lateinit var replace: String
        lateinit var unreadable: String
        setContent {
            replace = stringResource(
                Res.string.favourites_import_replace_title,
                stringResource(Res.string.favourites_title),
            )
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

    @Test
    fun `the title opens a picker that marks the current list and switches on a pick`() =
        runComposeUiTest {
            val picked = mutableListOf<UserList>()
            lateinit var favourites: String
            lateinit var history: String
            setContent {
                favourites = stringResource(Res.string.favourites_title)
                history = stringResource(Res.string.history_title)
                StateUnderTest(
                    FavouritesState(
                        list = UserList.Favourites,
                        content = FavouritesContentState.Ready(listOf(hit(1L))),
                        onSelectList = { picked += it },
                    ),
                )
            }

            onNodeWithText(history).assertDoesNotExist()
            onNodeWithText(favourites).performClick()
            waitForIdle()

            onNode(hasText(favourites) and isSelected()).assertExists()
            onNode(hasText(history) and isNotSelected()).performClick()
            waitForIdle()

            assertEquals(listOf(UserList.History), picked)
        }

    @Test
    fun `History is titled History and has its own empty state`() = runComposeUiTest {
        lateinit var title: String
        lateinit var empty: String
        lateinit var favouritesEmpty: String
        setContent {
            title = stringResource(Res.string.history_title)
            empty = stringResource(Res.string.history_empty)
            favouritesEmpty = stringResource(Res.string.favourites_empty)
            StateUnderTest(
                FavouritesState(
                    content = FavouritesContentState.Ready(emptyList()),
                    list = UserList.History,
                    onSelectList = {},
                ),
            )
        }

        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(empty).assertIsDisplayed()
        onNodeWithText(favouritesEmpty).assertDoesNotExist()
    }

    /** History offers the same three items as Favourites, all of them live. */
    @Test
    fun `History's menu holds export and import and Clear list`() = runComposeUiTest {
        var exports = 0
        lateinit var options: String
        lateinit var export: String
        lateinit var import: String
        lateinit var clear: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            export = stringResource(Res.string.favourites_export)
            import = stringResource(Res.string.favourites_import)
            clear = stringResource(Res.string.favourites_clear)
            StateUnderTest(
                state = FavouritesState(
                    content = FavouritesContentState.Ready(listOf(hit(1L))),
                    list = UserList.History,
                    onClearList = {},
                ),
                onExportClick = { exports++ },
                onImportClick = {},
            )
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()

        onNodeWithText(import).assertIsEnabled()
        onNodeWithText(clear).assertIsEnabled()
        onNodeWithText(export).performClick()
        waitForIdle()

        assertEquals(1, exports)
    }

    @Test
    fun `Favourites' menu holds Clear list beside export and import`() = runComposeUiTest {
        lateinit var options: String
        lateinit var export: String
        lateinit var import: String
        lateinit var clear: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            export = stringResource(Res.string.favourites_export)
            import = stringResource(Res.string.favourites_import)
            clear = stringResource(Res.string.favourites_clear)
            StateUnderTest(
                state = FavouritesState(
                    list = UserList.Favourites,
                    content = FavouritesContentState.Ready(listOf(hit(1L))),
                    onClearList = {},
                ),
            )
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()

        onNodeWithText(export).assertIsDisplayed()
        onNodeWithText(import).assertIsEnabled()
        onNodeWithText(clear).assertIsEnabled()
    }

    @Test
    fun `with nothing to clear Clear list is there and disabled`() = runComposeUiTest {
        lateinit var options: String
        lateinit var clear: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            clear = stringResource(Res.string.favourites_clear)
            StateUnderTest(
                state = FavouritesState(
                    list = UserList.Favourites,
                    content = FavouritesContentState.Ready(emptyList()),
                ),
            )
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()

        onNodeWithText(clear).assertIsNotEnabled()
    }

    @Test
    fun `the Clear list dialog names the list and reports the answer`() = runComposeUiTest {
        var confirms = 0
        var cancels = 0
        lateinit var title: String
        lateinit var confirm: String
        lateinit var cancel: String
        setContent {
            title = stringResource(
                Res.string.favourites_clear_title,
                stringResource(Res.string.history_title),
            )
            confirm = stringResource(Res.string.favourites_clear_confirm)
            cancel = stringResource(Res.string.favourites_clear_cancel)
            StateUnderTest(
                FavouritesState(
                    content = FavouritesContentState.Ready(listOf(hit(1L))),
                    clearPrompt = FavouritesClearPrompt(
                        list = UserList.History,
                        onConfirm = { confirms++ },
                        onCancel = { cancels++ },
                    ),
                ),
            )
        }

        onNodeWithText(title).assertIsDisplayed()
        onNodeWithText(cancel).performClick()
        waitForIdle()
        assertEquals(1, cancels)

        onNodeWithText(confirm).performClick()
        waitForIdle()
        assertEquals(1, confirms)
    }

    @Test
    fun `swiping a History row away removes that entry`() = runComposeUiTest {
        val removed = mutableListOf<Long>()
        setContent {
            StateUnderTest(
                FavouritesState(
                    content = FavouritesContentState.Ready(listOf(hit(7L))),
                    list = UserList.History,
                    onRemoveEntry = { removed += it },
                ),
            )
        }

        onNode(removableRow).performTouchInput { swipeLeft() }
        waitForIdle()

        assertEquals(listOf(7L), removed)
    }

    /**
     * A removal that never lands sends nothing back, so the swiped row
     * has to come back by itself. The clock is driven by hand, because
     * letting it run would also run past the moment the row is gone
     * and make the first assertion pass either way.
     */
    @Test
    fun `a swiped row whose removal never lands comes back`() = runComposeUiTest {
        setContent {
            StateUnderTest(
                FavouritesState(
                    content = FavouritesContentState.Ready(listOf(hit(7L))),
                    list = UserList.History,
                    onRemoveEntry = {},
                ),
            )
        }
        mainClock.autoAdvance = false

        onNode(removableRow).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("- to eat").assertIsNotDisplayed()

        mainClock.advanceTimeBy(2_000)
        onNodeWithText("- to eat").assertIsDisplayed()
    }

    @Test
    fun `a Favourites row can be swiped away too`() = runComposeUiTest {
        val removed = mutableListOf<Long>()
        setContent {
            StateUnderTest(
                FavouritesState(
                    content = FavouritesContentState.Ready(listOf(hit(7L))),
                    list = UserList.Favourites,
                    onRemoveEntry = { removed += it },
                ),
            )
        }

        onNode(removableRow).performTouchInput { swipeLeft() }
        waitForIdle()

        assertEquals(listOf(7L), removed)
    }

    @Test
    fun `a completed swipe plays exactly one confirm haptic`() = runComposeUiTest {
        val haptics = RecordingHaptics()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                StateUnderTest(
                    FavouritesState(
                        content = FavouritesContentState.Ready(listOf(hit(7L))),
                        list = UserList.History,
                        onRemoveEntry = {},
                    ),
                )
            }
        }

        onNode(removableRow).performTouchInput { swipeLeft() }
        waitForIdle()

        assertEquals(listOf(HapticFeedbackType.Confirm), haptics.played)
    }

    /**
     * Only end-to-start removes. A full swipe the other way must leave
     * the row where it was, remove nothing and play nothing.
     */
    @Test
    fun `a swipe from start to end removes nothing`() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val removed = mutableListOf<Long>()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                StateUnderTest(
                    FavouritesState(
                        content = FavouritesContentState.Ready(listOf(hit(7L))),
                        list = UserList.History,
                        onRemoveEntry = { removed += it },
                    ),
                )
            }
        }
        mainClock.autoAdvance = false

        onNode(removableRow).performTouchInput { swipeRight() }
        mainClock.advanceTimeBy(1_000)

        assertEquals(emptyList(), removed)
        assertEquals(emptyList(), haptics.played)
        onNodeWithText("- to eat").assertIsDisplayed()
    }

    /**
     * A slow drag a tenth of the way across is under both the distance
     * and the velocity threshold, so the row springs back. Asserting
     * the removal callback alongside is what shows the swipe really
     * fell short, rather than the haptic merely being missing.
     */
    @Test
    fun `a swipe that springs back plays no haptic and removes nothing`() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val removed = mutableListOf<Long>()
        setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                StateUnderTest(
                    FavouritesState(
                        content = FavouritesContentState.Ready(listOf(hit(7L))),
                        list = UserList.History,
                        onRemoveEntry = { removed += it },
                    ),
                )
            }
        }

        onNode(removableRow).performTouchInput {
            swipeLeft(startX = right - 1f, endX = right - 1f - width * 0.1f, durationMillis = 1_000)
        }
        waitForIdle()

        assertEquals(emptyList(), removed)
        assertEquals(emptyList(), haptics.played)
        onNodeWithText("- to eat").assertIsDisplayed()
    }

    @Test
    fun `switching lists starts the new list at its top`() = runComposeUiTest {
        val rows = (1L..40L).map { id -> hit(id).copy(senseLines = listOf("sense $id")) }
        var state by mutableStateOf(
            FavouritesState(list = UserList.Favourites, content = FavouritesContentState.Ready(rows)),
        )
        setContent { StateUnderTest(state) }

        onNode(hasScrollToIndexAction()).performScrollToIndex(39)
        waitForIdle()
        onNodeWithText("- sense 1").assertDoesNotExist()

        state = FavouritesState(
            content = FavouritesContentState.Ready(rows),
            list = UserList.History,
        )
        waitForIdle()

        onNodeWithText("- sense 1").assertIsDisplayed()
    }

    /**
     * Coming back to the tab: the composition is rebuilt from saved
     * state, and its first frame is the seeded one, which names no list.
     * The scroll position restored with it must survive the real state
     * arriving — on History above all, where a seeded frame defaulting
     * to Favourites read as a switch and threw the position away.
     */
    @Test
    fun `a scrolled History list stays scrolled when the tab is restored`() =
        runComposeUiTest { assertScrollSurvivesRestore(UserList.History) }

    @Test
    fun `a scrolled Favourites list stays scrolled when the tab is restored`() =
        runComposeUiTest { assertScrollSurvivesRestore(UserList.Favourites) }

    /**
     * The toolbar half of a switch. It is observed through the first
     * row's position: the toolbar's height is the list's top content
     * padding, so a toolbar left collapsed puts the first row higher
     * than it sits under an expanded one.
     */
    @Test
    fun `switching lists expands a collapsed toolbar again`() = runComposeUiTest {
        val rows = (1L..40L).map { id -> hit(id).copy(senseLines = listOf("sense $id")) }
        var state by mutableStateOf(
            FavouritesState(list = UserList.Favourites, content = FavouritesContentState.Ready(rows)),
        )
        setContent { StateUnderTest(state) }
        val expandedTop = onNodeWithText("- sense 1").fetchSemanticsNode().boundsInRoot.top

        onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }
        waitForIdle()
        onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        waitForIdle()
        val collapsedTop = onNodeWithText("- sense 1").fetchSemanticsNode().boundsInRoot.top
        assertTrue(collapsedTop < expandedTop, "the swipe has to have collapsed the toolbar")

        state = FavouritesState(list = UserList.History, content = FavouritesContentState.Ready(rows))
        waitForIdle()

        assertEquals(expandedTop, onNodeWithText("- sense 1").fetchSemanticsNode().boundsInRoot.top)
    }

    /**
     * The swipe's accessibility twin. It has to remove the same row the
     * swipe would and answer with the same single haptic.
     */
    @Test
    fun `the remove accessibility action removes that row with one haptic`() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val removed = mutableListOf<Long>()
        lateinit var label: String
        setContent {
            label = stringResource(
                Res.string.favourites_remove,
                stringResource(Res.string.history_title),
            )
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                StateUnderTest(
                    FavouritesState(
                        content = FavouritesContentState.Ready(listOf(hit(7L), hit(8L))),
                        list = UserList.History,
                        onRemoveEntry = { removed += it },
                    ),
                )
            }
        }

        onAllNodes(removableRow)[1].performCustomAccessibilityActionWithLabel(label)
        waitForIdle()

        assertEquals(listOf(8L), removed)
        assertEquals(listOf(HapticFeedbackType.Confirm), haptics.played)
    }

    /**
     * An empty History has nothing to export or clear, and is still a
     * perfectly good list to import into, so the menu stays open to it.
     */
    @Test
    fun `an empty History still offers Import and nothing else`() = runComposeUiTest {
        lateinit var options: String
        lateinit var export: String
        lateinit var import: String
        lateinit var clear: String
        setContent {
            options = stringResource(Res.string.favourites_options)
            export = stringResource(Res.string.favourites_export)
            import = stringResource(Res.string.favourites_import)
            clear = stringResource(Res.string.favourites_clear)
            StateUnderTest(
                state = FavouritesState(
                    list = UserList.History,
                    content = FavouritesContentState.Ready(emptyList()),
                    onClearList = null,
                ),
                onExportClick = null,
                onImportClick = {},
            )
        }

        onNodeWithContentDescription(options).assertIsEnabled().performClick()
        waitForIdle()

        onNodeWithText(import).assertIsEnabled()
        onNodeWithText(export).assertIsNotEnabled()
        onNodeWithText(clear).assertIsNotEnabled()
    }

    @Test
    fun `the list picker is a dropdown with a full-height touch target`() = runComposeUiTest {
        lateinit var title: String
        setContent {
            title = stringResource(Res.string.history_title)
            StateUnderTest(
                FavouritesState(
                    list = UserList.History,
                    content = FavouritesContentState.Ready(listOf(hit(1L))),
                    onSelectList = {},
                ),
            )
        }

        onNode(hasText(title) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assertHeightIsAtLeast(48.dp)
    }

    /**
     * What is asserted is that the row opens the entry and nothing else
     * is navigated. That opening from History records nothing is NOT
     * observable here, and is not claimed: the screen holds no store and
     * no recording callback, so there is nothing for a test to watch
     * stay silent. It holds by construction — recording is wired only
     * into SearchState — which SearchNavigationUiTest and
     * SearchStateProducerTest cover from the other side.
     */
    @Test
    fun `tapping a History row opens the entry`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            StateUnderTest(
                state = FavouritesState(
                    content = FavouritesContentState.Ready(listOf(hit(7L))),
                    list = UserList.History,
                    onRemoveEntry = {},
                ),
                navigation = navigation,
            )
        }

        onNodeWithText("- to eat").performClick()
        waitForIdle()

        assertEquals(listOf<Route>(EntryRoute(7L)), navigation.navigated)
    }
}

/**
 * Scrolls [list] down, saves the composition's state, throws the
 * composition away and builds it again from what was saved, the way a
 * tab coming back is built. The rebuilt composition's first frame is
 * the seeded state, as `produceScreenState` hands it out, and the real
 * one follows a frame later.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertScrollSurvivesRestore(list: UserList) {
    val rows = (1L..40L).map { id -> hit(id).copy(senseLines = listOf("sense $id")) }
    val real = FavouritesState(list = list, content = FavouritesContentState.Ready(rows), onSelectList = {})
    var registry by mutableStateOf(SaveableStateRegistry(null) { true })
    var shown by mutableStateOf(true)
    var rebuilt = false
    setContent {
        CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
            if (shown) {
                var state by remember { mutableStateOf(if (rebuilt) FavouritesState() else real) }
                LaunchedEffect(Unit) { state = real }
                StateUnderTest(state)
            }
        }
    }

    onNode(hasScrollToIndexAction()).performScrollToIndex(39)
    waitForIdle()
    onNodeWithText("- sense 1").assertDoesNotExist()

    val saved = registry.performSave()
    shown = false
    waitForIdle()
    rebuilt = true
    registry = SaveableStateRegistry(saved) { true }
    shown = true
    waitForIdle()

    onNodeWithText("- sense 40").assertExists()
    onNodeWithText("- sense 1").assertDoesNotExist()
}

private class RecordingHaptics : HapticFeedback {
    val played = mutableListOf<HapticFeedbackType>()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        played += hapticFeedbackType
    }
}

/** A row's swipe container: the only node carrying the removal action. */
private val removableRow = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)

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
                list = UserList.Favourites,
                content = content,
                importPrompt = importPrompt,
            ),
            onExportClick = onExportClick,
            onImportClick = onImportClick,
        )
    }
}

@Composable
private fun StateUnderTest(
    state: FavouritesState,
    navigation: RecordingNavigationController = RecordingNavigationController(),
    onExportClick: (() -> Unit)? = {},
    onImportClick: (() -> Unit)? = {},
) {
    ScreenHost(navigation = navigation) {
        FavouritesScreen(
            state = state,
            onExportClick = onExportClick,
            onImportClick = onImportClick,
        )
    }
}
