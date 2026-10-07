package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

@Serializable
data object AboutRoute : Route {
    @Composable
    override fun Content() {
        val state = produceAboutScreenState()
        AboutScreen(
            state = state.value,
        )
    }
}
