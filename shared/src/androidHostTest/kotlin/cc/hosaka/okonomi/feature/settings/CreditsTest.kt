package cc.hosaka.okonomi.feature.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the credits manifest: the licence obligations it discharges
 * (EDRDG attribution, Yomitan derivation, Tatoeba and Tanaka Corpus
 * CC-BY attribution) must not silently regress.
 */
class CreditsTest {
    private val stringsPath = "src/commonMain/composeResources/values/strings.xml"

    private fun stringsFile(): File {
        val candidates = listOf(File(stringsPath), File("shared", stringsPath))
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull(
            file,
            "strings.xml not found; tried ${candidates.map { it.absolutePath }} " +
                "from working directory ${File(".").absolutePath}",
        )
        return file
    }

    private fun bundledStrings(): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(stringsFile())
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name")
            assertNotNull(name, "string element #$index (\"${node.textContent}\") has no name attribute")
            name.nodeValue to node.textContent
        }
    }

    private fun bundledString(key: String): String {
        val strings = bundledStrings()
        val value = strings[key]
        assertNotNull(value, "no string named $key in strings.xml (found ${strings.keys})")
        return value
    }

    @Test
    fun `the manifest contains the four EDRDG sources, Yomitan and the sentence sources`() {
        assertEquals(
            listOf("JMdict", "JMnedict", "KANJIDIC2", "RADKFILE", "Yomitan", "Tatoeba (Tanaka Corpus)"),
            creditEntries.map { it.name },
        )
    }

    @Test
    fun `the sentence sources are credited to Creative Commons through Tatoeba`() {
        // One entry naming both sources rather than two entries: the
        // Japanese-English pairs began as Yasuhito Tanaka's corpus and
        // reach us through Tatoeba under Tatoeba's terms, so one credit
        // discharges both attributions and its name says whose work it
        // is. Alex made that call; this test follows it.
        //
        // What the test is actually for is unchanged: the field exists
        // to discharge the attribution obligation, so it has to reach
        // the terms rather than a home page, and the licence has to stay
        // the unqualified name Tatoeba itself uses.
        val sentenceSource = creditEntries.single { it.name == "Tatoeba (Tanaka Corpus)" }
        assertEquals("Creative Commons", sentenceSource.licence)
        assertEquals("https://tatoeba.org/en/terms_of_use", sentenceSource.licenceUrl)
    }

    @Test
    fun `no credit claims a Creative Commons variant the source does not name`() {
        // Tatoeba says its data is released under "various Creative
        // Commons licenses" and names no one of them, so any specific
        // variant here would be a precision we invented. "CC BY 2.0 FR"
        // is the plausible-looking string this guards against.
        val variant = Regex("""CC[ -]?BY|\d\.\d""")
        creditEntries
            .filter { it.licence.contains("Creative Commons") }
            .forEach { entry ->
                assertTrue(
                    !variant.containsMatchIn(entry.licence),
                    "${entry.name} names a specific CC variant: ${entry.licence}",
                )
            }
    }

    @Test
    fun `no credit links a bare host with nothing on it`() {
        // A crude check for a real mistake: this field is where the
        // reader goes for the terms, and a link to a project's front
        // door discharges nothing. A path is not proof of a licence
        // page, but its absence is proof of a home page.
        creditEntries.forEach { entry ->
            assertTrue(
                entry.licenceUrl.trimEnd('/').count { it == '/' } > 2,
                "${entry.name} links a home page rather than its terms: ${entry.licenceUrl}",
            )
        }
    }

    @Test
    fun `every url in the manifest is https`() {
        creditEntries.forEach { entry ->
            assertTrue(entry.licenceUrl.startsWith("https://"), "${entry.name}: ${entry.licenceUrl}")
        }
        assertTrue(EDRDG_LICENCE_URL.startsWith("https://"))
    }

    @Test
    fun `exactly four sources carry the EDRDG licence and link its url`() {
        val edrdgEntries = creditEntries.filter { it.licence == "EDRDG licence" }
        assertEquals(
            listOf("JMdict", "JMnedict", "KANJIDIC2", "RADKFILE"),
            edrdgEntries.map { it.name },
        )
        edrdgEntries.forEach { entry ->
            assertEquals(EDRDG_LICENCE_URL, entry.licenceUrl, entry.name)
        }
    }

    @Test
    fun `the Yomitan entry carries the GPL licence`() {
        val yomitan = creditEntries.single { it.name == "Yomitan" }
        assertEquals("GPL-3.0", yomitan.licence)
    }

    @Test
    fun `every usage string resolves to non blank text`() {
        creditEntries.forEach { entry ->
            val usage = bundledString(entry.usage.key)
            assertTrue(usage.isNotBlank(), "${entry.name}: blank usage text for ${entry.usage.key}")
        }
    }

    @Test
    fun `the EDRDG statement names all four sources`() {
        val statement = bundledString(edrdgStatement.key)
        listOf(
            "JMdict",
            "JMnedict",
            "KANJIDIC2",
            "RADKFILE",
            "Electronic Dictionary Research and Development Group",
        ).forEach { part ->
            assertTrue(statement.contains(part), "statement is missing: $part")
        }
    }
}
