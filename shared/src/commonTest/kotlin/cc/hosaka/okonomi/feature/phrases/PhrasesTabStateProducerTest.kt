package cc.hosaka.okonomi.feature.phrases

import cc.hosaka.okonomi.db.BreakdownPos
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // The sentence text carries the fixture's words, because the
    // producer now asks the dictionary only about words the scan could
    // place: a fixture whose sentence does not contain its own words
    // would take the "nothing located" path and test nothing.
    private fun sentences(count: Int): List<ExampleSentence> = (1..count).map { index ->
        sentence.copy(id = index.toLong(), japanese = "例文$index。早く食べる。")
    }

    /**
     * The tappable-word rule's lookup, stubbed out: these tests are
     * about paging and failure, and every one of them would otherwise
     * reach for a dictionary a host test has no file for. The rule
     * itself is pinned by [BreakdownWordTappableTest], and the wiring
     * that feeds it by the last test here.
     */
    private val noPos: suspend (List<BreakdownWord>) -> BreakdownPos = { BreakdownPos() }

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
            scope.phrasesTabStateProducer(
                entryId = 1358280L,
                headword = "食べる",
                load = load,
                loadPos = noPos,
                invalidate = neverInvalidate,
            ),
        )

        assertEquals(1358280L, asked)
        val ready = assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals(listOf(sentence), ready.sentences)
        assertEquals(1, loads)

        // A later run of the producer (the tab came back on screen)
        // reuses the persisted sentences instead of querying again.
        collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1358280L,
                headword = "食べる",
                load = load,
                loadPos = noPos,
                invalidate = neverInvalidate,
            ),
        )
        assertEquals(1, loads)
    }

    @Test
    fun `only the first page of a large stored set is shown`() = runTest {
        val scope = FakeScreenStateScope()
        val stored = sentences(50)

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = { stored },
                loadPos = noPos,
                invalidate = neverInvalidate,
            ),
        )

        val ready = assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals(stored.take(PHRASES_PAGE_SIZE), ready.sentences)
        assertNotNull(ready.onShowMore, "there are twenty more to reach")
    }

    @Test
    fun `showing more extends the page without reordering what was already there`() = runTest {
        val scope = FakeScreenStateScope()
        val stored = sentences(50)

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = { stored },
                loadPos = noPos,
                invalidate = neverInvalidate,
            ),
        )
        val first = assertIs<PhrasesTabContentState.Ready>(states.last().content)

        first.onShowMore!!.invoke()
        runCurrent()

        val second = assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals(50, second.sentences.size)
        // A page is a longer prefix of the same ordered list, so a
        // sentence the reader is looking at can never move.
        assertEquals(first.sentences, second.sentences.take(PHRASES_PAGE_SIZE))
        assertNull(second.onShowMore, "the stored set is exhausted")
    }

    @Test
    fun `asking for more twice before the page lands still adds one page`() = runTest {
        val scope = FakeScreenStateScope()
        val stored = sentences(50)

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = { stored },
                loadPos = noPos,
                invalidate = neverInvalidate,
            ),
        )
        val first = assertIs<PhrasesTabContentState.Ready>(states.last().content)

        // The scroll watcher is allowed to fire again before the state
        // it asked for arrives; the callback has to be idempotent.
        first.onShowMore!!.invoke()
        first.onShowMore.invoke()
        runCurrent()

        val second = assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals(50, second.sentences.size)
    }

    @Test
    fun `an entry whose examples all fit offers no way to show more`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = { sentences(2) },
                loadPos = noPos,
                invalidate = neverInvalidate,
            ),
        )

        val ready = assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals(2, ready.sentences.size)
        assertNull(ready.onShowMore)
    }

    @Test
    fun `an entry the corpus never uses is its own state rather than an error`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = { emptyList() },
                loadPos = noPos,
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
                headword = "食べる",
                load = { throw RuntimeException("database gone") },
                loadPos = noPos,
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
                headword = "食べる",
                load = { throw RuntimeException("database gone") },
                loadPos = noPos,
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
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = load,
                loadPos = noPos,
                invalidate = {},
            ),
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
                headword = "食べる",
                load = { throw RuntimeException("database gone") },
                loadPos = noPos,
                invalidate = { invalidations++ },
            ),
        )

        assertIs<PhrasesTabContentState.Error>(states.last().content)
        assertEquals(1, invalidations)
    }

    /**
     * The wiring between the loaded sentences, the part-of-speech
     * lookup and the rule. Every piece is tested on its own; this is
     * the only thing that fails if they stop being joined up, and its
     * symptom on screen is a breakdown where nothing can be tapped.
     */
    @Test
    fun `the words the rule accepts reach the state as tappable`() = runTest {
        val scope = FakeScreenStateScope()
        val particle = BreakdownWord("を", null, entryId = 2_029_010L)
        val verb = BreakdownWord("食べる", "たべる", entryId = 1_358_280L)
        // Named by the index but nowhere in the sentence, so it has no
        // span to tap and the dictionary is never asked about it.
        val absent = BreakdownWord("犬", "いぬ", entryId = 1_226_940L)
        var asked: List<BreakdownWord>? = null

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = {
                    listOf(
                        sentence.copy(
                            japanese = "ご飯を食べる。",
                            words = listOf(particle, absent, verb),
                        ),
                    )
                },
                loadPos = { words ->
                    asked = words
                    BreakdownPos(
                        byEntryId = mapOf(
                            2_029_010L to listOf("prt"),
                            1_358_280L to listOf("v1", "vt"),
                        ),
                        byText = mapOf("を" to listOf("prt"), "食べる" to listOf("v1", "vt")),
                    )
                },
                invalidate = neverInvalidate,
            ),
        )

        // Asked once, for the whole stored set, rather than per word —
        // and only about the words the sentence actually holds.
        assertEquals(listOf(particle, verb), asked)
        val ready = assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals(setOf(verb), ready.tappableWords)
    }

    /**
     * The entry's own dictionary form reaches the state, which is the
     * only thing the producer does with it and the only place the tab
     * can learn it. Its consequence — the word drawn as a content word
     * with no tap on it — is `PhrasesTapUiTest`'s, because it lives
     * inside a composable.
     *
     * Pinned separately from `tappableWords`, which must be unaffected:
     * folding the two together is what once drew the word the reader
     * came to study in the colour reserved for particles.
     */
    @Test
    fun `the entry's own headword reaches the state without changing what is tappable`() = runTest {
        val scope = FakeScreenStateScope()
        val taberu = BreakdownWord("食べる", "たべる", entryId = 1_358_280L)
        val gohan = BreakdownWord("ご飯", "ごはん", entryId = 1_269_500L)

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1_358_280L,
                headword = "食べる",
                load = {
                    listOf(sentence.copy(japanese = "ご飯を食べる。", words = listOf(gohan, taberu)))
                },
                loadPos = {
                    BreakdownPos(
                        byEntryId = mapOf(
                            1_358_280L to listOf("v1", "vt"),
                            1_269_500L to listOf("n"),
                        ),
                        byText = mapOf("食べる" to listOf("v1", "vt"), "ご飯" to listOf("n")),
                    )
                },
                invalidate = neverInvalidate,
            ),
        )

        val ready = assertIs<PhrasesTabContentState.Ready>(states.last().content)
        assertEquals("食べる", ready.wordBeingRead)
        assertEquals(setOf(gohan, taberu), ready.tappableWords, "both are still content words")
    }

    /**
     * A decoration failing must not cost the reader the thing they came
     * for. The sentences are loaded and readable; that the colouring
     * could not be worked out is a reason to colour nothing, not a
     * reason to hide them behind an error and a retry button.
     */
    @Test
    fun `a part of speech failure leaves the sentences on screen with nothing tappable`() = runTest {
        val scope = FakeScreenStateScope()
        var invalidations = 0

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = { listOf(sentence) },
                loadPos = { throw RuntimeException("database gone") },
                // A real database failure still gets the project's
                // standard heal, even though the reader never sees it.
                invalidate = { invalidations++ },
            ),
        )

        val ready = assertIs<PhrasesTabContentState.Ready>(
            states.last().content,
            "the examples loaded; only the colouring did not",
        )
        assertEquals(listOf(sentence), ready.sentences)
        assertTrue(ready.tappableWords.isEmpty(), "nothing may be tappable on evidence never obtained")
        assertEquals(1, invalidations)
    }

    @Test
    fun `an entry the corpus never uses asks the dictionary nothing further`() = runTest {
        val scope = FakeScreenStateScope()

        collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                load = { emptyList() },
                loadPos = { throw AssertionError("nothing to look a part of speech up for") },
                invalidate = neverInvalidate,
            ),
        )
    }

    @Test
    fun `a programming error leaves the shared handle alone`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.phrasesTabStateProducer(
                entryId = 1L,
                headword = "食べる",
                // Reopening cannot fix a broken invariant, and throwing
                // the handle away would turn one bug into a
                // reprovisioning storm.
                load = { throw IllegalStateException("bad invariant") },
                loadPos = noPos,
                invalidate = neverInvalidate,
            ),
        )

        assertIs<PhrasesTabContentState.Error>(states.last().content)
    }
}
