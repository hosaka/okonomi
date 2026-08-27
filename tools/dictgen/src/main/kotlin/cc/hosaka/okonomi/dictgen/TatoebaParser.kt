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

/**
 * One word of a sentence as the database stores it.
 *
 * [surface] is the word as this sentence writes it, and is null when
 * the sentence writes the headword unchanged. It is what locates the
 * word in the sentence text: the breakdown holds dictionary forms, so
 * nothing else says where 食べる sits in 父は果物を食べないんです。
 */
data class StoredBreakdownWord(
    val headword: String,
    val reading: String?,
    val surface: String?,
    val entryId: Long?,
)

/**
 * The word breakdown as the `sentence.breakdown` column holds it:
 * space-separated `headword(reading){surface}#entryId` words, with the
 * reading, the surface and the entry id each omitted when there is
 * none.
 *
 * It is a rewrite of Tatoeba's B-line rather than the B-line itself,
 * for two reasons:
 *
 * - **Every kanji word carries a reading.** The source states one for
 *   only 200,691 of its 717,800 kanji words (28%), because it states
 *   readings to tell homographs apart, not to make the line readable.
 *   Storing the B-line verbatim would leave 72% of kanji words bare and
 *   the reading aid failing at the one job it exists for, so a word
 *   without a stated reading takes the one from the entry the
 *   disambiguation cascade already resolved it to. That resolution is
 *   what makes 行った read いった rather than おこなった.
 * - **The sense index is dropped.** Nothing reads it: the tab links a
 *   sentence to an entry, not to one of its senses.
 *
 * The surface, on the other hand, is kept — it was dropped until the
 * furigana rework and is the whole reason this format moved. The app
 * sets the readings over the sentence itself, and the only thing that
 * says which characters of もっと果物を食べるべきです。are the 食べる the
 * breakdown holds is the surface Tatoeba states for it. Written only
 * where it differs from the headword, which is the minority case, so
 * the column grows by very little.
 *
 * The entry id is kept although the reader never sees it: every word is
 * already resolved here, and carrying the id is what lets the app ask
 * what part of speech a word was linked as and leave the sentence's
 * grammar words inert.
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

    const val SURFACE_OPEN = '{'

    const val SURFACE_CLOSE = '}'

    const val ENTRY_ID_PREFIX = '#'

    /**
     * The characters this encoding gives a meaning to, and which
     * therefore may not appear inside a headword, a reading or a
     * surface.
     */
    private val DELIMITERS = charArrayOf(
        WORD_SEPARATOR,
        READING_OPEN,
        READING_CLOSE,
        SURFACE_OPEN,
        SURFACE_CLOSE,
        ENTRY_ID_PREFIX,
    )

    /**
     * Encodes one sentence's words.
     *
     * The format carries no escape sequence, so it depends on no
     * headword, reading or surface containing a delimiter. That holds in
     * the shipped data — the B-line grammar uses the parentheses, the
     * braces and the space as delimiters itself, and no `reading` or
     * `kanji_form` row in JMdict contains one either — and this checks
     * it rather than assuming it: a future release that broke the
     * property would otherwise corrupt every breakdown carrying the
     * offending word, silently and unrecoverably.
     *
     * A blank reading encodes as no reading at all. `語()` would render
     * to the reader as `語 ()`, which is worse than the bare word. A
     * surface equal to the headword encodes as no surface, because it
     * says nothing the headword did not.
     */
    fun encode(words: List<StoredBreakdownWord>): String = words.joinToString(WORD_SEPARATOR.toString()) { word ->
        buildString {
            append(checkEncodable(word.headword, "headword"))
            word.reading
                ?.takeIf { it.isNotBlank() }
                ?.let { append(READING_OPEN).append(checkEncodable(it, "reading")).append(READING_CLOSE) }
            word.surface
                ?.takeIf { it.isNotBlank() && it != word.headword }
                ?.let { append(SURFACE_OPEN).append(checkEncodable(it, "surface")).append(SURFACE_CLOSE) }
            word.entryId?.let { append(ENTRY_ID_PREFIX).append(it) }
        }
    }

    /**
     * How many of [words] the app will be able to find in [japanese],
     * and a check that the spans it finds tile the sentence in order.
     *
     * The twin of `SentenceDetail.kt`'s `locateTokens`, written out here
     * for the same reason the format is: the two modules cannot share
     * code, and the property the app's whole rendering rests on — that
     * the pieces spell the stored sentence back exactly — is a property
     * of THIS data, checkable only where the data is. The app has 210 MB
     * of it and no way to assert over it; the generator has it in hand
     * one sentence at a time.
     *
     * A mismatch between this and the app's scan shows up as a count
     * that moved, which is what [DbWriter.counts] reports it for.
     */
    fun locate(japanese: String, words: List<StoredBreakdownWord>): Int {
        val folded = widthFolded(japanese)
        var located = 0
        var cursor = 0
        words.forEach { word ->
            val written = word.surface?.takeIf { it != word.headword } ?: word.headword
            if (written.isEmpty()) return@forEach
            val start = folded.indexOf(widthFolded(written), cursor)
            if (start < 0) return@forEach
            val end = start + written.length
            if (start < cursor || end > japanese.length) {
                throw PipelineException(
                    "Locating \"$written\" in \"$japanese\" produced the span [$start, $end), which " +
                        "runs backwards or past the end. The app renders the sentence from these spans, " +
                        "so it would render something other than the sentence.",
                )
            }
            located++
            cursor = end
        }
        return located
    }

    /** Full-width ASCII folded onto ASCII, length for length. See the app-side twin. */
    private fun widthFolded(text: String): String {
        if (text.none { it.code in FULL_WIDTH_ASCII }) return text
        return buildString(text.length) {
            text.forEach { append(if (it.code in FULL_WIDTH_ASCII) (it.code - FULL_WIDTH_OFFSET).toChar() else it) }
        }
    }

    private val FULL_WIDTH_ASCII = 0xFF01..0xFF5E

    private const val FULL_WIDTH_OFFSET = 0xFEE0

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
     *
     * The surface comes across untouched except that one equal to the
     * headword is dropped: it is the sentence's own spelling of the
     * word, and the app locates the word by scanning the sentence for
     * exactly it.
     */
    fun storedWord(token: BLineToken): StoredBreakdownWord {
        // Resolved once. Only a token the cascade cannot place is a
        // rescue candidate, and only a rescued one is resolved again.
        val direct = resolve(token)
        val token = if (direct != null) token else rescueUnresolvable(token)
        val entryId = direct ?: resolve(token)
        val reading = when {
            !containsKanji(token.headword) -> null
            token.reading != null -> token.reading
            else -> entryId?.let { readingFor(it, token.headword) }
        }
        return StoredBreakdownWord(
            headword = token.headword,
            reading = reading,
            surface = token.surface?.takeIf { it != token.headword },
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
    /**
     * A headword the dictionary cannot carry, rewritten to one it can.
     *
     * Two defects in the shipped index account for almost all of it,
     * and both leave a token that is a real word on screen but finds
     * nothing when tapped, so the reader gets a prefix-truncated search
     * for a word the app itself put in front of them:
     *
     * - **[PROPER_NOUN_MARKER] is a marker, not a word.** The index
     *   writes `NI{ヤフオク}`, `NI{西遊記}`, `NI{カルピス}` — the code
     *   marks the token as a proper noun and the actual word is the
     *   surface. Measured on the shipped index: 235 tokens, 142 distinct
     *   surfaces, 194 of those occurrences findable as a word or a name
     *   once the surface is used.
     * - **A particle glued to the word.** `になる` is the big one at
     *   2,559 occurrences; `も肉`, `ノートを`, `温度が` and `彼と` are
     *   the same defect in ones and twos. The particle is stripped only
     *   when what remains is a word the dictionary actually carries, so
     *   this can rescue a token and never redirect one.
     *
     * Deliberately narrow. The caller runs it ONLY for a token the
     * resolve cascade could not place, and `resolve` returns null only
     * when the dictionary carries the headword nowhere — so a token
     * that works today cannot be reached by this at all. Rewriting the
     * headword is safe for the app's word-locating scan because the
     * surface — the sentence's own spelling — is untouched: `になる`
     * becomes `なる` while the sentence is still scanned for `になりました`.
     *
     * What it does NOT try to fix, measured and left alone: 119
     * occurrences across 30 texts that are genuine phrase fragments
     * (`関する限り`, `よくある事だが`) or compositional compounds
     * (`三日間`, `何年間`). There is no dictionary entry to send those
     * to, so they keep the search's own truncated-prefix fallback,
     * which flags itself on screen.
     */
    private fun rescueUnresolvable(token: BLineToken): BLineToken {
        if (token.headword == PROPER_NOUN_MARKER) {
            val surface = token.surface
            // No surface leaves nothing to rescue it with; the marker
            // itself must never reach the reader as a word.
            if (surface != null) return token.copy(headword = surface)
            return token
        }
        val stripped = GLUED_PARTICLES.firstNotNullOfOrNull { particle ->
            token.headword.removeGluedParticle(particle)?.takeIf { candidatesFor(it).isNotEmpty() }
        }
        return if (stripped == null) token else token.copy(headword = stripped)
    }

    /**
     * [headword] without [particle] at either end, or null when it does
     * not carry it. The whole token is never consumed: a headword that
     * IS the particle is the particle, not a word wearing one.
     */
    private fun String.removeGluedParticle(particle: String): String? = when {
        length <= particle.length -> null
        startsWith(particle) -> drop(particle.length)
        endsWith(particle) -> dropLast(particle.length)
        else -> null
    }

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

/**
 * The index's own code for "this token is a proper noun". It is not a
 * Japanese word and must never be stored as one; the word it stands for
 * is the token's surface.
 */
private const val PROPER_NOUN_MARKER = "NI"

/**
 * Single-character particles the index glues to a word.
 *
 * Single-character only, deliberately. Multi-character sequences (`には`,
 * `では`) occur nowhere in the shipped index's unresolvable tokens, and
 * attempting them would mean choosing whether `特には` is `特` or `特に`
 * — a judgement no measurement here supports. They are left out rather
 * than guessed at; if a future index ships them, that is the moment to
 * decide with data in hand.
 *
 * A token strippable more than one way takes the first particle listed.
 * That is arbitrary but fixed, so the generated database does not depend
 * on iteration order; [EntryIndexRescueTest] pins it.
 */
private val GLUED_PARTICLES = listOf("に", "を", "が", "は", "と", "で", "も", "や", "へ")

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
