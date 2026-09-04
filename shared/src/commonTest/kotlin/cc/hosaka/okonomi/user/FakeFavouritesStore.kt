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
) : FavouritesStore {

    private val saved = MutableStateFlow(initial)

    /** Every id [toggleFavourite] was asked for, in order. */
    val writes = mutableListOf<Long>()

    /** Every list [replaceFavourites] was asked for, in order. */
    val replacements = mutableListOf<List<Long>>()

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

    override fun replaceFavourites(entryIds: List<Long>) {
        replacements += entryIds
        // `distinct` rather than the list as given, for the reason
        // `toggleFavourite` leaves a re-saved id where it is: the
        // production store inserts these against a primary key, so a
        // duplicate keeps its first, earlier position and no second row
        // appears.
        saved.value = entryIds.distinct()
    }
}
