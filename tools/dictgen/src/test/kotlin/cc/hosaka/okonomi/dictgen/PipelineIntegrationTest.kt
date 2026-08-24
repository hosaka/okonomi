package cc.hosaka.okonomi.dictgen

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.hosaka.okonomi.db.OkonomiDb
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PipelineIntegrationTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("dictgen").toFile().also { tempDirs += it }

    private fun generate(): File {
        val dataDir = tempDir()
        Fixtures.writeDataDir(dataDir)
        val out = File(tempDir(), "okonomi.db")
        val summary = Pipeline(dataDir, out).run()
        assertEquals(1L, summary.counts["entries"])
        assertEquals(2L, summary.counts["glosses"])
        assertEquals(1L, summary.counts["kanji"])
        assertEquals(2L, summary.counts["radicals"])
        assertEquals(1L, summary.counts["names"])
        // Five JMdict entities plus JMnedict's one.
        assertEquals(6L, summary.counts["tags"])
        assertTrue(summary.sizeBytes > 0)
        return out
    }

    private fun <T> withDb(file: File, block: (OkonomiDb) -> T): T {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        try {
            return block(OkonomiDb(driver))
        } finally {
            driver.close()
        }
    }

    @Test
    fun generatesSearchableDatabase() {
        val out = generate()
        withDb(out) { db ->
            val ftsHits = db.ftsQueries.searchGlossFtsRankedEntryIds("\"eat\"", 10).executeAsList()
            assertEquals(listOf(1358280L), ftsHits.map { it.entry_id }, "FTS should find 食べる")
            // Sense 0, gloss 0: the packed position the ranking sorts on.
            assertEquals(0L, ftsHits.single().pos)

            val words = db.entryQueries.wordsContainingKanji("食", 10).executeAsList()
            assertTrue("食べる" in words, "entry_kanji join should surface 食べる, got $words")

            assertEquals(Fixtures.JMDICT_DATE, db.metadataQueries.metadataValue("jmdict_date").executeAsOne())
        }
    }

    @Test
    fun persistsReadingDetailsAndRanks() {
        withDb(generate()) { db ->
            val readings = db.entryQueries.readingsForEntries(listOf(1358280L)).executeAsList()
            assertEquals(2, readings.size)
            assertEquals(125L, readings[0].common_rank)
            assertEquals(1L, readings[0].is_common)
            val second = readings[1]
            assertEquals("タベル", second.text)
            assertEquals(950L, second.common_rank)
            assertEquals(0L, second.is_common, "a reading with no priority tags is not common")

            val forms = db.entryQueries.kanjiFormsForEntries(listOf(1358280L)).executeAsList()
            assertEquals(125L, forms.single().common_rank)
            assertEquals(1L, forms.single().is_common)

            // no_kanji and restrictions live on the single-entry query.
            val detailed = db.entryQueries.readingsForEntry(1358280L).executeAsList()
            assertEquals(0L, detailed[0].no_kanji)
            assertEquals(1L, detailed[1].no_kanji)
            assertEquals("食べる", detailed[1].restrictions)
        }
    }

    @Test
    fun persistsKanjiDetailsWithFiltersApplied() {
        withDb(generate()) { db ->
            val kanji = db.kanjiQueries.kanjiByLiteral("食").executeAsOne()
            assertEquals(2L, kanji.grade)
            assertEquals(9L, kanji.stroke_count)
            assertEquals(328L, kanji.freq)
            assertEquals(3L, kanji.jlpt)

            val readings = db.kanjiQueries.kanjiReadings("食").executeAsList().map { it.type to it.text }.toSet()
            assertEquals(setOf("on" to "ショク", "kun" to "た.べる", "nanori" to "ぐい"), readings)

            assertEquals(listOf("eat"), db.kanjiQueries.kanjiMeanings("食").executeAsList())

            // Fixture 倉 is in radkfile but not kanjidic; kanjiByRadical inner-joins kanji,
            // so only characters with kanjidic data come back (in real data the sets match).
            val byRadical = db.kanjiQueries.kanjiByRadical("一").executeAsList().map { it.literal }.toSet()
            assertEquals(setOf("食"), byRadical)
        }
    }

    @Test
    fun persistsNameRows() {
        withDb(generate()) { db ->
            val name = db.nameQueries.searchNamePrefix("しめえ", 10).executeAsList().single()
            assertEquals(5000002L, name.id)
            assertEquals("〆ヱ", name.kanji)
            assertEquals("しめえ", name.reading)
            assertEquals("fem", name.name_type)
            assertEquals("Shimee", name.translation)
        }
    }

    @Test
    fun populatesTagLabelsFromBothDtdsInTheSourceCasing() {
        withDb(generate()) { db ->
            val labels = db.tagQueries
                .labelsForCodes(listOf("v1", "vt", "uk", "food", "ksb", "fem", "nonsense"))
                .executeAsList()
                .associate { it.code to it.label }
            assertEquals(
                mapOf(
                    // Casing is the source's: lowercasing is the entry
                    // view's presentation choice, not the file's.
                    "v1" to "Ichidan verb",
                    "vt" to "transitive verb",
                    "uk" to "word usually written using kana alone",
                    "food" to "food, cooking",
                    "ksb" to "Kansai-ben",
                    // JMnedict's DTD is unioned in, so name types resolve too.
                    "fem" to "female given name or forename",
                ),
                labels,
                "entity expansions should be stored verbatim, unknown codes absent",
            )
            assertEquals(6L, db.tagQueries.tagLabelCount().executeAsOne())
        }
    }

    @Test
    fun persistsSenseCodesAndRestrictions() {
        withDb(generate()) { db ->
            val senses = db.entryQueries.sensesForEntry(1358280L).executeAsList()
            assertEquals("v1,vt", senses[0].pos)
            assertEquals("food", senses[0].field_)
            assertEquals("ksb", senses[0].dial)
            assertEquals("食べる", senses[0].restrictions)
            assertEquals("たべる;タベル", senses[1].restrictions)
            assertEquals("colloquial", senses[1].info)
        }
    }

    @Test
    fun mergedEntitiesKeepsTheFirstSourcesWording() {
        val merged = mergedEntities(
            mapOf("m-sl" to "manga slang"),
            mapOf("m-sl" to "manga slang (names)", "fem" to "female given name"),
        )
        assertEquals(mapOf("m-sl" to "manga slang", "fem" to "female given name"), merged)
    }

    @Test
    fun schemaAndFormatVersionsArePinned() {
        // Literals on purpose: every other assertion here compares a
        // version to itself and would pass at any value. Both counters
        // started at 1 pre-release; the schema version only moves with
        // the DDL (the read-only database is regenerated, never
        // migrated), so the tag_label increment moved the format
        // version alone.
        assertEquals(1L, OkonomiDb.Schema.version)
        assertEquals(2, DICTIONARY_FORMAT_VERSION)
    }

    @Test
    fun bakesUserVersionAndWritesSidecar() {
        val out = generate()
        // PRAGMA goes through the raw driver: it is not part of the schema queries.
        val driver = JdbcSqliteDriver("jdbc:sqlite:${out.absolutePath}")
        try {
            val userVersion = driver.executeQuery(
                null,
                "PRAGMA user_version",
                { cursor ->
                    cursor.next()
                    app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0))
                },
                0,
            ).value
            assertEquals(1L, userVersion)
            assertEquals(OkonomiDb.Schema.version, userVersion)
        } finally {
            driver.close()
        }

        val sidecar = File(out.parentFile, out.name + ".version")
        assertTrue(sidecar.isFile, "sidecar should be written next to the database")
        // Literals on purpose: the sidecar is the only thing that makes
        // a device re-copy, so a silent version regression must fail here.
        assertEquals("${Fixtures.JMDICT_DATE}:1:2", sidecar.readText())
        assertEquals(
            "${Fixtures.JMDICT_DATE}:${OkonomiDb.Schema.version}:$DICTIONARY_FORMAT_VERSION",
            sidecar.readText(),
        )
        assertTrue(!File(sidecar.parentFile, sidecar.name + ".tmp").exists(), "sidecar tmp file should be moved away")
    }

    @Test
    fun missingJmdictCreationDateFailsGeneration() {
        val dataDir = tempDir()
        Fixtures.writeDataDir(dataDir)
        val jmdict = File(dataDir, "JMdict_e_examp.xml")
        jmdict.writeText(jmdict.readText().replace(Regex("<!-- JMdict created: .* -->"), ""))
        val out = File(tempDir(), "okonomi.db")

        val e = assertFailsWith<PipelineException> { Pipeline(dataDir, out).run() }
        assertTrue("JMdict created" in (e.message ?: ""), "message should name the missing comment: ${e.message}")
        assertTrue(!out.exists(), "no database should be left behind")
        assertTrue(!File(out.parentFile, out.name + ".version").exists(), "no sidecar should be left behind")
    }

    @Test
    fun rerunReplacesPreviousDatabase() {
        val dataDir = tempDir()
        Fixtures.writeDataDir(dataDir)
        val out = File(tempDir(), "okonomi.db")
        Pipeline(dataDir, out).run()
        Pipeline(dataDir, out).run()
        assertTrue(out.isFile)
        assertTrue(out.length() > 0)
        assertTrue(!File(out.parentFile, out.name + ".tmp").exists(), "tmp file should be moved away")
    }

    @Test
    fun missingSourceFailsFastWithFileName() {
        val emptyDir = tempDir()
        val out = File(tempDir(), "okonomi.db")
        val e = assertFailsWith<PipelineException> { Pipeline(emptyDir, out).run() }
        assertTrue("JMdict_e_examp.xml" in (e.message ?: ""), "message should name the file: ${e.message}")
        assertTrue(!out.exists(), "no partial DB should be left behind")
    }
}
