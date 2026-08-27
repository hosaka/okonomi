package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.entryRows
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.healDictionaryAfter
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.user.FavouritesStore
import cc.hosaka.okonomi.user.appFavourites
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest

@Composable
fun produceFavouritesScreenState(): State<FavouritesState> = produceScreenState(
    key = "favourites",
    initial = FavouritesState(),
) {
    favouritesScreenStateProducer()
}

/**
 * The saved ids come from the user database and the rows they render as
 * come from the dictionary, which is why this is two sources rather than
 * one query. The seam between them is where a dangling id is handled:
 * [entryRows] returns no row for an id the dictionary no longer carries,
 * and nothing here writes that absence back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun ScreenStateScope.favouritesScreenStateProducer(
    favourites: FavouritesStore = appFavourites(),
    loadRows: suspend (List<Long>) -> List<SearchHit> = { entryRows(it) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
): Flow<FavouritesState> {
    // Bumped to re-ask for rows that already failed. Deliberately not
    // persisted: a retry attempt is not state worth restoring. The ids
    // it is retried against come from the store, which is the source of
    // truth either way.
    val retries = MutableStateFlow(0)
    val onRetry: () -> Unit = { retries.value++ }
    // The rows standing on screen, so a failed reload can leave them
    // there rather than replacing a readable list with an error.
    // transformLatest runs its blocks one at a time, so no two writers
    // race for it.
    var standing: List<SearchHit>? = null
    return combine(favourites.favouriteEntryIds(), retries) { ids, _ -> ids }
        .transformLatest { ids ->
            if (ids.isEmpty()) {
                // Not a dictionary read at all: nothing saved is an
                // answer the dictionary cannot fail to give.
                standing = emptyList()
                emit(FavouritesState(FavouritesContentState.Ready(emptyList())))
                return@transformLatest
            }
            if (standing == null) {
                emit(FavouritesState(FavouritesContentState.Loading))
            }
            val next = try {
                val hits = loadRows(ids)
                standing = hits
                FavouritesContentState.Ready(hits)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failing dictionary must never take the tab down, and
                // must never be mistaken for the list being empty. What
                // the failure does to the shared handle is the one policy
                // every screen shares.
                healDictionaryAfter(e, invalidate)
                standing?.let { FavouritesContentState.Ready(it) }
                    ?: FavouritesContentState.Error(onRetry)
            }
            emit(FavouritesState(next))
        }
}
