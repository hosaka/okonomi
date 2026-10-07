package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.user.UserDataFailureReporter
import cc.hosaka.okonomi.user.UserList
import cc.hosaka.okonomi.user.printUserDataFailure
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

private const val EXPORT_FILE_EXTENSION = "json"

/** The Favourites tab's root. */
@Serializable
data object FavouritesRoute : Route {
    @Composable
    override fun Content() {
        val state by produceFavouritesScreenState()
        val transfer = rememberFavouritesTransfer(
            list = state.list,
            onExportJson = state.onExportJson,
            onFileImported = state.onFileImported,
        )
        AnkiPermissionRequest(state.onAnkiPermissionResult)
        FavouritesScreen(
            state = state,
            onExportClick = transfer.onExportClick,
            onImportClick = transfer.onImportClick,
        )
    }
}

/** What the toolbar menu needs: two taps, or nulls where an action is unavailable. */
private class FavouritesMenuActions(
    val onExportClick: (() -> Unit)?,
    val onImportClick: (() -> Unit)?,
)

/**
 * The system file dialogs, remembered at the route's root rather than
 * inside the menu that opens them. FileKit's own documentation warns
 * that a launcher remembered inside a dropdown or a dialog is disposed
 * with it on iOS, taking the pending result with it — and keeping them
 * out here is also what leaves `FavouritesScreen` a pure renderer that
 * the existing UI tests can host.
 *
 * Some behaviours in here are **not** covered by any test, and cannot
 * be: a cancelled dialog writing nothing, the name the save dialog
 * suggests, the list a picked file is handed on with, a file read that
 * outlives the route leaving composition, and a stashed file being
 * handed on once a handler appears. All are settled inside a launcher
 * callback, or an effect waiting on a state no host test can rebuild
 * the way a recreated activity does, so a test for any of them would
 * have to assert against a restatement of the check rather than the
 * check — green with the real one deleted. They are verified by running
 * the app instead. What the callbacks delegate to ([writeExport],
 * [readImport], [deliverPickedFile]) is tested, as is everything on the
 * other side of `onFileImported`.
 */
@Composable
private fun rememberFavouritesTransfer(
    list: UserList?,
    onExportJson: (() -> String)?,
    onFileImported: ((UserList, String) -> Unit)?,
): FavouritesMenuActions {
    val scope = rememberCoroutineScope()
    // The handler as it is now, not as it was when the picker was
    // remembered: the file can come back to a state rebuilt meanwhile.
    val currentOnFileImported by rememberUpdatedState(onFileImported)

    // The slug of the list Import was tapped on. The picker is another
    // activity: the file comes back to whatever state is standing then,
    // which may have been rebuilt on another list, so the list the file
    // is for has to travel with the dialog rather than be read off the
    // state. Saveable for the reason pendingExport is, and a String
    // because that saves on every platform.
    var importTarget by rememberSaveable { mutableStateOf<String?>(null) }

    // A file that came back while there was nothing to hand it to — the
    // seeded first frame of a screen rebuilt behind the dialog — held
    // until there is. Saveable for the same reason as the target.
    var stashedTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var stashedText by rememberSaveable { mutableStateOf<String?>(null) }
    val stash: (String, String) -> Unit = { slug, text ->
        stashedTarget = slug
        stashedText = text
    }
    LaunchedEffect(onFileImported != null, stashedText) {
        val text = stashedText ?: return@LaunchedEffect
        val handler = currentOnFileImported ?: return@LaunchedEffect
        val slug = stashedTarget
        stashedTarget = null
        stashedText = null
        deliverPickedFile(slug, text, handler, stash)
    }

    // Encoded when the reader picks Export, not when the dialog comes
    // back: the file is what was saved at the moment they asked for it.
    // Saveable because the save dialog is another activity, and this
    // one can be recreated underneath it.
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }

    val saver = rememberFileSaverLauncher(
        dialogSettings = FileKitDialogSettings.createDefault(),
    ) { file ->
        val json = pendingExport
        pendingExport = null
        if (file == null) return@rememberFileSaverLauncher
        if (json == null) {
            // The dialog created the document and there is nothing to
            // put in it. Rare — the process would have to have been
            // rebuilt without restoring the pending export — but it
            // leaves an empty file at a place the reader chose, so it
            // is worth a line in a bug report rather than silence.
            printUserDataFailure("an export was lost before it could be written", null)
            return@rememberFileSaverLauncher
        }
        scope.launch {
            // NonCancellable because this scope dies with the
            // composition, and the composition dies when the reader
            // switches tabs — which they can do the instant the save
            // dialog closes. A cancelled writeString leaves a truncated
            // file at a destination the reader picked, and the
            // cancellation is rethrown rather than reported, so nothing
            // would ever say so. The write is one small string.
            withContext(NonCancellable) {
                writeExport(json) { file.writeString(it) }
            }
        }
    }

    val picker = rememberFilePickerLauncher(
        // Every file, not only *.json. The extension resolves to an
        // application/json filter, and a file that reached the device
        // through Drive, a mail client or a messaging app frequently
        // arrives as application/octet-stream — which that filter greys
        // out, leaving the reader looking at their own export unable to
        // select it and nothing on screen saying why. decodeFavourites
        // already refuses anything that is not an export, so the filter
        // adds no safety, only a way to fail.
        type = FileKitType.File(),
    ) { file ->
        val slug = importTarget
        importTarget = null
        if (file == null) return@rememberFilePickerLauncher
        // Undispatched and NonCancellable, for the reason the export's
        // write is: the reader can leave the tab the instant the picker
        // closes, which cancels this scope, and a file they picked must
        // not vanish because of it. The hand-off sits inside the
        // NonCancellable block, because withContext rethrows a
        // cancellation on the way out and would drop it there. What it
        // hands to lives in the producer's persisted flows, which
        // outlive this composition.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                val text = readImport { file.readString() }
                deliverPickedFile(slug, text, currentOnFileImported, stash)
            }
        }
    }

    // Neither is offered before the producer has said which list is on
    // show: an export would not know what to call its file, and an
    // import would not know where it lands.
    if (list == null) return FavouritesMenuActions(onExportClick = null, onImportClick = null)
    return FavouritesMenuActions(
        onExportClick = onExportJson?.let { encode ->
            {
                pendingExport = encode()
                saver.launch(
                    // Named for the list, so an export of each can sit
                    // side by side without one overwriting the other.
                    suggestedName = list.slug,
                    defaultExtension = EXPORT_FILE_EXTENSION,
                )
            }
        },
        onImportClick = onFileImported?.let {
            {
                importTarget = list.slug
                picker.launch()
            }
        },
    )
}

/**
 * Hands a picked file's [text] to [handler] for the list named by
 * [targetSlug]. With no handler yet it is [stash]ed for one, never
 * dropped. A slug that names no list — nothing was recorded when Import
 * was tapped, or it named a list this version does not have — cannot be
 * delivered anywhere, and is reported rather than guessed at: the file
 * is the reader's, and the wrong list would overwrite something.
 */
internal fun deliverPickedFile(
    targetSlug: String?,
    text: String,
    handler: ((UserList, String) -> Unit)?,
    stash: (String, String) -> Unit,
    report: UserDataFailureReporter = printUserDataFailure,
) {
    val target = UserList.entries.firstOrNull { it.slug == targetSlug }
    if (target == null) {
        report("a picked file was dropped: the list it was for ($targetSlug) is not known", null)
        return
    }
    if (handler == null) {
        stash(target.slug, text)
        return
    }
    handler(target, text)
}

/**
 * A write that fails is reported and otherwise invisible: the reader
 * chose where the file goes and the app has nothing left to say about
 * it, and there is no message surface on this screen to say it on.
 *
 * [write] rather than a `PlatformFile` so that the policy this function
 * exists for — swallow, report, never throw — can be tested by handing
 * it a write that fails. A file dialog cannot be driven from the host
 * test harness, so taking the file itself would leave the `catch` here
 * executed by nothing.
 */
internal suspend fun writeExport(
    json: String,
    report: UserDataFailureReporter = printUserDataFailure,
    write: suspend (String) -> Unit,
) {
    try {
        write(json)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        report("the saved words could not be exported", e)
    }
}

/**
 * A file that cannot be read is handed on as empty text, which is not a
 * file this app can read either, so it lands on the same "could not be
 * read" dialog as a malformed one. The reason it failed survives here
 * rather than in the dialog, which is the same trade every other
 * user-data failure in this app makes.
 *
 * Takes [read] rather than a `PlatformFile` for the reason [writeExport]
 * does.
 */
internal suspend fun readImport(
    report: UserDataFailureReporter = printUserDataFailure,
    read: suspend () -> String,
): String = try {
    read()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Worded to name the storage failure specifically. The producer
    // reports again when the empty text this returns fails to decode,
    // and two lines both saying "could not be read" about one tap would
    // leave a bug report unable to tell an I/O failure from a file that
    // simply was not an export.
    report("a file offered for import could not be read from storage", e)
    ""
}
