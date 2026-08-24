package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.EntryDetail

@Immutable
data class EntryState(
    val entryId: Long,
    val content: EntryContentState = EntryContentState.Loading,
)

/**
 * The body of the entry view. [Error] covers both a load failure and an
 * id no entry carries: neither is actionable for the reader beyond
 * trying again, and both must leave the screen (and its back
 * affordance) standing. A null [Error.onRetry] means retrying is not
 * offered, following the project's "null callback is a disabled
 * action" rule.
 */
@Immutable
sealed interface EntryContentState {
    data object Loading : EntryContentState

    data class Ready(
        val entry: EntryDetail,
    ) : EntryContentState

    data class Error(
        val onRetry: (() -> Unit)? = null,
    ) : EntryContentState
}
