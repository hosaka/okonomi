package cc.hosaka.okonomi.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Test double for [PreferenceStore]: values live in a map for the
 * lifetime of the instance, so one instance shared between two runs of a
 * producer is what "the setting persisted" means in a test.
 *
 * Writes land synchronously, unlike the real store, which is the point:
 * a producer test drives virtual time and cannot wait on a file.
 */
internal class FakePreferenceStore(
    initial: Map<String, Boolean> = emptyMap(),
    strings: Map<String, String> = emptyMap(),
) : PreferenceStore {
    private val values = MutableStateFlow<Map<String, Any>>(initial + strings)

    /**
     * Every write, in order and of either type, so a test can assert what
     * reached storage — and that a read reached none of it.
     */
    val writes = mutableListOf<Pair<String, Any>>()

    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = values
        .map { stored -> stored[key] as? Boolean ?: default }
        .distinctUntilChanged()

    override fun setBoolean(key: String, value: Boolean) {
        write(key, value)
    }

    override fun stringFlow(key: String, default: String): Flow<String> = values
        .map { stored -> stored[key] as? String ?: default }
        .distinctUntilChanged()

    override fun setString(key: String, value: String) {
        write(key, value)
    }

    private fun write(key: String, value: Any) {
        writes += key to value
        values.value = values.value + (key to value)
    }
}
