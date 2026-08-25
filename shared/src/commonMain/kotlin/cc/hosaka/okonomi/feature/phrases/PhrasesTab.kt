package cc.hosaka.okonomi.feature.phrases

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
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
    modifier: Modifier = Modifier,
) {
    val state = producePhrasesTabState(entryId = entry.entryId)
    PhrasesTabContent(
        state = state.value,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun PhrasesTabContent(
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
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

@Composable
private fun SentenceList(
    sentences: List<ExampleSentence>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // A sentence is something a learner copies into a note or a search box.
    SelectionContainer {
        LazyColumn(
            modifier = modifier
                .fillMaxSize(),
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
                SentenceBlock(sentence)
            }
        }
    }
}

@Composable
private fun SentenceBlock(sentence: ExampleSentence) {
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
            BreakdownRow(sentence.words)
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
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownRow(words: List<BreakdownWord>) {
    // The template itself, not a formatted result: the substitution is
    // [breakdownWordLabel]'s, so that the rule for when a reading is
    // shown at all can be tested without rendering.
    val readingFormat = stringResource(Res.string.entry_phrases_word_reading)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingHalf),
        modifier = Modifier
            .padding(top = Dimens.verticalPaddingHalf),
    ) {
        words.forEach { word ->
            Text(
                text = breakdownWordLabel(word, readingFormat),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
