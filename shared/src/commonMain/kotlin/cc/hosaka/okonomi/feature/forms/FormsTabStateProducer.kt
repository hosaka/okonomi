package cc.hosaka.okonomi.feature.forms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import cc.hosaka.okonomi.db.loadTagLabels
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.lang.Conjugation
import cc.hosaka.okonomi.lang.conjugations
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
    posCodes: List<String>,
): State<FormsTabState> {
    // The initial state is the finished table, headed by the JMdict
    // code: there is nothing to wait for, so the first frame is already
    // the answer and only the heading arrives later.
    val initial = remember(base, posCodes) {
        FormsTabState(content = formsContent(base, posCodes, labels = emptyMap()))
    }
    return produceScreenState(
        // Keyed per entry beside the entry's own screen state, so two
        // entries on the same back stack cannot share one tab's tables.
        key = "entry-forms-$entryId",
        initial = initial,
    ) {
        formsTabStateProducer(base, posCodes)
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
    posCodes: List<String>,
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
                    FormsTabState(content = FormsTabContentState.Ready(conjugations.tables(resolved.orEmpty())))
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
    posCodes: List<String>,
    labels: Map<String, String>,
): FormsTabContentState {
    val conjugations = conjugations(base, posCodes)
    return if (conjugations.isEmpty()) {
        notConjugable(posCodes)
    } else {
        FormsTabContentState.Ready(conjugations.tables(labels))
    }
}

private fun notConjugable(posCodes: List<String>) =
    FormsTabContentState.NotConjugable(takesSuru = posCodes.contains(SURU_NOUN_CODE))

/**
 * A class whose code the label table does not know — or has not
 * returned yet — is headed by the code itself: an unlabelled table
 * would be worse than a cryptic one, and a withheld table worse still.
 */
private fun List<Conjugation>.tables(labels: Map<String, String>): List<ConjugationTable> = map { conjugation ->
    ConjugationTable(
        className = labels[conjugation.code] ?: conjugation.code,
        forms = conjugation.forms,
    )
}
