package cc.hosaka.okonomi.db

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * What the dictionary says about the part of speech of one breakdown's
 * words, answered the two ways the tappable-word rule asks:
 *
 * - [byEntryId]: the codes of every sense of the entry a word was
 *   linked to, for the words that resolved to one.
 * - [byText]: the codes of every sense of every entry whose reading or
 *   kanji form is exactly the word's text.
 *
 * Both are needed because the two disagree, and the disagreement is not
 * JMdict's: Tatoeba's index links single-kana particles to homographic
 * content words, so は arrives linked to 葉 ("leaf", `n`). Trusting the
 * link alone would leave the commonest particle in the language looking
 * tappable. The rule that reads this lives in the Phrases tab, where it
 * can be stated purely and tested without a database.
 *
 * Codes are the raw JMdict names (`prt`, `cop`, `vs-i`), not labels: a
 * label cannot be parsed back into the code a rule tests.
 *
 * Deliberately not `@Immutable`: this is a loader result the producer
 * consumes and discards, it never reaches composition, and annotating
 * plain `Map`s and `List`s with a promise nothing enforces would only
 * make the next reader trust it further than it deserves.
 */
data class BreakdownPos(
    val byEntryId: Map<Long, List<String>> = emptyMap(),
    val byText: Map<String, List<String>> = emptyMap(),
)

/**
 * Looks the part of speech of [words] up on the shared app-lifetime
 * dictionary. Two keyed queries for a whole entry's stored sentences,
 * run once beside the load that produced them rather than per rendered
 * word.
 */
suspend fun loadBreakdownPos(words: List<BreakdownWord>): BreakdownPos {
    if (words.isEmpty()) return BreakdownPos()
    val database = dictionary()
    // Same reasoning as loadSentencesForEntry: no common IO dispatcher
    // exists here and the queries are short-lived, so Default keeps the
    // synchronous SQLite work off the main thread.
    return withContext(Dispatchers.Default) {
        database.loadBreakdownPos(words)
    }
}

suspend fun DictionaryDatabase.loadBreakdownPos(words: List<BreakdownWord>): BreakdownPos {
    // Three queries against a list that can be several hundred long, on
    // behalf of a screen the reader can swipe away from mid-flight;
    // every one of them is checked in front of, not merely between.
    coroutineContext.ensureActive()
    if (words.isEmpty()) return BreakdownPos()
    val ids = words.mapNotNull { it.entryId }.distinct()
    // Non-empty by construction: `text` is not nullable and `words` is
    // not empty, so there is no empty-texts branch below to guard.
    val texts = words.map { it.text }.distinct()
    val byEntryId = if (ids.isEmpty()) {
        emptyMap()
    } else {
        db.entryQueries.posForEntries(ids).awaitList()
            .groupBy({ it.entry_id }, { it.pos })
            .mapValues { (_, columns) -> codesOf(columns) }
    }
    coroutineContext.ensureActive()
    // A word's text is one question whichever side of the dictionary
    // answers it: だ is a copula as a reading, 迄 a particle as a kanji
    // form.
    val readingColumns = db.entryQueries.posForReadingText(texts).awaitList()
        .map { it.text to it.pos }
    coroutineContext.ensureActive()
    val kanjiFormColumns = db.entryQueries.posForKanjiFormText(texts).awaitList()
        .map { it.text to it.pos }
    coroutineContext.ensureActive()
    return BreakdownPos(
        byEntryId = byEntryId,
        byText = (readingColumns + kanjiFormColumns)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, columns) -> codesOf(columns) },
    )
}

/** The distinct codes of several comma-joined `sense.pos` columns. */
private fun codesOf(columns: List<String?>): List<String> =
    columns.flatMap { StoredValues.codes(it) }.distinct()
