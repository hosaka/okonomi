package cc.hosaka.okonomi.dictgen

import cc.hosaka.okonomi.db.OkonomiDb
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

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
        val counts: Map<String, Long>
        DbWriter(tmp).use { writer ->
            writer.writeJmdict(JmdictParser(jmdict))
            writer.writeKanjidic(KanjidicParser(kanjidic))
            writer.writeRadk(RadkParser.parse(radk))
            writer.writeNames(JmnedictParser(jmnedict))
            writer.finish(
                mapOf(
                    "jmdict_date" to (extractCreationDate(jmdict) ?: "unknown"),
                    "schema_version" to OkonomiDb.Schema.version.toString(),
                    "generated_at" to Instant.now().toString(),
                ),
            )
            counts = writer.counts()
        }
        atomicReplace(tmp, out)
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
