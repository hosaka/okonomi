package cc.hosaka.okonomi.feature.phrases

import cc.hosaka.okonomi.db.BreakdownWord
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shipped reading template, checked against the substitution
 * [breakdownWordLabel] performs.
 *
 * `BreakdownWordLabelTest` pins the rule with a template of its own, so
 * on its own it would still pass if the resource lost a placeholder —
 * and a template without `%2$s` drops every reading in the app while
 * looking like an ordinary wording change. Reads strings.xml directly,
 * as `CreditsTest` does, because a resource lookup needs a composition.
 */
class PhrasesStringsTest {

    private fun bundledString(key: String): String {
        val candidates = listOf(
            File("src/commonMain/composeResources/values/strings.xml"),
            File("shared/src/commonMain/composeResources/values/strings.xml"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull(file, "strings.xml not found; tried ${candidates.map { it.absolutePath }}")
        val nodes = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .getElementsByTagName("string")
        val value = (0 until nodes.length)
            .map { nodes.item(it) }
            .firstOrNull { it.attributes.getNamedItem("name")?.nodeValue == key }
            ?.textContent
        assertNotNull(value, "no string named $key in strings.xml")
        return value
    }

    @Test
    fun `the reading template carries both of the placeholders it is substituted with`() {
        val template = bundledString("entry_phrases_word_reading")

        assertTrue("%1\$s" in template, "the word itself is missing from: $template")
        assertTrue("%2\$s" in template, "the reading is missing from: $template")
    }

    @Test
    fun `the shipped template renders a word and its reading`() {
        assertEquals(
            "学校 (がっこう)",
            breakdownWordLabel(
                BreakdownWord("学校", "がっこう"),
                bundledString("entry_phrases_word_reading"),
            ),
        )
    }
}
