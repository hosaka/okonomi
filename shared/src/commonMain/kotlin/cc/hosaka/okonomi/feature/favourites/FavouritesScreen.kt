package cc.hosaka.okonomi.feature.favourites

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.search.SearchResultRow
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
import cc.hosaka.okonomi.ui.plusScreenPadding
import cc.hosaka.okonomi.ui.scrollIndicator
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.favourites_empty
import okonomi.shared.generated.resources.favourites_error
import okonomi.shared.generated.resources.favourites_import_replace_cancel
import okonomi.shared.generated.resources.favourites_import_replace_confirm
import okonomi.shared.generated.resources.favourites_import_replace_message
import okonomi.shared.generated.resources.favourites_import_replace_title
import okonomi.shared.generated.resources.favourites_import_unreadable_dismiss
import okonomi.shared.generated.resources.favourites_import_unreadable_message
import okonomi.shared.generated.resources.favourites_import_unreadable_title
import okonomi.shared.generated.resources.favourites_retry
import okonomi.shared.generated.resources.favourites_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Favourites tab: the entries the reader saved, newest first, drawn
 * as the rows a search draws.
 *
 * Same rows on purpose. A saved word is the same word it was in the
 * results list — headword with its reading over the kanji, senses under
 * it — and giving it a second presentation would only make the two
 * screens disagree about what a word looks like.
 *
 * A pure renderer, and deliberately still one now that it has an export
 * and an import on it: [onExportClick] and [onImportClick] are opaque
 * taps, and the dialogs are read out of [FavouritesState]. The file
 * dialogs themselves live in `FavouritesRoute`, so nothing here has to
 * be hosted by anything a UI test cannot build.
 */
@Composable
fun FavouritesScreen(
    state: FavouritesState,
    onExportClick: (() -> Unit)?,
    onImportClick: (() -> Unit)?,
) {
    // Hoisted so the toolbar can ask whether this list actually
    // scrolls. One saved word does not fill the screen, and a toolbar
    // that collapses over content that cannot move leaves a band of
    // empty space where it used to be.
    val listState = rememberLazyListState()
    val scrollBehavior = ToolbarBehavior.behavior(
        canScroll = { listState.canScrollForward || listState.canScrollBackward },
    )
    // The list owns the scroll, so the plain Scaffold is used and its
    // inner padding goes to the list as content padding, letting the
    // rows scroll under the collapsing toolbar.
    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeToolbar(
                // The menu rides in the title row rather than in the
                // toolbar's `actions` slot, which is the one place this
                // screen departs from Settings and Libraries.
                //
                // `actions` is pinned to the top row, so while the bar
                // is expanded the button floats in the corner with the
                // title sitting well below it, belonging to nothing. In
                // here it sits on the title's own line and rises with it
                // as the bar collapses, which is one movement instead of
                // two. The icon is not text, so the title's typography
                // shrinking on collapse does not take the icon with it.
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.favourites_title),
                            modifier = Modifier.weight(1f),
                        )
                        FavouritesOverflowMenu(
                            onExportClick = onExportClick,
                            onImportClick = onImportClick,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        val contentPadding = innerPadding.plusScreenPadding()
        when (val content = state.content) {
            FavouritesContentState.Loading -> CenteredBox(
                contentPadding = contentPadding,
            ) {
                CircularProgressIndicator()
            }

            is FavouritesContentState.Error -> CenteredMessage(
                text = stringResource(Res.string.favourites_error),
                contentPadding = contentPadding,
                action = content.onRetry?.let { retry ->
                    {
                        TextButton(onClick = retry) {
                            Text(text = stringResource(Res.string.favourites_retry))
                        }
                    }
                },
            )

            is FavouritesContentState.Ready -> if (content.hits.isEmpty()) {
                CenteredMessage(
                    text = stringResource(Res.string.favourites_empty),
                    contentPadding = contentPadding,
                )
            } else {
                FavouritesList(
                    hits = content.hits,
                    contentPadding = contentPadding,
                    listState = listState,
                )
            }
        }
    }
    FavouritesImportDialog(state.importPrompt)
}

/**
 * The app's only dialog. It exists because an import destroys what is
 * already saved and there is no undo, and because a file that cannot be
 * read has to say so somewhere: this app has no snackbar or toast, and
 * this feature is not the place to introduce one.
 */
@Composable
private fun FavouritesImportDialog(
    prompt: FavouritesImportPrompt?,
) {
    when (prompt) {
        null -> Unit

        is FavouritesImportPrompt.ConfirmOverwrite -> AlertDialog(
            onDismissRequest = prompt.onCancel,
            title = { Text(text = stringResource(Res.string.favourites_import_replace_title)) },
            text = { Text(text = stringResource(Res.string.favourites_import_replace_message)) },
            confirmButton = {
                TextButton(onClick = prompt.onConfirm) {
                    Text(text = stringResource(Res.string.favourites_import_replace_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = prompt.onCancel) {
                    Text(text = stringResource(Res.string.favourites_import_replace_cancel))
                }
            },
        )

        is FavouritesImportPrompt.Unreadable -> AlertDialog(
            onDismissRequest = prompt.onDismiss,
            title = { Text(text = stringResource(Res.string.favourites_import_unreadable_title)) },
            text = { Text(text = stringResource(Res.string.favourites_import_unreadable_message)) },
            confirmButton = {
                TextButton(onClick = prompt.onDismiss) {
                    Text(text = stringResource(Res.string.favourites_import_unreadable_dismiss))
                }
            },
        )
    }
}

@Composable
private fun FavouritesList(
    hits: List<SearchHit>,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val navigation = LocalNavigationController.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollIndicator(listState, contentPadding),
        state = listState,
        contentPadding = contentPadding,
    ) {
        items(
            items = hits,
            key = { it.entryId },
        ) { hit ->
            SearchResultRow(
                hit = hit,
                // No query behind this list, so nothing in a sense line
                // is a match to light up.
                glossTokens = emptyList(),
                onClick = {
                    navigation.navigate(EntryRoute(hit.entryId))
                },
            )
        }
    }
}
