package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.lang.LearningLanguage

@Immutable
data class DictionarySettingsState(
    /**
     * The language the reader is learning — their choice, or the device
     * default while they have made none; null until the store has
     * answered, and the choice is not drawn till then.
     */
    val learningLanguage: LearningLanguage? = null,
    /** Null until the producer is running; the choice is disabled meanwhile. */
    val onLearningLanguageChange: ((LearningLanguage) -> Unit)? = null,
)
