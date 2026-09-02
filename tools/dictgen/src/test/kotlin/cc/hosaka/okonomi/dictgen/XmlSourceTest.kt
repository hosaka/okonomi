package cc.hosaka.okonomi.dictgen

import java.io.File
import java.nio.file.Files
import javax.xml.stream.XMLStreamConstants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A DOCTYPE's external subset is fetched even with
 * `IS_SUPPORTING_EXTERNAL_ENTITIES` off — that property governs external
 * ENTITIES, and the two are not the same thing. KanjiVG's 6,703 files
 * each name the W3C's SVG DTD by URL, so the first attempt at generating
 * the dictionary spent half an hour opening one HTTP connection to
 * w3.org per file and never finished. [XmlSource] installs a resolver
 * that answers every external reference with nothing.
 *
 * This asserts that by consequence rather than by timing: an entity the
 * external subset declares must never appear among the declared
 * entities, while one from the internal subset must. Both subsets are
 * local files here, so nothing about this test touches the network — and
 * without the resolver the external one is read and the assertion fails.
 */
class XmlSourceTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("xmlsource").toFile().also { tempDirs += it }

    @Test
    fun readsTheInternalSubsetAndNeverTheExternalOne() {
        val dir = tempDir()
        val external = File(dir, "external.dtd")
        external.writeText("""<!ENTITY fromOutside "fetched over the wire">""")
        val document = File(dir, "document.xml")
        document.writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE root SYSTEM "${external.toURI()}" [
            <!ENTITY fromInside "declared in the document itself">
            ]>
            <root/>
            """.trimIndent(),
        )

        val declared = XmlSource(document).use { source ->
            val reader = source.reader
            while (reader.next() != XMLStreamConstants.DTD) {
                // Spin to the DTD event; declaredEntities reads it.
            }
            reader.declaredEntities()
        }

        assertTrue("fromInside" in declared.names, "the internal subset is what the tag labels come from")
        assertEquals(
            emptyList(),
            declared.names.filter { it == "fromOutside" },
            "an external subset must never be read: doing so costs one network round trip per source file",
        )
    }
}
