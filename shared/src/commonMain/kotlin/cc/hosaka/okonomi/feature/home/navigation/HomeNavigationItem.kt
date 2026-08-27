package cc.hosaka.okonomi.feature.home.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import cc.hosaka.okonomi.feature.favourites.FavouritesRoute
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.feature.settings.SettingsRoute
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.home_favourites_label
import okonomi.shared.generated.resources.home_search_label
import okonomi.shared.generated.resources.home_settings_label
import org.jetbrains.compose.resources.StringResource

/**
 * A top-level section of the app, shown in the bottom navigation
 * bar or the navigation rail. Each item owns its own back stack
 * rooted at [route].
 */
data class HomeNavigationItem(
    val key: String,
    val route: Route,
    val icon: ImageVector,
    val iconSelected: ImageVector,
    val label: StringResource,
)

val homeSearchItem = HomeNavigationItem(
    key = "search",
    // No query: the tab's root is the empty search, which is also what
    // reselecting the tab pops back to.
    route = SearchRoute(),
    icon = Icons.Outlined.Search,
    iconSelected = Icons.Filled.Search,
    label = Res.string.home_search_label,
)

/**
 * The reader's saved entries, with its own back stack: tapping a saved
 * word pushes the entry view inside this section rather than switching
 * to Search's.
 */
val homeFavouritesItem = HomeNavigationItem(
    key = "favourites",
    route = FavouritesRoute,
    icon = Icons.Outlined.FavoriteBorder,
    iconSelected = Icons.Filled.Favorite,
    label = Res.string.home_favourites_label,
)

val homeSettingsItem = HomeNavigationItem(
    key = "settings",
    route = SettingsRoute,
    icon = Icons.Outlined.Settings,
    iconSelected = Icons.Filled.Settings,
    label = Res.string.home_settings_label,
)

// Search stays first: HomeSelectionState takes the first item as the
// default section, and system back on any other section returns to it.
val homeNavigationItems = listOf(
    homeSearchItem,
    homeFavouritesItem,
    homeSettingsItem,
)
