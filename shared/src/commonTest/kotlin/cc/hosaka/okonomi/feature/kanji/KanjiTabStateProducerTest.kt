package cc.hosaka.okonomi.feature.kanji

import cc.hosaka.okonomi.db.KanjiCharacter
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
class KanjiTabStateProducerTest {

    private fun character(literal: String) = KanjiCharacter(
        literal = literal,
        strokeCount = 9,
        grade = 2,
        jlpt = 4,
        freq = 382,
        onReadings = listOf("ショク"),
        kunReadings = listOf("く.う"),
        nameReadings = listOf("ぐい"),
        meanings = listOf("eat"),
        radicals = listOf(literal),
        strokePaths = emptyList(),
    )

    private val neverLoad: suspend (List<String>) -> List<KanjiCharacter> = {
        throw AssertionError("a headword with no kanji has nothing to look up")
    }

    private val neverInvalidate: suspend () -> Unit = {
        throw AssertionError("the dictionary handle must not be dropped for this failure")
    }

    private fun TestScope.collectStates(flow: Flow<KanjiTabState>): List<KanjiTabState> {
        val states = mutableListOf<KanjiTabState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { states += it }
        }
        runCurrent()
        return states
    }

    @Test
    fun `loads the headword's characters once and keeps them`() = runTest {
        val scope = FakeScreenStateScope()
        var loads = 0
        var asked: List<String> = emptyList()
        val load: suspend (List<String>) -> List<KanjiCharacter> = { literals ->
            loads++
            asked = literals
            literals.map { character(it) }
        }

        val states = collectStates(
            scope.kanjiTabStateProducer(headword = "食べ物", load = load, invalidate = neverInvalidate),
        )

        // Kana between the characters is skipped, in headword order.
        assertEquals(listOf("食", "物"), asked)
        val ready = assertIs<KanjiTabContentState.Ready>(states.last().content)
        assertEquals(listOf("食", "物"), ready.characters.map { it.literal })
        assertEquals(1, loads)

        // A later run of the producer (the tab came back on screen)
        // reuses the persisted characters instead of querying again.
        collectStates(
            scope.kanjiTabStateProducer(headword = "食べ物", load = load, invalidate = neverInvalidate),
        )
        assertEquals(1, loads)
    }

    @Test
    fun `a headword with no kanji is its own state reached without a query`() = runTest {
        val scope = FakeScreenStateScope()

        val kana = collectStates(
            scope.kanjiTabStateProducer(headword = "ありがとう", load = neverLoad, invalidate = neverInvalidate),
        )
        assertEquals(KanjiTabContentState.NoKanji, kana.last().content)

        // JMdict also carries fullwidth latin headwords, which are no
        // more kana than they are kanji.
        val fullwidthLatin = collectStates(
            scope.kanjiTabStateProducer(headword = "ＣＤ", load = neverLoad, invalidate = neverInvalidate),
        )
        assertEquals(KanjiTabContentState.NoKanji, fullwidthLatin.last().content)
    }

    @Test
    fun `two error emissions compare equal so a redundant one can be conflated`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.kanjiTabStateProducer(
                headword = "食",
                load = { throw RuntimeException("database gone") },
                invalidate = {},
            ),
        )
        val first = assertIs<KanjiTabContentState.Error>(states.last().content)

        // A fresh capturing retry lambda per emission would make these
        // unequal, and no conflation could suppress the repeat.
        first.onRetry?.invoke()
        runCurrent()

        assertEquals(first, states.last().content)
    }

    @Test
    fun `a load failure becomes an error body that offers a retry`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.kanjiTabStateProducer(
                headword = "食べ物",
                load = { throw RuntimeException("database gone") },
                invalidate = {},
            ),
        )

        val error = assertIs<KanjiTabContentState.Error>(states.last().content)
        assertNotNull(error.onRetry, "the reader must be able to try again")
    }

    @Test
    fun `retrying reloads and replaces the error with the characters`() = runTest {
        val scope = FakeScreenStateScope()
        var attempts = 0
        val load: suspend (List<String>) -> List<KanjiCharacter> = { literals ->
            attempts++
            if (attempts == 1) throw RuntimeException("database gone") else literals.map { character(it) }
        }

        val states = collectStates(
            scope.kanjiTabStateProducer(headword = "食", load = load, invalidate = {}),
        )
        val error = assertIs<KanjiTabContentState.Error>(states.last().content)

        error.onRetry?.invoke()
        runCurrent()

        assertIs<KanjiTabContentState.Ready>(states.last().content)
        assertEquals(2, attempts)
        assertTrue(
            states.any { it.content is KanjiTabContentState.Loading },
            "the retry should show progress rather than leaving the error standing",
        )
    }

    @Test
    fun `a database failure drops the shared handle so the retry reopens it`() = runTest {
        val scope = FakeScreenStateScope()
        var invalidations = 0

        val states = collectStates(
            scope.kanjiTabStateProducer(
                headword = "食",
                load = { throw RuntimeException("database gone") },
                invalidate = { invalidations++ },
            ),
        )

        assertIs<KanjiTabContentState.Error>(states.last().content)
        assertEquals(1, invalidations)
    }

    @Test
    fun `a programming error leaves the shared handle alone`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.kanjiTabStateProducer(
                headword = "食",
                // Reopening cannot fix a broken invariant, and throwing
                // the handle away would turn one bug into a
                // reprovisioning storm.
                load = { throw IllegalStateException("bad invariant") },
                invalidate = neverInvalidate,
            ),
        )

        assertIs<KanjiTabContentState.Error>(states.last().content)
    }
}
