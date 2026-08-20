package cc.hosaka.okonomi.feature.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The seam between screens and the navigation back stack. Producers
 * navigate through this interface without depending on Compose.
 */
@Stable
interface NavigationController {
    fun navigate(route: Route)

    /**
     * Pops the top route. Returns `false` if the back stack is at
     * its root and nothing was popped.
     */
    fun pop(): Boolean
}

val LocalNavigationController = staticCompositionLocalOf<NavigationController> {
    error("Navigation controller must be initialized!")
}

class BackStackNavigationController(
    private val backStack: NavBackStack<NavKey>,
) : NavigationController {
    override fun navigate(route: Route) {
        backStack.add(route)
    }

    override fun pop(): Boolean {
        if (backStack.size <= 1) {
            return false
        }
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
