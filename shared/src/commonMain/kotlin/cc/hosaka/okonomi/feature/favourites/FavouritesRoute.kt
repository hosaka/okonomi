package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.user.UserDataFailureReporter
import cc.hosaka.okonomi.user.printUserDataFailure
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** The name the save dialog suggests, without its extension. */
private const val EXPORT_FILE_NAME = "favourites"

private const val EXPORT_FILE_EXTENSION = "json"

/** The Favourites tab's root. */
@Serializable
data object FavouritesRoute : Route {
    @Composable
    override fun Content() {
        val state by produceFavouritesScreenState()
        val transfer = rememberFavouritesTransfer(
            onExportJson = state.onExportJson,
            onFileImported = state.onFileImported,
        )
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
 * Two behaviours in here are **not** covered by any test, and cannot be:
 * a cancelled dialog writing nothing, and the name the save dialog
 * suggests. Both are settled inside a launcher callback that no host
 * test can drive, so a test for either would have to asserted against a
 * restatement of the check rather than the check — green with the real
 * one deleted. They are verified by running the app instead. What the
 * callbacks delegate to ([writeExport], [readImport]) is tested, as is
 * everything on the other side of `onFileImported`.
 */
@Composable
private fun rememberFavouritesTransfer(
    onExportJson: (() -> String)?,
    onFileImported: ((String) -> Unit)?,
): FavouritesMenuActions {
    val scope = rememberCoroutineScope()

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
        if (file == null || onFileImported == null) return@rememberFilePickerLauncher
        scope.launch {
            onFileImported(readImport { file.readString() })
        }
    }

    return FavouritesMenuActions(
        onExportClick = onExportJson?.let { encode ->
            {
                pendingExport = encode()
                saver.launch(
                    suggestedName = EXPORT_FILE_NAME,
                    defaultExtension = EXPORT_FILE_EXTENSION,
                )
            }
        },
        onImportClick = onFileImported?.let { { picker.launch() } },
    )
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
