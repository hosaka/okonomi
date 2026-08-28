package cc.hosaka.okonomi.feature.radical

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

/**
 * The kanji built from one radical, as its own screen.
 *
 * A screen rather than a mode of the search, because the two questions
 * share almost nothing: no query editing, no debounce, no names toggle,
 * no paging, no fallback note. [radical] is on the route, so this is
 * reachable only from a radical chip — nothing a reader can type into
 * the search field can arrive here.
 *
 * The value is radkfile's own representative kanji, exactly as the chip
 * showed it and exactly as `kanji_radical.radical` stores it, never a
 * CJK Radical Supplement rewrite of it.
 */
@Serializable
data class RadicalRoute(
    val radical: String,
) : Route {
    @Composable
    override fun Content() {
        val state = produceRadicalScreenState(radical)
        RadicalScreen(
            state = state.value,
        )
    }
}
