package cc.hosaka.okonomi.dictgen

import cc.hosaka.okonomi.db.OkonomiDb
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
 * Started at 1 alongside the schema version, deliberately: nothing has
 * shipped and there is no installed base to migrate, so the pre-release
 * shape is simply "version 1". The first shipped release freezes both
 * counters — from then on every data change bumps this one.
 *
 * 2: the entry view's `tag_label` rows and the `sense.dial` column. The
 * database is regenerated wholesale rather than migrated (pre-release),
 * so the schema version does not move with it and this counter is the
 * only thing that makes a provisioned device re-copy.
 */
const val DICTIONARY_FORMAT_VERSION = 2

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

class Pipeline(private val dataDir: File, private val out: File) {

    data class Summary(val counts: Map<String, Long>, val sizeBytes: Long, val out: File) {
        fun report(): String = buildString {
            appendLine("Generated ${out.path}: %,d bytes (%.1f MB)".format(sizeBytes, sizeBytes / 1048576.0))
            counts.forEach { (table, count) -> appendLine("  %-9s %,d".format(table, count)) }
        }.trimEnd()
    }

    fun run(): Summary {
        val jmdict = source("JMdict_e_examp.xml")
        val jmnedict = source("JMnedict.xml")
        val kanjidic = source("kanjidic2.xml")
        val radk = source("radkfile")

        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, out.name + ".tmp")
        try {
            return generate(jmdict, jmnedict, kanjidic, radk, tmp)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private fun generate(jmdict: File, jmnedict: File, kanjidic: File, radk: File, tmp: File): Summary {
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
            writer.writeKanjidic(KanjidicParser(kanjidic))
            writer.writeRadk(RadkParser.parse(radk))
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
        return Summary(counts, out.length(), out)
    }

    private fun source(name: String): File {
        val file = File(dataDir, name)
        if (!file.isFile) throw PipelineException("Missing source file: $name (expected at ${file.path})")
        return file
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
