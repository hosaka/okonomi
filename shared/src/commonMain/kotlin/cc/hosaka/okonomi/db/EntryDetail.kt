package cc.hosaka.okonomi.db

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.lang.toRomaji
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
 * One reading of an entry, with its wapuro-Hepburn romaji and the kanji
 * forms it is restricted to (empty when it reads every form).
 */
@Immutable
data class EntryReading(
    val text: String,
    val romaji: String,
    val restrictions: List<String>,
    val isCommon: Boolean,
)

/**
 * One sense of an entry: its display labels (pos, misc, field and dial
 * codes resolved through `tag_label` in the source's own casing,
 * unknown codes kept verbatim), its English glosses, the editorial note
 * from `s_inf`, and the forms or readings the sense is restricted to
 * (`stagk`/`stagr`).
 */
@Immutable
data class EntrySense(
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
) {
    /** The written forms other than the headword, in source order. */
    val alternateForms: List<EntryForm>
        get() = forms.drop(1)
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
 * Hydrates one entry: its forms, its readings with restrictions and
 * romaji, and its senses with labels, glosses and notes. Returns null
 * for an id that is not in the dictionary — a route pushed with a stale
 * id is an error state, never a crash.
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
            romaji = toRomaji(row.text),
            restrictions = StoredValues.restrictions(row.restrictions),
            isCommon = row.is_common != 0L,
        )
    }
    coroutineContext.ensureActive()
    val senseRows = db.entryQueries.sensesForEntry(entryId).awaitList()
    val glosses = db.entryQueries.glossesForEntries(listOf(entryId)).awaitList().groupBy { it.sense_id }
    val labels = labelsFor(senseRows.flatMap { tagCodesOf(it.pos, it.misc, it.field_, it.dial) })
    val senses = senseRows
        .map { row ->
            EntrySense(
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

private suspend fun DictionaryDatabase.labelsFor(codes: List<String>): Map<String, String> {
    val distinct = codes.distinct()
    if (distinct.isEmpty()) return emptyMap()
    return db.tagQueries.labelsForCodes(distinct).awaitList().associate { it.code to it.label }
}
