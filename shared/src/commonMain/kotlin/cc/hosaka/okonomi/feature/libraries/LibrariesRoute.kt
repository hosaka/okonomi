package cc.hosaka.okonomi.feature.libraries

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

@Serializable
data object LibrariesRoute : Route {
    @Composable
    override fun Content() {
        val state = produceLibrariesScreenState()
        LibrariesScreen(
            state = state.value,
        )
    }
}
