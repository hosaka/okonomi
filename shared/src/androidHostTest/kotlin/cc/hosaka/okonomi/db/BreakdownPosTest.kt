package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The read path behind the tappable-word rule, over a JDBC-seeded
 * database file (see DictionaryInfoLoadTest for why JDBC).
 *
 * This is the rule's only supplier, and until it had a test of its own
 * every other test in the feature hand-built the map it returns: making
 * `loadBreakdownPos` return an empty [BreakdownPos] — the state in
 * which は, を and だ are all tappable again — left the whole suite
 * green.
 *
 * Seeded corpus, each entry chosen for one clause of the rule:
 *
 *   1  食べる / たべる    `v1,vt`      an ordinary content word
 *   2  は                `prt`        the particle, reached only by text
 *   3  葉 / は            `n`          the noun Tatoeba mislinks は to
 *   4  迄 / まで          `prt`        a particle matched on its kanji form
 *   5  だ                `aux-v,cop`  two codes in one comma-joined column
 */
class BreakdownPosTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("breakdownpos").toFile().also { tempDirs += it }

    private suspend fun seededDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        listOf(1L, 2L, 3L, 4L, 5L).forEach { db.entryQueries.insertEntry(it, 950L, 0L) }

        db.entryQueries.insertKanjiForm(1L, 0L, "食べる", 950L, 1L)
        db.entryQueries.insertReading(1L, 0L, "たべる", 0L, 950L, null, 1L)
        db.entryQueries.insertSense(10L, 1L, 0L, "v1,vt", null, null, null, null, null)

        db.entryQueries.insertReading(2L, 0L, "は", 0L, 950L, null, 1L)
        db.entryQueries.insertSense(20L, 2L, 0L, "prt", null, null, null, null, null)

        db.entryQueries.insertKanjiForm(3L, 0L, "葉", 950L, 1L)
        db.entryQueries.insertReading(3L, 0L, "は", 0L, 950L, null, 1L)
        db.entryQueries.insertSense(30L, 3L, 0L, "n", null, null, null, null, null)

        db.entryQueries.insertKanjiForm(4L, 0L, "迄", 950L, 0L)
        db.entryQueries.insertReading(4L, 0L, "まで", 0L, 950L, null, 1L)
        db.entryQueries.insertSense(40L, 4L, 0L, "prt", null, null, null, null, null)

        db.entryQueries.insertReading(5L, 0L, "だ", 0L, 950L, null, 1L)
        db.entryQueries.insertSense(50L, 5L, 0L, "aux-v,cop", null, null, null, null, null)

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    private fun word(text: String, entryId: Long? = null) =
        BreakdownWord(text = text, reading = null, entryId = entryId)

    @Test
    fun `a linked entry's codes come back under its id`() = runTest {
        val pos = seededDatabase().loadBreakdownPos(listOf(word("食べる", 1L)))

        assertEquals(listOf("v1", "vt"), pos.byEntryId[1L])
    }

    /** The column is comma-joined; a rule testing whole codes needs them split. */
    @Test
    fun `a comma joined pos column arrives as separate codes`() = runTest {
        val pos = seededDatabase().loadBreakdownPos(listOf(word("だ", 5L)))

        assertEquals(listOf("aux-v", "cop"), pos.byEntryId[5L])
        assertEquals(listOf("aux-v", "cop"), pos.byText["だ"])
    }

    /**
     * The clause the whole feature rests on: は arrives linked to 葉,
     * and only the text lookup can say it is also a particle.
     */
    @Test
    fun `a text lookup finds every entry read that way rather than the linked one alone`() = runTest {
        val pos = seededDatabase().loadBreakdownPos(listOf(word("は", 3L)))

        assertEquals(listOf("n"), pos.byEntryId[3L], "the link says noun, and it is wrong")
        assertTrue(
            "prt" in pos.byText["は"].orEmpty(),
            "the particle entry is reached through the reading, or は stays tappable forever",
        )
    }

    /** 迄 is a particle whose match is a kanji form, not a reading. */
    @Test
    fun `a text lookup searches kanji forms as well as readings`() = runTest {
        val pos = seededDatabase().loadBreakdownPos(listOf(word("迄")))

        assertEquals(listOf("prt"), pos.byText["迄"])
    }

    @Test
    fun `a word with no entry id still gets its text looked up`() = runTest {
        val pos = seededDatabase().loadBreakdownPos(listOf(word("だ")))

        assertTrue(pos.byEntryId.isEmpty(), "there was no id to ask about")
        assertEquals(listOf("aux-v", "cop"), pos.byText["だ"])
    }

    @Test
    fun `a word the dictionary carries nothing for is simply absent`() = runTest {
        val pos = seededDatabase().loadBreakdownPos(listOf(word("ホゲホゲ", 999L)))

        assertNull(pos.byEntryId[999L])
        assertNull(pos.byText["ホゲホゲ"])
    }

    @Test
    fun `no words means an empty result`() = runTest {
        assertEquals(BreakdownPos(), seededDatabase().loadBreakdownPos(emptyList()))
    }

    /**
     * The shape the producer actually calls it with: a whole
     * breakdown's worth of words at once, mixing linked and unlinked,
     * readings and kanji forms.
     */
    @Test
    fun `a whole breakdown is answered in one pass`() = runTest {
        val pos = seededDatabase().loadBreakdownPos(
            listOf(word("食べる", 1L), word("は", 3L), word("迄"), word("だ", 5L)),
        )

        assertEquals(setOf(1L, 3L, 5L), pos.byEntryId.keys)
        assertEquals(setOf("食べる", "は", "迄", "だ"), pos.byText.keys)
    }
}
