package cc.hosaka.okonomi.feature.kanji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.db.invalidateDictionary
import cc.hosaka.okonomi.db.loadKanjiForWord
import cc.hosaka.okonomi.feature.navigation.state.LoadState
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.loadOnce
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.lang.hanCharacters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Composable
fun produceKanjiTabState(
    entryId: Long,
    headword: String,
): State<KanjiTabState> = produceScreenState(
    // Keyed per entry beside the entry's own screen state, so two
    // entries on the same back stack cannot share one tab's characters.
    key = "entry-kanji-$entryId",
    initial = KanjiTabState(),
) {
    kanjiTabStateProducer(headword)
}

suspend fun ScreenStateScope.kanjiTabStateProducer(
    headword: String,
    load: suspend (List<String>) -> List<KanjiCharacter> = { loadKanjiForWord(it) },
    invalidate: suspend () -> Unit = { invalidateDictionary() },
): Flow<KanjiTabState> {
    val literals = hanCharacters(headword)
    // A headword with no Han characters is settled here, before any
    // database work: [KanjiTabContentState.NoKanji] is reached only on
    // this branch, so the reader can never be shown it because a query
    // happened to come back empty.
    if (literals.isEmpty()) {
        return flowOf(KanjiTabState(content = KanjiTabContentState.NoKanji))
    }
    return loadOnce(
        key = "kanji",
        load = { load(literals) },
        invalidate = invalidate,
    ).map { state ->
        KanjiTabState(
            content = when (state) {
                LoadState.Loading -> KanjiTabContentState.Loading
                is LoadState.Ready -> KanjiTabContentState.Ready(state.value)
                is LoadState.Error -> KanjiTabContentState.Error(state.onRetry)
            },
        )
    }
}
