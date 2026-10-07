package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * Every gloss of an entry, for the AnkiDroid export, read from a database
 * built with the real dictionary schema. The case the read exists for is
 * an entry with more senses than a result row shows and senses with
 * several glosses each; the row for the same entry is read alongside to
 * show the two really differ, so this cannot pass by reusing it.
 */
class EntryGlossesTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private suspend fun seededDatabase(): DictionaryDatabase {
        val path = Files.createTempDirectory("entryglosses").toFile()
            .also { tempDirs += it }
            .resolve(DICTIONARY_DB_NAME)
            .absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        db.entryQueries.insertEntry(1, 100, 1)
        db.entryQueries.insertKanjiForm(1, 0, "寝る", 100, 1)
        db.entryQueries.insertReading(1, 0, "ねる", 0, 100, null, 1)
        // Sense ids deliberately out of step with sense order, so an
        // ordering by id rather than by ord would show.
        db.entryQueries.insertSense(14, 1, 0, "v1,vi", null, null, null, null, null)
        db.entryQueries.insertGloss(14, 0, "to sleep (lying down)")
        db.entryQueries.insertSense(11, 1, 1, "v1,vi", null, null, null, null, null)
        db.entryQueries.insertGloss(11, 1, "to lie in bed")
        db.entryQueries.insertGloss(11, 0, "to go to bed")
        db.entryQueries.insertSense(13, 1, 2, "v1,vi", null, null, null, null, null)
        db.entryQueries.insertGloss(13, 0, "to lie down")
        db.entryQueries.insertSense(12, 1, 3, "v1,vi", null, null, null, null, null)
        db.entryQueries.insertGloss(12, 0, "to sleep (with someone)")
        db.entryQueries.insertGloss(12, 1, "to have sex")

        db.entryQueries.insertEntry(2, 900, 0)
        db.entryQueries.insertReading(2, 0, "ねね", 0, 900, null, 0)
        db.entryQueries.insertSense(20, 2, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(20, 0, "sleep (child language)")

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `every gloss of every sense comes back in sense order then gloss order`() = runTest {
        val database = seededDatabase()

        val glosses = database.entryGlosses(listOf(1L, 2L))

        assertEquals(
            listOf(
                "to sleep (lying down)",
                "to go to bed",
                "to lie in bed",
                "to lie down",
                "to sleep (with someone)",
                "to have sex",
            ),
            glosses[1L],
        )
        assertEquals(listOf("sleep (child language)"), glosses[2L])
        // The row is capped; the card is not. Without this the test could
        // be satisfied by an implementation that read the row.
        assertEquals(3, database.entryRows(listOf(1L)).single().senseLines.size)
    }

    @Test
    fun `an id the dictionary does not carry has no glosses rather than an error`() = runTest {
        val database = seededDatabase()

        assertEquals(setOf(2L), database.entryGlosses(listOf(2L, 999L)).keys)
    }

    @Test
    fun `a list longer than one query's worth of ids is read whole`() = runTest {
        val database = seededDatabase()
        // The wanted id last, so it falls in the second chunk.
        val ids = List(ENTRY_ROW_CHUNK + 1) { if (it == ENTRY_ROW_CHUNK) 1L else 10_000L + it }

        assertEquals(6, database.entryGlosses(ids)[1L]?.size)
    }
}
