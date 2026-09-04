package cc.hosaka.okonomi.feature.favourites

import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.user.FakeFavouritesStore
import cc.hosaka.okonomi.user.decodeFavourites
import cc.hosaka.okonomi.user.encodeFavourites
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
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
    fun `a file offered through the state a finished run left behind still raises the warning`() = runTest {
        // The tab stops being collected while the system file dialog
        // stands open, and this producer is cancelled five seconds
        // behind it. The picker returns to the state that cancelled run
        // left standing, so the file arrives through ITS onFileImported
        // — by which time the tab is on a new run. The two runs have to
        // be looking at the same pending import, or the reader picks a
        // file and watches nothing happen.
        //
        // Two runs over one ScreenStateScope is what reproduces that
        // here: the file is handed to the first run's callback and the
        // warning is asserted on the second run's state.
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore(listOf(7L))
        suspend fun run() = scope.favouritesScreenStateProducer(
            favourites = favourites,
            loadRows = rowsOf(setOf(7L)),
            invalidate = neverInvalidate,
            report = { _, _ -> },
        )

        val firstRun = collectStates(run())
        val strandedCallback = firstRun.last().onFileImported
        assertNotNull(strandedCallback)

        val secondRun = collectStates(run())
        strandedCallback(encodeFavourites(listOf(1L, 2L)))
        runCurrent()

        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(secondRun.last().importPrompt)
        // Still nothing written: the warning has not been answered.
        assertTrue(favourites.replacements.isEmpty(), favourites.replacements.toString())
    }

    @Test
    fun `a dictionary failure still leaves the saved ids exportable`() = runTest {
        // The state a reader most needs their words out of the app in.
        // The ids are known whether or not the dictionary can turn them
        // into rows, so the file is writable either way.
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = FakeFavouritesStore(listOf(4L, 9L)),
                loadRows = { throw IllegalStateException("the dictionary is unreadable") },
                invalidate = {},
                report = { _, _ -> },
            ),
        )

        assertIs<FavouritesContentState.Error>(states.last().content)
        val export = states.last().onExportJson
        assertNotNull(export, "a dictionary failure must not take the export with it")
        assertEquals(listOf(4L, 9L), decodeFavourites(export()))
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

    @Test
    fun `nothing saved leaves nothing to export`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = FakeFavouritesStore(),
                loadRows = rowsOf(emptySet()),
                invalidate = neverInvalidate,
            ),
        )

        assertNull(
            states.last().onExportJson,
            "an empty file is not worth a save dialog",
        )
    }

    /**
     * Export is off while the rows have not arrived, and on the moment
     * they have. The first half alone would pass with export never
     * enabled at all, so both are asserted against the same producer.
     */
    @Test
    fun `export is unavailable while the list is loading and available once it is not`() = runTest {
        val scope = FakeScreenStateScope()
        val rows = CompletableDeferred<List<SearchHit>>()

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = FakeFavouritesStore(initial = listOf(1L)),
                loadRows = { rows.await() },
                invalidate = neverInvalidate,
            ),
        )

        assertIs<FavouritesContentState.Loading>(states.last().content)
        assertNull(states.last().onExportJson)

        rows.complete(listOf(hit(1L)))
        runCurrent()

        assertNotNull(states.last().onExportJson, "the list has landed and can be exported")
    }

    /**
     * The export carries the reader's saved ids, not the rows that
     * resolved. A word the bundled dictionary dropped has no row on
     * screen and must still be in the file, or exporting is quietly how
     * a reader loses it.
     */
    @Test
    fun `an id the dictionary no longer carries is still in the exported file`() = runTest {
        val scope = FakeScreenStateScope()

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = FakeFavouritesStore(initial = listOf(1L, 9_999_999L)),
                loadRows = rowsOf(setOf(1L)),
                invalidate = neverInvalidate,
            ),
        )

        val export = assertNotNull(states.last().onExportJson)

        assertEquals(
            listOf(1L, 9_999_999L),
            decodeFavourites(export()),
            "the file is what is saved, not what could be drawn",
        )
    }

    @Test
    fun `a file imported into an empty list is written straight through`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore()

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(2L, 1L)),
                invalidate = neverInvalidate,
            ),
        )

        assertNotNull(states.last().onFileImported)(encodeFavourites(listOf(2L, 1L)))
        runCurrent()

        assertEquals(listOf(listOf(2L, 1L)), favourites.replacements)
        assertNull(states.last().importPrompt, "there was nothing to warn about")
    }

    @Test
    fun `a file imported over a list that is not empty warns before writing anything`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore(initial = listOf(1L))

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )

        assertNotNull(states.last().onFileImported)(encodeFavourites(listOf(2L)))
        runCurrent()

        val prompt = assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)
        assertEquals(emptyList(), favourites.replacements, "nothing may be written before the answer")

        prompt.onConfirm()
        runCurrent()

        assertEquals(listOf(listOf(2L)), favourites.replacements)
        assertNull(states.last().importPrompt, "the dialog has to go away once it is answered")
    }

    @Test
    fun `cancelling the warning writes nothing and leaves the list alone`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore(initial = listOf(1L))

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )

        assertNotNull(states.last().onFileImported)(encodeFavourites(listOf(2L)))
        runCurrent()
        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt).onCancel()
        runCurrent()

        assertEquals(emptyList(), favourites.replacements)
        assertNull(states.last().importPrompt)
        assertEquals(
            listOf(1L),
            favourites.favouriteEntryIds().first(),
            "what was saved has to still be saved",
        )
    }

    @Test
    fun `a file that cannot be read is reported and writes nothing`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val reports = mutableListOf<String>()

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L)),
                invalidate = neverInvalidate,
                report = { message, _ -> reports += message },
            ),
        )

        assertNotNull(states.last().onFileImported)("this is not an export")
        runCurrent()

        val prompt = assertIs<FavouritesImportPrompt.Unreadable>(states.last().importPrompt)
        assertEquals(emptyList(), favourites.replacements)
        assertTrue(
            reports.any { it.contains("could not be read") },
            "a refused file is invisible to a bug report unless it is reported",
        )

        prompt.onDismiss()
        runCurrent()

        assertNull(states.last().importPrompt)
    }

    /** Empty is a list, not a refusal: importing one is how a reader clears the tab. */
    @Test
    fun `an import of an empty list empties the tab once it is confirmed`() = runTest {
        val scope = FakeScreenStateScope()
        val favourites = FakeFavouritesStore(initial = listOf(1L))

        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L)),
                invalidate = neverInvalidate,
            ),
        )

        assertNotNull(states.last().onFileImported)("""{"version":1,"name":"x","entries":[]}""")
        runCurrent()
        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt).onConfirm()
        runCurrent()

        assertEquals(listOf(emptyList()), favourites.replacements)
        assertEquals(emptyList(), assertIs<FavouritesContentState.Ready>(states.last().content).hits)
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
