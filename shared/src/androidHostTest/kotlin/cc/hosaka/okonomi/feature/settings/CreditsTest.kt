package cc.hosaka.okonomi.feature.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What is left of this file after Alex cut it back deliberately.
 *
 * Most of what used to be here asserted `creditEntries` against
 * restatements of its own constants — an exact expected name list,
 * `assertEquals("GPL-3.0", yomitan.licence)`, Furiganable's url spelled
 * out a second time. None of those had an oracle outside `Credits.kt`,
 * so none could fail except as a two-file editing ritual: change the
 * manifest, watch the test go red, paste the new constant in. They were
 * removed.
 *
 * These three survive because each is checked against something the
 * manifest does not get to decide — a rule about URLs, or the contents
 * of `strings.xml`. That distinction is the whole point of the file now;
 * do not add an assertion here that a `Credits.kt` edit would simply be
 * copied into.
 *
 * The rendering half is `SettingsCreditsUiTest`, which is a different
 * kind of check: it exists because a reviewer once deleted the whole
 * `CreditsSection(...)` call with every test still green.
 */
class CreditsTest {
    private val stringsPath = "src/commonMain/composeResources/values/strings.xml"

    /**
     * KanjiVG's licence header asks specifically to link "KanjiVG's
     * website", so its credit points at the project page rather than at
     * a licence document. That is Alex's deliberate call and the reason
     * the rule below carries a name-based exception instead of being
     * dropped: the rule still guards every other entry, present and
     * future.
     */
    private val homePageIsTheAttribution = setOf("KanjiVG")

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

    private fun bundledString(key: String): String {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(stringsFile())
        val nodes = document.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name")
            assertNotNull(name, "string element #$index (\"${node.textContent}\") has no name attribute")
            name.nodeValue to node.textContent
        }
        val value = strings[key]
        assertNotNull(value, "no string named $key in strings.xml (found ${strings.keys})")
        return value
    }

    @Test
    fun `every url in the manifest is https`() {
        creditEntries.forEach { entry ->
            assertTrue(entry.licenceUrl.startsWith("https://"), "${entry.name}: ${entry.licenceUrl}")
        }
        assertTrue(EDRDG_LICENCE_URL.startsWith("https://"))
    }

    @Test
    fun `no credit links a bare host with nothing on it`() {
        // A crude check for a real mistake: this field is where the
        // reader goes for the terms, and a link to a project's front
        // door discharges nothing. A path is not proof of a licence
        // page, but its absence is proof of a home page.
        creditEntries
            .filterNot { it.name in homePageIsTheAttribution }
            .forEach { entry ->
                assertTrue(
                    entry.licenceUrl.trimEnd('/').count { it == '/' } > 2,
                    "${entry.name} links a home page rather than its terms: ${entry.licenceUrl}",
                )
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
