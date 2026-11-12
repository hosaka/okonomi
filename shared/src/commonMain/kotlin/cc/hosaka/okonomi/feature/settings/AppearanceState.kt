package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.lang.AppLanguage

@Immutable
data class AppearanceState(
    /**
     * The language the app is drawn in, as the reader chose it: System
     * default until they pick a language, and for any stored value this
     * version does not recognise. Null until the store has answered, and
     * the row is not drawn till then.
     */
    val appLanguage: AppLanguage? = null,
    /**
     * Null until the producer is running, which hands it over with
     * [appLanguage]; the row is not drawn without it.
     */
    val onAppLanguageChange: ((AppLanguage) -> Unit)? = null,
    /**
     * Whether the coach marks may show over the idle Search tab; null
     * until the store has answered, and the switch is not drawn till then.
     */
    val showHints: Boolean? = null,
    /** Null until the producer is running; the switch is disabled meanwhile. */
    val onShowHintsChange: ((Boolean) -> Unit)? = null,
    /**
     * Whether a run of kanji is divided into one ruby per kanji where
     * kanjidic's readings settle it; null until the store has answered.
     * Always false while [furiganaHidden], when the division is off
     * whatever is stored (see `ProvideKanjiReadings`).
     */
    val perKanjiFurigana: Boolean? = null,
    /**
     * Null until the producer is running, and while [furiganaHidden]; the
     * switch is disabled meanwhile.
     */
    val onPerKanjiFuriganaChange: ((Boolean) -> Unit)? = null,
    /**
     * True while the reader is learning English, when words are drawn with
     * no furigana and the names that keep theirs are not divided. The
     * per-kanji switch is then shown off and disabled, with a note saying
     * why, and what it has stored is left alone: learning Japanese again
     * brings the reader's own choice back.
     */
    val furiganaHidden: Boolean = false,
)
