package cc.hosaka.okonomi.feature.forms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import cc.hosaka.okonomi.db.loadTagLabels
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.lang.Conjugation
import cc.hosaka.okonomi.lang.conjugations
import cc.hosaka.okonomi.ui.furigana.KanjiReadings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    // The initial state is the finished table, headed by the JMdict
    // code: there is nothing to wait for, so the first frame is already
    // the answer and only the heading arrives later.
    val initial = remember(base, reading, posCodes, kanjiReadings) {
        FormsTabState(content = formsContent(base, reading, posCodes, labels = emptyMap(), kanjiReadings))
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
 * work over the entry the screen already has, so the first emission is
 * already the finished table. The class names are a separate,
 * best-effort read layered on top — a heading is not worth a spinner,
 * an error body, or dropping the app-wide dictionary handle, so a
 * failure here leaves the JMdict code as the heading and says nothing
 * else about it.
 */
suspend fun ScreenStateScope.formsTabStateProducer(
    base: String,
    reading: String?,
    posCodes: List<String>,
    kanjiReadings: KanjiReadings? = null,
    load: suspend (List<String>) -> Map<String, String> = { loadTagLabels(it) },
): Flow<FormsTabState> {
    val conjugations = conjugations(base, posCodes)
    if (conjugations.isEmpty()) {
        return flowOf(FormsTabState(content = notConjugable(posCodes)))
    }
    // Null means "not looked up yet", which is what stops a second run
    // of the producer from re-reading labels the first run already has
    // — including the legitimately empty result of a dictionary
    // generated without tag_label rows.
    val labels = mutablePersistedFlow<Map<String, String>?>(
        key = "forms-labels",
        initial = null,
    )
    return flow {
        coroutineScope {
            launch {
                if (labels.value == null) {
                    labels.value = loadLabels(load, conjugations.map { it.code })
                }
            }
            emitAll(
                labels.map { resolved ->
                    FormsTabState(
                        content = FormsTabContentState.Ready(
                            conjugations.tables(reading, resolved.orEmpty(), kanjiReadings),
                        ),
                    )
                },
            )
        }
    }
}

/**
 * Best effort by design. Cancellation still unwinds the scope; anything
 * else leaves the headings as codes, which is a strictly smaller loss
 * than the table it would otherwise replace.
 */
private suspend fun loadLabels(
    load: suspend (List<String>) -> Map<String, String>,
    codes: List<String>,
): Map<String, String> = try {
    load(codes)
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    emptyMap()
}

private fun formsContent(
    base: String,
    reading: String?,
    posCodes: List<String>,
    labels: Map<String, String>,
    kanjiReadings: KanjiReadings?,
): FormsTabContentState {
    val conjugations = conjugations(base, posCodes)
    return if (conjugations.isEmpty()) {
        notConjugable(posCodes)
    } else {
        FormsTabContentState.Ready(conjugations.tables(reading, labels, kanjiReadings))
    }
}

private fun notConjugable(posCodes: List<String>) =
    FormsTabContentState.NotConjugable(takesSuru = posCodes.contains(SURU_NOUN_CODE))

/**
 * A class whose code the label table does not know — or has not
 * returned yet — is headed by the code itself: an unlabelled table
 * would be worse than a cryptic one, and a withheld table worse still.
 */
private fun List<Conjugation>.tables(
    reading: String?,
    labels: Map<String, String>,
    kanjiReadings: KanjiReadings?,
): List<ConjugationTable> = map { conjugation ->
    ConjugationTable(
        className = labels[conjugation.code] ?: conjugation.code,
        rows = conjugationRows(conjugation, reading, kanjiReadings),
    )
}
