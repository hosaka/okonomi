package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute : Route {
    @Composable
    override fun Content() {
        val state = produceSearchScreenState()
        SearchScreen(
            state = state.value,
        )
    }
}
