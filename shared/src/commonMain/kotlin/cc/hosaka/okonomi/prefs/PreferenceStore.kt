package cc.hosaka.okonomi.prefs

import kotlinx.coroutines.flow.Flow

/**
 * The app's persisted settings, as a seam rather than a storage API.
 *
 * This is deliberately smaller than what DataStore offers. Everything a
 * screen needs is "watch this value" and "write this value", and keeping
 * the interface at that shape is what lets a producer test hand in a map
 * instead of a file, and what would let the storage behind it change
 * without touching a caller.
 *
 * [setBoolean] and [setString] do not suspend and report nothing. A
 * preference write is fire-and-forget by nature: the caller is a UI
 * callback with no scope of its own, and the value it wrote comes back
 * through [booleanFlow] or [stringFlow] rather than from the call, so the
 * flow stays the single source of truth for what is stored. A write that
 * fails leaves the flow saying what is actually on disk, which is the
 * honest answer.
 *
 * Writes of either type are applied in the order they were asked for,
 * whichever type each is: one setting never overtakes another.
 *
 * A read that fails yields the default rather than an error: a setting
 * nobody can read is a setting nobody has set.
 */
interface PreferenceStore {
    /**
     * The stored value of [key], starting with what is on disk now and
     * emitting again whenever it changes. Emits [default] when the key
     * has never been written or the storage cannot be read.
     */
    fun booleanFlow(key: String, default: Boolean): Flow<Boolean>

    /** Stores [value] under [key]. Returns immediately; see the class kdoc. */
    fun setBoolean(key: String, value: Boolean)

    /**
     * [booleanFlow] for a string: what is stored under [key], or
     * [default] when nothing is or the storage cannot be read.
     *
     * A string is what an enum is stored as, and parsing it is the
     * caller's job: this seam cannot know which values are valid, so a
     * stored value the caller no longer recognises arrives here as
     * itself, not as [default].
     */
    fun stringFlow(key: String, default: String): Flow<String>

    /** Stores [value] under [key]. Returns immediately; see the class kdoc. */
    fun setString(key: String, value: String)
}
