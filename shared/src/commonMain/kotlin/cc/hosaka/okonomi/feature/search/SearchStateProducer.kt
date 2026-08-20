package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
fun produceSearchScreenState(): State<SearchState> = produceScreenState(
    key = "search",
    initial = SearchState(),
) {
    searchScreenStateProducer()
}

suspend fun ScreenStateScope.searchScreenStateProducer(): Flow<SearchState> {
    val querySink = mutablePersistedFlow(
        key = "query",
        initial = "",
    )
    return querySink
        .map { query ->
            SearchState(
                query = query,
                onQueryChange = { querySink.value = it },
                onClear = { querySink.value = "" }.takeIf { query.isNotEmpty() },
            )
        }
}
