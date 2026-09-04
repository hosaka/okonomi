package cc.hosaka.okonomi.feature.word

import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.db.EntrySense
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.user.FakeFavouritesStore
import cc.hosaka.okonomi.user.FavouritesStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
        readings = listOf(EntryReading("たべる", emptyList(), isCommon = true)),
        senses = listOf(
            EntrySense(
                posCodes = listOf("v1"),
                tags = listOf("Ichidan verb"),
                glosses = listOf("to eat"),
                info = null,
                restrictions = emptyList(),
            ),
        ),
        isCommon = true,
        commonRank = 125,
        hasSentences = true,
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
            scope.entryScreenStateProducer(
                entryId = 1358280,
                load = load,
                invalidate = neverInvalidate,
                favourites = FakeFavouritesStore(),
            ),
        )
        assertEquals(1358280, states.last().entryId)
        assertEquals(detail(1358280), assertIs<EntryContentState.Ready>(states.last().content).entry)
        assertEquals(1, loads)

        // A later run of the producer (the screen came back on screen)
        // reuses the persisted entry instead of querying again.
        val second = collectStates(
            scope.entryScreenStateProducer(
                entryId = 1358280,
                load = load,
                invalidate = neverInvalidate,
                favourites = FakeFavouritesStore(),
            ),
        )
        assertIs<EntryContentState.Ready>(second.last().content)
        assertEquals(1, loads)
    }

    @Test
    fun `an unknown id becomes an error body that offers a retry`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.entryScreenStateProducer(
                entryId = 404,
                load = { null },
                invalidate = neverInvalidate,
                favourites = FakeFavouritesStore(),
            ),
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
            scope.entryScreenStateProducer(
                entryId = 1,
                load = load,
                invalidate = neverInvalidate,
                favourites = FakeFavouritesStore(),
            ),
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
                favourites = FakeFavouritesStore(),
            ),
        )

        assertIs<EntryContentState.Error>(states.last().content)
        assertEquals(1, invalidations)
    }

    @Test
    fun `the save action reports what the store says and writes through it`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore()

        val states = collectStates(
            scope.entryScreenStateProducer(
                entryId = 1358280,
                load = { detail(it) },
                invalidate = neverInvalidate,
                favourites = favourites,
            ),
        )

        assertFalse(states.last().isFavourite, "an entry nobody saved is not saved")
        val toggle = assertNotNull(states.last().onToggleFavourite, "a loaded entry must be savable")

        toggle()
        runCurrent()

        assertEquals(listOf(1358280L), favourites.writes)
        assertTrue(
            states.last().isFavourite,
            "the button follows the store, so a landed write has to come back through it",
        )

        toggle()
        runCurrent()

        assertFalse(states.last().isFavourite)
    }

    /**
     * The entry has to reach the screen even when the store has not
     * answered yet, which is what the seeded `false` in the producer is
     * for. Every other test here hands in a store that already has a
     * value, so `combine` never waits and the seed is deletable with the
     * suite green — this is the one that holds it down.
     */
    @Test
    fun `the entry is shown before storage has said whether it is saved`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.entryScreenStateProducer(
                entryId = 1358280,
                load = { detail(it) },
                invalidate = neverInvalidate,
                favourites = SilentFavouritesStore(),
            ),
        )

        assertIs<EntryContentState.Ready>(
            states.last().content,
            "a store that has not answered must not hold the word hostage",
        )
        assertFalse(states.last().isFavourite, "and it must read as unsaved until it does")
    }

    /**
     * A store that is already holding the entry has to be visible in the
     * first state the screen is shown, not a frame later.
     */
    @Test
    fun `an already-saved entry opens saved`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.entryScreenStateProducer(
                entryId = 1358280,
                load = { detail(it) },
                invalidate = neverInvalidate,
                favourites = FakeFavouritesStore(initial = listOf(1358280L)),
            ),
        )

        assertTrue(states.last().isFavourite)
    }

    /**
     * There is nothing to save on a body that has no entry in it, and a
     * null callback is how this project says an action is unavailable.
     */
    @Test
    fun `an entry that did not load offers no save action`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.entryScreenStateProducer(
                entryId = 404,
                load = { null },
                invalidate = neverInvalidate,
                favourites = FakeFavouritesStore(),
            ),
        )

        assertIs<EntryContentState.Error>(states.last().content)
        assertNull(states.last().onToggleFavourite)
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
                favourites = FakeFavouritesStore(),
            ),
        )

        assertIs<EntryContentState.Error>(states.last().content)
    }
}

/**
 * A store that never answers: its reads emit nothing at all, the way a
 * database file that will not open leaves them. Its writes are recorded
 * and change nothing, because there is nothing here to change.
 */
private class SilentFavouritesStore : FavouritesStore {
    override fun favouriteEntryIds(): Flow<List<Long>> = emptyFlow()

    override fun isFavourite(entryId: Long): Flow<Boolean> = emptyFlow()

    override fun toggleFavourite(entryId: Long) = Unit

    override fun replaceFavourites(entryIds: List<Long>) = Unit
}
