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
 * Exercises the Kanji tab's read path over a JDBC-seeded database file
 * (see DictionaryInfoLoadTest for why JDBC).
 *
 * Seeded corpus:
 * - 食: every kanjidic field set; on/kun/nanori readings, two meanings,
 *   and itself as its radical (a character that is its own radical)
 * - 物: grade, strokes and freq set, jlpt null; two radicals
 * - 一: grade, strokes and jlpt set, freq null; no radical row at all
 * - 兀: present in radkfile but absent from kanjidic, standing in for
 *   the 32 such characters in the shipped data
 *
 * 食's and 物's readings are seeded INTERLEAVED on purpose. Seeded
 * contiguously, rowid order and per-character grouping would coincide,
 * and a plan that resequenced rows across the IN list would still pass.
 */
class KanjiDetailTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("kanjidetail").toFile().also { tempDirs += it }

    private suspend fun seededDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        db.kanjiQueries.insertKanji("食", 2L, 9L, 382L, 4L)
        db.kanjiQueries.insertKanji("物", 3L, 8L, 32L, null)
        db.kanjiQueries.insertKanji("一", 1L, 1L, null, 4L)

        // Interleaved, and with kun deliberately seeded before on for
        // 食: row order is kanjidic's order and the only order the
        // reading table carries, so a query that grouped or sorted by
        // anything else would show up here.
        listOf("く.う", "く.らう", "た.べる", "は.む").forEach {
            db.kanjiQueries.insertKanjiReading("食", "kun", it)
        }
        db.kanjiQueries.insertKanjiReading("物", "on", "ブツ")
        listOf("ショク", "ジキ").forEach { db.kanjiQueries.insertKanjiReading("食", "on", it) }
        db.kanjiQueries.insertKanjiReading("物", "kun", "もの")
        db.kanjiQueries.insertKanjiReading("食", "nanori", "ぐい")

        db.kanjiQueries.insertKanjiMeaning("食", 0L, "eat")
        db.kanjiQueries.insertKanjiMeaning("物", 0L, "thing")
        db.kanjiQueries.insertKanjiMeaning("食", 1L, "food")

        db.kanjiQueries.insertRadical("食", 9L)
        db.kanjiQueries.insertRadical("牛", 4L)
        db.kanjiQueries.insertRadical("勿", 4L)
        db.kanjiQueries.insertRadical("儿", 2L)
        db.kanjiQueries.insertKanjiRadical("食", "食")
        db.kanjiQueries.insertKanjiRadical("物", "勿")
        db.kanjiQueries.insertKanjiRadical("物", "牛")
        // A character radkfile decomposes but kanjidic never describes.
        db.kanjiQueries.insertKanjiRadical("兀", "儿")

        // KanjiVG's paths arrive as one newline-joined value per
        // character. 物 deliberately gets none: the shipped corpus
        // covers 6,703 characters and kanjidic more than twice that, so
        // a character with readings and no strokes is normal data.
        db.kanjiQueries.insertKanjiStrokeOrder("食", "M1,2c3,4\nM5,6c7,8\nM9,10c11,12")
        db.kanjiQueries.insertKanjiStrokeOrder("一", "M11,54c3,0")

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `hydrates a character with its grouped readings, meanings and radical`() = runTest {
        val character = seededDatabase().loadKanjiForWord(listOf("食")).single()

        assertEquals("食", character.literal)
        assertTrue(character.hasData)
        assertEquals(9L, character.strokeCount)
        assertEquals(2L, character.grade)
        assertEquals(4L, character.jlpt)
        assertEquals(382L, character.freq)
        assertEquals(listOf("ショク", "ジキ"), character.onReadings)
        assertEquals(
            listOf("く.う", "く.らう", "た.べる", "は.む"),
            character.kunReadings,
            "the okurigana dot is kanjidic's own and must survive verbatim",
        )
        assertEquals(listOf("ぐい"), character.nameReadings)
        assertEquals(listOf("eat", "food"), character.meanings)
        // A character that is itself a radical shows itself.
        assertEquals(listOf("食"), character.radicals)
    }

    @Test
    fun `returns every character of a word in the order asked for`() = runTest {
        val characters = seededDatabase().loadKanjiForWord(listOf("食", "物"))

        assertEquals(listOf("食", "物"), characters.map { it.literal })
        assertEquals(listOf("ブツ"), characters[1].onReadings)
        assertEquals(listOf("もの"), characters[1].kunReadings)
        assertEquals(listOf("thing"), characters[1].meanings)
        // Ordered simplest radical first, ties broken by literal.
        assertEquals(listOf("勿", "牛"), characters[1].radicals)
    }

    @Test
    fun `stroke paths come back split, in the order they were stored`() = runTest {
        val characters = seededDatabase().loadKanjiForWord(listOf("食", "物"))

        assertEquals(
            listOf("M1,2c3,4", "M5,6c7,8", "M9,10c11,12"),
            characters.first().strokePaths,
            "the stored order IS the stroke order; nothing else records it",
        )
        assertTrue(
            characters[1].strokePaths.isEmpty(),
            "a character KanjiVG does not carry still renders, with an empty slot",
        )
    }

    @Test
    fun `a repeated character yields one card`() = runTest {
        val characters = seededDatabase().loadKanjiForWord(listOf("物", "食", "物"))

        assertEquals(listOf("物", "食"), characters.map { it.literal })
    }

    @Test
    fun `optional fields stay null without dropping the character`() = runTest {
        val characters = seededDatabase().loadKanjiForWord(listOf("物", "一"))

        val mono = characters.first()
        assertNull(mono.jlpt)
        assertEquals(8L, mono.strokeCount)

        val one = characters[1]
        assertNull(one.freq)
        assertEquals(1L, one.strokeCount)
        assertEquals(1L, one.grade)
        assertEquals(4L, one.jlpt)
        assertTrue(one.radicals.isEmpty(), "a character radkfile does not decompose still renders")
        assertTrue(one.meanings.isEmpty())
        assertTrue(one.onReadings.isEmpty())
    }

    @Test
    fun `a character kanjidic does not carry is synthesised rather than dropped`() = runTest {
        val characters = seededDatabase().loadKanjiForWord(listOf("食", "兀"))

        assertEquals(listOf("食", "兀"), characters.map { it.literal }, "the sibling must still render")
        val missing = characters[1]
        assertFalse(missing.hasData)
        assertNull(missing.strokeCount)
        assertNull(missing.grade)
        assertNull(missing.jlpt)
        assertNull(missing.freq)
        assertTrue(missing.onReadings.isEmpty())
        assertTrue(missing.kunReadings.isEmpty())
        assertTrue(missing.nameReadings.isEmpty())
        assertTrue(missing.meanings.isEmpty())
        // radkfile knows the character even where kanjidic does not.
        assertEquals(listOf("儿"), missing.radicals)
    }

    @Test
    fun `a character in no table at all is still a card`() = runTest {
        val character = seededDatabase().loadKanjiForWord(listOf("彁")).single()

        assertEquals("彁", character.literal)
        assertFalse(character.hasData)
        assertTrue(character.radicals.isEmpty())
        assertTrue(character.strokePaths.isEmpty())
    }

    @Test
    fun `an empty character list runs no query at all`() = runTest {
        val database = seededDatabase()
        // Closing the handle first is the proof: any query the loader
        // still ran would fail on a closed driver rather than pass
        // silently.
        database.close()

        assertEquals(emptyList(), database.loadKanjiForWord(emptyList()))
    }
}
