package cc.hosaka.okonomi.feature.radical

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.KanjiHit
import cc.hosaka.okonomi.feature.navigation.state.LoadState

/**
 * The radical screen's contract: which radical is being shown, the
 * kanji built from it, and the two things a reader can do with them.
 *
 * There is no query here and no way to put one here. The screen is
 * reached only by tapping a radical, and [radical] arrives on the route
 * rather than through an editable field, which is what keeps the word
 * search out of this feature entirely.
 *
 * [kanji] is the shared [LoadState] rather than a state of its own: one
 * load, kept for the life of the screen, whose only failure story is the
 * retry [LoadState.Error] carries. An empty [LoadState.Ready] is not an
 * error — the grid shows its own note for a radical nothing joins to.
 *
 * **Both callbacks are non-null, against this app's usual rule that a
 * null callback is a disabled action.** Neither has a disabled case:
 * back is the only way off a screen that the navigation bar hides itself
 * for and that iOS gives no system control for, and a cell exists to be
 * tapped. Nullable, they would have been null in the initial state the
 * screen renders before the first load reports — a dead back arrow for
 * the first frames — so [radicalState] builds them from the hosting
 * controller for that state too.
 */
@Immutable
data class RadicalState(
    val radical: String,
    val onBack: () -> Unit,
    val onKanjiClick: (String) -> Unit,
    val kanji: LoadState<List<KanjiHit>> = LoadState.Loading,
)
