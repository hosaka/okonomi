package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The search screen, both as the Search tab's own root and as a screen
 * pushed above an entry.
 *
 * [query] is what the field opens with: null for the tab's root, and
 * the tapped word for a search opened from a sentence breakdown. It is
 * part of the route rather than a channel between screens because each
 * back-stack entry gets its own scoped state producer — a route with a
 * different query is a different key, so the pushed screen seeds its
 * own query sink and the root search underneath keeps whatever the
 * reader last typed there.
 */
@Serializable
data class SearchRoute(
    val query: String? = null,
) : Route {
    @Composable
    override fun Content() {
        val state = produceSearchScreenState(query)
        SearchScreen(
            state = state.value,
        )
    }
}
