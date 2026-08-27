package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
import cc.hosaka.okonomi.ui.ListCard
import cc.hosaka.okonomi.ui.LoadMoreEffect
import cc.hosaka.okonomi.ui.PagingFooterState
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import cc.hosaka.okonomi.ui.furigana.FuriganaText
import cc.hosaka.okonomi.ui.pagingFooterItem
import cc.hosaka.okonomi.ui.scrollIndicator
import cc.hosaka.okonomi.ui.theme.atJapaneseReadingSize
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_phrases_empty
import okonomi.shared.generated.resources.entry_phrases_error
import okonomi.shared.generated.resources.entry_retry
import org.jetbrains.compose.resources.stringResource

/**
 * Example sentences for the word, two lines each: the Japanese sentence
 * with its readings set over it, and the English translation under it.
 *
 * There is no word-by-word breakdown row any more. It existed to supply
 * readings the sentence itself did not carry, and to be somewhere to
 * tap; the readings now sit where they belong and the sentence text is
 * what a reader taps, so the row was a second copy of the line in
 * dictionary forms and nothing else.
 *
 * The searched word is deliberately not highlighted — neither jisho nor
 * takoboto does, and a reader who opened this entry knows what they
 * looked up.
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
        producePhrasesTabState(entryId = entry.entryId, headword = entry.headword).value
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
            wordBeingRead = content.wordBeingRead,
            entryPos = content.entryPos,
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
    wordBeingRead: String,
    entryPos: Map<Long, List<String>>,
    onShowMore: (() -> Unit)?,
    footer: PagingFooterState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LoadMoreEffect(listState = listState, onLoadMore = onShowMore)
    // A sentence is something a learner copies into a note or a search
    // box. What a copy now yields is unverified and platform-divergent,
    // and it is here rather than hidden: the line used to be one Text
    // and is a row of them, and the ruby is a TextView outside the
    // selection registrar on Android while it is a BasicText inside it
    // on iOS — so a copy may pick up readings and a second copy of the
    // base characters there. Needs a device, on both.
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
            items(
                items = sentences,
                key = { sentence -> sentence.id },
            ) { sentence ->
                SentenceBlock(
                    sentence = sentence,
                    tappableWords = tappableWords,
                    wordBeingRead = wordBeingRead,
                    entryPos = entryPos,
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
    wordBeingRead: String,
    entryPos: Map<Long, List<String>>,
) {
    ListCard {
        SentenceText(
            sentence = sentence,
            tappableWords = tappableWords,
            wordBeingRead = wordBeingRead,
            entryPos = entryPos,
        )
        Text(
            text = sentence.english,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The minimum height of a word on this tab, in dp.
 *
 * This is the deferred 48dp item, and it is worth being exact about
 * what it does and does not settle. Every piece of the sentence —
 * tappable or not, so the line stays aligned — is laid out in a box at
 * least this tall, so a word is 48dp high whatever the reader's font
 * scale. The line box from [atJapaneseReadingSize] is 44 **sp**: at a
 * font scale of 0.85 that is 37dp, which is why the floor is stated
 * separately and in dp.
 *
 * The **width is the word's own** and is not padded out. 話 is a 22sp
 * target across, short of 48dp, and it stays that way deliberately:
 * widening it means either moving the characters apart — the sentence
 * stops being a sentence — or letting one word's target cover the
 * characters of the next, so a tap near the boundary opens the wrong
 * search. A reader aiming at a word is aiming at characters they can
 * see, and a target that reaches past them is worse than a narrow one.
 * The height is what a thumb misses on, and that is the half fixed
 * here.
 */
private val SENTENCE_TAP_HEIGHT = 48.dp

/**
 * The sentence as Tatoeba holds it, with the readings dictgen resolved
 * set over the words they belong to, and each of those words a tap
 * target.
 *
 * One `FuriganaText` per piece inside a `FlowRow`, rather than one for
 * the whole line: a tap has to land on a word and nothing else, and a
 * range inside a single text node is not something a finger can be
 * aimed at. Japanese breaks lines between any two characters, so
 * wrapping at word boundaries costs the line nothing and spares it a
 * word split across two lines. The pieces are laid out with no spacing
 * between them, which is what keeps the sentence reading as one line of
 * text rather than as a list of words.
 *
 * Two things the split costs, neither of them yet paid for. A screen
 * reader now walks the sentence as N siblings instead of announcing it
 * as one line, and no piece says what tapping it does. And line
 * breaking has moved from the text engine to `FlowRow`, which knows
 * nothing of kinsoku, so a wrap can put 。 or 、 at the head of a line
 * where one `Text` would not have. Both want a device and Alex's eyes
 * before being designed around.
 *
 * A content word is drawn in the dynamic primary colour and nothing
 * else: no underline, no background, no ripple — the rule Alex set for
 * the breakdown row this replaced, carried over to the sentence.
 *
 * **The colour says what the word is; the tap says where it goes, and
 * they are not the same question.** On 私's own examples every 私 in
 * them is still a pronoun and still drawn as one — it simply has
 * nowhere to send the reader, who is already on its page. Drawing it in
 * the colour reserved for particles would tell them 私 is grammar,
 * which is both wrong and the opposite of why they opened the entry.
 * See [opensSearch].
 *
 * That the sentence itself can afford it was the open question, and it
 * is settled: this shipped without the colour, on the reasoning that
 * tinting two words in three of the line makes it a different and worse
 * sentence, and Alex read it on a device and asked for the colour back
 * (2026-08-26) — "they're back to just being white". A word carries the
 * colour whole, ruby and characters together, because the reading is
 * part of the word and not an annotation on a plain one. Recorded
 * rather than argued: the case against was heard and lost, and the next
 * reader should not spend the afternoon making it again.
 *
 * A tap opens a *search*, above this entry, so system back returns the
 * reader to the sentence and several words can be looked up from one
 * line. The term searched for is the word's dictionary form and never
 * the surface on screen: tapping 食べない searches 食べる. The search
 * rather than the linked entry because the link can be wrong; see
 * [isBreakdownWordTappable].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentenceText(
    sentence: ExampleSentence,
    tappableWords: Set<BreakdownWord>,
    wordBeingRead: String,
    entryPos: Map<Long, List<String>>,
) {
    val navigation = LocalNavigationController.current
    // A whole sentence's alignment, conjugation, transfers and tap
    // lambdas, and the item recomposes whenever anything else in it
    // does. Derived once per sentence instead: the sentence, the
    // tappable set, the codes and the controller are all fixed for its
    // life.
    val pieces = remember(sentence, tappableWords, wordBeingRead, entryPos, navigation) {
        sentencePieces(sentence, entryPos).map { piece ->
            RenderedPiece(
                segments = piece.segments,
                onTap = piece.word
                    ?.takeIf { opensSearch(it, tappableWords, wordBeingRead) }
                    ?.let { word -> { navigation.navigate(SearchRoute(word.text)) } },
            )
        }
    }
    val style = MaterialTheme.typography.bodyLarge.atJapaneseReadingSize()
    val contentWordColor = MaterialTheme.colorScheme.primary
    FlowRow {
        pieces.forEach { piece ->
            Box(
                // The floor is applied to every piece and not only to
                // the tappable ones, so that a row of them still shares
                // one baseline; a taller box around 話 alone would drop
                // it below the words beside it.
                modifier = Modifier
                    .heightIn(min = SENTENCE_TAP_HEIGHT)
                    .then(
                        if (piece.onTap == null) {
                            Modifier
                        } else {
                            // A null interaction source rather than a
                            // remembered one: with no indication to
                            // drive there is nothing to collect
                            // interactions for, and clickable allocates
                            // one lazily only if something later asks.
                            Modifier.clickable(
                                interactionSource = null,
                                indication = null,
                                onClick = piece.onTap,
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                FuriganaText(
                    segments = piece.segments,
                    style = style,
                    // The colour marks what a tap will do, so it follows
                    // [RenderedPiece.onTap] and nothing else. It briefly
                    // followed "is a content word" instead, on the
                    // argument that the word being read would otherwise
                    // be drawn like a particle. Alex overruled that on a
                    // device (2026-08-27): colour is the affordance he
                    // set when tappable words were introduced, so a
                    // coloured word that does nothing reads as broken,
                    // which is worse than a studied word looking plain.
                    //
                    // The colour, not a highlight: a highlight styles
                    // the part of a run that matched something, and
                    // nothing here matched anything — the whole word is
                    // what can be tapped. Handed to the line rather than
                    // to its segments so both halves of a ruby unit take
                    // it, since the base and the reading are drawn from
                    // this one style.
                    color = if (piece.onTap != null) contentWordColor else Color.Unspecified,
                )
            }
        }
    }
}

/**
 * One piece of the sentence as the row draws it: the runs to render and
 * what tapping them does.
 *
 * A null [onTap] is a piece that goes nowhere, following the project's
 * "null callback is a disabled action" rule: a particle, punctuation,
 * text no word claimed, *or* the word this tab's entry is about. All of
 * them are drawn the same, because the colour promises a tap and these
 * have none to give.
 */
@Immutable
private data class RenderedPiece(
    val segments: List<FuriganaSegment>,
    val onTap: (() -> Unit)?,
)
