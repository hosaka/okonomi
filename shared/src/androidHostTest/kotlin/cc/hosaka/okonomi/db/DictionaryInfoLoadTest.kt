package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the real dictionary read path — the async-codegen queries
 * and the awaitOne/awaitOneOrNull helpers — against an actual database
 * file. sqlite-bundled's Android AAR only ships device ABIs, so these
 * tests run the async-generated code over the synchronous JDBC driver;
 * the bundled-driver layer itself is covered by dictgen's
 * BundledDriverSchemaTest.
 */
class DictionaryInfoLoadTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("dictinfo").toFile().also { tempDirs += it }

    private fun openWithJdbc(path: String): DictionaryDatabase {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        return DictionaryDatabase(OkonomiDb(driver), driver).also { openedDatabases += it }
    }

    @Test
    fun `reads the seeded metadata and entry count through the real query path`() = runTest {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val seedDriver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(seedDriver).await()
        val seed = OkonomiDb(seedDriver)
        seed.metadataQueries.insertMetadata("jmdict_date", "2026-08-21")
        seed.entryQueries.insertEntry(1, 950, 0)
        seed.entryQueries.insertEntry(2, 950, 0)
        seedDriver.close()

        val info = loadDictionaryInfo(
            dictionary = { openWithJdbc(path) },
            reset = { throw AssertionError("a successful load must not reset provisioning") },
        )

        assertEquals(DictionaryInfo(jmdictDate = "2026-08-21", entryCount = 2L), info)
    }

    @Test
    fun `a failing open wipes the provisioned files and rethrows`() = runTest {
        val dir = tempDir()
        val db = dir.resolve(DICTIONARY_DB_NAME).apply { writeText("pretend database") }
        val sidecarFile = dir.resolve(DICTIONARY_SIDECAR_NAME).apply { writeText("2026-08-21:1:1") }
        var resets = 0

        val e = assertFailsWith<IllegalStateException> {
            loadDictionaryInfo(
                dictionary = { error("cannot open") },
                reset = {
                    resets++
                    resetDictionaryProvisioningIn(dir)
                },
            )
        }

        assertEquals("cannot open", e.message)
        assertEquals(1, resets)
        assertFalse(db.exists(), "the corrupt copy must be gone so the next run re-copies")
        assertFalse(sidecarFile.exists(), "the sidecar must not describe a wiped database")
    }

    @Test
    fun `a corrupt database file fails the query and wipes the provisioned files`() = runTest {
        val dir = tempDir()
        val db = dir.resolve(DICTIONARY_DB_NAME).apply { writeText("this is not a sqlite file") }
        val sidecarFile = dir.resolve(DICTIONARY_SIDECAR_NAME).apply { writeText("2026-08-21:1:1") }

        assertFailsWith<SQLException> {
            loadDictionaryInfo(
                dictionary = { openWithJdbc(db.absolutePath) },
                reset = { resetDictionaryProvisioningIn(dir) },
            )
        }

        assertFalse(db.exists())
        assertFalse(sidecarFile.exists())
        assertTrue(dir.exists(), "only the provisioned files are wiped, not the directory")
    }
}
