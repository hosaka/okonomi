package cc.hosaka.okonomi.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import cc.hosaka.okonomi.feature.home.navigation.homeFavouritesItem
import cc.hosaka.okonomi.feature.home.navigation.homeSearchItem
import cc.hosaka.okonomi.feature.navigation.EntryLinks
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.ui.coach.CoachMarkRegistry
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_back
import okonomi.shared.generated.resources.home_favourites_label
import okonomi.shared.generated.resources.home_search_label
import okonomi.shared.generated.resources.search_options
import okonomi.shared.generated.resources.search_names_toggle
import org.jetbrains.compose.resources.stringResource

/**
 * `okonomi://entry/<id>` links, as the real shell opens them: the Search
 * section is selected, dropped to its root and the entry pushed over it;
 * system back on that entry leaves for the app that sent the link, and
 * everything else about back stays as it was.
 *
 * Search's back stack is read through the shell's test probe, so which
 * entry a link pushed is asserted directly. The entry screen itself has
 * no dictionary behind the host tests and shows its error body, which is
 * enough for its header back arrow.
 *
 * Checked manually on a device rather than here: the manifest's intent
 * filter and `singleTask`; `MainActivity` forwarding the URI from
 * `onCreate` and `onNewIntent` and ignoring a relaunch from Recents (there
 * is no androidApp test source set); and what `moveTaskToBack` does once
 * the leave hook is called — which app comes to the front is the
 * system's. Here the hook is counted instead. On a device:
 * `adb shell am start -a android.intent.action.VIEW -d okonomi://entry/1360010`,
 * then `adb shell input keyevent KEYCODE_BACK`.
 *
 * The same real shell, probes and back input also host one test of back
 * ordering on a pushed search, which needs exactly this setup.
 */
@OptIn(ExperimentalTestApi::class)
class HomeEntryLinkUiTest : ComposeUiTestBase() {

    /** The shell with a counted leave hook and Search's navigation in reach. */
    private class Shell {
        val links = EntryLinks()
        val back = TestBackInput()
        var leaves = 0
        var search: HomeSectionProbe? = null
        var favourites: HomeSectionProbe? = null
        var searchLabel = ""
        var favouritesLabel = ""
        var backLabel = ""
        var optionsLabel = ""
        var namesLabel = ""

        val searchStack: List<NavKey> get() = checkNotNull(search).stack()

        @Composable
        fun Content() {
            searchLabel = stringResource(Res.string.home_search_label)
            favouritesLabel = stringResource(Res.string.home_favourites_label)
            backLabel = stringResource(Res.string.entry_back)
            optionsLabel = stringResource(Res.string.search_options)
            namesLabel = stringResource(Res.string.search_names_toggle)
            BackHost(back) {
                HomeScreen(
                    coachMarks = remember { CoachMarkRegistry() },
                    entryLinks = links,
                    // The real store answers on a dispatcher the test
                    // clock cannot wait for; see CoachMarksUiTest.
                    preferences = remember { FakePreferenceStore() },
                    leaveApp = { leaves++ },
                    onSection = { key, probe ->
                        when (key) {
                            homeSearchItem.key -> search = probe
                            homeFavouritesItem.key -> favourites = probe
                        }
                    },
                )
            }
        }
    }

    private fun tab(label: String) = hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    private val searchRoot = homeSearchItem.route

    /** The filters menu's Names toggle, which exists only while the menu is open. */
    private fun namesPill(shell: Shell) = isToggleable() and hasText(shell.namesLabel)

    @Test
    fun `a link while on another tab pushes that entry over Search and system back leaves from Search's root`() =
        runComposeUiTest {
            val shell = Shell()
            setContent { shell.Content() }
            waitForIdle()
            onNode(tab(shell.favouritesLabel)).performClick()
            waitForIdle()
            onNode(tab(shell.favouritesLabel)).assertIsSelected()

            runOnIdle { shell.links.open("okonomi://entry/1360010") }
            waitForIdle()

            assertEquals(listOf(searchRoot, EntryRoute(1_360_010L, openedFromLink = true)), shell.searchStack)
            onNodeWithContentDescription(shell.backLabel).assertExists()

            runOnIdle { shell.back.back() }
            waitForIdle()

            assertEquals(1, shell.leaves)
            assertEquals(listOf(searchRoot), shell.searchStack)
            onNode(tab(shell.searchLabel)).assertIsSelected()
            onNode(hasSetTextAction()).assertExists()
        }

    /**
     * On a pushed search the filters menu's back handler competes with
     * NavDisplay's pop (the shell's own handler is off while Search, the
     * default section, is selected). Back with the menu open closes the
     * menu and leaves the stack alone; only the next back pops the search.
     * The root-search case is `SearchNamesUiTest`'s "system back closes an
     * open menu and only an open one".
     */
    @Test
    fun `system back on a pushed search closes its filters menu before popping the search`() =
        runComposeUiTest {
            val shell = Shell()
            setContent { shell.Content() }
            waitForIdle()
            // Any query will do — the host tests have no dictionary — but it
            // must not be null, or the pushed key would equal Search's root.
            val pushed = SearchRoute(query = "x")
            runOnIdle { checkNotNull(shell.search).controller.navigate(pushed) }
            waitForIdle()
            onNodeWithContentDescription(shell.optionsLabel).performClick()
            waitForIdle()
            onNode(namesPill(shell)).assertIsDisplayed()

            runOnIdle { shell.back.back() }
            waitForIdle()

            onNode(namesPill(shell)).assertDoesNotExist()
            assertEquals(listOf(searchRoot, pushed), shell.searchStack)

            runOnIdle { shell.back.back() }
            waitForIdle()

            assertEquals(listOf(searchRoot), shell.searchStack)
        }

    @Test
    fun `the header back arrow on a linked entry stays in the app`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { shell.links.open("okonomi://entry/1360010") }
        waitForIdle()

        onNodeWithContentDescription(shell.backLabel).performClick()
        waitForIdle()

        assertEquals(0, shell.leaves)
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    @Test
    fun `a link drops what Search had open so the arrow lands on Search's root`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { checkNotNull(shell.search).controller.navigate(EntryRoute(1_358_280L)) }
        waitForIdle()

        runOnIdle { shell.links.open("okonomi://entry/1360010") }
        waitForIdle()
        assertEquals(listOf(searchRoot, EntryRoute(1_360_010L, openedFromLink = true)), shell.searchStack)

        onNodeWithContentDescription(shell.backLabel).performClick()
        waitForIdle()

        assertEquals(listOf(searchRoot), shell.searchStack)
        assertEquals(0, shell.leaves)
    }

    @Test
    fun `a screen pushed over the linked entry pops first and only then does back leave`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { shell.links.open("okonomi://entry/1360010") }
        waitForIdle()
        // A related word opened from the linked entry.
        runOnIdle { checkNotNull(shell.search).controller.navigate(EntryRoute(1_358_280L)) }
        waitForIdle()

        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(0, shell.leaves)
        assertEquals(listOf(searchRoot, EntryRoute(1_360_010L, openedFromLink = true)), shell.searchStack)

        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(1, shell.leaves)
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    @Test
    fun `an entry opened from within the app never leaves it`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { checkNotNull(shell.search).controller.navigate(EntryRoute(1_360_010L)) }
        waitForIdle()

        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(0, shell.leaves)
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    @Test
    fun `switching tabs away from a linked entry makes it an ordinary one`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { shell.links.open("okonomi://entry/1360010") }
        waitForIdle()

        // Through the probe: the bar is hidden while the entry is pushed.
        runOnIdle { checkNotNull(shell.favourites).select() }
        waitForIdle()
        runOnIdle { checkNotNull(shell.search).select() }
        waitForIdle()

        assertEquals(listOf(searchRoot, EntryRoute(1_360_010L)), shell.searchStack)
        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(0, shell.leaves)
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    @Test
    fun `two links queued before the shell listens leave only the newer open`() = runComposeUiTest {
        val shell = Shell()
        shell.links.open("okonomi://entry/1358280")
        shell.links.open("okonomi://entry/1360010")

        setContent { shell.Content() }
        waitForIdle()

        assertEquals(listOf(searchRoot, EntryRoute(1_360_010L, openedFromLink = true)), shell.searchStack)
    }

    @Test
    fun `a second link replaces the first rather than stacking on it`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()

        runOnIdle { shell.links.open("okonomi://entry/1358280") }
        waitForIdle()
        runOnIdle { shell.links.open("okonomi://entry/1360010") }
        waitForIdle()

        assertEquals(listOf(searchRoot, EntryRoute(1_360_010L, openedFromLink = true)), shell.searchStack)
    }

    @Test
    fun `a malformed link changes nothing`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        onNode(tab(shell.favouritesLabel)).performClick()
        waitForIdle()

        runOnIdle { shell.links.open("okonomi://entry/-3") }
        waitForIdle()

        onNode(tab(shell.favouritesLabel)).assertIsSelected()
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    @Test
    fun `a linked entry restored from saved state is an ordinary one`() {
        val saved = Json.encodeToString(EntryRoute.serializer(), EntryRoute(1_360_010L, openedFromLink = true))

        assertEquals(EntryRoute(1_360_010L), Json.decodeFromString(EntryRoute.serializer(), saved))
    }
}

/** A system back the test can press. */
private class TestBackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
}

/** A back dispatcher of the test's own, wired to [back]. */
@Composable
private fun BackHost(back: TestBackInput, content: @Composable () -> Unit) {
    val owner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher().apply {
                addInput(back)
            }
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
        content()
    }
}
