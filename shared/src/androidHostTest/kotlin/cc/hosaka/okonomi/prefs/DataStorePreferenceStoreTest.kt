package cc.hosaka.okonomi.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

private const val KEY = "search.names_enabled"

/**
 * The real storage behind the Names toggle.
 *
 * Everything above this is tested against a map, which cannot fail the
 * way a file can. What has to be proved here is what the seam promises
 * when storage misbehaves: a write lands and is not reordered, a read
 * that fails **recovers** rather than freezing the setting for ever, a
 * corrupt file is replaced, and a write that cannot land is dropped
 * instead of reaching an uncaught handler.
 *
 * The store's scope is the test's own throughout. That is not tidiness:
 * writes run on it, so a write that threw would otherwise land on a
 * scope no test observes — and on Android the default handler for that
 * is process death, exactly where a host test cannot see it.
 *
 * One store per path, never two: DataStore rejects a second active
 * instance over the same file, so each test gets its own directory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePreferenceStoreTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempFile(): File =
        Files.createTempDirectory("preferences").toFile()
            .also { tempDirs += it }
            .resolve(PREFERENCES_FILE_NAME)

    // preferenceDataStore, not a store assembled here: these tests are
    // about how the app configures DataStore, so they have to use the
    // app's own construction or they prove nothing about it.
    private fun storeOver(file: File, scope: CoroutineScope) = DataStorePreferenceStore(
        preferenceDataStore { file.absolutePath.toPath() },
        scope,
    )

    @Test
    fun `an unset key reads as its default`() = runTest {
        val store = storeOver(tempFile(), backgroundScope)

        assertFalse(store.booleanFlow(KEY, default = false).first())
        assertTrue(store.booleanFlow("something.else", default = true).first())
    }

    @Test
    fun `a written value comes back and reaches the file`() = runTest {
        val file = tempFile()
        val store = storeOver(file, backgroundScope)
        val values = store.booleanFlow(KEY, default = false)

        assertFalse(values.first())
        store.setBoolean(KEY, true)

        // The write is fire-and-forget by design, so the flow is what
        // says it landed.
        assertTrue(values.first { it })
        assertTrue(file.isFile && file.length() > 0, "the value has to survive the process, not just the flow")
    }

    /**
     * Writes are asked for in an order and have to be applied in it.
     * Each one used to be launched independently: `DataStore.edit`
     * serialises its callers, but the order independent coroutines reach
     * it is not the order they were asked in, so a quick on-then-off
     * could persist "on" — the toggle silently disagreeing with the last
     * thing the reader did.
     */
    @Test
    fun `writes are applied in the order they were asked for`() = runTest {
        // A fake whose writes take longer the earlier they were started,
        // so four independent coroutines would finish in reverse and the
        // FIRST value asked for would be the one left stored. A queue
        // with one writer cannot do that.
        val slow = FlakyDataStore(failuresBeforeSuccess = 0, writeCostFallsBy = 1.milliseconds)
        val store = DataStorePreferenceStore(slow, backgroundScope)

        store.setBoolean(KEY, true)
        store.setBoolean(KEY, false)
        store.setBoolean(KEY, true)
        store.setBoolean(KEY, false)
        slow.awaitAttempts(4)

        assertFalse(
            store.booleanFlow(KEY, default = true).first(),
            "the last write asked for was false, so false is what must be stored",
        )
    }

    /**
     * A corrupt file is the failure this whole class exists for. The
     * corruption handler replaces it, so the setting reads as unset and
     * the next write succeeds — rather than throwing for ever, which is
     * what happens with no handler however well the read retries.
     */
    @Test
    fun `an unreadable file is replaced rather than throwing for ever`() = runTest {
        val file = tempFile()
        // Bytes that cannot be a protobuf at all: field number 0 with
        // wire type 7, both of which are outside the encoding. Plain
        // text would not do — most of it parses as unknown fields, so a
        // "corrupt" file written as a sentence is quietly valid and the
        // handler this test is about never runs.
        file.writeBytes(byteArrayOf(0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07))
        val store = storeOver(file, backgroundScope)

        assertFalse(store.booleanFlow(KEY, default = false).first())

        store.setBoolean(KEY, true)
        assertTrue(
            store.booleanFlow(KEY, default = false).first { it },
            "a replaced file must be writable again, not merely readable as empty",
        )
    }

    /**
     * A read that fails must not end the flow. With a plain `catch` the
     * first transient error was terminal: the collection emitted its
     * default and then never emitted again, so every later write was
     * invisible for the life of that screen.
     */
    @Test
    fun `a read that fails recovers instead of freezing the setting`() = runTest {
        val flaky = FlakyDataStore(failuresBeforeSuccess = 2)
        val store = DataStorePreferenceStore(flaky, backgroundScope)
        val values = store.booleanFlow(KEY, default = false)

        store.setBoolean(KEY, true)

        assertTrue(
            values.first { it },
            "the flow has to survive the failed reads and go on to see the write",
        )
        assertTrue(flaky.reads > 1, "the point is that it read again, not that it got lucky")
    }

    /**
     * A write runs on the store's scope, so a failing one reaches an
     * uncaught handler rather than the caller — and on Android that
     * handler kills the process. The scope here is the test's, so an
     * escaped exception fails this test; a real file that merely refuses
     * the write would not, because nothing would say when the attempt
     * had happened.
     */
    @Test
    fun `a write that cannot land is dropped rather than thrown`() = runTest {
        val unwritable = UnwritableDataStore()
        val store = DataStorePreferenceStore(unwritable, backgroundScope)

        store.setBoolean(KEY, true)
        unwritable.awaitAttempts(1)

        assertEquals(
            false,
            store.booleanFlow(KEY, default = false).first(),
            "storage that cannot be written reports what it actually holds, which is nothing",
        )
    }
}

/**
 * A store whose first [failuresBeforeSuccess] reads throw, then behaves.
 *
 * [writeCostFallsBy] makes each write cheaper than the one before it, so
 * writes started independently would finish in the reverse of the order
 * they were asked in. A single queued writer is unaffected, which is
 * what makes the ordering test able to fail.
 */
private class FlakyDataStore(
    private val failuresBeforeSuccess: Int,
    private val writeCostFallsBy: Duration = Duration.ZERO,
) : DataStore<Preferences> {

    private val stored = MutableStateFlow(emptyPreferences())
    private val attempts = MutableStateFlow(0)
    private var writesStarted = 0

    var reads = 0
        private set

    /**
     * Suspends until [count] writes have been applied. The writer runs
     * on a scope the test does not await, so this rather than a clock
     * nudge is what makes the assertion deterministic.
     */
    suspend fun awaitAttempts(count: Int) {
        attempts.first { it >= count }
    }

    override val data: Flow<Preferences> = flow {
        reads++
        if (reads <= failuresBeforeSuccess) throw IOException("transient read failure")
        emitAll(stored)
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        if (writeCostFallsBy > Duration.ZERO) {
            val started = writesStarted++
            delay(writeCostFallsBy * (MAX_WRITE_COST - started).coerceAtLeast(1))
        }
        return transform(stored.value).also {
            stored.value = it
            attempts.value++
        }
    }

    private companion object {
        const val MAX_WRITE_COST = 10
    }
}

/** A store that accepts nothing: every write fails, reads stay empty. */
private class UnwritableDataStore : DataStore<Preferences> {
    private val attempts = MutableStateFlow(0)

    override val data: Flow<Preferences> = flow { emit(emptyPreferences()) }

    /** Suspends until [count] writes have been tried and refused. */
    suspend fun awaitAttempts(count: Int) {
        attempts.first { it >= count }
    }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        attempts.value++
        throw IOException("nowhere to write")
    }
}
