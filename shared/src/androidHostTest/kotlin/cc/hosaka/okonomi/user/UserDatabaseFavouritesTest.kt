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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

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

    private suspend fun UserDatabase.storedEntryIds(): List<Long> {
        val listId = db.listQueries.listBySlug(FAVOURITES_LIST_SLUG).awaitOneOrNull()?.id
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

        store.replaceFavourites(listOf(7L, 8L, 9L))

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
        store.replaceFavourites(listOf(7L))

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

        store.replaceFavourites(emptyList())

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

        store.replaceFavourites(listOf(5L, 5L, 9L))

        assertEquals(listOf(5L, 9L), store.favouriteEntryIds().first { it.isNotEmpty() })
        assertEquals(listOf(5L, 9L), database.storedEntryIds())
    }

    @Test
    fun `an imported list survives the store being rebuilt over the same file`() = runTest {
        val file = tempFile()
        storeOver(openOver(file), backgroundScope).let { first ->
            first.replaceFavourites(listOf(3L, 2L, 1L))
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
        store.replaceFavourites(listOf(7L, 8L))
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

        store.replaceFavourites(listOf(7L, 8L))
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
}
