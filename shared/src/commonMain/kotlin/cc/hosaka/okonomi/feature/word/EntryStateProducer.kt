package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.loadEntryDetail
import cc.hosaka.okonomi.feature.navigation.state.LoadState
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.loadOnce
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.user.FavouritesStore
import cc.hosaka.okonomi.user.appFavourites
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

@Composable
fun produceEntryScreenState(entryId: Long): State<EntryState> = produceScreenState(
    // One screen state per entry: two entries on the same back stack
    // must not share a view model, or the second would show the first.
    key = "entry-$entryId",
    initial = EntryState(entryId),
) {
    entryScreenStateProducer(entryId)
}

suspend fun ScreenStateScope.entryScreenStateProducer(
    entryId: Long,
    load: suspend (Long) -> EntryDetail? = { loadEntryDetail(it) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
    favourites: FavouritesStore = appFavourites(),
): Flow<EntryState> {
    // One instance rather than one per emission, so two otherwise equal
    // states stay equal. Writing goes straight to the store and the
    // state comes back through [saved]; nothing here holds a second copy
    // of the answer.
    val onToggleFavourite: () -> Unit = { favourites.toggleFavourite(entryId) }
    // Unsaved is emitted up front for the reason the search producer
    // seeds its Names toggle: combine waits for every source, and this
    // one opens a database file. Without a value in hand the entry
    // would stay on its initial state — spinner included — until
    // storage answered, so a slow or wedged store would hold up the
    // word rather than the button.
    val saved = favourites.isFavourite(entryId)
        .onStart { emit(false) }
        .distinctUntilChanged()
    val content = loadOnce(
        key = "entry",
        // The entry never changes under us, so a loaded entry is kept for
        // the life of the screen; an id no entry carries is the same error
        // state as a failed query.
        load = { load(entryId) },
        invalidate = invalidate,
    )
    return combine(content, saved) { state, isFavourite ->
        EntryState(
            entryId = entryId,
            content = when (state) {
                LoadState.Loading -> EntryContentState.Loading
                is LoadState.Ready -> EntryContentState.Ready(state.value)
                is LoadState.Error -> EntryContentState.Error(state.onRetry)
            },
            isFavourite = isFavourite,
            // Only an entry that loaded can be saved: the button is not
            // drawn over a spinner or an error, and a null callback is
            // how this project says an action is unavailable.
            onToggleFavourite = onToggleFavourite.takeIf { state is LoadState.Ready },
        )
    }
}
