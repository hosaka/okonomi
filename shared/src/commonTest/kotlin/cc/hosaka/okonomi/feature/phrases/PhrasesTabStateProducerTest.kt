package cc.hosaka.okonomi.feature.phrases

import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
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
class PhrasesTabStateProducerTest {

    private val sentence = ExampleSentence(
        id = 1L,
        japanese = "早く食べる。",
        english = "Eat quickly.",
        words = listOf(BreakdownWord("早く", null), BreakdownWord("食べる", "たべる")),
    )

    private val neverInvalidate: suspend () -> Unit = {
        throw AssertionError("the dictionary handle must not be dropped for this failure")
    }

    private fun TestScope.collectStates(flow: Flow<PhrasesTabState>): List<PhrasesTabState> {
        val states = mutableListOf<PhrasesTabState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { states += it }
        }
        runCurrent()
        return states
    }

    @Test
    fun `loads the entry's sentences once and keeps them`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        var asked = 0L
        val load: suspend (Long) -> List<ExampleSentence> = { entryId ->
            loads++
            asked = entryId
            listOf(sentence)
        }

        val states = collectStates(
            scope.phrasesTabStateProducer(entryId = 1358280L, load = load, invalidate = neverInvalidate),
        )

        assertEquals(1358280L, asked)
        val ready = assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals(listOf(sentence), ready.sentences)
        assertEquals(1, loads)

        // A later run of the producer (the tab came back on screen)
        // reuses the persisted sentences instead of querying again.
        collectStates(
            scope.phrasesTabStateProducer(entryId = 1358280L, load = load, invalidate = neverInvalidate),
        )
        assertEquals(1, loads)
    }

    @Test
    fun `an entry the corpus never uses is its own state, not an error`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                load = { emptyList() },
                invalidate = neverInvalidate,
            ),
        )

        // The outcome for ~86% of the dictionary: a successful load with
        // nothing in it must never read as a failure.
        assertEquals(PhrasesTabContentState.Empty, states.last().content)
    }

    @Test
    fun `a load failure becomes an error body that offers a retry`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                load = { throw RuntimeException("database gone") },
                invalidate = {},
            ),
        )

        val error = assertIs<PhrasesTabContentState.Error>(states.last().content)
        assertNotNull(error.onRetry, "the reader must be able to try again")
    }

    @Test
    fun `two error emissions compare equal so a redundant one can be conflated`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                load = { throw RuntimeException("database gone") },
                invalidate = {},
            ),
        )
        val first = assertIs<PhrasesTabContentState.Error>(states.last().content)

        first.onRetry?.invoke()
        runCurrent()

        assertEquals(first, states.last().content)
    }

    @Test
    fun `retrying reloads and replaces the error with the sentences`() = runTest {
        val scope = FakeScreenStateScope()
        var attempts = 0
        val load: suspend (Long) -> List<ExampleSentence> = {
            attempts++
            if (attempts == 1) throw RuntimeException("database gone") else listOf(sentence)
        }

        val states = collectStates(
            scope.phrasesTabStateProducer(entryId = 1L, load = load, invalidate = {}),
        )
        val error = assertIs<PhrasesTabContentState.Error>(states.last().content)

        error.onRetry?.invoke()
        runCurrent()

        assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals(2, attempts)
        assertTrue(
            states.any { it.content is PhrasesTabContentState.Loading },
            "the retry should show progress rather than leaving the error standing",
        )
    }

    @Test
    fun `a database failure drops the shared handle so the retry reopens it`() = runTest {
        val scope = FakeScreenStateScope()
        var invalidations = 0

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                load = { throw RuntimeException("database gone") },
                invalidate = { invalidations++ },
            ),
        )

        assertIs<PhrasesTabContentState.Error>(states.last().content)
        assertEquals(1, invalidations)
    }

    @Test
    fun `a programming error leaves the shared handle alone`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                // Reopening cannot fix a broken invariant, and throwing
                // the handle away would turn one bug into a
                // reprovisioning storm.
                load = { throw IllegalStateException("bad invariant") },
                invalidate = neverInvalidate,
            ),
        )

        assertIs<PhrasesTabContentState.Error>(states.last().content)
    }
}
