package cc.hosaka.okonomi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.lang.AppLanguage
import cc.hosaka.okonomi.ui.OverflowMenu
import cc.hosaka.okonomi.ui.ScaffoldColumn
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.appearance_back
import okonomi.shared.generated.resources.appearance_language
import okonomi.shared.generated.resources.appearance_per_kanji_furigana
import okonomi.shared.generated.resources.appearance_per_kanji_furigana_hidden
import okonomi.shared.generated.resources.appearance_show_hints
import okonomi.shared.generated.resources.appearance_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun AppearanceScreen(
    state: AppearanceState,
) {
    val scrollBehavior = ToolbarBehavior.behavior()
    val navigation = LocalNavigationController.current
    ScaffoldColumn(
        topAppBarScrollBehavior = scrollBehavior,
        topBar = {
            LargeToolbar(
                title = {
                    Text(
                        text = stringResource(Res.string.appearance_title),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            navigation.pop()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.appearance_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        // Nothing until the store answers: a switch drawn on a guess
        // would flip in front of a reader whose stored value differs.
        val appLanguage = state.appLanguage
        val onAppLanguageChange = state.onAppLanguageChange
        if (appLanguage != null && onAppLanguageChange != null) {
            LanguageRow(
                selected = appLanguage,
                onSelect = onAppLanguageChange,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
        val showHints = state.showHints
        if (showHints != null) {
            SwitchRow(
                text = stringResource(Res.string.appearance_show_hints),
                checked = showHints,
                onCheckedChange = state.onShowHintsChange,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
        val perKanjiFurigana = state.perKanjiFurigana
        if (perKanjiFurigana != null) {
            SwitchRow(
                text = stringResource(Res.string.appearance_per_kanji_furigana),
                checked = perKanjiFurigana,
                onCheckedChange = state.onPerKanjiFuriganaChange,
                supportingText = if (state.furiganaHidden) {
                    stringResource(Res.string.appearance_per_kanji_furigana_hidden)
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * "Language", with the current choice under it. The whole row is one
 * dropdown, opening the app's one menu panel with every [AppLanguage] in
 * it, each by its own name, and the current one checked; the Favourites
 * list picker looks the same.
 *
 * To a screen reader the row is a dropdown that says whether it is open,
 * through the expand and collapse actions a service reads that state
 * from, and the menu is a single choice: each option a radio button,
 * the current one selected. The check is only the sighted reader's view
 * of the same thing.
 *
 * There is no disabled form. The producer hands over the choice and its
 * callback together, and the row is not drawn without both.
 */
@Composable
private fun LanguageRow(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList) { expanded = true }
                .semantics {
                    if (expanded) {
                        collapse {
                            expanded = false
                            true
                        }
                    } else {
                        expand {
                            expanded = true
                            true
                        }
                    }
                }
                .padding(
                    horizontal = Dimens.horizontalPadding,
                    vertical = Dimens.verticalPaddingHalf,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Dimens.horizontalPadding),
            ) {
                Text(
                    text = stringResource(Res.string.appearance_language),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(selected.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                // The row's role already says it opens a list.
                contentDescription = null,
            )
        }
        OverflowMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.selectableGroup(),
        ) {
            AppLanguage.entries.forEach { option ->
                val current = option == selected
                DropdownMenuItem(
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        this.selected = current
                    },
                    text = { Text(text = stringResource(option.displayName)) },
                    trailingIcon = if (current) {
                        { Icon(imageVector = Icons.Outlined.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The whole row toggles, and the [Switch] takes no input of its own, so a
 * screen reader finds one node carrying both the label and the state —
 * and [supportingText], the line under the label, when there is one.
 */
@Composable
private fun SwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    Row(
        modifier = modifier
            .toggleable(
                value = checked,
                enabled = onCheckedChange != null,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) },
            )
            .padding(
                horizontal = Dimens.horizontalPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = Dimens.horizontalPadding),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = onCheckedChange != null,
        )
    }
}
