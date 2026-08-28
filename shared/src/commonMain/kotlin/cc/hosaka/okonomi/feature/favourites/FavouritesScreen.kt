package cc.hosaka.okonomi.feature.favourites

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 */
@Composable
fun FavouritesScreen(
    state: FavouritesState,
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
                title = {
                    Text(
                        text = stringResource(Res.string.favourites_title),
                    )
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
