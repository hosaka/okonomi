package cc.hosaka.okonomi.ui.furigana

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import cc.hosaka.okonomi.prefs.PreferenceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * The kanji readings every furigana on screen divides its kanji runs
 * with, or null to keep them whole: the setting is off, or the readings
 * have not loaded (or could not be). Null draws exactly what the app
 * drew before per-kanji furigana existed.
 *
 * Every composable that aligns a reading reads this and keys its
 * `remember` on it, so turning the setting off and on redraws whatever
 * is on screen without a restart.
 */
val LocalKanjiReadings = staticCompositionLocalOf<KanjiReadings?> { null }

/**
 * Persisted key of Appearance's "Furigana over each kanji" switch. Off
 * keeps every run of kanji under one ruby, as before the switch existed.
 */
internal const val PER_KANJI_FURIGANA_PREFERENCE = "appearance.per_kanji_furigana"

/** On: settled after a device pass on a release build (2026-10-08), where neither the look nor the load cost gave a reason to ship it off. */
internal const val PER_KANJI_FURIGANA_DEFAULT = true

/**
 * Provides [LocalKanjiReadings] to [content]: what [load] returns while
 * the setting in [preferences] is on, null otherwise.
 *
 * Nothing is loaded until the setting is first seen on, and [load] is
 * expected to remember its answer (`appKanjiReadings` does), so turning
 * the switch back on is immediate. A load that fails leaves the runs
 * whole rather than taking a screen down: per-kanji furigana is a
 * refinement, and its absence is what the app looked like anyway.
 *
 * A failure is retried while the setting stays on, after
 * [KANJI_READINGS_RETRY_DELAYS_MILLIS], and then given up on for the
 * session (until the switch is turned off and on). On a first launch the
 * dictionary may still be being copied into place when this first asks,
 * and one failure must not leave every run whole until a restart.
 */
@Composable
fun ProvideKanjiReadings(
    preferences: PreferenceStore,
    load: suspend () -> KanjiReadings,
    content: @Composable () -> Unit,
) {
    // False until the store answers, so a reader who turned the switch
    // off never sees divided runs flash in on a cold start.
    val enabled by remember(preferences) {
        preferences.booleanFlow(PER_KANJI_FURIGANA_PREFERENCE, PER_KANJI_FURIGANA_DEFAULT)
    }.collectAsState(initial = false)
    val readings by produceState<KanjiReadings?>(initialValue = null, enabled, load) {
        // The state outlives a restart of this block, so off has to
        // withdraw what on provided.
        value = null
        if (!enabled) return@produceState
        value = loadOrNull(load)
        for (delayMillis in KANJI_READINGS_RETRY_DELAYS_MILLIS) {
            if (value != null) break
            delay(delayMillis)
            value = loadOrNull(load)
        }
    }
    CompositionLocalProvider(LocalKanjiReadings provides readings) {
        content()
    }
}

/**
 * How long to wait before each retry of a failed load: four retries,
 * backing off, about half a minute in all. Long enough to outlast the
 * first launch's dictionary copy, short enough that a load which will
 * never succeed stops being attempted.
 */
internal val KANJI_READINGS_RETRY_DELAYS_MILLIS = listOf(1_000L, 3_000L, 9_000L, 20_000L)

private suspend fun loadOrNull(load: suspend () -> KanjiReadings): KanjiReadings? = try {
    load()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    null
}
