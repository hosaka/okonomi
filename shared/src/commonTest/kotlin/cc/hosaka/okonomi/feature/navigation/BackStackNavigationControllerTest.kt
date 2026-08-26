package cc.hosaka.okonomi.feature.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.feature.settings.SettingsRoute
import cc.hosaka.okonomi.feature.word.EntryRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackStackNavigationControllerTest {
    @Test
    fun `pop at root returns false and keeps the root`() {
        val backStack = NavBackStack<NavKey>(SettingsRoute)
        val controller = BackStackNavigationController(backStack)

        val popped = controller.pop()

        assertFalse(popped)
        assertEquals(listOf<NavKey>(SettingsRoute), backStack.toList())
    }

    @Test
    fun `navigate pushes and pop restores the root`() {
        val backStack = NavBackStack<NavKey>(SettingsRoute)
        val controller = BackStackNavigationController(backStack)

        controller.navigate(SearchRoute())
        assertEquals(listOf<NavKey>(SettingsRoute, SearchRoute()), backStack.toList())

        val popped = controller.pop()

        assertTrue(popped)
        assertEquals(listOf<NavKey>(SettingsRoute), backStack.toList())
    }

    @Test
    fun `distinct routes stack in the order they were pushed`() {
        val backStack = NavBackStack<NavKey>(SearchRoute())
        val controller = BackStackNavigationController(backStack)

        controller.navigate(EntryRoute(1L))
        controller.navigate(SearchRoute("為る"))
        controller.navigate(EntryRoute(2L))

        assertEquals(
            listOf<NavKey>(SearchRoute(), EntryRoute(1L), SearchRoute("為る"), EntryRoute(2L)),
            backStack.toList(),
        )
    }

    /**
     * The stack Navigation 3 cannot hold: two equal keys share one
     * `ViewModelStore`, so the second search would open showing
     * whatever the first had been edited to, and composing both at once
     * throws outright.
     *
     * Reachable by tapping: sentences are shared between entries, so
     * the same word can be tapped from two different ones.
     */
    @Test
    fun `pushing a route already on the stack lifts it instead of duplicating it`() {
        val backStack = NavBackStack<NavKey>(SearchRoute())
        val controller = BackStackNavigationController(backStack)
        controller.navigate(EntryRoute(1L))
        controller.navigate(SearchRoute("為る"))
        controller.navigate(EntryRoute(2L))

        controller.navigate(SearchRoute("為る"))

        assertEquals(
            listOf<NavKey>(SearchRoute(), EntryRoute(1L), EntryRoute(2L), SearchRoute("為る")),
            backStack.toList(),
            "lifted to the top, so back returns to the entry the reader just left",
        )
    }

    @Test
    fun `pushing the route already on top does nothing`() {
        val backStack = NavBackStack<NavKey>(SearchRoute())
        val controller = BackStackNavigationController(backStack)
        controller.navigate(EntryRoute(1L))

        controller.navigate(EntryRoute(1L))

        assertEquals(listOf<NavKey>(SearchRoute(), EntryRoute(1L)), backStack.toList())
    }

    /**
     * The section root is not a screen to be moved. It is what [pop]
     * refuses to drop, what a tab reselect pops to and what the shell
     * counts to decide whether to show the navigation bar, so asking to
     * navigate to it means going back to it.
     */
    @Test
    fun `navigating to the section root goes back to it rather than moving it`() {
        val backStack = NavBackStack<NavKey>(SearchRoute())
        val controller = BackStackNavigationController(backStack)
        controller.navigate(EntryRoute(1L))
        controller.navigate(EntryRoute(2L))

        controller.navigate(SearchRoute())

        assertEquals(listOf<NavKey>(SearchRoute()), backStack.toList())
    }
}
