package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.entryRows
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.healDictionaryAfter
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.user.FavouritesStore
import cc.hosaka.okonomi.user.appFavourites
import cc.hosaka.okonomi.user.decodeFavourites
import cc.hosaka.okonomi.user.encodeFavourites
import cc.hosaka.okonomi.user.printUserDataFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest

@Composable
fun produceFavouritesScreenState(): State<FavouritesState> = produceScreenState(
    key = "favourites",
    initial = FavouritesState(),
) {
    favouritesScreenStateProducer()
}

/**
 * The saved ids come from the user database and the rows they render as
 * come from the dictionary, which is why this is two sources rather than
 * one query. The seam between them is where a dangling id is handled:
 * [entryRows] returns no row for an id the dictionary no longer carries,
 * and nothing here writes that absence back.
 *
 * Export and import are driven from here rather than from the screen for
 * the same reason. Export writes the **raw** saved ids, not the rows
 * that resolved, so a word the dictionary has since dropped is still in
 * the file; and only this side knows whether the list is empty, which is
 * what decides between importing outright and warning first.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun ScreenStateScope.favouritesScreenStateProducer(
    favourites: FavouritesStore = appFavourites(),
    loadRows: suspend (List<Long>) -> List<SearchHit> = { entryRows(it) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
    report: (String, Throwable?) -> Unit = printUserDataFailure,
): Flow<FavouritesState> {
    // Bumped to re-ask for rows that already failed. Deliberately not
    // persisted: a retry attempt is not state worth restoring. The ids
    // it is retried against come from the store, which is the source of
    // truth either way.
    val retries = MutableStateFlow(0)
    val onRetry: () -> Unit = { retries.value++ }
    // What an offered file left behind, as data, and persisted across
    // runs of this producer.
    //
    // Persisted because the file dialog is another activity. The tab
    // stops being collected while it stands open, this producer is
    // cancelled five seconds after that, and the state left standing is
    // the last one it emitted — so the callback the picker returns to is
    // that state's. A plain MutableStateFlow created here would by then
    // be one nothing collects, and the reader would pick a file and
    // watch nothing happen at all. mutablePersistedFlow hands back the
    // same flow the restarted run reads, so the write lands either way.
    //
    // Data rather than the dialog itself: FavouritesImportPrompt carries
    // the callbacks that answer it, and those belong to one run of this
    // producer. They are rebuilt from this on every emission.
    val pending = mutablePersistedFlow<PendingImport?>(PENDING_IMPORT_KEY, null)
    // The rows standing on screen, so a failed reload can leave them
    // there rather than replacing a readable list with an error.
    // transformLatest runs its blocks one at a time, so no two writers
    // race for it.
    var standing: List<SearchHit>? = null
    val content = combine(favourites.favouriteEntryIds(), retries) { ids, _ -> ids }
        .transformLatest { ids ->
            if (ids.isEmpty()) {
                // Not a dictionary read at all: nothing saved is an
                // answer the dictionary cannot fail to give.
                standing = emptyList()
                emit(ids to FavouritesContentState.Ready(emptyList()))
                return@transformLatest
            }
            if (standing == null) {
                emit(ids to FavouritesContentState.Loading)
            }
            val next = try {
                val hits = loadRows(ids)
                standing = hits
                FavouritesContentState.Ready(hits)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failing dictionary must never take the tab down, and
                // must never be mistaken for the list being empty. What
                // the failure does to the shared handle is the one policy
                // every screen shares.
                healDictionaryAfter(e, invalidate)
                standing?.let { FavouritesContentState.Ready(it) }
                    ?: FavouritesContentState.Error(onRetry)
            }
            emit(ids to next)
        }
    return combine(content, pending) { (ids, body), request ->
        FavouritesState(
            content = body,
            // Nothing to export while the ids have not resolved into
            // anything yet, and nothing to export when nothing is saved.
            // A dictionary failure is not one of those: the saved ids are
            // known either way, and they are what the file holds.
            onExportJson = if (ids.isEmpty() || body is FavouritesContentState.Loading) {
                null
            } else {
                { encodeFavourites(ids) }
            },
            onFileImported = { text -> handleImportedFile(text, ids, favourites, pending, report) },
            importPrompt = request?.asPrompt(favourites, pending),
        )
    }
}

/**
 * The whole of the import decision: refuse what cannot be read, replace
 * an empty list outright, and warn before overwriting one that is not.
 *
 * Nothing is written on the refusal path, and nothing is written on the
 * warning path until the reader confirms — the ids sit in the confirm
 * callback until then.
 */
private fun handleImportedFile(
    text: String,
    saved: List<Long>,
    favourites: FavouritesStore,
    pending: MutableStateFlow<PendingImport?>,
    report: (String, Throwable?) -> Unit,
) {
    val entryIds = decodeFavourites(text)
    if (entryIds == null) {
        // Reported as well as shown: the dialog tells the reader the
        // file was refused, and this is the only place that survives to
        // say a file was offered at all.
        report("an imported favourites file could not be read", null)
        pending.value = PendingImport.Unreadable
        return
    }
    if (saved.isEmpty()) {
        favourites.replaceFavourites(entryIds)
    } else {
        pending.value = PendingImport.Confirm(entryIds)
    }
}

/**
 * An offered file that still needs an answer, holding only what has to
 * outlive a run of the producer. The dialog it becomes is built fresh
 * each emission by [asPrompt], because the callbacks that answer it
 * close over the run that made them.
 */
private sealed interface PendingImport {
    /** A readable file waiting on the overwrite warning. */
    data class Confirm(val entryIds: List<Long>) : PendingImport

    /** A file that could not be read, waiting to be dismissed. */
    data object Unreadable : PendingImport
}

private const val PENDING_IMPORT_KEY = "favourites-pending-import"

private fun PendingImport.asPrompt(
    favourites: FavouritesStore,
    pending: MutableStateFlow<PendingImport?>,
): FavouritesImportPrompt {
    val dismiss: () -> Unit = { pending.value = null }
    return when (this) {
        is PendingImport.Confirm -> FavouritesImportPrompt.ConfirmOverwrite(
            onConfirm = {
                favourites.replaceFavourites(entryIds)
                pending.value = null
            },
            onCancel = dismiss,
        )

        PendingImport.Unreadable -> FavouritesImportPrompt.Unreadable(onDismiss = dismiss)
    }
}
