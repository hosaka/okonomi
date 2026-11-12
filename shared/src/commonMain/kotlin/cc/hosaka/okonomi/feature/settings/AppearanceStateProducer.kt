package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.produceScreenState
import cc.hosaka.okonomi.lang.AppLanguage
import cc.hosaka.okonomi.lang.appLanguageFlow
import cc.hosaka.okonomi.lang.currentDeviceLanguage
import cc.hosaka.okonomi.lang.learningLanguageFlow
import cc.hosaka.okonomi.lang.setAppLanguage
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.prefs.appPreferences
import cc.hosaka.okonomi.ui.coach.COACH_MARKS_ENABLED_DEFAULT
import cc.hosaka.okonomi.ui.coach.COACH_MARKS_ENABLED_PREFERENCE
import cc.hosaka.okonomi.ui.furigana.PER_KANJI_FURIGANA_DEFAULT
import cc.hosaka.okonomi.ui.furigana.PER_KANJI_FURIGANA_PREFERENCE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Composable
fun produceAppearanceScreenState(): State<AppearanceState> = produceScreenState(
    key = "appearance",
    initial = AppearanceState(),
) {
    appearanceScreenStateProducer()
}

/**
 * The switches and the language reflect only what the store says: a tap
 * writes, and the new value comes back through the store. A write that
 * fails therefore leaves the row showing what is actually stored.
 * Nothing is emitted before the store answers, so the state stays at its
 * unknown initial value rather than guessing the default.
 *
 * The per-kanji switch is the one exception to "what the store says".
 * While the reader is learning English the division is off whatever is
 * stored — `ProvideKanjiReadings` stops following the switch — so the
 * switch is shown off and cannot be moved. Nothing is written: the stored
 * value is the reader's choice for when they learn Japanese again, and
 * forcing it off on disk would lose it.
 */
suspend fun ScreenStateScope.appearanceScreenStateProducer(
    preferences: PreferenceStore = appPreferences(),
    deviceLanguage: () -> String = ::currentDeviceLanguage,
): Flow<AppearanceState> {
    val onShowHintsChange: (Boolean) -> Unit = { enabled ->
        preferences.setBoolean(COACH_MARKS_ENABLED_PREFERENCE, enabled)
    }
    val onPerKanjiFuriganaChange: (Boolean) -> Unit = { enabled ->
        preferences.setBoolean(PER_KANJI_FURIGANA_PREFERENCE, enabled)
    }
    val onAppLanguageChange: (AppLanguage) -> Unit = { language ->
        preferences.setAppLanguage(language)
    }
    return combine(
        preferences.appLanguageFlow(),
        preferences.booleanFlow(COACH_MARKS_ENABLED_PREFERENCE, COACH_MARKS_ENABLED_DEFAULT),
        preferences.booleanFlow(PER_KANJI_FURIGANA_PREFERENCE, PER_KANJI_FURIGANA_DEFAULT),
        preferences.learningLanguageFlow(deviceLanguage),
    ) { appLanguage, showHints, perKanjiFurigana, learningLanguage ->
        val furiganaShown = learningLanguage.showsFurigana
        AppearanceState(
            appLanguage = appLanguage,
            onAppLanguageChange = onAppLanguageChange,
            showHints = showHints,
            onShowHintsChange = onShowHintsChange,
            perKanjiFurigana = perKanjiFurigana && furiganaShown,
            onPerKanjiFuriganaChange = onPerKanjiFuriganaChange.takeIf { furiganaShown },
            furiganaHidden = !furiganaShown,
        )
    }
}
