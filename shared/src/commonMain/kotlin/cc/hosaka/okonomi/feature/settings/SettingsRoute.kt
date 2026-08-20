package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute : Route {
    @Composable
    override fun Content() {
        val state = produceSettingsScreenState()
        SettingsScreen(
            state = state.value,
        )
    }
}
