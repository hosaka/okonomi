package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import cc.hosaka.okonomi.feature.navigation.BackStackNavigationController
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.navigationSavedStateConfiguration
import cc.hosaka.okonomi.feature.navigation.routeEntryProvider
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test

private const val PLACEHOLDER = "Search"

private const val WORD = "為る"

/**
 * The seam between the route and the screen, driven through the real
 * `NavDisplay` and `routeEntryProvider` exactly as the app composes
 * them.
 *
 * Nothing else covers it. `PhrasesTapUiTest` stops at the navigation
 * controller and `SearchStateProducerTest` hands the producer an
 * `initialQuery` itself, so dropping the argument in
 * `SearchRoute.Content` — every pushed search opening blank — was a
 * change the whole suite passed.
 *
 * The search itself is left to fail against a dictionary a host test
 * has no file for; that is the results area's business, and what is
 * asserted here is the field.
 */
@OptIn(ExperimentalTestApi::class)
class SearchRouteUiTest : ComposeUiTestBase() {

    @Test
    fun `a pushed route opens the screen with its query already in the field`() = runComposeUiTest {
        lateinit var navigation: NavigationController
        setContent {
            navigation = SearchSectionUnderTest()
        }

        onNodeWithText(PLACEHOLDER).assertIsDisplayed()

        runOnIdle { navigation.navigate(SearchRoute(WORD)) }
        waitForIdle()

        onNodeWithText(WORD).assertIsDisplayed()
    }

    /**
     * Alex's ruling: the field is filled and the search runs, but focus
     * is not taken, so the results are the first thing on screen rather
     * than a keyboard over them. IME visibility is not observable here;
     * focus is, and focus is what raises it.
     */
    @Test
    fun `arriving on a pushed search takes no focus`() = runComposeUiTest {
        lateinit var navigation: NavigationController
        setContent {
            navigation = SearchSectionUnderTest()
        }

        runOnIdle { navigation.navigate(SearchRoute(WORD)) }
        waitForIdle()

        onNodeWithText(WORD).assertIsNotFocused()
    }

    @Test
    fun `the section root opens on an empty field`() = runComposeUiTest {
        setContent {
            SearchSectionUnderTest()
        }

        onNodeWithText(PLACEHOLDER).assertIsDisplayed()
        onNodeWithText(PLACEHOLDER).assertIsNotFocused()
    }
}

/**
 * A Search section as `HomeScreen` builds one: rooted on the tab's own
 * route, with the two entry decorators the shell installs — the view
 * model store decorator is not optional here, because every screen
 * reaching `produceScreenState` resolves one.
 */
@Composable
private fun SearchSectionUnderTest(): NavigationController {
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, SearchRoute())
    val controller = remember(backStack) { BackStackNavigationController(backStack) }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator<NavKey>(),
        ),
        entryProvider = routeEntryProvider,
    )
    CompositionLocalProvider(
        LocalNavigationController provides controller,
    ) {
        NavDisplay(
            entries = entries,
            onBack = { controller.pop() },
        )
    }
    return controller
}
