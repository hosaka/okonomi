package cc.hosaka.okonomi.feature.phrases

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.loadSentencesForEntry
import cc.hosaka.okonomi.feature.navigation.state.LoadState
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.loadOnce
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
fun producePhrasesTabState(
    entryId: Long,
): State<PhrasesTabState> = produceScreenState(
    // Keyed per entry beside the entry's own screen state, so two
    // entries on the same back stack cannot share one tab's sentences.
    key = "entry-phrases-$entryId",
    initial = PhrasesTabState(),
) {
    phrasesTabStateProducer(entryId)
}

/**
 * Unlike the Forms tab there is genuinely something to wait for here, so
 * the tab owns a real Loading and Error pair. A sentence failure costs
 * this tab alone: the entry view and its other tabs stand.
 */
suspend fun ScreenStateScope.phrasesTabStateProducer(
    entryId: Long,
    load: suspend (Long) -> List<ExampleSentence> = { loadSentencesForEntry(it) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
): Flow<PhrasesTabState> = loadOnce(
    key = "phrases",
    load = { load(entryId) },
    invalidate = invalidate,
).map { state ->
    PhrasesTabState(
        content = when (state) {
            LoadState.Loading -> PhrasesTabContentState.Loading

            // An entry the corpus never uses loads successfully with
            // nothing in it, which is the common case and its own state
            // rather than an error or an empty list on screen.
            is LoadState.Ready -> if (state.value.isEmpty()) {
                PhrasesTabContentState.Empty
            } else {
                PhrasesTabContentState.Ready(state.value)
            }

            is LoadState.Error -> PhrasesTabContentState.Error(state.onRetry)
        },
    )
}
