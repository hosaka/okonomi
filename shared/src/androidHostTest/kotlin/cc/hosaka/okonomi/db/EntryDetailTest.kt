package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Exercises the entry view's read path over a JDBC-seeded database file
 * (see DictionaryInfoLoadTest for why JDBC).
 *
 * Seeded corpus:
 * - 1 食べる/喰べる with たべる (+ restricted タベル) common, v1+vt with a
 *   food field, a Kansai-ben dialect, an unknown code, an s_inf note,
 *   two-value stagk restrictions, and an entirely empty trailing sense
 * - 2 ラーメン, kana only and not common, one plain sense
 * - 3 an entry row with no form and no reading at all
 */
class EntryDetailTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("entrydetail").toFile().also { tempDirs += it }

    private suspend fun seededDatabase(withTagLabels: Boolean = true): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        if (withTagLabels) {
            // Stored in the source's own casing, as dictgen writes them.
            db.tagQueries.insertTagLabel("v1", "Ichidan verb")
            db.tagQueries.insertTagLabel("vt", "transitive verb")
            db.tagQueries.insertTagLabel("food", "food, cooking")
            db.tagQueries.insertTagLabel("ksb", "Kansai-ben")
            db.tagQueries.insertTagLabel("uk", "word usually written using kana alone")
        }

        db.entryQueries.insertEntry(1, 125, 1)
        db.entryQueries.insertKanjiForm(1, 0, "食べる", 125, 1)
        db.entryQueries.insertKanjiForm(1, 1, "喰べる", 950, 0)
        db.entryQueries.insertReading(1, 0, "たべる", 0, 125, null, 1)
        db.entryQueries.insertReading(1, 1, "タベル", 1, 950, "食べる;喰べる", 0)
        db.entryQueries.insertSense(
            10, 1, 0, "v1,vt", "unheard-of", "food", "ksb", "colloquial", "食べる;喰べる",
        )
        db.entryQueries.insertGloss(10, 0, "to eat")
        db.entryQueries.insertGloss(10, 1, "to live on")
        db.entryQueries.insertSense(11, 1, 1, "v1", null, null, null, null, null)
        db.entryQueries.insertGloss(11, 0, "to take (medicine)")
        // A sense the source left with nothing in it at all.
        db.entryQueries.insertSense(12, 1, 2, null, null, null, null, null, null)

        db.entryQueries.insertEntry(2, 950, 0)
        db.entryQueries.insertReading(2, 0, "ラーメン", 0, 950, null, 0)
        db.entryQueries.insertSense(20, 2, 0, "n", "uk", null, null, null, null)
        db.entryQueries.insertGloss(20, 0, "ramen")

        db.entryQueries.insertEntry(3, 950, 0)

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `hydrates forms, readings with romaji and senses with resolved labels`() = runTest {
        val entry = seededDatabase().loadEntryDetail(1)

        requireNotNull(entry)
        assertEquals("食べる", entry.headword)
        assertEquals(listOf("食べる", "喰べる"), entry.forms.map { it.text })
        assertEquals(listOf("喰べる"), entry.alternateForms.map { it.text })
        assertTrue(entry.isCommon, "the entry row's is_common is what the badge reads")
        assertEquals(125, entry.commonRank)

        assertEquals(listOf("たべる", "タベル"), entry.readings.map { it.text })
        assertEquals(listOf("taberu", "taberu"), entry.readings.map { it.romaji })
        assertEquals(emptyList(), entry.readings[0].restrictions)
        // Two re_restr values, semicolon joined by the generator.
        assertEquals(listOf("食べる", "喰べる"), entry.readings[1].restrictions)

        val first = entry.senses.first()
        assertEquals(
            listOf(
                // Source casing survives: lowercasing is the screen's job.
                "Ichidan verb",
                "transitive verb",
                // Unknown codes fall back to themselves rather than vanishing.
                "unheard-of",
                "food, cooking",
                "Kansai-ben",
            ),
            first.tags,
        )
        assertEquals(listOf("to eat", "to live on"), first.glosses)
        assertEquals("colloquial", first.info)
        assertEquals(listOf("食べる", "喰べる"), first.restrictions)

        // The codes survive beside their labels: the Forms tab
        // classifies by code, which no label can be parsed back into.
        assertEquals(listOf("v1", "vt"), first.posCodes)

        val second = entry.senses[1]
        assertEquals(listOf("Ichidan verb"), second.tags)
        assertNull(second.info)
        assertEquals(emptyList(), second.restrictions)
        assertEquals(listOf("v1", "vt"), entry.posCodes, "repeats across senses collapse, in sense order")
    }

    @Test
    fun `drops a sense that carries nothing to show`() = runTest {
        val entry = seededDatabase().loadEntryDetail(1)

        requireNotNull(entry)
        assertEquals(2, entry.senses.size, "the empty third sense has no block to render")
        assertTrue(entry.senses.none { it.isEmpty })
    }

    @Test
    fun `a kana-only entry is headed by its reading and is not common`() = runTest {
        val entry = seededDatabase().loadEntryDetail(2)

        requireNotNull(entry)
        assertEquals("ラーメン", entry.headword)
        assertTrue(entry.forms.isEmpty())
        assertTrue(entry.alternateForms.isEmpty())
        assertEquals("raamen", entry.readings.single().romaji)
        assertFalse(entry.isCommon)
        assertEquals(
            listOf("n", "word usually written using kana alone"),
            entry.senses.single().tags,
        )
    }

    @Test
    fun `an entry with neither a form nor a reading has nothing to head it`() = runTest {
        assertNull(seededDatabase().loadEntryDetail(3), "a headwordless entry is an error state, not a blank screen")
    }

    @Test
    fun `every code falls back to itself when the label table is empty`() = runTest {
        val entry = seededDatabase(withTagLabels = false).loadEntryDetail(1)

        requireNotNull(entry)
        assertEquals(
            listOf("v1", "vt", "unheard-of", "food", "ksb"),
            entry.senses.first().tags,
            "a dictionary generated without tag_label must still render chips",
        )
    }

    @Test
    fun `an unknown id loads as null instead of failing`() = runTest {
        assertNull(seededDatabase().loadEntryDetail(9999))
    }
}
