package cc.hosaka.okonomi.feature.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private const val ROOT_TEXT = "root screen"

private const val PUSHED_TEXT = "pushed screen"

/**
 * A push and a pop through the real [NavDisplay] and
 * [BackStackNavigationController], with [routeEntryProvider] rendering
 * the routes.
 *
 * Deliberately a smoke test and nothing more: it says that a pushed
 * destination ends up composed and that popping brings the previous one
 * back. The transition between them is not asserted — a cross fade is
 * not something this environment can observe, and a test that claimed
 * to check it would be worse than no test.
 */
@OptIn(ExperimentalTestApi::class)
class NavigationDisplayUiTest : ComposeUiTestBase() {

    @Test
    fun `a push composes the destination and a pop brings the previous screen back`() =
        runComposeUiTest {
            lateinit var controller: NavigationController
            setContent {
                val backStack = rememberNavBackStack(testSavedStateConfiguration, RootTestRoute)
                controller = remember(backStack) { BackStackNavigationController(backStack) }
                NavDisplay(
                    backStack = backStack,
                    entryProvider = routeEntryProvider,
                    onBack = { controller.pop() },
                )
            }

            onNodeWithText(ROOT_TEXT).assertIsDisplayed()

            runOnIdle { controller.navigate(PushedTestRoute) }
            waitForIdle()

            onNodeWithText(PUSHED_TEXT).assertIsDisplayed()

            runOnIdle { controller.pop() }
            waitForIdle()

            onNodeWithText(ROOT_TEXT).assertIsDisplayed()
        }
}

/**
 * The routes this test pushes have to be serializable the same way the
 * app's own are, because the back stack persists itself.
 */
private val testSavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(RootTestRoute::class)
            subclass(PushedTestRoute::class)
        }
    }
}

@Serializable
internal data object RootTestRoute : Route, NavKey {
    @Composable
    override fun Content() {
        Text(text = ROOT_TEXT)
    }
}

@Serializable
internal data object PushedTestRoute : Route, NavKey {
    @Composable
    override fun Content() {
        Text(text = PUSHED_TEXT)
    }
}
