package cc.hosaka.okonomi.feature.navigation.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The three states of a screen body that loads once: waiting, loaded,
 * or failed with a way to try again.
 *
 * [Error] covers both a load failure and content the screen cannot show
 * (an id nothing carries, say): neither is actionable for the reader
 * beyond trying again, and both must leave the screen standing.
 */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Ready<T>(
        val value: T,
    ) : LoadState<T>

    data class Error(
        val onRetry: () -> Unit,
    ) : LoadState<Nothing>
}

/**
 * Runs [load] once for the life of the screen and keeps its result, so
 * coming back to the screen is instant and only a failure is ever
 * retried. A null result is an [LoadState.Error]: content the screen
 * cannot show leaves the reader in the same place a failure does.
 *
 * [key] namespaces the two persisted flows this owns, so one screen can
 * host several independent loads (the entry view's tabs each own one).
 *
 * Failures never propagate: [invalidate] applies the shared
 * dictionary-failure policy (see [healDictionaryAfter]) and the reader
 * gets an error body with a retry.
 */
suspend fun <T : Any> ScreenStateScope.loadOnce(
    key: String,
    load: suspend () -> T?,
    invalidate: suspend () -> Unit,
): Flow<LoadState<T>> {
    val content = mutablePersistedFlow<LoadState<T>>(
        key = "$key-content",
        initial = LoadState.Loading,
    )
    // Persisted so a retry survives the producer restarting under it,
    // and so a screen that failed while off screen loads again when the
    // reader comes back to it.
    val attempts = mutablePersistedFlow(
        key = "$key-attempts",
        initial = 0,
    )
    // One instance for every failure this run reports. A fresh capturing
    // lambda per emission would make two equal error states compare
    // unequal, so neither the state flow nor the composition could
    // conflate a redundant one away.
    val error = LoadState.Error { attempts.update { it + 1 } }
    return flow {
        coroutineScope {
            // The load runs beside the emission rather than before it, so
            // a retry shows its spinner immediately instead of leaving
            // the old error on screen until the query returns.
            launch {
                attempts.collect {
                    if (content.value !is LoadState.Ready) {
                        content.value = LoadState.Loading
                        content.value = runLoad(load, invalidate, error)
                    }
                }
            }
            emitAll(content)
        }
    }
}

private suspend fun <T : Any> runLoad(
    load: suspend () -> T?,
    invalidate: suspend () -> Unit,
    error: LoadState.Error,
): LoadState<T> = try {
    load()?.let { LoadState.Ready(it) } ?: error
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    healDictionaryAfter(e, invalidate)
    error
}

/**
 * The shared rule for a failed dictionary read.
 *
 * A database failure drops the shared handle so the next attempt
 * reopens it — a cheap heal for a handle gone bad. Wiping the
 * provisioned file is deliberately left to Settings' corrupt-copy path.
 *
 * Programming errors are not database failures: reopening cannot fix a
 * bad argument or a broken invariant, and throwing the handle away
 * would turn one bug into a reprovisioning storm.
 *
 * The heal itself is best effort — the caller is already reporting
 * [failure] and must not be derailed by the heal failing too — but
 * cancellation is rethrown rather than swallowed, so a cancelled scope
 * still unwinds.
 */
internal suspend fun healDictionaryAfter(
    failure: Exception,
    invalidate: suspend () -> Unit,
) {
    if (failure is IllegalArgumentException || failure is IllegalStateException) return
    try {
        invalidate()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // Nothing left to try; the original failure is what the reader sees.
    }
}
