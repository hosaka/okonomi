package cc.hosaka.okonomi.user

import kotlinx.coroutines.flow.Flow

/**
 * Machine name of the one list that ships. Never shown and never
 * changed: [FAVOURITES_LIST_NAME] is what the reader sees and what a
 * later rename would edit, and an export written today has to keep
 * naming the same list when it is read back.
 */
const val FAVOURITES_LIST_SLUG = "favourites"

/** The shipped list's initial display name. */
const val FAVOURITES_LIST_NAME = "Favourites"

/**
 * The reader's saved entries, as a seam rather than a storage API.
 *
 * Deliberately smaller than what the database offers, for the reason
 * `PreferenceStore` is: everything a screen needs is "watch what is
 * saved" and "change this", and keeping the interface at that shape is
 * what lets a producer test hand in a list instead of a file.
 *
 * Neither write suspends or reports anything. The caller is a button in
 * a composition with no scope of its own, and the write must not hold up
 * the frame the tap landed in. What was written comes back through
 * [isFavourite], never from the call, so the flow stays the single
 * source of truth for what is stored — a write that fails leaves the
 * button saying "unsaved", which is the honest answer.
 *
 * A read that fails yields an empty list rather than an error: an
 * unreadable store must never take a screen down. This is the spec's own
 * ruling ("Unreadable store yields an empty list, not a crash") and it
 * has a cost worth stating — to a screen, a store that has stopped
 * working is indistinguishable from one with nothing in it. Every such
 * failure is therefore reported through [UserDataFailureReporter], which
 * is the only place the difference survives.
 *
 * Entry ids only, and only ever ids (Alex's ruling). They are JMdict
 * `ent_seq` values, stable across releases. An id whose entry a later
 * dictionary no longer carries stays stored: resolving it is the
 * screen's problem, and a failed lookup is never a reason to throw the
 * reader's saved word away.
 */
interface FavouritesStore {
    /**
     * The saved entry ids, most recently saved first, re-emitted
     * whenever the set changes. Emits an empty list when nothing is
     * saved or the store cannot be read.
     */
    fun favouriteEntryIds(): Flow<List<Long>>

    /** Whether [entryId] is saved, re-emitted whenever that changes. */
    fun isFavourite(entryId: Long): Flow<Boolean>

    /**
     * Saves [entryId] if it is not saved and unsaves it if it is,
     * deciding against what is **stored** at the moment the write runs
     * rather than against what a screen was showing when it was tapped.
     *
     * That is the difference between this and a `setFavourite(id, value)`
     * the caller computes, and it is not theoretical. The button reads
     * committed state, so two quick taps on an unsaved word both compute
     * "save" and the word ends up saved — the reader's second tap does
     * nothing. The same happens on a first tap that lands before the
     * first read of storage has come back, where the button is showing
     * its seeded "unsaved" for a word that is in fact saved: the
     * "unsave" the reader asked for arrives as a save of something
     * already there, an insert-or-ignore no-op with nothing on screen to
     * say so. Deciding inside the transaction removes both.
     */
    fun toggleFavourite(entryId: Long)
}
