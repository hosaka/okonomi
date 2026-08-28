package cc.hosaka.okonomi.ui.test

import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.db.EntrySense

/**
 * A dictionary entry for UI tests, defaulting to a plain noun so a test states
 * only the part it cares about. The default entry conjugates into nothing,
 * which is what the Forms tab's empty state needs.
 */
internal fun entryDetail(
    entryId: Long = 1_356_150L,
    headword: String = "本",
    forms: List<EntryForm> = listOf(EntryForm(text = headword, isCommon = true)),
    readings: List<EntryReading> = listOf(
        EntryReading(text = "ほん", restrictions = emptyList(), isCommon = true),
    ),
    senses: List<EntrySense> = listOf(entrySense()),
    isCommon: Boolean = true,
    commonRank: Long = 1L,
    /**
     * True by default so a fixture that says nothing about sentences
     * still gets a Phrases tab, which is what almost every test of the
     * entry view assumes. A test about a tab being HIDDEN says so.
     */
    hasSentences: Boolean = true,
) = EntryDetail(
    entryId = entryId,
    headword = headword,
    forms = forms,
    readings = readings,
    senses = senses,
    isCommon = isCommon,
    commonRank = commonRank,
    hasSentences = hasSentences,
)

internal fun entrySense(
    posCodes: List<String> = listOf("n"),
    tags: List<String> = listOf("noun"),
    glosses: List<String> = listOf("book"),
    info: String? = null,
    restrictions: List<String> = emptyList(),
) = EntrySense(
    posCodes = posCodes,
    tags = tags,
    glosses = glosses,
    info = info,
    restrictions = restrictions,
)
