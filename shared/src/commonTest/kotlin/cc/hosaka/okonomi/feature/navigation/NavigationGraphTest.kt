package cc.hosaka.okonomi.feature.navigation

import androidx.navigation3.runtime.NavKey
import cc.hosaka.okonomi.feature.home.navigation.homeNavigationItems
import kotlin.test.Test
import kotlin.test.assertNotNull

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
}
