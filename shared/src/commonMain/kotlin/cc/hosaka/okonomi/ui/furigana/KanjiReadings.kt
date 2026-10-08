package cc.hosaka.okonomi.ui.furigana

import androidx.compose.runtime.Immutable

/**
 * What kanjidic says each kanji can read as, reduced to the kana a
 * reading of it could put into a word — the evidence that lets
 * [alignReading] divide a run of several kanji per character.
 *
 * Only on and kun readings count. Nanori are name readings, and they
 * admit splits no ordinary word has: 大 takes た as a name, which would
 * let 大気 read た+いき beside the true たい+き.
 *
 * Built once per process from the whole `kanji_reading` table (see
 * `db/KanjiReadingsLoad.kt`) and never changed afterwards, so one value
 * can be handed to every screen and compared by identity. That identity
 * is relied on: equality is deliberately not overridden, so the
 * `remember` keys of every furigana site, and the Forms tab's screen
 * state key (which uses [hashCode]), tell one loaded value from another
 * without comparing thirteen thousand entries.
 */
@Immutable
class KanjiReadings private constructor(
    private val candidates: Map<String, Set<String>>,
) {
    /** The kana [literal] can read as, or null when kanjidic gives none. */
    internal fun of(literal: String): Set<String>? = candidates[literal]

    /** Collects kanjidic rows as they come out of the database. */
    class Builder {
        private val candidates = mutableMapOf<String, MutableSet<String>>()

        /**
         * Adds one `kanji_reading` row. Rows of any type other than on
         * and kun, and rows that reduce to nothing, are dropped here.
         */
        fun add(kanji: String, type: String, text: String): Builder {
            val candidate = candidateOf(type, text)
            if (candidate != null) candidates.getOrPut(kanji) { mutableSetOf() } += candidate
            return this
        }

        fun build(): KanjiReadings = KanjiReadings(candidates.mapValues { (_, readings) -> readings.toSet() })
    }
}

/**
 * The part of a kanjidic reading that a word writes over the kanji
 * itself, in hiragana.
 *
 * On readings are katakana (ガク) and are folded. Kun readings mark
 * their okurigana after a dot (た.べる) — the kanji reads only what is
 * before it — and mark an affix with a hyphen (-か, おお-), which says
 * where the reading attaches, not what it reads.
 */
internal fun candidateOf(type: String, text: String): String? {
    val reading = when (type) {
        ON_READING -> text
        KUN_READING -> text.substringBefore('.')
        else -> return null
    }
    val kana = reading.replace("-", "").map(::toHiragana).joinToString("")
    return kana.ifEmpty { null }
}

private const val ON_READING = "on"
private const val KUN_READING = "kun"

/**
 * [run] — a run of kanji [alignReading] gave [reading] whole — cut into
 * one segment per character, or null when the readings do not settle it.
 *
 * A cut is made only when exactly one way to spend [reading] across the
 * characters exists, each character taking one of its own candidates.
 * That is the same rule the kana anchors live by: 動物園/どうぶつえん
 * cuts as どう+ぶつ+えん and nothing else, while 合気/あいき is あい+き or
 * あ+いき and is left whole. A character kanjidic says nothing about —
 * ヶ, a digit, a kanji outside it — leaves the whole run whole, and so
 * does a reading no combination spells, which is where every jukujikun
 * lands (大人/おとな, 刑事/でか).
 *
 * The candidates bend the two ways compounds bend them, and only where
 * they can: rendaku voices the first kana of any character but the first
 * (手紙 is て+がみ, 一杯's 杯 is ぱい), and gemination turns the last つ,
 * く, き or ち of any but the last into っ (学校 is がっ+こう). 々 reads as
 * the character before it does, voiced or not — 人々 is ひと+びと.
 *
 * The segments' readings are cut from [reading] itself, so they spell it
 * back exactly; the matching folds katakana, nothing else.
 *
 * Known limits, each of which leaves the run whole rather than divided
 * wrongly, because no candidate spells what the word reads:
 * - renjō, where a final ん or t carries into the next kanji's opening
 *   vowel, when kanjidic does not list the carried form — 天皇 てんのう
 *   (皇 is only こう and おう), 三位 さんみ (位 is い), 陰陽師 おんみょうじ.
 *   Where it does list one (応's -ノウ, 音's -ノン, 縁's -ネン), 反応,
 *   観音 and 因縁 divide;
 * - any other irregular sound change beyond voicing and the sokuon
 *   above — 雪隠 せっちん.
 * Half-voicing after ん is not among them: 散歩 さんぽ divides, since ぱ
 * row voicing is one of the bends allowed.
 *
 * The search is memoized on (character, position), so it is polynomial
 * in the length of the run however many candidates each character has;
 * see [divisionsFrom].
 */
internal fun KanjiReadings.divide(run: String, reading: String): List<FuriganaSegment>? {
    val characters = charactersOf(run)
    if (characters.size < 2) return null
    val folded = reading.map(::toHiragana).joinToString("")
    val options = characters.indices.map { index ->
        optionsAt(characters, index) ?: return null
    }
    val division = divisionsFrom(options, folded, index = 0, position = 0, memo = mutableMapOf())
    val lengths = (division as? Divisions.One)?.lengths ?: return null
    var start = 0
    return characters.zip(lengths) { character, length ->
        FuriganaSegment(character, reading.substring(start, start + length)).also { start += length }
    }
}

/**
 * The kana the character at [index] may read as here: its own
 * candidates, voiced when it is not first, and geminated when it is
 * not last. Null when there are none to try.
 */
private fun KanjiReadings.optionsAt(characters: List<String>, index: Int): Set<String>? {
    val literal = literalAt(characters, index) ?: return null
    val own = of(literal) ?: return null
    val voiced = if (index > 0) own + own.flatMap(::rendakuOf) else own
    return if (index < characters.lastIndex) voiced + voiced.mapNotNull(::geminatedOf) else voiced
}

/** The character whose readings [index] takes: itself, or what 々 repeats. */
private fun literalAt(characters: List<String>, index: Int): String? {
    var at = index
    while (at >= 0 && characters[at] == ITERATION_MARK) at--
    return if (at < 0) null else characters[at]
}

private const val ITERATION_MARK = "々"

/** How many ways a stretch of the reading divides, counted only as far as it matters. */
private sealed interface Divisions {
    data object None : Divisions

    /** Exactly one way, as the length each remaining character takes. */
    class One(val lengths: List<Int>) : Divisions

    data object Many : Divisions
}

/**
 * How `reading[position, end)` divides among `options[index..]`.
 *
 * Distinct lengths are what count, since two candidates spelling the
 * same kana (a voiced variant that is also a reading of its own) are one
 * division, not two; and a different first length always makes a
 * different division, so the counts of the branches simply add, capped
 * at "more than one".
 *
 * Each (index, position) is answered once and remembered. Without that
 * the search backtracks through every combination of candidates before
 * giving up on a run that has no division at all, which on a long enough
 * run never finishes — and this runs inside a composable's `remember`,
 * on the main thread.
 */
private fun divisionsFrom(
    options: List<Set<String>>,
    reading: String,
    index: Int,
    position: Int,
    memo: MutableMap<Int, Divisions>,
): Divisions {
    if (index == options.size) {
        return if (position == reading.length) Divisions.One(emptyList()) else Divisions.None
    }
    val key = index * (reading.length + 1) + position
    memo[key]?.let { return it }
    var result: Divisions = Divisions.None
    val lengths = options[index]
        .filter { candidate -> reading.startsWith(candidate, position) }
        .map { it.length }
        .distinct()
    for (length in lengths) {
        val rest = divisionsFrom(options, reading, index + 1, position + length, memo)
        result = when {
            rest == Divisions.None -> result
            rest == Divisions.Many || result != Divisions.None -> Divisions.Many
            else -> Divisions.One(listOf(length) + (rest as Divisions.One).lengths)
        }
        if (result == Divisions.Many) break
    }
    memo[key] = result
    return result
}

/**
 * [cells] — each aligned with no kanji readings — divided per kanji by
 * [kanjiReadings] only if every run of several kanji in every cell
 * divides; otherwise all of them exactly as they were.
 *
 * For callers that compare one word's runs across several spellings of
 * it: a conjugation table's rows, or a headword against its forms. Each
 * cell's run is divided against that cell's own reading, so one row's
 * stem can divide (頭為る: あたま+す) while another's has no division (頭為
 * reading あたまし, since し is not a kanjidic reading of 為) and stays
 * whole. Compared, a divided row and a whole one look like a stem whose
 * reading shifts, or like none at all. All or nothing keeps the
 * comparison like for like.
 *
 * A cell aligned as one segment carrying the whole reading is left out
 * of the test: it is the aligner's fallback or a cell with no okurigana,
 * and both callers already skip such cells as saying nothing about a
 * stem.
 */
internal fun divideAlike(
    cells: List<List<FuriganaSegment>>,
    kanjiReadings: KanjiReadings?,
): List<List<FuriganaSegment>> {
    if (kanjiReadings == null) return cells
    val divided = cells.map { cell ->
        if (cell.size == 1 && cell.single().reading != null) return@map cell
        cell.flatMap { segment ->
            val reading = segment.reading
            if (reading == null || charactersOf(segment.text).size < 2) {
                listOf(segment)
            } else {
                kanjiReadings.divide(segment.text, reading) ?: return cells
            }
        }
    }
    return divided
}

/** [reading] with its first kana voiced, in every way it can be. */
private fun rendakuOf(reading: String): List<String> {
    val first = reading.first()
    val rest = reading.substring(1)
    return buildList {
        RENDAKU_VOICED[first]?.let { add(it + rest) }
        RENDAKU_SEMI_VOICED[first]?.let { add(it + rest) }
    }
}

private val RENDAKU_VOICED: Map<Char, Char> =
    "かきくけこさしすせそたちつてとはひふへほ".zip("がぎぐげござじずぜぞだぢづでどばびぶべぼ").toMap()

private val RENDAKU_SEMI_VOICED: Map<Char, Char> = "はひふへほ".zip("ぱぴぷぺぽ").toMap()

/**
 * [reading] with its last kana closed into a sokuon, or null when that
 * kana never closes. A one-kana reading cannot close: it would leave the
 * character reading only っ.
 */
private fun geminatedOf(reading: String): String? =
    if (reading.length > 1 && reading.last() in GEMINATING_KANA) reading.dropLast(1) + "っ" else null

private const val GEMINATING_KANA = "つくきち"

/** [text] as its characters, a surrogate pair counted as one. */
private fun charactersOf(text: String): List<String> = buildList {
    var index = 0
    while (index < text.length) {
        val end = if (text[index].isHighSurrogate() && index + 1 < text.length) index + 2 else index + 1
        add(text.substring(index, end))
        index = end
    }
}
