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
        db.entryQueries.insertSense(10, 1, 0, "v1,vt", null, null, null, null)
        db.entryQueries.insertGloss(10, 0, "to eat")
        db.entryQueries.insertSense(11, 1, 1, "v1,vt", null, null, null, null)
        db.entryQueries.insertGloss(11, 0, "to live on")

        db.entryQueries.insertEntry(2, 145, 1)
        db.entryQueries.insertKanjiForm(2, 0, "食べ物", 145, 1)
        db.entryQueries.insertReading(2, 0, "たべもの", 0, 145, null, 1)
        db.entryQueries.insertSense(20, 2, 0, "n", null, null, null, null)
        db.entryQueries.insertGloss(20, 0, "food")
        db.entryQueries.insertGloss(20, 1, "foodstuff")

        db.entryQueries.insertEntry(3, 950, 0)
        db.entryQueries.insertKanjiForm(3, 0, "食べ歩き", 950, 0)
        db.entryQueries.insertReading(3, 0, "たべあるき", 0, 950, null, 0)
        db.entryQueries.insertSense(30, 3, 0, "n", null, null, null, null)
        db.entryQueries.insertGloss(30, 0, "trying the food at various restaurants")
        db.entryQueries.insertSense(31, 3, 1, "n", null, null, null, null)
        db.entryQueries.insertGloss(31, 0, "gourmet food tour")
        db.entryQueries.insertSense(32, 3, 2, "n", null, null, null, null)
        db.entryQueries.insertGloss(32, 0, "eating while walking")
        db.entryQueries.insertSense(33, 3, 3, "n", null, null, null, null)
        db.entryQueries.insertGloss(33, 0, "restaurant hopping")

        // Homograph of 食べる that is not a verb: a deinflection
        // candidate must never surface it.
        db.entryQueries.insertEntry(4, 950, 0)
        db.entryQueries.insertKanjiForm(4, 0, "食べる", 950, 0)
        db.entryQueries.insertReading(4, 0, "はむ", 0, 950, null, 0)
        db.entryQueries.insertSense(40, 4, 0, "n", null, null, null, null)
        db.entryQueries.insertGloss(40, 0, "fictional noun homograph")

        // More common than 食べる, prefix-matches 食べた: the
        // deinflected exact hit must still rank first.
        db.entryQueries.insertEntry(6, 105, 1)
        db.entryQueries.insertKanjiForm(6, 0, "食べた口", 105, 1)
        db.entryQueries.insertReading(6, 0, "たべたくち", 0, 105, null, 1)
        db.entryQueries.insertSense(60, 6, 0, "n", null, null, null, null)
        db.entryQueries.insertGloss(60, 0, "fictional test noun")
        db.entryQueries.insertGloss(60, 1, "café drink")

        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()

        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `prefix search matches readings, highlights the prefix and ranks by common_rank`() = runTest {
        val database = seededDatabase()

        val results = database.searchEntries("たべ")

        assertFalse(results.isFallback)
        assertEquals(listOf(6L, 1L, 2L, 3L), results.hits.map { it.entryId })

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
        assertEquals(listOf(6L, 1L, 2L), results.hits.take(3).map { it.entryId })
        assertEquals(setOf(3L, 4L), results.hits.drop(3).map { it.entryId }.toSet())
        val tabemono = results.hits.first { it.entryId == 2L }
        assertEquals(listOf("食べ物", "たべもの"), tabemono.titleSegments.map { it.text })
        assertEquals(0 until 2, tabemono.titleSegments[0].highlight)
        assertNull(tabemono.titleSegments[1].highlight)
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
        db.entryQueries.insertSense(4000, 400, 0, "n", null, null, null, null)
        db.entryQueries.insertGloss(4000, 0, "café one")

        db.entryQueries.insertEntry(401, 125, 1)
        db.entryQueries.insertReading(401, 0, "カフェに", 1, 125, null, 1)
        db.entryQueries.insertSense(4010, 401, 0, "n", null, null, null, null)
        db.entryQueries.insertGloss(4010, 0, "unrelated gloss")
        db.entryQueries.insertGloss(4010, 1, "unrelated gloss two")
        db.entryQueries.insertSense(4011, 401, 1, "n", null, null, null, null)
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
            db.entryQueries.insertSense(id * 10, id, 2, "n", null, null, null, null)
            db.entryQueries.insertGloss(id * 10, 5, "filler $id, something to eat")
        }
        db.entryQueries.insertEntry(2000, 950, 0)
        db.entryQueries.insertReading(2000, 0, "たべる", 1, 950, null, 0)
        db.entryQueries.insertSense(20000, 2000, 0, "v1", null, null, null, null)
        db.entryQueries.insertGloss(20000, 0, "to eat")
        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()
        val database = DictionaryDatabase(db, driver).also { openedDatabases += it }

        val results = database.searchEntries("eat")

        // Least common entry of the corpus, yet first: position wins.
        assertEquals(2000L, results.hits.first().entryId)
        assertEquals(SEARCH_RESULT_LIMIT, results.hits.size)
        assertTrue(results.hasMore)
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
        db.entryQueries.insertSense(3000, 300, 0, "v1", null, null, null, null)
        db.entryQueries.insertGloss(3000, 0, "to eat")

        db.entryQueries.insertEntry(301, 125, 1)
        db.entryQueries.insertKanjiForm(301, 0, "遣る", 125, 1)
        db.entryQueries.insertReading(301, 0, "やる", 0, 125, null, 1)
        (0L until 5L).forEach { ord ->
            db.entryQueries.insertSense(3010 + ord, 301, ord, "v5r", null, null, null, null)
            db.entryQueries.insertGloss(3010 + ord, 0, "to do sense $ord")
        }
        db.entryQueries.insertSense(3015, 301, 5, "v5r", null, null, null, null)
        db.entryQueries.insertGloss(3015, 0, "to smoke")
        db.entryQueries.insertGloss(3015, 1, "to drink")
        db.entryQueries.insertGloss(3015, 2, "to eat")

        db.entryQueries.insertEntry(302, 218, 1)
        db.entryQueries.insertKanjiForm(302, 0, "喫する", 218, 1)
        db.entryQueries.insertReading(302, 0, "きっする", 0, 218, null, 1)
        db.entryQueries.insertSense(3020, 302, 0, "vs-s", null, null, null, null)
        db.entryQueries.insertGloss(3020, 0, "to eat")

        db.entryQueries.insertEntry(303, 125, 1)
        db.entryQueries.insertKanjiForm(303, 0, "一口食う", 125, 1)
        db.entryQueries.insertReading(303, 0, "ひとくちくう", 0, 125, null, 1)
        db.entryQueries.insertSense(3030, 303, 0, "v5u", null, null, null, null)
        db.entryQueries.insertGloss(3030, 0, "to have a bite to eat")

        // The obscurities that outranked 食べる on the device: their
        // gloss opens on the matched word, but nothing marks them
        // common.
        db.entryQueries.insertEntry(304, 950, 0)
        db.entryQueries.insertKanjiForm(304, 0, "食言", 950, 0)
        db.entryQueries.insertReading(304, 0, "しょくげん", 0, 950, null, 0)
        db.entryQueries.insertSense(3040, 304, 0, "n", null, null, null, null)
        db.entryQueries.insertGloss(3040, 0, "eat one's words")

        db.entryQueries.insertEntry(305, 950, 0)
        db.entryQueries.insertReading(305, 0, "ディーケー", 1, 950, null, 0)
        db.entryQueries.insertSense(3050, 305, 0, "n", null, null, null, null)
        db.entryQueries.insertGloss(3050, 0, "eat-in kitchen")

        driver.execute(null, "INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')", 0).await()
        return DictionaryDatabase(db, driver).also { openedDatabases += it }
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
