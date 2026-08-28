package cc.hosaka.okonomi.feature.radical

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import cc.hosaka.okonomi.feature.navigation.BackStackNavigationController
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.navigationSavedStateConfiguration
import cc.hosaka.okonomi.feature.navigation.routeEntryProvider
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.radical_back
import okonomi.shared.generated.resources.radical_title
import org.jetbrains.compose.resources.stringResource

private const val RADICAL = "口"

/**
 * The seam between [RadicalRoute] and its screen, driven through the
 * real `NavDisplay` and `routeEntryProvider` exactly as the app composes
 * them.
 *
 * Nothing else covers it, and the gap is the one `SearchRouteUiTest`
 * exists for: `KanjiDetailDialogUiTest` stops at the navigation
 * controller and asserts only which route object was pushed;
 * `NavigationGraphTest` asserts only that a serializer is registered;
 * and both `RadicalScreenUiTest` and `RadicalStateProducerTest` hand the
 * radical in themselves. So `Content()` passing the empty string — or
 * any value other than its own [RadicalRoute.radical] — into the
 * producer would put every radical tap onto a blank, erroring screen
 * with the whole suite green. That is precisely how dropping the
 * argument in `SearchRoute.Content` once passed.
 *
 * The lookup itself is left to fail against a dictionary a host test has
 * no file for; the grid is the loader's business, and what is asserted
 * here is that the screen knows which radical it was opened for. The bar
 * reads that off the initial state, so it needs no database.
 */
@OptIn(ExperimentalTestApi::class)
class RadicalRouteUiTest : ComposeUiTestBase() {

    @Test
    fun `a pushed route opens the screen naming its own radical`() = runComposeUiTest {
        lateinit var navigation: NavigationController
        lateinit var title: String
        setContent {
            title = stringResource(Res.string.radical_title, RADICAL)
            navigation = SectionUnderTest()
        }

        runOnIdle { navigation.navigate(RadicalRoute(RADICAL)) }
        waitForIdle()

        onNodeWithText(title).assertIsDisplayed()
    }

    /**
     * Back has to work from the frame the screen arrives on, not from
     * the frame its load reports. The lookup fails here — a host test
     * has no dictionary file — so the screen never leaves the state it
     * opened in, which is exactly the state this pins: nullable and
     * unseeded, `onBack` was null there and the arrow was dead while the
     * only other way off the screen is an edge swipe iOS does not have.
     */
    @Test
    fun `back leaves the screen from the state it opened in`() = runComposeUiTest {
        lateinit var navigation: NavigationController
        lateinit var title: String
        lateinit var back: String
        setContent {
            title = stringResource(Res.string.radical_title, RADICAL)
            back = stringResource(Res.string.radical_back)
            navigation = SectionUnderTest()
        }

        runOnIdle { navigation.navigate(RadicalRoute(RADICAL)) }
        waitForIdle()
        onNodeWithText(title).assertIsDisplayed()

        onNodeWithContentDescription(back).performClick()
        waitForIdle()

        onNodeWithText(title).assertDoesNotExist()
    }
}

/**
 * A section as `HomeScreen` builds one, rooted on the Search tab's own
 * route — the same shape `SearchRouteUiTest` uses, and for the same
 * reason: every screen reaching `produceScreenState` resolves a view
 * model, so the store decorator is not optional here.
 */
@Composable
private fun SectionUnderTest(): NavigationController {
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
