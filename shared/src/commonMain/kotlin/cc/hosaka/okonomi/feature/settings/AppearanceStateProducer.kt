package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.prefs.appPreferences
import cc.hosaka.okonomi.ui.coach.COACH_MARKS_ENABLED_DEFAULT
import cc.hosaka.okonomi.ui.coach.COACH_MARKS_ENABLED_PREFERENCE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
fun produceAppearanceScreenState(): State<AppearanceState> = produceScreenState(
    key = "appearance",
    initial = AppearanceState(),
) {
    appearanceScreenStateProducer()
}

/**
 * The switch reflects only what the store says: a tap writes, and the new
 * value comes back through [PreferenceStore.booleanFlow]. A write that
 * fails therefore leaves the switch showing what is actually stored.
 * Nothing is emitted before the store answers, so the state stays at its
 * unknown initial value rather than guessing the default.
 */
suspend fun ScreenStateScope.appearanceScreenStateProducer(
    preferences: PreferenceStore = appPreferences(),
): Flow<AppearanceState> {
    val onShowHintsChange: (Boolean) -> Unit = { enabled ->
        preferences.setBoolean(COACH_MARKS_ENABLED_PREFERENCE, enabled)
    }
    return preferences.booleanFlow(COACH_MARKS_ENABLED_PREFERENCE, COACH_MARKS_ENABLED_DEFAULT)
        .map { showHints ->
            AppearanceState(
                showHints = showHints,
                onShowHintsChange = onShowHintsChange,
            )
        }
}
