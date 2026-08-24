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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
): Flow<EntryState> = loadOnce(
    key = "entry",
    // The entry never changes under us, so a loaded entry is kept for
    // the life of the screen; an id no entry carries is the same error
    // state as a failed query.
    load = { load(entryId) },
    invalidate = invalidate,
).map { state ->
    EntryState(
        entryId = entryId,
        content = when (state) {
            LoadState.Loading -> EntryContentState.Loading
            is LoadState.Ready -> EntryContentState.Ready(state.value)
            is LoadState.Error -> EntryContentState.Error(state.onRetry)
        },
    )
}
