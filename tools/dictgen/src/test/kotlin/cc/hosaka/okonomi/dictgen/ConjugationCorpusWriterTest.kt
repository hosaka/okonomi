package cc.hosaka.okonomi.dictgen

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The corpus fixture is committed, so what this generator picks is what the
 * conjugation sweep is allowed to see. These pin the selection rules against
 * a handful of entries rather than the 184 MB dictionary: the caps are too
 * large to hit with a fixture, but the rules that decide membership and the
 * shape of the emitted file are not.
 */
class ConjugationCorpusWriterTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("corpusgen").toFile().also { tempDirs += it }

    private fun entry(
        id: Long,
        reading: String,
        vararg senses: String,
        kanji: String? = null,
        rank: Long = 950,
    ) = JmdictEntry(
        id = id,
        kanjiForms = kanji?.let { listOf(JmdictKanjiForm(it, rank, rank < 950)) }.orEmpty(),
        readings = listOf(JmdictReading(reading, noKanji = kanji == null, rank, rank < 950, null)),
        senses = senses.map { pos ->
            JmdictSense(
                pos = pos,
                misc = null,
                field = null,
                dial = null,
                info = null,
                restrictions = null,
                glosses = listOf("gloss"),
            )
        },
    )

    /** Writes [entries] to a throwaway database and generates the fixture from it. */
    private fun generate(vararg entries: JmdictEntry): String {
        val dir = tempDir()
        val db = dir.resolve("okonomi.db")
        DbWriter(db).use { it.writeJmdictEntries(entries.toList()) }
        val out = dir.resolve("ConjugationCorpus.kt")
        ConjugationCorpusWriter(db, out).run()
        return out.readText()
    }

    @Test
    fun `picks the headword the way EntryDetail does`() {
        val corpus = generate(
            entry(1L, "たべる", "v1,vt", kanji = "食べる"),
            entry(2L, "する", "vs-i,vt"),
        )
        assertContains(corpus, """CorpusEntry(1, "食べる", listOf("v1", "vt")),""")
        assertContains(corpus, """CorpusEntry(2, "する", listOf("vs-i", "vt")),""")
    }

    @Test
    fun `collapses pos codes across senses, in sense order, without repeating`() {
        val corpus = generate(entry(1L, "かかる", "v5r,vi", "v5r,vt", "exp"))
        assertContains(corpus, """CorpusEntry(1, "かかる", listOf("v5r", "vi", "vt", "exp")),""")
    }

    @Test
    fun `orders rows by entry id whatever order the codes are sampled in`() {
        val corpus = generate(
            entry(30L, "みる", "v1"),
            entry(10L, "かく", "v5k"),
            entry(20L, "ほん", "n"),
        )
        val ids = Regex("""CorpusEntry\((\d+),""").findAll(corpus).map { it.groupValues[1].toLong() }.toList()
        assertEquals(listOf(10L, 20L, 30L), ids)
    }

    @Test
    fun `takes entries whose code the conjugator ignores, as controls`() {
        val corpus = generate(entry(1L, "ほん", "n", kanji = "本"))
        assertContains(corpus, """CorpusEntry(1, "本", listOf("n")),""")
    }

    @Test
    fun `leaves out entries carrying no code the sweep asks for`() {
        val corpus = generate(
            entry(1L, "たべる", "v1"),
            // `aux-v` is on neither list, so nothing samples this one.
            entry(2L, "ゆく", "aux-v"),
        )
        assertContains(corpus, "CorpusEntry(1,")
        assertFalse(corpus.contains("CorpusEntry(2,"))
    }

    @Test
    fun `lists every declared code in shippedPosCodes, sampled or not`() {
        val corpus = generate(entry(1L, "ゆく", "aux-v,v5k-s"))
        val codes = corpus.substringAfter("internal val shippedPosCodes")
        assertContains(codes, """    "aux-v",""")
        assertContains(codes, """    "v5k-s",""")
    }

    @Test
    fun `states the entry count it actually wrote`() {
        val corpus = generate(
            entry(1L, "たべる", "v1"),
            entry(2L, "かく", "v5k"),
        )
        assertContains(corpus, " * 2 entries; see the file header")
    }

    @Test
    fun `splits the list into chunked functions the JVM can initialize`() {
        // One code, more entries than a chunk holds, so the split is exercised
        // without needing the caps' worth of distinct codes.
        val entries = (1L..350L).map { entry(it, "たべる", "v1", rank = it) }
        val dir = tempDir()
        val db = dir.resolve("okonomi.db")
        DbWriter(db).use { it.writeJmdictEntries(entries) }
        val out = dir.resolve("ConjugationCorpus.kt")
        val summary = ConjugationCorpusWriter(db, out).run()
        val corpus = out.readText()
        // The `v1` caps admit at most 60 by rank + 25 longest + 15 kana, and
        // every headword here is identical, so the sample is the rank window.
        assertEquals(60, summary.entries)
        assertContains(corpus, "    addAll(corpusPart0())")
        assertFalse(corpus.contains("corpusPart1"))
    }

    @Test
    fun `samples the most common by rank, and the longest headwords besides`() {
        // Rank improves with id and so does headword length, so the two rules
        // pick overlapping windows off the same end: the 60 best-ranked are
        // ids 11..70, and the 25 longest are ids 46..70, already inside them.
        // Ids 1..10 are what neither rule reaches.
        val entries = (1L..70L).map { entry(it, "た".repeat(it.toInt()) + "る", "v1", rank = 71 - it) }
        val dir = tempDir()
        val db = dir.resolve("okonomi.db")
        DbWriter(db).use { it.writeJmdictEntries(entries) }
        val out = dir.resolve("ConjugationCorpus.kt")
        ConjugationCorpusWriter(db, out).run()
        val ids = Regex("""CorpusEntry\((\d+),""").findAll(out.readText())
            .map { it.groupValues[1].toLong() }
            .toList()
        assertEquals((11L..70L).toList(), ids)
    }

    @Test
    fun `samples headwords that fail their code's tail, as negative controls`() {
        val corpus = generate(
            entry(1L, "たべる", "v1"),
            // Tagged `v1` but does not end in る: the conjugator must produce
            // no table for it, which is only testable if it is in the corpus.
            entry(2L, "たべた", "v1"),
        )
        assertContains(corpus, """CorpusEntry(2, "たべた", listOf("v1")),""")
    }

    @Test
    fun `says what to run when there is no database to read`() {
        val missing = tempDir().resolve("okonomi.db")
        val failure = assertFailsWith<PipelineException> {
            ConjugationCorpusWriter(missing, tempDir().resolve("out.kt")).run()
        }
        assertTrue(failure.message.orEmpty().contains("generateDictionary"), failure.message.orEmpty())
    }
}
