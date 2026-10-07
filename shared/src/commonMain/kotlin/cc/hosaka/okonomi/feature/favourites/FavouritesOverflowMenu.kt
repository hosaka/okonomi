package cc.hosaka.okonomi.feature.favourites

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cc.hosaka.okonomi.ui.OverflowMenu
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.favourites_clear
import okonomi.shared.generated.resources.favourites_export
import okonomi.shared.generated.resources.favourites_import
import okonomi.shared.generated.resources.favourites_options
import okonomi.shared.generated.resources.favourites_send_to_anki
import org.jetbrains.compose.resources.stringResource

/**
 * The Favourites toolbar's overflow menu: the way a saved list gets out
 * of the app and back in, and the way any list is emptied.
 *
 * Both lists offer the same three items, each acting on the list on
 * show: Export, Import and Clear list.
 *
 * A null callback renders its item disabled. Text-only items because
 * only `material-icons-core` is a dependency and it carries no upload,
 * download or file icon; adding an icon set to decorate three short
 * labels would be a large dependency for a small decoration.
 *
 * The button itself stays enabled while any item it offers is
 * available. Once the producer has emitted, importing always is — an
 * empty list is a perfectly good thing to import into — so in practice
 * it is "Export list" and "Clear list" that render disabled, with
 * nothing in the list. The seeded first frame carries no callback at all
 * until the producer replaces it, and the button is disabled with them.
 *
 * Favourites on Android has a fourth, "Send to Anki", present only
 * when [showSendToAnki] says so: on iOS and on History there is nothing
 * to send to, so it is absent rather than disabled.
 */
@Composable
internal fun FavouritesOverflowMenu(
    onExportClick: (() -> Unit)?,
    onImportClick: (() -> Unit)?,
    onClearClick: (() -> Unit)?,
    showSendToAnki: Boolean = false,
    onSendToAnkiClick: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            enabled = onClearClick != null ||
                onExportClick != null ||
                onImportClick != null ||
                (showSendToAnki && onSendToAnkiClick != null),
            onClick = { expanded = true },
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(Res.string.favourites_options),
            )
        }
        OverflowMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(Res.string.favourites_export)) },
                enabled = onExportClick != null,
                onClick = {
                    expanded = false
                    onExportClick?.invoke()
                },
            )
            if (showSendToAnki) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(Res.string.favourites_send_to_anki)) },
                    enabled = onSendToAnkiClick != null,
                    onClick = {
                        expanded = false
                        onSendToAnkiClick?.invoke()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(text = stringResource(Res.string.favourites_import)) },
                enabled = onImportClick != null,
                onClick = {
                    expanded = false
                    onImportClick?.invoke()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(Res.string.favourites_clear)) },
                enabled = onClearClick != null,
                onClick = {
                    expanded = false
                    onClearClick?.invoke()
                },
            )
        }
    }
}
