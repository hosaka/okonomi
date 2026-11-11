package cc.hosaka.okonomi.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import cc.hosaka.okonomi.feature.home.navigation.homeFavouritesItem
import cc.hosaka.okonomi.feature.home.navigation.homeSearchItem
import cc.hosaka.okonomi.feature.navigation.EntryLinks
import cc.hosaka.okonomi.feature.navigation.TextLookups
import cc.hosaka.okonomi.feature.navigation.navigationSavedStateConfiguration
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.ui.coach.CoachMarkRegistry
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.PolymorphicSerializer
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
 * Text looked up from another app's selection toolbar is the same
 * hand-off with a search in place of the entry: the pushed route is a
 * `SearchRoute` carrying the text, marked so that system back on it
 * leaves, and every rule above holds for it too. What the shell cannot
 * see is checked on a device the same way: the toolbar offering
 * "Okonomi", `ProcessTextActivity` moving the lookup into okonomi's own
 * task, and that the app the text came from keeps it unchanged. On a device, naming the trampoline (an
 * implicit `PROCESS_TEXT` intent opens Android's app chooser instead):
 * `adb shell am start -n cc.hosaka.okonomi/.ProcessTextActivity -a android.intent.action.PROCESS_TEXT -t text/plain --es android.intent.extra.PROCESS_TEXT '食べた'`,
 * then `adb shell input keyevent KEYCODE_BACK`. A shell launch never
 * asks for a result, so only a real selection covers that path. The
 * activities' routing and guards are `AndroidHandOffs`, tested on its
 * own in `androidHostTest`.
 *
 * Text selected in okonomi itself is not a hand-off. It opens an
 * ordinary search over the screen being read, in whichever section that
 * is, and back returns there; the tests for it read Favourites' stack.
 *
 * The same real shell, probes and back input also host one test of back
 * ordering on a pushed search, which needs exactly this setup.
 */
@OptIn(ExperimentalTestApi::class)
class HomeEntryLinkUiTest : ComposeUiTestBase() {

    /**
     * The shell with a counted leave hook and Search's navigation in reach.
     * With [unstableCreationExtras] its view model store owner behaves like
     * an activity whose intent carries extras; see [UnstableExtrasOwner].
     */
    private class Shell(private val unstableCreationExtras: Boolean = false) {
        val links = EntryLinks()
        val lookups = TextLookups()
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
        val favouritesStack: List<NavKey> get() = checkNotNull(favourites).stack()

        @Composable
        fun Content() {
            searchLabel = stringResource(Res.string.home_search_label)
            favouritesLabel = stringResource(Res.string.home_favourites_label)
            backLabel = stringResource(Res.string.entry_back)
            optionsLabel = stringResource(Res.string.search_options)
            namesLabel = stringResource(Res.string.search_names_toggle)
            val hostOwner = checkNotNull(LocalViewModelStoreOwner.current)
            val owner = remember(hostOwner) {
                if (unstableCreationExtras) UnstableExtrasOwner(hostOwner) else hostOwner
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                Body()
            }
        }

        @Composable
        private fun Body() {
            BackHost(back) {
                HomeScreen(
                    coachMarks = remember { CoachMarkRegistry() },
                    entryLinks = links,
                    textLookups = lookups,
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

    private val favouritesRoot = homeFavouritesItem.route

    /** The filters menu's Names toggle, which exists only while the menu is open. */
    private fun namesPill(shell: Shell) = isToggleable() and hasText(shell.namesLabel)

    /** The search field, showing [text]. */
    private fun searchField(text: String) = hasSetTextAction() and hasText(text)

    private val lookedUp = SearchRoute("食べた", openedFromSelection = true)

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

    @Test
    fun `a lookup while on another tab pushes a search for the text and system back leaves from Search's root`() =
        runComposeUiTest {
            val shell = Shell()
            setContent { shell.Content() }
            waitForIdle()
            onNode(tab(shell.favouritesLabel)).performClick()
            waitForIdle()
            onNode(tab(shell.favouritesLabel)).assertIsSelected()

            runOnIdle { shell.lookups.lookUp("食べた") }
            waitForIdle()

            assertEquals(listOf(searchRoot, lookedUp), shell.searchStack)
            onNode(searchField("食べた")).assertExists()

            runOnIdle { shell.back.back() }
            waitForIdle()

            assertEquals(1, shell.leaves)
            assertEquals(listOf(searchRoot), shell.searchStack)
            onNode(tab(shell.searchLabel)).assertIsSelected()
        }

    @Test
    fun `the header back arrow on a lookup stays in the app`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { shell.lookups.lookUp("食べた") }
        waitForIdle()

        onNodeWithContentDescription(shell.backLabel).performClick()
        waitForIdle()

        assertEquals(0, shell.leaves)
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    @Test
    fun `an entry opened from a lookup pops first and only then does back leave`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { shell.lookups.lookUp("食べた") }
        waitForIdle()
        // A hit opened from the looked-up search.
        runOnIdle { checkNotNull(shell.search).controller.navigate(EntryRoute(1_358_280L)) }
        waitForIdle()

        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(0, shell.leaves)
        assertEquals(listOf(searchRoot, lookedUp), shell.searchStack)

        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(1, shell.leaves)
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    /**
     * The lookup's own leave handler must not outrank the filters menu's:
     * back with the menu open closes the menu and stays in the app, as on
     * any pushed search. Started from another tab, so the Search section
     * and the pushed search are composed together, the ordering most
     * likely to put the leave handler last.
     */
    @Test
    fun `system back on a lookup closes its filters menu before leaving`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        onNode(tab(shell.favouritesLabel)).performClick()
        waitForIdle()
        runOnIdle { shell.lookups.lookUp("食べた") }
        waitForIdle()
        onNodeWithContentDescription(shell.optionsLabel).performClick()
        waitForIdle()
        onNode(namesPill(shell)).assertIsDisplayed()

        runOnIdle { shell.back.back() }
        waitForIdle()

        onNode(namesPill(shell)).assertDoesNotExist()
        assertEquals(0, shell.leaves)
        assertEquals(listOf(searchRoot, lookedUp), shell.searchStack)

        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(1, shell.leaves)
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    @Test
    fun `switching tabs away from a lookup makes it an ordinary search`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { shell.lookups.lookUp("食べた") }
        waitForIdle()

        // Through the probe: the bar is hidden while the search is pushed.
        runOnIdle { checkNotNull(shell.favourites).select() }
        waitForIdle()
        runOnIdle { checkNotNull(shell.search).select() }
        waitForIdle()

        assertEquals(listOf(searchRoot, SearchRoute("食べた")), shell.searchStack)
        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(0, shell.leaves)
        assertEquals(listOf(searchRoot), shell.searchStack)
    }

    @Test
    fun `a second lookup replaces the first and whatever was opened over it`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { shell.lookups.lookUp("飲む") }
        waitForIdle()
        runOnIdle { checkNotNull(shell.search).controller.navigate(EntryRoute(1_358_280L)) }
        waitForIdle()

        runOnIdle { shell.lookups.lookUp("食べた") }
        waitForIdle()

        assertEquals(listOf(searchRoot, lookedUp), shell.searchStack)
        onNode(searchField("食べた")).assertExists()
    }

    /**
     * Through the serializer and configuration `rememberNavBackStack`
     * saves a section's stack with, so what is asserted is what a
     * configuration change or process death actually restores.
     */
    @Test
    fun `a looked-up search restored from saved state is an ordinary one`() {
        val serializer = NavBackStackSerializer(PolymorphicSerializer(NavKey::class))
        val saved = encodeToSavedState(serializer, NavBackStack(searchRoot, lookedUp), navigationSavedStateConfiguration)

        val restored = decodeFromSavedState(serializer, saved, navigationSavedStateConfiguration)

        assertEquals(listOf(searchRoot, SearchRoute("食べた")), restored.toList())
    }

    /** Favourites with an entry open, where text selected in it is looked up. */
    private fun ComposeUiTest.readingAnEntryOnFavourites(shell: Shell) {
        runOnIdle { checkNotNull(shell.favourites).select() }
        waitForIdle()
        runOnIdle { checkNotNull(shell.favourites).controller.navigate(EntryRoute(1_358_280L)) }
        waitForIdle()
    }

    @Test
    fun `a lookup from okonomi itself pushes an ordinary search over the screen being read`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        // Something open on Search too, which a lookup from another app
        // would drop.
        runOnIdle { checkNotNull(shell.search).controller.navigate(EntryRoute(1_360_010L)) }
        waitForIdle()
        readingAnEntryOnFavourites(shell)

        runOnIdle { shell.lookups.lookUp("食べた", fromOkonomi = true) }
        waitForIdle()

        assertEquals(listOf(favouritesRoot, EntryRoute(1_358_280L), SearchRoute("食べた")), shell.favouritesStack)
        assertEquals(listOf(searchRoot, EntryRoute(1_360_010L)), shell.searchStack)
        // Shown, so Favourites is still the selected section: the search is
        // on no other stack.
        onNode(searchField("食べた")).assertExists()
    }

    @Test
    fun `back on a lookup from okonomi itself returns to the screen it was opened over`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        readingAnEntryOnFavourites(shell)
        runOnIdle { shell.lookups.lookUp("食べた", fromOkonomi = true) }
        waitForIdle()

        runOnIdle { shell.back.back() }
        waitForIdle()

        assertEquals(0, shell.leaves)
        assertEquals(listOf(favouritesRoot, EntryRoute(1_358_280L)), shell.favouritesStack)
    }

    /**
     * Pins the accepted trade-off rather than a behaviour anyone wants:
     * clearing the mark re-keys the route, and the screen's state goes
     * with the old key. A change that kept the edit across the tab switch
     * turns this red, which is the point: it should come with the
     * redesign logged in `deferred-work.md`, not by accident.
     */
    @Test
    fun `an edited lookup shows the selection again once a tab switch clears its mark`() = runComposeUiTest {
        val shell = Shell()
        setContent { shell.Content() }
        waitForIdle()
        runOnIdle { shell.lookups.lookUp("食べた") }
        waitForIdle()
        onNode(searchField("食べた")).performTextReplacement("食べる")
        waitForIdle()
        onNode(searchField("食べる")).assertExists()

        runOnIdle { checkNotNull(shell.favourites).select() }
        waitForIdle()
        runOnIdle { checkNotNull(shell.search).select() }
        waitForIdle()

        assertEquals(listOf(searchRoot, SearchRoute("食べた")), shell.searchStack)
        onNode(searchField("食べた")).assertExists()
    }

    /**
     * On a device every lookup wiped every screen's view model: the
     * activity's creation extras, which the entry decorator's store
     * provider was remembered on, are a new object on each read while its
     * intent carries extras, and a forwarded lookup always does. The test
     * activity has no intent extras, so the shell is handed an owner that
     * behaves as that activity does; without it this cannot fail.
     */
    @Test
    fun `a lookup leaves Search's typed query alone while the activity's creation extras change on every read`() =
        runComposeUiTest {
            val shell = Shell(unstableCreationExtras = true)
            setContent { shell.Content() }
            waitForIdle()
            onNode(hasSetTextAction()).performTextInput("eat food")
            waitForIdle()
            onNode(searchField("eat food")).assertExists()

            runOnIdle { shell.lookups.lookUp("food") }
            waitForIdle()
            onNode(searchField("food")).assertExists()
            onNodeWithContentDescription(shell.backLabel).performClick()
            waitForIdle()

            assertEquals(listOf(searchRoot), shell.searchStack)
            onNode(searchField("eat food")).assertExists()
        }
}

/**
 * [host]'s store and factory, with creation extras that are a new,
 * unequal object on every read. That is what a `ComponentActivity` gives
 * while its intent carries extras: it puts `Intent.getExtras()` in them,
 * and that returns a fresh `Bundle` each call.
 */
private class UnstableExtrasOwner(
    private val host: ViewModelStoreOwner,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore: ViewModelStore
        get() = host.viewModelStore

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = (host as? HasDefaultViewModelProviderFactory)?.defaultViewModelProviderFactory ?: viewModelFactory {}

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras().apply { set(FreshObjectKey, Any()) }

    private object FreshObjectKey : CreationExtras.Key<Any>
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
