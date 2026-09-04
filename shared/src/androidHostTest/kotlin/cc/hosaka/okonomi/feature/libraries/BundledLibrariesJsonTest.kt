package cc.hosaka.okonomi.feature.libraries

import com.mikepenz.aboutlibraries.Libs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the committed AboutLibraries export: it must stay parseable by
 * the runtime parser and keep listing the dependencies the app is built
 * on. Regenerate it with `./gradlew :shared:exportLibraryDefinitions`.
 */
class BundledLibrariesJsonTest {
    private val jsonPath = "src/commonMain/composeResources/files/aboutlibraries.json"

    private fun bundledJson(): String {
        val file = listOf(File(jsonPath), File("shared", jsonPath)).first { it.exists() }
        return file.readText()
    }

    @Test
    fun `the committed export parses into a non empty library list`() {
        val libs = Libs.Builder().withJson(bundledJson()).build()
        val ids = libs.libraries.map { it.uniqueId }

        assertTrue(ids.isNotEmpty())
        assertTrue(ids.any { it.startsWith("com.mikepenz:aboutlibraries-core") }, ids.toString())
        assertTrue(ids.any { it.startsWith("org.jetbrains.androidx.navigation3") }, ids.toString())
        // The newest dependency, and the one a stale export would miss
        // first: FileKit is MIT and has to be in the credits the app
        // ships. The two above have been in the export since it was
        // first generated, so neither of them can catch that.
        val filekit = libs.libraries.filter { it.uniqueId.startsWith("io.github.vinceglb") }
        assertTrue(filekit.isNotEmpty(), ids.toString())
        // The coordinate alone was not the claim: what the credits owe
        // FileKit is its licence, and an export that carried the
        // dependency with an empty licence set would have satisfied a
        // name-only assertion while shipping the attribution empty.
        assertTrue(
            filekit.all { library -> library.licenses.any { it.name.contains("MIT") } },
            filekit.joinToString { library ->
                "${library.uniqueId}: ${library.licenses.joinToString { it.name }}"
            },
        )
    }

    @Test
    fun `invalid json parses into an empty library list`() {
        // The AboutLibraries parsers swallow parse errors and return an
        // empty result, so a corrupt resource degrades to an empty list;
        // the loader's own guard covers failures reading the resource.
        val libs = Libs.Builder().withJson("not json").build()

        assertTrue(libs.libraries.isEmpty())
        assertTrue(libs.licenses.isEmpty())
    }
}
