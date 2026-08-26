package cc.hosaka.okonomi.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.search_names_toggle
import okonomi.shared.generated.resources.search_options
import org.jetbrains.compose.resources.stringResource

/**
 * The search field's overflow menu, which today holds one setting: the
 * Names toggle (Alex's ruling on where it goes).
 *
 * A menu rather than a visible switch because names are a departure from
 * the default rather than a mode the reader picks between, and the field
 * already carries the one control that belongs on it — clear, which this
 * joins rather than displaces.
 *
 * That this is hard to find is deliberate and Alex's call (2026-08-26:
 * "Leave it hidden"). A reader searching たなが — a real surname that no
 * dictionary word matches — sees an empty result with nothing hinting
 * that a setting would have answered it. That was raised and accepted;
 * do not add a hint to the empty state, an entry in Settings, or a
 * reworded "no results".
 *
 * A null [onNamesEnabledChange] renders the button disabled, the same way
 * a null callback disables anything else in this app.
 */
@Composable
internal fun SearchOverflowMenu(
    namesEnabled: Boolean,
    onNamesEnabledChange: ((Boolean) -> Unit)?,
) {
    var expanded by remember { mutableStateOf(false) }
    val toggle: (Boolean) -> Unit = { enabled ->
        onNamesEnabledChange?.invoke(enabled)
        expanded = false
    }
    Box {
        IconButton(
            enabled = onNamesEnabledChange != null,
            onClick = { expanded = true },
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(Res.string.search_options),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = {
                    Text(text = stringResource(Res.string.search_names_toggle))
                },
                trailingIcon = {
                    // The switch is driven rather than drawn, and that is
                    // not only about the tap target: a decorative switch
                    // reports no state, so nothing could tell a switch
                    // showing "off" beside names that are on. Its own
                    // `checked` is now the thing a test reads, so the two
                    // cannot drift apart.
                    Switch(
                        checked = namesEnabled,
                        onCheckedChange = toggle,
                        enabled = onNamesEnabledChange != null,
                    )
                },
                // The whole row stays a target too, so the reader does
                // not have to hit the switch itself.
                onClick = { toggle(!namesEnabled) },
            )
        }
    }
}
