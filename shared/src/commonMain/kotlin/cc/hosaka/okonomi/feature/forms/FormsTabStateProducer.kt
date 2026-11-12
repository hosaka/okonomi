package cc.hosaka.okonomi.feature.forms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.lang.Conjugation
import cc.hosaka.okonomi.lang.conjugations
import cc.hosaka.okonomi.ui.furigana.KanjiReadings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * JMdict's "noun or participle which takes the aux. verb suru". Its
 * headword is stored without する (勉強, not 勉強する), so there is no
 * verb here to inflect — see [cc.hosaka.okonomi.lang.conjugationClassOf].
 */
private const val SURU_NOUN_CODE = "vs"

@Composable
fun produceFormsTabState(
    entryId: Long,
    base: String,
    reading: String?,
    posCodes: List<String>,
    kanjiReadings: KanjiReadings?,
): State<FormsTabState> {
    // The initial state is the finished table: there is nothing to wait
    // for, so the first frame is already the answer.
    val initial = remember(base, reading, posCodes, kanjiReadings) {
        FormsTabState(content = formsContent(base, reading, posCodes, kanjiReadings))
    }
    return produceScreenState(
        // Keyed per entry beside the entry's own screen state, so two
        // entries on the same back stack cannot share one tab's tables.
        // Keyed per readings value too: the producer is created once per
        // key and never sees a later argument, so turning per-kanji
        // furigana on or off has to reach a producer of its own to
        // redraw the table without a restart. Keyed on the instance
        // (KanjiReadings keeps identity hashing) rather than on whether
        // there is one, so a different value can never reuse a producer
        // built over another.
        key = "entry-forms-$entryId" + kanjiReadings?.let { "-per-kanji-${it.hashCode()}" }.orEmpty(),
        initial = initial,
    ) {
        formsTabStateProducer(base, reading, posCodes, kanjiReadings)
    }
}

/**
 * The tables are computed, never loaded: conjugation is pure string
 * work over the entry the screen already has, so the one emission is
 * the finished table. Nothing here reads the dictionary. Each table
 * carries its class's code, and the screen heads it with the app's own
 * name for that code (see `cc.hosaka.okonomi.ui.labels.tagLabel`),
 * which every code that names a paradigm has.
 */
suspend fun ScreenStateScope.formsTabStateProducer(
    base: String,
    reading: String?,
    posCodes: List<String>,
    kanjiReadings: KanjiReadings? = null,
): Flow<FormsTabState> = flowOf(FormsTabState(content = formsContent(base, reading, posCodes, kanjiReadings)))

private fun formsContent(
    base: String,
    reading: String?,
    posCodes: List<String>,
    kanjiReadings: KanjiReadings?,
): FormsTabContentState {
    val conjugations = conjugations(base, posCodes)
    return if (conjugations.isEmpty()) {
        notConjugable(posCodes)
    } else {
        FormsTabContentState.Ready(conjugations.tables(reading, kanjiReadings))
    }
}

private fun notConjugable(posCodes: List<String>) =
    FormsTabContentState.NotConjugable(takesSuru = posCodes.contains(SURU_NOUN_CODE))

private fun List<Conjugation>.tables(
    reading: String?,
    kanjiReadings: KanjiReadings?,
): List<ConjugationTable> = map { conjugation ->
    ConjugationTable(
        classCode = conjugation.code,
        rows = conjugationRows(conjugation, reading, kanjiReadings),
    )
}
