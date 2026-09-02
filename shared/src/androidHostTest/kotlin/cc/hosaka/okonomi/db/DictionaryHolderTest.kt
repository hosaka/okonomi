package cc.hosaka.okonomi.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DictionaryHolderTest {

    private val tempDirs = mutableListOf<File>()
    private val openedDatabases = mutableListOf<DictionaryDatabase>()

    @AfterTest
    fun cleanUp() {
        openedDatabases.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("dictholder").toFile().also { tempDirs += it }

    private suspend fun createDatabaseFile(): String {
        val path = tempDir().resolve(DICTIONARY_DB_NAME).absolutePath
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        OkonomiDb.Schema.create(driver).await()
        driver.close()
        return path
    }

    private fun openWithJdbc(path: String): DictionaryDatabase {
        val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
        return DictionaryDatabase(OkonomiDb(driver), driver).also { openedDatabases += it }
    }

    @Test
    fun `provisions and opens once, then memoizes the handle`() = runTest {
        val path = createDatabaseFile()
        var provisions = 0
        var opens = 0
        val holder = DictionaryHolder(
            provision = {
                provisions++
                path
            },
            open = {
                opens++
                openWithJdbc(it)
            },
        )

        val first = holder.dictionary()
        val second = holder.dictionary()

        assertSame(first, second)
        assertEquals(1, provisions)
        assertEquals(1, opens)
    }

    @Test
    fun `a provisioning failure is not memoized and the next call retries`() = runTest {
        val path = createDatabaseFile()
        var fail = true
        val holder = DictionaryHolder(
            provision = {
                if (fail) error("no space left")
                path
            },
            open = ::openWithJdbc,
        )

        assertFailsWith<IllegalStateException> { holder.dictionary() }

        fail = false
        holder.dictionary()
    }

    @Test
    fun `invalidate drops the handle so the next call reopens`() = runTest {
        val path = createDatabaseFile()
        var opens = 0
        val holder = DictionaryHolder(
            provision = { path },
            open = {
                opens++
                openWithJdbc(it)
            },
        )

        val first = holder.dictionary()
        holder.invalidate()
        val second = holder.dictionary()

        assertNotSame(first, second)
        assertEquals(2, opens)
    }

    // runCurrent() is experimental; it is what lets both callers be in
    // flight before the gate opens, which is the whole point of the test.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `two concurrent calls share one provision and one handle`() = runTest {
        val path = createDatabaseFile()
        var provisions = 0
        val gate = CompletableDeferred<Unit>()
        val holder = DictionaryHolder(
            provision = {
                provisions++
                gate.await()
                path
            },
            open = ::openWithJdbc,
        )

        val first = async { holder.dictionary() }
        val second = async { holder.dictionary() }
        // Both callers are now in flight: one inside provisioning, the
        // other queued on the holder's mutex.
        runCurrent()
        gate.complete(Unit)

        assertSame(first.await(), second.await())
        assertEquals(1, provisions)
    }

    @Test
    fun `a corrupt open is healed by the production-shaped reset lambda`() = runTest {
        val dir = tempDir()
        val db = dir.resolve(DICTIONARY_DB_NAME).apply { writeText("this is not a sqlite file") }
        var opens = 0
        val holder = DictionaryHolder(
            provision = { db.absolutePath },
            open = {
                opens++
                openWithJdbc(it)
            },
        )

        // Production-shaped composition: the memoized handle is dropped
        // and the provisioned files wiped when the query path fails.
        assertFailsWith<SQLException> {
            loadDictionaryInfo(
                dictionary = holder::dictionary,
                reset = {
                    runCatching { holder.invalidate() }
                    resetDictionaryProvisioningIn(dir)
                },
            )
        }
        assertEquals(1, opens)
        assertFalse(db.exists(), "the corrupt copy must be wiped")

        // Re-provisioning (simulated by recreating the file) succeeds
        // only if the stale memoized handle was actually dropped.
        val seedDriver = JdbcSqliteDriver("jdbc:sqlite:${db.absolutePath}")
        OkonomiDb.Schema.create(seedDriver).await()
        seedDriver.close()
        val healed = holder.dictionary()
        assertEquals(2, opens)
        assertEquals(0L, healed.db.entryQueries.entryCount().awaitOne())
    }
}
