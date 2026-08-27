package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real search path — deinflection, prefix ranges, POS
 * validation, FTS sanitizing, fallback, ranking and hydration — over a
 * JDBC-seeded database file (see DictionaryInfoLoadTest for why JDBC).
 *
 * Seeded corpus (common_rank is the tier*100+bucket composite):
 * - 1 食べる/たべる 125 common, v1: "to eat" / "to live on"
 * - 2 食べ物/たべもの 145 common, n: "food"
 * - 3 食べ歩き/たべあるき 950, n: four senses (overflow row)
 * - 4 食べる/はむ 950, n: homograph that must fail POS validation
 * - 6 食べた口/たべたくち 105 common, n: prefix hit for the ranking row;
 *   its second gloss "café drink" pins accented-Latin routing
 * - 7 叢立ち・総立ち/そうだち(総立ち only)・むらだち(叢立ち only) 500, n:
 *   readings JMdict restricts to one spelling each
 * - 8 空オケ/カラオケ(re_nokanji)・からオケ 600, n: a reading of the word
 *   that is a reading of no spelling of it
 */
class EntrySearchTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("entrysearch").toFile().also { tempDirs += it }

    private suspend fun seededDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        db.entryQueries.insertEntry(1, 125, 1)
        db.entryQueries.insertKanjiForm(1, 0, "食べる", 125, 1)
        db.entryQueries.insertReading(1, 0, "たべる", 0, 125, null, 1)
        db.entryQueries.insertSense(10, 1, 0, "v1,vt", null, null, null, null, null)
        db.entryQueries.insertGloss(10, 0, "to eat")
        db.entryQueries.insertSense(11, 1, 1, "v1,vt", null, null, null, null, null)
        db.entryQueries.insertGloss(11, 0, "to live on")

        db.entryQueries.insertEntry(2, 145, 1)
        db.entryQueries.insertKanjiForm(2, 0, "食べ物", 145, 1)
        db.entryQueries.insertReading(2, 0, "たべもの", 0, 145, null, 1)
        db.entryQueries.insertSense(20, 2, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(20, 0, "food")
        db.entryQueries.insertGloss(20, 1, "foodstuff")

        db.entryQueries.insertEntry(3, 950, 0)
        db.entryQueries.insertKanjiForm(3, 0, "食べ歩き", 950, 0)
        db.entryQueries.insertReading(3, 0, "たべあるき", 0, 950, null, 0)
        db.entryQueries.insertSense(30, 3, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(30, 0, "trying the food at various restaurants")
        db.entryQueries.insertSense(31, 3, 1, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(31, 0, "gourmet food tour")
        db.entryQueries.insertSense(32, 3, 2, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(32, 0, "eating while walking")
        db.entryQueries.insertSense(33, 3, 3, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(33, 0, "restaurant hopping")

        // Homograph of 食べる that is not a verb: a deinflection
        // candidate must never surface it.
        db.entryQueries.insertEntry(4, 950, 0)
        db.entryQueries.insertKanjiForm(4, 0, "食べる", 950, 0)
        db.entryQueries.insertReading(4, 0, "はむ", 0, 950, null, 0)
        db.entryQueries.insertSense(40, 4, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(40, 0, "fictional noun homograph")

        // More common than 食べる, prefix-matches 食べた: the
        // deinflected exact hit must still rank first.
        db.entryQueries.insertEntry(6, 105, 1)
        db.entryQueries.insertKanjiForm(6, 0, "食べた口", 105, 1)
        db.entryQueries.insertReading(6, 0, "たべたくち", 0, 105, null, 1)
        db.entryQueries.insertSense(60, 6, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(60, 0, "fictional test noun")
        db.entryQueries.insertGloss(60, 1, "café drink")

        // Two spellings whose readings JMdict states separately: そうだち
        // is 総立ち's alone, and むらだち is 叢立ち's.
        db.entryQueries.insertEntry(7, 500, 0)
        db.entryQueries.insertKanjiForm(7, 0, "叢立ち", 500, 0)
        db.entryQueries.insertKanjiForm(7, 1, "総立ち", 500, 0)
        db.entryQueries.insertReading(7, 0, "そうだち", 0, 500, "総立ち", 0)
        db.entryQueries.insertReading(7, 1, "むらだち", 0, 500, "叢立ち", 0)
        db.entryQueries.insertSense(70, 7, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(70, 0, "rising in a body")

        // A re_nokanji reading, listed first: カラオケ is a reading of
        // the word and of no spelling of it.
        db.entryQueries.insertEntry(8, 600, 0)
        db.entryQueries.insertKanjiForm(8, 0, "空オケ", 600, 0)
        db.entryQueries.insertReading(8, 0, "カラオケ", 1, 600, null, 0)
        db.entryQueries.insertReading(8, 1, "からオケ", 0, 600, null, 0)
        db.entryQueries.insertSense(80, 8, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(80, 0, "singing to a backing track")

        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    /**
     * たべ is both: 食べる is reached exactly through the continuative
     * candidate たべる *and* prefix-matches the typed characters. It is
     * ordered with the exact hits — above 食べた口, which merely starts
     * with them and is the more common entry — and presented as a prefix
     * hit, with the typed kana highlighted and no breadcrumb explaining
     * a match the reader can already see.
     */
    @Test
    fun `prefix search matches readings, highlights the prefix and ranks by common_rank`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("たべ")

        assertFalse(results.isFallback)
        assertEquals(listOf(1L, 6L, 2L, 3L), results.hits.map { it.entryId })

        val taberu = results.hits.first { it.entryId == 1L }
        assertEquals(listOf("食べる", "たべる"), taberu.titleSegments.map { it.text })
        assertNull(taberu.titleSegments[0].highlight)
        assertEquals(0 until 2, taberu.titleSegments[1].highlight)
        assertTrue(taberu.traceLabels.isEmpty())
        assertEquals(listOf("to eat", "to live on"), taberu.senseLines)
        assertTrue(taberu.isCommon)

        assertTrue(results.hits.first { it.entryId == 2L }.isCommon)
        assertFalse(results.hits.first { it.entryId == 3L }.isCommon)
    }

    /**
     * A row pairs a written form with a reading, and the ruby drawn from
     * that pair asserts the reading belongs to those characters. JMdict
     * says when it does not: そうだち is stated for 総立ち alone, and
     * over 叢立ち it would read 叢立 as そうだ.
     */
    @Test
    fun `a row takes the reading stated for the form it shows, not the entry's first`() = runTest {
        val database = seededDatabase()

        val hit = database.searchEntries("叢立ち").hits.single { it.entryId == 7L }

        assertEquals(listOf("叢立ち", "むらだち"), hit.titleSegments.map { it.text })
        assertTrue(hit.titleSegments[1].readsPreviousSegment, "むらだち is a reading of 叢立ち")
    }

    /**
     * A `re_nokanji` reading belongs to no spelling at all, so it is
     * neither chosen as the form's reading nor drawn over it when the
     * query is what found the entry. It is still shown — beside the
     * form, the way the row has always shown two texts it cannot pair.
     */
    @Test
    fun `a re_nokanji reading is shown beside the form rather than over it`() = runTest {
        val database = seededDatabase()

        val byForm = database.searchEntries("空オケ").hits.single { it.entryId == 8L }
        assertEquals(listOf("空オケ", "からオケ"), byForm.titleSegments.map { it.text })
        assertTrue(byForm.titleSegments[1].readsPreviousSegment)

        val byReading = database.searchEntries("カラオケ").hits.single { it.entryId == 8L }
        assertEquals(listOf("空オケ", "カラオケ"), byReading.titleSegments.map { it.text })
        assertFalse(
            byReading.titleSegments[1].readsPreviousSegment,
            "カラオケ is a reading of the word, not of 空オケ",
        )
    }

    @Test
    fun `deinflected search carries a trace and ranks before more common prefix hits`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("食べた")

        assertFalse(results.isFallback)
        // Entry 6 (rank 0) prefix-matches 食べた but the deinflected
        // exact hit for 食べる must come first.
        assertEquals(listOf(1L, 6L), results.hits.map { it.entryId })

        val taberu = results.hits.first()
        assertTrue(taberu.traceLabels.isNotEmpty())
        assertTrue(taberu.titleSegments.all { it.highlight == null })
    }

    @Test
    fun `the acceptance inflection finds the entry with a readable trace`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("食べなかった")

        assertFalse(results.isFallback)
        val taberu = results.hits.single { it.entryId == 1L }
        assertTrue(taberu.traceLabels.isNotEmpty())
        assertTrue(taberu.titleSegments.all { it.highlight == null })
    }

    @Test
    fun `a candidate never surfaces an entry whose parts of speech do not match`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("食べなかった")

        assertTrue(results.hits.none { it.entryId == 4L })
    }

    @Test
    fun `an exact query is deduplicated and fully highlighted`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("食べる")

        assertEquals(1, results.hits.count { it.entryId == 1L })
        val taberu = results.hits.first { it.entryId == 1L }
        assertTrue(taberu.traceLabels.isEmpty())
        assertEquals(0 until 3, taberu.titleSegments[0].highlight)
        // The identity candidate matches anything, so the noun
        // homograph is a legitimate exact hit here, ranked later.
        assertEquals(listOf(1L, 4L), results.hits.map { it.entryId })
    }

    /**
     * Alex's device case: searching 私 put わらわ first and わたし
     * twelfth.
     *
     * The ranking was never wrong — わたし carries 101 and the archaic
     * entries 950. What went wrong was the bucketing. The prefix window
     * holds `limit + 1` entries per source and fills with well-ranked
     * compounds, so the rank-950 entries written 私 fall outside it and
     * used to count as exact-only, which put them ahead of every prefix
     * hit — わたし among them, because it sits *inside* the window and
     * was therefore dropped from the exact group.
     *
     * [pronounDatabase] is that shape at the smallest size that
     * reproduces it: with `limit = 4` the window holds five entries,
     * which the common one and the four compounds fill exactly.
     */
    @Test
    fun `a common exact match leads the rare ones that fall outside the prefix window`() = runTest {
        val database = pronounDatabase()

        val results = database.searchEntries("私", limit = 4)

        assertEquals(
            listOf(500L, 501L, 502L, 510L),
            results.hits.map { it.entryId },
            "the exact matches lead, by commonness, and the compounds follow",
        )
        assertTrue(results.hasMore, "three compounds are still waiting below the page")
    }

    /**
     * The same corpus with room for everything, so the whole order is
     * visible rather than only its head: every entry written 私 comes
     * before every entry that merely starts with it, however common the
     * compounds are.
     */
    @Test
    fun `an exact match outranks a more common compound that merely starts with it`() = runTest {
        val database = pronounDatabase()

        val results = database.searchEntries("私")

        assertEquals(
            listOf(500L, 501L, 502L, 510L, 511L, 512L, 513L),
            results.hits.map { it.entryId },
        )
        assertFalse(results.hasMore, "and that is all of them")
    }

    /**
     * The claim the merge's KDoc makes about paging, over the corpus
     * built to break it: a longer page is the same page with more on the
     * end. The exact group grows as the window does — 501 and 502 are
     * outside the four-row prefix window and inside the seven-row one —
     * and a row already on screen must not move when it does.
     */
    @Test
    fun `a longer page of exact and prefix hits extends the shorter one`() = runTest {
        val database = pronounDatabase()

        val pages = listOf(1, 2, 4, 7).map { limit ->
            limit to database.searchEntries("私", limit = limit).hits.map { it.entryId }
        }

        pages.zipWithNext { (shortLimit, shorter), (longLimit, longer) ->
            assertEquals(
                shorter,
                longer.take(shorter.size),
                "page of $shortLimit must be a prefix of page of $longLimit",
            )
        }
        assertEquals(listOf(500L), pages.first().second)
    }

    /**
     * The 私 shape: one common entry written with the bare character,
     * two archaic ones nobody ranks, and compounds that start with it
     * and rank between the two — which is what fills the prefix window
     * and pushes the archaic entries out of it.
     */
    private suspend fun pronounDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        listOf(
            Triple(500L, 101L, "わたし"),
            Triple(501L, 950L, "わらわ"),
            Triple(502L, 950L, "わし"),
        ).forEach { (id, rank, reading) ->
            val common = if (rank == 101L) 1L else 0L
            db.entryQueries.insertEntry(id, rank, common)
            db.entryQueries.insertKanjiForm(id, 0, "私", rank, common)
            db.entryQueries.insertReading(id, 0, reading, 0, rank, null, common)
            db.entryQueries.insertSense(id * 10, id, 0, "pn", null, null, null, null, null)
            db.entryQueries.insertGloss(id * 10, 0, "I, me")
        }

        listOf(
            Triple(510L, 103L, "私立"),
            Triple(511L, 109L, "私鉄"),
            Triple(512L, 128L, "私費"),
            Triple(513L, 137L, "私用"),
        ).forEach { (id, rank, form) ->
            db.entryQueries.insertEntry(id, rank, 1)
            db.entryQueries.insertKanjiForm(id, 0, form, rank, 1)
            db.entryQueries.insertReading(id, 0, "よみ$id", 0, rank, null, 1)
            db.entryQueries.insertSense(id * 10, id, 0, "n", null, null, null, null, null)
            db.entryQueries.insertGloss(id * 10, 0, "compound $id")
        }

        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()
        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    /**
     * A deinflected hit far outside the prefix window is still shown as
     * what it is: き is the first character of きる, so it is
     * highlighted, and calling the match "continuative" would explain
     * something already on the row.
     *
     * The window is what this is about. 着る is the 174th き-prefix
     * entry in the shipped dictionary, so no page a reader sees puts it
     * inside one; [continuativeDatabase] reproduces that at `limit = 1`,
     * where the window holds two entries and the two common ones fill
     * it. Presentation taken from whether the entry also turned up among
     * the prefix hits gives a breadcrumb here.
     */
    @Test
    fun `a deinflected hit showing the typed characters is highlighted, not traced`() = runTest {
        val database = continuativeDatabase()

        val kiru = database.searchEntries("き", limit = 1).hits.single()

        assertEquals(600L, kiru.entryId)
        assertEquals(listOf("着る", "きる"), kiru.titleSegments.map { it.text })
        assertEquals(0 until 1, kiru.titleSegments[1].highlight)
        assertTrue(kiru.traceLabels.isEmpty(), "き is the first character of きる")
    }

    /**
     * And it stays that way as the reader pages. At `limit = 10` the
     * prefix window reaches 着る and at `limit = 1` it does not, so a
     * presentation read off the window flips between these two — a row
     * the reader is looking at losing its breadcrumb and gaining a
     * highlight when the next page lands.
     */
    @Test
    fun `paging past a deinflected hit does not change how it is shown`() = runTest {
        val database = continuativeDatabase()

        val narrow = database.searchEntries("き", limit = 1).hits.single { it.entryId == 600L }
        val wide = database.searchEntries("き", limit = 10).hits.single { it.entryId == 600L }

        assertEquals(narrow.titleSegments, wide.titleSegments)
        assertEquals(narrow.traceLabels, wide.traceLabels)
    }

    /**
     * The breadcrumb's own case, on the same corpus, so the two rules
     * are pinned against each other rather than one at a time: きた does
     * not begin きる, nothing on the row says why 着る matched, and the
     * trace is what says it.
     */
    @Test
    fun `a deinflected hit the query does not spell keeps its breadcrumb`() = runTest {
        val database = continuativeDatabase()

        val kiru = database.searchEntries("きた").hits.single { it.entryId == 600L }

        assertTrue(kiru.traceLabels.isNotEmpty(), "きた is not the start of きる")
        assertTrue(kiru.titleSegments.all { it.highlight == null })
    }

    /**
     * 着る, which nothing marks common, behind enough well-ranked
     * き-prefix entries to keep it out of a small prefix window.
     */
    private suspend fun continuativeDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        db.entryQueries.insertEntry(600, 950, 0)
        db.entryQueries.insertKanjiForm(600, 0, "着る", 950, 0)
        db.entryQueries.insertReading(600, 0, "きる", 0, 950, null, 0)
        db.entryQueries.insertSense(6000, 600, 0, "v1,vt", null, null, null, null, null)
        db.entryQueries.insertGloss(6000, 0, "to wear")

        // Readings rather than kanji forms: 着る is reached through the
        // reading prefix query, so it is that query's window it has to
        // be pushed out of.
        listOf(
            Triple(601L, 101L, "きこう"),
            Triple(602L, 105L, "きじ"),
            Triple(603L, 110L, "きかい"),
        ).forEach { (id, rank, reading) ->
            db.entryQueries.insertEntry(id, rank, 1)
            db.entryQueries.insertReading(id, 0, reading, 1, rank, null, 1)
            db.entryQueries.insertSense(id * 10, id, 0, "n", null, null, null, null, null)
            db.entryQueries.insertGloss(id * 10, 0, "common word $id")
        }

        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()
        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `english search goes through gloss fts`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("eat")

        assertEquals(listOf(1L), results.hits.map { it.entryId })
        val taberu = results.hits.single()
        assertEquals(listOf("to eat", "to live on"), taberu.senseLines)
        assertTrue(taberu.titleSegments.all { it.highlight == null })
    }

    @Test
    fun `fts metacharacters are sanitized and never crash`() = runTest {
        val database = seededDatabase()

        listOf("\"NEAR(", "eat OR \"", "NOT(*)", "a AND b", "\"\"").forEach { query ->
            database.searchEntries(query)
        }
    }

    @Test
    fun `a japanese query with no hits falls back to a truncated prefix`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("たべxyz")

        assertTrue(results.isFallback)
        assertTrue(results.hits.any { it.entryId == 1L })
        val taberu = results.hits.first { it.entryId == 1L }
        assertEquals(0 until 2, taberu.titleSegments[1].highlight)
    }

    @Test
    fun `more than three senses collapse into three lines with an overflow mark`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("たべあるき")

        val tabearuki = results.hits.first { it.entryId == 3L }
        assertEquals(3, tabearuki.senseLines.size)
        assertTrue(tabearuki.senseLines.last().endsWith("…"))
        assertEquals("trying the food at various restaurants", tabearuki.senseLines.first())
    }

    @Test
    fun `multiple glosses of one sense join into one line`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("たべもの")

        val tabemono = results.hits.first { it.entryId == 2L }
        assertEquals(listOf("food, foodstuff"), tabemono.senseLines)
    }

    @Test
    fun `a blank query returns nothing`() = runTest {
        val database = seededDatabase()

        assertEquals(emptyList(), database.searchEntries("   ").hits)
    }

    @Test
    fun `a non-positive limit is rejected`() = runTest {
        val database = seededDatabase()

        assertFailsWith<IllegalArgumentException> { database.searchEntries("たべ", limit = 0) }
        assertFailsWith<IllegalArgumentException> { database.searchEntries("たべ", limit = -1) }
    }

    @Test
    fun `the limit caps the hits and flags the larger pool`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("たべ", limit = 2)

        assertEquals(2, results.hits.size)
        assertTrue(results.hasMore)
        assertFalse(database.searchEntries("たべ").hasMore)
    }

    @Test
    fun `accented latin routes to the english path`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("café")

        assertEquals(listOf(6L), results.hits.map { it.entryId })
        assertFalse(results.isFallback)
    }

    @Test
    fun `kanji prefix input matches through the kanji form range query`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("食べ")

        // Entries 3 and 4 share rank 999, so only their membership in
        // the tail is asserted, not their mutual order.
        assertEquals(listOf(1L, 6L, 2L), results.hits.take(3).map { it.entryId })
        assertEquals(setOf(3L, 4L), results.hits.drop(3).map { it.entryId }.toSet())
        val tabemono = results.hits.first { it.entryId == 2L }
        assertEquals(listOf("食べ物", "たべもの"), tabemono.titleSegments.map { it.text })
        assertEquals(0 until 2, tabemono.titleSegments[0].highlight)
        assertNull(tabemono.titleSegments[1].highlight)
    }

    @Test
    fun `an all-kanji query routes to the japanese path`() = runTest {
        val database = seededDatabase()

        // Every other Japanese-routing case in this file contains kana,
        // so the router's han term is otherwise untested: drop it and
        // 食 would fall through to gloss FTS and find nothing, breaking
        // one of the most natural ways to use the app.
        val results = database.searchEntries("食")

        assertEquals(
            setOf(1L, 2L, 3L, 4L, 6L),
            results.hits.map { it.entryId }.toSet(),
            "a han-only query must reach the kanji form range query",
        )
        assertFalse(results.isFallback)
        val tabemono = results.hits.first { it.entryId == 2L }
        assertEquals(0 until 1, tabemono.titleSegments[0].highlight)
    }

    @Test
    fun `fallback truncation never cuts a surrogate pair`() = runTest {
        val database = seededDatabase()

        // 𠮟 is a surrogate pair; the truncation that would end on its
        // high surrogate must be skipped, landing on たべ afterwards.
        val results = database.searchEntries("たべ𠮟zzz")

        assertTrue(results.isFallback)
        assertTrue(results.hits.any { it.entryId == 1L })
        val taberu = results.hits.first { it.entryId == 1L }
        assertEquals(0 until 2, taberu.titleSegments[1].highlight)
    }

    @Test
    fun `fallback truncation gives up after its step budget`() = runTest {
        val database = seededDatabase()

        // 20 garbage chars: たべ is more than 8 truncation steps away.
        val results = database.searchEntries("たべ" + "z".repeat(20))

        assertEquals(emptyList(), results.hits)
        assertFalse(results.isFallback)
    }

    /**
     * The device case that sent this increment back to the drawing
     * board: searching "eat" put 食べる eighth behind entries whose eat
     * gloss sits in a deeply buried sense. Ranking is by where the
     * match sits — sense, then gloss, then word position inside the
     * gloss — and only then by how common the entry is.
     */
    @Test
    fun `english ranking puts the earliest match position first`() = runTest {
        val database = englishRankingDatabase()

        val results = database.searchEntries("eat")

        // 食べる leads; the rank-950 obscurities whose gloss opens on
        // "eat" sink below every common entry, and 遣る's buried sense
        // sinks furthest.
        assertEquals(listOf(300L, 303L, 302L, 304L, 305L, 301L), results.hits.map { it.entryId })
    }

    /**
     * Alex's device case end to end: searching "eat" must lead with
     * 食べる, not with the obscure entries whose gloss happens to start
     * on the matched word.
     */
    @Test
    fun `the device case for eat leads with taberu`() = runTest {
        val database = englishRankingDatabase()

        val results = database.searchEntries("eat")

        assertEquals(300L, results.hits.first().entryId)
        assertTrue(results.hits.first().isCommon)
        // The badge now follows the explicit flag, not a rank threshold.
        assertFalse(results.hits.first { it.entryId == 304L }.isCommon)
    }

    @Test
    fun `commonness outranks the word position inside the gloss`() = runTest {
        val database = englishRankingDatabase()

        val hits = database.searchEntries("eat").hits.map { it.entryId }

        // 304/305 match at word 0 of gloss 0; 300 matches at word 1 of
        // gloss 0 but is the common word, so it leads.
        assertTrue(hits.indexOf(300L) < hits.indexOf(304L))
        assertTrue(hits.indexOf(300L) < hits.indexOf(305L))
    }

    @Test
    fun `a first-gloss match outranks a common entry whose eat sense is buried`() = runTest {
        val database = englishRankingDatabase()

        val hits = database.searchEntries("eat").hits.map { it.entryId }

        // 301 (遣る shape) is exactly as common as 300, but its eat
        // gloss lives in the sixth sense.
        assertTrue(hits.indexOf(300L) < hits.indexOf(301L))
    }

    @Test
    fun `common_rank breaks a tie between equal match positions`() = runTest {
        val database = englishRankingDatabase()

        val hits = database.searchEntries("eat").hits.map { it.entryId }

        // 302 (喫する shape) matches at the very same position as 300;
        // only common_rank separates them — 218 against 125, the
        // ichi1 everyday tier beating raw newspaper frequency.
        assertTrue(hits.indexOf(300L) < hits.indexOf(302L))
    }

    @Test
    fun `an earlier word inside the gloss outranks a later one`() = runTest {
        val database = englishRankingDatabase()

        val hits = database.searchEntries("eat").hits.map { it.entryId }

        // 303 is exactly as common as 300 and matches at gloss 0 too,
        // so the word position decides: word 1 of "to eat" against
        // word 5 of "to have a bite to eat".
        assertTrue(hits.indexOf(300L) < hits.indexOf(303L))
    }

    @Test
    fun `a multi-token query only ranks by glosses carrying every token`() = runTest {
        val database = englishRankingDatabase()

        val hits = database.searchEntries("to eat").hits.map { it.entryId }

        // 301's first gloss is "to do sense 0": it carries "to" but not
        // "eat", so FTS never matched it and it must not rank the entry
        // at the top. Its real match is the buried "to eat" in sense 5.
        assertEquals(300L, hits.first())
        assertEquals(301L, hits.last())
    }

    /**
     * The FTS tokenizer folds diacritics, so `cafe` matches the gloss
     * `café` that plain word matching cannot see. Ranking then has no
     * Kotlin-side position and must fall back to the position SQL
     * computed — decoding it the wrong way round (sense and gloss
     * swapped) would otherwise pass every other test.
     */
    @Test
    fun `a diacritic-folded match ranks on the position sql computed`() = runTest {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        // Same commonness; only the position of the café gloss differs.
        db.entryQueries.insertEntry(400, 125, 1)
        db.entryQueries.insertReading(400, 0, "カフェいち", 1, 125, null, 1)
        db.entryQueries.insertSense(4000, 400, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(4000, 0, "café one")

        db.entryQueries.insertEntry(401, 125, 1)
        db.entryQueries.insertReading(401, 0, "カフェに", 1, 125, null, 1)
        db.entryQueries.insertSense(4010, 401, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(4010, 0, "unrelated gloss")
        db.entryQueries.insertGloss(4010, 1, "unrelated gloss two")
        db.entryQueries.insertSense(4011, 401, 1, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(4011, 0, "café two")

        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()
        val database = DictionaryDatabase(db, driver).also { openedDatabases += it }

        val results = database.searchEntries("cafe")

        // Sense 0 gloss 0 (packed 0) before sense 1 gloss 0 (packed
        // 1000). Swapping the / and % of the decode inverts this.
        assertEquals(listOf(400L, 401L), results.hits.map { it.entryId })
        // The fold is real: neither gloss contains the typed spelling.
        assertTrue(results.hits.all { hit -> hit.senseLines.none { "cafe" in it } })
    }

    @Test
    fun `an english hit shows the sense it matched in even when it is buried`() = runTest {
        val database = englishRankingDatabase()

        val yaru = database.searchEntries("eat").hits.single { it.entryId == 301L }

        // 301's eat gloss lives in its sixth sense; showing only the
        // first three would leave the row with nothing highlighted.
        assertTrue(
            yaru.senseLines.any { "to eat" in it },
            "the matched sense must be on screen, got ${yaru.senseLines}",
        )
        assertEquals(3, yaru.senseLines.size)
        assertTrue(yaru.senseLines.last().endsWith("…"), "dropped senses must still be marked")
    }

    @Test
    fun `a japanese hit keeps its leading senses`() = runTest {
        val database = englishRankingDatabase()

        val yaru = database.searchEntries("やる").hits.single { it.entryId == 301L }

        // No matched sense to prefer: the first three, as before.
        assertEquals("to do sense 0", yaru.senseLines.first())
    }

    @Test
    fun `sql pre-ranking selects the winner out of hundreds of matches`() = runTest {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)
        // 500 entries matching "eat" in a buried sense, inserted first
        // and numbered lower than the winner: any truncation that does
        // not order by match position first would drop entry 2000.
        (1000L until 1500L).forEach { id ->
            db.entryQueries.insertEntry(id, 125, 1)
            db.entryQueries.insertReading(id, 0, "だみー$id", 1, 125, null, 1)
            db.entryQueries.insertSense(id * 10, id, 2, "n", null, null, null, null, null)
            db.entryQueries.insertGloss(id * 10, 5, "filler $id, something to eat")
        }
        db.entryQueries.insertEntry(2000, 950, 0)
        db.entryQueries.insertReading(2000, 0, "たべる", 1, 950, null, 0)
        db.entryQueries.insertSense(20000, 2000, 0, "v1", null, null, null, null, null)
        db.entryQueries.insertGloss(20000, 0, "to eat")
        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()
        val database = DictionaryDatabase(db, driver).also { openedDatabases += it }

        val results = database.searchEntries("eat")

        // Least common entry of the corpus, yet first: position wins.
        assertEquals(2000L, results.hits.first().entryId)
        assertEquals(SEARCH_RESULT_LIMIT, results.hits.size)
        assertTrue(results.hasMore)

        // The ceiling, on a match set larger than the pool. The pool is
        // a constant at every limit, never `limit + 1`: the last page
        // would otherwise be the one page ranked over 401 entries while
        // every earlier page ranked 400, and that 401st entry is fine
        // ranked by a comparator the SQL order knows nothing about, so
        // it could land anywhere among rows already on screen.
        val lastPage = database.searchEntries("eat", limit = SEARCH_MAX_RESULTS)
        assertEquals(SEARCH_MAX_RESULTS, lastPage.hits.size)
        assertFalse(lastPage.hasMore, "paging must stop honestly at the pool")

        // One page short of the ceiling there is still more to show.
        val nextToLast = database.searchEntries("eat", limit = SEARCH_MAX_RESULTS - SEARCH_RESULT_LIMIT)
        assertTrue(nextToLast.hasMore)
        // And what it shows is a prefix of the last page: same pool,
        // same ranking, one longer.
        assertEquals(
            nextToLast.hits.map { it.entryId },
            lastPage.hits.map { it.entryId }.take(nextToLast.hits.size),
        )
    }

    @Test
    fun `english results carry the query tokens for gloss highlighting`() = runTest {
        val database = seededDatabase()

        assertEquals(listOf("to", "eat"), database.searchEntries("to eat").glossTokens)
        // The Japanese path highlights in the title line instead.
        assertEquals(emptyList(), database.searchEntries("たべ").glossTokens)
    }

    /**
     * The shapes the device case was made of, all matching "eat", with
     * the composite ranks their real JMdict tags produce:
     * - 300 食べる 125, "to eat" at sense 0 / gloss 0 / word 1
     * - 301 遣る shape 125, eat gloss buried in sense 5
     * - 302 喫する shape 218, "to eat" at sense 0 / gloss 0
     * - 303 125, gloss 0 but the word sits at position 5
     * - 304 食言 shape 950, gloss 0 with "eat" at word 0
     * - 305 ＤＫ shape 950, gloss 0 with "eat" at word 0
     */
    private suspend fun englishRankingDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)

        db.entryQueries.insertEntry(300, 125, 1)
        db.entryQueries.insertKanjiForm(300, 0, "食べる", 125, 1)
        db.entryQueries.insertReading(300, 0, "たべる", 0, 125, null, 1)
        db.entryQueries.insertSense(3000, 300, 0, "v1", null, null, null, null, null)
        db.entryQueries.insertGloss(3000, 0, "to eat")

        db.entryQueries.insertEntry(301, 125, 1)
        db.entryQueries.insertKanjiForm(301, 0, "遣る", 125, 1)
        db.entryQueries.insertReading(301, 0, "やる", 0, 125, null, 1)
        (0L until 5L).forEach { ord ->
            db.entryQueries.insertSense(3010 + ord, 301, ord, "v5r", null, null, null, null, null)
            db.entryQueries.insertGloss(3010 + ord, 0, "to do sense $ord")
        }
        db.entryQueries.insertSense(3015, 301, 5, "v5r", null, null, null, null, null)
        db.entryQueries.insertGloss(3015, 0, "to smoke")
        db.entryQueries.insertGloss(3015, 1, "to drink")
        db.entryQueries.insertGloss(3015, 2, "to eat")

        db.entryQueries.insertEntry(302, 218, 1)
        db.entryQueries.insertKanjiForm(302, 0, "喫する", 218, 1)
        db.entryQueries.insertReading(302, 0, "きっする", 0, 218, null, 1)
        db.entryQueries.insertSense(3020, 302, 0, "vs-s", null, null, null, null, null)
        db.entryQueries.insertGloss(3020, 0, "to eat")

        db.entryQueries.insertEntry(303, 125, 1)
        db.entryQueries.insertKanjiForm(303, 0, "一口食う", 125, 1)
        db.entryQueries.insertReading(303, 0, "ひとくちくう", 0, 125, null, 1)
        db.entryQueries.insertSense(3030, 303, 0, "v5u", null, null, null, null, null)
        db.entryQueries.insertGloss(3030, 0, "to have a bite to eat")

        // The obscurities that outranked 食べる on the device: their
        // gloss opens on the matched word, but nothing marks them
        // common.
        db.entryQueries.insertEntry(304, 950, 0)
        db.entryQueries.insertKanjiForm(304, 0, "食言", 950, 0)
        db.entryQueries.insertReading(304, 0, "しょくげん", 0, 950, null, 0)
        db.entryQueries.insertSense(3040, 304, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(3040, 0, "eat one's words")

        db.entryQueries.insertEntry(305, 950, 0)
        db.entryQueries.insertReading(305, 0, "ディーケー", 1, 950, null, 0)
        db.entryQueries.insertSense(3050, 305, 0, "n", null, null, null, null, null)
        db.entryQueries.insertGloss(3050, 0, "eat-in kitchen")

        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()
        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    /**
     * A corpus built to break the paging guarantee the way the real
     * dictionary did: one entry carrying several readings under the same
     * prefix, and a common_rank shared by everything so the order is
     * decided entirely by the tie-break.
     *
     * Under the old queries — one row per matching *text*, ordered by
     * common_rank alone — entry 101 spent three of the reading query's
     * rows, so a page of two saw only one entry through the readings and
     * filled the rest from the kanji forms. The next page's larger limit
     * reached entries 102 and 103, which sort above the kanji-form
     * entries already on screen: page one was [101, 201] and page two
     * began [101, 102]. The row the reader was looking at moved.
     *
     * The queries now return one row per entry with its best rank and a
     * total (common_rank, entry_id) order, so a page can only ever be a
     * longer prefix of the same sequence.
     */
    private suspend fun duplicateReadingDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        val db = OkonomiDb(driver)
        val rank = 500L

        // 101 matches the prefix through three readings at once; 102-105
        // through one each.
        db.entryQueries.insertEntry(101, rank, 0)
        listOf("かあ", "かい", "かう").forEachIndexed { ord, text ->
            db.entryQueries.insertReading(101, ord.toLong(), text, 0, rank, null, 0)
        }
        listOf(102L to "かえ", 103L to "かお", 104L to "かき", 105L to "かく")
            .forEach { (id, text) ->
                db.entryQueries.insertEntry(id, rank, 0)
                db.entryQueries.insertReading(id, 0, text, 0, rank, null, 0)
            }
        // Kanji forms that begin with kana are ordinary in JMdict
        // (かき氷, かけ算). They matter here because they arrive from the
        // second of the two prefix queries, each with its own limit.
        listOf(201L to "かき氷", 202L to "かけ算", 203L to "かん詰め", 204L to "かな書き", 205L to "から傘")
            .forEach { (id, text) ->
                db.entryQueries.insertEntry(id, rank, 0)
                db.entryQueries.insertKanjiForm(id, 0, text, rank, 0)
                db.entryQueries.insertReading(id, 0, "よみ$id", 0, rank, null, 0)
            }
        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()
        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `a page of japanese results is always a prefix of the next page`() = runTest {
        val database = duplicateReadingDatabase()

        val pages = listOf(2, 4, 6, 8).map { limit ->
            limit to database.searchEntries("か", limit = limit).hits.map { it.entryId }
        }

        pages.zipWithNext { (shortLimit, shorter), (longLimit, longer) ->
            assertEquals(
                shorter,
                longer.take(shorter.size),
                "page of $shortLimit must be a prefix of page of $longLimit",
            )
        }
        // Named outright, so the assertion above cannot pass on two
        // orders that merely agree with each other while both being
        // wrong. The entry with three matching readings spends one row,
        // not three, and the tie is broken by entry id.
        assertEquals(listOf(101L, 102L), pages.first().second)
    }

    @Test
    fun `awaitList returns an empty list for a query with no rows`() = runTest {
        val database = seededDatabase()

        val rows = database.db.entryQueries.readingsExact(listOf("そんざいしない"), 5).awaitList()

        assertEquals(emptyList(), rows)
    }

    @Test
    fun `the prefix range end sorts after every prefixed string`() {
        val end = prefixRangeEnd("たべ")
        assertEquals("たべ￿", end)
        assertTrue("たべもの" < end)
        assertTrue("たべ" >= "たべ")
    }

    @Test
    fun `the fts sanitizer quotes every token`() {
        assertEquals("\"eat\"", sanitizeFtsQuery("eat"))
        assertEquals("\"to\" \"eat\"", sanitizeFtsQuery("to eat"))
        // Word tokenization strips the metacharacters outright; the
        // quotes then keep a bare operator like NEAR from being one.
        assertEquals("\"NEAR\"", sanitizeFtsQuery("\"NEAR("))
        // Punctuation splits words the same way the FTS tokenizer does,
        // so "eat-in" is an unordered pair, never an adjacent phrase.
        assertEquals("\"eat\" \"in\"", sanitizeFtsQuery("eat-in"))
        assertNull(sanitizeFtsQuery("   "))
    }
}
