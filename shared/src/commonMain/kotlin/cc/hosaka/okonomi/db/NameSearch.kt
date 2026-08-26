package cc.hosaka.okonomi.db

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One person-name row: what JMnedict states about a name and nothing
 * more. A name has no senses, no part of speech and no kanji breakdown,
 * which is why this is its own type rather than a [SearchHit] with most
 * of its fields empty — and why a name row is not tappable (Alex's
 * ruling): the Entry View has nothing to show for one.
 *
 * [types] holds only the person-name codes of the stored `name_type`, in
 * a fixed order; a code the row also carries but that says nothing about
 * a person (`person` beside `fem`, say) is not shown. [romanisation] is
 * the source's own translation of the name, displayed but never searched.
 */
@Immutable
data class NameHit(
    val id: Long,
    val kanji: String?,
    val reading: String,
    val types: List<String>,
    val romanisation: String,
) {
    /**
     * Stable list identity, and it has to be the whole row.
     *
     * The entry id is not unique — one JMnedict entry becomes a row per
     * kanji per reading per translation, which is exactly how 田中 and
     * 田仲 reach the screen as two rows — so a key of anything less
     * makes a `LazyColumn` holding two spellings of one name throw
     * "Key was already used". [types] is in it for the same reason it is
     * in the query's ORDER BY: it is the only thing separating two rows
     * that are otherwise alike.
     */
    val key: String get() = "$id|$kanji|$reading|${types.joinToString(",")}|$romanisation"
}

/**
 * [hits] with [hasMore] set when the match set ran past what was asked
 * for, which is what lets one pager serve the words above and the names
 * below.
 */
data class NameResults(
    val hits: List<NameHit>,
    val hasMore: Boolean = false,
)

/**
 * The person-name codes a row is shown as, in the order they are shown.
 * Fixed here rather than taken from the row so two rows tagged the same
 * way never read differently.
 */
private val DISPLAYED_NAME_TYPES = listOf("surname", "given", "fem", "masc")

/**
 * Searches the shared app-lifetime dictionary for names. Callers gate
 * this on the Names toggle: with it off the query is never issued, which
 * is the toggle's purpose rather than an optimisation, since search runs
 * on every keystroke.
 */
suspend fun searchNames(
    query: String,
    offset: Int = 0,
    limit: Int = SEARCH_RESULT_LIMIT,
): NameResults {
    require(limit > 0) { "limit must be positive: $limit" }
    require(offset >= 0) { "offset must not be negative: $offset" }
    val database = dictionary()
    return withContext(Dispatchers.Default) {
        database.searchNames(query, offset, limit)
    }
}

/**
 * Indexed prefix matching over a name's reading and its kanji, in both
 * kana scripts, and over nothing else. See name.sq for why that is one
 * four-armed query rather than four merged in Kotlin.
 *
 * [offset] is what makes paging cheap: the order is total, so page two
 * is the rows after page one rather than page one fetched again with a
 * longer limit. The caller accumulates.
 *
 * Non-Japanese input returns without touching the database. Names are
 * not searched in romaji, so an English query has nothing here to match,
 * and issuing a query for it would be exactly the per-keystroke cost the
 * toggle exists to avoid.
 *
 * There is no ranking to apply — names carry no commonness, no priority
 * tags and no frequency — so the order is the one the SQL states.
 */
suspend fun DictionaryDatabase.searchNames(
    query: String,
    offset: Int = 0,
    limit: Int = SEARCH_RESULT_LIMIT,
): NameResults {
    require(limit > 0) { "limit must be positive: $limit" }
    require(offset >= 0) { "offset must not be negative: $offset" }
    val trimmed = query.trim()
    if (trimmed.isEmpty() || !containsJapaneseText(trimmed)) return NameResults(emptyList())
    val (hiragana, katakana) = kanaPrefixes(trimmed)
    // One row of overfetch, so a full page can still report more behind it.
    val rows = db.nameQueries.searchNamePrefix(
        hiragana = hiragana,
        hiraganaEnd = prefixRangeEnd(hiragana),
        katakana = katakana,
        katakanaEnd = prefixRangeEnd(katakana),
        limit = (limit + 1).toLong(),
        offset = offset.toLong(),
    ).awaitList()
    val hits = rows.map {
        NameHit(it.id, it.kanji, it.reading, displayedTypes(it.name_type), it.translation)
    }
    return NameResults(hits = hits.take(limit), hasMore = hits.size > limit)
}

/** The person-name codes of a stored `name_type`, in display order. */
internal fun displayedTypes(nameType: String?): List<String> {
    val codes = StoredValues.codes(nameType).toSet()
    return DISPLAYED_NAME_TYPES.filter { it in codes }
}

/**
 * [text] with its kana folded to hiragana and to katakana, as the pair
 * the name query needs.
 *
 * Name readings are stored in whichever script the source used, and a
 * SQL range comparison is byte order rather than kana equivalence, so a
 * query has to be asked in both. Anything that is not kana — kanji,
 * digits, the prolonged sound mark, punctuation — is left exactly as it
 * is by both folds, so a query with no kana in it yields the same string
 * twice and the extra query arms collapse to duplicates.
 *
 * Deliberately narrower than the folding in `ui/furigana/ReadingAlignment`,
 * which also treats ー as the vowel it lengthens. That is right when
 * matching a reading against a word it belongs to and wrong here: this
 * has to agree character for character with a byte-ordered range scan,
 * and a fold that rewrote ー would produce a prefix that no stored form
 * begins with.
 */
internal fun kanaPrefixes(text: String): Pair<String, String> =
    text.map(::toHiragana).joinToString("") to text.map(::toKatakana).joinToString("")

/**
 * The katakana block mirrors hiragana at a fixed offset of 0x60, up to
 * ヶ. Small ヵ and ヶ (0x30F5, 0x30F6) fold with the rest: unlike the
 * furigana aligner, which cares that they are *read* か/が/こ, all that
 * matters here is that the two scripts map onto each other one to one.
 */
private fun toHiragana(char: Char): Char = when (char.code) {
    in 0x30A1..0x30F6 -> (char.code - 0x60).toChar()
    else -> char
}

private fun toKatakana(char: Char): Char = when (char.code) {
    in 0x3041..0x3096 -> (char.code + 0x60).toChar()
    else -> char
}
