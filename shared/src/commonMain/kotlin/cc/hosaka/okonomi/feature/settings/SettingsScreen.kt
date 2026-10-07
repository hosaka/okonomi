package cc.hosaka.okonomi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.ScaffoldColumn
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.settings_about_description
import okonomi.shared.generated.resources.settings_about_title
import okonomi.shared.generated.resources.settings_appearance_description
import okonomi.shared.generated.resources.settings_appearance_title
import okonomi.shared.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/** The Settings root: one row per category, each pushing its own screen. */
@Composable
fun SettingsScreen() {
    val scrollBehavior = ToolbarBehavior.behavior()
    val navigation = LocalNavigationController.current
    ScaffoldColumn(
        topAppBarScrollBehavior = scrollBehavior,
        topBar = {
            LargeToolbar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        CategoryRow(
            icon = PaletteIcon,
            title = stringResource(Res.string.settings_appearance_title),
            description = stringResource(Res.string.settings_appearance_description),
            onClick = {
                navigation.navigate(AppearanceRoute)
            },
            modifier = Modifier
                .fillMaxWidth(),
        )
        CategoryRow(
            icon = Icons.Outlined.Info,
            title = stringResource(Res.string.settings_about_title),
            description = stringResource(Res.string.settings_about_description),
            onClick = {
                navigation.navigate(AboutRoute)
            },
            modifier = Modifier
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = Dimens.horizontalPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.horizontalPadding),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Material's Outlined "Palette" icon. Like the Tune icon in
 * `SearchFiltersButton`, it lives in `material-icons-extended`, which this
 * project does not depend on, so its path is copied here (Apache 2.0, as
 * the icon set itself).
 */
private val PaletteIcon: ImageVector by lazy {
    materialIcon(name = "Outlined.Palette") {
        materialPath {
            moveTo(12.0f, 22.0f)
            curveTo(6.49f, 22.0f, 2.0f, 17.51f, 2.0f, 12.0f)
            reflectiveCurveTo(6.49f, 2.0f, 12.0f, 2.0f)
            reflectiveCurveToRelative(10.0f, 4.04f, 10.0f, 9.0f)
            curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
            horizontalLineToRelative(-1.77f)
            curveToRelative(-0.28f, 0.0f, -0.5f, 0.22f, -0.5f, 0.5f)
            curveToRelative(0.0f, 0.12f, 0.05f, 0.23f, 0.13f, 0.33f)
            curveToRelative(0.41f, 0.47f, 0.64f, 1.06f, 0.64f, 1.67f)
            curveToRelative(0.0f, 1.38f, -1.12f, 2.5f, -2.5f, 2.5f)
            close()
            moveTo(12.0f, 4.0f)
            curveToRelative(-4.41f, 0.0f, -8.0f, 3.59f, -8.0f, 8.0f)
            reflectiveCurveToRelative(3.59f, 8.0f, 8.0f, 8.0f)
            curveToRelative(0.28f, 0.0f, 0.5f, -0.22f, 0.5f, -0.5f)
            curveToRelative(0.0f, -0.16f, -0.08f, -0.28f, -0.14f, -0.35f)
            curveToRelative(-0.41f, -0.46f, -0.63f, -1.05f, -0.63f, -1.65f)
            curveToRelative(0.0f, -1.38f, 1.12f, -2.5f, 2.5f, -2.5f)
            horizontalLineTo(16.0f)
            curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
            curveToRelative(0.0f, -3.86f, -3.59f, -7.0f, -8.0f, -7.0f)
            close()
            paletteDot(6.5f, 11.5f)
            paletteDot(9.5f, 7.5f)
            paletteDot(14.5f, 7.5f)
            paletteDot(17.5f, 11.5f)
        }
    }
}

/** A filled dot of radius 1.5 centred on ([x], [y]), as two half arcs. */
private fun PathBuilder.paletteDot(x: Float, y: Float) {
    moveTo(x - 1.5f, y)
    arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
    arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
    close()
}
