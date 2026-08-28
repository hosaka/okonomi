package cc.hosaka.okonomi.feature.radical

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import cc.hosaka.okonomi.db.KanjiHit
import cc.hosaka.okonomi.feature.navigation.state.LoadState
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
import cc.hosaka.okonomi.ui.CharacterChip
import cc.hosaka.okonomi.ui.SearchTextField
import cc.hosaka.okonomi.ui.characterChipMinSize
import cc.hosaka.okonomi.ui.plusScreenPadding
import cc.hosaka.okonomi.ui.screenMaxWidth
import cc.hosaka.okonomi.ui.scrollIndicator
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingFourth
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.radical_back
import okonomi.shared.generated.resources.radical_empty
import okonomi.shared.generated.resources.radical_error
import okonomi.shared.generated.resources.radical_kanji_search
import okonomi.shared.generated.resources.radical_retry
import okonomi.shared.generated.resources.radical_title
import org.jetbrains.compose.resources.stringResource

/**
 * Every kanji built from one radical, as a grid of tappable characters
 * under a bar naming the radical.
 *
 * The bar is where a search field would be, and it is deliberately not
 * one: nothing can be typed into it, it carries no leading search icon,
 * no clear action and no overflow menu, and it wears the recessed tone
 * [SearchTextField] gives a field nothing can be typed into. It is a
 * heading that happens to sit in the toolbar's place, so the screen
 * reads as a continuation of the search chrome without offering a search
 * that would answer the wrong question.
 *
 * The bar and the grid share one [screenMaxWidth] column rather than
 * capping the grid alone, so on a tablet the title sits over its
 * characters instead of floating off to the side of them. Not asserted:
 * the cap only binds above 768dp, which no host test is wide enough to
 * reach, so an assertion written for it here would pass on a phone
 * whether the two agreed or not.
 */
@Composable
fun RadicalScreen(
    state: RadicalState,
) {
    val title = stringResource(Res.string.radical_title, state.radical)
    Surface(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                // What a screen reader is told has arrived. The overlay
                // that named the radical is gone by now, so without this
                // nothing on the new screen announces which radical it
                // is about.
                .semantics { paneTitle = title },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = screenMaxWidth)
                    .fillMaxSize(),
            ) {
                RadicalBar(
                    title = title,
                    onBack = state.onBack,
                )
                RadicalContent(
                    kanji = state.kanji,
                    onKanjiClick = state.onKanjiClick,
                )
            }
        }
    }
}

/**
 * The back control and the read-only bar beside it.
 *
 * This screen is always pushed — there is no radical tab — so back is
 * never absent: the navigation bar hides itself at depth greater than
 * one and iOS has no system back button, so without it the only way out
 * is the edge swipe. `RadicalState` makes it non-null for that reason,
 * initial state included, so the arrow is live from the first frame
 * rather than from the first load.
 *
 * The title is marked a heading, and the pill is merged so the heading
 * lands on the node that carries the text rather than on an empty
 * wrapper above it — an unmerged `heading()` here is announced to
 * nobody, because a screen reader never focuses a container with no
 * content of its own. Merging is safe on this bar and only on this bar:
 * the pill is a label with no leading icon, no clear and no overflow, so
 * there is nothing inside it a reader would need to reach separately.
 */
@Composable
private fun RadicalBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.verticalPaddingHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(Res.string.radical_back),
            )
        }
        SearchTextField(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { heading() },
            text = title,
            // Never shown: the bar always holds the radical's name, so
            // the empty-text branch the placeholder serves is
            // unreachable here.
            placeholder = "",
            // The three nulls are the whole point. No text change means
            // no editing and the recessed read-only tone; no clear means
            // the clear action is not drawn at all; and nothing is
            // passed for `trailing`, so there is no overflow menu.
            onTextChange = null,
            onClear = null,
            leading = {},
        )
    }
}

@Composable
private fun RadicalContent(
    kanji: LoadState<List<KanjiHit>>,
    onKanjiClick: (String) -> Unit,
) {
    when (kanji) {
        LoadState.Loading -> CenteredBox {
            CircularProgressIndicator()
        }

        is LoadState.Error -> CenteredMessage(
            text = stringResource(Res.string.radical_error),
            action = {
                TextButton(onClick = kanji.onRetry) {
                    Text(text = stringResource(Res.string.radical_retry))
                }
            },
        )

        is LoadState.Ready -> if (kanji.value.isEmpty()) {
            CenteredMessage(
                text = stringResource(Res.string.radical_empty),
            )
        } else {
            RadicalGrid(
                kanji = kanji.value,
                onKanjiClick = onKanjiClick,
            )
        }
    }
}

/**
 * The whole set at once, in the order the SQL pinned.
 *
 * A [LazyVerticalGrid] at the root of its own screen, so it recycles its
 * rows and there is nothing to page: the payload is single characters,
 * and the worst radical in the shipped data yields 1,337 of them. No
 * offset, no limit, no footer — see `kanjiContainingRadical` in kanji.sq
 * for why the read is whole.
 *
 * That length is also why the grid state is hoisted: this is the longest
 * list in the app, and [scrollIndicator] is the only thing telling a
 * reader a third of the way down 1,337 characters where they are. The
 * indicator is a draw call with no semantics node, so no host test can
 * see it; its arithmetic is `ScrollThumbTest`'s, shared with every other
 * list, and that it is attached at all is a device check.
 *
 * The columns are adaptive rather than counted, so the grid fills a
 * phone and a tablet alike with cells no smaller than a fingertip.
 */
@Composable
private fun RadicalGrid(
    kanji: List<KanjiHit>,
    onKanjiClick: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val contentPadding = PaddingValues(horizontal = Dimens.contentPadding).plusScreenPadding()
    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .scrollIndicator(gridState, contentPadding),
        state = gridState,
        columns = GridCells.Adaptive(minSize = characterChipMinSize),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
        verticalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
    ) {
        items(
            items = kanji,
            // The kanji primary key, and `SELECT DISTINCT` collapses a
            // repeated decomposition, so no two cells can share a key.
            key = { it.literal },
        ) { hit ->
            CharacterChip(
                character = hit.literal,
                // Per character, not hoisted: the label names the kanji
                // it searches for, so the spoken action hint identifies
                // its own cell in a grid of them.
                onClickLabel = stringResource(
                    Res.string.radical_kanji_search,
                    hit.literal,
                ),
                onClick = { onKanjiClick(hit.literal) },
            )
        }
    }
}
