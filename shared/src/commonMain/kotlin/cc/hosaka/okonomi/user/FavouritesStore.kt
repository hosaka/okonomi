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
 * Machine name of the second built-in list: every word opened from
 * search results, newest first. Same rules as [FAVOURITES_LIST_SLUG].
 */
const val HISTORY_LIST_SLUG = "history"

/** The history list's display name as stored. Screens show their own string. */
const val HISTORY_LIST_NAME = "History"

/**
 * The lists that exist. Both are built in and created on first use;
 * there are no lists of the reader's own making, so this is closed.
 *
 * [ord] is the row's position among lists, written once when the row is
 * created and read by nothing yet.
 */
enum class UserList(
    val slug: String,
    val initialName: String,
    val ord: Long,
) {
    Favourites(FAVOURITES_LIST_SLUG, FAVOURITES_LIST_NAME, 0),
    History(HISTORY_LIST_SLUG, HISTORY_LIST_NAME, 1),
}

/**
 * The reader's saved entries — Favourites, and the History of words
 * opened from search — as a seam rather than a storage API.
 *
 * Deliberately smaller than what the database offers, for the reason
 * `PreferenceStore` is: everything a screen needs is "watch what is
 * saved" and "change this", and keeping the interface at that shape is
 * what lets a producer test hand in a list instead of a file.
 *
 * No write suspends or reports anything. The caller is a tap in a
 * composition with no scope of its own, and the write must not hold up
 * the frame the tap landed in. What was written comes back through the
 * reads — [favouriteEntryIds], [isFavourite], [historyEntryIds] — never
 * from the call, so the flows stay the single source of truth for what
 * is stored: a write that fails leaves the heart saying "unsaved" and a
 * swiped row still in its list, which is the honest answer.
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

    /**
     * Replaces everything in [list] with [entryIds], whose first element
     * is the one shown first, and leaves every other list alone. An
     * empty [entryIds] empties the list.
     *
     * This is import, and import replaces: there is no merge and no
     * undo, and warning the reader first is the caller's job rather than
     * this one's. Duplicates in [entryIds] are stored once, keeping the
     * first occurrence's position.
     *
     * Non-suspending and silent for the reason [toggleFavourite] is, and
     * ordered against it for a reason of its own: an import and a heart
     * tap — or a recording — that land together must not interleave, so
     * all of them go through the same single writer.
     */
    fun replaceList(list: UserList, entryIds: List<Long>)

    /**
     * [replaceList], but only into an empty [list]; [otherwise] is
     * called instead when the list is not empty — and also when the
     * answer cannot be had, because the write failed or could not be
     * queued. An import uses it to decide whether to ask first, and the
     * failure case falls on the side of asking: a store that cannot say
     * whether the list is empty must never be taken for one that is.
     *
     * Decided by the writer, inside the transaction that would replace
     * the list, so a write still in the queue ahead of it — a word being
     * recorded — counts. [otherwise] runs on the writer, off the main
     * thread, and must only hand the answer on.
     */
    fun replaceListIfEmpty(list: UserList, entryIds: List<Long>, otherwise: () -> Unit)

    /**
     * The words opened from search results, most recently opened first,
     * every one of them: History has no cap. Same emission rules as
     * [favouriteEntryIds], and independent of it: a word can be in both.
     */
    fun historyEntryIds(): Flow<List<Long>>

    /**
     * Puts [entryId] at the top of History: added if it is not there,
     * moved if it is, so each word appears once. Nothing is ever
     * dropped to make room.
     *
     * Non-suspending and silent for the reason [toggleFavourite] is; the
     * caller is a row tap that is about to navigate, and must not wait.
     */
    fun recordInHistory(entryId: Long)

    /**
     * Takes [entryId] out of [list], and out of nothing else: a word in
     * both lists stays in the other.
     *
     * Not [toggleFavourite]. A swipe means "remove", so a row whose word
     * is already gone — a second swipe, a write from elsewhere landing
     * first — must stay gone rather than flip back in. Removing what is
     * not there is a no-op, decided inside the transaction like a
     * toggle is, and queued on the same writer.
     */
    fun removeFromList(list: UserList, entryId: Long)

    /**
     * Empties [list] and leaves every other list alone. No undo, and
     * asking the reader first is the caller's job.
     */
    fun clearList(list: UserList)
}
