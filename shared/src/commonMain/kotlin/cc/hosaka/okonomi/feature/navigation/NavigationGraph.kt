package cc.hosaka.okonomi.feature.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import cc.hosaka.okonomi.feature.favourites.FavouritesRoute
import cc.hosaka.okonomi.feature.libraries.LibrariesRoute
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.feature.settings.SettingsRoute
import cc.hosaka.okonomi.feature.word.EntryRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Saved state configuration that knows how to (de)serialize every
 * route of the app. Register new routes here so that the back stack
 * survives process death.
 */
val navigationSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(SearchRoute::class)
            subclass(FavouritesRoute::class)
            subclass(SettingsRoute::class)
            subclass(LibrariesRoute::class)
            subclass(EntryRoute::class)
        }
    }
}

/**
 * Renders any [Route] by delegating to its own [Route.Content].
 */
val routeEntryProvider: (NavKey) -> NavEntry<NavKey> = { key ->
    NavEntry(key) {
        checkNotNull(key as? Route) { "Unsupported NavKey: $key" }.Content()
    }
}
