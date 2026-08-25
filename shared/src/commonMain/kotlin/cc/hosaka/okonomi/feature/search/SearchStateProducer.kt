package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.SEARCH_MAX_RESULTS
import cc.hosaka.okonomi.db.SEARCH_RESULT_LIMIT
import cc.hosaka.okonomi.db.SearchResults
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.searchEntries
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.healDictionaryAfter
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.ui.PagingFooterState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    search: suspend (String, Int) -> SearchResults = { query, limit -> searchEntries(query, limit) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
): Flow<SearchState> {
    val querySink = mutablePersistedFlow(
        key = "query",
        initial = "",
    )
    // How many hits the current query is asking for. Paging is a larger
    // limit on the same search rather than an offset into a previous
    // one: the ranking is deterministic, so page two is the first
    // hundred of the same order and the fifty rows already on screen
    // cannot move. An offset would have to trust that the pool it was
    // computed against had not changed under it.
    val limitSink = mutablePersistedFlow(
        key = "limit",
        initial = SEARCH_RESULT_LIMIT,
    )
    // Bumped only to re-ask for a limit that already failed. Deliberately
    // not persisted: a retry attempt is not state worth restoring.
    //
    // Without a second thing to change, a failed extension killed paging
    // outright. The limit that failed is already the one in limitSink, so
    // the standing result's "show more" wrote it again, StateFlow
    // conflated the identical value away, and no search ever ran — for
    // the rest of that query. LazyListPaging promises the opposite:
    // scrolling to the end again is how a failed extension gets retried.
    val retrySink = MutableStateFlow(0)
    // The rows standing on screen, so a failed extension can leave them
    // there. Only the producer writes it, and transformLatest runs its
    // blocks one at a time, so no two writers race for it.
    var standing: SearchResultsState.Results? = null
    // transformLatest gives the debounce its latest-wins behavior: a
    // newer query cancels the delay and the in-flight search, so stale
    // results can never overwrite newer ones. While a search is in
    // flight the previous results state simply stays current (no
    // Loading emission), so refinement never blanks the visible hits.
    val results = combine(querySink, limitSink, retrySink) { query, limit, _ -> query to limit }
        .transformLatest { (query, limit) ->
            if (query.isBlank()) {
                standing = null
                emit(SearchResultsState.Idle)
            } else {
                // Only the first page waits out the debounce. An
                // extension comes from a scroll that has already
                // happened, not from a keystroke that another one may
                // supersede, so making the reader wait for it would be
                // latency with nothing to gain by it.
                val onScreen = standing?.takeIf { it.query == query }
                if (limit <= SEARCH_RESULT_LIMIT) {
                    delay(searchDebounce)
                } else if (onScreen != null) {
                    // An extension: the rows stay, with a footer saying
                    // a page is on its way. Without this the reader
                    // could not tell a page being fetched from the end
                    // of the results. onShowMore goes null for the
                    // duration — the page it would ask for is the one
                    // already coming.
                    emit(onScreen.copy(onShowMore = null, footer = PagingFooterState.Loading))
                }
                val outcome = runSearch(search, invalidate, query, limit, limitSink)
                // A failed extension must not take the rows the reader
                // is already reading off the screen — but it must say
                // that the page failed, and the rows put back have to
                // stay pageable, or the failure would end paging for
                // the rest of this query. Both the footer's retry and
                // the scroll watcher's "show more" re-ask for the limit
                // that just failed (already the one in limitSink) by
                // bumping the retry counter, which is what makes the
                // flow emit at all. Scrolling to the end again then
                // runs the search again, exactly as LazyListPaging says
                // it should.
                val next = if (outcome is SearchResultsState.Error && onScreen != null) {
                    val retry: () -> Unit = { retrySink.value++ }
                    onScreen.copy(
                        onShowMore = onScreen.onShowMore?.let { retry },
                        footer = PagingFooterState.Failed(onRetry = retry),
                    )
                } else {
                    outcome
                }
                // Only a real result becomes the standing one: a footer
                // is a passing condition of the rows, never part of
                // what the next failure should put back.
                if (outcome is SearchResultsState.Results) {
                    standing = outcome
                }
                emit(next)
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
            onQueryChange = { value ->
                // The limit falls back to one page before the query
                // moves, so a refined query is never asked for at the
                // depth the previous one had been scrolled to.
                limitSink.value = SEARCH_RESULT_LIMIT
                querySink.value = value
            },
            onClear = {
                limitSink.value = SEARCH_RESULT_LIMIT
                querySink.value = ""
            }.takeIf { query.isNotEmpty() },
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
    search: suspend (String, Int) -> SearchResults,
    invalidate: suspend () -> Unit,
    query: String,
    limit: Int,
    limitSink: MutableStateFlow<Int>,
): SearchResultsState = try {
    val results = search(query, limit)
    SearchResultsState.Results(
        query = query,
        hits = results.hits,
        isFallback = results.isFallback,
        glossTokens = results.glossTokens,
        // Two conditions rather than one: a match set larger than the
        // ranking pool leaves hasMore true at the very point paging has
        // to stop. See SearchResults.hasMore and SEARCH_MAX_RESULTS.
        //
        // The next limit is computed from the one this search ran at,
        // not incremented, so the scroll watcher calling it twice before
        // the page lands asks for one page rather than two.
        onShowMore = { limitSink.value = limit + SEARCH_RESULT_LIMIT }
            .takeIf { results.hasMore && limit < SEARCH_MAX_RESULTS },
    )
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // A failing search must never take the screen down; the next query
    // change retries. What the failure does to the shared handle is the
    // one policy every screen shares.
    healDictionaryAfter(e, invalidate)
    SearchResultsState.Error(query)
}
