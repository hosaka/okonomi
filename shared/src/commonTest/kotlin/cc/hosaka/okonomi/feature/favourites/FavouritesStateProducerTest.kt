package cc.hosaka.okonomi.feature.favourites

import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.user.FakeFavouritesStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesStateProducerTest {

    private fun hit(entryId: Long) = SearchHit(
        entryId = entryId,
        titleSegments = listOf(TitleSegment("食べる")),
        traceLabels = emptyList(),
        senseLines = listOf("to eat"),
        isCommon = true,
    )

    /** The dictionary as the Favourites tab sees it: ids in, rows out, unknown ids dropped. */
    private fun rowsOf(known: Set<Long>): suspend (List<Long>) -> List<SearchHit> = { ids ->
        ids.filter { it in known }.map { hit(it) }
    }

    private val neverInvalidate: suspend () -> Unit = {
        throw AssertionError("the dictionary handle must not be dropped for this failure")
    }

    private fun TestScope.collectStates(flow: Flow<FavouritesState>): List<FavouritesState> {
        val states = mutableListOf<FavouritesState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { states += it }
        }
        runCurrent()
        return states
    }

    @Test
    fun `nothing saved is a ready empty list that asks the dictionary for nothing`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = FakeFavouritesStore(),
                loadRows = { throw AssertionError("an empty list must never reach the dictionary") },
                invalidate = neverInvalidate,
            ),
        )

        assertEquals(emptyList(), assertIs<FavouritesContentState.Ready>(states.last().content).hits)
    }

    @Test
    fun `saved ids become rows in the order the store gave them`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = FakeFavouritesStore(initial = listOf(2L, 1L)),
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )

        assertEquals(
            listOf(2L, 1L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
        )
    }

    /**
     * The matrix's dangling-id row, from the screen's side: the row is
     * gone and the saved id is not. Both halves are asserted, because
     * dropping the row is only correct if nothing wrote that back.
     */
    @Test
    fun `an id the dictionary no longer carries loses its row and keeps its place in the store`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore(initial = listOf(1L, 9_999_999L))

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L)),
                invalidate = neverInvalidate,
            ),
        )

        assertEquals(
            listOf(1L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
        )
        assertEquals(
            emptyList(),
            favourites.writes,
            "a row that could not be resolved must never delete the reader's saved id",
        )
        assertEquals(
            listOf(1L, 9_999_999L),
            favourites.favouriteEntryIds().first(),
            "and the id must still be stored",
        )
    }

    @Test
    fun `saving something while the tab is open adds its row`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore(initial = listOf(1L))

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )
        assertEquals(1, assertIs<FavouritesContentState.Ready>(states.last().content).hits.size)

        favourites.toggleFavourite(2L)
        runCurrent()

        assertEquals(
            listOf(2L, 1L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
        )
    }

    @Test
    fun `a dictionary failure is an error with a retry and drops the shared handle`() = runTest {
        val scope = FakeScreenStateScope()
        var invalidations = 0
        var attempts = 0

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = FakeFavouritesStore(initial = listOf(1L)),
                loadRows = {
                    attempts++
                    if (attempts == 1) throw RuntimeException("database gone") else listOf(hit(1L))
                },
                invalidate = { invalidations++ },
            ),
        )

        val error = assertIs<FavouritesContentState.Error>(states.last().content)
        assertEquals(1, invalidations)
        val retry = assertNotNull(error.onRetry, "the reader must be able to try again")

        retry()
        runCurrent()

        assertEquals(
            listOf(1L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
        )
    }

    /**
     * A failed reload must not take rows the reader is looking at off the
     * screen. This is the same rule the search results follow, and it
     * matters more here: the list is the whole screen.
     */
    @Test
    fun `a failure after rows have landed leaves them standing`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        var attempts = 0

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = { ids ->
                    attempts++
                    if (attempts == 1) ids.map { hit(it) } else throw RuntimeException("database gone")
                },
                invalidate = { },
            ),
        )
        assertEquals(1, assertIs<FavouritesContentState.Ready>(states.last().content).hits.size)

        favourites.toggleFavourite(2L)
        runCurrent()

        assertTrue(attempts > 1, "the second load has to have been attempted, or nothing failed")
        assertEquals(
            listOf(1L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
            "the rows on screen must survive a failed reload",
        )
    }
}
