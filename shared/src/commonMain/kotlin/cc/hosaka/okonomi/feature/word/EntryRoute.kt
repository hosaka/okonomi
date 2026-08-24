package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

/**
 * A dictionary entry pushed from a search result row. Stub for now:
 * the Word increment fills in the actual entry view.
 */
@Serializable
data class EntryRoute(
    val entryId: Long,
) : Route {
    @Composable
    override fun Content() {
        EntryScreen(
            entryId = entryId,
        )
    }
}
