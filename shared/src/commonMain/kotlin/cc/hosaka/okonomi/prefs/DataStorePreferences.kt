package cc.hosaka.okonomi.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toPath

/**
 * File name of the preferences store, in the app's private storage. Not
 * the dictionary's directory: the dictionary is a regenerable copy that
 * provisioning deletes to self-heal, and settings the reader chose are
 * not something to lose alongside it.
 */
const val PREFERENCES_FILE_NAME = "okonomi.preferences_pb"

/** First backoff after a failed read, doubling to [MAX_READ_RETRY_DELAY]. */
private val FIRST_READ_RETRY_DELAY = 100.milliseconds

/**
 * Ceiling on the read backoff. A store that is permanently unreadable
 * settles into a poll this slow, which costs nothing and leaves the door
 * open for a later write to be picked up.
 */
private val MAX_READ_RETRY_DELAY = 30.seconds

/**
 * Absolute path of [PREFERENCES_FILE_NAME], with its directory created.
 * Resolved lazily, on the first read or write, so nothing here depends
 * on platform initialization order.
 */
internal expect fun preferencesFilePath(): String

/**
 * [PreferenceStore] over `androidx.datastore`.
 *
 * Failures are absorbed on both sides, which is the whole reason this
 * class exists rather than callers holding a `DataStore` directly. A
 * screen state producer that let a storage error through would take the
 * search screen down over a setting.
 *
 * Reads **recover**, they do not merely fall back once. A plain `catch`
 * would end the flow at the first error, so one transient read failure
 * would leave the setting frozen at its default for the life of that
 * collection — every later write invisible. [retryWhen] emits the
 * default and starts the upstream again, backing off to
 * [MAX_READ_RETRY_DELAY] so a permanently broken file is a slow poll
 * rather than a spin.
 *
 * Writes are **ordered**. Each one is queued to a single writer rather
 * than launched on its own: `DataStore.edit` serialises its callers, but
 * the order independent coroutines reach it is not the order they were
 * asked in, so a quick on-then-off could persist "on". Strings share the
 * queue with Booleans rather than having one of their own, so the order
 * holds across types too.
 */
internal class DataStorePreferenceStore(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PreferenceStore {

    private val writes = Channel<Write<*>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (write in writes) {
                try {
                    store.edit { preferences -> write.applyTo(preferences) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Nothing to do and nobody to tell: the read flow
                    // keeps reporting what is actually stored, so the
                    // toggle is seen to refuse rather than seen to lie.
                }
            }
        }
    }

    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> =
        valueFlow(booleanPreferencesKey(key), default)

    override fun setBoolean(key: String, value: Boolean) {
        enqueue(Write(booleanPreferencesKey(key), value))
    }

    override fun stringFlow(key: String, default: String): Flow<String> =
        valueFlow(stringPreferencesKey(key), default)

    override fun setString(key: String, value: String) {
        enqueue(Write(stringPreferencesKey(key), value))
    }

    private fun <T> valueFlow(key: Preferences.Key<T>, default: T): Flow<T> = store.data
        .retryWhen { cause, attempt ->
            if (cause is CancellationException) return@retryWhen false
            emit(emptyPreferences())
            delay(retryDelay(attempt))
            true
        }
        .map { preferences -> preferences[key] ?: default }
        // The file carries every setting, so a write to any other
        // key emits here too; without this, toggling something
        // unrelated would re-run whatever this value gates. It also
        // absorbs the repeated defaults a retry loop emits.
        .distinctUntilChanged()

    private fun enqueue(write: Write<*>) {
        // trySend on an unlimited channel never fails and never
        // reorders, which is the whole point of queueing here.
        writes.trySend(write)
    }

    /** One queued write, typed so the key and its value cannot disagree. */
    private class Write<T>(
        private val key: Preferences.Key<T>,
        private val value: T,
    ) {
        fun applyTo(preferences: MutablePreferences) {
            preferences[key] = value
        }
    }
}

private fun retryDelay(attempt: Long): Duration {
    val doublings = attempt.coerceAtMost(MAX_BACKOFF_DOUBLINGS).toInt()
    return minOf(FIRST_READ_RETRY_DELAY * (1 shl doublings), MAX_READ_RETRY_DELAY)
}

/** Enough doublings to reach the ceiling; more would overflow the shift. */
private const val MAX_BACKOFF_DOUBLINGS = 20L

/**
 * The DataStore the app runs on, over whatever [path] names.
 *
 * Extracted so the tests build the store the same way the app does. A
 * test that assembled its own would be testing its own assembly: the
 * corruption handler below could be dropped from the app and every
 * assertion about corrupt files would stay green.
 *
 * A corrupt file is replaced with an empty one rather than throwing for
 * ever. Without this the read retry is the only thing between a
 * truncated write and a setting that can never be read or rewritten.
 */
internal fun preferenceDataStore(path: () -> Path): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = path,
    )

private val sharedPreferenceStore: PreferenceStore by lazy {
    DataStorePreferenceStore(preferenceDataStore { preferencesFilePath().toPath() })
}

/**
 * The shared app-lifetime preference store. One instance for the process:
 * DataStore permits only one active reader/writer per file, and two
 * stores over the same path throw.
 */
fun appPreferences(): PreferenceStore = sharedPreferenceStore
