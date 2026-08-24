package cc.hosaka.okonomi.feature.word

import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.db.EntrySense
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EntryStateProducerTest {

    private fun detail(entryId: Long) = EntryDetail(
        entryId = entryId,
        headword = "食べる",
        forms = listOf(EntryForm("食べる", isCommon = true)),
        readings = listOf(EntryReading("たべる", "taberu", emptyList(), isCommon = true)),
        senses = listOf(
            EntrySense(
                tags = listOf("Ichidan verb"),
                glosses = listOf("to eat"),
                info = null,
                restrictions = emptyList(),
            ),
        ),
        isCommon = true,
        commonRank = 125,
    )

    private val neverInvalidate: suspend () -> Unit = {
        throw AssertionError("the dictionary handle must not be dropped for this failure")
    }

    private fun TestScope.collectStates(flow: Flow<EntryState>): List<EntryState> {
        val states = mutableListOf<EntryState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { states += it }
        }
        runCurrent()
        return states
    }

    @Test
    fun `loads the entry once and keeps it for the life of the screen`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        val load: suspend (Long) -> EntryDetail? = { id ->
            loads++
            detail(id)
        }

        val states = collectStates(
            scope.entryScreenStateProducer(entryId = 1358280, load = load, invalidate = neverInvalidate),
        )
        assertEquals(1358280, states.last().entryId)
        assertEquals(detail(1358280), assertIs<EntryContentState.Ready>(states.last().content).entry)
        assertEquals(1, loads)

        // A later run of the producer (the screen came back on screen)
        // reuses the persisted entry instead of querying again.
        val second = collectStates(
            scope.entryScreenStateProducer(entryId = 1358280, load = load, invalidate = neverInvalidate),
        )
        assertIs<EntryContentState.Ready>(second.last().content)
        assertEquals(1, loads)
    }

    @Test
    fun `an unknown id becomes an error body that offers a retry`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.entryScreenStateProducer(entryId = 404, load = { null }, invalidate = neverInvalidate),
        )

        val error = assertIs<EntryContentState.Error>(states.last().content)
        assertNotNull(error.onRetry, "the reader must be able to try again")
        assertEquals(404, states.last().entryId)
    }

    @Test
    fun `retrying reloads and replaces the error with the entry`() = runTest {
        val scope = FakeScreenStateScope()
        var attempts = 0
        val load: suspend (Long) -> EntryDetail? = { id ->
            attempts++
            if (attempts == 1) null else detail(id)
        }

        val states = collectStates(
            scope.entryScreenStateProducer(entryId = 1, load = load, invalidate = neverInvalidate),
        )
        val error = assertIs<EntryContentState.Error>(states.last().content)

        error.onRetry?.invoke()
        runCurrent()

        assertIs<EntryContentState.Ready>(states.last().content)
        assertEquals(2, attempts)
        assertTrue(
            states.any { it.content is EntryContentState.Loading },
            "the retry should show progress rather than leaving the error standing",
        )
    }

    @Test
    fun `a database failure drops the shared handle so the retry reopens it`() = runTest {
        val scope = FakeScreenStateScope()
        var invalidations = 0

        val states = collectStates(
            scope.entryScreenStateProducer(
                entryId = 1,
                load = { throw RuntimeException("database gone") },
                invalidate = { invalidations++ },
            ),
        )

        assertIs<EntryContentState.Error>(states.last().content)
        assertEquals(1, invalidations)
    }

    @Test
    fun `a programming error leaves the shared handle alone`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.entryScreenStateProducer(
                entryId = 1,
                // Reopening cannot fix a broken invariant, and throwing
                // the handle away would turn one bug into a
                // reprovisioning storm.
                load = { throw IllegalStateException("bad invariant") },
                invalidate = neverInvalidate,
            ),
        )

        assertIs<EntryContentState.Error>(states.last().content)
    }
}
