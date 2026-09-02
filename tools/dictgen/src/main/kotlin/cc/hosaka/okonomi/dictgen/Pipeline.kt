package cc.hosaka.okonomi.dictgen

import cc.hosaka.okonomi.db.OkonomiDb
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.sql.DriverManager
import java.time.Instant

/**
 * Version of the generated dictionary's data — its shape AND the
 * semantics of what is in it. The app re-copies the bundled asset when
 * the sidecar it persisted differs from the bundled one, so this must
 * be bumped for ANY change a device could not otherwise notice:
 * a new column, a renamed value, or — the case that motivated splitting
 * this out from the schema version — a changed ranking formula. The
 * SQLDelight schema version only moves when the DDL moves, so a pure
 * formula change would otherwise leave every provisioned device on the
 * old rankings forever.
 *
 * What a bump costs, which is the other half of the decision: the app
 * re-copies the whole bundled database, currently 184 MB, on every
 * device that already has one — decompressing and writing it out again
 * before the dictionary can be used. That is not a migration cost that
 * scales with what changed; it is the same for a new column and for a
 * one-word label fix. Bump it for anything a device could not otherwise
 * notice, and batch data changes rather than shipping one per bump.
 *
 * Started at 1 alongside the schema version, deliberately: nothing has
 * shipped and there is no installed base to migrate, so the pre-release
 * shape is simply "version 1". The first shipped release freezes both
 * counters — from then on every data change bumps this one.
 *
 * 2: the entry view's `tag_label` rows and the `sense.dial` column. The
 * database is regenerated wholesale rather than migrated (pre-release),
 * so the schema version does not move with it and this counter is the
 * only thing that makes a provisioned device re-copy.
 *
 * 3: the Phrases tab's `sentence` and `entry_sentence` tables, built
 * from Tatoeba's Japanese/English pairs and its word index.
 *
 * 4: [SENTENCES_PER_ENTRY] raised from 10 to 50 so the Phrases tab has
 * something to page through. No DDL moved — only how many rows the
 * generator keeps — which is exactly the case this counter exists for:
 * a device holding a version 3 copy would otherwise stay capped at ten
 * sentences forever.
 *
 * 5: the `sentence.breakdown` column carries each word's inflected
 * surface form, which the Phrases tab needs to find the word in the
 * sentence and set its reading over it. Again no DDL: the column's
 * contents changed and its type did not, so
 * [DICTIONARY_SCHEMA_FINGERPRINT] below must NOT have moved with it.
 * A device left on a version 4 copy does not lose its sentences — the
 * 71% of words a sentence writes exactly as the dictionary does still
 * locate off the headword alone — but it loses the 29% that carry a
 * surface, which is every inflected verb in the corpus.
 *
 * 6: `name_entry` keeps person names only — `surname`, `given`, `fem`,
 * `masc` and any combination containing one of them — and drops the
 * places, stations, companies, products, works and famous people that
 * were two thirds of the file's rows and a third of its bytes. Rows were
 * filtered; no DDL moved, so [DICTIONARY_SCHEMA_FINGERPRINT] must not
 * have moved either. A device left on version 5 keeps a database whose
 * name rows are mostly places, which the new search would happily return.
 *
 * 7: `kanji_stroke_order`, KanjiVG's per-stroke SVG path data, which
 * the Kanji tab's 88.dp slot draws and animates. This one IS a DDL
 * change, so [DICTIONARY_SCHEMA_FINGERPRINT] moves with it — a device
 * left on version 6 would hold a database with no such table, and the
 * tab's fifth query would fail against it rather than degrade.
 *
 * 8: the stored breakdown rescues two defects in Tatoeba's index that
 * left a real word on screen finding nothing when tapped — the `NI`
 * proper-noun marker stored as if it were a word (235 tokens, the word
 * itself sitting in the surface), and a particle glued to the front or
 * back of one (2,567 tokens, `になる` alone 2,559). Rows changed, no DDL,
 * so [DICTIONARY_SCHEMA_FINGERPRINT] must NOT have moved. A device left
 * on version 7 keeps sentences that render correctly but whose taps on
 * those words open a truncated-prefix search.
 *
 * THIS COUNTER IS THE ONLY RE-COPY SIGNAL. Bump it for a schema change
 * too, not only for a data change.
 *
 * The sidecar is `<jmdict_date>:<schema_version>:<format_version>` and
 * is compared by whole-string equality, so on the face of it either of
 * the last two would do. In practice only this one can move. SQLDelight
 * derives `OkonomiDb.Schema.version` from the number of `.sqm` migration
 * files, and this project has none and will have none: it ships a
 * read-only prebuilt database that is replaced wholesale, never
 * migrated (`verifyMigrations` is off for the same reason). With no
 * migration files the schema version is pinned at 1 by policy and is
 * inert — it cannot move even when the DDL genuinely does.
 *
 * So: a schema change that does not bump THIS counter leaves every
 * provisioned device on its old database, with the old DDL, forever. A
 * missing index is merely slow; a missing or renamed column is a crash.
 * The covering search indexes shipped alongside version 4 and were
 * carried by it; a future one gets no such luck. This has already been
 * relied on by accident once. Do not rely on it again — and do not add
 * a migration file to make the schema version move instead, because the
 * app has no migration path to run it.
 *
 * [DICTIONARY_SCHEMA_FINGERPRINT] is what stops that from being a thing
 * anyone has to remember.
 */
const val DICTIONARY_FORMAT_VERSION = 8

/**
 * The POS code sidecar, written beside the database and copied into :shared's
 * host-test resources by `:tools:dictgen:syncPosCodes`. See [Pipeline].
 */
const val POS_CODES_NAME = "pos-codes.tsv"

private val POS_CODES_HEADER = """
    |# Tag codes the shipped dictionary declares, one per line, as
    |# code<TAB>declared|used. "used" means some sense actually carries it.
    |#
    |# Generated from the database by :tools:dictgen. Regenerate with
    |# ./gradlew :tools:dictgen:syncPosCodes and commit the diff -- a code
    |# JMdict retires is meant to be visible in review, not a surprise in CI.
    |
""".trimMargin()

/**
 * Fingerprint of the schema DDL, as a guard on the counter above.
 *
 * The failure mode this exists for: change the schema, forget to bump
 * [DICTIONARY_FORMAT_VERSION], ship — and every device that already has
 * a database silently keeps the old one. Nothing else catches it. The
 * schema version cannot move (no migration files, by policy), the app
 * does not inspect the DDL it copied, and a stale database is not an
 * error, just wrong: a missing index is slow, a renamed column crashes.
 *
 * So the DDL is hashed and the hash is checked in HERE, one line from
 * the counter it guards, and `SchemaFingerprintTest` fails the moment
 * they disagree. The point is not the value; it is that editing a `.sq`
 * file makes a test fail with a message naming the version bump.
 *
 * Changed by a human, never by a generator that rewrites it — a guard
 * that re-baselines itself guards nothing. When the test fails: decide
 * whether devices need the change (they almost always do), bump
 * [DICTIONARY_FORMAT_VERSION], then paste the new value the failure
 * message prints.
 */
const val DICTIONARY_SCHEMA_FINGERPRINT = "3769814c5932b696"

/**
 * Union of several DTDs' entity declarations, earlier sources winning:
 * JMdict and JMnedict share code names (`m-sl`, for instance) and the
 * JMdict wording is the one the entry view is written around.
 */
internal fun mergedEntities(vararg sources: Map<String, String>): Map<String, String> {
    val merged = LinkedHashMap<String, String>()
    sources.forEach { source ->
        source.forEach { (code, expansion) ->
            if (code !in merged) {
                merged[code] = expansion
            }
        }
    }
    return merged
}

class Pipeline(
    private val dataDir: File,
    private val out: File,
    // Deliberately not defaulted beside the database: that directory is
    // packaged into the APK verbatim, and SyncDictionaryAssets asserts its
    // contents are exactly the database and its version sidecar.
    private val posCodes: File = File(out.absoluteFile.parentFile, POS_CODES_NAME),
) {

    data class Summary(val counts: Map<String, Long>, val sizeBytes: Long, val out: File) {
        fun report(): String = buildString {
            appendLine("Generated ${out.path}: %,d bytes (%.1f MB)".format(sizeBytes, sizeBytes / 1048576.0))
            counts.forEach { (table, count) -> appendLine("  %-9s %,d".format(table, count)) }
        }.trimEnd()
    }

    fun run(): Summary {
        val jmdict = source("JMdict_e.xml")
        val jmnedict = source("JMnedict.xml")
        val kanjidic = source("kanjidic2.xml")
        val radk = source("radkfile")
        val kanjivg = sourceDir("kanji")
        val tatoeba = TatoebaParser(
            japanese = source("jpn_sentences.tsv"),
            english = source("eng_sentences.tsv"),
            indices = source("jpn_indices.csv"),
        )

        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, out.name + ".tmp")
        try {
            return generate(jmdict, jmnedict, kanjidic, radk, kanjivg, tatoeba, tmp)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private fun generate(
        jmdict: File,
        jmnedict: File,
        kanjidic: File,
        radk: File,
        kanjivg: File,
        tatoeba: TatoebaParser,
        tmp: File,
    ): Summary {
        // The date stamps the sidecar that the app uses to detect updates;
        // without it, successive JMdict releases would look identical.
        val jmdictDate = extractCreationDate(jmdict)
            ?: throw PipelineException(
                "No 'JMdict created: <date>' comment found in ${jmdict.name}; " +
                    "the version sidecar needs it to detect dictionary updates.",
            )
        val counts: Map<String, Long>
        val jmdictParser = JmdictParser(jmdict)
        val jmnedictParser = JmnedictParser(jmnedict)
        DbWriter(tmp).use { writer ->
            writer.writeJmdict(jmdictParser)
            // Straight after the entries: the sentence linker resolves
            // B-line headwords against what writeJmdict just wrote.
            writer.writeTatoeba(tatoeba)
            writer.writeKanjidic(KanjidicParser(kanjidic))
            writer.writeRadk(RadkParser.parse(radk))
            writer.writeKanjivg(KanjivgParser(kanjivg))
            writer.writeNames(jmnedictParser)
            // Both parsers have read their DTD by now, so the label
            // table can be written from what they declared.
            writer.writeTagLabels(
                mergedEntities(jmdictParser.entityLabels, jmnedictParser.entityLabels),
            )
            writer.finish(
                mapOf(
                    "jmdict_date" to jmdictDate,
                    "schema_version" to OkonomiDb.Schema.version.toString(),
                    "format_version" to DICTIONARY_FORMAT_VERSION.toString(),
                    "generated_at" to Instant.now().toString(),
                ),
            )
            counts = writer.counts()
        }
        atomicReplace(tmp, out)
        // The sidecar is the app's staleness check for its copied database; it is
        // written after the database move so it never describes a partial file,
        // and moved atomically itself so it is never observable half-written.
        val sidecar = File(out.absoluteFile.parentFile, out.name + ".version")
        val sidecarTmp = File(sidecar.parentFile, sidecar.name + ".tmp")
        sidecarTmp.writeText("$jmdictDate:${OkonomiDb.Schema.version}:$DICTIONARY_FORMAT_VERSION")
        atomicReplace(sidecarTmp, sidecar)
        // A second sidecar, for the tappable-word rule's guard tests. Read back
        // from the finished database rather than accumulated while writing it,
        // so it describes what actually shipped and cannot drift from it.
        writePosCodes(posCodes)
        return Summary(counts, out.length(), out)
    }

    /**
     * Writes the tag codes this dictionary declares, and which of them a sense
     * actually carries, one per line as `code<TAB>declared|used`.
     *
     * `:tools:dictgen:syncPosCodes` copies this into :shared's host-test
     * resources, where it is committed. That is what lets BreakdownPosCodesTest
     * hold the rule to real data without the 184 MB database, and what turns a
     * code JMdict retires into a reviewable line in a diff.
     *
     * Every declared code is listed, not just the used ones: the two tests ask
     * different questions, and a code that stops being carried is exactly the
     * kind of quiet change worth seeing in review.
     */
    private fun writePosCodes(target: File) {
        val declared = mutableSetOf<String>()
        val used = mutableSetOf<String>()
        DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { database ->
            database.createStatement().use { statement ->
                statement.executeQuery("SELECT code FROM tag_label").use { rows ->
                    while (rows.next()) declared += rows.getString(1)
                }
            }
            database.createStatement().use { statement ->
                statement.executeQuery("SELECT pos FROM sense WHERE pos IS NOT NULL").use { rows ->
                    while (rows.next()) {
                        rows.getString(1).split(',').forEach { used += it.trim() }
                    }
                }
            }
        }
        check(declared.isNotEmpty()) { "tag_label is empty; the POS sidecar would assert nothing." }
        val body = (declared + used).sorted().joinToString("\n") { code ->
            "$code\t" + if (code in used) "used" else "declared"
        }
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(POS_CODES_HEADER + body + "\n")
        atomicReplace(tmp, target)
    }

    private fun source(name: String): File {
        val file = File(dataDir, name)
        if (!file.isFile) throw PipelineException("Missing source file: $name (expected at ${file.path})")
        return file
    }

    /** KanjiVG ships one file per character, so its source is a directory. */
    private fun sourceDir(name: String): File {
        val dir = File(dataDir, name)
        if (!dir.isDirectory) throw PipelineException("Missing source directory: $name (expected at ${dir.path})")
        return dir
    }

    private fun extractCreationDate(jmdict: File): String? =
        jmdict.bufferedReader().useLines { lines ->
            lines.take(5000).firstNotNullOfOrNull { line ->
                Regex("JMdict created: (\\d{4}-\\d{2}-\\d{2})").find(line)?.groupValues?.get(1)
            }
        }

    private fun atomicReplace(tmp: File, out: File) {
        try {
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
