package cc.hosaka.okonomi.feature.radical

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import cc.hosaka.okonomi.db.KanjiHit
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.kanjiContainingRadical
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.loadOnce
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.feature.search.SearchRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
fun produceRadicalScreenState(
    radical: String,
): State<RadicalState> {
    // The initial state is what the screen renders until the first load
    // reports, and the bar is already on screen by then, so its back
    // control has to work from the first frame. It is wired from the
    // hosting controller directly rather than from the producer's proxy,
    // which does not exist yet at this point.
    val navigation = LocalNavigationController.current
    val initial = remember(navigation, radical) { radicalState(navigation, radical) }
    return produceScreenState(
        // Keyed per radical: two radical screens can sit on one back
        // stack (a kanji tapped here opens a search, which opens an
        // entry, whose overlay offers another radical), and one key would
        // make the second show the first's characters.
        key = "radical-$radical",
        initial = initial,
    ) {
        radicalScreenStateProducer(radical)
    }
}

/**
 * The screen's two navigations, stated once.
 *
 * Both the initial state and every state the producer emits need them,
 * and two spellings of "a kanji tap pushes a search" could drift into
 * disagreeing about where a tap goes depending on how far the load had
 * got.
 */
internal fun radicalState(
    navigation: NavigationController,
    radical: String,
): RadicalState = RadicalState(
    radical = radical,
    onBack = { navigation.pop() },
    onKanjiClick = { literal -> navigation.navigate(SearchRoute(literal)) },
)

/**
 * One load for the life of the screen, and two navigations.
 *
 * A kanji tap pushes a [SearchRoute] carrying the literal: from a
 * character, the next question is what words are written with it, and
 * that is the word search's job rather than this screen's.
 */
suspend fun ScreenStateScope.radicalScreenStateProducer(
    radical: String,
    load: suspend (String) -> List<KanjiHit> = { kanjiContainingRadical(it) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
): Flow<RadicalState> {
    // Built once for the whole run and copied per emission, for the
    // reason `loadOnce` documents for its own error lambda: a fresh
    // capturing lambda per emission makes two otherwise equal states
    // compare unequal, so neither the state flow nor the composition
    // could conflate a redundant emission away.
    val base = radicalState(navigation, radical)
    return loadOnce(
        key = "radical",
        load = { load(radical) },
        invalidate = invalidate,
    ).map { kanji -> base.copy(kanji = kanji) }
}
