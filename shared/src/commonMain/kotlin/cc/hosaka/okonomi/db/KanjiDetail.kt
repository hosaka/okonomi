package cc.hosaka.okonomi.db

import androidx.compose.runtime.Immutable
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The `type` values dictgen writes into `kanji_reading`. kanjidic2 also
 * defines pinyin, korean_r, korean_h and vietnam readings; dictgen does
 * not write them today, and the tab shows only these three Japanese
 * kinds, so any other type is dropped by [textsOf] on purpose.
 */
internal object KanjiReadingType {
    const val ON = "on"
    const val KUN = "kun"
    const val NANORI = "nanori"
}

/**
 * Everything the Kanji tab shows for one character of a headword.
 *
 * [jlpt] is kanjidic2's **former** four-level scale, where 1 is the most
 * advanced and 4 the most elementary — the inverse of the modern N5–N1
 * intuition, and not faithfully convertible to it. It is stored and
 * shown as the source states it; the label is what tells the reader
 * which scale they are looking at.
 */
@Immutable
data class KanjiCharacter(
    val literal: String,
    val strokeCount: Long?,
    val grade: Long?,
    val jlpt: Long?,
    val freq: Long?,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val nameReadings: List<String>,
    val meanings: List<String>,
    /** Radical literals from radkfile, simplest first. */
    val radicals: List<String>,
    /**
     * One SVG path per stroke, in KanjiVG's drawing order, on the
     * `109x109` viewBox KanjiVG draws every character in. Empty for a
     * character KanjiVG does not carry, which is why the Kanji tab
     * treats emptiness as a rendering state rather than an error.
     *
     * Emptiness is NOT how an older database presents itself: a copy
     * predating dictionary format version 7 has no `kanji_stroke_order`
     * table at all, so the query below would fail rather than come back
     * empty. That state is unreachable — provisioning re-copies the
     * bundled database whenever the version sidecar differs, and does so
     * before anything queries it (`DictionaryProvisioningTest`) — which
     * is exactly what the format bump buys.
     */
    val strokePaths: List<String>,
) {
    /**
     * False for a character that appears in a word but carries no
     * kanjidic2 entry — a real state of the shipped data, not a bug:
     * such a character still gets a card showing its literal, with
     * every kanjidic field null or empty.
     *
     * Derived rather than stored: `kanji.stroke_count` is NOT NULL, so
     * a row either supplies a stroke count or does not exist at all,
     * and a separate flag could only ever drift out of agreement with
     * the column that decides it.
     */
    val hasData: Boolean
        get() = strokeCount != null
}

/**
 * Loads the characters of one headword off the shared app-lifetime
 * dictionary, in the order [literals] states them.
 */
suspend fun loadKanjiForWord(literals: List<String>): List<KanjiCharacter> {
    if (literals.isEmpty()) return emptyList()
    val database = dictionary()
    // Same reasoning as loadEntryDetail: no common IO dispatcher exists
    // here and the queries are short-lived, so Default keeps the
    // synchronous SQLite work off the main thread.
    return withContext(Dispatchers.Default) {
        database.loadKanjiForWord(literals)
    }
}

/**
 * Hydrates each character of [literals]: its kanjidic metadata, its
 * readings grouped by kind, its meanings and its radicals. Input order
 * is preserved and repeats are collapsed, so a headword that writes the
 * same character twice still yields one card.
 *
 * Five queries, one per table, each over the whole character list: a
 * headword is short but a per-character round trip would still be an
 * N+1 against a file-backed database.
 */
suspend fun DictionaryDatabase.loadKanjiForWord(literals: List<String>): List<KanjiCharacter> {
    val distinct = literals.distinct()
    if (distinct.isEmpty()) return emptyList()
    val characters = db.kanjiQueries.kanjiForLiterals(distinct).awaitList().associateBy { it.literal }
    coroutineContext.ensureActive()
    val readings = db.kanjiQueries.kanjiReadingsForLiterals(distinct).awaitList().groupBy { it.kanji }
    coroutineContext.ensureActive()
    val meanings = db.kanjiQueries.kanjiMeaningsForLiterals(distinct).awaitList().groupBy { it.kanji }
    coroutineContext.ensureActive()
    val radicals = db.kanjiQueries.radicalsForLiterals(distinct).awaitList().groupBy { it.kanji }
    coroutineContext.ensureActive()
    val strokes = db.kanjiQueries.strokeOrderForLiterals(distinct).awaitList().associateBy { it.literal }
    return distinct.map { literal ->
        val row = characters[literal]
        // One pass over the character's readings; the map keeps each
        // kind in the order the query returned it.
        val byType = readings[literal].orEmpty().groupBy { it.type }
        KanjiCharacter(
            literal = literal,
            strokeCount = row?.stroke_count,
            grade = row?.grade,
            jlpt = row?.jlpt,
            freq = row?.freq,
            onReadings = byType.textsOf(KanjiReadingType.ON),
            kunReadings = byType.textsOf(KanjiReadingType.KUN),
            nameReadings = byType.textsOf(KanjiReadingType.NANORI),
            // The query orders meanings by (kanji, ord) already.
            meanings = meanings[literal].orEmpty().map { it.text },
            // The query orders radicals by (kanji, stroke count, literal).
            radicals = radicals[literal].orEmpty().map { it.literal },
            // One row per character, so stroke order is the order inside
            // the stored value and no query plan can reach it.
            strokePaths = StoredValues.strokePaths(strokes[literal]?.paths),
        )
    }
}

/**
 * The readings of one kind, in the order the query returned them, which
 * `ORDER BY kanji, rowid` pins to kanjidic's own. Readings keep their
 * okurigana dot (`く.う`) exactly as stored: the dot is the character's
 * boundary within the word, and dropping it would lose information the
 * reader needs.
 *
 * A type this does not ask for is dropped — see [KanjiReadingType].
 */
private fun Map<String, List<Kanji_reading>>.textsOf(type: String): List<String> =
    this[type].orEmpty().map { it.text }
