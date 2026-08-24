package cc.hosaka.okonomi.feature.libraries

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.plusScreenPadding
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.variant.LibrariesVariant
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.libraries_back
import okonomi.shared.generated.resources.libraries_empty
import okonomi.shared.generated.resources.libraries_error
import okonomi.shared.generated.resources.libraries_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibrariesScreen(
    state: LibrariesState,
) {
    val scrollBehavior = ToolbarBehavior.behavior()
    val navigation = LocalNavigationController.current
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
                        text = stringResource(Res.string.libraries_title),
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
                            contentDescription = stringResource(Res.string.libraries_back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        val contentPadding = innerPadding.plusScreenPadding()
        when (val libraries = state.libraries) {
            Loadable.Loading -> Centered(
                contentPadding = contentPadding,
            ) {
                CircularProgressIndicator()
            }

            is Loadable.Ok -> LibrariesContent(
                libraries = libraries.value,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun LibrariesContent(
    libraries: Libs?,
    contentPadding: PaddingValues,
) {
    when {
        libraries == null -> Centered(
            contentPadding = contentPadding,
        ) {
            Text(
                text = stringResource(Res.string.libraries_error),
            )
        }

        libraries.libraries.isEmpty() -> Centered(
            contentPadding = contentPadding,
        ) {
            Text(
                text = stringResource(Res.string.libraries_empty),
            )
        }

        else -> LibrariesContainer(
            libraries = libraries,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = contentPadding,
            variant = LibrariesVariant.Refined,
            colors = LibraryDefaults.libraryColors(
                libraryBackgroundColor = MaterialTheme.colorScheme.surface,
            ),
        )
    }
}

@Composable
private fun Centered(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
