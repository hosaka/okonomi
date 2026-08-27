package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

/** The Favourites tab's root. */
@Serializable
data object FavouritesRoute : Route {
    @Composable
    override fun Content() {
        val state by produceFavouritesScreenState()
        FavouritesScreen(state)
    }
}
