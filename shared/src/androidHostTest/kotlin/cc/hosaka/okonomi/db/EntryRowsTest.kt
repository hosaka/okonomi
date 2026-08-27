package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * Rows for a list of entry ids the reader chose, which is what the
 * Favourites tab renders.
 *
 * The case worth the file is the dangling one: a saved id whose entry a
 * later dictionary no longer carries. It must produce no row and must not
 * throw, and — the part that cannot be asserted here because nothing in
 * this function can reach it — it must not cause the saved id to be
 * deleted. See `UserDatabaseFavouritesTest` for the store's side of that.
 */
class EntryRowsTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private suspend fun seededDatabase(): DictionaryDatabase {
        val path = Files.createTempDirectory("entryrows").toFile()
            .also { tempDirs += it }
            .resolve(DICTIONARY_DB_NAME)
            .absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        db.entryQueries.insertEntry(1, 125, 1)
        db.entryQueries.insertKanjiForm(1, 0, "食べる", 125, 1)
        db.entryQueries.insertReading(1, 0, "たべる", 0, 125, null, 1)
        db.entryQueries.insertSense(10, 1, 0, "v1,vt", null, null, null, null, null)
        db.entryQueries.insertGloss(10, 0, "to eat")

        db.entryQueries.insertEntry(2, 950, 0)
        db.entryQueries.insertKanjiForm(2, 0, "食べ物", 950, 0)
        db.entryQueries.insertReading(2, 0, "たべもの", 0, 950, null, 0)
        db.entryQueries.insertSense(20, 2, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(20, 0, "food")

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `rows come back in the order they were asked for rather than in ranking order`() = runTest {
        val database = seededDatabase()

        // 2 is the less common entry, so any ranking would put it second.
        val rows = database.entryRows(listOf(2L, 1L))

        assertEquals(listOf(2L, 1L), rows.map { it.entryId })
        assertEquals(listOf("食べ物", "たべもの"), rows[0].titleSegments.map { it.text })
        assertEquals(listOf("food"), rows[0].senseLines)
    }

    @Test
    fun `an id the dictionary no longer carries is omitted rather than thrown over`() = runTest {
        val database = seededDatabase()

        val rows = database.entryRows(listOf(1L, 9_999_999L, 2L))

        assertEquals(
            listOf(1L, 2L),
            rows.map { it.entryId },
            "a dangling id must drop out of the list without disturbing the rows around it",
        )
    }

    @Test
    fun `a list of nothing but dangling ids is an empty list rather than a failure`() = runTest {
        val database = seededDatabase()

        assertEquals(emptyList(), database.entryRows(listOf(9_999_998L, 9_999_999L)).map { it.entryId })
    }

    /**
     * The Favourites list has no ceiling on it — every other list in this
     * app is capped at SEARCH_MAX_RESULTS, and this one is however many
     * words the reader saved. Each id becomes a bound parameter in five
     * `IN` queries, so an unchunked lookup meets SQLITE_MAX_VARIABLE_NUMBER
     * as a plain failure, which the Favourites read path would then report
     * as an empty tab: the reader's whole list gone, for having too much
     * in it.
     *
     * The size is chosen to clear the ceiling of the JDBC SQLite these
     * host tests run on, which is far higher than the 32766 of the
     * bundled build the app actually ships — so it demonstrates the
     * mechanism rather than modelling a list anyone would have. Without
     * the chunking it fails with "too many SQL variables"; the two real
     * entries at the end are what proves the walk completed rather than
     * stopping early.
     */
    @Test
    fun `a list far past the bound parameter limit still resolves its rows`() = runTest {
        val database = seededDatabase()
        val ids = (100_000L until 400_000L).toList() + listOf(1L, 2L)

        val rows = database.entryRows(ids)

        assertEquals(listOf(1L, 2L), rows.map { it.entryId })
    }

    @Test
    fun `rows carry no highlight because no query chose them`() = runTest {
        val database = seededDatabase()

        val rows = database.entryRows(listOf(1L))

        assertEquals(
            emptyList(),
            rows.single().titleSegments.mapNotNull { it.highlight },
            "a saved word was not matched against anything, so nothing in it may be lit",
        )
        assertEquals(emptyList(), rows.single().traceLabels)
    }
}
