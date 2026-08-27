package cc.hosaka.okonomi.dictgen

import java.io.File
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.XMLStreamReader

/** One character's strokes, in the order KanjiVG draws them. */
data class KanjiStrokes(
    val literal: String,
    val paths: List<String>,
)

/**
 * Reads KanjiVG's per-character SVGs out of a directory of them.
 *
 * Only the non-variant files are taken: KanjiVG also ships `-Kaisho`,
 * `-Insatsu` and similar calligraphic variants of the same character,
 * which would collide on the literal and are not what a learner is
 * shown. The filename is the character's codepoint in five lowercase
 * hex digits, so [KANJI_FILE] is both the variant filter and the source
 * of the literal.
 *
 * Stroke order is the whole point of this source, and it is carried by
 * document order alone — there is no ordinal attribute to sort on. That
 * is only trustworthy because it is checked: every `<path>` must be
 * `kvg:<code>-s<n>` with `n` counting up from 1 in document order, so a
 * file whose ids are shuffled, renumbered or written for a different
 * character fails generation instead of shipping a character that draws
 * itself backwards.
 */
class KanjivgParser(private val dir: File) {

    fun parse(onCharacter: (KanjiStrokes) -> Unit) {
        val files = dir.listFiles()
            ?.filter { it.isFile && KANJI_FILE.matches(it.name) }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) {
            throw PipelineException(
                "No KanjiVG character files in ${dir.path}: expected files named like 04e00.svg. " +
                    "A silently empty read here ships a dictionary with no stroke order in it at all.",
            )
        }
        files.forEach { onCharacter(parseFile(it)) }
    }

    private fun parseFile(file: File): KanjiStrokes {
        val code = file.name.removeSuffix(SVG_SUFFIX)
        val literal = literalOf(code, file)
        val paths = mutableListOf<String>()
        var sawRootGroup = false
        XmlSource(file).use { source ->
            val reader = source.reader
            try {
                while (reader.hasNext()) {
                    if (reader.next() != XMLStreamConstants.START_ELEMENT) continue
                    when (reader.localName) {
                        "g" -> if (!sawRootGroup) {
                            sawRootGroup = true
                            checkRootGroup(reader, code, file)
                        }
                        "path" -> paths += readStroke(reader, code, paths.size + 1, file)
                    }
                }
            } catch (e: XMLStreamException) {
                throw PipelineException("XML parse error in ${file.name}: ${e.message}", e)
            }
        }
        if (!sawRootGroup) {
            throw PipelineException("No stroke group in ${file.name}: expected a <g> with id $STROKE_PATHS_PREFIX$code")
        }
        if (paths.isEmpty()) {
            throw PipelineException("No strokes in ${file.name}: a character file with no <path> carries nothing to draw")
        }
        return KanjiStrokes(literal, paths)
    }

    /**
     * The outermost group names the character the file is about. A file
     * whose name and content disagree means the directory was assembled
     * by hand or renamed, and taking the filename on trust would file
     * one character's strokes under another's literal.
     */
    private fun checkRootGroup(reader: XMLStreamReader, code: String, file: File) {
        val id = reader.getAttributeValue(null, "id")
        val expected = STROKE_PATHS_PREFIX + code
        if (id != expected) {
            throw PipelineException(
                "Codepoint mismatch in ${file.name}: root group is id=\"$id\", expected \"$expected\"",
            )
        }
    }

    private fun readStroke(reader: XMLStreamReader, code: String, ordinal: Int, file: File): String {
        val id = reader.getAttributeValue(null, "id")
        val expected = "$STROKE_ID_PREFIX$code$STROKE_ID_INFIX$ordinal"
        if (id != expected) {
            throw PipelineException(
                "Stroke order broken in ${file.name}: stroke $ordinal is id=\"$id\", expected \"$expected\". " +
                    "Document order is the only stroke order this source has.",
            )
        }
        val d = reader.getAttributeValue(null, "d")
        if (d.isNullOrBlank()) {
            throw PipelineException("Stroke $ordinal of ${file.name} (id=\"$id\") has no path data")
        }
        val data = d.trim()
        // The strokes of a character are stored newline-joined in one
        // column, which is what keeps stroke order out of row order (see
        // kanji.sq). That contract holds only while no single `d`
        // contains a newline, and it is asserted in comments in three
        // files without anything enforcing it. Enforced here, at the
        // boundary where the value enters the database.
        //
        // Not a hypothetical about line wrapping: XML normalises the line
        // breaks of a wrapped attribute to spaces, so wrapping alone is
        // harmless (measured). What does get through is a character
        // reference - `d="M1,2&#10;c3,4"` yields a real newline - and so
        // would any future change to how this value is read. A violation
        // would read back as two strokes mid-character, everywhere and in
        // silence, so it is worth the one comparison.
        if (data.any { it in LINE_BREAKS }) {
            throw PipelineException(
                "Stroke $ordinal of ${file.name} (id=\"$id\") has a line break inside its path data. " +
                    "Strokes are stored newline-joined, one row per character, so this would read back " +
                    "as two strokes.",
            )
        }
        return data
    }

    private fun literalOf(code: String, file: File): String {
        val codePoint = code.toInt(radix = 16)
        if (!Character.isValidCodePoint(codePoint) || codePoint in SURROGATES) {
            throw PipelineException("Filename ${file.name} is not a character codepoint")
        }
        return String(Character.toChars(codePoint))
    }

    private companion object {
        const val SVG_SUFFIX = ".svg"

        /**
         * Non-variant files only. KanjiVG's variants append a style name
         * (`04e00-Kaisho.svg`), which this deliberately does not match.
         */
        val KANJI_FILE = Regex("^[0-9a-f]{5}\\.svg$")

        const val STROKE_PATHS_PREFIX = "kvg:StrokePaths_"

        const val STROKE_ID_PREFIX = "kvg:"

        const val STROKE_ID_INFIX = "-s"

        /** Half of a surrogate pair is a codepoint no character has. */
        val SURROGATES = 0xD800..0xDFFF

        /** What the stored separator cannot survive appearing inside a stroke. */
        val LINE_BREAKS = charArrayOf('\n', '\r')
    }
}
