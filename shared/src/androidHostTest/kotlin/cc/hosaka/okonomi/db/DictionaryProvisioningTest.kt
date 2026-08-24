package cc.hosaka.okonomi.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictionaryProvisioningTest {

    private val dbBytes = "sqlite pretend content".encodeToByteArray()
    // Synthetic on purpose: provisioning only ever compares sidecar
    // strings verbatim, so these tests must keep working whatever the
    // real schema and format counters happen to be.
    private val sidecar = "2026-08-21:4:7"

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun targetDir() = Files.createTempDirectory("provision").toFile().also { tempDirs += it }

    private class FakeAssets(
        private val sidecar: String,
        private val dbBytes: ByteArray,
    ) {
        var dbOpens = 0
            private set

        fun readSidecar(): String = sidecar

        fun openDb(): InputStream {
            dbOpens++
            return ByteArrayInputStream(dbBytes)
        }
    }

    @Test
    fun `first launch copies the database and persists the sidecar`() {
        val dir = targetDir()
        val assets = FakeAssets(sidecar, dbBytes)

        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)

        assertEquals(dir.resolve(DICTIONARY_DB_NAME), db)
        assertTrue(dbBytes.contentEquals(db.readBytes()))
        assertEquals(sidecar, dir.resolve(DICTIONARY_SIDECAR_NAME).readText())
        assertFalse(dir.resolve("$DICTIONARY_DB_NAME.tmp").exists(), "tmp file should be renamed away")
        assertEquals(1, assets.dbOpens)
    }

    @Test
    fun `a warm launch with a matching sidecar does not copy again`() {
        val dir = targetDir()
        val assets = FakeAssets(sidecar, dbBytes)

        provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)
        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)

        assertEquals(1, assets.dbOpens)
        assertTrue(dbBytes.contentEquals(db.readBytes()))
    }

    @Test
    fun `a stale sidecar replaces the database with a fresh copy`() {
        val dir = targetDir()
        val oldAssets = FakeAssets("2025-01-01:4:7", "old content".encodeToByteArray())
        provisionDictionaryInto(dir, oldAssets::readSidecar, oldAssets::openDb)

        val assets = FakeAssets(sidecar, dbBytes)
        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)

        assertEquals(1, assets.dbOpens)
        assertTrue(dbBytes.contentEquals(db.readBytes()), "stale database should be replaced")
        assertEquals(sidecar, dir.resolve(DICTIONARY_SIDECAR_NAME).readText())
    }

    @Test
    fun `a data format bump alone replaces the database`() {
        // The case the format version exists for: the JMdict release is
        // unchanged and so is the schema, but what the columns MEAN
        // changed (a reworked ranking formula). Comparing only the date
        // — or only the schema version — would leave every device on
        // the old data forever.
        val dir = targetDir()
        val oldAssets = FakeAssets("2026-08-21:4:7", "old content".encodeToByteArray())
        provisionDictionaryInto(dir, oldAssets::readSidecar, oldAssets::openDb)

        val assets = FakeAssets("2026-08-21:4:8", dbBytes)
        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)

        assertEquals(1, assets.dbOpens)
        assertTrue(dbBytes.contentEquals(db.readBytes()), "a format bump must replace the database")
        assertEquals("2026-08-21:4:8", dir.resolve(DICTIONARY_SIDECAR_NAME).readText())
    }

    @Test
    fun `a schema version bump alone replaces the database`() {
        val dir = targetDir()
        val oldAssets = FakeAssets("2026-08-21:3:7", "old content".encodeToByteArray())
        provisionDictionaryInto(dir, oldAssets::readSidecar, oldAssets::openDb)

        val assets = FakeAssets("2026-08-21:4:7", dbBytes)
        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)

        assertEquals(1, assets.dbOpens)
        assertTrue(dbBytes.contentEquals(db.readBytes()), "a schema bump must replace the database")
    }

    @Test
    fun `a sidecar from before the format version was added replaces the database`() {
        // A device provisioned by an older build holds a two-component
        // sidecar. Comparison is verbatim, so the extra component alone
        // forces the re-copy — no parsing, no component-wise compare.
        val dir = targetDir()
        val oldAssets = FakeAssets("2026-08-21:4", "old content".encodeToByteArray())
        provisionDictionaryInto(dir, oldAssets::readSidecar, oldAssets::openDb)

        val assets = FakeAssets("2026-08-21:4:7", dbBytes)
        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)

        assertEquals(1, assets.dbOpens)
        assertTrue(dbBytes.contentEquals(db.readBytes()), "a legacy sidecar must replace the database")
        assertEquals("2026-08-21:4:7", dir.resolve(DICTIONARY_SIDECAR_NAME).readText())
    }

    @Test
    fun `a missing database is recopied even when the sidecar matches`() {
        val dir = targetDir()
        val assets = FakeAssets(sidecar, dbBytes)
        provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)
        dir.resolve(DICTIONARY_DB_NAME).delete()

        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)

        assertEquals(2, assets.dbOpens)
        assertTrue(dbBytes.contentEquals(db.readBytes()))
    }

    @Test
    fun `an interrupted copy never surfaces as the database and recovers on the next run`() {
        val dir = targetDir()
        val failing = object : InputStream() {
            private var read = 0

            override fun read(): Int {
                if (read >= 4) throw IOException("interrupted")
                return dbBytes[read++].toInt()
            }
        }

        assertFailsWith<IOException> {
            provisionDictionaryInto(dir, { sidecar }, { failing })
        }
        assertFalse(dir.resolve(DICTIONARY_DB_NAME).exists(), "partial file must never appear as the database")
        assertFalse(dir.resolve(DICTIONARY_SIDECAR_NAME).exists(), "sidecar must not describe a missing database")

        val assets = FakeAssets(sidecar, dbBytes)
        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)
        assertTrue(dbBytes.contentEquals(db.readBytes()))
        assertEquals(sidecar, dir.resolve(DICTIONARY_SIDECAR_NAME).readText())
    }

    @Test
    fun `concurrent provisioning calls are single-flight and produce one valid copy`() = runBlocking {
        val dir = targetDir()
        val opens = AtomicInteger()
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        // A slow asset stream keeps the first provisioning run busy long
        // enough for the second call to contend for the mutex.
        fun slowDbStream(): InputStream = object : InputStream() {
            private val delegate = ByteArrayInputStream(dbBytes)

            override fun read(): Int {
                Thread.sleep(2)
                return delegate.read()
            }
        }

        val provision: suspend () -> String = {
            val now = active.incrementAndGet()
            maxActive.getAndUpdate { known -> max(known, now) }
            try {
                provisionDictionaryInto(
                    targetDir = dir,
                    readBundledSidecar = { sidecar },
                    openBundledDb = {
                        opens.incrementAndGet()
                        slowDbStream()
                    },
                ).absolutePath
            } finally {
                active.decrementAndGet()
            }
        }

        val (first, second) = coroutineScope {
            val a = async(Dispatchers.IO) { provisionSingleFlight(provision) }
            val b = async(Dispatchers.IO) { provisionSingleFlight(provision) }
            a.await() to b.await()
        }

        assertEquals(first, second)
        assertEquals(1, maxActive.get(), "provisioning bodies must never overlap")
        assertEquals(1, opens.get(), "the second call must take the warm path")
        assertTrue(dbBytes.contentEquals(File(first).readBytes()), "the copy must be complete and uncorrupted")
        assertEquals(sidecar, dir.resolve(DICTIONARY_SIDECAR_NAME).readText())
    }

    @Test
    fun `an interrupted update keeps serving a complete database only`() {
        val dir = targetDir()
        val oldBytes = "old content".encodeToByteArray()
        val oldAssets = FakeAssets("2025-01-01:1", oldBytes)
        provisionDictionaryInto(dir, oldAssets::readSidecar, oldAssets::openDb)

        assertFailsWith<IOException> {
            provisionDictionaryInto(dir, { sidecar }, { throw IOException("interrupted before streaming") })
        }
        // The old file is still complete; the deleted sidecar forces a
        // fresh copy instead of trusting it.
        assertTrue(oldBytes.contentEquals(dir.resolve(DICTIONARY_DB_NAME).readBytes()))
        assertFalse(dir.resolve(DICTIONARY_SIDECAR_NAME).exists())

        val assets = FakeAssets(sidecar, dbBytes)
        val db = provisionDictionaryInto(dir, assets::readSidecar, assets::openDb)
        assertTrue(dbBytes.contentEquals(db.readBytes()))
    }
}
