package cc.hosaka.okonomi.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.settings_dictionary_label
import okonomi.shared.generated.resources.settings_dictionary_value
import okonomi.shared.generated.resources.settings_libraries_empty
import okonomi.shared.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    state: SettingsState,
) {
    val scrollBehavior = ToolbarBehavior.behavior()
    // LibrariesContainer owns its own LazyColumn, so the plain Scaffold
    // is used here and the inner padding goes to the list as content
    // padding, letting the rows scroll under the collapsing toolbar. The
    // dictionary row sits above the list, so the toolbar inset is
    // consumed by the column and only the remaining padding reaches the
    // list.
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        // The scaffold insets are applied to the column once, so the row
        // and the list share the same horizontal base. The dictionary row
        // then indents by Dimens.horizontalPadding (16.dp), the same inset
        // the library rows apply internally (LibraryDefaults row padding),
        // keeping both columns of text aligned.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
        ) {
            val dictionary = (state.dictionary as? Loadable.Ok)?.value
            if (dictionary != null) {
                DictionaryRow(
                    dictionary = dictionary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.horizontalPadding)
                        .padding(top = Dimens.contentPadding),
                )
            }
            when (val libraries = state.libraries) {
                Loadable.Loading -> Unit
                is Loadable.Ok -> SettingsLibraries(
                    libraries = libraries.value,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = Dimens.contentPadding,
                        bottom = innerPadding.calculateBottomPadding() + Dimens.contentPadding,
                    ),
                )
            }
        }
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

@Composable
private fun SettingsLibraries(
    libraries: Libs,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
) {
    if (libraries.libraries.isEmpty()) {
        Box(
            modifier = modifier
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.settings_libraries_empty),
            )
        }
        return
    }
    LibrariesContainer(
        libraries = libraries,
        modifier = modifier,
        contentPadding = contentPadding,
        colors = LibraryDefaults.libraryColors(
            libraryBackgroundColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
