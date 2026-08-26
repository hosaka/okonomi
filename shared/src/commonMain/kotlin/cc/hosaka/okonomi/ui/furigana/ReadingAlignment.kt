package cc.hosaka.okonomi.ui.furigana

/**
 * Splitting a word against its reading so the reading can be set over
 * the characters it belongs to.
 *
 * This is ours, not vendored: the renderer draws readings it is handed
 * and never derives them, and no source we ship states which part of a
 * reading belongs to which kanji. What the dictionary does give is the
 * kana of the word itself, and those are the anchors — 食べる and たべる
 * agree on べる, so 食 takes what is left.
 *
 * Where the kana do not settle it, the whole word takes the whole
 * reading as one unit. 大人 is おとな with no anchor to divide it, and
 * any split invents a reading the dictionary never claimed: a coarse
 * ruby is right, a confident wrong one teaches something false.
 * Splitting a run of several kanji per character is out of scope for the
 * same reason — 相殺 takes そうさい whole.
 */

/**
 * [word] split into the runs its [reading] is drawn over.
 *
 * Kana that already agree are matched in place and carry no ruby;
 * everything else takes the reading between its neighbours.
 *
 * Two invariants hold, and the weaker one is the one callers may rely
 * on. The segments always spell [word] back **exactly**. Their
 * readings — a segment's own text where it has none — spell [reading]
 * back only up to the folding the matching allows: プー太郎 against
 * ぷうたろう keeps its プー, so the readings say プーたろう. What always
 * holds is that they are the same **length**, character for character,
 * which is what lets a caller map an offset in either onto the segments.
 * (An unalignable pair takes the whole reading, so that holds there too;
 * an empty [reading] is the one case where nothing is claimed at all.)
 */
fun alignReading(word: String, reading: String): List<FuriganaSegment> {
    if (word.isEmpty()) return emptyList()
    if (reading.isEmpty() || word == reading) return listOf(FuriganaSegment(word))
    val runs = runsOf(word)
    // A word made of more runs than this is not a word; the search below
    // is exponential in the number of interior kana runs, and no shipped
    // form comes near the bound.
    if (runs.size > MAX_RUNS) return wholeWord(word, reading)
    if (runs.all { it.isKana }) {
        // Nothing to annotate. A kana word that disagrees with its
        // reading is a data case (a katakana form read as hiragana, say)
        // and takes the reading whole rather than being read over.
        return if (kanaMatches(word, reading)) listOf(FuriganaSegment(word)) else wholeWord(word, reading)
    }
    return alignRuns(runs, reading) ?: wholeWord(word, reading)
}

private fun wholeWord(word: String, reading: String) = listOf(FuriganaSegment(word, reading))

/**
 * The longest word this will try to divide, counted in runs. The
 * longest shipped headword reaches 21 characters and 11 runs
 * (井の中の蛙、大海を知らず); the bound is above that and far below where
 * the search could cost anything.
 */
private const val MAX_RUNS = 24

/** One maximal run of [word] that is either all kana or none of it. */
private class Run(val text: String, val isKana: Boolean)

private fun runsOf(word: String): List<Run> {
    val runs = mutableListOf<Run>()
    var start = 0
    while (start < word.length) {
        val kana = isKana(word[start])
        var end = start + 1
        while (end < word.length && isKana(word[end]) == kana) {
            end++
        }
        runs += Run(word.substring(start, end), kana)
        start = end
    }
    return runs
}

/**
 * Matches the leading and trailing okurigana against the two ends of the
 * reading, then divides what is left between the interior runs. Returns
 * null when the reading does not agree, which is the caller's cue to
 * annotate the whole word instead.
 *
 * The ends are anchored rather than searched for. 意味合い ends in い and
 * so does the *start* of いみあい, and a forward search for the okurigana
 * finds that one and collapses the word.
 */
private fun alignRuns(runs: List<Run>, reading: String): List<FuriganaSegment>? {
    var first = 0
    var last = runs.lastIndex
    var start = 0
    var end = reading.length
    val head = mutableListOf<FuriganaSegment>()
    val tail = mutableListOf<FuriganaSegment>()

    if (runs[first].isKana) {
        val text = runs[first].text
        if (end - start < text.length) return null
        if (!kanaMatches(text, reading.substring(start, start + text.length))) return null
        head += FuriganaSegment(text)
        start += text.length
        first++
    }
    if (last >= first && runs[last].isKana) {
        val text = runs[last].text
        if (end - start < text.length) return null
        if (!kanaMatches(text, reading.substring(end - text.length, end))) return null
        tail += FuriganaSegment(text)
        end -= text.length
        last--
    }
    // Stripping the ends leaves a list that starts and ends with a run
    // needing a reading, with single kana runs between them.
    val middle = alignInterior(runs.subList(first, last + 1), reading, start, end) ?: return null
    return head + middle + tail
}

/**
 * The one way to divide `[from, to)` of the reading between runs that
 * alternate needing a reading and reading themselves, starting and
 * ending with one that needs a reading.
 *
 * "The one way" is the whole rule. Where a kana run occurs twice in the
 * reading the candidates are each carried through to the end, and only
 * a candidate that leaves every run a reading of its own survives:
 * 行き先/いきさき has two き to anchor on, but anchoring on the second
 * leaves 先 with nothing, so the first is not a guess. Where two
 * candidates both survive — 五つ子 is いつ+つ+ご or い+つ+つご, and the
 * reading does not say which — nothing is claimed and the caller
 * annotates the whole word.
 */
private fun alignInterior(
    runs: List<Run>,
    reading: String,
    from: Int,
    to: Int,
): List<FuriganaSegment>? = divisions(runs, 0, reading, from, to).singleOrNull()

/**
 * Every valid division, given up on as soon as a second is found: past
 * that the answer is "ambiguous" however many more there are.
 */
private fun divisions(
    runs: List<Run>,
    index: Int,
    reading: String,
    position: Int,
    to: Int,
): List<List<FuriganaSegment>> {
    val annotated = runs[index]
    val okurigana = runs.getOrNull(index + 1)
        // The last run takes what is left, and must be left something it
        // could actually be read as.
        ?: return if (to > position && isReadable(reading, position, to)) {
            listOf(listOf(FuriganaSegment(annotated.text, reading.substring(position, to))))
        } else {
            emptyList()
        }

    val found = mutableListOf<List<FuriganaSegment>>()
    // The run before the okurigana must take at least one kana, so the
    // first place the okurigana could sit is one past where we are.
    for (candidate in (position + 1)..(to - okurigana.text.length)) {
        val end = candidate + okurigana.text.length
        if (!kanaMatches(okurigana.text, reading.substring(candidate, end))) continue
        if (!isReadable(reading, position, candidate)) continue
        val head = listOf(
            FuriganaSegment(annotated.text, reading.substring(position, candidate)),
            FuriganaSegment(okurigana.text),
        )
        for (tail in divisions(runs, index + 2, reading, end, to)) {
            found += head + tail
            if (found.size > 1) return found
        }
    }
    return found
}

/**
 * Whether `reading[from, to)` could be a reading of anything at all.
 *
 * A reading is made of morae, and no mora opens with a sokuon, a moraic
 * n, a small kana or a length mark — those continue the mora before
 * them, which under this division belongs to the neighbouring run.
 * 泣き面/なきっつら divides as 泣=な and 面=っつら only because the kana
 * happen to line up; っつら is not a reading, and the whole-word ruby
 * (なきっつら over 泣き面) is the honest answer.
 */
private fun isReadable(reading: String, from: Int, to: Int): Boolean {
    if (from >= to) return false
    return toHiragana(reading[from]) !in NON_INITIAL_KANA
}

/** Kana that continue the mora before them and so cannot open one. */
private const val NON_INITIAL_KANA = "っんゃゅょぁぃぅぇぉゎゕゖー゛゜"

/**
 * Whether two kana sequences are the same reading. Katakana is folded to
 * hiragana, so ドイツ語's ドイツ matches the ドイツ of its reading however
 * either is written, and a prolonged sound mark matches the vowel of the
 * mora it lengthens, so プー太郎 aligns against ぷうたろう.
 */
private fun kanaMatches(word: CharSequence, reading: CharSequence): Boolean {
    if (word.length != reading.length) return false
    return word.indices.all { index ->
        kanaCharMatches(
            word = toHiragana(word[index]),
            reading = toHiragana(reading[index]),
            // The mark stands for the vowel of what precedes it, which
            // has to be the same character on both sides — matching ー
            // against any vowel at all would let ソース align to そうす.
            previous = if (index > 0) toHiragana(word[index - 1]) else null,
        )
    }
}

private fun kanaCharMatches(word: Char, reading: Char, previous: Char?): Boolean {
    if (word == reading) return true
    val lengthened = vowelOf(previous) ?: return false
    return (word == PROLONGED_SOUND_MARK && reading == lengthened) ||
        (reading == PROLONGED_SOUND_MARK && word == lengthened)
}

private const val PROLONGED_SOUND_MARK = 'ー'

/**
 * The vowel [kana] ends on, or null when it has none to lengthen (ん, a
 * sokuon, a mark, a non-kana character).
 *
 * Written out by row rather than derived from the codepoint: the
 * gojūon's order is regular within a row but the rows themselves are
 * not evenly spaced once the voiced and small forms are interleaved.
 */
private fun vowelOf(kana: Char?): Char? {
    if (kana == null) return null
    return VOWEL_ROWS.entries.firstOrNull { (_, row) -> kana in row }?.key
}

private val VOWEL_ROWS: Map<Char, String> = mapOf(
    'あ' to "あかさたなはまやらわがざだばぱゃゎぁ",
    'い' to "いきしちにひみりゐぎじぢびぴぃ",
    'う' to "うくすつぬふむゆるぐずづぶぷゔゅぅ",
    'え' to "えけせてねへめれゑげぜでべぺぇ",
    'お' to "おこそとのほもよろをごぞどぼぽょぉ",
)

/**
 * The katakana block mirrors hiragana at a fixed offset, up to ヶ.
 *
 * Halfwidth katakana is deliberately not folded, and [isKana] does not
 * admit it either, so the two agree: a halfwidth form is a run needing a
 * reading and takes a correct whole-word ruby. Four shipped headwords
 * are written that way (the net-slang ﾀﾋ), which is not enough to carry
 * a folding table for.
 */
private fun toHiragana(char: Char): Char = when (char.code) {
    in 0x30A1..0x30F6 -> (char.code - 0x60).toChar()
    else -> char
}

/**
 * Whether [char] is kana that can be expected to read as itself.
 *
 * ヵ and ヶ are deliberately excluded. They are written as small katakana
 * but read か, が or こ — 何ヶ月 is なんかげつ, 霞ヶ関 is かすみがせき —
 * so treating them as ordinary kana would look for a match that is never
 * there and quietly collapse the word. Counted as needing a reading,
 * they fall inside the run beside them and the whole thing takes the
 * reading it is given. The iteration marks ゝゞ (8 headwords) are outside
 * these ranges for the same reason and by the same accident.
 */
private fun isKana(char: Char): Boolean = when (char.code) {
    0x30F5, 0x30F6 -> false

    in 0x3041..0x3096, // hiragana, marks and iteration marks excluded
    in 0x30A0..0x30FF, // katakana, the prolonged sound mark included
    -> true

    else -> false
}
