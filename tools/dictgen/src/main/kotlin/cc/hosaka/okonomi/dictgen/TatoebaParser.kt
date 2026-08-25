package cc.hosaka.okonomi.dictgen

import java.io.File

/**
 * One word of a Tatoeba B-line.
 *
 * The grammar is `headword(reading)[sense](#ent_seq){surface}~`, with
 * everything after the headword optional:
 *
 * - `(reading)` is the word's kana reading, EXCEPT when it opens with
 *   `#`, in which case it is JMdict's own `ent_seq` number. The two
 *   never appear together in the shipped index.
 * - `[sense]` is the one-based JMdict sense the word is used in. Parsed
 *   so the grammar is stated in full, and deliberately unused: the tab
 *   links a sentence to an entry, not to one of its senses.
 * - `{surface}` is the inflected form as the sentence actually writes
 *   it; absent when the sentence writes the headword unchanged.
 * - `~` marks THIS WORD as checked by a Tatoeba editor, not the whole
 *   sentence: 26,329 index lines carry the mark on some of their words
 *   and only 24 on all of them.
 *
 * This is the SOURCE grammar, read at build time only. What the app
 * stores and renders is [StoredBreakdown], which resolves each word to
 * an entry and gives every kanji word a reading — the source supplies
 * one for just 28% of them, because it states readings to disambiguate
 * rather than to be read.
 */
data class BLineToken(
    val headword: String,
    val reading: String?,
    val entrySeq: Long?,
    val senseIndex: Int?,
    val surface: String?,
    val checked: Boolean,
)

/** The B-line grammar, in the one place dictgen states it. */
object BLine {

    /**
     * The annotations are matched as a repeated alternation rather than
     * in a fixed order: one token in the shipped index writes its sense
     * before its reading (`仮令[01](たとえ){たとえ}`), and pinning the order
     * would drop it.
     */
    private val TOKEN = Regex("""^([^(\[{~]+)((?:\([^)]*\)|\[\d+])*)(?:\{([^}]*)})?(~?)$""")

    private val ANNOTATION = Regex("""\(([^)]*)\)|\[(\d+)]""")

    private const val ENTRY_SEQ_PREFIX = '#'

    /**
     * The words of one B-line, in sentence order, and however many of
     * its words the grammar rejected.
     *
     * A rejected word is skipped rather than failing the line — the
     * breakdown is an aid to reading the sentence above it, and losing
     * one word of it is a far smaller loss than losing the example
     * entirely — but the count is reported so a grammar that stopped
     * matching cannot pass silently. One word of the shipped 1,178,504
     * is rejected today (`か如何か{かどうか}{かどう}`, two surface forms).
     */
    fun parse(line: String): BLineWords {
        val tokens = ArrayList<BLineToken>()
        var rejected = 0
        line.split(WORD_SEPARATOR).forEach { token ->
            if (token.isEmpty()) return@forEach
            val parsed = parseToken(token)
            if (parsed == null) rejected++ else tokens += parsed
        }
        return BLineWords(tokens, rejected)
    }

    private fun parseToken(token: String): BLineToken? {
        val match = TOKEN.matchEntire(token) ?: return null
        var reading: String? = null
        var entrySeq: Long? = null
        var senseIndex: Int? = null
        var repeated = false
        ANNOTATION.findAll(match.groupValues[2]).forEach { annotation ->
            val parenthesised = annotation.groups[1]?.value
            val bracketed = annotation.groups[2]?.value
            when {
                parenthesised == null -> {
                    if (senseIndex != null) repeated = true
                    senseIndex = bracketed?.toIntOrNull()
                }

                parenthesised.startsWith(ENTRY_SEQ_PREFIX) -> {
                    if (entrySeq != null) repeated = true
                    entrySeq = parenthesised.drop(1).toLongOrNull()
                }

                else -> {
                    if (reading != null) repeated = true
                    reading = parenthesised
                }
            }
        }
        // A word stating one kind of annotation twice (`端(はし)(はた)`)
        // has no single answer to which reading it means. Taking the
        // last one silently is the shape of bug that only shows up as a
        // wrong reading on screen, so the word is rejected instead.
        if (repeated) return null
        return BLineToken(
            headword = match.groupValues[1],
            reading = reading,
            entrySeq = entrySeq,
            senseIndex = senseIndex,
            surface = match.groups[3]?.value,
            checked = match.groupValues[4].isNotEmpty(),
        )
    }

    const val WORD_SEPARATOR = ' '
}

/** The words of one B-line, with the count the grammar rejected. */
data class BLineWords(
    val tokens: List<BLineToken>,
    val rejected: Int,
)

/** True for a word written with at least one Han character. */
internal fun containsKanji(text: String): Boolean =
    text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.HAN }

/** One word of a sentence as the database stores it. */
data class StoredBreakdownWord(
    val headword: String,
    val reading: String?,
    val entryId: Long?,
)

/**
 * The word breakdown as the `sentence.breakdown` column holds it:
 * space-separated `headword(reading)#entryId` words, the reading and
 * the entry id each omitted when there is none.
 *
 * It is a rewrite of Tatoeba's B-line rather than the B-line itself,
 * for two reasons:
 *
 * - **Every kanji word carries a reading.** The source states one for
 *   only 200,691 of its 717,800 kanji words (28%), because it states
 *   readings to tell homographs apart, not to make the line readable.
 *   Storing the B-line verbatim would leave 72% of kanji words bare and
 *   the breakdown failing at the one job it exists for, so a word
 *   without a stated reading takes the one from the entry the
 *   disambiguation cascade already resolved it to. That resolution is
 *   what makes 行った read いった rather than おこなった.
 * - **The sense index and the inflected surface form are dropped.**
 *   Nothing reads them: the breakdown shows dictionary forms, and the
 *   sentence above it already shows the surface. Dropping them pays for
 *   most of what the injected readings and entry ids cost.
 *
 * The entry id is kept although nothing reads it today: every word is
 * already resolved here, and carrying the id is what will let a later
 * increment make the words tappable without regenerating the database.
 *
 * `SentenceDetail.kt` reads this format back on the app side. The two
 * are separate modules and cannot share the code, so
 * [cc.hosaka.okonomi.dictgen.StoredBreakdownTest] and its app-side twin
 * assert the same canonical line from both ends.
 */
object StoredBreakdown {

    const val WORD_SEPARATOR = ' '

    const val READING_OPEN = '('

    const val READING_CLOSE = ')'

    const val ENTRY_ID_PREFIX = '#'

    /**
     * The characters this encoding gives a meaning to, and which
     * therefore may not appear inside a headword or a reading.
     */
    private val DELIMITERS = charArrayOf(WORD_SEPARATOR, READING_OPEN, READING_CLOSE, ENTRY_ID_PREFIX)

    /**
     * Encodes one sentence's words.
     *
     * The format carries no escape sequence, so it depends on no
     * headword or reading containing a delimiter. That holds in the
     * shipped data — the B-line grammar uses the parentheses and the
     * space as delimiters itself, and no `reading` or `kanji_form` row
     * in JMdict contains one either — and this checks it rather than
     * assuming it: a future release that broke the property would
     * otherwise corrupt every breakdown carrying the offending word,
     * silently and unrecoverably.
     *
     * A blank reading encodes as no reading at all. `語()` would render
     * to the reader as `語 ()`, which is worse than the bare word.
     */
    fun encode(words: List<StoredBreakdownWord>): String = words.joinToString(WORD_SEPARATOR.toString()) { word ->
        buildString {
            append(checkEncodable(word.headword, "headword"))
            word.reading
                ?.takeIf { it.isNotBlank() }
                ?.let { append(READING_OPEN).append(checkEncodable(it, "reading")).append(READING_CLOSE) }
            word.entryId?.let { append(ENTRY_ID_PREFIX).append(it) }
        }
    }

    private fun checkEncodable(value: String, part: String): String {
        val offending = value.indexOfAny(DELIMITERS)
        if (offending >= 0) {
            throw PipelineException(
                "Breakdown $part \"$value\" contains '${value[offending]}', which the stored format " +
                    "uses as a delimiter. The encoding has no escape for it; add one, or exclude the word.",
            )
        }
        return value
    }
}

/**
 * One reading of an entry, with the two things that decide whether it
 * belongs to a given written form: JMdict's `re_nokanji`, which marks a
 * reading that is not a reading of the kanji forms at all (タベル for
 * 食べる), and `re_restr`, which ties a reading to particular ones.
 */
data class IndexedReading(
    val text: String,
    val noKanji: Boolean,
    val restrictions: List<String>,
)

/**
 * The entry side of the link: which dictionary entry a B-line word
 * names, and how that entry reads. Built from the entries dictgen has
 * already written, so the ids it hands back are guaranteed to exist.
 */
class EntryIndex(
    private val byKanjiForm: Map<String, List<Long>>,
    private val byReading: Map<String, List<Long>>,
    private val commonRank: Map<Long, Long>,
    /**
     * Required, not defaulted: "every kanji word carries a reading" is
     * this module's headline rule, and an omitted map would drop it
     * everywhere while every other behaviour still looked right.
     */
    private val readings: Map<Long, List<IndexedReading>>,
) {

    /**
     * How [headword] reads as part of [entryId]: the entry's first
     * reading that could belong to that written form.
     *
     * Both of JMdict's qualifiers are honoured, because ignoring them
     * hands the reader a reading that is not one. A `re_nokanji`
     * reading is a katakana rendering of the word rather than a reading
     * of its kanji, and a restricted reading belongs only to the forms
     * it names — 上手 reads じょうず or うわて depending on nothing the
     * spelling shows, and 十八番 reads おはこ for one form alone.
     *
     * Falls back to the first unrestricted-by-nokanji reading when the
     * headword is not one of the entry's written forms (a token that
     * resolved through its `(#ent_seq)` may name a spelling the entry
     * does not carry), and to null for the handful of entries that
     * state no usable reading at all.
     */
    fun readingFor(entryId: Long, headword: String): String? {
        val candidates = readings[entryId].orEmpty().filterNot { it.noKanji }
        return candidates
            .firstOrNull { it.restrictions.isEmpty() || headword in it.restrictions }
            ?.text
            ?: candidates.firstOrNull()?.text
    }

    /**
     * The word as the breakdown stores it: the headword as the
     * dictionary writes it, a reading for every word written with
     * kanji, and the entry the cascade resolved it to.
     *
     * A stated reading wins over the entry's own — the source states it
     * precisely where the entry's primary reading would be wrong for
     * this sentence (二十歳 as はたち, not にじゅっさい). A word with no
     * kanji in it needs no reading and gets none, which is the rule
     * that keeps どんな and は clean.
     */
    fun storedWord(token: BLineToken): StoredBreakdownWord {
        val entryId = resolve(token)
        val reading = when {
            !containsKanji(token.headword) -> null
            token.reading != null -> token.reading
            else -> entryId?.let { readingFor(it, token.headword) }
        }
        return StoredBreakdownWord(
            headword = token.headword,
            reading = reading,
            entryId = entryId,
        )
    }

    /**
     * The entry [token] names, or null when the dictionary carries no
     * entry for its headword (4,202 of the shipped index's 1,178,504
     * tokens — the sentence still links through its other words).
     *
     * The cascade, in this order:
     *
     * 1. `(#ent_seq)` when present. It is JMdict's own sequence number
     *    and therefore our `entry.id` directly, so it settles the token
     *    outright — every one of the shipped 62,239 resolves.
     * 2. The headword's candidates narrowed by the parenthesised
     *    reading, when it narrows them to something.
     * 3. The best (lowest) `common_rank` among whatever is left, ties
     *    broken by id so the choice is stable across runs.
     */
    fun resolve(token: BLineToken): Long? {
        val stated = token.entrySeq
        if (stated != null && stated in commonRank) return stated
        val candidates = candidatesFor(token.headword)
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()
        val narrowed = token.reading
            ?.let { reading -> byReading[reading]?.toSet() }
            ?.let { withReading -> candidates.filter { it in withReading } }
            ?.takeIf { it.isNotEmpty() }
            ?: candidates
        return narrowed.minWithOrNull(
            compareBy({ commonRank[it] ?: Long.MAX_VALUE }, { it }),
        )
    }

    /**
     * A headword may be written in kanji or in kana, and the index does
     * not know which: both tables are consulted and their candidates
     * unioned, keeping the kanji forms' order first.
     */
    private fun candidatesFor(headword: String): List<Long> {
        val forms = byKanjiForm[headword].orEmpty()
        val readings = byReading[headword].orEmpty()
        return when {
            readings.isEmpty() -> forms
            forms.isEmpty() -> readings
            else -> (forms + readings).distinct()
        }
    }
}

/** One usable Japanese/English pair with the B-line that indexes it. */
data class TatoebaSentence(
    val id: Long,
    val japanese: String,
    val english: String,
    val breakdown: String,
)

/**
 * What one pass over the index produced, and why the rest of it did
 * not. Reported by the pipeline: a source whose shape changed would
 * otherwise generate an empty Phrases tab and a successful build.
 */
data class TatoebaStats(
    val rows: Long,
    val pairs: Long,
    /** Rows the reader could not split into three columns. */
    val malformedRows: Long,
    /** Rows naming eng_id 0, which is Tatoeba's "no translation". */
    val untranslated: Long,
    val missingJapanese: Long,
    val missingEnglish: Long,
    /** Rows whose sentence text is present but empty. */
    val blank: Long,
) {
    val skipped: Long
        get() = rows - pairs
}

/**
 * Streams Tatoeba's Japanese/English sentence pairs.
 *
 * The index states a `(jpn_id, eng_id, B-line)` triple per row; a row
 * survives only when both ids resolve to non-empty text, which 147,705
 * of the shipped 150,075 do. The 2,370 that do not divide into 1,629
 * naming eng_id 0 (Tatoeba's "no translation"), 203 naming a Japanese
 * sentence the file does not carry, and 538 naming an English one it
 * does not.
 *
 * [TatoebaSentence.id] is this parser's own sequence rather than
 * Tatoeba's sentence id. Tatoeba's would be ambiguous in principle —
 * 1,128 Japanese sentences appear in more than one index row — though
 * in the shipped data every one of those extra rows names eng_id 0 and
 * so is dropped anyway. The sequence is future-proofing against a
 * release where they are not.
 *
 * Each sentence file is read once and filtered down to the ids the
 * index actually names, which matters most for `eng_sentences.tsv`: 2
 * million rows of which some 139,000 are ever referenced. The index is
 * therefore read twice, once for the ids and once for the pairs.
 */
class TatoebaParser(
    private val japanese: File,
    private val english: File,
    private val indices: File,
) {

    fun parse(onSentence: (TatoebaSentence) -> Unit): TatoebaStats {
        val japaneseIds = HashSet<String>()
        val englishIds = HashSet<String>()
        var malformedRows = 0L
        var rows = 0L
        forEachIndexRow(
            onMalformed = { malformedRows++ },
        ) { japaneseId, englishId, _ ->
            rows++
            japaneseIds += japaneseId
            englishIds += englishId
        }
        // Both files are filtered by the same pass: the Japanese one is
        // smaller but there is no reason to hold 100,000 sentences the
        // index never names.
        val japaneseText = readSentences(japanese, japaneseIds)
        val englishText = readSentences(english, englishIds)
        var id = 0L
        var untranslated = 0L
        var missingJapanese = 0L
        var missingEnglish = 0L
        var blank = 0L
        forEachIndexRow(onMalformed = {}) { japaneseId, englishId, breakdown ->
            val japaneseSentence = japaneseText[japaneseId]
            val englishSentence = englishText[englishId]
            when {
                englishId == NO_TRANSLATION -> untranslated++
                japaneseSentence == null -> missingJapanese++
                englishSentence == null -> missingEnglish++
                // A zero-length Japanese sentence would sort ahead of
                // every real one and become the first example on every
                // entry it touched.
                japaneseSentence.isBlank() || englishSentence.isBlank() -> blank++
                else -> onSentence(
                    TatoebaSentence(
                        id = ++id,
                        japanese = japaneseSentence,
                        english = englishSentence,
                        breakdown = breakdown,
                    ),
                )
            }
        }
        return TatoebaStats(
            rows = rows,
            pairs = id,
            malformedRows = malformedRows,
            untranslated = untranslated,
            missingJapanese = missingJapanese,
            missingEnglish = missingEnglish,
            blank = blank,
        )
    }

    private fun forEachIndexRow(
        onMalformed: () -> Unit,
        onRow: (japaneseId: String, englishId: String, breakdown: String) -> Unit,
    ) {
        indices.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isEmpty()) return@forEach
                // Limited to three: the B-line is the last column and a
                // tab inside it must not truncate the row.
                val columns = line.split(COLUMN_SEPARATOR, limit = INDEX_COLUMNS)
                if (columns.size < INDEX_COLUMNS) {
                    onMalformed()
                    return@forEach
                }
                onRow(columns[0], columns[1], columns[2])
            }
        }
    }

    /** `id⇥lang⇥text`, keyed by id, keeping only the rows [wanted] names. */
    private fun readSentences(file: File, wanted: Set<String>): Map<String, String> =
        file.bufferedReader().useLines { lines ->
            val texts = HashMap<String, String>()
            lines.forEach { line ->
                val columns = line.split(COLUMN_SEPARATOR, limit = SENTENCE_COLUMNS)
                if (columns.size < SENTENCE_COLUMNS) return@forEach
                if (columns[0] in wanted) texts[columns[0]] = columns[2]
            }
            texts
        }

    private companion object {
        const val COLUMN_SEPARATOR = "\t"
        const val SENTENCE_COLUMNS = 3
        const val INDEX_COLUMNS = 3
        const val NO_TRANSLATION = "0"
    }
}
