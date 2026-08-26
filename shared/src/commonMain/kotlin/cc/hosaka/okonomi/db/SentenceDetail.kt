package cc.hosaka.okonomi.db

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One word of a sentence's breakdown line: the word as the dictionary
 * writes it, how it reads here, how this sentence spells it, and the
 * entry dictgen resolved it to.
 *
 * A null [reading] means the word is written in kana already and reads
 * as itself. Every word with kanji in it carries a reading, resolved at
 * build time — that is what makes the sentence legible.
 *
 * A null [surface] means the sentence writes the word exactly as the
 * dictionary does, so [written] is the headword. Where the two differ
 * the sentence's spelling is the inflected one — 食べる seen as 食べない
 * — and it is what locates the word in the line.
 *
 * A null [entryId] means the word resolved to no entry (0.4% of the
 * corpus's words). The id is what the tappable-word rule asks the
 * dictionary about; it is deliberately *not* where a tap goes, because
 * the link itself can be wrong — Tatoeba's index sends は to 葉 — and a
 * search lets the reader correct it. See [BreakdownPos].
 */
@Immutable
data class BreakdownWord(
    val text: String,
    val reading: String?,
    val surface: String? = null,
    val entryId: Long? = null,
) {
    /** The word as this sentence writes it. */
    val written: String
        get() = surface ?: text
}

/**
 * One word of a sentence, located: [word] occupies `[start, end)` of
 * the sentence's `japanese` text.
 *
 * A span rather than the word alone because the sentence is rendered
 * verbatim: the characters between two spans belong to no word and are
 * drawn as they stand, and the characters inside one are what a reading
 * is set over and what a tap lands on.
 */
@Immutable
data class SentenceToken(
    val word: BreakdownWord,
    val start: Int,
    val end: Int,
)

/**
 * One Tatoeba example: the Japanese sentence, its English translation,
 * and the words the sentence is indexed by.
 *
 * [id] is the sentence's own identity in the dictionary, which the list
 * keys on: two entries can show the same sentence, and one entry can
 * show two sentences that read almost alike, so position is not an
 * identity.
 *
 * [tokens] is [words] resolved to positions in [japanese], computed
 * here rather than taken as a parameter so that every way of building a
 * sentence — the loader, a test fixture, a preview — locates its words
 * the same way.
 *
 * Eagerly, and that is the point of putting it here at all: a sentence
 * is built by [loadSentencesForEntry], which runs off the main thread,
 * so the scan is paid for beside the query that produced the text.
 * Deferred to first use it would instead run inside composition, during
 * the layout pass that draws the row.
 */
@Immutable
data class ExampleSentence(
    val id: Long,
    val japanese: String,
    val english: String,
    val words: List<BreakdownWord>,
) {
    val tokens: List<SentenceToken> = locateTokens(japanese, words)
}

/**
 * Where each of [words] sits in [japanese], by scanning forward for one
 * word's written form at a time.
 *
 * A scan rather than a search per word, and that is the whole rule:
 * the words arrive in sentence order, so each one is looked for from
 * where the previous one ended. That is what makes a sentence saying
 * the same word twice — 食べる食べる — two separate spans rather than
 * one span found twice.
 *
 * Comparison is width-folded, and that is not a nicety. The index
 * states ２月 where the sentence writes 2月, and ＡＬＳ where it writes
 * ALS. Unfolded, those words are not merely lost: they leave their
 * characters unclaimed, and a later one-character word — 日, 月, 人 —
 * is then located INSIDE them and takes a reading and a tap belonging
 * to the word that was lost. Dropping a span is harmless; putting a
 * reading on the wrong characters is not, so the fold is what keeps
 * the two failure modes apart. It is length-preserving, so an index
 * into either string is an index into the other.
 *
 * A word the scan still cannot find contributes nothing and is skipped
 * without moving the cursor, so every other word of the sentence is
 * still located and the sentence still renders in full — the frozen
 * rule that failure is per token, never per sentence. Measured over the
 * 126,361 sentences of the regenerated database — the population the
 * app sees, which is the only one worth quoting; the raw index scores
 * worse and is never rendered — the scan places every word of 126,344
 * sentences (99.987%) and 977,985 of their 978,002 words (99.998%),
 * leaving seventeen unplaced in all.
 * What is left is a word absent from its sentence altogether — a
 * variant kanji (鱠 for 膾), a joined index form (ダンス・パーティー against
 * ダンスパーティー), a row naming a word the text does not contain — and
 * a word that is absent takes no characters with it, so nothing after
 * it can be misplaced.
 *
 * No morphological analysis is involved anywhere in this, which is why
 * the app ships no tokenizer: Tatoeba's index already states the
 * surface form of every word it names.
 */
internal fun locateTokens(japanese: String, words: List<BreakdownWord>): List<SentenceToken> {
    val tokens = mutableListOf<SentenceToken>()
    val folded = widthFolded(japanese)
    var cursor = 0
    words.forEach { word ->
        val written = word.written
        if (written.isEmpty()) return@forEach
        val start = folded.indexOf(widthFolded(written), cursor)
        if (start < 0) return@forEach
        tokens += SentenceToken(word = word, start = start, end = start + written.length)
        cursor = start + written.length
    }
    return tokens
}

/**
 * [text] with the full-width ASCII block folded onto ASCII itself, and
 * nothing else touched.
 *
 * Deliberately narrower than a Unicode normalisation. Every codepoint
 * in `U+FF01..U+FF5E` stands for exactly one ASCII character, so the
 * folded string has the same length as the original and an offset in
 * one is an offset in the other. A wider fold — half-width katakana,
 * where ｶﾞ is two characters for one — does not have that property, and
 * an offset that no longer means what it did is precisely how a span
 * lands on the wrong characters.
 */
private fun widthFolded(text: String): String {
    if (text.none { it.code in FULL_WIDTH_ASCII }) return text
    return buildString(text.length) {
        text.forEach { append(if (it.code in FULL_WIDTH_ASCII) (it.code - FULL_WIDTH_OFFSET).toChar() else it) }
    }
}

private val FULL_WIDTH_ASCII = 0xFF01..0xFF5E

private const val FULL_WIDTH_OFFSET = 0xFEE0

/**
 * The stored breakdown format, as the app reads it: space-separated
 * `headword(reading){surface}#entryId` words, each annotation present
 * only where dictgen had one. It is dictgen that writes this (see
 * `TatoebaParser.kt`'s `StoredBreakdown`); the two modules cannot share
 * the code, so each keeps the format in one documented place of its own
 * and both are tested against the same shapes.
 *
 * The headword, the reading and the surface all reach the reader — the
 * surface as the characters the reading is set over. The entry id does
 * not, but the tappable-word rule reads it to ask the dictionary what
 * part of speech the word was linked as.
 *
 * A word this reader does not accept is dropped silently, where dictgen
 * counts the ones ITS grammar rejects and reports them. The asymmetry is
 * not an oversight: dictgen runs once, on a workstation, with somewhere
 * to print to; this runs on a device with nothing to tell. What stands
 * in for the counter is that the two ends are pinned to the same
 * literal — `BreakdownTest.CANONICAL` and dictgen's `StoredBreakdownTest`
 * are the same string typed twice, and it carries all three annotations
 * in order. A dictgen that reordered or renamed them fails there before
 * it can ship a column this reader silently drops.
 */
internal object Breakdown {

    // No anchors: every use goes through matchEntire, which anchors
    // both ends itself.
    //
    // Both braces are escaped, and the closing one has to be. Android
    // compiles patterns with ICU (`com.android.icu.util.regex`), which
    // rejects a bare `}` outright — `PatternSyntaxException` at class
    // init, so the Phrases tab dies the moment it reads a breakdown.
    // The host JVM's `java.util.regex` accepts it as a literal, and the
    // tests run there (Robolectric shadows Android but regex does not
    // go through ICU), so the whole suite passes while the device
    // crashes. Seen 2026-08-26. `[^}]` inside a character class is
    // fine on both; it is the one outside that must be escaped.
    private val WORD = Regex("""([^(#{]+)(?:\(([^)]*)\))?(?:\{([^}]*)\})?(?:#(\d+))?""")

    /**
     * The words of one stored breakdown, in sentence order. A word the
     * format does not accept is skipped rather than failing the
     * sentence: the breakdown locates the readings for the line, and
     * losing one word of it is a far smaller loss than losing the
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
            // An empty surface would locate nothing and, worse, would
            // read as "this sentence spells the word as nothing at all"
            // rather than as the absent annotation it is.
            surface = match.groups[3]?.value?.takeIf { it.isNotEmpty() },
            // toLongOrNull rather than toLong: the group is digits by
            // construction, but a word linked to an id wider than a
            // Long must read as unlinked rather than fail the word.
            entryId = match.groups[4]?.value?.toLongOrNull(),
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
 * One query, capped by the generator, each row's stored breakdown
 * parsed into the words the tab sets its readings from. A parse and no
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
