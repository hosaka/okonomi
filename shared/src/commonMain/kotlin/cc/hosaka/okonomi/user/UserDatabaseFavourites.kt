package cc.hosaka.okonomi.user

import cc.hosaka.okonomi.db.awaitList
import cc.hosaka.okonomi.db.awaitOne
import cc.hosaka.okonomi.db.awaitOneOrNull
import cc.hosaka.okonomi.user.db.UserDb
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * How many unwritten changes the queue holds.
 *
 * Bounded on purpose. An unbounded queue cannot refuse, which sounds
 * like a virtue and is how a wedged writer becomes invisible: taps keep
 * being accepted, nothing is ever written, and the only symptom is a
 * button that will not change. With a bound, a queue that stops draining
 * starts refusing, and a refusal is something that can be reported. The
 * size is far past what a thumb can produce — this is a backstop, not a
 * throttle.
 */
private const val WRITE_QUEUE_CAPACITY = 64

/**
 * [FavouritesStore] over the user database.
 *
 * Failures are absorbed on both sides, which is the whole reason this
 * class exists rather than screens holding a `UserDb` directly — the same
 * argument `DataStorePreferenceStore` makes for settings, with more at
 * stake, since this is the only data in the app that cannot be
 * regenerated. Absorbed is not the same as hidden: everything caught
 * here is reported through [UserDataFailureReporter].
 *
 * Writes are **ordered**. Each one is queued to a single writer rather
 * than launched on its own: SQLite serialises its writers, but the order
 * independent coroutines reach it is not the order they were asked in,
 * so two quick taps could both read "not saved" and both save. An
 * import is queued behind the same writer for the same reason: it
 * replaces the whole list, so a heart tap that landed beside it must
 * fall clearly before or clearly after, never inside.
 *
 * The writer **survives what a write throws**, including an `Error`. It
 * is a single `for` over a channel, and a loop that ends never starts
 * again: every later save would be accepted by the queue and quietly
 * dropped for the life of the process, with the button refusing and
 * nothing to say why. So the drain is restarted rather than lost.
 *
 * Reads re-run on a **revision counter** that a successful write bumps.
 * SQLDelight can notify query listeners instead, but its coroutine
 * bridge lives in an artifact this project deliberately does not depend
 * on (see the hand-written `awaitList`/`awaitOne` this file uses for the
 * same reason). A counter is the smaller mechanism and has the property
 * that matters here: nothing re-emits until a write actually landed, so
 * a failed write leaves every flow saying what is really on disk.
 *
 * The read flow is **shared**, and [isFavourite] is derived from it. One
 * query, one answer: the button on the entry view and the rows on the
 * Favourites tab observe the same emission rather than issuing their own
 * reads, so they cannot settle a beat apart and disagree about whether a
 * word is saved.
 */
internal class UserDatabaseFavourites(
    private val database: suspend () -> UserDatabase = ::userDatabase,
    private val now: () -> Long = ::epochMillis,
    private val report: UserDataFailureReporter = printUserDataFailure,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : FavouritesStore {

    private val writes = Channel<Write>(WRITE_QUEUE_CAPACITY)

    /**
     * Bumped once per landed write. Only the writer coroutine touches
     * it, so the reads it triggers always run after the write they are
     * reporting.
     */
    private val revisions = MutableStateFlow(0)

    /**
     * The one read every collector shares. It runs in [scope], which is
     * not the collector's: the first collection opens the database file
     * and may migrate it, and a screen's state flow can be collecting on
     * the main thread.
     *
     * `replayExpirationMillis = 0` so the cache is dropped once the last
     * collector leaves. A screen coming back reads storage again rather
     * than being handed whatever was true when it left.
     */
    private val entryIds: Flow<List<Long>> = revisions
        .map { readEntryIds() }
        .distinctUntilChanged()
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = SHARE_STOP_TIMEOUT_MILLIS,
                replayExpirationMillis = 0,
            ),
            replay = 1,
        )

    init {
        scope.launch {
            while (true) {
                try {
                    for (write in writes) {
                        if (runWrite(write)) {
                            revisions.value++
                        }
                    }
                    // The channel was closed; there is nothing left to drain.
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // Anything runWrite did not catch — an Error, or a
                    // failure in the bookkeeping around it. Restarting
                    // costs one lost write; not restarting costs every
                    // write for the rest of the process.
                    report("the favourites writer failed and was restarted", t)
                }
            }
        }
    }

    override fun favouriteEntryIds(): Flow<List<Long>> = entryIds

    override fun isFavourite(entryId: Long): Flow<Boolean> = entryIds
        .map { entryId in it }
        .distinctUntilChanged()

    override fun toggleFavourite(entryId: Long) {
        queue(Write.Toggle(entryId = entryId, at = now()))
    }

    override fun replaceFavourites(entryIds: List<Long>) {
        queue(Write.Replace(entryIds = entryIds, at = now()))
    }

    private fun queue(write: Write) {
        // trySend rather than a launch, so the tap returns in the frame
        // it landed in. A full queue means the writer is not draining;
        // the caller has no way to act on that, but a bug report does.
        if (!writes.trySend(write).isSuccess) {
            report(
                "${write.description} was dropped: " +
                    "the write queue is full ($WRITE_QUEUE_CAPACITY unwritten changes)",
                null,
            )
        }
    }

    private suspend fun readEntryIds(): List<Long> = try {
        val db = database().db
        val listId = db.favouritesListId()
        if (listId == null) emptyList() else db.list_entryQueries.entriesInList(listId).awaitList()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A store nobody can read is a store with nothing saved in it,
        // as far as any screen is concerned (the spec's ruling). Never
        // an exception: this runs inside a screen's state flow. Always
        // reported: an empty list is otherwise the same answer as an
        // empty store.
        report("the saved words could not be read", e)
        emptyList()
    }

    /** True when the write landed, which is the only thing that re-emits the reads. */
    private suspend fun runWrite(write: Write): Boolean = try {
        val db = database().db
        db.transaction {
            val listId = db.ensureFavouritesList(write.at)
            when (write) {
                is Write.Toggle -> db.applyToggle(listId, write)
                is Write.Replace -> db.applyReplace(listId, write)
            }
            db.listQueries.touchList(updated_at = write.at, id = listId)
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The reads keep reporting what is actually stored, so the
        // button is seen to refuse rather than seen to lie.
        report("${write.description} could not be written", e)
        false
    }

    private suspend fun UserDb.applyToggle(listId: Long, write: Write.Toggle) {
        // Read inside the transaction, so what the toggle flips is what
        // is stored at this instant rather than what a screen was
        // showing when the reader tapped. See
        // FavouritesStore.toggleFavourite.
        if (list_entryQueries.isInList(list_id = listId, entry_id = write.entryId).awaitOne()) {
            list_entryQueries.removeFromList(list_id = listId, entry_id = write.entryId)
        } else {
            // The append counter, read inside the transaction so two
            // saves cannot compute the same position.
            val ord = list_entryQueries.nextOrdInList(listId).awaitOne()
            list_entryQueries.addToList(
                list_id = listId,
                entry_id = write.entryId,
                ord = ord,
                created_at = write.at,
            )
        }
    }

    /**
     * Clear and rewrite, in the one transaction the caller is already
     * inside: nothing ever observes the list emptied, and a failure
     * anywhere in it leaves the reader's saved words exactly as they
     * were.
     *
     * `ord` counts down from the size, so the file's first id gets the
     * highest one and `entriesInList`, which reads `ORDER BY ord DESC`,
     * hands it back first. A duplicate is an INSERT OR IGNORE no-op
     * against the primary key, which leaves the first occurrence with
     * its higher position.
     *
     * `created_at` is **restamped**, including for ids that were already
     * saved. `list_entry.sq` states the opposite invariant for saving —
     * re-saving an entry keeps its original `created_at` — and this is
     * the one write that does not honour it: an import is a new list
     * rather than an edit to the old one, and the rows it clears are
     * gone before the rows it writes exist, so there is no earlier
     * timestamp left to carry over. Nothing renders `created_at` today.
     * Anything that comes to depend on it — "saved this week", say —
     * needs to know that re-importing your own export moves every date
     * to the import.
     */
    private suspend fun UserDb.applyReplace(listId: Long, write: Write.Replace) {
        list_entryQueries.clearList(listId)
        write.entryIds.forEachIndexed { index, entryId ->
            list_entryQueries.addToList(
                list_id = listId,
                entry_id = entryId,
                ord = (write.entryIds.size - index).toLong(),
                created_at = write.at,
            )
        }
    }

    /**
     * One queued change. Sealed rather than two channels: order between
     * an import and a heart tap is exactly the thing the single writer
     * exists to fix, and two queues would put it back.
     */
    private sealed class Write {
        abstract val at: Long

        /** How a report names this write; the reader never sees it. */
        abstract val description: String

        class Toggle(
            val entryId: Long,
            override val at: Long,
        ) : Write() {
            override val description: String
                get() = "a favourite change for entry $entryId"
        }

        class Replace(
            val entryIds: List<Long>,
            override val at: Long,
        ) : Write() {
            override val description: String
                get() = "an import of ${entryIds.size} saved words"
        }
    }
}

/**
 * How long the shared read stays alive after its last collector. Long
 * enough to cover moving between the entry view and the Favourites tab
 * without reopening the read, short enough that a backgrounded app is
 * not holding one.
 */
private const val SHARE_STOP_TIMEOUT_MILLIS = 5_000L

private suspend fun UserDb.favouritesListId(): Long? =
    listQueries.listBySlug(FAVOURITES_LIST_SLUG).awaitOneOrNull()?.id

/**
 * The shipped list's id, creating the row on first use. Insert-or-ignore
 * then select rather than select-then-insert: the slug is unique, so two
 * concurrent creators end up on the same row instead of racing to make a
 * second one.
 */
private suspend fun UserDb.ensureFavouritesList(at: Long): Long {
    listQueries.insertList(
        slug = FAVOURITES_LIST_SLUG,
        name = FAVOURITES_LIST_NAME,
        ord = 0,
        created_at = at,
    )
    return listQueries.listBySlug(FAVOURITES_LIST_SLUG).awaitOne().id
}

@OptIn(ExperimentalTime::class)
private fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()

private val sharedFavouritesStore: FavouritesStore by lazy { UserDatabaseFavourites() }

/**
 * The shared app-lifetime favourites store. One instance for the
 * process, so every screen queues its writes behind the same writer and
 * observes the same shared read.
 */
fun appFavourites(): FavouritesStore = sharedFavouritesStore
