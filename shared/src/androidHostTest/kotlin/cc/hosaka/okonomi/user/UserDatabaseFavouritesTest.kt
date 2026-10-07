package cc.hosaka.okonomi.user

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.hosaka.okonomi.db.awaitList
import cc.hosaka.okonomi.db.awaitOne
import cc.hosaka.okonomi.db.awaitOneOrNull
import cc.hosaka.okonomi.user.db.UserDb
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The real storage behind the save button.
 *
 * Everything above this is tested against a fake, which cannot fail the
 * way a file can. What has to be proved here is what the seam promises:
 * a save lands and survives the process, saving twice leaves one row, a
 * store nobody can open reads as empty rather than throwing, writes keep
 * their order, and no single failed write can stop the ones after it.
 *
 * The store's scope is the test's own throughout. That is not tidiness:
 * writes run on it, so a write that threw would otherwise land on a
 * scope no test observes — and on Android the default handler for that
 * is process death, exactly where a host test cannot see it.
 *
 * JDBC rather than the app's own driver, for the reason every other
 * database test in this module uses it: these cases are about the
 * queries and the writer, and a synchronous driver makes them quick.
 * `UserDatabaseOpenTest` runs the app's actual opener, which is a
 * separate claim and was for a while a completely untested one.
 */
class UserDatabaseFavouritesTest {

    private val tempDirs = mutableListOf<File>()
    private val opened = mutableListOf<UserDatabase>()

    @AfterTest
    fun cleanUp() {
        opened.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempFile(): File =
        Files.createTempDirectory("userdb").toFile()
            .also { tempDirs += it }
            .resolve(USER_DB_NAME)

    private suspend fun openOver(file: File): UserDatabase {
        // Created only for a file that is not there yet, so reopening the
        // same one is a reopen rather than a second create.
        val fresh = !file.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        if (fresh) {
            UserDb.Schema.create(driver).await()
        }
        return UserDatabase(UserDb(driver), driver).also { opened += it }
    }

    private suspend fun UserDatabase.storedEntryIds(
        list: UserList = UserList.Favourites,
    ): List<Long> {
        val listId = db.listQueries.listBySlug(list.slug).awaitOneOrNull()?.id
            ?: return emptyList()
        return db.list_entryQueries.entriesInList(listId).awaitList()
    }

    private fun storeOver(
        database: UserDatabase,
        scope: CoroutineScope,
        report: UserDataFailureReporter = { _, _ -> },
    ) = UserDatabaseFavourites(
        database = { database },
        now = { 1L },
        report = report,
        scope = scope,
    )

    @Test
    fun `a saved entry comes back and reaches the file`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(42L)

        assertEquals(listOf(42L), store.favouriteEntryIds().first { it.isNotEmpty() })
        assertTrue(store.isFavourite(42L).first())
        assertEquals(
            listOf(42L),
            database.storedEntryIds(),
            "the row has to be in the database, not merely in the flow",
        )
    }

    @Test
    fun `toggling a saved entry takes it out of the list`() = runTest {
        val store = storeOver(openOver(tempFile()), backgroundScope)

        store.toggleFavourite(42L)
        store.favouriteEntryIds().first { it.isNotEmpty() }

        store.toggleFavourite(42L)

        assertEquals(emptyList(), store.favouriteEntryIds().first { it.isEmpty() })
        assertFalse(store.isFavourite(42L).first())
    }

    /**
     * The ordering guarantee the write queue exists for, and the reason
     * the toggle decides inside its own transaction.
     *
     * Two taps in quick succession is one gesture a reader really makes.
     * If the two writes ran on scopes of their own they could both read
     * "not saved" and both save, and the word would end up saved when
     * the reader put it back. One writer, in order, ends where it
     * started.
     */
    @Test
    fun `two taps in a row leave the word exactly as it was`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(42L)
        store.toggleFavourite(42L)
        // A third, distinguishable write, so the read below cannot be the
        // state from before the pair was processed.
        store.toggleFavourite(7L)
        store.favouriteEntryIds().first { it == listOf(7L) }

        assertEquals(listOf(7L), database.storedEntryIds())
    }

    @Test
    fun `three taps in a row leave the word saved`() = runTest {
        val store = storeOver(openOver(tempFile()), backgroundScope)

        store.toggleFavourite(42L)
        store.toggleFavourite(42L)
        store.toggleFavourite(42L)

        assertEquals(listOf(42L), store.favouriteEntryIds().first { it.isNotEmpty() })
    }

    /**
     * A duplicate save is a no-op, not a re-save. Row count alone does not
     * say that — the primary key would hold either way — so the entries
     * around it are what make the assertion: a word saved again must not
     * jump back to the top of the list the reader is looking at.
     *
     * Reached through the store's own front door: two toggles put 42 back
     * where it was, and only then does the third one re-save it.
     */
    @Test
    fun `re-saving an entry puts it back in the place it already had`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(42L)
        store.favouriteEntryIds().first { it == listOf(42L) }
        store.toggleFavourite(7L)
        store.favouriteEntryIds().first { it == listOf(7L, 42L) }

        // Off and on again: the row is written afresh, and the fresh one
        // must take the newest position rather than the old one.
        store.toggleFavourite(42L)
        store.favouriteEntryIds().first { it == listOf(7L) }
        store.toggleFavourite(42L)
        store.favouriteEntryIds().first { it.size == 2 }

        assertEquals(
            listOf(42L, 7L),
            database.storedEntryIds(),
            "a word saved again is a new save and belongs at the top",
        )
    }

    /**
     * Import, against the file rather than against the flow. The order
     * is the assertion that matters: the file's first id has to read
     * back first, which is a claim about the `ord` the writer computes
     * and about `entriesInList`'s `ORDER BY ord DESC` agreeing with it.
     */
    @Test
    fun `an import replaces the stored list and keeps the order it was given`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(42L)
        store.favouriteEntryIds().first { it == listOf(42L) }

        store.replaceList(UserList.Favourites, listOf(7L, 8L, 9L))

        assertEquals(listOf(7L, 8L, 9L), store.favouriteEntryIds().first { it.size == 3 })
        assertEquals(
            listOf(7L, 8L, 9L),
            database.storedEntryIds(),
            "the imported rows have to be in the database, and 42 has to be gone from it",
        )
    }

    @Test
    fun `an import empties only the list it is importing into`() = runTest {
        // clearList is an unqualified DELETE with a list_id predicate,
        // and this database ships exactly one list, so every other test
        // here would stay green if that predicate were dropped — while
        // an import silently emptied every list the reader owns. Named
        // lists are planned, so the guard is written before they arrive
        // rather than after the first import destroys one.
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)
        val other = database.db.listQueries.let { lists ->
            lists.insertList(slug = "another", name = "Another", ord = 1, created_at = 1L)
            lists.listBySlug("another").awaitOne().id
        }
        database.db.list_entryQueries.addToList(
            list_id = other,
            entry_id = 99L,
            ord = 1L,
            created_at = 1L,
        )

        store.toggleFavourite(42L)
        store.favouriteEntryIds().first { it == listOf(42L) }
        store.replaceList(UserList.Favourites, listOf(7L))

        assertEquals(listOf(7L), store.favouriteEntryIds().first { it == listOf(7L) })
        assertEquals(
            listOf(99L),
            database.db.list_entryQueries.entriesInList(other).awaitList(),
            "the other list must not have been touched by an import into Favourites",
        )
    }

    @Test
    fun `importing an empty list empties the stored list`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(42L)
        store.favouriteEntryIds().first { it.isNotEmpty() }

        store.replaceList(UserList.Favourites, emptyList())

        assertEquals(emptyList(), store.favouriteEntryIds().first { it.isEmpty() })
        assertEquals(emptyList(), database.storedEntryIds())
    }

    /**
     * A file may repeat an id. `addToList` is INSERT OR IGNORE against
     * the `(list_id, entry_id)` primary key, so the second one is a
     * no-op and the first keeps its higher `ord` — which is to say its
     * earlier place in the list.
     */
    @Test
    fun `a repeated id in an import is stored once, where it first appeared`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.replaceList(UserList.Favourites, listOf(5L, 5L, 9L))

        assertEquals(listOf(5L, 9L), store.favouriteEntryIds().first { it.isNotEmpty() })
        assertEquals(listOf(5L, 9L), database.storedEntryIds())
    }

    @Test
    fun `an imported list survives the store being rebuilt over the same file`() = runTest {
        val file = tempFile()
        storeOver(openOver(file), backgroundScope).let { first ->
            first.replaceList(UserList.Favourites, listOf(3L, 2L, 1L))
            first.favouriteEntryIds().first { it.isNotEmpty() }
        }

        val reopened = storeOver(openOver(file), backgroundScope)

        assertEquals(listOf(3L, 2L, 1L), reopened.favouriteEntryIds().first())
    }

    /**
     * The reason an import is queued behind the same writer as a heart
     * tap rather than launched on its own.
     *
     * A tap that lands before an import must be replaced by it, and a
     * tap that lands after must be applied on top of what it wrote. On
     * separate scopes the two could reach SQLite in either order, and
     * the reader's saved word would survive or not by luck. Asserted on
     * disk, because the flow could be showing either write's result
     * while the other is still in the queue.
     */
    @Test
    fun `an import and the taps around it land in the order they were asked`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(1L)
        store.replaceList(UserList.Favourites, listOf(7L, 8L))
        store.toggleFavourite(9L)

        store.favouriteEntryIds().first { it.size == 3 }

        assertEquals(
            listOf(9L, 7L, 8L),
            database.storedEntryIds(),
            "the tap before the import must be gone, and the one after it must sit on top",
        )
    }

    @Test
    fun `an import that cannot reach storage is reported and leaves the list alone`() = runTest {
        val database = openOver(tempFile())
        // Fail every open until the import has been reported, rather
        // than failing "the first call". Which caller reaches the opener
        // first is not this test's to decide — nothing collects the read
        // flow before the assertions below, but that is a property of
        // the test rather than of the store, and counting calls quietly
        // depended on it.
        val storageGone = MutableStateFlow(true)
        val reports = MutableStateFlow(emptyList<String>())
        val store = UserDatabaseFavourites(
            database = { if (storageGone.value) error("storage is gone") else database },
            now = { 1L },
            report = { message, _ -> reports.value = reports.value + message },
            scope = backgroundScope,
        )

        store.replaceList(UserList.Favourites, listOf(7L, 8L))
        // Waiting on the report rather than on the opener: the opener is
        // entered before it throws, so a counter can be satisfied while
        // the failure it is standing in for has not been recorded yet.
        reports.first { messages ->
            messages.any { it.contains("import") && it.contains("could not be written") }
        }
        // The import has been through the opener and failed; everything
        // after this sees a working database.
        storageGone.value = false
        store.toggleFavourite(2L)

        assertEquals(
            listOf(2L),
            store.favouriteEntryIds().first { it.isNotEmpty() },
            "the failed import must be dropped and the next write must still land",
        )
    }

    @Test
    fun `saved entries survive the store being rebuilt over the same file`() = runTest {
        val file = tempFile()
        storeOver(openOver(file), backgroundScope).let { first ->
            first.toggleFavourite(42L)
            first.favouriteEntryIds().first { it.isNotEmpty() }
        }

        val reopened = storeOver(openOver(file), backgroundScope)

        assertEquals(listOf(42L), reopened.favouriteEntryIds().first())
    }

    /**
     * The matrix's "unreadable store yields an empty list, not a crash".
     * This reads as an assertion about a default, and is not: without the
     * catch the flow does not emit an empty list, it throws out of the
     * screen's state producer, and `first()` fails the test.
     *
     * The report is asserted alongside, because an empty list is
     * otherwise the same answer as an empty store — to the reader and to
     * a bug report.
     */
    @Test
    fun `a store that cannot be opened reads as empty rather than throwing`() = runTest {
        val reports = mutableListOf<String>()
        val store = UserDatabaseFavourites(
            database = { error("no database here") },
            now = { 1L },
            report = { message, _ -> reports += message },
            scope = backgroundScope,
        )

        assertEquals(emptyList(), store.favouriteEntryIds().first())
        assertFalse(store.isFavourite(42L).first())
        assertTrue(
            reports.any { it.contains("could not be read") },
            "a store that has stopped working must not be silently indistinguishable from an empty one",
        )
    }

    /**
     * A write that cannot reach storage is dropped — and, the part that
     * actually needs proving, the writer survives it. Without the catch
     * the failed write kills the writer coroutine and every later save is
     * silently lost, which is the same defect the preference store's own
     * swallowed-write test exists for.
     */
    @Test
    fun `a write that cannot reach storage does not stop the next one`() = runTest {
        val database = openOver(tempFile())
        // Which call fails is fixed rather than timed: the writer is the
        // only thing asking for the database while the first save is in
        // flight, so "the first call" is exactly "the first write".
        val calls = MutableStateFlow(0)
        val reports = mutableListOf<String>()
        val store = UserDatabaseFavourites(
            database = {
                calls.value++
                if (calls.value == 1) error("storage is gone") else database
            },
            now = { 1L },
            report = { message, _ -> reports += message },
            scope = backgroundScope,
        )

        store.toggleFavourite(1L)
        calls.first { it >= 1 }
        store.toggleFavourite(2L)

        assertEquals(
            listOf(2L),
            store.favouriteEntryIds().first { it.isNotEmpty() },
            "the failed save must be dropped and the next one must still land",
        )
        assertTrue(reports.any { it.contains("could not be written") })
    }

    /**
     * The writer is a single `for` over a channel, and a loop that ends
     * never starts again. If one write throws something the per-write
     * catch does not cover — an `Error` — the drain stops, `trySend`
     * keeps succeeding, and every save for the rest of the process is
     * accepted and lost, with the button refusing and nothing to say
     * why. So it has to come back.
     */
    @Test
    fun `a writer felled by an Error comes back and drains what follows`() = runTest {
        val database = openOver(tempFile())
        val calls = MutableStateFlow(0)
        val reports = mutableListOf<String>()
        val store = UserDatabaseFavourites(
            database = {
                calls.value++
                if (calls.value == 1) throw OutOfMemoryError("pretend") else database
            },
            now = { 1L },
            report = { message, _ -> reports += message },
            scope = backgroundScope,
        )

        store.toggleFavourite(1L)
        calls.first { it >= 1 }
        store.toggleFavourite(2L)

        assertEquals(
            listOf(2L),
            store.favouriteEntryIds().first { it.isNotEmpty() },
            "a writer that died takes every later save with it",
        )
        assertTrue(reports.any { it.contains("restarted") })
    }

    /**
     * The queue is bounded so that a writer which has stopped draining
     * refuses instead of accepting for ever. An unbounded queue cannot
     * refuse, and a save nobody can refuse is a save nobody can report.
     */
    @Test
    fun `a queue that is not draining reports the changes it had to drop`() = runTest {
        val database = openOver(tempFile())
        val blocked = CompletableDeferred<Unit>()
        val reports = mutableListOf<String>()
        val store = UserDatabaseFavourites(
            database = {
                blocked.await()
                database
            },
            now = { 1L },
            report = { message, _ -> reports += message },
            scope = backgroundScope,
        )

        // One write is taken off the queue and wedged in `database()`;
        // the queue then fills behind it. The exact capacity is not the
        // point and is deliberately not restated here.
        repeat(200) { index -> store.toggleFavourite(index.toLong()) }

        assertTrue(
            reports.any { it.contains("dropped") },
            "a queue that stopped draining must say what it lost, not accept it silently",
        )

        blocked.complete(Unit)
    }

    @Test
    fun `a recorded word is at the top of History and on disk`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.recordInHistory(1L)
        store.recordInHistory(2L)

        assertEquals(listOf(2L, 1L), store.historyEntryIds().first { it.size == 2 })
        assertEquals(listOf(2L, 1L), database.storedEntryIds(UserList.History))
        assertEquals(
            emptyList(),
            database.storedEntryIds(UserList.Favourites),
            "opening a word is not saving it",
        )
    }

    /**
     * The opposite of a re-save in Favourites, which keeps its place
     * (see `re-saving an entry puts it back in the place it already
     * had`). 1 is recorded first, so without the move it would read
     * back last.
     */
    @Test
    fun `reopening a word moves it to the top and leaves one row`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.recordInHistory(1L)
        store.recordInHistory(2L)
        store.recordInHistory(3L)
        store.historyEntryIds().first { it.size == 3 }

        store.recordInHistory(1L)

        assertEquals(listOf(1L, 3L, 2L), store.historyEntryIds().first { it.first() == 1L })
        assertEquals(listOf(1L, 3L, 2L), database.storedEntryIds(UserList.History))
    }

    /**
     * History has no cap (Alex, 2026-10-07): an export has to carry
     * every word. Well past the 200 it used to stop at, the oldest word
     * is still there, last.
     */
    @Test
    fun `History keeps every word however many are recorded`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)
        val count = 250

        // Each record is waited for, because the write queue is bounded
        // and would drop most of a burst this size.
        repeat(count) { index ->
            store.recordInHistory(index.toLong())
            store.historyEntryIds().first { it.firstOrNull() == index.toLong() }
        }

        assertEquals(
            (count - 1L downTo 0L).toList(),
            database.storedEntryIds(UserList.History),
        )
    }

    @Test
    fun `an import into History replaces History and leaves Favourites alone`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)
        store.toggleFavourite(1L)
        store.recordInHistory(2L)
        store.historyEntryIds().first { it == listOf(2L) }

        store.replaceList(UserList.History, listOf(7L, 8L, 9L))

        assertEquals(listOf(7L, 8L, 9L), store.historyEntryIds().first { it.size == 3 })
        assertEquals(listOf(7L, 8L, 9L), database.storedEntryIds(UserList.History))
        assertEquals(listOf(1L), database.storedEntryIds(UserList.Favourites))
    }

    @Test
    fun `an import into Favourites leaves History alone`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)
        store.toggleFavourite(1L)
        store.recordInHistory(2L)
        store.historyEntryIds().first { it == listOf(2L) }

        store.replaceList(UserList.Favourites, listOf(7L))

        assertEquals(listOf(7L), store.favouriteEntryIds().first { it == listOf(7L) })
        assertEquals(listOf(2L), database.storedEntryIds(UserList.History))
    }

    @Test
    fun `removing a word from History leaves the same word in Favourites`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(1L)
        store.recordInHistory(1L)
        store.recordInHistory(2L)
        store.historyEntryIds().first { it.size == 2 }

        store.removeFromList(UserList.History, 1L)

        assertEquals(listOf(2L), store.historyEntryIds().first { it.size == 1 })
        assertEquals(listOf(2L), database.storedEntryIds(UserList.History))
        assertEquals(listOf(1L), database.storedEntryIds(UserList.Favourites))
    }

    @Test
    fun `removing a word from Favourites leaves the same word in History`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(1L)
        store.toggleFavourite(2L)
        store.recordInHistory(1L)
        store.historyEntryIds().first { it.isNotEmpty() }

        store.removeFromList(UserList.Favourites, 1L)

        assertEquals(listOf(2L), store.favouriteEntryIds().first { it == listOf(2L) })
        assertEquals(listOf(2L), database.storedEntryIds(UserList.Favourites))
        assertEquals(listOf(1L), database.storedEntryIds(UserList.History))
    }

    /**
     * A remove is not a toggle. Two swipes on one row — or a swipe that
     * lands after the heart already unsaved the word — must leave it
     * gone, not put it back. The marker write after it is what proves
     * the remove was processed before the read.
     */
    @Test
    fun `removing a word that is not there leaves it not there`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(1L)
        store.favouriteEntryIds().first { it == listOf(1L) }
        store.removeFromList(UserList.Favourites, 1L)
        store.removeFromList(UserList.Favourites, 1L)
        store.toggleFavourite(9L)

        store.favouriteEntryIds().first { 9L in it }
        assertEquals(listOf(9L), database.storedEntryIds(UserList.Favourites))
    }

    @Test
    fun `clearing History leaves Favourites untouched`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(1L)
        store.recordInHistory(2L)
        store.historyEntryIds().first { it.isNotEmpty() }

        store.clearList(UserList.History)

        assertEquals(emptyList(), store.historyEntryIds().first { it.isEmpty() })
        assertEquals(emptyList(), database.storedEntryIds(UserList.History))
        assertEquals(listOf(1L), database.storedEntryIds(UserList.Favourites))
    }

    @Test
    fun `clearing Favourites leaves History untouched`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.toggleFavourite(1L)
        store.recordInHistory(2L)
        store.favouriteEntryIds().first { it.isNotEmpty() }

        store.clearList(UserList.Favourites)

        assertEquals(emptyList(), store.favouriteEntryIds().first { it.isEmpty() })
        assertEquals(emptyList(), database.storedEntryIds(UserList.Favourites))
        assertEquals(listOf(2L), database.storedEntryIds(UserList.History))
    }

    /**
     * History is a list row of its own, created on first use with the
     * slug every read looks it up by. Asserted on the row rather than
     * through the store, which would find it by that same slug either
     * way.
     */
    @Test
    fun `the first record creates the History list row`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.recordInHistory(1L)
        store.historyEntryIds().first { it.isNotEmpty() }

        val row = database.db.listQueries.listBySlug(HISTORY_LIST_SLUG).awaitOne()
        assertEquals(HISTORY_LIST_NAME, row.name)
        assertEquals(1L, row.ord)
    }

    /**
     * What this proves is the report and nothing more. The opener
     * throws before any transaction starts, so the list being left
     * alone is not something this failure could have got wrong — and
     * asserting it would be an assertion that cannot fail.
     */
    @Test
    fun `a clear that cannot reach storage is reported`() = runTest {
        val reports = MutableStateFlow(emptyList<String>())
        val store = UserDatabaseFavourites(
            database = { error("storage is gone") },
            now = { 1L },
            report = { message, _ -> reports.value = reports.value + message },
            scope = backgroundScope,
        )

        store.clearList(UserList.History)

        reports.first { messages -> messages.any { it.contains("clear") && it.contains("could not be written") } }
    }

    /**
     * A remove that finds nothing to remove is not a write: it must not
     * move the list's `updated_at`, which an export or a later sort may
     * read as "changed". The record into History afterwards is the
     * marker that the remove has been through the writer.
     *
     * Only `updated_at` is asserted. The revision bump sits behind the
     * same "changed" answer, but nothing outside the store can see it:
     * the reads drop an emission equal to the last one, so a needless
     * bump and none look the same from every flow.
     */
    @Test
    fun `removing a word that is not there touches nothing`() = runTest {
        val database = openOver(tempFile())
        var clock = 0L
        val store = UserDatabaseFavourites(
            database = { database },
            now = { ++clock },
            report = { _, _ -> },
            scope = backgroundScope,
        )
        store.toggleFavourite(1L)
        store.favouriteEntryIds().first { it == listOf(1L) }
        val before = database.db.listQueries.listBySlug(FAVOURITES_LIST_SLUG).awaitOne().updated_at

        store.removeFromList(UserList.Favourites, 2L)
        store.recordInHistory(3L)
        store.historyEntryIds().first { it == listOf(3L) }

        assertEquals(
            before,
            database.db.listQueries.listBySlug(FAVOURITES_LIST_SLUG).awaitOne().updated_at,
            "a remove that changed nothing must not stamp the list as changed",
        )
    }

    private suspend fun UserDatabase.updatedAt(list: UserList): Long =
        db.listQueries.listBySlug(list.slug).awaitOne().updated_at

    @Test
    fun `clearing a list that is already empty touches nothing`() = runTest {
        val database = openOver(tempFile())
        var clock = 0L
        val store = UserDatabaseFavourites(
            database = { database },
            now = { ++clock },
            report = { _, _ -> },
            scope = backgroundScope,
        )
        // Saved and unsaved: the list row exists and holds nothing.
        store.toggleFavourite(1L)
        store.favouriteEntryIds().first { it == listOf(1L) }
        store.toggleFavourite(1L)
        store.favouriteEntryIds().first { it.isEmpty() }
        val before = database.updatedAt(UserList.Favourites)

        store.clearList(UserList.Favourites)
        store.recordInHistory(3L)
        store.historyEntryIds().first { it == listOf(3L) }

        assertEquals(before, database.updatedAt(UserList.Favourites))
    }

    /**
     * A list row is created by the first thing put into the list, never
     * by taking something out of a list that was never there. The
     * Favourites save afterwards is the marker that both writes have
     * been through the writer.
     */
    @Test
    fun `removing from or clearing a list that does not exist yet creates nothing`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.removeFromList(UserList.History, 1L)
        store.clearList(UserList.History)
        store.toggleFavourite(9L)
        store.favouriteEntryIds().first { it == listOf(9L) }

        assertEquals(null, database.db.listQueries.listBySlug(HISTORY_LIST_SLUG).awaitOneOrNull())
    }

    @Test
    fun `an import into History stores a repeated id once where it first appeared`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)

        store.replaceList(UserList.History, listOf(5L, 9L, 5L, 3L))

        assertEquals(listOf(5L, 9L, 3L), store.historyEntryIds().first { it.isNotEmpty() })
        assertEquals(listOf(5L, 9L, 3L), database.storedEntryIds(UserList.History))
    }

    /**
     * The file's first id is the newest word, so it has to come back
     * first — ahead of a word recorded before the import, which the
     * import replaces.
     */
    @Test
    fun `an import into History puts the file's first id first`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)
        store.recordInHistory(1L)
        store.historyEntryIds().first { it == listOf(1L) }

        store.replaceList(UserList.History, listOf(3L, 2L, 4L))

        assertEquals(listOf(3L, 2L, 4L), store.historyEntryIds().first { it.firstOrNull() == 3L })
        assertEquals(listOf(3L, 2L, 4L), database.storedEntryIds(UserList.History))
    }

    @Test
    fun `a conditional import into an empty list replaces it without a word`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)
        var declined = false

        store.replaceListIfEmpty(UserList.History, listOf(7L, 8L)) { declined = true }

        assertEquals(listOf(7L, 8L), store.historyEntryIds().first { it.isNotEmpty() })
        assertFalse(declined)
    }

    /**
     * The decision is the writer's, so a write queued ahead of the
     * import counts even though no read has seen it land: the word
     * recorded first makes History not empty.
     */
    @Test
    fun `a conditional import behind a queued record declines and writes nothing`() = runTest {
        val database = openOver(tempFile())
        val store = storeOver(database, backgroundScope)
        val declined = CompletableDeferred<Unit>()

        store.recordInHistory(1L)
        store.replaceListIfEmpty(UserList.History, listOf(7L)) { declined.complete(Unit) }
        assertTrue(withTimeoutOrNull(1.seconds) { declined.await() } != null, "History was not empty")

        assertEquals(listOf(1L), database.storedEntryIds(UserList.History))
    }

    /**
     * A store that cannot answer must not be taken for an empty one: a
     * read that fails reads as empty everywhere else in this class, and
     * here that would overwrite the reader's list without asking. What
     * is asserted is the decline arriving, which is what makes the
     * caller ask; nothing about the list is, because a store that cannot
     * be reached could not have replaced it either way.
     */
    @Test
    fun `a conditional import that cannot reach storage declines rather than replacing`() = runTest {
        val declined = CompletableDeferred<Unit>()
        val store = UserDatabaseFavourites(
            database = { error("storage is gone") },
            now = { 1L },
            report = { _, _ -> },
            scope = backgroundScope,
        )

        store.replaceListIfEmpty(UserList.Favourites, listOf(7L)) { declined.complete(Unit) }

        assertTrue(
            withTimeoutOrNull(1.seconds) { declined.await() } != null,
            "a failed conditional import must be answered with a decline",
        )
    }

    /**
     * A conditional import the queue had no room for is a decision
     * nobody made. It is answered with a decline on the spot, so the
     * reader is asked instead of the file vanishing.
     */
    @Test
    fun `a conditional import the full queue refuses is declined at once`() = runTest {
        val database = openOver(tempFile())
        val blocked = CompletableDeferred<Unit>()
        val store = UserDatabaseFavourites(
            database = {
                blocked.await()
                database
            },
            now = { 1L },
            report = { _, _ -> },
            scope = backgroundScope,
        )
        repeat(200) { index -> store.toggleFavourite(index.toLong()) }
        var declined = false

        store.replaceListIfEmpty(UserList.Favourites, listOf(7L)) { declined = true }

        assertTrue(declined, "a dropped conditional import must say it was not written")
        blocked.complete(Unit)
    }
}
