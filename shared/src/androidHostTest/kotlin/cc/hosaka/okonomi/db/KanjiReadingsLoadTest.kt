package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import cc.hosaka.okonomi.ui.furigana.KanjiReadings
import cc.hosaka.okonomi.ui.furigana.alignReading
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

/**
 * The per-kanji furigana's one read: every on and kun reading out of a
 * JDBC-seeded database (see DictionaryInfoLoadTest for why JDBC), and the
 * process-wide memo over it.
 *
 * What is asserted is how the loaded readings divide words, since that
 * is all they are for: a row the query dropped, or a nanori it kept,
 * changes a division.
 */
class KanjiReadingsLoadTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private suspend fun seededDatabase(): DictionaryDatabase {
        val dir = Files.createTempDirectory("kanjireadings").toFile().also { tempDirs += it }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dir.resolve(DICTIONARY_DB_NAME).absolutePath}")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)
        db.kanjiQueries.insertKanji("大", 1L, 3L, 7L, 4L)
        db.kanjiQueries.insertKanji("気", 1L, 6L, 113L, 4L)
        db.kanjiQueries.insertKanjiReading("大", "on", "ダイ")
        db.kanjiQueries.insertKanjiReading("大", "on", "タイ")
        db.kanjiQueries.insertKanjiReading("大", "kun", "おお.きい")
        // kanjidic's own name reading of 大. Loaded, it would let 大気
        // read た+いき beside たい+き and keep the run whole.
        db.kanjiQueries.insertKanjiReading("大", "nanori", "た")
        db.kanjiQueries.insertKanjiReading("気", "on", "キ")
        db.kanjiQueries.insertKanjiReading("気", "kun", "いき")
        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `on and kun readings are loaded and name readings are not`() = runTest {
        val readings = seededDatabase().loadKanjiReadings()

        assertEquals(
            listOf(FuriganaSegment("大", "たい"), FuriganaSegment("気", "き")),
            alignReading("大気", "たいき", readings),
        )
        // おお comes from the kun row, its okurigana dropped.
        assertEquals(
            listOf(FuriganaSegment("大", "おお"), FuriganaSegment("気", "いき")),
            alignReading("大気", "おおいき", readings),
        )
    }

    @Test
    fun `the readings load once and are shared`() = runTest {
        var loads = 0
        val holder = KanjiReadingsHolder(invalidate = {}) {
            loads++
            KanjiReadings.Builder().build()
        }

        val first = holder.kanjiReadings()
        val second = holder.kanjiReadings()

        assertSame(first, second)
        assertEquals(1, loads)
    }

    @Test
    fun `a load that fails drops the dictionary handle and is tried again`() = runTest {
        var loads = 0
        var invalidations = 0
        val holder = KanjiReadingsHolder(invalidate = { invalidations++ }) {
            loads++
            if (loads == 1) error("dictionary not ready")
            KanjiReadings.Builder().build()
        }

        assertFailsWith<IllegalStateException> { holder.kanjiReadings() }
        assertEquals(1, invalidations, "the handle that failed is not reused")
        holder.kanjiReadings()

        assertEquals(2, loads)
        assertEquals(1, invalidations, "a load that succeeds invalidates nothing")
    }
}
