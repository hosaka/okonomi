package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.EntryDetail

@Immutable
data class EntryState(
    val entryId: Long,
    val content: EntryContentState = EntryContentState.Loading,
    /**
     * Whether this entry is in the reader's Favourites list. Read back
     * from storage rather than held optimistically, so a write that
     * could not land is seen to refuse rather than seen to lie; see
     * `FavouritesStore`.
     */
    val isFavourite: Boolean = false,
    /**
     * Saves the entry, or unsaves it if it is already saved. Takes no
     * argument on purpose: which way the toggle goes is decided against
     * storage when the write runs, not against [isFavourite] as the
     * button saw it — see `FavouritesStore.toggleFavourite` for the two
     * taps that go wrong when the caller decides.
     *
     * Null means the action is not offered, following the project's
     * "null callback is a disabled action" rule — which is what the
     * loading and error bodies get, since there is no entry there to
     * save.
     */
    val onToggleFavourite: (() -> Unit)? = null,
)

/**
 * The body of the entry view. [Error] covers both a load failure and an
 * id no entry carries: neither is actionable for the reader beyond
 * trying again, and both must leave the screen (and its back
 * affordance) standing. A null [Error.onRetry] means retrying is not
 * offered, following the project's "null callback is a disabled
 * action" rule.
 */
@Immutable
sealed interface EntryContentState {
    data object Loading : EntryContentState

    data class Ready(
        val entry: EntryDetail,
    ) : EntryContentState

    data class Error(
        val onRetry: (() -> Unit)? = null,
    ) : EntryContentState
}
