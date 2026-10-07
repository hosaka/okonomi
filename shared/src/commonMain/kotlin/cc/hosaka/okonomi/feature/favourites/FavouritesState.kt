package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.anki.AnkiSendResult
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.user.UserList

/**
 * @property list which list the tab is showing. The rows, the title,
 * the empty state and what the overflow menu offers all follow it. Null
 * only on the seeded first frame, before the producer has said which
 * list is picked: a default of Favourites there would be a guess, and on
 * a tab coming back to History it would be wrong for a frame — long
 * enough to flash the wrong title and to read as a switch of lists.
 * @property onSelectList switches the tab to another list, or null
 * while the tab is not ready to switch (the seeded first frame).
 * @property onExportJson the file contents for [list] right now — every
 * id in it, in its order — or null when there is nothing to export. A
 * lambda rather than a `String` on purpose: encoding on every emission
 * would build a string nothing renders, and null is how this codebase
 * spells "disabled".
 * @property onFileImported the list the reader asked to import into and
 * the text of the file they picked for it. The list is passed in rather
 * than read from [list] because the file dialog is another activity:
 * by the time it answers, this state may have been rebuilt on another
 * list, and the file belongs to the one Import was tapped on. What
 * happens next — replace outright, warn first, or refuse — is decided
 * here rather than by the screen, because only this side can ask the
 * store whether that list is empty.
 * @property importPrompt the dialog standing over the tab, if any.
 * Dialogs are state so that they can be driven, and tested, without a
 * file dialog anywhere near them.
 * @property onClearList asks to empty [list], which raises
 * [clearPrompt] rather than clearing anything. Null when the list is
 * empty or has not loaded.
 * @property clearPrompt the Clear list confirmation, if one is standing.
 * @property onRemoveEntry takes one row out of [list] and out of no
 * other list. Null only on the seeded first frame.
 * @property showSendToAnki whether the menu offers "Send to AnkiDroid"
 * at all: only on Android, and only on Favourites.
 * @property onSendToAnki sends every row on show to AnkiDroid. Null
 * while there is nothing to send, while a send is already under way,
 * and while any dialog stands.
 * @property onAnkiPermissionResult non-null while a send waits on the
 * reader granting AnkiDroid access: the route asks for the permission
 * and hands the answer here.
 * @property ankiPrompt how the last send ended, until it is dismissed.
 */
@Immutable
data class FavouritesState(
    val content: FavouritesContentState = FavouritesContentState.Loading,
    val list: UserList? = null,
    val onSelectList: ((UserList) -> Unit)? = null,
    val onExportJson: (() -> String)? = null,
    val onFileImported: ((UserList, String) -> Unit)? = null,
    val importPrompt: FavouritesImportPrompt? = null,
    val onClearList: (() -> Unit)? = null,
    val clearPrompt: FavouritesClearPrompt? = null,
    val onRemoveEntry: ((Long) -> Unit)? = null,
    val showSendToAnki: Boolean = false,
    val onSendToAnki: (() -> Unit)? = null,
    val onAnkiPermissionResult: ((Boolean) -> Unit)? = null,
    val ankiPrompt: FavouritesAnkiPrompt? = null,
)

/**
 * How a send to AnkiDroid ended. Every outcome is a dialog, success
 * included: unlike an import, a send changes nothing on this screen, so
 * without one the reader would have no way to tell it happened.
 */
@Immutable
data class FavouritesAnkiPrompt(
    val result: AnkiSendResult,
    val onDismiss: () -> Unit,
)

/**
 * "Clear [list]?" — nothing is written until [onConfirm], and [onCancel]
 * writes nothing at all.
 */
@Immutable
data class FavouritesClearPrompt(
    val list: UserList,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The two things an import has to say to the reader.
 *
 * There is no third for success: the tab is the list, so a landed import
 * shows itself. There is no snackbar or toast anywhere in this app, and
 * this feature is not the place to introduce one.
 */
@Immutable
sealed interface FavouritesImportPrompt {
    /** The list the file was offered to, which both dialogs name. */
    val list: UserList

    /**
     * Importing over a list that is not empty. Nothing is written until
     * [onConfirm], and [onCancel] leaves the list exactly as it was.
     */
    data class ConfirmOverwrite(
        override val list: UserList,
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit,
    ) : FavouritesImportPrompt

    /** The picked file is not one this app can read. Nothing was written to [list]. */
    data class Unreadable(
        override val list: UserList,
        val onDismiss: () -> Unit,
    ) : FavouritesImportPrompt
}

/**
 * The body of the Favourites tab.
 *
 * [Ready] with an empty list is the empty state, and it is deliberately
 * not a case of its own. Two ways to reach it — nothing saved, and
 * everything saved having been deleted from the dictionary upstream —
 * read the same to the reader, and neither is actionable beyond saving
 * something.
 *
 * [Error] is only ever a dictionary failure: the saved ids are read from
 * the user database, which reports an unreadable store as an empty list
 * rather than an error (see `FavouritesStore`). A null [Error.onRetry]
 * means retrying is not offered, following the project's "null callback
 * is a disabled action" rule.
 */
@Immutable
sealed interface FavouritesContentState {
    data object Loading : FavouritesContentState

    data class Ready(
        val hits: List<SearchHit>,
    ) : FavouritesContentState

    data class Error(
        val onRetry: (() -> Unit)? = null,
    ) : FavouritesContentState
}
