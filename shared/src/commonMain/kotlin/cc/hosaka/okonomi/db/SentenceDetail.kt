package cc.hosaka.okonomi.db

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One word of a sentence's breakdown line: the word as the dictionary
 * writes it, and how it reads here.
 *
 * A null [reading] means the word is written in kana already and reads
 * as itself. Every word with kanji in it carries a reading, resolved at
 * build time — that is what the breakdown exists for.
 */
@Immutable
data class BreakdownWord(
    val text: String,
    val reading: String?,
)

/**
 * One Tatoeba example: the Japanese sentence, its English translation,
 * and the word-by-word breakdown that makes the Japanese readable.
 *
 * [id] is the sentence's own identity in the dictionary, which the list
 * keys on: two entries can show the same sentence, and one entry can
 * show two sentences that read almost alike, so position is not an
 * identity.
 */
@Immutable
data class ExampleSentence(
    val id: Long,
    val japanese: String,
    val english: String,
    val words: List<BreakdownWord>,
)

/**
 * The stored breakdown format, as the app reads it: space-separated
 * `headword(reading)#entryId` words, the reading and the entry id each
 * present only where dictgen had one. It is dictgen that writes this
 * (see `TatoebaParser.kt`'s `StoredBreakdown`); the two modules cannot
 * share the code, so each keeps the format in one documented place of
 * its own and both are tested against the same shapes.
 *
 * Only the headword and the reading reach the reader. The entry id is
 * parsed off and discarded: it is stored for the increment that makes
 * the words tappable, and reading it before that exists would be a
 * field nothing uses.
 */
internal object Breakdown {

    private val WORD = Regex("""^([^(#]+)(?:\(([^)]*)\))?(?:#\d+)?$""")

    /**
     * The words of one stored breakdown, in sentence order. A word the
     * format does not accept is skipped rather than failing the
     * sentence: the breakdown is an aid to reading the line above it,
     * and losing one word of it is a far smaller loss than losing the
     * example entirely.
     */
    fun words(line: String): List<BreakdownWord> = line.split(' ').mapNotNull { token ->
        if (token.isEmpty()) null else word(token)
    }

    private fun word(token: String): BreakdownWord? {
        val match = WORD.matchEntire(token) ?: return null
        return BreakdownWord(
            text = match.groupValues[1],
            // dictgen never writes `語()`, but this reader is documented
            // as an independent one and must not depend on that: an
            // empty group rendered as a reading would put a bare `()`
            // beside the word.
            reading = match.groups[2]?.value?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Loads the example sentences of one entry off the shared app-lifetime
 * dictionary, in the display order dictgen chose. Empty for the ~86% of
 * entries the Tatoeba corpus never uses — a normal outcome, not a
 * failure.
 */
suspend fun loadSentencesForEntry(entryId: Long): List<ExampleSentence> {
    val database = dictionary()
    // Same reasoning as loadEntryDetail: no common IO dispatcher exists
    // here and the query is short-lived, so Default keeps the
    // synchronous SQLite work off the main thread.
    return withContext(Dispatchers.Default) {
        database.loadSentencesForEntry(entryId)
    }
}

/**
 * One query, capped at ten rows by the generator, each row's stored
 * breakdown parsed into the words the tab renders. A parse and no
 * second query: the readings were resolved at build time. Parsing
 * happens here rather than in the composable, as with the search's
 * title line — the loader is where stored text becomes a display model.
 */
suspend fun DictionaryDatabase.loadSentencesForEntry(entryId: Long): List<ExampleSentence> =
    db.sentenceQueries.sentencesForEntry(entryId).awaitList().map { row ->
        ExampleSentence(
            id = row.id,
            japanese = row.japanese,
            english = row.english,
            words = Breakdown.words(row.breakdown),
        )
    }
