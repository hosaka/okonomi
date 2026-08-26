package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.db.forEachWord
import cc.hosaka.okonomi.db.matchedToken
import cc.hosaka.okonomi.ui.PagingFooterState
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import cc.hosaka.okonomi.ui.furigana.alignReading

@Immutable
data class SearchState(
    val query: String = "",
    val onQueryChange: ((String) -> Unit)? = null,
    val onClear: (() -> Unit)? = null,
    /**
     * Leaves this search for whatever is under it, or null when this
     * search *is* its section's root and there is nothing under it.
     *
     * The tab's own search must look exactly as it always has, so the
     * back control is not drawn at all rather than drawn disabled. A
     * search pushed above an entry needs one: the navigation bar hides
     * itself at depth greater than one, and iOS has no system back
     * button, so without this the only way out is the edge swipe.
     */
    val onBack: (() -> Unit)? = null,
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
 * A result row's title as furigana: the entry's written form with the
 * matched reading set above its kanji, instead of the two spelled out
 * side by side.
 *
 * The match keeps its highlight through the fold, and keeps its extent.
 * A plain run is split at the highlight's boundaries, so it stays
 * character-exact. A run with a reading lights whole when the match
 * covers a whole half of it and only in part when it does not: たべ
 * against 食べる lights 食-with-た as a unit and べ exactly, while
 * そうさい against 相殺関税 — one undivided run reading そうさいかんぜい
 * — lights the そうさい of the ruby and leaves the kanji alone, because
 * which of those kanji take そうさい is the thing nobody can say.
 *
 * Both the form's own offsets and the reading's are honoured, because a
 * Japanese query matches through whichever of the two it was typed as.
 *
 * Pure so the offset math is testable rather than buried in a
 * composable.
 */
fun titleFurigana(segments: List<TitleSegment>): List<FuriganaSegment> {
    val title = mutableListOf<FuriganaSegment>()
    var index = 0
    while (index < segments.size) {
        val segment = segments[index]
        // Only a segment that says it reads the one before it is set
        // over it; anything else stands beside it, joined as the row
        // always joined two texts it could not pair.
        val reading = segments.getOrNull(index + 1)?.takeIf { it.readsPreviousSegment }
        if (index > 0) {
            title += FuriganaSegment(TITLE_SEPARATOR)
        }
        val aligned = if (reading == null) {
            listOf(FuriganaSegment(segment.text))
        } else {
            alignReading(segment.text, reading.text)
        }
        title += highlighted(aligned, segment.highlight, reading?.highlight)
        index += if (reading == null) 1 else 2
    }
    return title
}

/** What separates two title segments that could not be paired. */
private const val TITLE_SEPARATOR = ", "

/**
 * [segments] with the runs covered by [formHighlight] (offsets into the
 * written form) or [readingHighlight] (offsets into the reading) marked.
 */
private fun highlighted(
    segments: List<FuriganaSegment>,
    formHighlight: IntRange?,
    readingHighlight: IntRange?,
): List<FuriganaSegment> {
    if (formHighlight == null && readingHighlight == null) return segments
    val result = mutableListOf<FuriganaSegment>()
    var formStart = 0
    var readingStart = 0
    segments.forEach { segment ->
        val reading = segment.reading
        val readingLength = reading?.length ?: segment.text.length
        if (reading == null) {
            result += splitByHighlight(segment.text, formStart, readingStart, formHighlight, readingHighlight)
        } else {
            result += segment.copy(
                highlight = rubyHighlight(
                    text = formHighlight.within(formStart, segment.text.length),
                    reading = readingHighlight.within(readingStart, readingLength),
                    textLength = segment.text.length,
                    readingLength = readingLength,
                ),
            )
        }
        formStart += segment.text.length
        readingStart += readingLength
    }
    return result
}

/**
 * What a match covers of one kanji-with-ruby run, given how much of each
 * half it reached.
 *
 * A half matched from end to end means the run itself matched, and both
 * halves light: 食 reads た and nothing else, so a search for たべ has
 * matched 食 as much as it has matched た. A half matched only in part
 * means the run is larger than the match, and only the characters
 * actually typed light — 相殺関税 is one undivided run, and lighting its
 * kanji for a match on そうさい would claim そうさい belongs to 相殺,
 * which is the split [alignReading] declined to make.
 */
private fun rubyHighlight(
    text: IntRange?,
    reading: IntRange?,
    textLength: Int,
    readingLength: Int,
): FuriganaSegment.Highlight? = when {
    text != null && text.count() == textLength -> FuriganaSegment.Highlight.Whole
    reading != null && reading.count() == readingLength -> FuriganaSegment.Highlight.Whole
    text != null -> FuriganaSegment.Highlight.PartOfText(text)
    reading != null -> FuriganaSegment.Highlight.PartOfReading(reading)
    else -> null
}

/**
 * The part of this highlight that falls inside a run starting at [start]
 * and [length] long, rebased onto the run, or null when none of it does.
 */
private fun IntRange?.within(start: Int, length: Int): IntRange? {
    if (this == null || length <= 0) return null
    val from = maxOf(first, start)
    val to = minOf(last, start + length - 1)
    return if (from > to) null else (from - start)..(to - start)
}

/**
 * A run with no reading of its own, cut into highlighted and plain
 * pieces. Its reading is its own text, so a character is covered when
 * either offset falls in its highlight.
 */
private fun splitByHighlight(
    text: String,
    formStart: Int,
    readingStart: Int,
    formHighlight: IntRange?,
    readingHighlight: IntRange?,
): List<FuriganaSegment> {
    val pieces = mutableListOf<FuriganaSegment>()
    var runStart = 0
    var runHighlighted = isHighlighted(0, formStart, readingStart, formHighlight, readingHighlight)
    for (index in 1..text.length) {
        val highlighted = index < text.length &&
            isHighlighted(index, formStart, readingStart, formHighlight, readingHighlight)
        if (index < text.length && highlighted == runHighlighted) continue
        pieces += FuriganaSegment(
            text = text.substring(runStart, index),
            highlight = if (runHighlighted) FuriganaSegment.Highlight.Whole else null,
        )
        runStart = index
        runHighlighted = highlighted
    }
    return pieces
}

private fun isHighlighted(
    offset: Int,
    formStart: Int,
    readingStart: Int,
    formHighlight: IntRange?,
    readingHighlight: IntRange?,
): Boolean = formHighlight?.contains(formStart + offset) == true ||
    readingHighlight?.contains(readingStart + offset) == true

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
