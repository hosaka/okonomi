package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun produceSettingsScreenState(): State<SettingsState> = produceScreenState(
    key = "settings",
    initial = SettingsState,
) {
    settingsScreenStateProducer()
}

suspend fun ScreenStateScope.settingsScreenStateProducer(): Flow<SettingsState> =
    flowOf(SettingsState)
