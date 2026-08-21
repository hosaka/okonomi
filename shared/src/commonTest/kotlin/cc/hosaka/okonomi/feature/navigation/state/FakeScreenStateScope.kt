package cc.hosaka.okonomi.feature.navigation.state

import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test double for [ScreenStateScope]: persisted flows are kept per key
 * for the lifetime of the instance and navigation calls are recorded.
 */
internal class FakeScreenStateScope : ScreenStateScope {
    private val persisted = mutableMapOf<String, MutableStateFlow<*>>()

    val navigated = mutableListOf<Route>()

    override val navigation: NavigationController = object : NavigationController {
        override fun navigate(route: Route) {
            navigated += route
        }

        override fun pop(): Boolean = false
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> mutablePersistedFlow(
        key: String,
        initial: T,
    ): MutableStateFlow<T> = persisted
        .getOrPut(key) { MutableStateFlow(initial) } as MutableStateFlow<T>
}
