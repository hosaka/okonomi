package cc.hosaka.okonomi.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.db.NameHit
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.feature.home.navigation.LocalHomeReselect
import cc.hosaka.okonomi.feature.home.navigation.resolveHomeReselect
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CommonWordChip
import cc.hosaka.okonomi.ui.LoadMoreEffect
import cc.hosaka.okonomi.ui.PagingFooterState
import cc.hosaka.okonomi.ui.SearchTextField
import cc.hosaka.okonomi.ui.TagChip
import cc.hosaka.okonomi.ui.furigana.FuriganaText
import cc.hosaka.okonomi.ui.pagingFooterItem
import cc.hosaka.okonomi.ui.scrollIndicator
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.atJapaneseReadingSize
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_back
import okonomi.shared.generated.resources.name_type_fem
import okonomi.shared.generated.resources.name_type_given
import okonomi.shared.generated.resources.name_type_masc
import okonomi.shared.generated.resources.name_type_surname
import okonomi.shared.generated.resources.search_error
import okonomi.shared.generated.resources.search_no_results
import okonomi.shared.generated.resources.search_placeholder
import okonomi.shared.generated.resources.search_results_fallback
import org.jetbrains.compose.resources.stringResource

/**
 * What a deinflected hit is prefixed with: the row's title is the
 * dictionary form, and this points back at what was typed.
 */
private const val TRACE_MARKER = "‹ "

/** Separates the title from the breadcrumb and from the common chip. */
private val ROW_GAP = 8.dp

@Composable
fun SearchScreen(
    state: SearchState,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.ime),
                ),
        ) {
            val focusRequester = remember { FocusRequester() }
            SearchFieldFocusEffect(focusRequester)
            SearchField(
                state = state,
                focusRequester = focusRequester,
            )
            SearchResultsContent(
                state = state,
            )
        }
    }
}

/**
 * The query field, with a back control in front of it when this search
 * is not its section's root, and the overflow menu at its trailing edge
 * in both cases.
 *
 * What the root case still does not get is the Row and the leading space
 * a back control would need — that is the part the pushed case adds and
 * the tab's own search does without. The menu is not part of that split:
 * the Names toggle belongs to searching, not to how this screen was
 * reached, so both branches carry it.
 */
@Composable
private fun SearchField(
    state: SearchState,
    focusRequester: FocusRequester,
) {
    val onBack = state.onBack
    val overflow: @Composable RowScope.() -> Unit = {
        SearchOverflowMenu(
            namesEnabled = state.namesEnabled,
            onNamesEnabledChange = state.onNamesEnabledChange,
        )
    }
    if (onBack == null) {
        SearchTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.verticalPaddingHalf),
            text = state.query,
            placeholder = stringResource(Res.string.search_placeholder),
            onTextChange = state.onQueryChange,
            onClear = state.onClear,
            focusRequester = focusRequester,
            trailing = overflow,
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.verticalPaddingHalf),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.entry_back),
                )
            }
            SearchTextField(
                modifier = Modifier
                    .weight(1f),
                text = state.query,
                placeholder = stringResource(Res.string.search_placeholder),
                onTextChange = state.onQueryChange,
                onClear = state.onClear,
                focusRequester = focusRequester,
                trailing = overflow,
            )
        }
    }
}

/**
 * Focuses the search field and raises the IME when the Search tab is
 * reselected while already active. The counter decision lives in
 * [resolveHomeReselect]; the handled value is kept in plain composition
 * memory, so every (re)entry to composition resyncs quietly and only
 * increments observed while composed trigger focus.
 */
@Composable
private fun SearchFieldFocusEffect(
    focusRequester: FocusRequester,
) {
    val reselect = LocalHomeReselect.current
    val keyboard = LocalSoftwareKeyboardController.current
    var handledReselect by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(reselect) {
        val decision = resolveHomeReselect(current = reselect, handled = handledReselect)
        handledReselect = decision.handled
        if (decision.focus) {
            // A reselect can land in the same frame the screen composes,
            // before the requester is attached to the field; focusing is
            // a convenience, never worth taking the screen down for.
            val focused = runCatching { focusRequester.requestFocus() }.isSuccess
            if (focused) {
                keyboard?.show()
            }
        }
    }
}

@Composable
private fun SearchResultsContent(
    state: SearchState,
) {
    when (val results = state.results) {
        SearchResultsState.Idle -> Unit

        is SearchResultsState.Searching -> CenteredBox {
            CircularProgressIndicator()
        }

        is SearchResultsState.Error -> CenteredBox {
            Text(
                text = stringResource(Res.string.search_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is SearchResultsState.Results -> {
            // While the newer query is in flight the previous hits stay
            // visible; a spinner only when there is nothing to show.
            val refining = results.query != state.query
            when {
                // Names count as results: a reading no word uses can
                // still be a name, and with the toggle on that is a list
                // rather than an empty state.
                results.hits.isNotEmpty() || results.names.isNotEmpty() -> SearchResultsList(
                    hits = results.hits,
                    names = results.names,
                    isFallback = results.isFallback,
                    resultsQuery = results.query,
                    glossTokens = results.glossTokens,
                    onShowMore = results.onShowMore,
                    footer = results.footer,
                )

                refining -> CenteredBox {
                    CircularProgressIndicator()
                }

                else -> CenteredBox {
                    Text(
                        text = stringResource(Res.string.search_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    hits: List<SearchHit>,
    names: List<NameHit>,
    isFallback: Boolean,
    resultsQuery: String,
    glossTokens: List<String>,
    onShowMore: (() -> Unit)?,
    footer: PagingFooterState,
) {
    val navigation = LocalNavigationController.current
    val listState = rememberLazyListState()
    // A new query's results start reading from the top again.
    LaunchedEffect(resultsQuery) {
        listState.scrollToItem(0)
    }
    LoadMoreEffect(listState = listState, onLoadMore = onShowMore)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .scrollIndicator(listState),
        state = listState,
    ) {
        if (isFallback) {
            item(key = "fallback-note") {
                Text(
                    text = stringResource(Res.string.search_results_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(
                            horizontal = Dimens.contentPadding,
                            vertical = Dimens.verticalPaddingHalf,
                        ),
                )
            }
        }
        items(
            items = hits,
            key = { it.entryId },
        ) { hit ->
            SearchResultRow(
                hit = hit,
                glossTokens = glossTokens,
                onClick = {
                    navigation.navigate(EntryRoute(hit.entryId))
                },
            )
        }
        // Below every word, in the same list: one scroll and one pager
        // serve both, and a word result can never be pushed off the top
        // by a name however many of them match.
        items(
            items = names,
            key = { it.key },
        ) { name ->
            NameResultRow(name = name)
        }
        pagingFooterItem(footer)
    }
}

/**
 * Sally's row contract, as furigana: the written form with its matched
 * reading over the kanji rather than spelled out beside it, the match
 * in the dynamic primary colour, and a muted `‹ rule, rule` breadcrumb
 * beside the title for a deinflected hit. What is highlighted, and how
 * finely, comes from the pure [titleFurigana].
 *
 * Internal rather than private because the Favourites tab draws the
 * same row. It stays here, in the file that owns the row contract,
 * rather than moving to a shared package: a saved word IS a search
 * result row, and two definitions of one row would drift.
 *
 * The headword is set at [atJapaneseReadingSize], the same size the
 * Phrases tab reads its sentences at, and the two are meant to stay
 * equal — that is why the size lives in one place named for the
 * language rather than for either screen. The English below it does
 * not move: the sizes say which of the two lines is the word and which
 * explains it, and enlarging both would say neither.
 *
 * A long headword at that size can take the whole row, and the order it
 * takes it in is deliberate. `Row` measures its unweighted children
 * first and in order, so the title claims what it needs, the chip takes
 * what is left, and the breadcrumb — the only weighted child, and the
 * only one that is an explanation rather than a fact — gives way first.
 * A title with nowhere left to go wraps rather than clipping, so the
 * word itself is never the thing that is lost. That was already the
 * rule; the larger size only reaches it sooner.
 */
@Composable
internal fun SearchResultRow(
    hit: SearchHit,
    glossTokens: List<String>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The title takes no weight and the breadcrumb takes it all:
            // a Row measures its unweighted children first, so the word
            // claims the width it needs and the breadcrumb explains the
            // match in whatever is left. Weighting both split the row
            // evenly and squeezed the headword — the one thing on the
            // row that must be readable — behind an explanation of it.
            FuriganaText(
                segments = remember(hit) { titleFurigana(hit.titleSegments) },
                style = MaterialTheme.typography.titleMedium.atJapaneseReadingSize(),
                highlightStyle = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (hit.traceLabels.isNotEmpty()) {
                // Its own text beside the title rather than a second
                // colour inside it: the title is now furigana, whose
                // runs are laid out against the reading above them, and
                // a breadcrumb is neither read nor aligned that way.
                Spacer(
                    modifier = Modifier
                        .width(ROW_GAP),
                )
                Text(
                    text = TRACE_MARKER + hit.traceLabels.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false),
                )
            }
            if (hit.isCommon) {
                Spacer(
                    modifier = Modifier
                        .width(ROW_GAP),
                )
                CommonWordChip()
            }
        }
        hit.senseLines.forEach { line ->
            Text(
                text = senseLineText(line, glossTokens),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A name row: everything JMnedict says about a name, and nothing more.
 *
 * The headword is set the way a word row's is — the written form with its
 * reading over the kanji, at the same size — so the two read as one list.
 * What is different is what a name has: no senses and no part of speech,
 * so the line below the headword is the romanisation rather than glosses,
 * and the chips say which kind of name it is.
 *
 * Deliberately not clickable (Alex's ruling). The Entry View is built out
 * of senses, forms and a kanji breakdown, none of which a name has, and a
 * screen made for one could only repeat this row back.
 */
@Composable
private fun NameResultRow(
    name: NameHit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FuriganaText(
                segments = remember(name) { nameFurigana(name.kanji, name.reading) },
                style = MaterialTheme.typography.titleMedium.atJapaneseReadingSize(),
            )
            name.types.forEach { code ->
                Spacer(
                    modifier = Modifier
                        .width(ROW_GAP),
                )
                TagChip(text = nameTypeLabel(code))
            }
        }
        Text(
            text = name.romanisation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The short label a person-name code is shown as. A code with no label of
 * its own is shown verbatim, the way the entry view shows an unknown
 * sense code: dictgen only ever writes the four, so this is the branch
 * for a source that grew a fifth rather than a case anyone will see.
 */
@Composable
private fun nameTypeLabel(code: String): String = when (code) {
    "surname" -> stringResource(Res.string.name_type_surname)
    "given" -> stringResource(Res.string.name_type_given)
    "fem" -> stringResource(Res.string.name_type_fem)
    "masc" -> stringResource(Res.string.name_type_masc)
    else -> code
}

/**
 * A sense line with the English query's matched words highlighted the
 * same way the Japanese title match is. Japanese results pass no
 * tokens, so their sense lines stay plain. The text and offsets come
 * from the pure [senseLine].
 */
@Composable
private fun senseLineText(line: String, glossTokens: List<String>): AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    return remember(line, glossTokens, highlightColor) {
        val rendered = senseLine(line, glossTokens)
        buildAnnotatedString {
            append(rendered.text)
            rendered.highlights.forEach { range ->
                addStyle(
                    style = SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Medium,
                    ),
                    start = range.first,
                    end = range.last + 1,
                )
            }
        }
    }
}
