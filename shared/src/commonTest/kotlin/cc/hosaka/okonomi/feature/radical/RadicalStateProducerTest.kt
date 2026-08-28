package cc.hosaka.okonomi.feature.radical

import cc.hosaka.okonomi.db.KanjiHit
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.LoadState
import cc.hosaka.okonomi.feature.search.SearchRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * The radical screen's producer: one load kept for the life of the
 * screen, a retry behind every failure, and the two navigations the
 * screen offers.
 *
 * The navigation assertions live here rather than in the UI test because
 * this is where the routes are actually chosen — the screen is a pure
 * renderer that only calls the callbacks it is handed.
 * [RadicalScreenUiTest] covers the other half, that a tap on a cell
 * reaches `onKanjiClick` with that cell's literal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RadicalStateProducerTest {

    private fun hit(literal: String) = KanjiHit(literal = literal, strokeCount = 9, freq = 382)

    private val neverInvalidate: suspend () -> Unit = {
        throw AssertionError("the dictionary handle must not be dropped for this failure")
    }

    private fun TestScope.collectStates(flow: Flow<RadicalState>): List<RadicalState> {
        val states = mutableListOf<RadicalState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { states += it }
        }
        runCurrent()
        return states
    }

    @Test
    fun `loads the radical's kanji once and keeps them`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        var asked: String? = null
        val load: suspend (String) -> List<KanjiHit> = { radical ->
            loads++
            asked = radical
            listOf(hit("院"), hit("部"))
        }

        val states = collectStates(scope.radicalScreenStateProducer("阝", load = load, invalidate = neverInvalidate))

        assertEquals("阝", asked)
        val ready = assertIs<LoadState.Ready<List<KanjiHit>>>(states.last().kanji)
        assertEquals(listOf("院", "部"), ready.value.map { it.literal })
        assertEquals("阝", states.last().radical)
        assertEquals(1, loads)

        // A later run of the producer (the screen came back on top of
        // the stack) reuses the persisted result instead of querying
        // again.
        collectStates(scope.radicalScreenStateProducer("阝", load = load, invalidate = neverInvalidate))
        assertEquals(1, loads)
    }

    /**
     * An empty result is a state the screen renders, not a failure: a
     * radical nothing joins to must still leave the screen standing with
     * its bar on it.
     */
    @Test
    fun `a radical with no kanji is a ready empty list rather than an error`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.radicalScreenStateProducer("龠", load = { emptyList() }, invalidate = neverInvalidate),
        )

        val ready = assertIs<LoadState.Ready<List<KanjiHit>>>(states.last().kanji)
        assertTrue(ready.value.isEmpty())
    }

    /**
     * The retry itself, and the frame in the middle of it. The second
     * attempt is held open on [secondAttempt] so the in-flight state is
     * observable at all: with a load that returns without suspending,
     * the Loading the retry publishes is conflated away by the state
     * flow before any collector sees it, and an assertion counting
     * Loading emissions would be satisfied by the screen's very first
     * one whether the retry published anything or not.
     */
    @Test
    fun `a load failure becomes an error that retries into the kanji`() = runTest {
        val scope = FakeScreenStateScope()
        val secondAttempt = CompletableDeferred<Unit>()
        var attempts = 0
        val load: suspend (String) -> List<KanjiHit> = {
            attempts++
            if (attempts == 1) throw RuntimeException("database gone")
            secondAttempt.await()
            listOf(hit("院"))
        }

        val states = collectStates(scope.radicalScreenStateProducer("阝", load = load, invalidate = {}))
        val error = assertIs<LoadState.Error>(states.last().kanji)

        error.onRetry()
        runCurrent()

        // The error must not be left standing while the query runs.
        assertEquals(LoadState.Loading, states.last().kanji)

        secondAttempt.complete(Unit)
        runCurrent()

        val ready = assertIs<LoadState.Ready<List<KanjiHit>>>(states.last().kanji)
        assertEquals(listOf("院"), ready.value.map { it.literal })
        assertEquals(2, attempts)
    }

    /**
     * Two error emissions have to compare equal, or nothing can conflate
     * a redundant one away. That holds only while the state's callbacks
     * are built once for the whole run rather than per emission, which
     * is the thing this pins.
     */
    @Test
    fun `two error states compare equal so a redundant emission can be conflated`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.radicalScreenStateProducer(
                "阝",
                load = { throw RuntimeException("database gone") },
                invalidate = {},
            ),
        )
        val first = states.last()
        val error = assertIs<LoadState.Error>(first.kanji)

        error.onRetry()
        runCurrent()

        assertEquals(first, states.last())
    }

    @Test
    fun `a database failure drops the shared handle so the retry reopens it`() = runTest {
        val scope = FakeScreenStateScope()
        var invalidations = 0

        val states = collectStates(
            scope.radicalScreenStateProducer(
                "阝",
                load = { throw RuntimeException("database gone") },
                invalidate = { invalidations++ },
            ),
        )

        assertIs<LoadState.Error>(states.last().kanji)
        assertEquals(1, invalidations)
    }

    @Test
    fun `a programming error leaves the shared handle alone`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.radicalScreenStateProducer(
                "阝",
                // Reopening cannot fix a broken invariant, and throwing
                // the handle away would turn one bug into a
                // reprovisioning storm.
                load = { throw IllegalStateException("bad invariant") },
                invalidate = neverInvalidate,
            ),
        )

        assertIs<LoadState.Error>(states.last().kanji)
    }

    /**
     * From a character, the next question is which words are written
     * with it — which is the word search's job, reached by pushing a
     * route carrying the literal rather than by this screen growing word
     * results of its own.
     */
    @Test
    fun `tapping a kanji pushes a search for that literal`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.radicalScreenStateProducer("阝", load = { listOf(hit("院")) }, invalidate = neverInvalidate),
        )

        states.last().onKanjiClick("院")

        assertEquals<List<Route>>(listOf(SearchRoute("院")), scope.navigated)
    }

    @Test
    fun `going back pops the screen`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.radicalScreenStateProducer("阝", load = { listOf(hit("院")) }, invalidate = neverInvalidate),
        )

        states.last().onBack()

        assertEquals(1, scope.pops)
        assertTrue(scope.navigated.isEmpty(), "back must pop rather than push anything")
    }
}
