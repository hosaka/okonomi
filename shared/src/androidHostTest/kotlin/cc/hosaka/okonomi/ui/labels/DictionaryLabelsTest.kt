package cc.hosaka.okonomi.ui.labels

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.DictionaryTag
import cc.hosaka.okonomi.deinflect.japaneseTransforms
import cc.hosaka.okonomi.lang.readStringResourceFolder
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.robolectric.annotation.Config

/**
 * The dictionary's tags and the deinflector's rules against the app's
 * strings, in both directions: every tag the shipped dictionary declares
 * is drawn in the app's own words, in English and in Japanese, and every
 * `tag_` string is for a code it declares; every rule named in words has
 * a string of its own, and every rule string has its rule. Nothing
 * reaches a Japanese screen as the dictionary's English unless the app
 * has no string for it.
 *
 * Drawn through the real [tagLabel] and [ruleLabel], in a composition,
 * so what is tested is the lookup the screens make, the naming of
 * `tag_<code>` included. The expected text is read off the XML files
 * rather than through `stringResource`, which would follow the very
 * lookup under test. The phone is Robolectric's qualifiers: en-US
 * without any, Japanese under `qualifiers = "ja"`.
 *
 * The codes are `pos-codes.tsv`, the dictionary's own list. A code a new
 * dictionary declares arrives through `:tools:dictgen:syncPosCodes`
 * (see `BreakdownPosCodesTest` for why the file is committed), and turns
 * this red until `tags.xml` has it in both languages.
 */
@OptIn(ExperimentalTestApi::class)
class DictionaryLabelsTest : ComposeUiTestBase() {

    @Test
    fun `every declared tag is drawn as its own english string`() = runComposeUiTest {
        val drawn = drawTagLabels(declaredCodes())

        assertDrawnAsExpected(expectedTagLabels("values"), drawn)
        assertCasePairsHaveDifferentTexts(drawn)
    }

    @Test
    @Config(qualifiers = "ja")
    fun `every declared tag is drawn as its own japanese string`() = runComposeUiTest {
        val drawn = drawTagLabels(declaredCodes())

        assertDrawnAsExpected(expectedTagLabels("values-ja"), drawn)
        assertCasePairsHaveDifferentTexts(drawn)
    }

    /**
     * The other direction. A `tag_` string for a code the dictionary does
     * not declare is one no chip can reach: a misspelt name, or a code
     * JMdict has retired. A misspelt one also leaves the code it was
     * meant for on its fallback, which only the tests above would catch,
     * and only in the language it was misspelt in.
     */
    @Test
    fun `every tag string is for a declared code`() {
        val declaredNames = declaredCodes().map(::tagStringName).toSet()
        for (folder in FOLDERS) {
            val orphans = readStringResourceFolder(folder).strings.keys
                .filter { it.startsWith(TAG_STRING_PREFIX) && it !in declaredNames }
            assertEquals(emptyList(), orphans, "tag strings in $folder for codes the dictionary does not declare")
        }
    }

    /**
     * `tag_<code>` writes `-` as `_`, which a resource name can hold, so
     * it cannot tell the two apart: a code with an `_` of its own would
     * share its name with the hyphenated code beside it. None has one;
     * this says so before one arrives.
     */
    @Test
    fun `no declared code holds an underscore`() {
        assertEquals(
            emptyList(),
            declaredCodes().filter { '_' in it },
            "codes whose tag_ name cannot be told from a hyphenated code's",
        )
    }

    /**
     * English reads exactly as the deinflector names its rules, which is
     * what the breadcrumb showed before it had strings at all.
     */
    @Test
    fun `every rule is drawn in english as the deinflector names it`() = runComposeUiTest {
        val names = ruleNames()
        val drawn = drawRuleLabels(names)

        assertEquals(names.associateWith { it }, drawn)
    }

    /**
     * Compared by resource rather than by text, so two rules that came to
     * share a translation would still pass, and a rule borrowing another
     * rule's string would not.
     */
    @Test
    fun `each rule named in words has its own string and each rule string its own rule`() {
        val (suffixNamed, wordNamed) = ruleNames().partition(::isSuffixNamed)
        assertTrue(suffixNamed.isNotEmpty() && wordNamed.isNotEmpty(), "the rule names did not split; nothing is tested")
        val resources = wordNamed.associateWith { ruleStringResource(it) }

        assertEquals(emptyList(), resources.filterValues { it == null }.keys.toList(), "rules named in words with no string")
        assertEquals(emptyList(), suffixNamed.filter { ruleStringResource(it) != null }, "suffix rules given a string")
        val keys = resources.values.mapNotNull { it?.key }
        assertEquals(keys.distinct(), keys, "rules sharing one string")
        val ruleStringNames = readStringResourceFolder("values").strings.keys.filter { it.startsWith(RULE_STRING_PREFIX) }
        assertEquals(ruleStringNames.sorted(), keys.sorted(), "rule strings and the rules named in words")
    }

    /**
     * The rules named in words are drawn as their own Japanese strings,
     * and the rules named by their suffix (`-た`, `-ます`) as they are.
     */
    @Test
    @Config(qualifiers = "ja")
    fun `every rule is drawn in japanese as its own string or as its suffix`() = runComposeUiTest {
        val japanese = readStringResourceFolder("values-ja").strings
        val names = ruleNames()
        val expected = names.associateWith { name ->
            if (isSuffixNamed(name)) {
                name
            } else {
                ruleStringResource(name)?.key?.let { japanese[it] } ?: "(no Japanese string for $name)"
            }
        }

        val drawn = drawRuleLabels(names)

        assertEquals(expected, drawn)
    }
}

/**
 * What a tag with no app string would be drawn as. Nothing in either
 * file reads like this, so a code that fell back cannot pass for one
 * that resolved.
 */
private const val FALLBACK = "(the dictionary's own label)"

private const val TAG_STRING_PREFIX = "tag_"

private const val RULE_STRING_PREFIX = "deinflection_rule_"

private val FOLDERS = listOf("values", "values-ja")

/** The dictionary's case pairs: kanji and kana forms of the same note. */
private val CASE_PAIRS = listOf("iK" to "ik", "oK" to "ok", "rK" to "rk", "sK" to "sk")

private const val POS_CODES = "pos-codes.tsv"

private fun tagStringName(code: String): String = TAG_STRING_PREFIX + code.replace('-', '_')

private fun isSuffixNamed(rule: String): Boolean = rule.startsWith("-")

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.drawTagLabels(codes: List<String>): Map<String, String> {
    val drawn = mutableMapOf<String, String>()
    setContent {
        codes.forEach { code -> drawn[code] = tagLabel(DictionaryTag(code = code, label = FALLBACK)) }
    }
    waitForIdle()
    return drawn
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.drawRuleLabels(names: List<String>): Map<String, String> {
    val drawn = mutableMapOf<String, String>()
    setContent {
        names.forEach { name -> drawn[name] = ruleLabel(name) }
    }
    waitForIdle()
    return drawn
}

/**
 * Each declared code with its `tag_<code>` string in [folder]. A code
 * the folder has no string for is expected as a note saying so, which
 * nothing can be drawn as, and not as [FALLBACK]: that would expect the
 * very fallback this test exists to rule out, and pass.
 */
private fun expectedTagLabels(folder: String): Map<String, String> {
    val strings = readStringResourceFolder(folder).strings
    return declaredCodes().associateWith { code ->
        val name = tagStringName(code)
        strings[name] ?: "(no $name in $folder)"
    }
}

/** Names each code drawn as anything other than its expected text. */
private fun assertDrawnAsExpected(expected: Map<String, String>, drawn: Map<String, String>) {
    val wrong = expected
        .filter { (code, text) -> drawn[code] != text }
        .map { (code, text) -> "$code: expected \"$text\", drawn \"${drawn[code]}\"" }
    assertEquals(emptyList(), wrong, "tags not drawn as their own string")
}

/**
 * The files give `iK` and `ik`, `oK` and `ok`, `rK` and `rk`, and `sK`
 * and `sk` different texts in the language [drawn] is in: the kanji and
 * the kana form of each note read differently. Each code matching its
 * own string does not show this, since two strings of the same text
 * would each match.
 */
private fun assertCasePairsHaveDifferentTexts(drawn: Map<String, String>) {
    for ((upper, lower) in CASE_PAIRS) {
        assertNotEquals(drawn[upper], drawn[lower], "$upper and $lower are drawn the same")
    }
}

/** Every distinct rule name the deinflector can put in a trace. */
private fun ruleNames(): List<String> = japaneseTransforms.transforms.map { it.name }.distinct()

/** The codes `pos-codes.tsv` declares, used or not: a chip can carry any of them. */
private fun declaredCodes(): List<String> {
    val text = DictionaryLabelsTest::class.java
        .getResourceAsStream("/$POS_CODES")
        ?.bufferedReader()
        ?.use { it.readText() }
    assertNotNull(text, "$POS_CODES is not on the test classpath; regenerate it with :tools:dictgen:syncPosCodes")
    val codes = text.lineSequence()
        .map(String::trim)
        .filterNot { it.isEmpty() || it.startsWith("#") }
        .map { it.substringBefore('\t') }
        .toList()
    assertTrue(codes.isNotEmpty(), "$POS_CODES parsed to nothing; the format has drifted")
    assertTrue(CASE_PAIRS.all { (upper, lower) -> upper in codes && lower in codes }, "a case pair is no longer declared")
    return codes
}
