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
 * because a load came back empty.
 *
 * **[NoKanji] is not reachable through the tab bar any more.**
 * `availableTabs` hides this tab entirely for a headword with no Han
 * characters, using the very same `hanCharacters` check this state is
 * built from, so a reader cannot arrive here. It is kept rather than
 * deleted because it is the cheaper side of a disagreement: if the
 * visibility rule and this check ever drift apart, the reader gets a
 * sentence explaining the empty tab instead of a blank screen.
 *
 * A null [Error.onRetry] means retrying is not offered, following the
 * project's "null callback is a disabled action" rule.
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
