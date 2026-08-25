package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.db.forEachWord
import cc.hosaka.okonomi.db.matchedToken
import cc.hosaka.okonomi.ui.PagingFooterState

@Immutable
data class SearchState(
    val query: String = "",
    val onQueryChange: ((String) -> Unit)? = null,
    val onClear: (() -> Unit)? = null,
    val results: SearchResultsState = SearchResultsState.Idle,
)

/**
 * The results area below the search field. [Idle] while the query is
 * blank, [Searching] before the first result for a query lands,
 * [Error] when a search failed (typing again retries), and [Results]
 * with the hits — possibly from the truncated-prefix fallback.
 *
 * [Results] and [Error] carry the query they were produced for, so the
 * UI can keep showing the previous hits while a newer query is still in
 * flight (`results.query != state.query` means "refining") instead of
 * blanking the list on every keystroke.
 */
@Immutable
sealed interface SearchResultsState {
    data object Idle : SearchResultsState

    /**
     * A non-blank query whose first search has not landed yet — the
     * debounce window, or a query restored into a fresh screen.
     */
    data class Searching(
        val query: String,
    ) : SearchResultsState

    data class Error(
        val query: String,
    ) : SearchResultsState

    data class Results(
        val query: String,
        val hits: List<SearchHit>,
        val isFallback: Boolean,
        /**
         * Query tokens to highlight in the sense lines (English
         * results only); see [glossHighlights].
         */
        val glossTokens: List<String> = emptyList(),
        /**
         * Asks for the next page of hits. Null when there is no next
         * page — either the whole match set is already on screen, or
         * paging has reached the ranking pool it stops honestly at.
         *
         * A page is a longer prefix of the same ranking, so extending
         * appends rows rather than reordering the ones being read. That
         * holds because of two properties the search deliberately
         * maintains, not by luck:
         *
         * - every ranked order is *total*. The Japanese path orders on
         *   (common_rank, entry_id) in both the SQL and the Kotlin
         *   merge, and the English path on (sense, gloss, common_rank,
         *   word position, entry_id). common_rank alone is not total,
         *   and the ties used to fall out of the scan order, which
         *   changes with the limit.
         * - every LIMIT counts *entries*, not matching rows. The
         *   Japanese prefix queries group by entry (see entry.sq), so
         *   the fifty rows fetched are fifty entries; before that an
         *   entry with several matching readings spent several of them
         *   and page two pulled in entries that sorted above rows
         *   already on screen. The English path ranks the same fixed
         *   pool at every limit for the same reason.
         *
         * Null while a page is already in flight, too: asking again for
         * the page that is coming would be noise. [footer] is what says
         * so on screen.
         */
        val onShowMore: (() -> Unit)? = null,
        /**
         * What is drawn under the last row: nothing, a page on its way,
         * or a page that failed with a way to ask again. Its own state
         * rather than something read off [onShowMore], which cannot
         * tell "no more results" apart from "more results, loading".
         */
        val footer: PagingFooterState = PagingFooterState.None,
    ) : SearchResultsState
}

/**
 * A result row's joined title line: the segment texts joined with
 * ", " and the highlight ranges re-based onto the joined string. Kept
 * as a pure function so the offset math is testable — a highlight on a
 * later segment must land at segment start plus its range, never on a
 * separator.
 */
data class TitleLine(
    val text: String,
    val highlights: List<IntRange>,
)

fun titleLine(segments: List<TitleSegment>): TitleLine {
    val text = StringBuilder()
    val highlights = mutableListOf<IntRange>()
    segments.forEachIndexed { index, segment ->
        if (index > 0) {
            text.append(", ")
        }
        val start = text.length
        segment.highlight?.let { range ->
            highlights += (start + range.first)..(start + range.last)
        }
        text.append(segment.text)
    }
    return TitleLine(
        text = text.toString(),
        highlights = highlights,
    )
}

/**
 * Character ranges of [text] to highlight for an English result: every
 * occurrence of every query token, matched as a whole word and
 * case-insensitively.
 *
 * Two rules, both inherited from what FTS actually matched:
 * - Whole words only, so "eat" highlights in "to eat" but not inside
 *   "creature" — a partial highlight would promise a match the search
 *   never made.
 * - Nothing at all unless the line carries *every* token, mirroring
 *   FTS's per-row AND and the same rule ranking uses. Otherwise a
 *   search for "to eat" would highlight the "to" of 遣る's "to do",
 *   the very gloss ranking refuses to count.
 *
 * Pure so the offsets are testable; empty tokens (the Japanese path)
 * mean no highlighting at all.
 */
fun glossHighlights(text: String, tokens: List<String>): List<IntRange> {
    if (tokens.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    val matched = mutableSetOf<String>()
    forEachWord(text) { word, start, _ ->
        val token = matchedToken(word, tokens)
        if (token != null) {
            ranges += start until start + word.length
            matched += token
        }
    }
    return if (matched.size == tokens.size) ranges else emptyList()
}

/** The bullet every sense line is rendered behind. */
const val SENSE_LINE_BULLET = "- "

/**
 * A sense line ready to render: the bullet-prefixed text and the
 * highlight ranges shifted onto it. The shift is the one place pure
 * offsets become screen positions, so it is pinned by tests rather
 * than done inline in the composable.
 */
data class SenseLine(
    val text: String,
    val highlights: List<IntRange>,
)

fun senseLine(text: String, tokens: List<String>): SenseLine = SenseLine(
    text = SENSE_LINE_BULLET + text,
    highlights = glossHighlights(text, tokens).map { range ->
        (SENSE_LINE_BULLET.length + range.first)..(SENSE_LINE_BULLET.length + range.last)
    },
)
