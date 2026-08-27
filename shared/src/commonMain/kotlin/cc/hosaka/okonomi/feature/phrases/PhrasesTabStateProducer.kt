package cc.hosaka.okonomi.feature.phrases

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.BreakdownPos
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.loadBreakdownPos
import cc.hosaka.okonomi.db.loadSentencesForEntry
import cc.hosaka.okonomi.feature.navigation.state.LoadState
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.healDictionaryAfter
import cc.hosaka.okonomi.feature.navigation.state.loadOnce
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.ui.PagingFooterState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Composable
fun producePhrasesTabState(
    entryId: Long,
    headword: String,
): State<PhrasesTabState> = produceScreenState(
    // Keyed per entry beside the entry's own screen state, so two
    // entries on the same back stack cannot share one tab's sentences.
    key = "entry-phrases-$entryId",
    initial = PhrasesTabState(),
) {
    phrasesTabStateProducer(entryId, headword)
}

/**
 * How many sentences the tab shows before the reader scrolls for more,
 * and how many each further page adds.
 *
 * A page rather than the whole stored set because dictgen now keeps up
 * to fifty per entry: locating fifty sentences' words, aligning their
 * readings and composing a text node per word — 食べる's examples run to
 * dozens each — to show the three that fit on screen is the cost this
 * exists to avoid. The query still reads the entry's set in one go; it
 * is indexed on (entry_id, ord) and the rows are small.
 */
internal const val PHRASES_PAGE_SIZE = 30

/**
 * Unlike the Forms tab there is genuinely something to wait for here, so
 * the tab owns a real Loading and Error pair. A sentence failure costs
 * this tab alone: the entry view and its other tabs stand.
 */
suspend fun ScreenStateScope.phrasesTabStateProducer(
    entryId: Long,
    /**
     * The entry's own dictionary form, carried through untouched so the
     * tab can leave the word the reader is studying without a tap. It
     * takes no part in loading anything; see
     * [PhrasesTabContentState.Ready.wordBeingRead].
     */
    headword: String,
    load: suspend (Long) -> List<ExampleSentence> = { loadSentencesForEntry(it) },
    loadPos: suspend (List<BreakdownWord>) -> BreakdownPos = { loadBreakdownPos(it) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
): Flow<PhrasesTabState> {
    // Persisted beside the sentences themselves, so a reader who paged
    // through an entry's examples and swiped to another tab comes back
    // to the same list rather than to the first page again.
    val shown = mutablePersistedFlow(
        key = "phrases-shown",
        initial = PHRASES_PAGE_SIZE,
    )
    return combine(
        loadOnce(
            key = "phrases",
            load = { loadExamples(entryId, load, loadPos, invalidate) },
            invalidate = invalidate,
        ),
        shown,
    ) { state, limit ->
        PhrasesTabState(
            content = when (state) {
                LoadState.Loading -> PhrasesTabContentState.Loading

                // An entry the corpus never uses loads successfully with
                // nothing in it, which is the common case and its own state
                // rather than an error or an empty list on screen.
                is LoadState.Ready -> if (state.value.sentences.isEmpty()) {
                    PhrasesTabContentState.Empty
                } else {
                    PhrasesTabContentState.Ready(
                        sentences = state.value.sentences.take(limit),
                        tappableWords = state.value.tappableWords,
                        wordBeingRead = headword,
                        entryPos = state.value.entryPos,
                        // Computed from the limit this state was built
                        // for rather than incremented, so the scroll
                        // watcher calling it twice before the next state
                        // lands asks for one page, not two.
                        onShowMore = { shown.value = limit + PHRASES_PAGE_SIZE }
                            .takeIf { limit < state.value.sentences.size },
                        // Always None, and honestly so: this tab's
                        // pages are a `take` over a list already in
                        // memory, so no page is ever in flight and none
                        // can fail. The load that *can* fail is the
                        // entry's whole set, which is [Error] with its
                        // own retry rather than a footer. The field is
                        // here because the day sentences are paged out
                        // of the database, the state must already have
                        // somewhere to say a page is coming — inferring
                        // it from onShowMore is exactly what could not
                        // be done.
                        footer = PagingFooterState.None,
                    )
                }

                is LoadState.Error -> PhrasesTabContentState.Error(state.onRetry)
            },
        )
    }
}

/**
 * An entry's stored examples, which of their words a tap opens a search
 * for, and what the dictionary said about those words' parts of speech
 * — which decides the taps and, in `SentenceFurigana.kt`, which
 * readings may be set over an inflected form.
 */
private data class LoadedExamples(
    val sentences: List<ExampleSentence>,
    val tappableWords: Set<BreakdownWord>,
    val entryPos: Map<Long, List<String>> = emptyMap(),
)

/**
 * One load for the sentences and one for what may be tapped in them,
 * so the tab has a single thing to wait for. The two failures are not
 * equal, though, and are deliberately not treated as one:
 *
 * - **The sentences failing is the tab failing.** It throws, and the
 *   caller turns it into an error body with a retry.
 * - **The part of speech failing is a decoration failing.** The
 *   examples are loaded and readable; hiding them behind an error
 *   because the colouring could not be worked out would cost the reader
 *   the thing they came for to spare them a thing they did not ask for.
 *   Nothing is tappable that run, the shared handle still gets the
 *   project's standard heal, and the sentences render.
 *
 * Tappability is settled over the entry's whole stored set rather than
 * the page on screen: paging then adds sentences without a second
 * query, and a word cannot change colour when the page it sits on
 * grows.
 */
private suspend fun loadExamples(
    entryId: Long,
    load: suspend (Long) -> List<ExampleSentence>,
    loadPos: suspend (List<BreakdownWord>) -> BreakdownPos,
    invalidate: suspend () -> Unit,
): LoadedExamples {
    val sentences = load(entryId)
    // Located words only: one the scan could not place has no span on
    // screen, so asking the dictionary about it buys nothing.
    val located = sentences.flatMap { sentence -> sentence.tokens.map { it.word } }
    // One question per (dictionary form, entry id). A headword the
    // corpus inflects five ways is five distinct BreakdownWord values
    // with one answer between them, because the rule reads neither the
    // surface nor the reading — asking five times would put five copies
    // of the same word in the query.
    val asked = located.distinctBy { it.text to it.entryId }
    if (asked.isEmpty()) return LoadedExamples(sentences, emptySet())
    val pos = try {
        loadPos(asked)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        healDictionaryAfter(e, invalidate)
        return LoadedExamples(sentences, emptySet())
    }
    val tappable = asked
        .filter { isBreakdownWordTappable(it, pos) }
        .mapTo(mutableSetOf()) { it.text to it.entryId }
    return LoadedExamples(
        sentences = sentences,
        // Expanded back to the values the tab holds, which carry their
        // surfaces: the answer was settled once, and every spelling of
        // the word it was settled for takes it.
        tappableWords = located.filterTo(mutableSetOf()) { (it.text to it.entryId) in tappable },
        entryPos = pos.byEntryId,
    )
}
