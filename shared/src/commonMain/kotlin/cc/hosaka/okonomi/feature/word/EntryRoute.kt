package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A dictionary entry, pushed from a result row or opened by an
 * `okonomi://entry/<id>` link.
 *
 * @property openedFromLink true for the entry a link opened. System back
 * on it leaves okonomi for the app that sent the link rather than
 * stopping on Search; see `HomeScreen`'s link handling. It belongs to
 * that one hand-off only: `@Transient`, so a route restored from saved
 * state is an ordinary entry, and the shell clears it once the reader
 * switches tabs. Part of the key while it lasts, so the same entry
 * opened both ways is two screens, not one.
 */
@Serializable
data class EntryRoute(
    val entryId: Long,
    @Transient val openedFromLink: Boolean = false,
) : Route {
    @Composable
    override fun Content() {
        val state by produceEntryScreenState(entryId)
        EntryScreen(state)
    }
}
