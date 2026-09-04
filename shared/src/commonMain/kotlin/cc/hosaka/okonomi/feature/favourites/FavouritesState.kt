package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.SearchHit

/**
 * @property onExportJson the file contents for what is saved right now,
 * or null when there is nothing to export. A lambda rather than a
 * `String` on purpose: encoding on every emission would build a string
 * nothing renders, and null is how this codebase spells "disabled".
 * @property onFileImported the text of a file the reader picked. What
 * happens next — replace outright, warn first, or refuse — is decided
 * here rather than by the screen, because only this side knows whether
 * the list is empty.
 * @property importPrompt the dialog standing over the tab, if any.
 * Dialogs are state so that they can be driven, and tested, without a
 * file dialog anywhere near them.
 */
@Immutable
data class FavouritesState(
    val content: FavouritesContentState = FavouritesContentState.Loading,
    val onExportJson: (() -> String)? = null,
    val onFileImported: ((String) -> Unit)? = null,
    val importPrompt: FavouritesImportPrompt? = null,
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
    /**
     * Importing over a list that is not empty. Nothing is written until
     * [onConfirm], and [onCancel] leaves the list exactly as it was.
     */
    data class ConfirmOverwrite(
        val onConfirm: () -> Unit,
        val onCancel: () -> Unit,
    ) : FavouritesImportPrompt

    /** The picked file is not one this app can read. Nothing was written. */
    data class Unreadable(
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
