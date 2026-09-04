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
import okonomi.shared.generated.resources.favourites_export
import okonomi.shared.generated.resources.favourites_import
import okonomi.shared.generated.resources.favourites_options
import org.jetbrains.compose.resources.stringResource

/**
 * The Favourites toolbar's overflow menu: the way a saved list gets out
 * of the app and back in.
 *
 * Built like `SearchOverflowMenu`, the app's only other one, down to a
 * null callback rendering its item disabled. Text-only items because
 * only `material-icons-core` is a dependency and it carries no upload,
 * download or file icon; adding an icon set for two menu rows would be
 * a large dependency for a small decoration.
 *
 * The button itself stays enabled while either action is available.
 * Once the producer has emitted, importing always is — an empty list is
 * a perfectly good thing to import into — so in practice it is "Export
 * list" that renders disabled, with nothing saved or the list still
 * loading. The one moment both are unavailable, and the button with
 * them, is the seeded first frame: `FavouritesState()` carries neither
 * callback until the producer replaces it.
 */
@Composable
internal fun FavouritesOverflowMenu(
    onExportClick: (() -> Unit)?,
    onImportClick: (() -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            enabled = onExportClick != null || onImportClick != null,
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
            DropdownMenuItem(
                text = { Text(text = stringResource(Res.string.favourites_import)) },
                enabled = onImportClick != null,
                onClick = {
                    expanded = false
                    onImportClick?.invoke()
                },
            )
        }
    }
}
