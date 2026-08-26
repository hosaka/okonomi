package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
import cc.hosaka.okonomi.ui.LoadMoreEffect
import cc.hosaka.okonomi.ui.PagingFooterState
import cc.hosaka.okonomi.ui.pagingFooterItem
import cc.hosaka.okonomi.ui.scrollIndicator
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingHalf
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_phrases_empty
import okonomi.shared.generated.resources.entry_phrases_error
import okonomi.shared.generated.resources.entry_phrases_word_reading
import okonomi.shared.generated.resources.entry_retry
import org.jetbrains.compose.resources.stringResource

/**
 * Example sentences for the word, three lines each: the Japanese, its
 * English translation, and a word-by-word breakdown carrying the
 * readings that make the Japanese legible.
 *
 * The searched word is deliberately not highlighted — neither jisho nor
 * takoboto does, and the breakdown already tells the reader how the
 * line is put together.
 */
@Composable
fun PhrasesTab(
    entry: EntryDetail,
    contentPadding: PaddingValues,
    loadEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // See KanjiTab: false means the pager is only passing through on its
    // way to another tab, and this one must not query for the trip — and
    // see there too for why this parameter carries no default.
    val state = if (loadEnabled) {
        producePhrasesTabState(entryId = entry.entryId).value
    } else {
        PhrasesTabState()
    }
    PhrasesTabContent(
        state = state,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

/**
 * Internal rather than private so a UI test can drive each branch directly:
 * [PhrasesTab] resolves its state off the shared dictionary, which a host test
 * has no database for.
 */
@Composable
internal fun PhrasesTabContent(
    state: PhrasesTabState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (val content = state.content) {
        PhrasesTabContentState.Loading -> CenteredBox(
            modifier = modifier,
            contentPadding = contentPadding,
        ) {
            CircularProgressIndicator()
        }

        is PhrasesTabContentState.Error -> CenteredMessage(
            text = stringResource(Res.string.entry_phrases_error),
            modifier = modifier,
            contentPadding = contentPadding,
            action = content.onRetry?.let { retry ->
                {
                    TextButton(onClick = retry) {
                        Text(text = stringResource(Res.string.entry_retry))
                    }
                }
            },
        )

        PhrasesTabContentState.Empty -> CenteredMessage(
            text = stringResource(Res.string.entry_phrases_empty),
            modifier = modifier,
            contentPadding = contentPadding,
        )

        is PhrasesTabContentState.Ready -> SentenceList(
            sentences = content.sentences,
            tappableWords = content.tappableWords,
            onShowMore = content.onShowMore,
            footer = content.footer,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

@Composable
private fun SentenceList(
    sentences: List<ExampleSentence>,
    tappableWords: Set<BreakdownWord>,
    onShowMore: (() -> Unit)?,
    footer: PagingFooterState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // The template itself, not a formatted result: the substitution is
    // [breakdownWordLabel]'s, so that the rule for when a reading is
    // shown at all can be tested without rendering. Read once for the
    // whole list rather than per row: a resource lookup inside an item
    // is a lookup for every row the reader flicks past.
    val readingFormat = stringResource(Res.string.entry_phrases_word_reading)
    LoadMoreEffect(listState = listState, onLoadMore = onShowMore)
    // A sentence is something a learner copies into a note or a search box.
    SelectionContainer {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                // The tab's content padding keeps the indicator clear of
                // the floating tab bar the last rows scroll under.
                .scrollIndicator(listState, contentPadding),
            state = listState,
            contentPadding = contentPadding,
        ) {
            itemsIndexed(
                items = sentences,
                key = { _, sentence -> sentence.id },
            ) { index, sentence ->
                // The rule goes above the block rather than below it, so
                // the last sentence does not end on a dangling line.
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier
                            .padding(horizontal = Dimens.contentPadding),
                    )
                }
                SentenceBlock(
                    sentence = sentence,
                    tappableWords = tappableWords,
                    readingFormat = readingFormat,
                )
            }
            pagingFooterItem(footer)
        }
    }
}

@Composable
private fun SentenceBlock(
    sentence: ExampleSentence,
    tappableWords: Set<BreakdownWord>,
    readingFormat: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
    ) {
        Text(
            text = sentence.japanese,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = sentence.english,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sentence.words.isNotEmpty()) {
            BreakdownRow(
                words = sentence.words,
                tappableWords = tappableWords,
                readingFormat = readingFormat,
            )
        }
    }
}

/**
 * The breakdown: each word as the dictionary writes it, followed by its
 * reading, laid out as its own unit so a long sentence stays scannable.
 * A word with no reading is written in kana already and reads as
 * itself.
 *
 * One `Text` per word rather than one joined string: a 47-word sentence
 * — the corpus has them — reads as an undelimited paragraph otherwise,
 * with a reading in the middle of a line looking like the next word's.
 * It is also what makes a word a tap target of its own.
 *
 * A content word opens a search for itself, *above* this entry, so
 * system back returns the reader to the sentence and several words can
 * be looked up from one line. The search term is the word's own text,
 * which is already the dictionary form the breakdown resolved — no
 * deinflection is involved. The search rather than the linked entry
 * because the link can be wrong; see [isBreakdownWordTappable].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownRow(
    words: List<BreakdownWord>,
    tappableWords: Set<BreakdownWord>,
    readingFormat: String,
) {
    val navigation = LocalNavigationController.current
    // Up to 47 substitutions and 47 tap lambdas per row, and the row
    // recomposes whenever anything above it in the item does. Derived
    // once per sentence instead: the words, the template, the tappable
    // set and the controller are all fixed for its life.
    val rendered = remember(words, tappableWords, readingFormat, navigation) {
        words.map { word ->
            RenderedBreakdownWord(
                label = breakdownWordLabel(word, readingFormat),
                onTap = if (word in tappableWords) {
                    { navigation.navigate(SearchRoute(word.text)) }
                } else {
                    null
                },
            )
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingHalf),
        modifier = Modifier
            .padding(top = Dimens.verticalPaddingHalf),
    ) {
        rendered.forEach { word ->
            BreakdownWordText(label = word.label, onTap = word.onTap)
        }
    }
}

/**
 * One word of the breakdown as the row draws it: the text to show and,
 * for a content word, what tapping it does. A null [onTap] is an inert
 * word, following the project's "null callback is a disabled action"
 * rule.
 */
@Immutable
private data class RenderedBreakdownWord(
    val label: String,
    val onTap: (() -> Unit)?,
)

/**
 * One word of the breakdown. A tappable one is drawn in the dynamic
 * primary colour and nothing else: no underline, no background, and no
 * ripple — roughly two words in three carry the affordance, which any
 * heavier treatment would turn into a wall.
 *
 * Tap target sizes are deliberately left alone. The breakdown is due to
 * be reworked around furigana, where the tappable text is larger and
 * the English sits under it; sizing it twice is work thrown away.
 */
@Composable
private fun BreakdownWordText(label: String, onTap: (() -> Unit)?) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (onTap != null) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = if (onTap == null) {
            Modifier
        } else {
            // A null interaction source rather than a remembered one:
            // with no indication to drive there is nothing to collect
            // interactions for, and clickable allocates one lazily only
            // if something later asks.
            Modifier.clickable(
                interactionSource = null,
                indication = null,
                onClick = onTap,
            )
        },
    )
}
