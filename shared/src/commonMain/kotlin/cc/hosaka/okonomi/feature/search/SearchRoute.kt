package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * The search screen, both as the Search tab's own root and as a screen
 * pushed above an entry or, for a text lookup, above that root.
 *
 * [query] is what the field opens with: null for the tab's root, the
 * tapped word for a search opened from a sentence breakdown, and the
 * selected text for a text lookup. It is part of the route
 * rather than a channel between screens because each back-stack entry
 * gets its own scoped state producer — a route with a different query
 * is a different key, so the pushed screen seeds its own query sink and
 * the root search underneath keeps whatever the reader last typed there.
 *
 * @property openedFromSelection true for the search a text selection in
 * another app opened (the "Okonomi" item in Android's selection
 * toolbar). System back on it leaves okonomi for that app rather than
 * stopping on Search's root; see `HomeScreen`'s hand-off handling. It
 * belongs to that one hand-off only: `@Transient`, so a route restored
 * from saved state is an ordinary search, and the shell clears it once
 * the reader switches tabs. Part of the key while it lasts, so the same
 * text searched both ways is two screens, not one. Text selected in
 * okonomi itself is never marked: its search is an ordinary pushed one.
 */
@Serializable
data class SearchRoute(
    val query: String? = null,
    @Transient val openedFromSelection: Boolean = false,
) : Route {
    @Composable
    override fun Content() {
        val state = produceSearchScreenState(query)
        SearchScreen(
            state = state.value,
        )
    }
}
