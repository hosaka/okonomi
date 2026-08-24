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

/**
 * The `entry` table's `common_rank`/`is_common` are denormalized from
 * the entry's forms and readings so the search can order by commonness
 * inside SQL. This pins the rules that fold many forms into one value.
 */
class DbWriterEntryRankTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempFile(): File =
        Files.createTempDirectory("dbwriter").toFile().also { tempDirs += it }.resolve("okonomi.db")

    private fun sense(vararg glosses: String) = JmdictSense(
        pos = "n",
        misc = null,
        field = null,
        dial = null,
        info = null,
        restrictions = null,
        glosses = glosses.toList(),
    )

    private fun form(text: String, tags: List<String>) =
        JmdictKanjiForm(text, PriorityRank.rank(tags), PriorityRank.isCommon(tags))

    private fun reading(text: String, tags: List<String>) = JmdictReading(
        text = text,
        noKanji = false,
        commonRank = PriorityRank.rank(tags),
        isCommon = PriorityRank.isCommon(tags),
        restrictions = null,
    )

    private fun write(vararg entries: JmdictEntry): OkonomiDb {
        val target = tempFile()
        DbWriter(target).use { writer ->
            writer.writeJmdictEntries(entries.toList())
        }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${target.absolutePath}")
        return OkonomiDb(driver)
    }

    @Test
    fun aCommonReadingWithNoKanjiFormStillMarksTheEntryCommon() {
        // ありがとう / こんにちは class: kana-only headwords whose
        // priority tags live on the reading. Folding only over kanji
        // forms would silently drop their badge.
        val db = write(
            JmdictEntry(
                id = 1,
                kanjiForms = emptyList(),
                readings = listOf(reading("ありがとう", listOf("ichi1", "nf12"))),
                senses = listOf(sense("thank you")),
            ),
        )

        val entry = db.entryQueries.entriesForIds(listOf(1L)).executeAsList().single()
        assertEquals(1L, entry.is_common)
        assertEquals(112L, entry.common_rank)
    }

    @Test
    fun aCommonKanjiFormWithAnUncommonReadingStillMarksTheEntryCommon() {
        val db = write(
            JmdictEntry(
                id = 2,
                kanjiForms = listOf(form("食べる", listOf("ichi1", "nf25"))),
                readings = listOf(reading("タベル", emptyList())),
                senses = listOf(sense("to eat")),
            ),
        )

        val entry = db.entryQueries.entriesForIds(listOf(2L)).executeAsList().single()
        assertEquals(1L, entry.is_common)
        // The best rank across the forms, not the first or the worst.
        assertEquals(125L, entry.common_rank)
    }

    @Test
    fun anEntryWithNoPriorityTagsAnywhereIsNotCommon() {
        val db = write(
            JmdictEntry(
                id = 3,
                kanjiForms = listOf(form("食言", emptyList())),
                readings = listOf(reading("しょくげん", emptyList())),
                senses = listOf(sense("eat one's words")),
            ),
        )

        val entry = db.entryQueries.entriesForIds(listOf(3L)).executeAsList().single()
        assertEquals(0L, entry.is_common)
        assertEquals(950L, entry.common_rank)
    }

    @Test
    fun anEntryWithNoFormsAtAllGetsTheUnrankedValue() {
        val db = write(
            JmdictEntry(id = 4, kanjiForms = emptyList(), readings = emptyList(), senses = listOf(sense("orphan"))),
        )

        val entry = db.entryQueries.entriesForIds(listOf(4L)).executeAsList().single()
        assertEquals(950L, entry.common_rank)
        assertEquals(0L, entry.is_common)
    }

    @Test
    fun aSenseTooLargeForThePositionPackingFailsGeneration() {
        val glosses = Array(GLOSS_POSITION_FACTOR) { "gloss $it" }

        val e = assertFailsWith<PipelineException> {
            write(
                JmdictEntry(
                    id = 5,
                    kanjiForms = emptyList(),
                    readings = listOf(reading("あ", emptyList())),
                    senses = listOf(sense(*glosses)),
                ),
            )
        }

        assertTrue(GLOSS_POSITION_FACTOR.toString() in (e.message ?: ""), "message should name the limit: ${e.message}")
    }
}
