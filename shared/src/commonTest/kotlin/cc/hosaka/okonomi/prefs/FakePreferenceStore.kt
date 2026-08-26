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
) : PreferenceStore {
    private val values = MutableStateFlow(initial)

    /** Every write, in order, so a test can assert what reached storage. */
    val writes = mutableListOf<Pair<String, Boolean>>()

    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = values
        .map { stored -> stored[key] ?: default }
        .distinctUntilChanged()

    override fun setBoolean(key: String, value: Boolean) {
        writes += key to value
        values.value = values.value + (key to value)
    }
}
