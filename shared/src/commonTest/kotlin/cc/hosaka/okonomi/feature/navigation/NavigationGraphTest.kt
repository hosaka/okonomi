package cc.hosaka.okonomi.feature.navigation

import androidx.navigation3.runtime.NavKey
import cc.hosaka.okonomi.feature.home.navigation.homeNavigationItems
import cc.hosaka.okonomi.feature.libraries.LibrariesRoute
import cc.hosaka.okonomi.feature.radical.RadicalRoute
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.feature.word.EntryRoute
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.serialization.ExperimentalSerializationApi

// SerializersModule.getPolymorphic, the only way to ask what the module
// actually registered, is still experimental.
@OptIn(ExperimentalSerializationApi::class)
class NavigationGraphTest {
    @Test
    fun `every home route is registered for saved state`() {
        homeNavigationItems.forEach { item ->
            assertNotNull(
                navigationSavedStateConfiguration.serializersModule
                    .getPolymorphic(NavKey::class, item.route),
                "${item.route} is missing from navigationSavedStateConfiguration",
            )
        }
    }

    @Test
    fun `every pushed detail route is registered for saved state`() {
        // Hand-maintained: every new pushed detail route MUST be added
        // to this list when it is registered in the navigation graph.
        listOf(
            LibrariesRoute,
            EntryRoute(entryId = 1),
            // Search is both a section root and a pushed screen: a
            // breakdown word taps into one carrying its own query.
            SearchRoute(query = "為る"),
            // Only ever pushed, and only from a radical chip.
            RadicalRoute(radical = "口"),
        ).forEach { route ->
            assertNotNull(
                navigationSavedStateConfiguration.serializersModule
                    .getPolymorphic(NavKey::class, route),
                "$route is missing from navigationSavedStateConfiguration",
            )
        }
    }
}
