package cc.hosaka.okonomi.feature.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.feature.settings.SettingsRoute
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

        controller.navigate(SearchRoute)
        assertEquals(listOf<NavKey>(SettingsRoute, SearchRoute), backStack.toList())

        val popped = controller.pop()

        assertTrue(popped)
        assertEquals(listOf<NavKey>(SettingsRoute), backStack.toList())
    }
}
