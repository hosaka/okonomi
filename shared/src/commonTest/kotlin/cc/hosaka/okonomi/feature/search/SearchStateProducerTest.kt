package cc.hosaka.okonomi.feature.search

import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.SearchResults
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

    private val noSearch: suspend (String) -> SearchResults = {
        throw AssertionError("search must not run for this scenario")
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
    fun `starts with an empty query, no clear action and idle results`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(scope.searchScreenStateProducer(search = noSearch))
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
            scope.searchScreenStateProducer(search = { SearchResults(listOf(hit(1))) }),
        )

        states.last().onQueryChange!!.invoke("こんにちは")
        runCurrent()

        val state = states.last()
        assertEquals("こんにちは", state.query)
        assertNotNull(state.onClear)
    }

    @Test
    fun `clear empties the query, hides clear and returns to idle`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.searchScreenStateProducer(search = { SearchResults(listOf(hit(1))) }),
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
            scope.searchScreenStateProducer(search = { SearchResults(emptyList()) }),
        )

        states.last().onQueryChange!!.invoke("辞書")
        runCurrent()

        val persisted = scope.mutablePersistedFlow("query", "")
        assertEquals("辞書", persisted.value)
        assertSame(persisted, scope.mutablePersistedFlow("query", "other"))
    }

    @Test
    fun `a blank query stays idle and never searches`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(scope.searchScreenStateProducer(search = noSearch))

        states.last().onQueryChange!!.invoke("   ")
        settle()

        assertEquals(SearchResultsState.Idle, states.last().results)
    }

    @Test
    fun `search is debounced and returns results`() = runTest {
        val scope = FakeScreenStateScope()
        val searched = mutableListOf<String>()
        val states = collectStates(
            scope.searchScreenStateProducer(
                search = { query ->
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
            scope.searchScreenStateProducer(
                search = { query ->
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
            scope.searchScreenStateProducer(
                search = { query ->
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
            scope.searchScreenStateProducer(
                search = {
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
            scope.searchScreenStateProducer(
                search = { query -> SearchResults(listOf(hit(query.length.toLong()))) },
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
            scope.searchScreenStateProducer(
                search = { SearchResults(listOf(hit(1)), glossTokens = listOf("to", "eat")) },
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
            scope.searchScreenStateProducer(search = { SearchResults(listOf(hit(1))) }),
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
            scope.searchScreenStateProducer(search = { throw IllegalArgumentException("limit must be positive") }),
        )

        states.last().onQueryChange!!.invoke("たべ")
        settle()

        // Still a non-crashing error state; reopening the database
        // cannot fix a bad argument, so the handle must survive.
        assertEquals(SearchResultsState.Error(query = "たべ"), states.last().results)
    }

    @Test
    fun `the fallback flag reaches the state`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.searchScreenStateProducer(
                search = { SearchResults(listOf(hit(1)), isFallback = true) },
            ),
        )

        states.last().onQueryChange!!.invoke("たべxyz")
        settle()

        val results = assertIs<SearchResultsState.Results>(states.last().results)
        assertTrue(results.isFallback)
    }
}
