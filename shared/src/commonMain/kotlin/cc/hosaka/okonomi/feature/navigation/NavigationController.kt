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
    /**
     * Pushes [route] on top of the stack. A route already on the stack
     * is moved to the top rather than pushed a second time — see
     * [BackStackNavigationController.navigate] for why that is not an
     * optimisation.
     */
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
    /**
     * A route already on the stack is lifted back to the top instead of
     * being added again. Two equal keys on one stack are not merely
     * untidy:
     *
     * - Navigation 3 keys its per-entry `SaveableStateHolder` and
     *   `ViewModelStore` on the key, so duplicates share one store —
     *   and therefore one `ScreenStateViewModel` and one query sink.
     *   The second search would open showing whatever the first had
     *   been edited to.
     * - `SaveableStateHolder` throws outright ("Key ... was used
     *   multiple times") if two entries with one key are ever composed
     *   at once, which a transition can do.
     *
     * Reachable in one screen's worth of tapping: an entry's sentence
     * shares a word with another entry's, so search → entry A → tap 為る
     * → search → result → entry B → tap 為る lands the same
     * `SearchRoute` twice. Lifting rather than skipping keeps the tap
     * doing something, and lifting rather than popping back to the
     * older copy keeps the promise the feature is built on: back
     * returns the reader to the screen they just left.
     *
     * The section root is the exception. It is what [pop] refuses to
     * drop, what `popToRoot` lands on and what `isAtRoot` counts, so it
     * is never moved off the bottom; asking to navigate to it means
     * going back to it.
     */
    override fun navigate(route: Route) {
        val existing = backStack.indexOf(route)
        when {
            // Ordered so that an empty stack, where lastIndex is also
            // -1, still pushes rather than silently doing nothing.
            existing < 0 -> backStack.add(route)

            existing == backStack.lastIndex -> Unit

            existing == 0 -> while (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }

            else -> {
                backStack.removeAt(existing)
                backStack.add(route)
            }
        }
    }

    override fun pop(): Boolean {
        if (backStack.size <= 1) {
            return false
        }
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
