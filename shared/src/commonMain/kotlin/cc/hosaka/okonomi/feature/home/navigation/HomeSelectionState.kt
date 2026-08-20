package cc.hosaka.okonomi.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Which top-level section is shown and how system back moves
 * between them: back on a non-default section returns to the
 * default one, back on the default section is left to the platform.
 */
@Stable
class HomeSelectionState(
    val items: List<HomeNavigationItem>,
    val default: HomeNavigationItem = items.first(),
    initialKey: String = default.key,
) {
    var selectedKey: String by mutableStateOf(initialKey)
        private set

    val selected: HomeNavigationItem
        get() = items.firstOrNull { it.key == selectedKey } ?: default

    val isBackEnabled: Boolean
        get() = selected.key != default.key

    fun select(item: HomeNavigationItem) {
        selectedKey = item.key
    }

    fun onBack(): Boolean {
        if (!isBackEnabled) {
            return false
        }
        selectedKey = default.key
        return true
    }

    companion object {
        fun saver(
            items: List<HomeNavigationItem>,
            default: HomeNavigationItem,
        ): Saver<HomeSelectionState, String> = Saver(
            save = { it.selectedKey },
            restore = { key ->
                HomeSelectionState(
                    items = items,
                    default = default,
                    initialKey = key,
                )
            },
        )
    }
}

@Composable
fun rememberHomeSelectionState(
    items: List<HomeNavigationItem>,
    default: HomeNavigationItem = items.first(),
): HomeSelectionState = rememberSaveable(
    items,
    default,
    saver = HomeSelectionState.saver(items, default),
) {
    HomeSelectionState(
        items = items,
        default = default,
    )
}
