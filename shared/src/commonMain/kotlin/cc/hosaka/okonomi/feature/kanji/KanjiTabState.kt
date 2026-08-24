package cc.hosaka.okonomi.feature.kanji

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.KanjiCharacter

@Immutable
data class KanjiTabState(
    val content: KanjiTabContentState = KanjiTabContentState.Loading,
)

/**
 * The body of the entry view's Kanji tab. [NoKanji] is its own state
 * rather than an empty [Ready]: a headword with no Han characters — kana
 * alone, but also the fullwidth latin ones JMdict carries — is settled
 * before any query runs, so the reader can never be shown that message
 * because a load came back empty. A null [Error.onRetry] means retrying
 * is not offered, following the project's "null callback is a disabled
 * action" rule.
 */
@Immutable
sealed interface KanjiTabContentState {
    data object Loading : KanjiTabContentState

    data object NoKanji : KanjiTabContentState

    data class Ready(
        val characters: List<KanjiCharacter>,
    ) : KanjiTabContentState

    data class Error(
        val onRetry: (() -> Unit)? = null,
    ) : KanjiTabContentState
}
