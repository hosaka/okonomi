package cc.hosaka.okonomi.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cc.hosaka.okonomi.feature.home.navigation.HomeNavigationItem
import cc.hosaka.okonomi.feature.home.navigation.HomeSelectAction
import cc.hosaka.okonomi.feature.home.navigation.LocalHomeReselect
import cc.hosaka.okonomi.feature.home.navigation.homeNavigationItems
import cc.hosaka.okonomi.feature.home.navigation.homeSelectAction
import cc.hosaka.okonomi.feature.home.navigation.popToRootCount
import cc.hosaka.okonomi.feature.home.navigation.rememberHomeSelectionState
import cc.hosaka.okonomi.feature.navigation.BackStackNavigationController
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.navigationSavedStateConfiguration
import cc.hosaka.okonomi.feature.navigation.routeEntryProvider
import org.jetbrains.compose.resources.stringResource

/**
 * The shell of the app: a navigation bar (portrait) or a navigation
 * rail (landscape) that switches between the top-level sections,
 * each of which keeps its own back stack.
 */
@Composable
fun HomeScreen(
    items: List<HomeNavigationItem> = homeNavigationItems,
) {
    require(items.isNotEmpty()) { "Home needs at least one section" }
    require(items.distinctBy { it.key }.size == items.size) { "Home section keys must be unique" }
    // Decorators are shared by all sections; the entries of every
    // section stay remembered even while another section is shown,
    // so switching tabs never counts as popping an entry and the
    // per-entry saved state and view models are kept.
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>(),
    )
    val sections = items.map { item ->
        key(item.key) {
            rememberHomeSection(
                item = item,
                entryDecorators = entryDecorators,
            )
        }
    }
    val selection = rememberHomeSelectionState(items)
    val selectedItem = selection.selected
    val selectedSection = sections.first { it.item.key == selectedItem.key }
    // The shell owns both the selection and the per-section back
    // stacks, so it is the only place that can tell a "go back to the
    // top of this section" tap from a "focus the section" tap.
    val onSelect = { item: HomeNavigationItem ->
        val section = sections.first { it.item.key == item.key }
        when (
            homeSelectAction(
                tappedKey = item.key,
                selectedKey = selection.selectedKey,
                depth = section.depth,
            )
        ) {
            HomeSelectAction.Switch -> selection.switchTo(item)
            HomeSelectAction.SignalReselect -> selection.signalReselect(item)
            HomeSelectAction.PopToRoot -> section.popToRoot()
        }
    }

    // System back on a section root returns to the default section.
    // Registered before the NavDisplay so that the display's own
    // handler, which pops within a section, takes priority while its
    // back stack is deeper than the root.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selection.isBackEnabled,
        onBackCompleted = {
            selection.onBack()
        },
    )

    ResponsiveLayout {
        val horizontalInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Start)
        Row(
            modifier = Modifier
                .windowInsetsPadding(horizontalInsets),
        ) {
            val layout = LocalHomeLayout.current
            if (layout is HomeLayout.Horizontal) {
                HomeNavigationRail(
                    items = items,
                    selectedItem = selectedItem,
                    onSelect = onSelect,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val bottomInsets = WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom)
                HomeNavigationContent(
                    section = selectedSection,
                    reselectCount = selection.reselectionsOf(selectedItem.key),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (layout is HomeLayout.Vertical) {
                                // The bottom navigation bar sits below the
                                // content and handles these insets itself.
                                Modifier.consumeWindowInsets(bottomInsets)
                            } else {
                                Modifier
                            },
                        ),
                )
                if (layout is HomeLayout.Vertical) {
                    HomeNavigationBar(
                        items = items,
                        selectedItem = selectedItem,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

/**
 * A top-level section with its own back stack, navigation
 * controller and decorated entries.
 */
private class HomeSection(
    val item: HomeNavigationItem,
    val backStack: NavBackStack<NavKey>,
    val controller: NavigationController,
    val entries: List<NavEntry<NavKey>>,
) {
    val depth: Int
        get() = backStack.size

    /** Drops every pushed screen, leaving the section's root route. */
    fun popToRoot() {
        repeat(popToRootCount(depth)) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}

@Composable
private fun rememberHomeSection(
    item: HomeNavigationItem,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
): HomeSection {
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, item.route)
    val controller = remember(backStack) {
        BackStackNavigationController(backStack)
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = entryDecorators,
        entryProvider = routeEntryProvider,
    )
    return remember(item, backStack, controller, entries) {
        HomeSection(
            item = item,
            backStack = backStack,
            controller = controller,
            entries = entries,
        )
    }
}

@Composable
private fun HomeNavigationContent(
    section: HomeSection,
    reselectCount: Int,
    modifier: Modifier = Modifier,
) {
    // Each section gets its own display, so switching tabs is a
    // plain swap rather than a transition between unrelated stacks.
    key(section.item.key) {
        CompositionLocalProvider(
            LocalNavigationController provides section.controller,
            LocalHomeReselect provides reselectCount,
        ) {
            NavDisplay(
                entries = section.entries,
                modifier = modifier
                    .fillMaxSize(),
                onBack = {
                    section.controller.pop()
                },
            )
        }
    }
}

@Composable
private fun HomeNavigationRail(
    items: List<HomeNavigationItem>,
    selectedItem: HomeNavigationItem,
    onSelect: (HomeNavigationItem) -> Unit,
) {
    val verticalInsets = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Vertical)
    WideNavigationRail(
        modifier = Modifier
            .fillMaxHeight(),
        windowInsets = verticalInsets,
    ) {
        items.forEach { item ->
            key(item.key) {
                val selected = item.key == selectedItem.key
                WideNavigationRailItem(
                    icon = {
                        NavigationIcon(
                            selected = selected,
                            icon = item.icon,
                            iconSelected = item.iconSelected,
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(item.label),
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    },
                    selected = selected,
                    railExpanded = false,
                    onClick = {
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeNavigationBar(
    items: List<HomeNavigationItem>,
    selectedItem: HomeNavigationItem,
    onSelect: (HomeNavigationItem) -> Unit,
) {
    ShortNavigationBar {
        items.forEach { item ->
            key(item.key) {
                val selected = item.key == selectedItem.key
                ShortNavigationBarItem(
                    icon = {
                        NavigationIcon(
                            selected = selected,
                            icon = item.icon,
                            iconSelected = item.iconSelected,
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(item.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            // Default style does not fit on devices with small
                            // screens.
                            fontSize = 10.sp,
                        )
                    },
                    selected = selected,
                    onClick = {
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    selected: Boolean,
    icon: ImageVector,
    iconSelected: ImageVector,
) {
    Crossfade(targetState = selected) {
        val vector = if (it) {
            iconSelected
        } else {
            icon
        }
        Icon(vector, null)
    }
}
