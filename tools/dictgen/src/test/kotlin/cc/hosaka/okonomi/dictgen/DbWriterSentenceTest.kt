package cc.hosaka.okonomi.dictgen

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.hosaka.okonomi.db.OkonomiDb
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rules that turn 1.17 million B-line words into at most ten
 * sentences per entry: which ten, in what order, and what happens to
 * the sentences nothing keeps.
 */
class DbWriterSentenceTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("dbwriter-sentence").toFile().also { tempDirs += it }

    private fun sense(vararg glosses: String) = JmdictSense(
        pos = "v1",
        misc = null,
        field = null,
        dial = null,
        info = null,
        restrictions = null,
        glosses = glosses.toList(),
    )

    private fun entry(id: Long, form: String, reading: String, tags: List<String> = emptyList()) = JmdictEntry(
        id = id,
        kanjiForms = listOf(JmdictKanjiForm(form, PriorityRank.rank(tags), PriorityRank.isCommon(tags))),
        readings = listOf(
            JmdictReading(reading, false, PriorityRank.rank(tags), PriorityRank.isCommon(tags), null),
        ),
        senses = listOf(sense("gloss")),
    )

    /**
     * Writes [entries] and then the Tatoeba rows, and hands back the
     * finished file's queries. [indices] rows are `jpn_id⇥eng_id⇥B-line`;
     * every Japanese and English id they name is generated for them.
     */
    private fun write(entries: List<JmdictEntry>, japanese: List<String>, indices: List<String>): OkonomiDb {
        val dir = tempDir()
        val target = File(dir, "okonomi.db")
        val japaneseFile = File(dir, "jpn_sentences.tsv").apply {
            writeText(japanese.mapIndexed { index, text -> "${index + 1}\tjpn\t$text" }.joinToString("\n"))
        }
        val englishFile = File(dir, "eng_sentences.tsv").apply {
            writeText(japanese.indices.joinToString("\n") { "${it + 1}\teng\ttranslation ${it + 1}" })
        }
        val indexFile = File(dir, "jpn_indices.csv").apply { writeText(indices.joinToString("\n")) }
        DbWriter(target).use { writer ->
            writer.writeJmdictEntries(entries)
            writer.writeTatoeba(TatoebaParser(japaneseFile, englishFile, indexFile))
        }
        return OkonomiDb(JdbcSqliteDriver("jdbc:sqlite:${target.absolutePath}"))
    }

    /** A sentence of exactly [length] characters that uses 食べる. */
    private fun sentenceOfLength(length: Int) = "あ".repeat(length - 3) + "食べる"

    @Test
    fun keepsTheTenMostReadableSentencesPerEntry() {
        // Twelve candidates: nine inside the readable band, two below it
        // and one above. The band comes first and is ordered by length
        // within itself; the shortest sentence of all is kept only
        // because a slot is left over, and lands last.
        val lengths = listOf(16, 9, 25, 11, 4, 13, 8, 15, 6, 10, 14, 12)
        val db = write(
            entries = listOf(entry(1L, "食べる", "たべる")),
            japanese = lengths.map { sentenceOfLength(it) },
            indices = lengths.indices.map { "${it + 1}\t${it + 1}\t食べる" },
        )

        val kept = db.sentenceQueries.sentencesForEntry(1L).executeAsList()
        assertEquals(SENTENCES_PER_ENTRY, kept.size)
        assertEquals(
            listOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 4).map { sentenceOfLength(it) },
            kept.map { it.japanese },
        )
    }

    @Test
    fun prefersTheSentenceVerifiedForThisEntry() {
        val db = write(
            entries = listOf(entry(1L, "食べる", "たべる"), entry(2L, "犬", "いぬ"), entry(3L, "猫", "ねこ")),
            // Both are eight characters and inside the band, so only the
            // tilde can order them.
            japanese = listOf("犬があれを食べる", "猫があれを食べる"),
            // The first sentence's tilde sits on 犬 and the second's on
            // 食べる. An editor vouching for a sentence as an example of
            // 犬 says nothing about its worth as an example of 食べる, so
            // the second must lead for 食べる and the first for 犬.
            indices = listOf("1\t1\t犬~ が 食べる", "2\t2\t猫 が 食べる~"),
        )

        assertEquals(
            listOf("猫があれを食べる", "犬があれを食べる"),
            db.sentenceQueries.sentencesForEntry(1L).executeAsList().map { it.japanese },
            "reading the tilde as a property of the whole sentence would order these by id instead",
        )
        assertEquals(
            "犬があれを食べる",
            db.sentenceQueries.sentencesForEntry(2L).executeAsList().single().japanese,
        )
    }

    @Test
    fun collapsesSentencesThatDifferOnlyInHowTheyEnd() {
        val db = write(
            entries = listOf(entry(1L, "食べる", "たべる")),
            japanese = listOf("教室で食べるの。", "教室で食べるの？", "廊下で食べるの。"),
            indices = (1..3).map { "$it\t$it\t食べる" },
        )

        assertEquals(
            listOf("教室で食べるの。", "廊下で食べるの。"),
            db.sentenceQueries.sentencesForEntry(1L).executeAsList().map { it.japanese },
            "one sentence must not take two of an entry's ten slots",
        )
    }

    @Test
    fun prunesSentencesNoLinkReferences() {
        val db = write(
            entries = listOf(entry(1L, "食べる", "たべる")),
            japanese = listOf("食べる", "泳ぐ"),
            // 泳ぐ is in no entry, so its sentence keeps no link at all.
            indices = listOf("1\t1\t食べる", "2\t2\t泳ぐ"),
        )

        assertEquals(1L, db.sentenceQueries.sentenceCount().executeAsOne())
        assertEquals("食べる", db.sentenceQueries.sentencesForEntry(1L).executeAsList().single().japanese)
    }

    @Test
    fun aSentenceNamingOneEntryTwiceLinksItOnce() {
        val db = write(
            entries = listOf(entry(1L, "食べる", "たべる")),
            japanese = listOf("食べる食べる"),
            indices = listOf("1\t1\t食べる 食べる{食べた}"),
        )

        assertEquals(1L, db.sentenceQueries.entrySentenceCount().executeAsOne())
    }

    @Test
    fun linksEverySeparateEntryOneSentenceNames() {
        val db = write(
            entries = listOf(entry(1L, "食べる", "たべる"), entry(2L, "水", "みず")),
            japanese = listOf("水を食べる"),
            indices = listOf("1\t1\t水 を 食べる"),
        )

        assertEquals(2L, db.sentenceQueries.entrySentenceCount().executeAsOne())
        assertEquals("水を食べる", db.sentenceQueries.sentencesForEntry(2L).executeAsList().single().japanese)
    }

    @Test
    fun rewritesTheBreakdownWithAReadingForEveryKanjiWord() {
        val db = write(
            entries = listOf(entry(1L, "食べる", "たべる"), entry(2L, "水", "みず")),
            japanese = listOf("水を食べた"),
            // 水 states its reading, 食べる does not, を is kana and in
            // no entry, and the sense index and surface form are noise.
            indices = listOf("1\t1\t水(みず) を[01] 食べる{食べた}~"),
        )

        assertEquals(
            "水(みず)#2 を 食べる(たべる)#1",
            db.sentenceQueries.sentencesForEntry(1L).executeAsList().single().breakdown,
        )
    }

    @Test
    fun aKanjiWordInNoEntryStaysBareRatherThanBeingDropped() {
        val db = write(
            entries = listOf(entry(1L, "食べる", "たべる")),
            japanese = listOf("三日間食べた"),
            indices = listOf("1\t1\t三日間 食べる{食べた}"),
        )

        assertEquals(
            "三日間 食べる(たべる)#1",
            db.sentenceQueries.sentencesForEntry(1L).executeAsList().single().breakdown,
        )
    }

    @Test
    fun theCascadePicksTheCommonerHomograph() {
        val db = write(
            // One spelling, three readings: only the resolved entry can
            // say which of them belongs in a given sentence.
            entries = listOf(
                entry(1L, "端", "はな"),
                entry(2L, "端", "はし", listOf("ichi1", "nf25")),
                entry(3L, "端", "はた"),
            ),
            japanese = listOf("川の端", "机の端", "布の端"),
            indices = listOf(
                // Bare headword: commonness decides.
                "1\t1\t端",
                // Reading narrows before commonness does.
                "2\t2\t端(はた)",
                // JMdict's own number settles it outright.
                "3\t3\t端(#1)",
            ),
        )

        assertEquals("川の端", db.sentenceQueries.sentencesForEntry(2L).executeAsList().single().japanese)
        assertEquals("机の端", db.sentenceQueries.sentencesForEntry(3L).executeAsList().single().japanese)
        assertEquals("布の端", db.sentenceQueries.sentencesForEntry(1L).executeAsList().single().japanese)
        assertTrue(
            db.sentenceQueries.entrySentenceCount().executeAsOne() == 3L,
            "each of the three rows should have linked exactly one entry",
        )
        // The reading follows the entry the cascade chose, not the
        // spelling: one 端 reads はし, another はた and a third はな, which
        // is the whole reason resolving has to precede reading.
        assertEquals("端(はし)#2", db.sentenceQueries.sentencesForEntry(2L).executeAsList().single().breakdown)
        assertEquals("端(はた)#3", db.sentenceQueries.sentencesForEntry(3L).executeAsList().single().breakdown)
        assertEquals(
            "端(はな)#1",
            db.sentenceQueries.sentencesForEntry(1L).executeAsList().single().breakdown,
            "the ent_seq picked entry 1, so the breakdown must read as entry 1 does",
        )
    }
}
