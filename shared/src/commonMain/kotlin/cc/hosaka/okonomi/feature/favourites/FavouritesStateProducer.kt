package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.anki.AnkiAccess
import cc.hosaka.okonomi.anki.AnkiExport
import cc.hosaka.okonomi.anki.AnkiSendResult
import cc.hosaka.okonomi.anki.ankiSendScope
import cc.hosaka.okonomi.anki.appAnkiExport
import cc.hosaka.okonomi.anki.toAnkiNote
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.entryGlosses
import cc.hosaka.okonomi.db.entryRows
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.healDictionaryAfter
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.user.FavouritesStore
import cc.hosaka.okonomi.user.UserList
import cc.hosaka.okonomi.user.appFavourites
import cc.hosaka.okonomi.user.decodeFavourites
import cc.hosaka.okonomi.user.encodeFavourites
import cc.hosaka.okonomi.user.printUserDataFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.anki_card_link
import org.jetbrains.compose.resources.getString

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
 *
 * The tab shows one list at a time, Favourites or History. Everything
 * below follows the list on show: export writes it, Clear list and a
 * swipe empty or shorten it. Import is the one thing that names its list
 * itself, because the file dialog it waits on can outlive the state that
 * opened it — see [FavouritesState.onFileImported].
 *
 * Sending to AnkiDroid is Favourites' alone, and only where [anki]
 * exists. It sends the rows on show — the words as the reader sees them
 * — and runs on [ankiScope] rather than on this producer, which is
 * cancelled five seconds after the tab stops being watched; see
 * [AnkiStage] for why its progress is persisted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun ScreenStateScope.favouritesScreenStateProducer(
    favourites: FavouritesStore = appFavourites(),
    loadRows: suspend (List<Long>) -> List<SearchHit> = { entryRows(it) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
    report: (String, Throwable?) -> Unit = printUserDataFailure,
    anki: AnkiExport? = appAnkiExport(),
    loadGlosses: suspend (List<Long>) -> Map<Long, List<String>> = { entryGlosses(it) },
    ankiLinkLabel: suspend () -> String = { getString(Res.string.anki_card_link) },
    ankiScope: CoroutineScope = ankiSendScope,
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
    // producer. They are rebuilt from this on every emission. It names
    // the list the file is for, so the answer replaces that list
    // whichever list the tab has come back on.
    val pending = mutablePersistedFlow<PendingImport?>(PENDING_IMPORT_KEY, null)
    // The list on show. Persisted so the tab keeps the reader's pick for
    // as long as the process lives — mutablePersistedFlow is in memory
    // only, which is also what makes a fresh process open on Favourites.
    val selected = mutablePersistedFlow(SELECTED_LIST_KEY, UserList.Favourites)
    val onSelectList: (UserList) -> Unit = { selected.value = it }
    // The list a Clear list confirmation is standing for. Persisted for
    // the reason the pending import is: an answer that arrives through a
    // state an earlier run left standing must still land.
    val clearing = mutablePersistedFlow<UserList?>(PENDING_CLEAR_KEY, null)
    // The rows standing on screen and the list they belong to, so a
    // failed reload can leave them there rather than replacing a
    // readable list with an error — but never another list's rows.
    // transformLatest runs its blocks one at a time, so no two writers
    // race for it.
    var standing: Pair<UserList, List<SearchHit>>? = null
    // One instance per list rather than one per emission, so two
    // otherwise equal states stay equal.
    val removeFrom: Map<UserList, (Long) -> Unit> = UserList.entries.associateWith { list ->
        { entryId: Long -> favourites.removeFromList(list, entryId) }
    }
    val onFileImported: (UserList, String) -> Unit = { target, text ->
        handleImportedFile(target, text, favourites, selected, pending, clearing, report)
    }
    val ankiStage = mutablePersistedFlow<AnkiStage?>(ANKI_STAGE_KEY, null)
    val launchAnkiSend: (List<SearchHit>) -> Unit = { hits ->
        if (anki != null) {
            ankiScope.launch {
                // Whatever happens below, the send ends in a dialog: the
                // finally leaves the Sending stage even for a Throwable
                // that is not an Exception, so the menu item can never be
                // left disabled for good with no dialog coming.
                var result: AnkiSendResult = AnkiSendResult.Failed(added = 0, attempted = 0)
                try {
                    // The rows on screen carry only their first senses; a
                    // card gets the entry's glosses, read here off the
                    // main thread rather than at the tap.
                    val glosses = loadGlosses(hits.map { it.entryId })
                    result = anki.send(hits.map { it.toAnkiNote(glosses[it.entryId].orEmpty()) }, ankiLinkLabel())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // send() promises not to throw, so this is the
                    // dictionary failing, or a broken promise.
                    report("the saved words could not be prepared for AnkiDroid", e)
                } finally {
                    ankiStage.value = AnkiStage.Done(result)
                }
            }
        }
    }
    val startAnkiSend: (List<SearchHit>) -> Unit = { hits ->
        when (anki?.access()) {
            null -> Unit
            AnkiAccess.Unavailable -> ankiStage.compareAndSet(null, AnkiStage.Done(AnkiSendResult.Unavailable))
            AnkiAccess.NeedsPermission -> ankiStage.compareAndSet(null, AnkiStage.AwaitingPermission(hits))
            AnkiAccess.Granted -> if (ankiStage.compareAndSet(null, AnkiStage.Sending)) launchAnkiSend(hits)
        }
    }
    val listed = selected.flatMapLatest { list ->
        favourites.entryIds(list).map { ids -> list to ids }
    }
    val content = combine(listed, retries) { shown, _ -> shown }
        .transformLatest { (list, ids) ->
            // Only the standing rows still in this list are worth
            // keeping up through a failure. A list emptied and filled
            // again has none — and keeping "no rows" up would show the
            // empty state over a list that is not empty.
            val onScreen = standing
                ?.takeIf { it.first == list }
                ?.second
                ?.filter { it.entryId in ids }
                ?.takeIf { it.isNotEmpty() }
            if (ids.isEmpty()) {
                // Not a dictionary read at all: nothing saved is an
                // answer the dictionary cannot fail to give.
                standing = list to emptyList()
                emit(ListContent(list, ids, FavouritesContentState.Ready(emptyList())))
                return@transformLatest
            }
            if (onScreen == null) {
                emit(ListContent(list, ids, FavouritesContentState.Loading))
            }
            val next = try {
                val hits = loadRows(ids)
                standing = list to hits
                FavouritesContentState.Ready(hits)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failing dictionary must never take the tab down, and
                // must never be mistaken for the list being empty. What
                // the failure does to the shared handle is the one policy
                // every screen shares.
                healDictionaryAfter(e, invalidate)
                onScreen?.let { FavouritesContentState.Ready(it) }
                    ?: FavouritesContentState.Error(onRetry)
            }
            emit(ListContent(list, ids, next))
        }
        // A confirmation standing for a list that has since emptied —
        // its last row swiped away — has nothing left to ask about.
        // Dropped rather than merely hidden, or it would stand up again
        // the moment a word landed in the list.
        .onEach { (list, ids) -> if (ids.isEmpty()) clearing.compareAndSet(list, null) }
    return combine(content, pending, clearing, ankiStage) { (list, ids, body), request, clearRequest, stage ->
        // A prompt stands only over the list it would replace. The file
        // switches the tab to its list when it arrives, so in practice
        // the two agree; this keeps them agreeing through a state an
        // earlier run left standing.
        val importPrompt = request?.takeIf { it.list == list }?.asPrompt(favourites, pending)
        // A send's result is the reader's whichever list is on show, so
        // it stands over either — but never over an import's question,
        // which a file can raise at any time. It waits behind it.
        val ankiPrompt = (stage as? AnkiStage.Done)
            ?.takeIf { importPrompt == null }
            ?.let { done -> FavouritesAnkiPrompt(done.result, onDismiss = { ankiStage.compareAndSet(done, null) }) }
        val sendable = (body as? FavouritesContentState.Ready)?.hits.orEmpty()
        // Only for the list on show and only while it has rows; the
        // onEach above drops one that outlived its list's last row.
        val clearPrompt = clearRequest?.takeIf {
            it == list && ids.isNotEmpty() && importPrompt == null && ankiPrompt == null
        }?.let { target ->
            FavouritesClearPrompt(
                list = target,
                // One-shot, as the import's confirmation is.
                onConfirm = {
                    if (clearing.compareAndSet(target, null)) {
                        favourites.clearList(target)
                    }
                },
                onCancel = { clearing.value = null },
            )
        }
        FavouritesState(
            content = body,
            list = list,
            onSelectList = onSelectList,
            // Nothing to export while the ids have not resolved into
            // anything yet, and nothing to export when the list is empty.
            // A dictionary failure is not one of those: the ids are known
            // either way, and they are what the file holds — all of them,
            // in the order the list shows them.
            onExportJson = if (ids.isEmpty() || body is FavouritesContentState.Loading) {
                null
            } else {
                { encodeFavourites(ids, name = list.initialName) }
            },
            onFileImported = onFileImported,
            importPrompt = importPrompt,
            // The ids, not the rows: a list whose words the dictionary
            // has dropped still holds them, and clearing it is still
            // something to offer. Not while an import is asking its own
            // question: one dialog at a time.
            onClearList = if (ids.isEmpty() || importPrompt != null || stage != null) {
                null
            } else {
                { clearing.value = list }
            },
            clearPrompt = clearPrompt,
            // Bound to the list this emission shows, so a row swiped on
            // one list can never be taken out of the other.
            onRemoveEntry = removeFrom.getValue(list),
            showSendToAnki = anki != null && list == UserList.Favourites,
            // One send at a time, one dialog at a time, and only the rows
            // that resolved: a word the dictionary has dropped has
            // nothing to put on a card.
            onSendToAnki = if (
                anki != null &&
                list == UserList.Favourites &&
                sendable.isNotEmpty() &&
                stage == null &&
                importPrompt == null &&
                clearPrompt == null
            ) {
                { startAnkiSend(sendable) }
            } else {
                null
            },
            onAnkiPermissionResult = (stage as? AnkiStage.AwaitingPermission)?.let { waiting ->
                { answer: AnkiPermissionAnswer ->
                    when (answer) {
                        AnkiPermissionAnswer.Granted ->
                            if (ankiStage.compareAndSet(waiting, AnkiStage.Sending)) launchAnkiSend(waiting.hits)

                        AnkiPermissionAnswer.Denied, AnkiPermissionAnswer.DeniedPermanently -> ankiStage.compareAndSet(
                            waiting,
                            AnkiStage.Done(
                                AnkiSendResult.PermissionDenied(
                                    permanently = answer == AnkiPermissionAnswer.DeniedPermanently,
                                ),
                            ),
                        )
                    }
                }
            },
            ankiPrompt = ankiPrompt,
        )
    }
}

/**
 * Where a send to AnkiDroid has got to. Persisted for the reason the
 * pending import is: the permission dialog is another activity, so its
 * answer comes back through a state an earlier run of the producer left
 * standing, and a send can finish after the run that started it has
 * been cancelled.
 */
private sealed interface AnkiStage {
    /** The rows to send wait here while the reader is asked for AnkiDroid access. */
    data class AwaitingPermission(val hits: List<SearchHit>) : AnkiStage

    data object Sending : AnkiStage

    /** Finished, with a dialog standing until it is dismissed. */
    data class Done(val result: AnkiSendResult) : AnkiStage
}

private const val ANKI_STAGE_KEY = "favourites-anki-stage"

/** One list's ids and the body they resolved to, as one emission. */
private data class ListContent(
    val list: UserList,
    val ids: List<Long>,
    val body: FavouritesContentState,
)

private fun FavouritesStore.entryIds(list: UserList): Flow<List<Long>> = when (list) {
    UserList.Favourites -> favouriteEntryIds()
    UserList.History -> historyEntryIds()
}

/**
 * The whole of the import decision: refuse what cannot be read, replace
 * an empty list outright, and warn before overwriting one that is not.
 *
 * [target] is the list the reader asked to import into, which the file
 * dialog has carried across however long it stood open. The tab is
 * switched to it, so whatever comes next — the rows, or a question —
 * is about the list on screen.
 *
 * Whether it is empty is decided by the store's writer, in the
 * transaction that would replace it (see
 * [FavouritesStore.replaceListIfEmpty]) — not from a read here, which
 * reports a store it cannot read as empty, and which a write still
 * queued ahead of it would make stale. Anything but a clean "it was
 * empty, and it is replaced" lands on the warning.
 *
 * Nothing is written on the refusal path, and nothing is written on the
 * warning path until the reader confirms — the ids sit in the pending
 * import until then.
 */
private fun handleImportedFile(
    target: UserList,
    text: String,
    favourites: FavouritesStore,
    selected: MutableStateFlow<UserList>,
    pending: MutableStateFlow<PendingImport?>,
    clearing: MutableStateFlow<UserList?>,
    report: (String, Throwable?) -> Unit,
) {
    // A file arriving answers any Clear list still standing: the import
    // is the newer question, and two dialogs must never stack. Only
    // reachable through a callback an earlier state left standing — the
    // clear dialog is modal — but that is exactly how a file arrives.
    clearing.value = null
    selected.value = target
    val entryIds = decodeFavourites(text)
    if (entryIds == null) {
        // Reported as well as shown: the dialog tells the reader the
        // file was refused, and this is the only place that survives to
        // say a file was offered at all.
        report("a file imported into ${target.slug} could not be read", null)
        pending.value = PendingImport.Unreadable(target)
        return
    }
    favourites.replaceListIfEmpty(target, entryIds) {
        pending.value = PendingImport.Confirm(target, entryIds)
    }
}

/**
 * An offered file that still needs an answer, holding only what has to
 * outlive a run of the producer. The dialog it becomes is built fresh
 * each emission by [asPrompt], because the callbacks that answer it
 * close over the run that made them.
 */
private sealed interface PendingImport {
    /** The list the file is for. */
    val list: UserList

    /** A readable file waiting on the overwrite warning. */
    data class Confirm(override val list: UserList, val entryIds: List<Long>) : PendingImport

    /** A file that could not be read, waiting to be dismissed. */
    data class Unreadable(override val list: UserList) : PendingImport
}

private const val PENDING_IMPORT_KEY = "favourites-pending-import"

private const val SELECTED_LIST_KEY = "favourites-selected-list"

private const val PENDING_CLEAR_KEY = "favourites-pending-clear"

private fun PendingImport.asPrompt(
    favourites: FavouritesStore,
    pending: MutableStateFlow<PendingImport?>,
): FavouritesImportPrompt {
    val dismiss: () -> Unit = { pending.value = null }
    return when (this) {
        is PendingImport.Confirm -> FavouritesImportPrompt.ConfirmOverwrite(
            list = list,
            // One-shot: a second tap before the dialog has gone finds the
            // request already taken and writes nothing. Two replaces
            // queued back to back would wipe whatever landed between them.
            onConfirm = {
                if (pending.compareAndSet(this, null)) {
                    favourites.replaceList(list, entryIds)
                }
            },
            onCancel = dismiss,
        )

        is PendingImport.Unreadable -> FavouritesImportPrompt.Unreadable(list = list, onDismiss = dismiss)
    }
}
