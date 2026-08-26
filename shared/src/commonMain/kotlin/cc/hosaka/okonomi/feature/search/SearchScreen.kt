package cc.hosaka.okonomi.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import cc.hosaka.okonomi.ui.furigana.FuriganaText
import cc.hosaka.okonomi.ui.pagingFooterItem
import cc.hosaka.okonomi.ui.scrollIndicator
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_back
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
 * is not its section's root.
 *
 * The root case composes exactly the field it always did — no Row
 * around it, no leading space held for a control that is not there — so
 * the Search tab is untouched by this.
 */
@Composable
private fun SearchField(
    state: SearchState,
    focusRequester: FocusRequester,
) {
    val onBack = state.onBack
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
                results.hits.isNotEmpty() -> SearchResultsList(
                    hits = results.hits,
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
        pagingFooterItem(footer)
    }
}

/**
 * Sally's row contract, as furigana: the written form with its matched
 * reading over the kanji rather than spelled out beside it, the match
 * in the dynamic primary colour, and a muted `‹ rule, rule` breadcrumb
 * beside the title for a deinflected hit. What is highlighted, and how
 * finely, comes from the pure [titleFurigana].
 */
@Composable
private fun SearchResultRow(
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
                style = MaterialTheme.typography.titleMedium,
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
