package cc.hosaka.okonomi.dictgen

import java.io.File
import java.nio.charset.Charset

data class RadkData(
    val radicalStrokes: Map<String, Long>,
    val kanjiByRadical: Map<String, List<String>>,
)

/** radkfile is small enough to read whole; it is EUC-JP encoded. */
object RadkParser {
    private val eucJp = Charset.forName("EUC-JP")

    fun parse(file: File): RadkData {
        val radicalStrokes = linkedMapOf<String, Long>()
        val kanjiByRadical = linkedMapOf<String, MutableList<String>>()
        var current: String? = null
        for (rawLine in file.readText(eucJp).lineSequence()) {
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.startsWith("#") -> Unit
                line.startsWith("$") -> {
                    val parts = line.removePrefix("$").trim().split(Regex("\\s+"))
                    val radical = parts[0]
                    radicalStrokes[radical] = parts.getOrNull(1)?.toLongOrNull()
                        ?: throw PipelineException("Malformed radical header in ${file.name}: $line")
                    current = radical
                }
                else -> {
                    val radical = current
                        ?: throw PipelineException("Kanji line before any radical header in ${file.name}")
                    val list = kanjiByRadical.getOrPut(radical) { mutableListOf() }
                    line.codePoints().forEach { cp ->
                        if (!Character.isWhitespace(cp)) list += String(Character.toChars(cp))
                    }
                }
            }
        }
        return RadkData(radicalStrokes, kanjiByRadical)
    }
}
