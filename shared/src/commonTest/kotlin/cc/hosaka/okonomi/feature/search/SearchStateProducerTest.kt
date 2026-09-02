package cc.hosaka.okonomi.feature.search

import cc.hosaka.okonomi.db.NameHit
import cc.hosaka.okonomi.db.NameResults
import cc.hosaka.okonomi.db.SEARCH_MAX_RESULTS
import cc.hosaka.okonomi.db.SEARCH_RESULT_LIMIT
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.SearchResults
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.ui.PagingFooterState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SearchStateProducerTest {

    private fun hit(entryId: Long) = SearchHit(
        entryId = entryId,
        titleSegments = listOf(TitleSegment("食べる")),
        traceLabels = emptyList(),
        senseLines = listOf("to eat"),
        isCommon = true,
    )

    private fun name(reading: String, kanji: String? = null) = NameHit(
        id = reading.hashCode().toLong(),
        kanji = kanji,
        reading = reading,
        types = listOf("surname"),
        romanisation = reading,
    )

    private val noSearch: suspend (String, Int) -> SearchResults = { _, _ ->
        throw AssertionError("search must not run for this scenario")
    }

    /**
     * The assertion behind "off means the query never runs". A lambda
     * that throws is the only way to tell "not issued" apart from
     * "issued and discarded", and the difference is the whole toggle:
     * search runs on every keystroke and こう matches 4,302 names.
     */
    private val noNameSearch: suspend (String, Int, Int) -> NameResults = { _, _, _ ->
        throw AssertionError("no name query may be issued while names are off")
    }

    private fun TestScope.collectStates(flow: Flow<SearchState>): List<SearchState> {
        val states = mutableListOf<SearchState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { states += it }
        }
        runCurrent()
        return states
    }

    /**
     * Runs the debounce window out. Background collectors never drive
     * the virtual clock on their own, so time is advanced explicitly.
     */
    private fun TestScope.settle() {
        advanceTimeBy(250.milliseconds)
        runCurrent()
    }

    @Test
    fun `the initial state is an empty query with no clear action and idle results`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(scope.producerUnderTest(search = noSearch))
        settle()

        val state = states.last()
        assertEquals("", state.query)
        assertNotNull(state.onQueryChange)
        assertNull(state.onClear)
        assertEquals(SearchResultsState.Idle, state.results)
    }

    @Test
    fun `typing updates the query and enables clear`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(search = { _, _ -> SearchResults(listOf(hit(1))) }),
        )

        states.last().onQueryChange!!.invoke("こんにちは")
        runCurrent()

        val state = states.last()
        assertEquals("こんにちは", state.query)
        assertNotNull(state.onClear)
    }

    @Test
    fun `clear empties the query and returns to idle with the clear action hidden`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(search = { _, _ -> SearchResults(listOf(hit(1))) }),
        )

        states.last().onQueryChange!!.invoke("こんにちは")
        settle()
        states.last().onClear!!.invoke()
        settle()

        val state = states.last()
        assertEquals("", state.query)
        assertNull(state.onClear)
        assertEquals(SearchResultsState.Idle, state.results)
    }

    @Test
    fun `query is kept in the persisted flow`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(search = { _, _ -> SearchResults(emptyList()) }),
        )

        states.last().onQueryChange!!.invoke("辞書")
        runCurrent()

        val persisted = scope.mutablePersistedFlow("query", "")
        assertEquals("辞書", persisted.value)
        assertSame(persisted, scope.mutablePersistedFlow("query", "other"))
    }

    /**
     * A search pushed from a tapped breakdown word arrives with the
     * word already in it and results already on the way: the reader
     * sees hits, not an empty field they have to type into.
     */
    @Test
    fun `a query carried by the route seeds the field and runs`() = runTest {
        val scope = FakeScreenStateScope()
        var searched: String? = null
        val states = collectStates(
            scope.producerUnderTest(
                initialQuery = "為る",
                search = { query, limit ->
                    searched = query
                    assertEquals(SEARCH_RESULT_LIMIT, limit, "a pushed search starts at one page")
                    SearchResults(listOf(hit(1)))
                },
            ),
        )
        settle()

        assertEquals("為る", states.last().query)
        assertEquals("為る", searched)
        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(listOf(1L), results.hits.map { it.entryId })
        assertNotNull(states.last().onClear, "a seeded query is still the reader's to clear")
    }

    /**
     * The route's query is an initial value, not a standing one: the
     * producer restarts whenever the screen has been off-screen for a
     * while, and re-seeding then would undo the reader's own editing.
     */
    @Test
    fun `a later run of the producer does not push the route's query back`() = runTest {
        val scope = FakeScreenStateScope()
        val search: suspend (String, Int) -> SearchResults = { _, _ -> SearchResults(emptyList()) }

        val states = collectStates(
            scope.producerUnderTest(initialQuery = "為る", search = search),
        )
        states.last().onQueryChange!!.invoke("為さる")
        settle()

        val restarted = collectStates(
            scope.producerUnderTest(initialQuery = "為る", search = search),
        )
        settle()

        assertEquals("為さる", restarted.last().query)
    }

    @Test
    fun `no route query is the empty field the tab's own root shows`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(scope.producerUnderTest(search = noSearch))
        settle()

        assertEquals("", states.last().query)
    }

    /**
     * A search pushed above an entry needs a way out that is not the
     * system gesture: the navigation bar hides itself once a section is
     * deeper than its root, and iOS has no system back button.
     */
    @Test
    fun `a pushed search offers a way back and the tab's root does not`() = runTest {
        val scope = FakeScreenStateScope()

        val pushed = collectStates(
            // A seeded query runs a search, so this one cannot use
            // [noSearch]; what it returns is beside the point here.
            scope.producerUnderTest(
                initialQuery = "為る",
                search = { _, _ -> SearchResults(emptyList()) },
            ),
        )
        settle()
        assertNotNull(pushed.last().onBack, "a pushed search must be leavable without a gesture")

        pushed.last().onBack!!.invoke()
        assertEquals(1, scope.pops, "back leaves this search for whatever is under it")

        val root = collectStates(
            FakeScreenStateScope().producerUnderTest(search = noSearch),
        )
        settle()
        assertNull(
            root.last().onBack,
            "the Search tab's own root has nothing under it and must look exactly as it did",
        )
    }

    @Test
    fun `a blank query stays idle and never searches`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(scope.producerUnderTest(search = noSearch))

        states.last().onQueryChange!!.invoke("   ")
        settle()

        assertEquals(SearchResultsState.Idle, states.last().results)
    }

    @Test
    fun `search is debounced and returns results`() = runTest {
        val scope = FakeScreenStateScope()
        val searched = mutableListOf<String>()
        val states = collectStates(
            scope.producerUnderTest(
                search = { query, _ ->
                    searched += query
                    SearchResults(listOf(hit(1)))
                },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        runCurrent()
        assertEquals(SearchResultsState.Searching("たべ"), states.last().results)
        assertTrue(searched.isEmpty(), "the search must wait out the debounce")

        settle()
        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals("たべ", results.query)
        assertEquals(listOf(1L), results.hits.map { it.entryId })
        assertEquals(listOf("たべ"), searched)
    }

    @Test
    fun `rapid typing searches only the latest query`() = runTest {
        val scope = FakeScreenStateScope()
        val searched = mutableListOf<String>()
        val states = collectStates(
            scope.producerUnderTest(
                search = { query, _ ->
                    searched += query
                    SearchResults(listOf(hit(query.length.toLong())))
                },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        advanceTimeBy(100.milliseconds)
        states.last().onQueryChange!!.invoke("たべも")
        settle()

        assertEquals(listOf("たべも"), searched)
        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(listOf(3L), results.hits.map { it.entryId })
    }

    @Test
    fun `a stale slow search never overwrites newer results`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { query, _ ->
                    if (query == "たべ") {
                        // Slow stale search: cancelled by the newer query.
                        delay(10_000)
                    }
                    SearchResults(listOf(hit(query.length.toLong())))
                },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        advanceTimeBy(300.milliseconds)
        states.last().onQueryChange!!.invoke("たべも")
        settle()
        // Even after the stale search's own delay would have elapsed
        // the newer results must stand.
        advanceTimeBy(20_000.milliseconds)
        runCurrent()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(listOf(3L), results.hits.map { it.entryId })
    }

    @Test
    fun `a failing search surfaces the error state and the next query retries`() = runTest {
        val scope = FakeScreenStateScope()
        var fail = true
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ ->
                    if (fail) error("boom")
                    SearchResults(listOf(hit(1)))
                },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        assertEquals(SearchResultsState.Error(query = "たべ"), states.last().results)

        fail = false
        states.last().onQueryChange!!.invoke("たべる")
        settle()
        assertIs<SearchResultsState.Results>(states.last().results)
    }

    @Test
    fun `previous results stay current while a newer query is in flight`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { query, _ -> SearchResults(listOf(hit(query.length.toLong()))) },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        assertIs<SearchResultsState.Results>(states.last().results)

        states.last().onQueryChange!!.invoke("たべも")
        runCurrent()

        // The older results stay in place, tagged with their query, so
        // the UI can render them while treating the state as refining.
        val inFlight = states.last()
        assertEquals("たべも", inFlight.query)
        val stale = assertIs<SearchResultsState.Results>(inFlight.results)
        assertEquals("たべ", stale.query)
        assertEquals(listOf(2L), stale.hits.map { it.entryId })

        settle()
        val fresh = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals("たべも", fresh.query)
        assertEquals(listOf(3L), fresh.hits.map { it.entryId })
    }

    @Test
    fun `the gloss tokens reach the state so english results can highlight`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1)), glossTokens = listOf("to", "eat")) },
            ),
        )

        states.last().onQueryChange!!.invoke("to eat")
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        // Dropping this from the producer would silently turn off
        // English gloss highlighting with every other test still green.
        assertEquals(listOf("to", "eat"), results.glossTokens)
    }

    @Test
    fun `a restored query reports searching before its first result lands`() = runTest {
        val scope = FakeScreenStateScope()
        // A query already in the persisted flow, as after process death.
        scope.mutablePersistedFlow("query", "").value = "たべ"

        val states = collectStates(
            scope.producerUnderTest(search = { _, _ -> SearchResults(listOf(hit(1))) }),
        )

        // Before the debounce elapses the screen must say it is working,
        // not sit on an unexplained empty results area.
        assertEquals(SearchResultsState.Searching("たべ"), states.last().results)
        settle()
        assertIs<SearchResultsState.Results>(states.last().results)
    }

    @Test
    fun `a programming error does not drop the shared handle`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> throw IllegalArgumentException("limit must be positive") },
                invalidate = { throw AssertionError("the handle must survive a programming error") },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()

        // Still a non-crashing error state; reopening the database
        // cannot fix a bad argument, so the handle must survive.
        assertEquals(SearchResultsState.Error(query = "たべ"), states.last().results)
    }

    @Test
    fun `a database failure drops the shared handle so the next query reopens it`() = runTest {
        val scope = FakeScreenStateScope()
        var invalidations = 0
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> throw RuntimeException("database gone") },
                invalidate = { invalidations++ },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()

        assertEquals(SearchResultsState.Error(query = "たべ"), states.last().results)
        assertEquals(1, invalidations)
    }

    @Test
    fun `results with nothing more behind them offer no way to show more`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1)), hasMore = false) },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertNull(results.onShowMore)
    }

    @Test
    fun `showing more asks for a longer page of the same ranking`() = runTest {
        val scope = FakeScreenStateScope()
        val limits = mutableListOf<Int>()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit ->
                    limits += limit
                    // Deterministic ranking: a longer limit is a longer
                    // prefix of the same order, which is what lets the
                    // rows already on screen stay put.
                    SearchResults(
                        hits = (1..limit).map { hit(it.toLong()) },
                        hasMore = true,
                    )
                },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        val first = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(SEARCH_RESULT_LIMIT, first.hits.size)

        first.onShowMore!!.invoke()
        settle()

        val second = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(SEARCH_RESULT_LIMIT * 2, second.hits.size)
        assertEquals(first.hits, second.hits.take(SEARCH_RESULT_LIMIT))
        assertEquals(listOf(SEARCH_RESULT_LIMIT, SEARCH_RESULT_LIMIT * 2), limits)
    }

    @Test
    fun `asking for more twice before the page lands still adds one page`() = runTest {
        val scope = FakeScreenStateScope()
        val limits = mutableListOf<Int>()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit ->
                    limits += limit
                    SearchResults((1..limit).map { hit(it.toLong()) }, hasMore = true)
                },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        val first = assertIs<SearchResultsState.Results>(states.last().results)

        // The scroll watcher may fire again before the page it asked for
        // arrives, so the callback has to be idempotent.
        first.onShowMore!!.invoke()
        first.onShowMore.invoke()
        settle()

        assertEquals(listOf(SEARCH_RESULT_LIMIT, SEARCH_RESULT_LIMIT * 2), limits)
    }

    @Test
    fun `paging stops at the ranking pool rather than growing it`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                // hasMore stays true forever: a match set larger than the
                // pool always has more behind it, and stopping is the
                // producer's decision rather than the search's.
                search = { _, limit ->
                    SearchResults((1..limit).map { hit(it.toLong()) }, hasMore = true)
                },
            ),
        )

        states.last().onQueryChange!!.invoke("to eat")
        settle()
        var results = assertIs<SearchResultsState.Results>(states.last().results)
        var pages = 0
        while (results.onShowMore != null) {
            pages++
            assertTrue(pages < 100, "paging must terminate")
            results.onShowMore.invoke()
            settle()
            results = assertIs<SearchResultsState.Results>(states.last().results)
        }

        assertEquals(SEARCH_MAX_RESULTS, results.hits.size)
    }

    @Test
    fun `a refined query starts from the first page again`() = runTest {
        val scope = FakeScreenStateScope()
        val limits = mutableListOf<Int>()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit ->
                    limits += limit
                    SearchResults((1..limit).map { hit(it.toLong()) }, hasMore = true)
                },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        assertIs<SearchResultsState.Results>(states.last().results).onShowMore!!.invoke()
        settle()
        limits.clear()

        states.last().onQueryChange!!.invoke("たべる")
        settle()

        // Asking the new query at the depth the old one was scrolled to
        // would make a keystroke cost a hundred rows nobody has reached.
        assertEquals(listOf(SEARCH_RESULT_LIMIT), limits)
    }

    @Test
    fun `a failed extension leaves the rows already on screen standing`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit ->
                    if (limit > SEARCH_RESULT_LIMIT) error("database gone")
                    SearchResults((1..limit).map { hit(it.toLong()) }, hasMore = true)
                },
                invalidate = {},
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        val first = assertIs<SearchResultsState.Results>(states.last().results)

        first.onShowMore!!.invoke()
        settle()

        // The reader was reading these; a page that failed to arrive
        // must not replace them with an error screen.
        val after = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(first.hits, after.hits)
    }

    /**
     * The half of the failed extension that was silently broken.
     *
     * The rows stayed on screen, but the "show more" they came back with
     * was the one built for the fifty-row result — it wrote 100 into the
     * limit, which already held 100, and a `StateFlow` conflates an
     * identical value away. No search ever ran again, for the rest of
     * that query. LazyListPaging's own kdoc justifies watching the
     * layout precisely because "an extension that failed could never be
     * retried by scrolling to the end again"; this is that promise.
     */
    @Test
    fun `scrolling to the end again after a failed extension runs the search again`() = runTest {
        val scope = FakeScreenStateScope()
        val limits = mutableListOf<Int>()
        var failExtensions = true
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit ->
                    limits += limit
                    if (limit > SEARCH_RESULT_LIMIT && failExtensions) error("database gone")
                    SearchResults((1..limit).map { hit(it.toLong()) }, hasMore = true)
                },
                invalidate = {},
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        assertIs<SearchResultsState.Results>(states.last().results).onShowMore!!.invoke()
        settle()
        assertEquals(listOf(SEARCH_RESULT_LIMIT, SEARCH_RESULT_LIMIT * 2), limits)

        // The reader scrolls to the end again. The database has come
        // back by now, so a retry that actually runs also lands.
        failExtensions = false
        val standing = assertIs<SearchResultsState.Results>(states.last().results)
        assertNotNull(standing.onShowMore, "a failed extension must leave the rows pageable")
        standing.onShowMore.invoke()
        settle()

        assertEquals(
            listOf(SEARCH_RESULT_LIMIT, SEARCH_RESULT_LIMIT * 2, SEARCH_RESULT_LIMIT * 2),
            limits,
            "the retry must re-run the search at the limit that failed",
        )
        val after = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(SEARCH_RESULT_LIMIT * 2, after.hits.size)
    }

    /**
     * Item K: the reader has to be told that a page is on its way, and
     * that one did not arrive. Neither could be inferred from
     * `onShowMore` being null, which is why the footer is its own state.
     */
    @Test
    fun `an extension in flight puts a loading footer under the standing rows`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit ->
                    if (limit > SEARCH_RESULT_LIMIT) delay(1_000)
                    SearchResults((1..limit).map { hit(it.toLong()) }, hasMore = true)
                },
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        val first = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(PagingFooterState.None, first.footer)

        first.onShowMore!!.invoke()
        runCurrent()

        val inFlight = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(PagingFooterState.Loading, inFlight.footer)
        assertEquals(first.hits, inFlight.hits, "the rows being read must stay put")
        assertNull(inFlight.onShowMore, "the page it would ask for is already on its way")

        advanceTimeBy(2_000.milliseconds)
        runCurrent()

        val landed = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(PagingFooterState.None, landed.footer)
        assertEquals(SEARCH_RESULT_LIMIT * 2, landed.hits.size)
    }

    @Test
    fun `a failed extension says so in the footer and its retry runs the search`() = runTest {
        val scope = FakeScreenStateScope()
        val limits = mutableListOf<Int>()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit ->
                    limits += limit
                    if (limit > SEARCH_RESULT_LIMIT) error("database gone")
                    SearchResults((1..limit).map { hit(it.toLong()) }, hasMore = true)
                },
                invalidate = {},
            ),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()
        assertIs<SearchResultsState.Results>(states.last().results).onShowMore!!.invoke()
        settle()

        val failed = assertIs<SearchResultsState.Results>(states.last().results)
        val footer = assertIs<PagingFooterState.Failed>(failed.footer)
        assertEquals(SEARCH_RESULT_LIMIT, failed.hits.size, "the rows on screen must stand")

        footer.onRetry!!.invoke()
        settle()

        assertEquals(
            listOf(SEARCH_RESULT_LIMIT, SEARCH_RESULT_LIMIT * 2, SEARCH_RESULT_LIMIT * 2),
            limits,
        )
    }

    @Test
    fun `names are off on a fresh install and no name query is issued`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1))) },
                nameSearch = noNameSearch,
                preferences = FakePreferenceStore(),
            ),
        )

        states.last().onQueryChange!!.invoke("たなか")
        settle()

        assertFalse(states.last().namesEnabled)
        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(listOf(1L), results.hits.map { it.entryId }, "the words are exactly today's")
        assertTrue(results.names.isEmpty())
    }

    @Test
    fun `turning names on runs the name query and puts the names below the words`() = runTest {
        val scope = FakeScreenStateScope()
        val preferences = FakePreferenceStore()
        val nameQueries = mutableListOf<String>()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1))) },
                nameSearch = { query, _, _ ->
                    nameQueries += query
                    NameResults(listOf(name("たなか", "田中")))
                },
                preferences = preferences,
            ),
        )

        states.last().onQueryChange!!.invoke("たなか")
        settle()
        assertTrue(nameQueries.isEmpty(), "off is off until the reader says otherwise")

        states.last().onNamesEnabledChange!!.invoke(true)
        settle()

        assertTrue(states.last().namesEnabled)
        assertEquals(listOf("たなか"), nameQueries)
        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(listOf(1L), results.hits.map { it.entryId })
        assertEquals(listOf("田中"), results.names.map { it.kanji })
        assertEquals(
            listOf(NAMES_IN_SEARCH_PREFERENCE to true),
            preferences.writes,
            "the toggle must reach storage, or it cannot survive a relaunch",
        )
    }

    /**
     * The matrix's "toggle persists" row. A later run of the producer is
     * what a relaunch looks like from here: the store outlives it, the
     * producer does not.
     */
    @Test
    fun `the names toggle survives a restart of the producer`() = runTest {
        val scope = FakeScreenStateScope()
        val preferences = FakePreferenceStore()
        val search: suspend (String, Int) -> SearchResults = { _, _ -> SearchResults(emptyList()) }
        val nameSearch: suspend (String, Int, Int) -> NameResults = { _, _, _ -> NameResults(emptyList()) }

        val first = collectStates(
            scope.producerUnderTest(search = search, nameSearch = nameSearch, preferences = preferences),
        )
        settle()
        first.last().onNamesEnabledChange!!.invoke(true)
        settle()

        val restarted = collectStates(
            scope.producerUnderTest(search = search, nameSearch = nameSearch, preferences = preferences),
        )
        settle()

        assertTrue(restarted.last().namesEnabled, "a stored toggle must come back on")
    }

    @Test
    fun `turning names off again stops the name query`() = runTest {
        val scope = FakeScreenStateScope()
        val preferences = FakePreferenceStore(mapOf(NAMES_IN_SEARCH_PREFERENCE to true))
        var namesOn = true
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1))) },
                nameSearch = { _, _, _ ->
                    if (!namesOn) throw AssertionError("no name query may be issued while names are off")
                    NameResults(listOf(name("たなか", "田中")))
                },
                preferences = preferences,
            ),
        )

        states.last().onQueryChange!!.invoke("たなか")
        settle()
        assertTrue(assertIs<SearchResultsState.Results>(states.last().results).names.isNotEmpty())

        namesOn = false
        states.last().onNamesEnabledChange!!.invoke(false)
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertTrue(results.names.isEmpty(), "the rows go with the query that fetched them")
        assertEquals(listOf(1L), results.hits.map { it.entryId }, "the words are untouched by the toggle")
    }

    /**
     * The matrix's "words absent" row: a reading no word uses is still a
     * name, and the screen has to treat that as a result rather than as
     * an empty search.
     */
    @Test
    fun `names alone are a result even with no words above them`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(emptyList()) },
                nameSearch = { _, _, _ -> NameResults(listOf(name("たなか", "田中"))) },
                preferences = FakePreferenceStore(mapOf(NAMES_IN_SEARCH_PREFERENCE to true)),
            ),
        )

        states.last().onQueryChange!!.invoke("たなか")
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertTrue(results.hits.isEmpty())
        assertEquals(listOf("田中"), results.names.map { it.kanji })
    }

    /**
     * こう matches 4,302 names and a handful of words. One limit and one
     * pager serve both halves of the list, so the names having more is
     * enough to keep paging alive after the words have run out.
     */
    @Test
    fun `names with more behind them keep the list pageable after the words run out`() = runTest {
        val scope = FakeScreenStateScope()
        val names = FakeNames(size = 500)
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1)), hasMore = false) },
                nameSearch = names::page,
                preferences = FakePreferenceStore(mapOf(NAMES_IN_SEARCH_PREFERENCE to true)),
            ),
        )

        states.last().onQueryChange!!.invoke("こう")
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertNotNull(results.onShowMore, "the names below still have pages behind them")

        results.onShowMore.invoke()
        settle()

        val extended = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(SEARCH_RESULT_LIMIT * 2, extended.names.size)
        assertEquals(listOf(1L), extended.hits.map { it.entryId }, "the words never move")
    }

    /**
     * Alex renegotiated "uncapped" on 2026-08-26 — "Cap at 400 like
     * words" — so one ceiling now binds both lists. The version this
     * replaces let names page without bound, which for あ meant holding
     * all 22,831 of its matches.
     */
    @Test
    fun `both lists stop at the same ceiling`() = runTest {
        val scope = FakeScreenStateScope()
        val names = FakeNames(size = SEARCH_MAX_RESULTS * 2)
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit -> SearchResults((1..limit).map { hit(it.toLong()) }, hasMore = true) },
                nameSearch = names::page,
                preferences = FakePreferenceStore(mapOf(NAMES_IN_SEARCH_PREFERENCE to true)),
            ),
        )

        states.last().onQueryChange!!.invoke("こう")
        settle()
        var results = assertIs<SearchResultsState.Results>(states.last().results)
        var pages = 0
        while (results.onShowMore != null) {
            pages++
            assertTrue(pages < 100, "paging must terminate")
            results.onShowMore.invoke()
            settle()
            results = assertIs<SearchResultsState.Results>(states.last().results)
        }

        assertEquals(SEARCH_MAX_RESULTS, results.hits.size, "the words stop at the pool")
        assertEquals(SEARCH_MAX_RESULTS, results.names.size, "and the names stop with them")
        assertTrue(
            names.calls.all { (offset, limit) -> offset + limit <= SEARCH_MAX_RESULTS },
            "nothing may be fetched past the ceiling: ${names.calls}",
        )
    }

    /**
     * Paging grows one limit that both lists share, so without a memory
     * of what has already been fetched every name page re-ran the word
     * search — up to eight English FTS pool-and-hydrate passes for a
     * result set that could not change — and re-read every name row
     * already on screen.
     */
    @Test
    fun `a name page asks only for what is new and does not run the word search again`() = runTest {
        val scope = FakeScreenStateScope()
        val wordLimits = mutableListOf<Int>()
        val names = FakeNames(size = 500)
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, limit ->
                    wordLimits += limit
                    // Exhausted at the first page: nothing a longer limit
                    // could add.
                    SearchResults(listOf(hit(1)), hasMore = false)
                },
                nameSearch = names::page,
                preferences = FakePreferenceStore(mapOf(NAMES_IN_SEARCH_PREFERENCE to true)),
            ),
        )

        states.last().onQueryChange!!.invoke("こう")
        settle()
        assertIs<SearchResultsState.Results>(states.last().results).onShowMore!!.invoke()
        settle()
        assertIs<SearchResultsState.Results>(states.last().results).onShowMore!!.invoke()
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(SEARCH_RESULT_LIMIT * 3, results.names.size)
        assertEquals(
            listOf(0 to SEARCH_RESULT_LIMIT, SEARCH_RESULT_LIMIT to SEARCH_RESULT_LIMIT, 100 to SEARCH_RESULT_LIMIT),
            names.calls,
            "each page must fetch its own rows and no others",
        )
        assertEquals(
            listOf(SEARCH_RESULT_LIMIT),
            wordLimits,
            "the word search ran once; the two name pages must not have re-run it",
        )
    }

    /**
     * Names decorate a word result and must never hide one. The name
     * search used to sit inside the same try as the word search, so a
     * name query failing discarded results that had already arrived and
     * put an error on screen — the defect the Phrases tab had once.
     */
    @Test
    fun `a failing name search leaves the words on screen`() = runTest {
        val scope = FakeScreenStateScope()
        var namesFail = true
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1))) },
                nameSearch = { _, _, _ ->
                    if (namesFail) error("names gone")
                    NameResults(listOf(name("たなか", "田中")))
                },
                preferences = FakePreferenceStore(mapOf(NAMES_IN_SEARCH_PREFERENCE to true)),
                invalidate = { throw AssertionError("a name failure must not drop the shared handle") },
            ),
        )

        states.last().onQueryChange!!.invoke("たなか")
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertEquals(listOf(1L), results.hits.map { it.entryId }, "the words were fetched and must stand")
        assertTrue(results.names.isEmpty())

        // And the failure is not sticky: the next query gets names again.
        namesFail = false
        states.last().onQueryChange!!.invoke("たなかa")
        settle()
        assertEquals(
            listOf("田中"),
            assertIs<SearchResultsState.Results>(states.last().results).names.map { it.kanji },
        )
    }

    /**
     * The toggle is seeded with its default so the screen never waits
     * on storage, and the stored value then replaces it — a second pass
     * through the whole pipeline whenever a reader has names on.
     *
     * That pass must not cost a second search. The store here answers at
     * 300 ms, past the 200 ms debounce, so the first pass has already
     * queried by the time the real value lands: what stops the second
     * from querying again is [SearchMemory] recognising the same query
     * at the same limit. Without it this is two full searches — and on
     * the English path, two FTS pool-and-hydrate passes.
     */
    @Test
    fun `a restored search with names already on runs the word search once`() = runTest {
        val scope = FakeScreenStateScope()
        scope.mutablePersistedFlow("query", "").value = "たなか"
        val wordSearches = mutableListOf<String>()
        val nameSearches = mutableListOf<String>()
        val states = collectStates(
            scope.producerUnderTest(
                search = { query, _ ->
                    wordSearches += query
                    SearchResults(listOf(hit(1)))
                },
                nameSearch = { query, _, _ ->
                    nameSearches += query
                    NameResults(listOf(name("たなか", "田中")))
                },
                preferences = SlowPreferenceStore(
                    answerAfter = 300.milliseconds,
                    values = mapOf(NAMES_IN_SEARCH_PREFERENCE to true),
                ),
            ),
        )
        advanceTimeBy(2_000.milliseconds)
        runCurrent()

        assertEquals(listOf("たなか"), wordSearches, "the seed must not have cost a second search")
        assertEquals(listOf("たなか"), nameSearches)
        assertTrue(states.last().namesEnabled)
    }

    /**
     * The switch on the field and the rows under it come from one
     * collection of one flow, so they cannot report different answers.
     * Two collections could settle a beat apart and did.
     */
    @Test
    fun `the toggle and the rows under it never disagree`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1))) },
                nameSearch = { _, _, _ -> NameResults(listOf(name("たなか", "田中"))) },
                preferences = FakePreferenceStore(),
            ),
        )

        states.last().onQueryChange!!.invoke("たなか")
        settle()
        states.last().onNamesEnabledChange!!.invoke(true)
        settle()

        assertTrue(states.last().namesEnabled)
        states.forEach { state ->
            val results = state.results
            if (results is SearchResultsState.Results && results.names.isNotEmpty()) {
                assertTrue(
                    state.namesEnabled,
                    "a state showing name rows must also report the toggle as on",
                )
            }
        }
    }

    /**
     * The matrix's "falls back to off if storage fails" row, at the seam
     * the producer sees: a store that reports nothing at all must not
     * leave the screen waiting on it.
     */
    @Test
    fun `a preference store that never answers leaves names off rather than the screen blank`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1))) },
                nameSearch = noNameSearch,
                preferences = SilentPreferenceStore(),
            ),
        )

        states.last().onQueryChange!!.invoke("たなか")
        settle()

        assertFalse(states.last().namesEnabled)
        assertIs<SearchResultsState.Results>(states.last().results)
    }

    /**
     * The harder half of the same promise: a store that neither answers
     * nor fails, which an empty flow does not model because it completes.
     * The seed has to arrive on a timer for the screen to come up at all.
     */
    @Test
    fun `a preference store that hangs still lets the screen come up`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1))) },
                nameSearch = noNameSearch,
                preferences = HangingPreferenceStore(),
            ),
        )
        states.last().onQueryChange!!.invoke("たなか")
        settle()

        assertFalse(states.last().namesEnabled)
        assertIs<SearchResultsState.Results>(states.last().results)
    }

    @Test
    fun `the fallback flag reaches the state`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.producerUnderTest(
                search = { _, _ -> SearchResults(listOf(hit(1)), isFallback = true) },
            ),
        )

        states.last().onQueryChange!!.invoke("たべxyz")
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertTrue(results.isFallback)
    }
}

/**
 * [searchScreenStateProducer] with a preference store the test owns.
 *
 * The production default is `appPreferences()`, which opens a real
 * DataStore over a real file: it answers on a real dispatcher, so a test
 * driving virtual time cannot say when. Every case here injects a map
 * instead, and the two cases that are *about* storage inject something
 * deliberately broken. Every other argument keeps the production
 * default, so a signature change still has to be dealt with here.
 */
private suspend fun ScreenStateScope.producerUnderTest(
    initialQuery: String? = null,
    search: suspend (String, Int) -> SearchResults,
    nameSearch: suspend (String, Int, Int) -> NameResults = { _, _, _ -> NameResults(emptyList()) },
    preferences: PreferenceStore = FakePreferenceStore(),
    invalidate: suspend () -> Unit = { invalidateDictionary() },
): Flow<SearchState> = searchScreenStateProducer(
    initialQuery = initialQuery,
    search = search,
    nameSearch = nameSearch,
    preferences = preferences,
    invalidate = invalidate,
)

/** A store whose reads complete without ever producing a value. */
private class SilentPreferenceStore : PreferenceStore {
    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = emptyFlow()

    override fun setBoolean(key: String, value: Boolean) = Unit
}

/** A store whose reads neither answer nor fail — a wedged file. */
private class HangingPreferenceStore : PreferenceStore {
    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = flow { awaitCancellation() }

    override fun setBoolean(key: String, value: Boolean) = Unit
}

/** A store that answers, but only after [answerAfter] — a cold file read. */
private class SlowPreferenceStore(
    private val answerAfter: Duration,
    private val values: Map<String, Boolean>,
) : PreferenceStore {
    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = flow {
        delay(answerAfter)
        emit(values[key] ?: default)
    }

    override fun setBoolean(key: String, value: Boolean) = Unit
}

/**
 * A name corpus that pages the way the database does: by offset, over a
 * total order, so a page is the rows after the previous one rather than
 * the previous one fetched again.
 */
private class FakeNames(private val size: Int) {
    /** Every (offset, limit) asked for, in order. */
    val calls = mutableListOf<Pair<Int, Int>>()

    fun page(query: String, offset: Int, limit: Int): NameResults {
        calls += offset to limit
        val end = minOf(offset + limit, size)
        val rows = if (offset >= size) {
            emptyList()
        } else {
            (offset until end).map {
                NameHit(id = it.toLong(), kanji = null, reading = "$query$it", types = listOf("surname"), romanisation = "n$it")
            }
        }
        return NameResults(rows, hasMore = end < size)
    }
}
