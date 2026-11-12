package cc.hosaka.okonomi.lang

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * The resources directory, relative to the module, which is where Gradle
 * runs host tests from (as `UserDbSchemaTest` relies on too).
 */
internal const val RESOURCES_DIRECTORY = "src/commonMain/composeResources"

/** The strings and plurals of one resources folder, by name. */
internal class StringResourceSet(
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
)

/**
 * Every string file in the resources folder [folderName] (`values`,
 * `values-ja`), read as one, the way the build serves them: a name may
 * live in `strings.xml` or `tags.xml` alike.
 *
 * A name defined twice, in one file or across two, fails here. Compose
 * Multiplatform builds such a duplicate without complaint, and the app
 * then throws when the string is looked up, on whichever screen first
 * draws it. Every test that reads the files reads them through this, so
 * a duplicate fails the same way in all of them.
 */
internal fun readStringResourceFolder(folderName: String): StringResourceSet {
    val folder = File(RESOURCES_DIRECTORY, folderName)
    val files = folder.listFiles { file -> file.isFile && file.extension == "xml" }.orEmpty().sortedBy { it.name }
    assertTrue(files.any { it.name == "strings.xml" }, "${folder.path} has no strings.xml")
    val strings = mutableMapOf<String, String>()
    val plurals = mutableMapOf<String, Map<String, String>>()
    for (file in files) {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        for (string in root.children("string")) {
            val name = string.getAttribute("name")
            assertTrue(name !in strings, "$name is defined twice in ${folder.path}, the second time in ${file.name}")
            strings[name] = string.textContent
        }
        for (plural in root.children("plurals")) {
            val name = plural.getAttribute("name")
            assertTrue(name !in plurals, "$name is defined twice in ${folder.path}, the second time in ${file.name}")
            plurals[name] = plural.children("item").associate { it.getAttribute("quantity") to it.textContent }
        }
    }
    assertTrue(strings.isNotEmpty(), "${folder.path} holds no strings; the format has drifted")
    return StringResourceSet(strings, plurals)
}

private fun Element.children(tag: String): List<Element> {
    val nodes = getElementsByTagName(tag)
    return (0 until nodes.length).map { nodes.item(it) as Element }.filter { it.parentNode == this }
}
