package cc.hosaka.okonomi.dictgen

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager

/**
 * Regenerates :shared's committed conjugation corpus fixture from a
 * generated dictionary.
 *
 * A separate task rather than part of [Pipeline] for the same reason
 * `syncPosCodes` is: it writes a file that lives in the source tree and is
 * committed, and a build step that rewrites checked-in sources on every
 * `generateDictionary` would be a surprise, not a convenience. Run it when
 * the dictionary is rebuilt and commit what changes.
 *
 * Deterministic by construction: every selection below is ordered and ties
 * break by entry id, so the same JMdict build reproduces the file byte for
 * byte.
 */
class ConjugationCorpusWriter(private val db: File, private val out: File) {

    data class Summary(val entries: Int, val codes: Int, val out: File) {
        fun report(): String = "Wrote ${out.path}: %,d entries, %,d codes".format(entries, codes)
    }

    /** Headword and part-of-speech codes as `EntryDetail` would resolve them. */
    private data class Row(val id: Long, val headword: String)

    fun run(): Summary {
        if (!db.isFile) {
            throw PipelineException(
                "No dictionary database at ${db.path}. Run ':tools:dictgen:generateDictionary' first.",
            )
        }
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { connection ->
            val ranks = readRanks(connection)
            val headwords = readHeadwords(connection)
            val senses = readSenses(connection)
            check(headwords.isNotEmpty()) { "The dictionary holds no headwords; the corpus would be empty." }

            val picked = LinkedHashSet<Long>()
            fun take(id: Long) {
                if (headwords.containsKey(id)) picked += id
            }

            for (code in CONJUGABLE + NON_CONJUGABLE) {
                val rows = senses.entriesWith(code).mapNotNull { id ->
                    headwords[id]?.let { Row(id, it) }
                }
                val byRank = rows.sortedWith(compareBy({ ranks[it.id] ?: UNRANKED }, { it.id }))
                byRank.take(MOST_COMMON).forEach { take(it.id) }
                rows.sortedWith(compareBy({ -it.headword.length }, { it.id }))
                    .take(LONGEST)
                    .forEach { take(it.id) }
                byRank.filter { row -> row.headword.any { it.isKatakanaSample() } }
                    .take(KATAKANA_SAMPLES)
                    .forEach { take(it.id) }
                TAILS[code]?.let { tails ->
                    byRank.filterNot { row -> tails.any { row.headword.endsWith(it) } }
                        .take(NEGATIVE_CONTROLS)
                        .forEach { take(it.id) }
                }
            }
            NAMED.forEach { take(it) }

            val entries = picked.sorted().map { id ->
                val codes = senses.codesOf(id).joinToString(", ") { "\"${it.escaped()}\"" }
                "    CorpusEntry($id, \"${headwords.getValue(id).escaped()}\", listOf($codes)),"
            }
            write(entries, senses.allCodes)
            return Summary(entries.size, senses.allCodes.size, out)
        }
    }

    /** `EntryDetail.headword`: first kanji form by ord, else first reading by ord. */
    private fun readHeadwords(connection: Connection): Map<Long, String> {
        val headwords = HashMap<Long, String>()
        // Readings first, so a kanji form overwrites the fallback rather than
        // needing a per-entry probe for one.
        for (table in listOf("reading", "kanji_form")) {
            var current = -1L
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT entry_id, text FROM $table ORDER BY entry_id, ord").use { rows ->
                    while (rows.next()) {
                        val id = rows.getLong(1)
                        if (id != current) {
                            headwords[id] = rows.getString(2)
                            current = id
                        }
                    }
                }
            }
        }
        return headwords
    }

    private fun readRanks(connection: Connection): Map<Long, Int> {
        val ranks = HashMap<Long, Int>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, common_rank FROM entry").use { rows ->
                while (rows.next()) ranks[rows.getLong(1)] = rows.getInt(2)
            }
        }
        return ranks
    }

    private fun readSenses(connection: Connection): Senses {
        val byCode = HashMap<String, MutableList<Long>>()
        val byEntry = HashMap<Long, MutableList<String>>()
        val allCodes = sortedSetOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT entry_id, pos FROM sense ORDER BY entry_id, ord").use { rows ->
                while (rows.next()) {
                    val id = rows.getLong(1)
                    val pos = rows.getString(2) ?: continue
                    val codes = byEntry.getOrPut(id) { mutableListOf() }
                    pos.split(',').forEach { raw ->
                        val code = raw.trim()
                        if (code.isEmpty()) return@forEach
                        allCodes += code
                        if (code !in codes) codes += code
                        val entries = byCode.getOrPut(code) { mutableListOf() }
                        // Rows arrive ordered by entry id, so appending keeps
                        // each list sorted and the last element is the only
                        // possible duplicate.
                        if (entries.lastOrNull() != id) entries += id
                    }
                }
            }
        }
        return Senses(byCode, byEntry, allCodes.toList())
    }

    private class Senses(
        private val byCode: Map<String, List<Long>>,
        private val byEntry: Map<Long, List<String>>,
        val allCodes: List<String>,
    ) {
        /** Entry ids carrying [code] on any sense, ascending. */
        fun entriesWith(code: String): List<Long> = byCode[code].orEmpty()

        /** `EntryDetail.posCodes`: distinct across senses, in sense order. */
        fun codesOf(id: Long): List<String> = byEntry[id].orEmpty()
    }

    private fun write(entries: List<String>, codes: List<String>) {
        val groups = entries.chunked(CHUNK)
        val chunkCalls = groups.indices.joinToString("\n") { "    addAll(corpusPart$it())" }
        val chunks = groups.withIndex().joinToString("\n") { (index, group) ->
            "\nprivate fun corpusPart$index(): List<CorpusEntry> = listOf(\n${group.joinToString("\n")}\n)"
        }
        val body = header(
            count = entries.size,
            chunkCalls = chunkCalls,
            chunks = chunks,
            codes = codes.joinToString("\n") { "    \"${it.escaped()}\"," },
        )
        out.absoluteFile.parentFile?.mkdirs()
        val tmp = File(out.absoluteFile.parentFile, out.name + ".tmp")
        tmp.writeText(body)
        try {
            Files.move(
                tmp.toPath(),
                out.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        /** `common_rank` is NOT NULL, so this only guards a missing entry row. */
        const val UNRANKED = 1 shl 30
        const val MOST_COMMON = 60
        const val LONGEST = 25
        const val KATAKANA_SAMPLES = 15
        const val NEGATIVE_CONTROLS = 10

        /** A single list literal past this overruns the JVM's 64KB class initializer limit. */
        const val CHUNK = 300

        // Kept in step with Conjugator.kt's `paradigms` table by ConjugationVocabularyTest.
        val CONJUGABLE = listOf(
            "v1", "v1-s", "v5u", "v5k", "v5g", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r",
            "v5k-s", "v5r-i", "v5aru", "v5u-s", "v5uru", "vk", "vs-i", "vs-s", "vs-c",
            "vz", "adj-i", "adj-ix", "aux-adj",
        )

        // Controls: codes the conjugator must leave alone.
        val NON_CONJUGABLE = listOf(
            "vs", "n", "adv", "exp", "adj-na", "adj-no", "pn", "int", "v2k-s", "v4r", "vn", "vr", "v-unspec",
        )

        // The tail each code requires; entries that fail it are sampled as negative controls.
        val TAILS = mapOf(
            "v1" to listOf("る"), "v1-s" to listOf("る"), "v5uru" to listOf("る"),
            "v5u" to listOf("う"), "v5k" to listOf("く"), "v5g" to listOf("ぐ"),
            "v5s" to listOf("す"), "v5t" to listOf("つ"), "v5n" to listOf("ぬ"),
            "v5b" to listOf("ぶ"), "v5m" to listOf("む"), "v5r" to listOf("る"),
            "v5k-s" to listOf("く"), "v5r-i" to listOf("ある", "有る", "在る"),
            "v5aru" to listOf("る"), "v5u-s" to listOf("う"),
            "vk" to listOf("来る", "くる"), "vs-i" to listOf("する", "為る"),
            "vs-s" to listOf("する", "為る"), "vs-c" to listOf("する", "為る", "す"),
            "vz" to listOf("ずる"),
            "adj-i" to listOf("い", "イ", "ぃ"), "adj-ix" to listOf("い", "イ", "ぃ"),
            "aux-adj" to listOf("い", "イ", "ぃ"),
        )

        // Entries the review round named, and the bases ConjugationRoundTripTest seeds from.
        // Pinned by id so a later resample cannot quietly drop them.
        val NAMED = listOf(
            1000000L, 1157170L, 1231840L, 1296400L, 1343950L, 1358280L, 1454500L, 1547720L,
            1562350L, 1949750L, 1975230L, 2018300L, 2146840L, 2820690L, 2871942L,
        )

        /** Katakana proper, plus the small `ぃ` the adjective codes are sampled for. */
        fun Char.isKatakanaSample(): Boolean = code in 0x30A0..0x30FF || this == 'ぃ'

        fun String.escaped(): String = replace("\\", "\\\\").replace("\"", "\\\"")

        fun header(count: Int, chunkCalls: String, chunks: String, codes: String): String = """
package cc.hosaka.okonomi.lang

/**
 * A sample of the shipped dictionary, committed so the conjugation
 * sweep runs in the default suite without depending on a build artifact
 * being present. Sibling in spirit to `JapaneseTransformsCorpus`, which
 * pins the deinflector against its own fixture for the same reason.
 *
 * Scope, stated plainly: this is not every conjugable entry. It is,
 * for each part-of-speech code the conjugator handles and a control set
 * of codes it does not, the $MOST_COMMON most common entries by `common_rank`,
 * the $LONGEST longest headwords (compounds and expressions inflecting on a
 * tail), up to $KATAKANA_SAMPLES katakana or small-kana spellings, and up to $NEGATIVE_CONTROLS entries
 * whose headword does not end in the kana the code names — the last
 * group being negative controls, which must produce no table at all.
 * Every entry the review round named is pinned by id on top of that.
 *
 * Regenerate with `./gradlew :tools:dictgen:generateConjugationCorpus`;
 * every selection is ordered and ties break by entry id, so the same
 * JMdict build reproduces this file.
 *
 * [headword] is what `EntryDetail.headword` resolves to (first kanji
 * form by ord, else first reading) and [posCodes] what
 * `EntryDetail.posCodes` resolves to (distinct across senses, in sense
 * order), so a corpus row is exactly what the Forms tab is handed.
 */
internal data class CorpusEntry(
    val entryId: Long,
    val headword: String,
    val posCodes: List<String>,
)

/**
 * $count entries; see the file header for how they were chosen.
 *
 * Split across several functions because a single list literal this
 * long overruns the JVM's 64KB limit on a class initializer.
 */
internal val conjugationCorpus: List<CorpusEntry> = buildList {
$chunkCalls
}
$chunks

/**
 * Every distinct part-of-speech code in the shipped dictionary at the
 * JMdict build this fixture was taken from. `ConjugationVocabularyTest`
 * asserts each one is either conjugated or explicitly ignored, so a
 * code JMdict adds later shows up as a red test rather than as a tab
 * that quietly has nothing to say.
 */
internal val shippedPosCodes: List<String> = listOf(
$codes
)
""".trimStart()
    }
}
