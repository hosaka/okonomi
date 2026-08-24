package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

/** A dictionary entry pushed from a search result row. */
@Serializable
data class EntryRoute(
    val entryId: Long,
) : Route {
    @Composable
    override fun Content() {
        val state by produceEntryScreenState(entryId)
        EntryScreen(state)
    }
}
