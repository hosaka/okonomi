package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.NameHit
import cc.hosaka.okonomi.db.NameResults
import cc.hosaka.okonomi.db.SEARCH_MAX_RESULTS
import cc.hosaka.okonomi.db.SEARCH_RESULT_LIMIT
import cc.hosaka.okonomi.db.SearchResults
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.searchEntries
import cc.hosaka.okonomi.db.searchNames
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.healDictionaryAfter
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.prefs.appPreferences
import cc.hosaka.okonomi.ui.PagingFooterState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

internal val searchDebounce = 200.milliseconds

/**
 * Persisted key of the Names toggle. Namespaced by screen because this
 * is the first thing the app ever stored, and the store is one file that
 * every later setting shares.
 */
const val NAMES_IN_SEARCH_PREFERENCE = "search.names_enabled"

/**
 * Names are off until asked for (Alex's ruling), so a fresh install
 * searches exactly as it did before names existed — in what it returns
 * and in what it costs.
 */
const val NAMES_IN_SEARCH_DEFAULT = false

/**
 * [initialQuery] is the route's query (see `SearchRoute`): the word a
 * sentence breakdown was tapped on, or null for the Search tab's root.
 * It seeds the state the screen is first shown with as well as the
 * query sink, so a pushed search draws its field already filled rather
 * than blank for a frame.
 */
@Composable
fun produceSearchScreenState(initialQuery: String? = null): State<SearchState> = produceScreenState(
    key = "search",
    initial = SearchState(query = initialQuery.orEmpty()),
) {
    searchScreenStateProducer(initialQuery = initialQuery)
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun ScreenStateScope.searchScreenStateProducer(
    initialQuery: String? = null,
    search: suspend (String, Int) -> SearchResults = { query, limit -> searchEntries(query, limit) },
    /**
     * Called only while the Names toggle is on. Not "called and its
     * result discarded": search runs on every keystroke and a common
     * reading matches thousands of names, so the toggle's whole purpose
     * is that the query is never issued. `SearchStateProducerTest` hands
     * in a lambda that throws to keep it that way.
     */
    nameSearch: suspend (String, Int, Int) -> NameResults = { query, offset, limit ->
        searchNames(query, offset, limit)
    },
    preferences: PreferenceStore = appPreferences(),
    invalidate: suspend () -> Unit = { invalidateDictionary() },
): Flow<SearchState> {
    // Only the *initial* value: mutablePersistedFlow hands back the
    // same flow on every later run of the producer, so a reader who
    // edited the seeded query keeps their edit when the screen comes
    // back rather than having the tapped word pushed at them again.
    //
    // Nothing here takes focus. The field is filled and the search
    // runs, but the keyboard stays down so the results the tap asked
    // for are the first thing on screen; SearchFieldFocusEffect only
    // focuses on a tab reselect, which a pushed screen never sees.
    val querySink = mutablePersistedFlow(
        key = "query",
        initial = initialQuery.orEmpty(),
    )
    // A route with no query is a section root — that is how
    // `homeSearchItem` defines the Search tab, and a breakdown tap is
    // the only thing that ever pushes a SearchRoute, always with the
    // word in it. One instance rather than one per emission, so two
    // otherwise equal states stay equal.
    val onBack: (() -> Unit)? = if (initialQuery == null) null else { -> navigation.pop() }
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
    // Persisted, so it is not a sink of the producer's own: the store is
    // the single source of truth and the toggle writes through it. Read
    // failures surface as the default (off), never as an exception, so a
    // broken preferences file cannot take the search screen down.
    //
    // Collected ONCE, here, and carried to the state through the results
    // flow rather than combined in a second time. Two collections would
    // open two subscriptions on the same store and — the part that
    // actually shows — could settle a beat apart, so the switch on the
    // field and the rows under it could disagree about whether names are
    // on.
    //
    // The default is emitted up front for the reason the results flow
    // below emits Idle up front: combine waits for every source, and
    // this one reads a file. Without a value in hand the whole state
    // flow — query field included — would stay silent until storage
    // answered, so a slow or wedged store would show a blank screen
    // rather than a search with names off.
    //
    // The stored value then replaces it, and on a cold start with names
    // on that is a second pass through the pipeline. It does NOT cost a
    // second search: the second pass asks [SearchMemory] for the same
    // query at the same limit and is served from what the first one
    // fetched. That is the same memory the name pages use, and it is
    // what makes the seed cheap enough to be unconditional — an earlier
    // attempt raced the seed against a timer instead, which was a great
    // deal of machinery for a cost the cache had already removed.
    val namesEnabled = preferences.booleanFlow(NAMES_IN_SEARCH_PREFERENCE, NAMES_IN_SEARCH_DEFAULT)
        .onStart { emit(NAMES_IN_SEARCH_DEFAULT) }
        .distinctUntilChanged()
    // One instance rather than one per emission, as with onBack above.
    // Writing goes straight to the store and the state comes back from
    // [namesEnabled]; nothing here holds a second copy of the answer.
    val onNamesEnabledChange: (Boolean) -> Unit = { enabled ->
        preferences.setBoolean(NAMES_IN_SEARCH_PREFERENCE, enabled)
    }
    // The rows standing on screen, so a failed extension can leave them
    // there. Only the producer writes it, and transformLatest runs its
    // blocks one at a time, so no two writers race for it.
    var standing: SearchResultsState.Results? = null
    // What has already been fetched for the current query, so a page
    // asks only for what is new. Same single-writer argument as above.
    val memory = SearchMemory()
    // transformLatest gives the debounce its latest-wins behavior: a
    // newer query cancels the delay and the in-flight search, so stale
    // results can never overwrite newer ones. While a search is in
    // flight the previous results state simply stays current (no
    // Loading emission), so refinement never blanks the visible hits.
    val results = combine(querySink, limitSink, retrySink, namesEnabled) { query, limit, _, names ->
        SearchRequest(query, limit, names)
    }
        .transformLatest { (query, limit, names) ->
            if (query.isBlank()) {
                standing = null
                memory.forget()
                emit(SearchOutcome(names, SearchResultsState.Idle))
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
                    emit(
                        SearchOutcome(
                            names,
                            onScreen.copy(onShowMore = null, footer = PagingFooterState.Loading),
                        ),
                    )
                }
                // One ceiling for both lists (Alex, 2026-08-26: "Cap at
                // 400 like words" — see the spec's change log). Names
                // were briefly uncapped and that meant holding all 22,831
                // matches of あ.
                //
                // The ceiling is enforced in exactly one place, on
                // onShowMore below, which is the only thing that ever
                // raises the limit. Clamping here as well would be a
                // second rule that no reachable state could tell apart
                // from the first.
                val outcome = when (val words = memory.words(search, invalidate, query, limit)) {
                    WordOutcome.Failed -> SearchResultsState.Error(query)

                    is WordOutcome.Loaded -> {
                        // Names are asked only after the words are in
                        // hand, and only when the toggle is on. A name
                        // query behind a failed word search would be
                        // work for a screen that is about to show an
                        // error.
                        val nameHits = memory.names(nameSearch, names, query, limit)
                        SearchResultsState.Results(
                            query = query,
                            hits = words.results.hits,
                            isFallback = words.results.isFallback,
                            names = nameHits.hits,
                            glossTokens = words.results.glossTokens,
                            // Two conditions rather than one: a match set
                            // larger than the ranking pool leaves hasMore
                            // true at the very point paging has to stop.
                            // See SearchResults.hasMore and
                            // SEARCH_MAX_RESULTS.
                            //
                            // Either list having more is enough — one
                            // pager serves both halves, and a common
                            // reading runs out of words long before it
                            // runs out of names — but the ceiling binds
                            // both alike.
                            //
                            // The next limit is computed from the one
                            // this search ran at, not incremented, so the
                            // scroll watcher calling it twice before the
                            // page lands asks for one page rather than
                            // two.
                            onShowMore = { limitSink.value = limit + SEARCH_RESULT_LIMIT }
                                .takeIf {
                                    (words.results.hasMore || nameHits.hasMore) &&
                                        limit < SEARCH_MAX_RESULTS
                                },
                        )
                    }
                }
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
                emit(SearchOutcome(names, next))
            }
        }
        // combine waits for every source, so without a value up front a
        // restored non-blank query would leave the whole state flow
        // silent for the length of the debounce — the screen would show
        // its initial (empty) state, query field included.
        .onStart { emit(SearchOutcome(NAMES_IN_SEARCH_DEFAULT, SearchResultsState.Idle)) }
    return combine(querySink, results) { query, outcome ->
        val resultsState = outcome.results
        SearchState(
            query = query,
            namesEnabled = outcome.namesEnabled,
            onNamesEnabledChange = onNamesEnabledChange,
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
            onBack = onBack,
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

/** One search the producer has been asked for: what to find, how much of it, and whether names count. */
private data class SearchRequest(
    val query: String,
    val limit: Int,
    val namesEnabled: Boolean,
)

/**
 * One emission of the results pipeline. It carries the toggle alongside
 * the results because [namesEnabled] is collected exactly once — see the
 * producer — and this is how its value reaches the state without a
 * second collection that could settle at a different moment.
 */
private data class SearchOutcome(
    val namesEnabled: Boolean,
    val results: SearchResultsState,
)

private sealed interface WordOutcome {
    data class Loaded(val results: SearchResults) : WordOutcome

    data object Failed : WordOutcome
}

/**
 * What the producer has already fetched for the query on screen, so a
 * page asks the database only for what is new.
 *
 * Both halves exist for the same reason and neither is an optimisation
 * of the ordinary path. Paging grows one limit that both lists share, so
 * without this every name page re-ran the word search — up to eight
 * English FTS pool-and-hydrate passes for a word result set that could
 * not change — and re-fetched every name row already on screen.
 *
 * A cache entry is only ever written after a successful fetch, and
 * `transformLatest` runs its blocks one at a time, so a cancelled search
 * leaves the memory exactly as it found it.
 */
private class SearchMemory {
    private var words: WordCache? = null
    private var names: NameCache? = null

    fun forget() {
        words = null
        names = null
    }

    /**
     * The word results for [query] at [limit], from memory when asking
     * again could not return anything new: the same limit, or any limit
     * at least as large as one the search already said was exhausted.
     */
    suspend fun words(
        search: suspend (String, Int) -> SearchResults,
        invalidate: suspend () -> Unit,
        query: String,
        limit: Int,
    ): WordOutcome {
        val cached = words?.takeIf {
            it.query == query && (it.limit == limit || (!it.results.hasMore && it.limit <= limit))
        }
        if (cached != null) return WordOutcome.Loaded(cached.results)
        return try {
            val results = search(query, limit)
            words = WordCache(query, limit, results)
            WordOutcome.Loaded(results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failing search must never take the screen down; the next
            // query change retries. What the failure does to the shared
            // handle is the one policy every screen shares.
            healDictionaryAfter(e, invalidate)
            WordOutcome.Failed
        }
    }

    /**
     * The name rows for [query] up to [limit], extending what is already
     * held rather than fetching it again.
     *
     * A failure here returns the rows already in hand instead of an
     * error. Names decorate a word result and must never hide it: the
     * word search has its own failure path, and letting a name query
     * take the screen down would be the defect the Phrases tab already
     * had once. The shared handle is deliberately not healed from here
     * either — the word search runs first on every keystroke and owns
     * that policy, so a genuinely dead database is caught there.
     */
    suspend fun names(
        nameSearch: suspend (String, Int, Int) -> NameResults,
        enabled: Boolean,
        query: String,
        limit: Int,
    ): NameResults {
        if (!enabled) {
            // The gate, and the only one: with names off nothing calls
            // the name search at all, so the default search issues
            // exactly the queries it issued before names existed.
            names = null
            return NameResults(emptyList())
        }
        val cached = names?.takeIf { it.query == query }
        if (cached != null && (cached.hits.size >= limit || !cached.hasMore)) {
            return NameResults(
                hits = cached.hits.take(limit),
                hasMore = cached.hits.size > limit || cached.hasMore,
            )
        }
        val held = cached?.hits.orEmpty()
        return try {
            val page = nameSearch(query, held.size, limit - held.size)
            val extended = held + page.hits
            names = NameCache(query, extended, page.hasMore)
            NameResults(extended, page.hasMore)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            NameResults(held, hasMore = false)
        }
    }
}

private class WordCache(
    val query: String,
    val limit: Int,
    val results: SearchResults,
)

private class NameCache(
    val query: String,
    val hits: List<NameHit>,
    val hasMore: Boolean,
)
