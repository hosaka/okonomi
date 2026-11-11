package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.feature.navigation.Route
import kotlinx.serialization.Serializable

@Serializable
data object DictionarySettingsRoute : Route {
    @Composable
    override fun Content() {
        val state = produceDictionarySettingsScreenState()
        DictionarySettingsScreen(
            state = state.value,
        )
    }
}
