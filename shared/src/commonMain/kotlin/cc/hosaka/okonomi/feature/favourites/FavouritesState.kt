package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.SearchHit

@Immutable
data class FavouritesState(
    val content: FavouritesContentState = FavouritesContentState.Loading,
)

/**
 * The body of the Favourites tab.
 *
 * [Ready] with an empty list is the empty state, and it is deliberately
 * not a case of its own. Two ways to reach it — nothing saved, and
 * everything saved having been deleted from the dictionary upstream —
 * read the same to the reader, and neither is actionable beyond saving
 * something.
 *
 * [Error] is only ever a dictionary failure: the saved ids are read from
 * the user database, which reports an unreadable store as an empty list
 * rather than an error (see `FavouritesStore`). A null [Error.onRetry]
 * means retrying is not offered, following the project's "null callback
 * is a disabled action" rule.
 */
@Immutable
sealed interface FavouritesContentState {
    data object Loading : FavouritesContentState

    data class Ready(
        val hits: List<SearchHit>,
    ) : FavouritesContentState

    data class Error(
        val onRetry: (() -> Unit)? = null,
    ) : FavouritesContentState
}
