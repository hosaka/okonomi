package cc.hosaka.okonomi.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.ScaffoldColumn
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.appearance_back
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
    }
}

/**
 * The whole row toggles, and the [Switch] takes no input of its own, so a
 * screen reader finds one node carrying both the label and the state.
 */
@Composable
private fun SwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
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
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = Dimens.horizontalPadding),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = onCheckedChange != null,
        )
    }
}
