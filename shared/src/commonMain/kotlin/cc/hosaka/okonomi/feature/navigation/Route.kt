package cc.hosaka.okonomi.feature.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey

/**
 * A self-rendering navigation destination. Every route is a
 * serializable key on the Navigation 3 back stack and knows
 * how to render its own content.
 */
interface Route : NavKey {
    @Composable
    fun Content()
}
