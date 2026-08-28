package cc.hosaka.okonomi.db

import androidx.compose.runtime.Immutable
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One written form of an entry. */
@Immutable
data class EntryForm(
    val text: String,
    val isCommon: Boolean,
)

/**
 * One reading of an entry, with the kanji forms it is restricted to
 * (empty when it reads every form) and JMdict's `re_nokanji`, which
 * says the reading belongs to no written form at all.
 */
@Immutable
data class EntryReading(
    val text: String,
    val restrictions: List<String>,
    val isCommon: Boolean,
    val noKanji: Boolean = false,
)

/**
 * Whether a reading stated with these [restrictions] and [noKanji] is a
 * reading of [form] — the rule for pairing a written form with the
 * reading drawn over it, wherever that pairing is made.
 *
 * Two things in JMdict say a reading is not one of a given form, and
 * both matter on screen because the ruby asserts a reading the entry
 * does not state:
 *
 * - `re_nokanji` (779 entries) marks a reading attached to no written
 *   form: 刻々's ギザギザ, 空オケ's カラオケ. Set over the kanji it is
 *   simply wrong — the actual readings are ぎざぎざ and からオケ.
 * - `re_restr` names the forms a reading does belong to. 叢立ち and
 *   総立ち share an entry, and そうだち is 総立ち's alone.
 *
 * A null [form] is a word written in kana, where the reading is the
 * headword and there is nothing to restrict it against.
 */
fun readingAppliesTo(form: String?, restrictions: List<String>, noKanji: Boolean): Boolean = when {
    form == null -> true
    noKanji -> false
    restrictions.isEmpty() -> true
    else -> form in restrictions
}

/**
 * One sense of an entry: its display labels (pos, misc, field and dial
 * codes resolved through `tag_label` in the source's own casing,
 * unknown codes kept verbatim), its English glosses, the editorial note
 * from `s_inf`, and the forms or readings the sense is restricted to
 * (`stagk`/`stagr`).
 *
 * [posCodes] keeps the part-of-speech codes unresolved beside their
 * labels: the Forms tab classifies the entry by code (`v5k`, `adj-ix`),
 * which no label can be parsed back into.
 */
@Immutable
data class EntrySense(
    val posCodes: List<String>,
    val tags: List<String>,
    val glosses: List<String>,
    val info: String?,
    val restrictions: List<String>,
) {
    /** A sense the source left with nothing to show has no block to render. */
    val isEmpty: Boolean
        get() = tags.isEmpty() && glosses.isEmpty() && info.isNullOrBlank() && restrictions.isEmpty()
}

/**
 * Everything the entry view shows for one dictionary entry. [headword]
 * is the primary kanji form, or the first reading for an entry written
 * in kana alone; [forms] carries every written form, the headword
 * included. [isCommon] is the denormalized entry-level commonness the
 * "common word" chip reads.
 */
@Immutable
data class EntryDetail(
    val entryId: Long,
    val headword: String,
    val forms: List<EntryForm>,
    val readings: List<EntryReading>,
    val senses: List<EntrySense>,
    val isCommon: Boolean,
    /** Best (lowest) commonness rank across the entry's forms and readings. */
    val commonRank: Long,
    /**
     * Whether the dictionary carries any example sentence for this
     * entry. Loaded with the entry rather than by the Phrases tab
     * because the TAB BAR needs it: a tab is not shown at all when it
     * would have nothing in it, and that has to be settled before the
     * bar is drawn or it would change shape underneath the reader.
     *
     * The other two hideable tabs need no such flag — whether a word has
     * kanji or conjugates is computable from what is already here.
     */
    val hasSentences: Boolean,
) {
    /** The written forms other than the headword, in source order. */
    val alternateForms: List<EntryForm>
        get() = forms.drop(1)

    /**
     * The reading set over [headword] as furigana: the first reading
     * the entry states as a reading of it (see [readingAppliesTo]), or
     * null when it states none. For a word written in kana alone that
     * is the headword itself.
     */
    val headwordReading: EntryReading?
        get() = readings.getOrNull(headwordReadingIndex)

    /**
     * The readings the Reading section lists: every reading except the
     * one already shown over the headword. An entry with a single
     * reading has nothing left to list, and gets no section at all.
     */
    val otherReadings: List<EntryReading>
        get() {
            val shown = headwordReadingIndex
            return readings.filterIndexed { index, _ -> index != shown }
        }

    private val headwordReadingIndex: Int
        get() {
            // A kana headword IS the first reading, so nothing about it
            // is restricted; a kanji headword takes only a reading the
            // entry states for it.
            val form = if (forms.isEmpty()) null else headword
            return readings.indexOfFirst { readingAppliesTo(form, it.restrictions, it.noKanji) }
        }

    /**
     * Every part-of-speech code the entry states, in sense order and
     * without repeats. The Forms tab reads it to decide which
     * paradigms the entry has: the first sense carrying a conjugable
     * code heads the table, and a second sense in a genuinely
     * different class adds one of its own.
     *
     * A sense's `stagk`/`stagr` restrictions are deliberately not
     * applied. 81 senses in the shipped dictionary restrict a
     * conjugable sense to a subset of the entry's spellings, and in
     * every one of them the restricted and unrestricted senses land in
     * the same conjugation class, so honouring the restriction would
     * change no table today. It would start to matter for an entry
     * whose spellings conjugate differently — the display-form
     * increment is where that becomes visible, and where this should be
     * revisited.
     */
    val posCodes: List<String>
        get() = senses.flatMap { it.posCodes }.distinct()
}

/**
 * Loads one entry off the shared app-lifetime dictionary, or null when
 * no entry carries [entryId].
 */
suspend fun loadEntryDetail(entryId: Long): EntryDetail? {
    val database = dictionary()
    // Same reasoning as searchEntries: no common IO dispatcher exists
    // here and the queries are short-lived, so Default keeps the
    // synchronous SQLite work off the main thread.
    return withContext(Dispatchers.Default) {
        database.loadEntryDetail(entryId)
    }
}

/**
 * Hydrates one entry: its forms, its readings with their restrictions,
 * and its senses with labels, glosses and notes. Returns null for an id
 * that is not in the dictionary — a route pushed with a stale id is an
 * error state, never a crash.
 */
suspend fun DictionaryDatabase.loadEntryDetail(entryId: Long): EntryDetail? {
    val entry = db.entryQueries.entryById(entryId).awaitOneOrNull() ?: return null
    coroutineContext.ensureActive()
    val forms = db.entryQueries.kanjiFormsForEntries(listOf(entryId)).awaitList().map { row ->
        EntryForm(
            text = row.text,
            isCommon = row.is_common != 0L,
        )
    }
    val readings = db.entryQueries.readingsForEntry(entryId).awaitList().map { row ->
        EntryReading(
            text = row.text,
            restrictions = StoredValues.restrictions(row.restrictions),
            isCommon = row.is_common != 0L,
            noKanji = row.no_kanji != 0L,
        )
    }
    coroutineContext.ensureActive()
    val senseRows = db.entryQueries.sensesForEntry(entryId).awaitList()
    val glosses = db.entryQueries.glossesForEntries(listOf(entryId)).awaitList().groupBy { it.sense_id }
    val labels = labelsFor(senseRows.flatMap { tagCodesOf(it.pos, it.misc, it.field_, it.dial) })
    val senses = senseRows
        .map { row ->
            EntrySense(
                posCodes = StoredValues.codes(row.pos),
                tags = tagCodesOf(row.pos, row.misc, row.field_, row.dial)
                    .distinct()
                    // A code the label table does not know is shown as
                    // itself: an unlabelled chip would be worse than a
                    // cryptic one, and a missing chip worse still.
                    .map { code -> labels[code] ?: code },
                // The query orders glosses by (sense, ord) already.
                glosses = glosses[row.id].orEmpty().map { it.text },
                info = row.info,
                restrictions = StoredValues.restrictions(row.restrictions),
            )
        }
        // A sense with nothing in it would render as an unexplained gap.
        .filterNot { it.isEmpty }
    val headword = forms.firstOrNull()?.text ?: readings.firstOrNull()?.text
    // An entry row without a single form or reading cannot be shown at
    // all; the caller renders the same error state as a missing id.
    if (headword == null) return null
    coroutineContext.ensureActive()
    // One index seek, and it decides whether a Phrases tab is drawn at
    // all; see EntryDetail.hasSentences.
    val hasSentences = db.sentenceQueries.entryHasSentences(entryId).awaitOne()
    return EntryDetail(
        entryId = entryId,
        headword = headword,
        forms = forms,
        readings = readings,
        senses = senses,
        // The column the search list reads, rather than a second rule
        // over the forms that would only agree with it by coincidence.
        isCommon = entry.is_common != 0L,
        commonRank = entry.common_rank,
        hasSentences = hasSentences,
    )
}

/**
 * The sense's codes in the order the source states them: part of speech
 * first, then usage, field and dialect.
 */
private fun tagCodesOf(
    pos: String?,
    misc: String?,
    field: String?,
    dial: String?,
): List<String> = StoredValues.codes(pos) +
    StoredValues.codes(misc) +
    StoredValues.codes(field) +
    StoredValues.codes(dial)

/**
 * Reads the `tag_label` rows for [codes] off the shared app-lifetime
 * dictionary. The Forms tab heads each table with the class's own label
 * rather than a second hard-coded code-to-name map; a code the table
 * does not know is simply absent from the result, and the caller shows
 * the code itself.
 */
suspend fun loadTagLabels(codes: List<String>): Map<String, String> {
    if (codes.isEmpty()) return emptyMap()
    val database = dictionary()
    // Same reasoning as loadEntryDetail: no common IO dispatcher exists
    // here and the query is short-lived, so Default keeps the
    // synchronous SQLite work off the main thread.
    return withContext(Dispatchers.Default) {
        database.labelsFor(codes)
    }
}

private suspend fun DictionaryDatabase.labelsFor(codes: List<String>): Map<String, String> {
    val distinct = codes.distinct()
    if (distinct.isEmpty()) return emptyMap()
    return db.tagQueries.labelsForCodes(distinct).awaitList().associate { it.code to it.label }
}
