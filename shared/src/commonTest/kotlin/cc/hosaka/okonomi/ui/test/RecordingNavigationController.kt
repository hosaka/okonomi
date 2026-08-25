package cc.hosaka.okonomi.ui.test

import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.Route

/**
 * Records forward navigation performed from inside a composable.
 *
 * [cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope] covers the
 * producer seam; this is its counterpart for navigation that lives in the UI
 * layer, provided through `LocalNavigationController`.
 */
internal class RecordingNavigationController : NavigationController {
    val navigated = mutableListOf<Route>()

    var pops = 0
        private set

    override fun navigate(route: Route) {
        navigated += route
    }

    override fun pop(): Boolean {
        pops += 1
        return true
    }
}
