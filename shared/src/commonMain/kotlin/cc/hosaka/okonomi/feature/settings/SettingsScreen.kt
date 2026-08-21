package cc.hosaka.okonomi.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.ui.plusScreenPadding
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import okonomi.shared.generated.resources.Res
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
    // padding, letting the rows scroll under the collapsing toolbar.
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
        when (val libraries = state.libraries) {
            Loadable.Loading -> Unit
            is Loadable.Ok -> SettingsLibraries(
                libraries = libraries.value,
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding),
                contentPadding = innerPadding.plusScreenPadding(),
            )
        }
    }
}

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
