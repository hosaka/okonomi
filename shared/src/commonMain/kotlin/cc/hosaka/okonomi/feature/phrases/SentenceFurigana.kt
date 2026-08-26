package cc.hosaka.okonomi.feature.phrases

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.lang.conjugate
import cc.hosaka.okonomi.lang.conjugationClassOf
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import cc.hosaka.okonomi.ui.furigana.alignReading
import cc.hosaka.okonomi.ui.furigana.transferReading

/**
 * One stretch of a rendered sentence: the runs to draw, and the word
 * they are, when they are one.
 *
 * A null [word] is the text between two words — punctuation, a particle
 * the index never named, anything the scan could not place. It is drawn
 * exactly as the sentence writes it and nothing can be done with it.
 */
@Immutable
internal data class SentencePiece(
    val segments: List<FuriganaSegment>,
    val word: BreakdownWord?,
)

/**
 * [sentence] cut into the pieces the tab draws, with the readings set
 * over the words that could take them.
 *
 * The sentence is rendered VERBATIM. Every piece's text is a substring
 * of `sentence.japanese`, taken in order and covering all of it, so
 * concatenating the pieces spells the stored sentence back exactly.
 * That is the property this function exists to keep: rebuilding the
 * line out of the breakdown's headwords instead would rewrite
 * もっと果物を食べるべきです。 as …食べる可きです, because 可き is what the
 * dictionary calls べき and the sentence is what Tatoeba actually holds.
 *
 * [entryPos] is the part-of-speech codes of the entries the words were
 * linked to, keyed by entry id — the same answer the tappable-word rule
 * reads, put to a second use here. It is what tells 来る from 食べる, and
 * without it neither can be told from the other; see [surfaceSegments].
 *
 * Failure is per word, never per sentence. A word the scan could not
 * place is already absent from `sentence.tokens` and simply leaves its
 * characters in a plain piece; a word whose reading cannot be shown to
 * belong to its surface keeps its span and its tap and takes no ruby.
 * Either way the rest of the sentence is unaffected.
 */
internal fun sentencePieces(
    sentence: ExampleSentence,
    entryPos: Map<Long, List<String>> = emptyMap(),
): List<SentencePiece> {
    val japanese = sentence.japanese
    val pieces = mutableListOf<SentencePiece>()
    var cursor = 0
    sentence.tokens.forEach { token ->
        if (token.start > cursor) {
            pieces += plainPiece(japanese.substring(cursor, token.start))
        }
        // Cut from the sentence rather than taken from the word: the two
        // are the same string by construction, and the sentence is the
        // one that is being rendered.
        pieces += SentencePiece(
            segments = surfaceSegments(
                word = token.word,
                written = japanese.substring(token.start, token.end),
                entryPos = entryPos,
            ),
            word = token.word,
        )
        cursor = token.end
    }
    if (cursor < japanese.length) {
        pieces += plainPiece(japanese.substring(cursor))
    }
    return pieces
}

private fun plainPiece(text: String) = SentencePiece(
    segments = listOf(FuriganaSegment(text)),
    word = null,
)

/**
 * How [written] — the characters this sentence spells [word] with —
 * reads, in the strongest evidence available, and plain when none of it
 * reaches.
 *
 * The order is the order of how much is being claimed:
 *
 * 1. **The sentence writes the headword.** The dictionary stated the
 *    reading for exactly these characters; [alignReading] divides it.
 * 2. **The surface is a form of the word's paradigm.** Conjugating the
 *    reading through the same paradigm gives that form's own reading,
 *    and the two are aligned against each other — 来る/くる produces
 *    来ない/こない, so 来 reads こ there and く nowhere near it. This is
 *    the only thing that can get an irregular verb right, and it is why
 *    the pos codes are carried this far.
 * 3. **The paradigm shows the stem shifting, and the surface is not one
 *    of its forms.** 来 on its own is not a row of anything; with 来
 *    reading く, き and こ across the table there is no reading to give
 *    it, so it takes none.
 * 4. **Nothing says otherwise.** [transferReading] carries what the two
 *    spellings agree on, which is right for every word whose stem does
 *    not shift and is most of the corpus.
 *
 * Step 2 is `feature/forms/ConjugationFurigana.kt`'s evidence put to
 * the opposite use. That file drops the readings of stems that never
 * shift, because a table repeating 食=た on fourteen rows teaches
 * nothing; this one keeps them and refuses the ones that do. The two
 * ask different questions of the same paradigm, which is why the
 * pairing is stated in both rather than shared: neither's answer is
 * usable to the other.
 *
 * With no pos codes — the entry resolved to nothing, or the
 * part-of-speech query failed, which is the same run that leaves
 * nothing tappable — steps 2 and 3 are unavailable and a surface falls
 * to step 4. That is right for every word whose stem does not shift,
 * which is all of them but 来る and 為る, and those two are then read
 * as their dictionary forms: 来ない would draw 来[く]ない. It is the one
 * path on this tab that can still put a wrong reading on screen, and
 * it is pinned as such in `SentenceFuriganaTest` rather than left to be
 * discovered.
 */
private fun surfaceSegments(
    word: BreakdownWord,
    written: String,
    entryPos: Map<Long, List<String>>,
): List<FuriganaSegment> {
    // A word written in kana already reads as itself, which is every
    // particle and most inflected endings.
    val reading = word.reading ?: return listOf(FuriganaSegment(written))
    if (written == word.text) return alignReading(word.text, reading)
    val codes = word.entryId?.let { entryPos[it] }.orEmpty()
    var sawParadigm = false
    var stemShifts = false
    codes.forEach { code ->
        val pairs = conjugatedPairs(word.text, reading, code)
        if (pairs.isEmpty()) return@forEach
        sawParadigm = true
        val matched = pairs.firstOrNull { (formText, _) -> formText == written }
        if (matched != null) return alignReading(matched.first, matched.second)
        if (!stemsHoldAcross(word.text, reading, pairs)) stemShifts = true
    }
    if (sawParadigm && stemShifts) return listOf(FuriganaSegment(written))
    return transferReading(word = word.text, reading = reading, surface = written)
}

/**
 * Every (written form, its reading) pair one paradigm produces for
 * [word], or none when the two cannot be conjugated side by side.
 *
 * The guards are the ones `ConjugationFurigana.alignedRows` states for
 * the same reason: a reading that does not inflect as its written form
 * does (`conjugationClassOf` disagreeing) or a paradigm that produced a
 * different set of rows for the two would pair a form with a reading of
 * some other form, which is a wrong reading rather than a missing one.
 */
private fun conjugatedPairs(word: String, reading: String, code: String): List<Pair<String, String>> {
    val conjugationClass = conjugationClassOf(code, word) ?: return emptyList()
    if (conjugationClassOf(code, reading) != conjugationClass) return emptyList()
    val written = conjugate(word, code)
    val read = conjugate(reading, code)
    if (written.isEmpty() || written.size != read.size) return emptyList()
    return buildList {
        written.zip(read).forEach { (writtenForm, readingForm) ->
            if (writtenForm.id != readingForm.id) return emptyList()
            add(writtenForm.affirmative to readingForm.affirmative)
            val writtenNegative = writtenForm.negative
            val readingNegative = readingForm.negative
            if (writtenNegative != null && readingNegative != null) {
                add(writtenNegative to readingNegative)
            }
        }
    }
}

/**
 * Whether every run [word] would hand to a surface reads the same
 * everywhere the paradigm writes those characters.
 *
 * 食べる gives 食=た and every row of the table agrees, so a surface the
 * table does not list — 食べさせて, 食べながら — can still be given た.
 * 来る gives 来=く and the same table reads it き and こ, so no surface
 * may be given く on the strength of the dictionary form alone.
 *
 * A row the aligner could not divide is skipped rather than counted, on
 * `ConjugationFurigana`'s reasoning: it arrives as one segment covering
 * the whole form, which is not a stem and says nothing about one.
 */
private fun stemsHoldAcross(word: String, reading: String, pairs: List<Pair<String, String>>): Boolean {
    val stated = alignReading(word, reading)
        .mapNotNull { segment -> segment.reading?.let { segment.text to it } }
        .toMap()
    if (stated.isEmpty()) return true
    return pairs.all { (form, formReading) ->
        val cell = alignReading(form, formReading)
        if (cell.size == 1 && cell.single().reading != null) {
            true
        } else {
            cell.all { segment ->
                val claimed = stated[segment.text]
                claimed == null || segment.reading == null || segment.reading == claimed
            }
        }
    }
}
