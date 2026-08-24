package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.SearchResults
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.searchEntries
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

internal val searchDebounce = 200.milliseconds

@Composable
fun produceSearchScreenState(): State<SearchState> = produceScreenState(
    key = "search",
    initial = SearchState(),
) {
    searchScreenStateProducer()
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun ScreenStateScope.searchScreenStateProducer(
    search: suspend (String) -> SearchResults = { searchEntries(it) },
): Flow<SearchState> {
    val querySink = mutablePersistedFlow(
        key = "query",
        initial = "",
    )
    // transformLatest gives the debounce its latest-wins behavior: a
    // newer query cancels the delay and the in-flight search, so stale
    // results can never overwrite newer ones. While a search is in
    // flight the previous results state simply stays current (no
    // Loading emission), so refinement never blanks the visible hits.
    val results = querySink
        .transformLatest { query ->
            if (query.isBlank()) {
                emit(SearchResultsState.Idle)
            } else {
                delay(searchDebounce)
                emit(runSearch(search, query))
            }
        }
        // combine waits for every source, so without a value up front a
        // restored non-blank query would leave the whole state flow
        // silent for the length of the debounce — the screen would show
        // its initial (empty) state, query field included.
        .onStart { emit(SearchResultsState.Idle) }
    return combine(querySink, results) { query, resultsState ->
        SearchState(
            query = query,
            onQueryChange = { querySink.value = it },
            onClear = { querySink.value = "" }.takeIf { query.isNotEmpty() },
            // Idle under a non-blank query means no search has landed
            // for this screen yet — the debounce window, or a query
            // restored into a fresh screen, which would otherwise show
            // an empty results area with no explanation. Results from
            // an older query are deliberately left standing instead.
            results = if (resultsState is SearchResultsState.Idle && query.isNotBlank()) {
                SearchResultsState.Searching(query)
            } else {
                resultsState
            },
        )
    }
}

private suspend fun runSearch(
    search: suspend (String) -> SearchResults,
    query: String,
): SearchResultsState = try {
    val results = search(query)
    SearchResultsState.Results(
        query = query,
        hits = results.hits,
        isFallback = results.isFallback,
        glossTokens = results.glossTokens,
    )
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // A failing search must never take the screen down; the next
    // query change retries. A database failure also drops the shared
    // handle so that retry reopens it — a cheap heal for a handle
    // gone bad. Wiping the provisioned file is deliberately left to
    // Settings' corrupt-copy path.
    //
    // Programming errors are not database failures: reopening cannot
    // fix a bad argument or a broken invariant, and throwing the
    // handle away would turn one bug into a reprovisioning storm.
    if (e !is IllegalArgumentException && e !is IllegalStateException) {
        runCatching { invalidateDictionary() }
    }
    SearchResultsState.Error(query)
}
