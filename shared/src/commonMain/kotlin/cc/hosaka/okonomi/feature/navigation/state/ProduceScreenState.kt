package cc.hosaka.okonomi.feature.navigation.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Scope available to a screen state producer.
 */
interface ScreenStateScope {
    val navigation: NavigationController

    /**
     * Returns a flow that keeps its value for as long as the screen
     * lives on the back stack, in memory only. Calling it again with
     * the same [key] returns the same flow, also across restarts of
     * the producer, so anything that must survive between runs of
     * the producer belongs here.
     */
    fun <T> mutablePersistedFlow(
        key: String,
        initial: T,
    ): MutableStateFlow<T>
}

/**
 * Produces the state of a screen. The resulting flow is shared by a
 * view model that is scoped to the current back stack entry, so the
 * state survives recompositions and configuration changes. The
 * upstream stops while the screen has been off-screen for a while,
 * so [init] may be run again when the screen is shown again; keep
 * anything that must survive between runs in
 * [ScreenStateScope.mutablePersistedFlow].
 */
@Composable
fun <T> produceScreenState(
    key: String,
    initial: T,
    init: suspend ScreenStateScope.() -> Flow<T>,
): State<T> {
    val navigation = LocalNavigationController.current
    val viewModel = viewModel(key = key) {
        ScreenStateViewModel(initial = initial, init = init)
    }
    SideEffect {
        viewModel.hostNavigation = navigation
    }
    return viewModel.state.collectAsStateWithLifecycle()
}

@Suppress("UNCHECKED_CAST")
internal class ScreenStateViewModel<T>(
    initial: T,
    init: suspend ScreenStateScope.() -> Flow<T>,
) : ViewModel(), ScreenStateScope {
    private val persisted = mutableMapOf<String, MutableStateFlow<*>>()

    /**
     * The latest controller that hosts this screen. Updated on every
     * composition so that the producer never talks to a stale one.
     */
    var hostNavigation: NavigationController? = null

    override val navigation: NavigationController = object : NavigationController {
        override fun navigate(route: Route) {
            hostNavigation?.navigate(route)
        }

        override fun pop(): Boolean = hostNavigation?.pop() ?: false
    }

    val state: StateFlow<T> = flow { emitAll(init()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initial,
        )

    override fun <T> mutablePersistedFlow(
        key: String,
        initial: T,
    ): MutableStateFlow<T> = persisted
        .getOrPut(key) { MutableStateFlow(initial) } as MutableStateFlow<T>
}
