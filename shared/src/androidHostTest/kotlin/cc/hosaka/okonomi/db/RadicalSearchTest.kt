package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The radical lookup over a JDBC-seeded database file (see
 * DictionaryInfoLoadTest for why JDBC).
 *
 * The corpus exists to make the ORDER BY prove itself. Every clause of
 * it decides at least one adjacent pair here, and the rows are seeded in
 * an order that agrees with none of them, so a dropped clause cannot be
 * masked by insertion order:
 *
 * - 見 (freq 22, 7 strokes) and 発 (freq 22, 9 strokes) tie on frequency
 *   and are separated by stroke count alone.
 * - 中 (freq 100, 4 strokes) and 右 (freq 100, 4 strokes) tie on both and
 *   are separated by the literal alone.
 * - 口 (freq 320) is its own radical, and must appear in its own result.
 * - 吋, 吐 (both 6 strokes) and 呀 (7 strokes) carry no frequency at all
 *   and must sort behind every ranked character, then by stroke count,
 *   then by literal.
 * - 兀 is decomposed by radkfile but absent from kanjidic, standing in
 *   for the characters the shipped data really does leave out.
 * - 見 is decomposed by 口 TWICE. kanji_radical has no primary key and
 *   no unique index, so this is a row the table permits; the shipped
 *   data carries no such pair today, which is exactly why nothing but a
 *   seeded one can hold `SELECT DISTINCT` in place.
 *
 * 儿 is a second radical whose single kanji must never leak into 口's
 * result, and 龠 is a radical with no kanji at all.
 */
class RadicalSearchTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { runCatching { it.close() } }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("radicalsearch").toFile().also { tempDirs += it }

    private suspend fun seededDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        db.kanjiQueries.insertRadical("口", 3L)
        db.kanjiQueries.insertRadical("儿", 2L)
        db.kanjiQueries.insertRadical("龠", 17L)

        // Seeded literal, grade, stroke_count, freq, jlpt — deliberately
        // in an order that matches no clause of the ORDER BY, so rowid
        // order can never stand in for the sort.
        db.kanjiQueries.insertKanji("呀", null, 7L, null, null)
        db.kanjiQueries.insertKanji("口", 1L, 3L, 320L, 4L)
        db.kanjiQueries.insertKanji("右", 1L, 4L, 100L, 4L)
        db.kanjiQueries.insertKanji("発", 3L, 9L, 22L, 3L)
        db.kanjiQueries.insertKanji("吐", null, 6L, null, null)
        db.kanjiQueries.insertKanji("中", 1L, 4L, 100L, 4L)
        db.kanjiQueries.insertKanji("吋", null, 6L, null, null)
        db.kanjiQueries.insertKanji("見", 1L, 7L, 22L, 4L)
        db.kanjiQueries.insertKanji("元", 2L, 4L, 190L, 3L)

        listOf("呀", "口", "右", "発", "吐", "中", "吋", "見").forEach {
            db.kanjiQueries.insertKanjiRadical(it, "口")
        }
        // radkfile decomposes 兀 but kanjidic never describes it, so the
        // inner join has nothing to attach it to.
        db.kanjiQueries.insertKanjiRadical("兀", "口")
        // The pair the table cannot reject: a second (見, 口) row.
        db.kanjiQueries.insertKanjiRadical("見", "口")
        db.kanjiQueries.insertKanjiRadical("元", "儿")

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    /**
     * The whole order in one assertion, because the clauses are only
     * correct together: ranked before unranked, ascending frequency,
     * then stroke count, then literal.
     */
    @Test
    fun `the kanji come back ranked first then by strokes then by literal`() = runTest {
        val hits = seededDatabase().kanjiContainingRadical("口")

        assertEquals(
            listOf("見", "発", "中", "右", "口", "吋", "吐", "呀"),
            hits.map { it.literal },
        )
    }

    /**
     * The `freq IS NULL` clause on its own. Without it SQLite sorts
     * NULLs first, so every unranked character would come before the
     * common ones and the first screen would be useless — which is the
     * whole reason the order is ranked rather than alphabetical.
     */
    @Test
    fun `every ranked character sorts ahead of every unranked one`() = runTest {
        val hits = seededDatabase().kanjiContainingRadical("口")

        val lastRanked = hits.indexOfLast { it.freq != null }
        val firstUnranked = hits.indexOfFirst { it.freq == null }
        assertTrue(lastRanked >= 0 && firstUnranked >= 0, "the corpus must hold both kinds")
        assertTrue(
            lastRanked < firstUnranked,
            "unranked characters must follow every ranked one, got ${hits.map { it.literal to it.freq }}",
        )
    }

    /** Two characters on one frequency, separated by stroke count alone. */
    @Test
    fun `a tie on frequency is broken by stroke count`() = runTest {
        val hits = seededDatabase().kanjiContainingRadical("口")

        val tied = hits.filter { it.freq == 22L }
        assertEquals(listOf("見" to 7L, "発" to 9L), tied.map { it.literal to it.strokeCount })
    }

    /**
     * And a tie on both, which only the literal can break. This is what
     * makes the order total: `literal` is the kanji primary key, so no
     * two rows can compare equal and nothing is left for the plan to
     * decide.
     */
    @Test
    fun `a tie on frequency and stroke count is broken by the literal`() = runTest {
        val hits = seededDatabase().kanjiContainingRadical("口")

        val tied = hits.filter { it.freq == 100L }
        assertEquals(listOf("中", "右"), tied.map { it.literal })
        assertEquals(listOf(4L, 4L), tied.map { it.strokeCount })
    }

    /** 口 is a kanji built from 口, and the reader expects to find it. */
    @Test
    fun `the radical itself is among its own kanji`() = runTest {
        val hits = seededDatabase().kanjiContainingRadical("口")

        assertTrue("口" in hits.map { it.literal }, "the radical must not be excluded from its own grid")
    }

    /**
     * kanji_radical carries no foreign key to kanji(literal) and the
     * lookup inner-joins kanji, so a radkfile character kanjidic does
     * not describe is dropped rather than surfacing as a cell with no
     * stroke count. Correct — the grid shows characters the dictionary
     * can talk about — but it has to be a tested fact rather than an
     * accident of the join.
     */
    @Test
    fun `a decomposition with no kanji row is dropped rather than shown blank`() = runTest {
        val database = seededDatabase()

        val hits = database.kanjiContainingRadical("口")

        assertTrue(
            "兀" !in hits.map { it.literal },
            "a radkfile character kanjidic does not carry must not reach the grid",
        )
        // And the row really is in kanji_radical, so this cannot pass by
        // the decomposition never having been seeded. Asked from the
        // other side, where the join is to `radical` rather than to
        // `kanji`, 兀 is there.
        assertEquals(
            listOf("口"),
            database.db.kanjiQueries.radicalsForLiterals(listOf("兀")).awaitList().map { it.literal },
        )
    }

    /**
     * A repeated decomposition must not repeat its kanji. The grid keys
     * its cells on the literal, so a second 見 is not a cosmetic
     * duplicate: Compose throws on the duplicate key and the screen
     * comes down.
     */
    @Test
    fun `a decomposition seeded twice yields its kanji once`() = runTest {
        val database = seededDatabase()

        val hits = database.kanjiContainingRadical("口")

        assertEquals(1, hits.count { it.literal == "見" }, "got ${hits.map { it.literal }}")
        assertEquals(hits.size, hits.map { it.literal }.distinct().size, "every literal must be unique")
        // And the second row really is in the table, so this cannot pass
        // by the duplicate never having been seeded: the un-collapsed
        // read of the same join returns 見 twice.
        assertEquals(
            2,
            database.db.kanjiQueries.kanjiByRadical("口").awaitList().count { it.literal == "見" },
        )
    }

    /**
     * A radical nothing joins to is an empty list, not a failure: the
     * screen shows its own note for it.
     */
    @Test
    fun `a radical with no kanji comes back empty`() = runTest {
        assertEquals(emptyList<KanjiHit>(), seededDatabase().kanjiContainingRadical("龠"))
    }

    /** One radical's kanji, never another's. */
    @Test
    fun `only the asked-for radical's kanji come back`() = runTest {
        val hits = seededDatabase().kanjiContainingRadical("儿")

        assertEquals(listOf("元"), hits.map { it.literal })
    }

    /**
     * The public entry's guard, reached with no database in the picture
     * at all: `require` runs before `dictionary()`, so an empty radical
     * never provisions, opens or queries anything. Asserted against the
     * top-level function rather than the receiver form, which carries no
     * guard of its own — one check, in the one place production code
     * passes through.
     */
    @Test
    fun `an empty radical is rejected before the dictionary is opened`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            kanjiContainingRadical("")
        }
    }
}
