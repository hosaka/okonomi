package cc.hosaka.okonomi.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * [FavouritesStore] over a list in memory, for producer and screen tests.
 *
 * It keeps the seam's contract that matters above it and nothing else:
 * writes are visible through the reads rather than returned, the newest
 * save comes first, and a save of something already saved leaves it
 * where it is. That last rule is not decoration — production stores with
 * `INSERT OR IGNORE`, and `UserDatabaseFavouritesTest` asserts it — so a
 * fake that moved the id to the front would run every test above this
 * seam against the opposite rule to the one that ships.
 *
 * What a real store also has to survive — a file that will not open, a
 * write that cannot land, a writer that dies — is not modelled here on
 * purpose; `UserDatabaseFavouritesTest` covers it against the real
 * thing, and a fake that pretended to fail would only be testing its own
 * pretence.
 */
internal class FakeFavouritesStore(
    initial: List<Long> = emptyList(),
    initialHistory: List<Long> = emptyList(),
    /**
     * Stands in for a store that cannot say whether a list is empty: a
     * conditional import then always answers "not written", as the
     * production store does when its write fails.
     */
    private val cannotTellEmptiness: Boolean = false,
) : FavouritesStore {

    private val saved = MutableStateFlow(initial)

    private val history = MutableStateFlow(initialHistory)

    /** Every (list, id) [removeFromList] was asked for, in order. */
    val removals = mutableListOf<Pair<UserList, Long>>()

    /** Every list [clearList] was asked to empty, in order. */
    val clears = mutableListOf<UserList>()

    /** Every id [toggleFavourite] was asked for, in order. */
    val writes = mutableListOf<Long>()

    /** Every (list, ids) [replaceList] was asked for, in order. */
    val replacements = mutableListOf<Pair<UserList, List<Long>>>()

    override fun favouriteEntryIds(): Flow<List<Long>> = saved

    override fun isFavourite(entryId: Long): Flow<Boolean> = saved.map { entryId in it }

    override fun toggleFavourite(entryId: Long) {
        writes += entryId
        saved.value = if (entryId in saved.value) {
            saved.value.filterNot { it == entryId }
        } else {
            listOf(entryId) + saved.value
        }
    }

    override fun replaceList(list: UserList, entryIds: List<Long>) {
        replacements += list to entryIds
        // `distinct` rather than the list as given, for the reason
        // `toggleFavourite` leaves a re-saved id where it is: the
        // production store inserts these against a primary key, so a
        // duplicate keeps its first, earlier position and no second row
        // appears.
        when (list) {
            UserList.Favourites -> saved.value = entryIds.distinct()
            UserList.History -> history.value = entryIds.distinct()
        }
    }

    override fun replaceListIfEmpty(list: UserList, entryIds: List<Long>, otherwise: () -> Unit) {
        val current = when (list) {
            UserList.Favourites -> saved.value
            UserList.History -> history.value
        }
        if (cannotTellEmptiness || current.isNotEmpty()) otherwise() else replaceList(list, entryIds)
    }

    override fun historyEntryIds(): Flow<List<Long>> = history

    /** Moves to the front, unlike a re-save: the production store does the same. */
    override fun recordInHistory(entryId: Long) {
        history.value = listOf(entryId) + history.value.filterNot { it == entryId }
    }

    override fun removeFromList(list: UserList, entryId: Long) {
        removals += list to entryId
        when (list) {
            UserList.Favourites -> saved.value = saved.value.filterNot { it == entryId }
            UserList.History -> history.value = history.value.filterNot { it == entryId }
        }
    }

    override fun clearList(list: UserList) {
        clears += list
        when (list) {
            UserList.Favourites -> saved.value = emptyList()
            UserList.History -> history.value = emptyList()
        }
    }
}
