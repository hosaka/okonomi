package cc.hosaka.okonomi.feature.forms

import cc.hosaka.okonomi.lang.Conjugation
import cc.hosaka.okonomi.lang.Form
import cc.hosaka.okonomi.lang.conjugate
import cc.hosaka.okonomi.lang.conjugationClassOf
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import cc.hosaka.okonomi.ui.furigana.KanjiReadings
import cc.hosaka.okonomi.ui.furigana.RunKey
import cc.hosaka.okonomi.ui.furigana.alignReading
import cc.hosaka.okonomi.ui.furigana.divideAlike
import cc.hosaka.okonomi.ui.furigana.keyed

/**
 * The rows of one table, with furigana on the cells whose stem shifts
 * across it.
 *
 * The readings come from conjugating the entry's *reading* through the
 * same paradigm as its written form and aligning the two row by row:
 * 為る against する gives 為ない/しない, and the alignment hands 為 the し.
 * Nothing here knows any word by name, which is the point — the rule is
 * derived from the table it is applied to.
 *
 * A ruby is kept only where the same characters take different readings
 * somewhere in the table. 食べる reads 食 as た in every row, and a ruby
 * repeating that on fourteen rows teaches nothing; 為る reads 為 as す,
 * し and さ depending on the row, which is exactly what a reader cannot
 * work out and what this tab exists to show. 出来る sits in the same
 * table as 為 and is left plain by the same rule, being constant within
 * it.
 *
 * [kanjiReadings] divides a stem of several kanji per character where
 * kanjidic settles it — but only where it settles it on every row (see
 * `divideAlike`). Each row's stem is divided against that row's own
 * reading, so one row can divide while another has no division at all;
 * then the whole table stays undivided, so rows are only ever compared
 * like for like. Divided, each kanji is compared with the kanji at the
 * same place in the other rows, and one whose reading never shifts drops
 * its ruby by the same rule a whole stem does.
 */
internal fun conjugationRows(
    conjugation: Conjugation,
    reading: String?,
    kanjiReadings: KanjiReadings? = null,
): List<ConjugationRow> {
    val aligned = alignedRows(conjugation, reading)?.dividedAlike(kanjiReadings)
        ?: return conjugation.forms.map { form -> plainRow(form) }
    val varying = varyingStems(aligned)
    return aligned.map { row ->
        ConjugationRow(
            id = row.id,
            affirmative = row.affirmative.keepReadingsOf(varying),
            negative = row.negative?.keepReadingsOf(varying),
        )
    }
}

/**
 * Every row aligned against the reading's own conjugation, or null when
 * the two cannot be paired: no reading, a reading that does not inflect
 * as the written form does, or paradigms that disagree on their rows. A
 * table with no readings to show is a smaller loss than one showing
 * invented ones.
 */
private fun alignedRows(conjugation: Conjugation, reading: String?): List<ConjugationRow>? {
    if (reading == null) return null
    if (conjugationClassOf(conjugation.code, reading) != conjugation.conjugationClass) return null
    val readingForms = conjugate(reading, conjugation.code)
    if (readingForms.size != conjugation.forms.size) return null
    return conjugation.forms.zip(readingForms) { form, readingForm ->
        if (form.id != readingForm.id) return null
        ConjugationRow(
            id = form.id,
            affirmative = alignReading(form.affirmative, readingForm.affirmative),
            // One cell may have a negative while the other does not only
            // if the paradigms disagree, which the row check above has
            // already ruled out.
            negative = form.negative?.let { alignReading(it, readingForm.negative.orEmpty()) },
        )
    }
}

/** The rows with their kanji runs divided per kanji, if they all divide; see `divideAlike`. */
private fun List<ConjugationRow>.dividedAlike(kanjiReadings: KanjiReadings?): List<ConjugationRow> {
    val cells = divideAlike(flatMap { row -> listOfNotNull(row.affirmative, row.negative) }, kanjiReadings)
    var next = 0
    return map { row ->
        ConjugationRow(
            id = row.id,
            affirmative = cells[next++],
            negative = row.negative?.let { cells[next++] },
        )
    }
}

private fun plainRow(form: Form) = ConjugationRow(
    id = form.id,
    affirmative = listOf(FuriganaSegment(form.affirmative)),
    negative = form.negative?.let { listOf(FuriganaSegment(it)) },
)

/**
 * The written runs that take more than one reading somewhere in the
 * table. Keyed by [RunKey], so a stem appearing in several rows is one
 * entry however many rows carry it.
 *
 * A cell the aligner could not divide is skipped rather than counted. It
 * arrives as one segment covering the whole conjugated form, which is
 * not a stem: counted, two such cells would "vary" against each other
 * and put a whole-cell ruby on rows that never shared a stem at all.
 * The two facts — a stem that never shifts, and a cell that could not be
 * divided — both end in a plain cell, but only the first is a statement
 * about the verb.
 */
private fun varyingStems(rows: List<ConjugationRow>): Set<RunKey> {
    val readings = mutableMapOf<RunKey, MutableSet<String>>()
    rows.forEach { row ->
        listOfNotNull(row.affirmative, row.negative).forEach { cell ->
            if (cell.size == 1 && cell.single().reading != null) return@forEach
            cell.keyed().forEach { (key, segment) ->
                val reading = segment.reading ?: return@forEach
                readings.getOrPut(key) { mutableSetOf() } += reading
            }
        }
    }
    return readings.filterValues { it.size > 1 }.keys
}

/**
 * The cell with the readings of constant stems dropped, and the runs
 * that leaves behind rejoined — a cell that ends up with no readings at
 * all is one plain string again, the way it was before the alignment
 * cut it up.
 */
private fun List<FuriganaSegment>.keepReadingsOf(varying: Set<RunKey>): List<FuriganaSegment> {
    val kept = mutableListOf<FuriganaSegment>()
    keyed().forEach { (key, segment) ->
        val annotated = segment.reading != null && key in varying
        val previous = kept.lastOrNull()
        // Runs join only when they are the same run: no reading on
        // either, and the same highlight. This is the one place segments
        // recombine, and a join across a highlight boundary would spread
        // it over text that was never matched.
        val joinable = !annotated &&
            previous != null &&
            previous.reading == null &&
            previous.highlight == segment.highlight
        if (joinable) {
            kept[kept.lastIndex] = previous.copy(text = previous.text + segment.text)
        } else {
            kept += if (annotated) segment else segment.copy(reading = null)
        }
    }
    return kept
}
