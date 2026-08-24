package cc.hosaka.okonomi.feature.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the credits manifest: the licence obligations it discharges
 * (EDRDG attribution, Yomitan derivation) must not silently regress.
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
    fun `the manifest contains the four EDRDG sources and Yomitan`() {
        assertEquals(
            listOf("JMdict", "JMnedict", "KANJIDIC2", "RADKFILE", "Yomitan"),
            creditEntries.map { it.name },
        )
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
