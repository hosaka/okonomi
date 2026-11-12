package cc.hosaka.okonomi.lang

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Every translation the Language menu offers has every string the
 * English file has, translated, with the same placeholders, and a form
 * for every plural that any count can fall back on; and every
 * translation on disk is one the menu offers.
 *
 * A key missing from a translation is not an error anywhere else:
 * Compose Multiplatform quietly draws the English string in its place,
 * in the middle of an otherwise translated screen. A placeholder dropped
 * or renumbered fails only when that string is formatted, on whichever
 * screen and in whichever language reaches it. So the files themselves
 * are compared, in this host test.
 */
class TranslationCompletenessTest {

    private val english = readResources(File(RESOURCES_DIRECTORY, "values/strings.xml"))

    private val translations: Map<AppLanguage, Resources> = translatedLanguages()
        .associateWith { language -> readResources(File(RESOURCES_DIRECTORY, "values-${language.tag}/strings.xml")) }

    @Test
    fun `the menu offers at least one translation to check`() {
        assertTrue(translations.isNotEmpty(), "no translated language in AppLanguage; nothing below checks anything")
    }

    /**
     * Both ways. A menu entry with no folder would offer a language drawn
     * in English. A folder with no entry would still be served to a phone
     * in that language by System default, and nothing here would check it.
     */
    @Test
    fun `the translation folders are exactly the translated menu entries`() {
        val folders = File(RESOURCES_DIRECTORY).listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith(TRANSLATION_PREFIX) && File(it, "strings.xml").isFile }
            .map { it.name.removePrefix(TRANSLATION_PREFIX) }
            .sorted()

        assertEquals(translatedLanguages().mapNotNull { it.tag }.sorted(), folders)
    }

    @Test
    fun `every translation has exactly the english string names`() {
        for ((language, translated) in translations) {
            assertEquals(
                english.strings.keys.sorted(),
                translated.strings.keys.sorted(),
                "string names of $language differ: missing ${english.strings.keys - translated.strings.keys}, " +
                    "extra ${translated.strings.keys - english.strings.keys}",
            )
            assertEquals(
                english.plurals.keys.sorted(),
                translated.plurals.keys.sorted(),
                "plural names of $language differ",
            )
        }
    }

    /**
     * The strings that read the same in every language are exactly the
     * listed ones ([SAME_IN_EVERY_LANGUAGE]), and they read exactly as
     * the English does. Anything else equal to the English was left
     * untranslated; a listed one that differs is, for the licence
     * statement, attribution reworded or gone stale.
     */
    @Test
    fun `only the listed strings read the same as the english and those exactly`() {
        for ((language, translated) in translations) {
            val sameStrings = english.strings.filter { (name, text) -> translated.strings[name] == text }.keys
            val samePlurals = english.plurals.filter { (name, forms) ->
                translated.plurals[name].orEmpty().values.contains(forms[OTHER])
            }.keys
            val same = sameStrings + samePlurals
            assertEquals(SAME_IN_EVERY_LANGUAGE.sorted(), same.sorted(), "strings reading as the English in $language")
        }
    }

    @Test
    fun `every translated string has the english placeholders`() {
        for ((language, translated) in translations) {
            val mismatched = english.strings.mapNotNull { (name, text) ->
                val theirs = translated.strings[name] ?: return@mapNotNull null
                if (placeholders(text) == placeholders(theirs)) null else "$name: \"$text\" vs \"$theirs\""
            }
            assertEquals(emptyList(), mismatched, "placeholders differ in $language")
        }
    }

    /**
     * `other` is the one quantity every language has, and the only one
     * Japanese has; a plural without it has nothing to show for most
     * counts. Each translated form carries the placeholders of English's
     * `other`, since any of them may be what a count is formatted with.
     */
    @Test
    fun `every translated plural has an other form with the english placeholders`() {
        for ((language, translated) in translations) {
            for ((name, forms) in translated.plurals) {
                assertTrue(OTHER in forms, "$name in $language has no \"$OTHER\" form")
                val expected = placeholders(english.plurals.getValue(name).getValue(OTHER))
                for ((quantity, text) in forms) {
                    assertEquals(expected, placeholders(text), "placeholders of $name/$quantity in $language")
                }
            }
        }
    }

    @Test
    fun `placeholders are ordered by their argument numbers`() {
        assertEquals(listOf("%2\$s", "%10\$s"), placeholders("%10\$s and %2\$s"))
    }
}

/**
 * The resources directory, relative to the module, which is where Gradle
 * runs host tests from (as `UserDbSchemaTest` relies on too).
 */
private const val RESOURCES_DIRECTORY = "src/commonMain/composeResources"

private const val TRANSLATION_PREFIX = "values-"

private const val OTHER = "other"

/**
 * Strings a translation leaves as the English has them: the app's name,
 * each language's own name, the bare OK, JLPT and its number, and the
 * EDRDG's licence statement, whose wording stays in English in every UI
 * language.
 */
private val SAME_IN_EVERY_LANGUAGE = listOf(
    "app_name",
    "language_name_english",
    "language_name_japanese",
    "favourites_import_unreadable_dismiss",
    "anki_dismiss",
    "entry_kanji_jlpt",
    "settings_credits_edrdg_statement",
)

/** The menu's languages with a folder of their own; English is `values` itself. */
private fun translatedLanguages(): List<AppLanguage> =
    AppLanguage.entries.filter { it.tag != null && it != AppLanguage.English }

private class Resources(
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
)

private fun readResources(file: File): Resources {
    assertTrue(file.isFile, "${file.path} does not exist")
    val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    val strings = root.children("string").associate { it.getAttribute("name") to it.textContent }
    val plurals = root.children("plurals").associate { plural ->
        val forms = plural.children("item").associate { it.getAttribute("quantity") to it.textContent }
        plural.getAttribute("name") to forms
    }
    return Resources(strings, plurals)
}

private fun Element.children(tag: String): List<Element> {
    val nodes = getElementsByTagName(tag)
    return (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.parentNode == this }
}

/**
 * The format specifiers in [text], ordered by argument number: a
 * translation may need its arguments in a different order from the
 * English (`anki_failed_partly_message` does), and `%10$s` comes after
 * `%2$s`, which sorting the text would not give.
 */
private fun placeholders(text: String): List<String> =
    PLACEHOLDER.findAll(text)
        .sortedWith(compareBy({ it.groupValues[1].toIntOrNull() ?: 0 }, { it.value }))
        .map { it.value }
        .toList()

private val PLACEHOLDER = Regex("""%(?:(\d+)\$)?[a-z]""")
