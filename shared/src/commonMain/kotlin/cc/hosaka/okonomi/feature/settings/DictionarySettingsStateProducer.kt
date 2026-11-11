package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.lang.currentDeviceLanguage
import cc.hosaka.okonomi.lang.learningLanguageFlow
import cc.hosaka.okonomi.lang.setLearningLanguage
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.prefs.appPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
fun produceDictionarySettingsScreenState(): State<DictionarySettingsState> = produceScreenState(
    key = "dictionary-settings",
    initial = DictionarySettingsState(),
) {
    dictionarySettingsScreenStateProducer()
}

/**
 * The choice reflects only what the store says, as Appearance's switches
 * do: a tap writes, and the new value comes back through the store.
 *
 * Reading never writes. A reader who has not chosen is shown the device
 * default, and it stays a default — derived again on every read — until
 * they pick a language themselves.
 */
suspend fun ScreenStateScope.dictionarySettingsScreenStateProducer(
    preferences: PreferenceStore = appPreferences(),
    deviceLanguage: () -> String = ::currentDeviceLanguage,
): Flow<DictionarySettingsState> {
    val onLearningLanguageChange: (LearningLanguage) -> Unit = { language ->
        preferences.setLearningLanguage(language)
    }
    return preferences.learningLanguageFlow(deviceLanguage).map { language ->
        DictionarySettingsState(
            learningLanguage = language,
            onLearningLanguageChange = onLearningLanguageChange,
        )
    }
}
