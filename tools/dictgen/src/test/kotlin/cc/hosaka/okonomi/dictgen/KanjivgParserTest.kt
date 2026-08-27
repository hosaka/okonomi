package cc.hosaka.okonomi.dictgen

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * KanjiVG records stroke order as document order and nothing else: no
 * ordinal attribute, no sort key. Everything here is about the parser
 * refusing to take that on trust, because a character drawn in the wrong
 * order is not a rendering glitch — it is the tab teaching the reader
 * something false, with nothing on screen to say so.
 */
class KanjivgParserTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("kanjivg").toFile().also { tempDirs += it }

    private fun dirWith(vararg files: Pair<String, String>): File =
        tempDir().also { dir -> files.forEach { (name, body) -> File(dir, name).writeText(body) } }

    private fun parseAll(dir: File): List<KanjiStrokes> =
        buildList { KanjivgParser(dir).parse { add(it) } }

    /** A real KanjiVG file's preamble around whatever [body] the test needs. */
    private fun rawSvg(body: String): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        // Kept verbatim from the real files: it names an external DTD by
        // URL, and a parse that fetched it would reach onto the network.
        appendLine("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.0//EN\" \"$SVG_DTD_URL\" [")
        appendLine("""<!ATTLIST g xmlns:kvg CDATA #FIXED "http://kanjivg.tagaini.net" kvg:element CDATA #IMPLIED >""")
        appendLine("""<!ATTLIST path xmlns:kvg CDATA #FIXED "http://kanjivg.tagaini.net" kvg:type CDATA #IMPLIED >""")
        appendLine("]>")
        appendLine(
            """<svg xmlns="http://www.w3.org/2000/svg" width="109" height="109" """ +
                """viewBox="0 0 109 109" xmlns:kvg="https://kanjivg.tagaini.net/">""",
        )
        appendLine(body)
        appendLine("</svg>")
    }

    /**
     * [strokes] are `(id, d)` pairs written out exactly as given, so a
     * test can put ids in an order no real file would have.
     */
    private fun svg(
        rootCode: String,
        strokes: List<Pair<String, String>>,
    ): String = rawSvg(
        buildString {
            appendLine("""<g id="kvg:StrokePaths_$rootCode" style="fill:none;stroke:#000000;stroke-width:3;">""")
            appendLine("""<g id="kvg:$rootCode">""")
            strokes.forEach { (id, d) -> appendLine("""<path id="$id" kvg:type="㇐" d="$d"/>""") }
            appendLine("</g>")
            appendLine("</g>")
            append(strokeNumberLayer(rootCode))
        },
    )

    /**
     * KanjiVG's second layer, which labels each stroke with its number.
     * Its children are `<text>`, never `<path>`, so it must contribute no
     * strokes - which is why every fixture carries it.
     */
    private fun strokeNumberLayer(rootCode: String): String = buildString {
        appendLine("""<g id="kvg:StrokeNumbers_$rootCode" style="font-size:8;fill:#808080">""")
        appendLine("""<text transform="matrix(1 0 0 1 4.25 54.13)">1</text>""")
        appendLine("</g>")
    }

    private fun wellFormed(code: String, vararg paths: String): String =
        svg(code, paths.mapIndexed { index, d -> "kvg:$code-s${index + 1}" to d })

    @Test
    fun readsEachCharactersStrokesInDocumentOrder() {
        val dir = dirWith(
            "04e00.svg" to wellFormed("04e00", "M11,54.25c3.19,0.62", "M20,60c1,1"),
            "098df.svg" to wellFormed("098df", "M1,2c3,4"),
        )

        val parsed = parseAll(dir)

        assertEquals(listOf("一", "食"), parsed.map { it.literal })
        assertEquals(listOf("M11,54.25c3.19,0.62", "M20,60c1,1"), parsed.first().paths)
        assertEquals(listOf("M1,2c3,4"), parsed.last().paths)
    }

    /**
     * KanjiVG's codepoints run past the BMP, so the literal has to be
     * built from the codepoint rather than from a single Char. This one
     * is U+20B9F, which is a surrogate pair.
     */
    @Test
    fun buildsLiteralsAboveTheBasicMultilingualPlane() {
        val dir = dirWith("20b9f.svg" to wellFormed("20b9f", "M1,2c3,4"))

        val parsed = parseAll(dir).single()

        assertEquals("𠮟", parsed.literal)
        assertEquals(2, parsed.literal.length, "a supplementary codepoint is two Chars, not one")
    }

    /**
     * KanjiVG ships thousands of calligraphic variants of characters it
     * also ships plainly. They must never reach the database: they carry
     * the same literal and would collide on it.
     */
    @Test
    fun skipsVariantFilesAndAnythingThatIsNotACharacterFile() {
        val dir = dirWith(
            "04e00.svg" to wellFormed("04e00", "M1,2c3,4"),
            "04e00-Kaisho.svg" to wellFormed("04e00", "M9,9c9,9"),
            "04e00-Insatsu.svg" to wellFormed("04e00", "M8,8c8,8"),
            "04E00.svg" to wellFormed("04e00", "M7,7c7,7"),
            "README.md" to "not a character",
        )

        assertEquals(listOf("一"), parseAll(dir).map { it.literal })
    }

    @Test
    fun rejectsStrokeIdsThatDoNotAscendFromOne() {
        val dir = dirWith(
            "04e00.svg" to svg(
                "04e00",
                listOf("kvg:04e00-s1" to "M1,2c3,4", "kvg:04e00-s3" to "M5,6c7,8"),
            ),
        )

        val e = assertFailsWith<PipelineException> { parseAll(dir) }

        assertTrue("04e00.svg" in (e.message ?: ""), "message should name the file: ${e.message}")
        assertTrue("kvg:04e00-s2" in (e.message ?: ""), "message should name what was expected: ${e.message}")
    }

    @Test
    fun rejectsStrokeIdsWrittenOutOfDocumentOrder() {
        val dir = dirWith(
            "04e00.svg" to svg(
                "04e00",
                listOf("kvg:04e00-s2" to "M5,6c7,8", "kvg:04e00-s1" to "M1,2c3,4"),
            ),
        )

        val e = assertFailsWith<PipelineException> { parseAll(dir) }

        assertTrue("04e00.svg" in (e.message ?: ""), "message should name the file: ${e.message}")
    }

    @Test
    fun rejectsAFileWhoseRootGroupNamesAnotherCharacter() {
        val dir = dirWith("04e00.svg" to svg("098df", listOf("kvg:04e00-s1" to "M1,2c3,4")))

        val e = assertFailsWith<PipelineException> { parseAll(dir) }

        assertTrue("04e00.svg" in (e.message ?: ""), "message should name the file: ${e.message}")
        assertTrue("kvg:StrokePaths_04e00" in (e.message ?: ""), "message should name what was expected: ${e.message}")
    }

    /**
     * The stroke-number layer is the only other `<g>` in a real file and
     * its children are `<text>`, so a file whose stroke group is empty
     * has nothing to draw - and would otherwise reach the database as a
     * character with a diagram made of no strokes.
     */
    @Test
    fun rejectsAFileWhoseStrokeGroupHasNoPathsInIt() {
        val dir = dirWith(
            "04e00.svg" to svg("04e00", strokes = emptyList()),
        )

        val e = assertFailsWith<PipelineException> { parseAll(dir) }

        assertTrue("04e00.svg" in (e.message ?: ""), "message should name the file: ${e.message}")
        assertTrue("No strokes" in (e.message ?: ""), "message should say what was missing: ${e.message}")
    }

    /**
     * The body carries no `<g>` at all, deliberately. Written with only
     * the stroke-number layer instead, this passed with the guard
     * deleted: that layer is a `<g>` too, so it became the root group and
     * the codepoint check rejected the file first. The test proved a
     * branch it was not named for. Found by mutation, not by reading.
     */
    @Test
    fun rejectsAFileWithNoStrokeGroupAtAll() {
        val dir = dirWith("04e00.svg" to rawSvg("<!-- a file that declares no groups -->"))

        val e = assertFailsWith<PipelineException> { parseAll(dir) }

        assertTrue("04e00.svg" in (e.message ?: ""), "message should name the file: ${e.message}")
        assertTrue(
            "No stroke group" in (e.message ?: ""),
            "the missing-group branch is the one under test here: ${e.message}",
        )
        assertTrue(
            "kvg:StrokePaths_04e00" in (e.message ?: ""),
            "message should name the group it looked for: ${e.message}",
        )
    }

    /**
     * KanjiVG's stroke-number layer is a `<g>` as well, and it is the one
     * that would be reached first in a file that had lost its stroke
     * group but kept its numbers. It must not be mistaken for the root.
     */
    @Test
    fun rejectsAFileWhoseFirstGroupIsTheStrokeNumberLayer() {
        val dir = dirWith("04e00.svg" to rawSvg(strokeNumberLayer("04e00")))

        val e = assertFailsWith<PipelineException> { parseAll(dir) }

        assertTrue("04e00.svg" in (e.message ?: ""), "message should name the file: ${e.message}")
        assertTrue(
            "kvg:StrokeNumbers_04e00" in (e.message ?: ""),
            "message should name the group it actually found: ${e.message}",
        )
    }

    @Test
    fun rejectsAStrokeWithNoPathData() {
        val blank = dirWith("04e00.svg" to svg("04e00", listOf("kvg:04e00-s1" to "")))
        val missing = dirWith(
            "04e00.svg" to rawSvg(
                """
                <g id="kvg:StrokePaths_04e00">
                <path id="kvg:04e00-s1"/>
                </g>
                """.trimIndent(),
            ),
        )

        listOf(blank, missing).forEach { dir ->
            val e = assertFailsWith<PipelineException> { parseAll(dir) }

            assertTrue("04e00.svg" in (e.message ?: ""), "message should name the file: ${e.message}")
            assertTrue("no path data" in (e.message ?: ""), "message should say what was missing: ${e.message}")
        }
    }

    /**
     * A character's strokes are stored newline-joined in one column, so a
     * newline inside a single `d` would read back as two strokes.
     *
     * The fixture writes `&#10;` rather than wrapping the attribute over
     * two lines, and that is the whole point of the test: XML normalises
     * a wrapped attribute's line breaks to spaces, so a literally wrapped
     * fixture would carry no newline by the time the parser saw it and
     * this test would pass with the guard deleted. A character reference
     * is not normalised, and is what actually reaches the value.
     */
    @Test
    fun rejectsAStrokeWhosePathDataContainsALineBreak() {
        val dir = dirWith(
            "04e00.svg" to svg("04e00", listOf("kvg:04e00-s1" to "M1,2&#10;c3,4")),
        )

        val e = assertFailsWith<PipelineException> { parseAll(dir) }

        assertTrue("04e00.svg" in (e.message ?: ""), "message should name the file: ${e.message}")
        assertTrue("line break" in (e.message ?: ""), "message should say what was wrong: ${e.message}")
    }

    /**
     * An empty directory would otherwise generate a database with no
     * stroke order in it at all, and nothing about that is an error the
     * app could notice.
     */
    @Test
    fun rejectsADirectoryWithNoCharacterFilesInIt() {
        val dir = dirWith("README.md" to "not a character")

        val e = assertFailsWith<PipelineException> { parseAll(dir) }

        assertTrue(dir.path in (e.message ?: ""), "message should name the directory: ${e.message}")
    }
}

private const val SVG_DTD_URL = "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd"
