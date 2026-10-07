package cc.hosaka.okonomi.feature.favourites

import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.user.FakeFavouritesStore
import cc.hosaka.okonomi.user.UserList
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
        strandedCallback(UserList.Favourites, encodeFavourites(listOf(1L, 2L)))
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

        assertNotNull(states.last().onFileImported)(UserList.Favourites, encodeFavourites(listOf(2L, 1L)))
        runCurrent()

        assertEquals(listOf(UserList.Favourites to listOf(2L, 1L)), favourites.replacements)
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

        assertNotNull(states.last().onFileImported)(UserList.Favourites, encodeFavourites(listOf(2L)))
        runCurrent()

        val prompt = assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)
        assertEquals(emptyList(), favourites.replacements, "nothing may be written before the answer")

        prompt.onConfirm()
        runCurrent()

        assertEquals(listOf(UserList.Favourites to listOf(2L)), favourites.replacements)
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

        assertNotNull(states.last().onFileImported)(UserList.Favourites, encodeFavourites(listOf(2L)))
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

        assertNotNull(states.last().onFileImported)(UserList.Favourites, "this is not an export")
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

        assertNotNull(states.last().onFileImported)(UserList.Favourites, """{"version":1,"name":"x","entries":[]}""")
        runCurrent()
        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt).onConfirm()
        runCurrent()

        assertEquals(listOf(UserList.Favourites to emptyList<Long>()), favourites.replacements)
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

    private suspend fun TestScope.historyTab(
        scope: FakeScreenStateScope = FakeScreenStateScope(),
        favourites: FakeFavouritesStore,
        loadRows: suspend (List<Long>) -> List<SearchHit> = rowsOf(setOf(1L, 2L, 3L)),
        invalidate: suspend () -> Unit = neverInvalidate,
    ): List<FavouritesState> {
        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = loadRows,
                invalidate = invalidate,
            ),
        )
        assertNotNull(states.last().onSelectList)(UserList.History)
        runCurrent()
        return states
    }

    @Test
    fun `the tab opens on Favourites and picking History shows its rows newest first`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(3L, 2L))
        val scope = FakeScreenStateScope()
        val states = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L, 3L)),
                invalidate = neverInvalidate,
            ),
        )
        assertEquals(UserList.Favourites, states.last().list)
        assertEquals(
            listOf(1L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
        )

        assertNotNull(states.last().onSelectList)(UserList.History)
        runCurrent()

        assertEquals(UserList.History, states.last().list)
        assertEquals(
            listOf(3L, 2L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
        )
    }

    /**
     * Within a session the pick survives the producer being restarted —
     * which is what happens after five seconds off the tab — and a new
     * process, which is a new scope, opens on Favourites again.
     */
    @Test
    fun `the pick survives a restart of the producer but not a fresh process`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(2L))
        val scope = FakeScreenStateScope()
        historyTab(scope = scope, favourites = favourites)

        val restarted = collectStates(
            scope.favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )
        assertEquals(UserList.History, restarted.last().list)

        val freshProcess = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )
        assertEquals(UserList.Favourites, freshProcess.last().list)
    }

    @Test
    fun `a History row is removed from History and Favourites keeps it`() = runTest {
        // 2 is in both lists, so removing it from History has something
        // in Favourites to wrongly take with it.
        val favourites = FakeFavouritesStore(initial = listOf(1L, 2L), initialHistory = listOf(2L, 3L))
        val states = historyTab(favourites = favourites)

        assertNotNull(states.last().onRemoveEntry)(2L)
        runCurrent()

        assertEquals(listOf(UserList.History to 2L), favourites.removals)
        assertEquals(listOf(3L), favourites.historyEntryIds().first())
        assertEquals(listOf(1L, 2L), favourites.favouriteEntryIds().first())
        assertEquals(
            listOf(3L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
        )
    }

    @Test
    fun `a Favourites row is removed from Favourites and History keeps it`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L, 2L), initialHistory = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )

        assertNotNull(states.last().onRemoveEntry)(1L)
        runCurrent()

        assertEquals(listOf(UserList.Favourites to 1L), favourites.removals)
        assertEquals(listOf(2L), favourites.favouriteEntryIds().first())
        assertEquals(listOf(1L), favourites.historyEntryIds().first())
        assertEquals(
            listOf(2L),
            assertIs<FavouritesContentState.Ready>(states.last().content).hits.map { it.entryId },
        )
    }

    @Test
    fun `an empty History has nothing to clear and a non-empty one does`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = historyTab(favourites = favourites)
        assertNull(states.last().onClearList)

        favourites.recordInHistory(2L)
        runCurrent()

        assertNotNull(states.last().onClearList)
    }

    @Test
    fun `Clear list asks first and confirming empties only the list on show`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(2L))
        val states = historyTab(favourites = favourites)

        assertNotNull(states.last().onClearList)()
        runCurrent()

        val prompt = assertNotNull(states.last().clearPrompt)
        assertEquals(UserList.History, prompt.list)
        assertEquals(emptyList(), favourites.clears, "nothing may be cleared before the answer")

        prompt.onConfirm()
        runCurrent()

        assertEquals(listOf(UserList.History), favourites.clears)
        assertNull(states.last().clearPrompt)
        assertEquals(emptyList(), assertIs<FavouritesContentState.Ready>(states.last().content).hits)
        assertEquals(listOf(1L), favourites.favouriteEntryIds().first())
    }

    @Test
    fun `cancelling Clear list writes nothing`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L)),
                invalidate = neverInvalidate,
            ),
        )

        assertNotNull(states.last().onClearList)()
        runCurrent()
        val prompt = assertNotNull(states.last().clearPrompt)
        assertEquals(UserList.Favourites, prompt.list)

        prompt.onCancel()
        runCurrent()

        assertEquals(emptyList(), favourites.clears)
        assertNull(states.last().clearPrompt)
        assertEquals(listOf(1L), favourites.favouriteEntryIds().first())
    }

    /**
     * The rows left standing through a failed reload belong to one list.
     * Switching to History with the dictionary down must say so, not
     * show the Favourites rows under a History title.
     */
    @Test
    fun `a failure after switching lists never shows the other list's rows`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(2L))
        var attempts = 0
        val states = historyTab(
            favourites = favourites,
            loadRows = { ids ->
                attempts++
                if (attempts == 1) ids.map { hit(it) } else throw RuntimeException("database gone")
            },
            invalidate = {},
        )

        assertTrue(attempts > 1, "History's rows have to have been asked for")
        assertEquals(UserList.History, states.last().list)
        assertIs<FavouritesContentState.Error>(states.last().content)
    }

    /**
     * A pending import is persisted, so it outlives a switch. Its
     * dialog is about Favourites and must not stand over History — and
     * must still be there on the way back, because nothing answered it.
     */
    @Test
    fun `a pending import's warning shows over its own list only`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(2L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L, 3L)),
                invalidate = neverInvalidate,
            ),
        )
        assertNotNull(states.last().onFileImported)(UserList.Favourites, encodeFavourites(listOf(3L)))
        runCurrent()
        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)

        assertNotNull(states.last().onSelectList)(UserList.History)
        runCurrent()
        assertEquals(UserList.History, states.last().list)
        assertNull(states.last().importPrompt)

        assertNotNull(states.last().onSelectList)(UserList.Favourites)
        runCurrent()
        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)
    }

    @Test
    fun `Clear list is not offered while an import is asking`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )
        assertNotNull(states.last().onClearList, "a list with rows can be cleared")

        assertNotNull(states.last().onFileImported)(UserList.Favourites, encodeFavourites(listOf(2L)))
        runCurrent()

        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)
        assertNull(states.last().onClearList)
        assertNull(states.last().clearPrompt)
    }

    /**
     * The other order: a Clear list standing when a file arrives —
     * through a callback an earlier state left standing. The import
     * wins, the clear is dropped rather than queued behind it, and the
     * two dialogs never stand together.
     */
    @Test
    fun `a file arriving over a standing Clear list replaces it`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )
        val strandedImport = assertNotNull(states.last().onFileImported)
        assertNotNull(states.last().onClearList)()
        runCurrent()
        assertNotNull(states.last().clearPrompt)

        strandedImport(UserList.Favourites, encodeFavourites(listOf(2L)))
        runCurrent()

        val prompt = assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)
        assertNull(states.last().clearPrompt)

        prompt.onCancel()
        runCurrent()

        assertNull(states.last().importPrompt)
        assertNull(states.last().clearPrompt, "the dropped clear must not stand up again")
        assertEquals(emptyList(), favourites.clears)
    }

    @Test
    fun `a Clear list standing over a list that empties goes away for good`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(2L))
        val states = historyTab(favourites = favourites)
        assertNotNull(states.last().onClearList)()
        runCurrent()
        assertNotNull(states.last().clearPrompt)

        favourites.removeFromList(UserList.History, 2L)
        runCurrent()
        assertNull(states.last().clearPrompt, "there is nothing left to clear")

        favourites.recordInHistory(3L)
        runCurrent()
        assertNull(states.last().clearPrompt, "a word landing must not raise the old question again")
        assertEquals(emptyList(), favourites.clears)
    }

    /**
     * The guards on the prompt itself, rather than on the menu item that
     * raises it, exist for callbacks an earlier state left standing. A
     * Clear list kept from the Favourites state must not stand over
     * History, where it would read as a question about History.
     */
    @Test
    fun `a stale Clear list for another list does not stand over this one`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(2L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )
        val staleClear = assertNotNull(states.last().onClearList)
        assertNotNull(states.last().onSelectList)(UserList.History)
        runCurrent()

        staleClear()
        runCurrent()

        assertEquals(UserList.History, states.last().list)
        assertNull(states.last().clearPrompt)
    }

    @Test
    fun `a stale Clear list for a list already empty asks nothing`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L)),
                invalidate = neverInvalidate,
            ),
        )
        val staleClear = assertNotNull(states.last().onClearList)
        favourites.removeFromList(UserList.Favourites, 1L)
        runCurrent()

        staleClear()
        runCurrent()

        assertNull(states.last().clearPrompt)
    }

    @Test
    fun `a stale Clear list does not stack on an import's warning`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )
        val staleClear = assertNotNull(states.last().onClearList)
        assertNotNull(states.last().onFileImported)(UserList.Favourites, encodeFavourites(listOf(2L)))
        runCurrent()

        staleClear()
        runCurrent()

        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)
        assertNull(states.last().clearPrompt)
    }

    /**
     * Export on History writes History: every id, newest first, under
     * History's name. The two lists share ids here on purpose — 1 is in
     * both — so a file of the wrong list cannot pass by containing the
     * right ids in another order.
     */
    @Test
    fun `export on History writes every History id in its order under its name`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L, 9L), initialHistory = listOf(3L, 1L, 2L))
        val states = historyTab(favourites = favourites, loadRows = rowsOf(setOf(1L, 2L, 3L, 9L)))

        val json = assertNotNull(states.last().onExportJson)()

        assertEquals(listOf(3L, 1L, 2L), decodeFavourites(json))
        assertTrue(json.contains("\"History\""), json)
    }

    @Test
    fun `a file imported into History asks first and replaces History alone`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(2L))
        val states = historyTab(favourites = favourites, loadRows = rowsOf(setOf(1L, 2L, 7L, 8L)))

        assertNotNull(states.last().onFileImported)(UserList.History, encodeFavourites(listOf(7L, 8L)))
        runCurrent()

        val prompt = assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)
        assertEquals(UserList.History, prompt.list)
        assertEquals(emptyList(), favourites.replacements, "nothing may be written before the answer")

        prompt.onConfirm()
        runCurrent()

        assertEquals(listOf(UserList.History to listOf(7L, 8L)), favourites.replacements)
        assertEquals(listOf(7L, 8L), favourites.historyEntryIds().first())
        assertEquals(listOf(1L), favourites.favouriteEntryIds().first())
    }

    /**
     * Whether to ask first is decided by the list the file lands in. An
     * empty History is written straight through even though Favourites,
     * which is not empty, is the list the state was built on.
     */
    @Test
    fun `a file imported into an empty History is written straight through`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 7L)),
                invalidate = neverInvalidate,
            ),
        )

        assertNotNull(states.last().onFileImported)(UserList.History, encodeFavourites(listOf(7L)))
        runCurrent()

        assertEquals(listOf(UserList.History to listOf(7L)), favourites.replacements)
        assertNull(states.last().importPrompt)
        assertEquals(UserList.History, states.last().list, "the tab shows the list the file landed in")
    }

    /**
     * The file dialog is another activity, and the tab can be rebuilt
     * behind it — on Favourites, which is where a fresh state opens.
     * A file picked for History must still be History's: the question
     * names History, the tab goes to it, and the answer replaces it.
     */
    @Test
    fun `a file for History arriving at a state rebuilt on Favourites still lands in History`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L), initialHistory = listOf(2L))
        historyTab(favourites = favourites)
        val rebuilt = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L, 7L)),
                invalidate = neverInvalidate,
            ),
        )
        assertEquals(UserList.Favourites, rebuilt.last().list)

        assertNotNull(rebuilt.last().onFileImported)(UserList.History, encodeFavourites(listOf(7L)))
        runCurrent()

        assertEquals(UserList.History, rebuilt.last().list)
        val prompt = assertIs<FavouritesImportPrompt.ConfirmOverwrite>(rebuilt.last().importPrompt)
        assertEquals(UserList.History, prompt.list)
        prompt.onConfirm()
        runCurrent()

        assertEquals(listOf(UserList.History to listOf(7L)), favourites.replacements)
        assertEquals(listOf(1L), favourites.favouriteEntryIds().first())
    }

    @Test
    fun `an unreadable file names the list it was offered to`() = runTest {
        val favourites = FakeFavouritesStore(initialHistory = listOf(2L))
        val states = historyTab(favourites = favourites)

        assertNotNull(states.last().onFileImported)(UserList.History, "this is not an export")
        runCurrent()

        assertEquals(UserList.History, assertIs<FavouritesImportPrompt.Unreadable>(states.last().importPrompt).list)
        assertEquals(emptyList(), favourites.replacements)
    }

    /**
     * The store could not say whether Favourites was empty — here it is,
     * which is what a failed read would have reported — so the reader is
     * asked rather than the list replaced.
     */
    @Test
    fun `an import the store cannot decide on asks first and writes nothing`() = runTest {
        val favourites = FakeFavouritesStore(cannotTellEmptiness = true)
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(7L)),
                invalidate = neverInvalidate,
            ),
        )

        assertNotNull(states.last().onFileImported)(UserList.Favourites, encodeFavourites(listOf(7L)))
        runCurrent()

        assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)
        assertEquals(emptyList(), favourites.replacements)
    }

    @Test
    fun `confirming an import twice writes it once`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L, 2L)),
                invalidate = neverInvalidate,
            ),
        )
        assertNotNull(states.last().onFileImported)(UserList.Favourites, encodeFavourites(listOf(2L)))
        runCurrent()
        val prompt = assertIs<FavouritesImportPrompt.ConfirmOverwrite>(states.last().importPrompt)

        prompt.onConfirm()
        prompt.onConfirm()
        runCurrent()

        assertEquals(listOf(UserList.Favourites to listOf(2L)), favourites.replacements)
    }

    @Test
    fun `confirming Clear list twice clears once`() = runTest {
        val favourites = FakeFavouritesStore(initial = listOf(1L))
        val states = collectStates(
            FakeScreenStateScope().favouritesScreenStateProducer(
                favourites = favourites,
                loadRows = rowsOf(setOf(1L)),
                invalidate = neverInvalidate,
            ),
        )
        assertNotNull(states.last().onClearList)()
        runCurrent()
        val prompt = assertNotNull(states.last().clearPrompt)

        prompt.onConfirm()
        prompt.onConfirm()
        runCurrent()

        assertEquals(listOf(UserList.Favourites), favourites.clears)
    }

    /**
     * A list emptied leaves "no rows" standing, and those are not worth
     * keeping up through a failure once the list has words again: they
     * would show the empty-state message over a list that is not empty.
     */
    @Test
    fun `a failed load after a list is emptied and refilled is an error and not the empty state`() = runTest {
        val favourites = FakeFavouritesStore(initialHistory = listOf(1L))
        var failing = false
        val states = historyTab(
            favourites = favourites,
            loadRows = { ids -> if (failing) throw RuntimeException("database gone") else ids.map { hit(it) } },
            invalidate = {},
        )
        assertEquals(1, assertIs<FavouritesContentState.Ready>(states.last().content).hits.size)

        favourites.removeFromList(UserList.History, 1L)
        runCurrent()
        assertEquals(emptyList(), assertIs<FavouritesContentState.Ready>(states.last().content).hits)

        failing = true
        favourites.recordInHistory(2L)
        runCurrent()

        assertIs<FavouritesContentState.Error>(states.last().content)
    }
}
