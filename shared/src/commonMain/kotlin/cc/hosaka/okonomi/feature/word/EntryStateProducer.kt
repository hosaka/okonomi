package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.loadEntryDetail
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
): Flow<EntryState> {
    val contentSink = mutablePersistedFlow<EntryContentState>(
        key = "content",
        initial = EntryContentState.Loading,
    )
    // Persisted so a retry survives the producer restarting under it,
    // and so a screen that failed while off screen loads again when the
    // reader comes back to it.
    val attempts = mutablePersistedFlow(
        key = "attempts",
        initial = 0,
    )
    return flow {
        coroutineScope {
            // The load runs beside the emission rather than before it, so
            // a retry shows its spinner immediately instead of leaving
            // the old error on screen until the query returns.
            launch {
                attempts.collect {
                    // The entry never changes under us, so a loaded entry
                    // is kept for the life of the screen and coming back
                    // to it is instant.
                    if (contentSink.value !is EntryContentState.Ready) {
                        contentSink.value = EntryContentState.Loading
                        contentSink.value = loadContent(load, invalidate, entryId)
                    }
                }
            }
            emitAll(contentSink)
        }
    }.map { content ->
        EntryState(
            entryId = entryId,
            content = withRetry(content, attempts),
        )
    }
}

private fun withRetry(
    content: EntryContentState,
    attempts: MutableStateFlow<Int>,
): EntryContentState = if (content is EntryContentState.Error) {
    EntryContentState.Error(onRetry = { attempts.value += 1 })
} else {
    content
}

private suspend fun loadContent(
    load: suspend (Long) -> EntryDetail?,
    invalidate: suspend () -> Unit,
    entryId: Long,
): EntryContentState = try {
    load(entryId)?.let { EntryContentState.Ready(it) } ?: EntryContentState.Error()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Same rule as the search producer: a database failure drops the
    // shared handle so the next attempt reopens it, while a programming
    // error must not turn into a reprovisioning storm.
    if (e !is IllegalArgumentException && e !is IllegalStateException) {
        runCatching { invalidate() }
    }
    EntryContentState.Error()
}
