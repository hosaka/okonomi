package cc.hosaka.okonomi.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Exercises the name search over a JDBC-seeded database file (see
 * DictionaryInfoLoadTest for why JDBC), through a driver that records
 * every query so the tests can assert what actually reached SQLite
 * rather than what they hoped would.
 *
 * Seeded corpus, all of it person names because dictgen writes nothing
 * else (see isPersonNameType):
 * - 田中 / たなか, surname,person — the combination the type filter keeps
 * - 田仲 / たなか, surname — a second spelling of the same reading
 * - 田中 / たなが, given — the same kanji under another reading
 * - みちこ, fem — kana only, no kanji at all
 * - 高志 / こうし, masc,given — two chips on one row
 * - 大阪 / オオサカ and 大坂 / おおさか, surname — one name whose reading
 *   the source wrote in katakana and one in hiragana
 * - さくらん坊 / あきこ, fem — kana in the *kanji* column, under a reading
 *   that shares nothing with it
 * - さくらこ, fem — a reading match for the same prefix, sorting after it
 */
class NameSearchTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { runCatching { it.close() } }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("namesearch").toFile().also { tempDirs += it }

    private lateinit var driver: RecordingDriver

    private suspend fun seededDatabase(): DictionaryDatabase {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val jdbc = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(jdbc).await()
        driver = RecordingDriver(jdbc)
        val db = OkonomiDb(driver)

        db.nameQueries.insertNameEntry(5000001, "田中", "たなか", "surname,person", "Tanaka")
        db.nameQueries.insertNameEntry(5000002, "田仲", "たなか", "surname", "Tanaka")
        db.nameQueries.insertNameEntry(5000003, "田中", "たなが", "given", "Tanaga")
        db.nameQueries.insertNameEntry(5000004, null, "みちこ", "fem", "Michiko")
        db.nameQueries.insertNameEntry(5000005, "高志", "こうし", "masc,given", "Koushi")
        db.nameQueries.insertNameEntry(5000006, "大阪", "オオサカ", "surname", "Oosaka")
        db.nameQueries.insertNameEntry(5000007, "大坂", "おおさか", "surname", "Oosaka")
        db.nameQueries.insertNameEntry(5000008, "さくらん坊", "あきこ", "fem", "Akiko")
        db.nameQueries.insertNameEntry(5000009, null, "さくらこ", "fem", "Sakurako")
        // A written form continuing into the supplementary plane; see
        // the prefix-range test for what it pins.
        db.nameQueries.insertNameEntry(5000010, "田𠮷", "たよし", "surname", "Tayoshi")

        // Seeding goes through execute(), not executeQuery(), but clear
        // anyway so a test that counts queries counts only its own.
        driver.executed.clear()
        return DictionaryDatabase(db, driver).also { openedDatabases += it }
    }

    @Test
    fun `a reading prefix finds every spelling under it`() = runTest {
        val results = seededDatabase().searchNames("たなか")

        assertEquals(listOf("田中", "田仲"), results.hits.map { it.kanji })
        assertFalse(results.hasMore)
    }

    @Test
    fun `a kanji prefix goes through the kanji column`() = runTest {
        val results = seededDatabase().searchNames("田中")

        assertEquals(
            listOf("たなか", "たなが"),
            results.hits.map { it.reading },
            "one written form can be read more than one way, and both are names",
        )
    }

    /** The matrix's kana-only row: a reading with no kanji is still a name. */
    @Test
    fun `a name written only in kana comes back with no kanji`() = runTest {
        val hit = seededDatabase().searchNames("みちこ").hits.single()

        assertEquals(null, hit.kanji)
        assertEquals("みちこ", hit.reading)
        assertEquals(listOf("fem"), hit.types)
        assertEquals("Michiko", hit.romanisation)
    }

    /**
     * A reading is stored in whichever script JMnedict used — 8,973 of
     * them contain katakana — and a SQL range comparison is byte order,
     * not kana equivalence. Before the query was folded both ways,
     * オオサカ found nothing at all while おおさか found the same names.
     */
    @Test
    fun `a katakana query finds hiragana readings and the other way round`() = runTest {
        val db = seededDatabase()

        assertEquals(
            listOf("オオサカ", "おおさか"),
            db.searchNames("オオサカ").hits.map { it.reading }.sortedDescending(),
            "a katakana query must reach the hiragana reading",
        )
        assertEquals(
            listOf("オオサカ", "おおさか"),
            db.searchNames("おおさか").hits.map { it.reading }.sortedDescending(),
            "and a hiragana query the katakana one",
        )
    }

    /**
     * 6,735 rows are written in kana, so a kana query matches the `kanji`
     * column as well as `reading` — and the two sets are not the same
     * rows. Concatenating them put every kanji-column hit behind every
     * reading hit, which for a real prefix meant page seven. One UNION
     * under one total order puts each row where its reading belongs.
     */
    @Test
    fun `a kanji-column hit is ordered among the reading hits and not appended after them`() = runTest {
        val results = seededDatabase().searchNames("さくら")

        assertEquals(
            listOf("あきこ", "さくらこ"),
            results.hits.map { it.reading },
            "あきこ matches through the kanji column alone and sorts first; appending it would put it last",
        )
    }

    /**
     * The chips a row shows are the person-name codes only. `person`
     * rides along on 525 kept rows and says nothing a reader needs; it
     * must not become a chip.
     */
    @Test
    fun `only person-name codes are offered as chips and their order is fixed`() = runTest {
        val db = seededDatabase()

        assertEquals(listOf("surname"), db.searchNames("たなか").hits.first().types)
        assertEquals(
            listOf("given", "masc"),
            db.searchNames("こうし").hits.single().types,
            "the display order is this code list's, not the row's",
        )
    }

    /**
     * Names are not searched in romaji (Alex's ruling), and the point is
     * not that the result is empty — the corpus would give an empty
     * result anyway, so an emptiness assertion passes with the guard
     * deleted. What has to hold is that **no query is issued**: search
     * runs on every keystroke, and this is the same promise the toggle
     * itself makes.
     */
    @Test
    fun `a romaji query issues no query at all`() = runTest {
        val db = seededDatabase()

        assertEquals(emptyList(), db.searchNames("Tanaka").hits)
        assertEquals(emptyList(), db.searchNames("   ").hits)

        assertEquals(
            emptyList(),
            driver.executed.map { it.sql },
            "a query the name search cannot answer must never reach the database",
        )

        // The same call with Japanese input does reach it, or the zero
        // above would hold however the search were broken.
        db.searchNames("たなか")
        assertEquals(1, driver.executed.size)
    }

    @Test
    fun `pages are fetched by offset and nest rather than overlapping`() = runTest {
        val db = seededDatabase()

        val firstPage = db.searchNames("た", offset = 0, limit = 2)
        assertEquals(2, firstPage.hits.size)
        assertTrue(firstPage.hasMore)

        val secondPage = db.searchNames("た", offset = 2, limit = 2)
        assertEquals(2, secondPage.hits.size, "four rows read た; the second page holds the rest of them")
        assertFalse(secondPage.hasMore)

        val whole = db.searchNames("た", offset = 0, limit = 10)
        assertEquals(
            whole.hits,
            firstPage.hits + secondPage.hits,
            "paging by offset must reconstruct exactly the undivided list, in order",
        )
    }

    /**
     * [prefixRangeEnd] closes the range with U+FFFF, which under
     * SQLite's BINARY collation — UTF-8 byte order — sorts before every
     * supplementary-plane character rather than after it. So a stored
     * form whose *next* character is supplementary falls outside a
     * prefix range that should contain it.
     *
     * That loss is documented and accepted, and nothing in the shipped
     * data hits it. It is pinned here in both directions because "no
     * shipped row does this" is luck rather than a property: whoever
     * changes the bound should see which behaviour they are changing.
     */
    @Test
    fun `a prefix range stops short of a supplementary-plane continuation`() = runTest {
        val db = seededDatabase()

        assertFalse(
            "たよし" in db.searchNames("田", limit = 50).hits.map { it.reading },
            "田𠮷 continues into the supplementary plane and falls outside the 田 range",
        )
        assertEquals(
            listOf("たよし"),
            db.searchNames("田𠮷").hits.map { it.reading },
            "typing the whole form still finds it, so the loss is only the shorter prefix",
        )
    }

    @Test
    fun `a prefix nothing starts with returns nothing`() = runTest {
        assertEquals(emptyList(), seededDatabase().searchNames("ぬぬぬ").hits)
    }

    @Test
    fun `a non-positive limit or negative offset is a programming error`() = runTest {
        val db = seededDatabase()
        assertFailsWith<IllegalArgumentException> { db.searchNames("たなか", limit = 0) }
        assertFailsWith<IllegalArgumentException> { db.searchNames("たなか", offset = -1) }
    }

    /**
     * The constraint that made this a rewrite rather than a first use of
     * the old `searchNamePrefix`: `LIKE :prefix || '%'` cannot use an
     * index, so every keystroke scanned all 333,481 rows — 43 ms against
     * 0.6 ms here.
     *
     * The plan is taken from the SQL **SQLDelight generated**, captured
     * off the driver and re-explained with the very binder it built, so
     * the test cannot drift from the query. Asserting the absence of
     * SCAN rather than the presence of an index name is what makes it
     * fail for its stated reason: any future rewrite that gives up the
     * index fails here whatever it is called.
     */
    @Test
    fun `the prefix query never scans the table`() = runTest {
        val db = seededDatabase()
        db.searchNames("た")

        val plan = driver.queryPlan(driver.executed.single())

        assertFalse(
            "SCAN" in plan,
            "the name prefix query must not scan name_entry. Plan was:\n$plan",
        )
        assertTrue("name_entry" in plan, "the plan should be about name_entry at all:\n$plan")
    }
}

/**
 * Delegating driver that keeps every query it was asked to run, together
 * with the binder that supplied its parameters, so a test can count what
 * was issued and re-explain it exactly as it ran.
 */
private class RecordingDriver(
    private val delegate: SqlDriver,
) : SqlDriver by delegate {

    val executed = mutableListOf<Executed>()

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        executed += Executed(sql, parameters, binders)
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }

    fun queryPlan(query: Executed): String = delegate.executeQuery(
        null,
        "EXPLAIN QUERY PLAN ${query.sql}",
        { cursor ->
            val plan = StringBuilder()
            while (cursor.next().value) {
                // Column 3 of EXPLAIN QUERY PLAN is the human-readable detail.
                plan.append(cursor.getString(3)).append('\n')
            }
            QueryResult.Value(plan.toString())
        },
        query.parameters,
        query.binders,
    ).value

    class Executed(
        val sql: String,
        val parameters: Int,
        val binders: (SqlPreparedStatement.() -> Unit)?,
    )
}
