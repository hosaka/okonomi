package cc.hosaka.okonomi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo
import cc.hosaka.okonomi.feature.libraries.LibrariesRoute
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.ScaffoldColumn
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.libraries_title
import okonomi.shared.generated.resources.settings_credit_open_project
import okonomi.shared.generated.resources.settings_credits_edrdg_licence_link
import okonomi.shared.generated.resources.settings_credits_open_licence
import okonomi.shared.generated.resources.settings_credits_title
import okonomi.shared.generated.resources.settings_dictionary_label
import okonomi.shared.generated.resources.settings_dictionary_value
import okonomi.shared.generated.resources.settings_libraries_open
import okonomi.shared.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    state: SettingsState,
) {
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
        val dictionary = (state.dictionary as? Loadable.Ok)?.value
        if (dictionary != null) {
            DictionaryRow(
                dictionary = dictionary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.horizontalPadding),
            )
        }
        CreditsSection(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.verticalPadding),
        )
        LibrariesRow(
            onClick = {
                navigation.navigate(LibrariesRoute)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.verticalPaddingHalf),
        )
    }
}

@Composable
private fun DictionaryRow(
    dictionary: DictionaryInfo,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.settings_dictionary_label),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(
                Res.string.settings_dictionary_value,
                dictionary.jmdictDate,
                dictionary.entryCount.grouped(),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Long.grouped(): String = toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()

/**
 * The attribution section: the EDRDG conformance statement with its
 * licence link, followed by one row per [CreditEntry]. Rendered from the
 * credits manifest, never hardcoded prose.
 */
@Composable
private fun CreditsSection(
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier,
    ) {
        Text(
            text = stringResource(Res.string.settings_credits_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = Dimens.horizontalPadding)
                .semantics {
                    heading()
                },
        )
        Text(
            text = stringResource(edrdgStatement),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(horizontal = Dimens.horizontalPadding)
                .padding(top = Dimens.topPaddingCaption),
        )
        creditEntries.forEach { entry ->
            CreditRow(
                entry = entry,
                onClick = {
                    uriHandler.openSafely(entry.licenceUrl)
                },
                modifier = Modifier
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CreditRow(
    entry: CreditEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(
                onClickLabel = stringResource(Res.string.settings_credit_open_project),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = Dimens.horizontalPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f),
            )
            Text(
                text = entry.licence,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(entry.usage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val detail = entry.detail
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibrariesRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(
                onClickLabel = stringResource(Res.string.settings_libraries_open),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(
                horizontal = Dimens.horizontalPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.libraries_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
        )
    }
}

/**
 * Opens the URL in the browser. The failure is swallowed deliberately:
 * a device without a browser must never crash the screen, and the app
 * has no logging or snackbar infrastructure yet to surface it, so the
 * tap simply has no effect.
 */
private fun UriHandler.openSafely(uri: String) {
    try {
        openUri(uri)
    } catch (e: Exception) {
        // Deliberately swallowed, see above.
    }
}
