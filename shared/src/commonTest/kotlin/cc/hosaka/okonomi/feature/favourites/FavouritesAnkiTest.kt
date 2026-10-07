package cc.hosaka.okonomi.feature.favourites

import cc.hosaka.okonomi.anki.AnkiAccess
import cc.hosaka.okonomi.anki.AnkiExport
import cc.hosaka.okonomi.anki.AnkiNote
import cc.hosaka.okonomi.anki.AnkiSendResult
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.user.FakeFavouritesStore
import cc.hosaka.okonomi.user.UserList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
 * Send to AnkiDroid as the Favourites producer drives it: when it is
 * offered, the permission round trip, one send at a time, and the dialog
 * every outcome ends in. AnkiDroid itself is a fake [AnkiExport]; what a
 * send does inside it is `SendToAnkiDroidTest`'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FavouritesAnkiTest {

    private class FakeAnkiExport(
        var access: AnkiAccess = AnkiAccess.Granted,
        var result: AnkiSendResult = AnkiSendResult.Sent(added = 1, alreadyThere = 0),
    ) : AnkiExport {
        val sent = mutableListOf<List<AnkiNote>>()

        /** When set, a send waits on it, standing in for a slow AnkiDroid. */
        var gate: CompletableDeferred<Unit>? = null
        var failure: Exception? = null

        override fun access(): AnkiAccess = access

        override suspend fun send(notes: List<AnkiNote>): AnkiSendResult {
            sent += notes
            gate?.await()
            failure?.let { throw it }
            return result
        }
    }

    private fun hit(entryId: Long) = SearchHit(
        entryId = entryId,
        titleSegments = listOf(TitleSegment("食べる$entryId"), TitleSegment("たべる", readsPreviousSegment = true)),
        traceLabels = emptyList(),
        senseLines = listOf("to eat"),
        isCommon = true,
    )

    private val rows: suspend (List<Long>) -> List<SearchHit> = { ids -> ids.map { hit(it) } }

    /** More than a row shows, so a card built from the row would visibly lose some. */
    private val glosses: suspend (List<Long>) -> Map<Long, List<String>> = { ids ->
        ids.associateWith { id -> listOf("to eat $id", "to live on $id", "to bite $id", "to be eaten $id") }
    }

    private fun TestScope.collectStates(flow: Flow<FavouritesState>): List<FavouritesState> {
        val states = mutableListOf<FavouritesState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { states += it }
        }
        runCurrent()
        return states
    }

    private suspend fun TestScope.produce(
        scope: FakeScreenStateScope = FakeScreenStateScope(),
        favourites: FakeFavouritesStore = FakeFavouritesStore(listOf(1L, 2L)),
        anki: AnkiExport?,
        loadRows: suspend (List<Long>) -> List<SearchHit> = rows,
        loadGlosses: suspend (List<Long>) -> Map<Long, List<String>> = glosses,
        reports: MutableList<String> = mutableListOf(),
    ): Flow<FavouritesState> = scope.favouritesScreenStateProducer(
        favourites = favourites,
        loadRows = loadRows,
        invalidate = {},
        report = { message, _ -> reports += message },
        anki = anki,
        loadGlosses = loadGlosses,
        ankiScope = backgroundScope,
    )

    @Test
    fun `Favourites offers the send where AnkiDroid exists`() = runTest {
        val states = collectStates(produce(anki = FakeAnkiExport()))

        assertTrue(states.last().showSendToAnki)
        assertNotNull(states.last().onSendToAnki)
    }

    @Test
    fun `History never offers the send`() = runTest {
        val scope = FakeScreenStateScope()
        val states = collectStates(
            produce(scope = scope, favourites = FakeFavouritesStore(initialHistory = listOf(1L)), anki = FakeAnkiExport()),
        )

        states.last().onSelectList!!(UserList.History)
        runCurrent()

        assertEquals(UserList.History, states.last().list)
        assertFalse(states.last().showSendToAnki)
        assertNull(states.last().onSendToAnki)
    }

    @Test
    fun `with no AnkiDroid on the platform the send is not offered at all`() = runTest {
        val states = collectStates(produce(anki = null))

        assertFalse(states.last().showSendToAnki)
        assertNull(states.last().onSendToAnki)
    }

    @Test
    fun `an empty Favourites shows the send disabled`() = runTest {
        val states = collectStates(produce(favourites = FakeFavouritesStore(), anki = FakeAnkiExport()))

        assertTrue(states.last().showSendToAnki)
        assertNull(states.last().onSendToAnki)
    }

    @Test
    fun `rows the dictionary could not load leave nothing to send`() = runTest {
        val states = collectStates(
            produce(anki = FakeAnkiExport(), loadRows = { throw IllegalStateException("unreadable") }),
        )

        assertIs<FavouritesContentState.Error>(states.last().content)
        assertNull(states.last().onSendToAnki)
    }

    @Test
    fun `a send carries every row on show as a note and ends in its dialog`() = runTest {
        val anki = FakeAnkiExport(result = AnkiSendResult.Sent(added = 2, alreadyThere = 0))
        val states = collectStates(produce(anki = anki))

        states.last().onSendToAnki!!()
        runCurrent()

        assertEquals(listOf(listOf("食べる1", "食べる2")), anki.sent.map { notes -> notes.map { it.word } })
        assertEquals(listOf("たべる", "たべる"), anki.sent.single().map { it.reading })
        assertEquals(
            "- to eat 2<br>- to live on 2<br>- to bite 2<br>- to be eaten 2",
            anki.sent.single().last().meaning,
            "a card carries its own entry's glosses, not the row's lines",
        )
        assertEquals(AnkiSendResult.Sent(added = 2, alreadyThere = 0), states.last().ankiPrompt?.result)
    }

    @Test
    fun `a dictionary that cannot give the glosses ends in a failure dialog and sends nothing`() = runTest {
        val anki = FakeAnkiExport()
        val reports = mutableListOf<String>()
        val states = collectStates(
            produce(anki = anki, reports = reports, loadGlosses = { throw IllegalStateException("unreadable") }),
        )

        states.last().onSendToAnki!!()
        runCurrent()

        assertTrue(anki.sent.isEmpty())
        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 2), states.last().ankiPrompt?.result)
        assertEquals(1, reports.size, reports.toString())
    }

    @Test
    fun `while a send is under way it cannot be started again`() = runTest {
        val anki = FakeAnkiExport().apply { gate = CompletableDeferred() }
        val states = collectStates(produce(anki = anki))
        val send = states.last().onSendToAnki!!

        send()
        runCurrent()
        assertNull(states.last().onSendToAnki, "the item must be disabled while a send is in flight")
        // A second tap through a callback that was standing before the
        // first took effect still sends nothing more.
        send()
        runCurrent()
        assertEquals(1, anki.sent.size)
        assertNull(states.last().ankiPrompt)

        anki.gate!!.complete(Unit)
        runCurrent()
        assertNotNull(states.last().ankiPrompt)
    }

    @Test
    fun `dismissing the result offers the send again`() = runTest {
        val states = collectStates(produce(anki = FakeAnkiExport()))
        states.last().onSendToAnki!!()
        runCurrent()
        assertNull(states.last().onSendToAnki)

        states.last().ankiPrompt!!.onDismiss()
        runCurrent()

        assertNull(states.last().ankiPrompt)
        assertNotNull(states.last().onSendToAnki)
    }

    @Test
    fun `without access yet the reader is asked first and nothing is sent until they answer`() = runTest {
        val anki = FakeAnkiExport(access = AnkiAccess.NeedsPermission)
        val states = collectStates(produce(anki = anki))

        states.last().onSendToAnki!!()
        runCurrent()

        assertNotNull(states.last().onAnkiPermissionResult)
        assertNull(states.last().onSendToAnki)
        assertTrue(anki.sent.isEmpty())
        assertNull(states.last().ankiPrompt)
    }

    @Test
    fun `a granted permission goes on to send`() = runTest {
        val anki = FakeAnkiExport(access = AnkiAccess.NeedsPermission)
        val states = collectStates(produce(anki = anki))
        states.last().onSendToAnki!!()
        runCurrent()

        states.last().onAnkiPermissionResult!!(true)
        runCurrent()

        assertEquals(1, anki.sent.size)
        assertNull(states.last().onAnkiPermissionResult)
        assertEquals(anki.result, states.last().ankiPrompt?.result)
    }

    @Test
    fun `a refused permission says so and sends nothing`() = runTest {
        val anki = FakeAnkiExport(access = AnkiAccess.NeedsPermission)
        val states = collectStates(produce(anki = anki))
        states.last().onSendToAnki!!()
        runCurrent()

        states.last().onAnkiPermissionResult!!(false)
        runCurrent()

        assertTrue(anki.sent.isEmpty())
        assertEquals(AnkiSendResult.PermissionDenied, states.last().ankiPrompt?.result)
    }

    @Test
    fun `the permission answer still lands through the state a finished run left behind`() = runTest {
        // The permission dialog is another activity: the tab stops being
        // collected under it and the answer comes back through the last
        // state the cancelled run emitted.
        val scope = FakeScreenStateScope()
        val anki = FakeAnkiExport(access = AnkiAccess.NeedsPermission)
        val firstRun = collectStates(produce(scope = scope, anki = anki))
        firstRun.last().onSendToAnki!!()
        runCurrent()
        val strandedAnswer = firstRun.last().onAnkiPermissionResult
        assertNotNull(strandedAnswer)

        val secondRun = collectStates(produce(scope = scope, anki = anki))
        strandedAnswer(true)
        runCurrent()

        assertEquals(1, anki.sent.size)
        assertEquals(anki.result, secondRun.last().ankiPrompt?.result)
    }

    @Test
    fun `no AnkiDroid to send to is a dialog and no send`() = runTest {
        val anki = FakeAnkiExport(access = AnkiAccess.Unavailable)
        val states = collectStates(produce(anki = anki))

        states.last().onSendToAnki!!()
        runCurrent()

        assertTrue(anki.sent.isEmpty())
        assertNull(states.last().onAnkiPermissionResult)
        assertEquals(AnkiSendResult.Unavailable, states.last().ankiPrompt?.result)
    }

    @Test
    fun `a send that throws despite its promise still ends in a dialog and a report`() = runTest {
        val anki = FakeAnkiExport().apply { failure = IllegalStateException("broken") }
        val reports = mutableListOf<String>()
        val states = collectStates(produce(anki = anki, reports = reports))

        states.last().onSendToAnki!!()
        runCurrent()

        assertEquals(AnkiSendResult.Failed(added = 0, attempted = 2), states.last().ankiPrompt?.result)
        assertEquals(1, reports.size, reports.toString())
    }

    @Test
    fun `a standing result takes Clear list off the menu`() = runTest {
        val states = collectStates(produce(anki = FakeAnkiExport()))
        assertNotNull(states.last().onClearList)

        states.last().onSendToAnki!!()
        runCurrent()

        assertNotNull(states.last().ankiPrompt)
        assertNull(states.last().onClearList)
    }

    @Test
    fun `a standing Clear list question takes the send off the menu`() = runTest {
        val states = collectStates(produce(anki = FakeAnkiExport()))

        states.last().onClearList!!()
        runCurrent()

        assertNotNull(states.last().clearPrompt)
        assertNull(states.last().onSendToAnki)
    }

    @Test
    fun `an import's question stands in front of a send's result`() = runTest {
        val states = collectStates(produce(anki = FakeAnkiExport()))
        states.last().onSendToAnki!!()
        runCurrent()

        states.last().onFileImported!!(UserList.Favourites, "not an export")
        runCurrent()

        assertNotNull(states.last().importPrompt)
        assertNull(states.last().ankiPrompt)

        (states.last().importPrompt as FavouritesImportPrompt.Unreadable).onDismiss()
        runCurrent()

        assertNotNull(states.last().ankiPrompt, "the result waits behind the import rather than being lost")
    }
}
