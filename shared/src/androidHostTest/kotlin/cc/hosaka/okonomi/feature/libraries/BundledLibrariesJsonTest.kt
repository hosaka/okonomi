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
