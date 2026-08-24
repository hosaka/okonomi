package cc.hosaka.okonomi.db

import cc.hosaka.okonomi.deinflect.Deinflection
import cc.hosaka.okonomi.deinflect.JapaneseDeinflector
import cc.hosaka.okonomi.deinflect.LanguageTransformer
import cc.hosaka.okonomi.deinflect.posCodesToConditionFlags
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The most hits one search returns. */
const val SEARCH_RESULT_LIMIT = 50

private const val SENSE_LINE_LIMIT = 3

/** At most this many tail-truncation retries on the fallback path. */
private const val MAX_FALLBACK_STEPS = 8

/**
 * How many entries the English path hydrates before fine ranking. The
 * SQL side already ordered the full match set by the earliest
 * sense/gloss position each entry matched at, so this pool holds the
 * best-positioned entries no matter how large the match set was; the
 * cap only bounds hydration work.
 */
private const val FTS_RANKING_POOL = 400

/** Packs (sense ord, gloss ord) into one sortable number; see fts.sq. */
private const val GLOSS_POSITION_FACTOR = 1000L

/**
 * One piece of a result row's title line. [highlight] is the character
 * range of [text] that matched the query, or null when this segment did
 * not match (deinflected hits carry no highlight at all — their match is
 * explained by [SearchHit.traceLabels] instead).
 */
data class TitleSegment(
    val text: String,
    val highlight: IntRange? = null,
)

/**
 * One search result row: the title segments (primary kanji form, then
 * the matched reading; reading only when the entry has no kanji form),
 * the human-readable deinflection trace (empty for direct matches), up
 * to three sense lines (glosses joined ", ", the last suffixed "…" when
 * more senses exist), and whether any of the entry's forms carries a
 * first-tier priority tag (jisho's "common word" badge).
 */
data class SearchHit(
    val entryId: Long,
    val titleSegments: List<TitleSegment>,
    val traceLabels: List<String>,
    val senseLines: List<String>,
    val isCommon: Boolean,
)

data class SearchResults(
    val hits: List<SearchHit>,
    val isFallback: Boolean = false,
    /**
     * More entries matched than [hits] carries (the pre-truncation
     * pool exceeded the limit). No UI treatment yet; reserved for
     * future pagination.
     */
    val hasMore: Boolean = false,
    /**
     * The query's word tokens when the English path produced these
     * hits, so the UI can highlight their occurrences in the sense
     * lines. Empty on the Japanese path, where the match is already
     * shown in the title line.
     */
    val glossTokens: List<String> = emptyList(),
)

/**
 * Searches the shared app-lifetime dictionary. See
 * [DictionaryDatabase.searchEntries] for the algorithm.
 */
suspend fun searchEntries(
    query: String,
    limit: Int = SEARCH_RESULT_LIMIT,
): SearchResults {
    require(limit > 0) { "limit must be positive: $limit" }
    val database = dictionary()
    // No common multiplatform IO dispatcher is available to this
    // module; Default keeps the synchronous SQLite work off the main
    // thread, and the queries are short-lived.
    return withContext(Dispatchers.Default) {
        database.searchEntries(query, limit)
    }
}

/**
 * As-you-type dictionary search.
 *
 * Japanese input (any kana or kanji codepoint) runs indexed prefix
 * matching on readings and kanji forms plus exact matching of the
 * deinflection candidates, each candidate validated against the entry's
 * part-of-speech flags. Deinflected exact hits rank before prefix hits;
 * both groups are ordered by common_rank and entries are deduplicated.
 * When nothing matches, the query tail is truncated progressively and
 * prefix matching re-run; such results are marked
 * [SearchResults.isFallback].
 *
 * All other input (including accented Latin or Cyrillic) searches the
 * English glosses through FTS; the query is sanitized so raw input
 * never reaches MATCH syntax.
 */
suspend fun DictionaryDatabase.searchEntries(
    query: String,
    limit: Int = SEARCH_RESULT_LIMIT,
): SearchResults {
    require(limit > 0) { "limit must be positive: $limit" }
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return SearchResults(emptyList())
    return if (containsJapaneseText(trimmed)) {
        searchJapanese(trimmed, limit)
    } else {
        searchEnglish(trimmed, limit)
    }
}

/**
 * True when [text] contains at least one Japanese-script codepoint:
 * hiragana, katakana (including phonetic extensions and halfwidth
 * forms) or han ideographs (including extensions). Everything else —
 * accented Latin, fullwidth ABC, Cyrillic — is not Japanese input.
 */
internal fun containsJapaneseText(text: String): Boolean {
    var i = 0
    while (i < text.length) {
        val char = text[i]
        val code: Int
        if (char.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
            code = ((char.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00) + 0x10000
            i += 2
        } else {
            code = char.code
            i += 1
        }
        if (isJapaneseCodePoint(code)) return true
    }
    return false
}

private fun isJapaneseCodePoint(code: Int): Boolean = when (code) {
    in 0x3040..0x309F, // hiragana
    in 0x30A0..0x30FF, // katakana
    in 0x31F0..0x31FF, // katakana phonetic extensions
    in 0xFF66..0xFF9D, // halfwidth katakana
    in 0x3400..0x4DBF, // CJK unified ideographs extension A
    in 0x4E00..0x9FFF, // CJK unified ideographs
    in 0xF900..0xFAFF, // CJK compatibility ideographs
    in 0x20000..0x2FA1F, // CJK unified ideographs extensions B+
    -> true

    else -> false
}

/**
 * Upper bound of the index-friendly prefix range: `text >= prefix AND
 * text < prefixEnd` spans every string starting with the prefix. U+FFFF
 * sorts after every BMP character under SQLite's BINARY collation
 * (UTF-8 byte order equals codepoint order); prefixes whose next
 * character would be a supplementary-plane codepoint are an accepted
 * loss.
 */
internal fun prefixRangeEnd(prefix: String): String = prefix + '￿'

/**
 * Turns raw user input into a safe FTS5 MATCH expression: the query's
 * word tokens, each quoted so no MATCH metacharacter or operator
 * survives. Returns null when no token remains.
 *
 * Tokenizing on the same word boundaries as [queryTokens] rather than
 * on whitespace keeps MATCH and our own ranking/highlighting rules in
 * agreement: splitting "eat-in" on whitespace would ask FTS for the
 * adjacent phrase "eat in" while every Kotlin rule treats the two as an
 * unordered pair.
 */
internal fun sanitizeFtsQuery(query: String): String? {
    val tokens = queryTokens(query)
    if (tokens.isEmpty()) return null
    // Tokens are runs of letters and digits, so no quote character can
    // appear inside one; quoting only has to neutralize operators.
    return tokens.joinToString(" ") { token -> "\"" + token + "\"" }
}

/**
 * The query's word tokens: maximal runs of letters and digits, in the
 * order typed, deduplicated. This mirrors what the FTS tokenizer sees
 * (punctuation separates words), so ranking and highlighting agree on
 * what "a match" is.
 */
internal fun queryTokens(query: String): List<String> = buildList {
    forEachWord(query) { word, _, _ -> add(word) }
}.distinctBy { it.lowercase() }

/**
 * Walks the letter/digit words of [text], reporting each word, its
 * start index and its zero-based position within the line. Shared by
 * gloss ranking and gloss highlighting so both agree on word
 * boundaries.
 */
internal inline fun forEachWord(
    text: String,
    action: (word: String, start: Int, position: Int) -> Unit,
) {
    var index = 0
    var position = 0
    while (index < text.length) {
        if (!text[index].isLetterOrDigit()) {
            index++
            continue
        }
        var end = index
        while (end < text.length && text[end].isLetterOrDigit()) {
            end++
        }
        action(text.substring(index, end), index, position)
        position++
        index = end
    }
}

/**
 * The token [word] is, ignoring case, or null when it is none of them.
 * Callers that must see every token matched keep the returned token
 * rather than a boolean.
 */
internal fun matchedToken(word: String, tokens: List<String>): String? =
    tokens.firstOrNull { it.equals(word, ignoreCase = true) }

/**
 * One matched row before hydration: which entry, through which text it
 * matched, how it ranks, and how the match is presented (a highlight of
 * [highlightLength] leading characters, or a deinflection [trace]).
 */
private class RankedMatch(
    val entryId: Long,
    val matchedText: String = "",
    val commonRank: Long = Long.MAX_VALUE,
    val highlightLength: Int? = null,
    val trace: List<String> = emptyList(),
)

private suspend fun DictionaryDatabase.searchJapanese(query: String, limit: Int): SearchResults {
    // One row of overfetch per source, so a pool fed by a single query
    // can still exceed the limit and set hasMore.
    val fetchLimit = limit + 1
    val exact = deinflectedExactMatches(query, fetchLimit)
    val prefix = prefixMatches(query, fetchLimit)
    val pool = mergeMatches(exact, prefix)
    if (pool.isNotEmpty()) {
        val shown = pool.take(limit)
        return SearchResults(
            hits = buildHits(shown, loadRankingContent(shown.map { it.entryId })),
            hasMore = pool.size > limit,
        )
    }
    // Nothing matched: progressively drop the tail of the query and
    // retry prefix matching, so a half-typed or overshot inflection
    // still surfaces nearby entries. Bounded so garbage tails give up
    // instead of scanning the whole query.
    var steps = 0
    var length = query.length - 1
    while (length >= 1 && steps < MAX_FALLBACK_STEPS) {
        // Never cut a surrogate pair in half: a truncation that would
        // end on a high surrogate is skipped, not queried.
        if (query[length - 1].isHighSurrogate()) {
            length--
            continue
        }
        coroutineContext.ensureActive()
        steps++
        val fallbackPool = mergeMatches(emptyList(), prefixMatches(query.take(length), fetchLimit))
        if (fallbackPool.isNotEmpty()) {
            val shown = fallbackPool.take(limit)
            return SearchResults(
                hits = buildHits(shown, loadRankingContent(shown.map { it.entryId })),
                isFallback = true,
                hasMore = fallbackPool.size > limit,
            )
        }
        length--
    }
    return SearchResults(emptyList())
}

/**
 * Gloss search, ranked the way a dictionary reader expects: the entry
 * whose *first* sense's *first* gloss is the typed word comes first,
 * however common the entries buried behind it are.
 *
 * SQL pre-ranks the full match set by the earliest sense/gloss position
 * an entry matched at, breaking position ties by entry commonness so
 * the pool it hands over is both the best-placed and the most common
 * of the matches. Kotlin then fine ranks that pool by (sense ord, gloss
 * ord, entry common_rank, word position within the gloss) — commonness
 * settles which of two equally-placed glosses leads, and word position
 * separates entries of equal standing ("to eat" at word 1 beats "to
 * have a bite to eat" at word 5).
 *
 * Only the entries that survive ranking are turned into rows: the pool
 * is hundreds of entries per keystroke and at most [limit] of them are
 * ever shown.
 */
private suspend fun DictionaryDatabase.searchEnglish(query: String, limit: Int): SearchResults {
    val match = sanitizeFtsQuery(query) ?: return SearchResults(emptyList())
    val tokens = queryTokens(query)
    // A caller asking for more than the standard pool still gets a pool
    // big enough to answer, and one row of headroom keeps hasMore true.
    val pool = maxOf(FTS_RANKING_POOL, limit + 1)
    val rows = db.ftsQueries.searchGlossFtsRankedEntryIds(match, pool.toLong()).awaitList()
    if (rows.isEmpty()) return SearchResults(emptyList())
    coroutineContext.ensureActive()
    val content = loadRankingContent(rows.map { it.entry_id })
    val ranked = rows
        .map { row ->
            row.entry_id to englishRank(
                content = content[row.entry_id],
                tokens = tokens,
                // A null MIN() should be impossible (the GROUP BY only
                // sees matched rows), so fail toward the worst place
                // rather than silently claiming the best one.
                sqlPosition = row.pos ?: Long.MAX_VALUE,
            )
        }
        .sortedWith(
            compareBy(
                { (_, rank) -> rank.senseOrd },
                { (_, rank) -> rank.glossOrd },
                // Commonness dominates position inside a gloss:
                // otherwise 食言 ("eat one's words", no priority tags)
                // beats 食べる's "to eat" purely for starting on the
                // matched word.
                { (_, rank) -> rank.commonRank },
                { (_, rank) -> rank.wordPosition },
                // Ties would otherwise depend on SQLite's row order.
                { (entryId, _) -> entryId },
            ),
        )
    coroutineContext.ensureActive()
    val shown = ranked.take(limit)
    return SearchResults(
        hits = buildHits(
            matches = shown.map { (entryId, _) -> RankedMatch(entryId) },
            content = content,
            matchedSenseOrds = shown.associate { (entryId, rank) -> entryId to rank.senseOrd },
        ),
        hasMore = ranked.size > limit,
        glossTokens = tokens,
    )
}

/** Where and how well an entry matched an English query. */
private class EnglishRank(
    val senseOrd: Long,
    val glossOrd: Long,
    val wordPosition: Int,
    val commonRank: Long,
)

private fun englishRank(
    content: RankingContent?,
    tokens: List<String>,
    sqlPosition: Long,
): EnglishRank {
    content?.senses?.forEach { sense ->
        sense.glosses.forEach { gloss ->
            val position = matchedWordPosition(gloss.text, tokens)
            if (position != null) {
                return EnglishRank(
                    senseOrd = sense.ord,
                    glossOrd = gloss.ord,
                    wordPosition = position,
                    commonRank = content.commonRank,
                )
            }
        }
    }
    // The FTS tokenizer matched something plain word matching cannot
    // see (diacritic folding, for instance). The position SQL computed
    // over the real match set still orders the entry sensibly; only the
    // word position is unknown, so it sorts last among its position
    // peers.
    return EnglishRank(
        senseOrd = sqlPosition / GLOSS_POSITION_FACTOR,
        glossOrd = sqlPosition % GLOSS_POSITION_FACTOR,
        wordPosition = Int.MAX_VALUE,
        commonRank = content?.commonRank ?: Long.MAX_VALUE,
    )
}

/**
 * Position of the first word of [text] that is one of [tokens], or
 * null when [text] does not carry *every* token.
 *
 * The all-tokens rule is what FTS itself matched on: a multi-token
 * MATCH is an AND over the gloss row, so a gloss holding only some of
 * the tokens was never part of the match set and must not be allowed
 * to rank the entry — otherwise 遣る's "to do" would rank it at the
 * very top of a search for "to eat".
 */
private fun matchedWordPosition(text: String, tokens: List<String>): Int? {
    if (tokens.isEmpty()) return null
    var first = -1
    val matched = mutableSetOf<String>()
    forEachWord(text) { word, _, position ->
        val token = matchedToken(word, tokens)
        if (token != null) {
            if (first < 0) {
                first = position
            }
            matched += token
        }
    }
    return first.takeIf { matched.size == tokens.size }
}

/**
 * Exact matches of the deduplicated deinflection candidates, each
 * validated per candidate: the candidate's condition flags must
 * intersect the flags of the entry's parts of speech (0 matches all).
 */
private suspend fun DictionaryDatabase.deinflectedExactMatches(
    query: String,
    limit: Int,
): List<RankedMatch> {
    val candidates = JapaneseDeinflector.deinflect(query)
    val texts = candidates.map { it.text }.distinct()
    if (texts.isEmpty()) return emptyList()
    val rows = db.entryQueries.readingsExact(texts, limit.toLong()).awaitList()
        .map { MatchedRow(it.entry_id, it.text, it.common_rank) } +
        db.entryQueries.kanjiFormsExact(texts, limit.toLong()).awaitList()
            .map { MatchedRow(it.entry_id, it.text, it.common_rank) }
    if (rows.isEmpty()) return emptyList()

    coroutineContext.ensureActive()
    val entryFlags = db.entryQueries.sensesForEntries(rows.map { it.entryId }.distinct())
        .awaitList()
        .groupBy({ it.entry_id }, { posCodesToConditionFlags(it.pos) })
        .mapValues { (_, flags) -> flags.fold(0L) { acc, f -> acc or f } }

    return rows.mapNotNull { row ->
        // The same text can be reached through several derivation
        // paths; the shortest valid chain wins so the breadcrumb stays
        // minimal and deterministic (minByOrNull keeps the first of
        // equal-length chains, which follows the engine's BFS order).
        val candidate = candidates
            .filter { c ->
                c.text == row.text && c.matchesEntryFlags(entryFlags[row.entryId] ?: 0L)
            }
            .minByOrNull { it.trace.size }
            ?: return@mapNotNull null
        RankedMatch(
            entryId = row.entryId,
            matchedText = row.text,
            commonRank = row.commonRank,
            // The identity candidate (empty trace) is a plain exact
            // match: highlight the whole word instead of a breadcrumb.
            highlightLength = if (candidate.trace.isEmpty()) row.text.length else null,
            trace = candidate.trace,
        )
    }
}

private fun Deinflection.matchesEntryFlags(entryFlags: Long): Boolean =
    LanguageTransformer.conditionsMatch(conditions, entryFlags)

private suspend fun DictionaryDatabase.prefixMatches(prefix: String, limit: Int): List<RankedMatch> {
    val end = prefixRangeEnd(prefix)
    val rows = db.entryQueries.searchReadingRangePrefix(prefix, end, limit.toLong()).awaitList()
        .map { MatchedRow(it.entry_id, it.text, it.common_rank) } +
        db.entryQueries.searchKanjiFormRangePrefix(prefix, end, limit.toLong()).awaitList()
            .map { MatchedRow(it.entry_id, it.text, it.common_rank) }
    return rows.map { row ->
        RankedMatch(
            entryId = row.entryId,
            matchedText = row.text,
            commonRank = row.commonRank,
            highlightLength = prefix.length,
            trace = emptyList(),
        )
    }
}

private class MatchedRow(
    val entryId: Long,
    val text: String,
    val commonRank: Long,
)

/**
 * Deinflected exact hits first, then prefix hits, each group ordered by
 * the matched row's common_rank; entries are deduplicated. An entry
 * that also prefix-matches the typed query is presented as a prefix hit
 * (highlight over the typed prefix) rather than a deinflected one — the
 * breadcrumb ranking applies to prefix-only situations, so e.g. たべ
 * shows 食べる as a highlighted prefix match, not as "continuative".
 * Returns the full deduplicated pool; callers truncate to their limit.
 */
private fun mergeMatches(
    exact: List<RankedMatch>,
    prefix: List<RankedMatch>,
): List<RankedMatch> {
    val prefixIds = prefix.map { it.entryId }.toSet()
    val exactOnly = exact.filter { it.entryId !in prefixIds }
    return (exactOnly.sortedBy { it.commonRank } + prefix.sortedBy { it.commonRank })
        .distinctBy { it.entryId }
}

/**
 * Everything ranking needs about one entry, and nothing display needs:
 * its denormalized commonness plus its senses and glosses. Loaded for
 * the whole candidate pool, which is hundreds of entries per keystroke.
 */
private class RankingContent(
    val commonRank: Long,
    val isCommon: Boolean,
    val senses: List<SenseContent>,
)

private class SenseContent(
    val ord: Long,
    val glosses: List<GlossContent>,
) {
    /** The sense's glosses as one displayable line. */
    fun line(): String = glosses.joinToString(", ") { it.text }
}

private class GlossContent(
    val ord: Long,
    val text: String,
)

private suspend fun DictionaryDatabase.loadRankingContent(ids: List<Long>): Map<Long, RankingContent> {
    if (ids.isEmpty()) return emptyMap()
    coroutineContext.ensureActive()
    val entries = db.entryQueries.entriesForIds(ids).awaitList().associateBy { it.id }
    val senses = db.entryQueries.sensesForEntries(ids).awaitList().groupBy { it.entry_id }
    val glosses = db.entryQueries.glossesForEntries(ids).awaitList().groupBy { it.sense_id }
    coroutineContext.ensureActive()
    return ids.distinct().associateWith { id ->
        val entry = entries[id]
        RankingContent(
            // A match without its entry row cannot happen through a
            // foreign key, but ranking must not reward the impossible.
            commonRank = entry?.common_rank ?: Long.MAX_VALUE,
            isCommon = entry != null && entry.is_common != 0L,
            senses = senses[id].orEmpty().map { sense ->
                SenseContent(
                    ord = sense.ord,
                    glosses = glosses[sense.id].orEmpty().map { GlossContent(it.ord, it.text) },
                )
            },
        )
    }
}

/**
 * Turns the matches that will actually be shown into rows, fetching
 * the forms and readings only for those. [matchedSenseOrds] names, per
 * entry, the sense the English query matched in, so a match buried
 * below the visible senses is still one of the lines on screen.
 */
private suspend fun DictionaryDatabase.buildHits(
    matches: List<RankedMatch>,
    content: Map<Long, RankingContent>,
    matchedSenseOrds: Map<Long, Long> = emptyMap(),
): List<SearchHit> {
    if (matches.isEmpty()) return emptyList()
    coroutineContext.ensureActive()
    val ids = matches.map { it.entryId }
    val forms = db.entryQueries.kanjiFormsForEntries(ids).awaitList().groupBy { it.entry_id }
    val readings = db.entryQueries.readingsForEntries(ids).awaitList().groupBy { it.entry_id }
    return matches.mapNotNull { match ->
        val entryForms = forms[match.entryId].orEmpty()
        val entryReadings = readings[match.entryId].orEmpty()
        val entryContent = content[match.entryId]

        val matchedIsReading = entryReadings.any { it.text == match.matchedText }
        // Show the matched kanji form when the match came through one,
        // so the highlight lands on visible text; the primary (first)
        // form otherwise.
        val formText = if (!matchedIsReading && entryForms.any { it.text == match.matchedText }) {
            match.matchedText
        } else {
            entryForms.firstOrNull()?.text
        }
        val readingText = if (matchedIsReading) {
            match.matchedText
        } else {
            entryReadings.firstOrNull()?.text
        }

        val segments = buildList {
            if (formText != null) {
                add(
                    TitleSegment(
                        text = formText,
                        highlight = highlightRange(match, matchedText = formText, matched = !matchedIsReading),
                    ),
                )
            }
            if (readingText != null && (formText == null || readingText != formText)) {
                add(
                    TitleSegment(
                        text = readingText,
                        highlight = highlightRange(match, matchedText = readingText, matched = matchedIsReading),
                    ),
                )
            }
        }
        // A degenerate entry with nothing to title cannot render a row.
        if (segments.isEmpty()) return@mapNotNull null

        SearchHit(
            entryId = match.entryId,
            titleSegments = segments,
            traceLabels = match.trace.map { JapaneseDeinflector.ruleDisplayName(it) },
            senseLines = senseLines(
                senses = entryContent?.senses.orEmpty(),
                matchedSenseOrd = matchedSenseOrds[match.entryId],
            ),
            isCommon = entryContent?.isCommon == true,
        )
    }
}

/**
 * The sense lines of a row: the first few senses, except that the
 * sense the query matched in always earns a place — an English hit
 * whose only matching sense is the entry's sixth would otherwise show
 * three lines with nothing highlighted in them. A trailing "…" marks
 * the senses that did not fit.
 */
private fun senseLines(senses: List<SenseContent>, matchedSenseOrd: Long?): List<String> {
    val matchedIndex = senses
        .indexOfFirst { matchedSenseOrd != null && it.ord == matchedSenseOrd }
        .takeIf { it >= 0 }
    val shown = if (matchedIndex != null && matchedIndex >= SENSE_LINE_LIMIT) {
        senses.take(SENSE_LINE_LIMIT - 1) + senses[matchedIndex]
    } else {
        senses.take(SENSE_LINE_LIMIT)
    }
    val lines = shown.map { it.line() }.filter { it.isNotBlank() }
    return if (senses.size > shown.size && lines.isNotEmpty()) {
        lines.dropLast(1) + (lines.last() + " …")
    } else {
        lines
    }
}

private fun highlightRange(match: RankedMatch, matchedText: String, matched: Boolean): IntRange? {
    val length = match.highlightLength ?: return null
    if (!matched || matchedText != match.matchedText) return null
    return 0 until minOf(length, matchedText.length)
}
