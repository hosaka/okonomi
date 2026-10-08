package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class AppearanceState(
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
     */
    val perKanjiFurigana: Boolean? = null,
    /** Null until the producer is running; the switch is disabled meanwhile. */
    val onPerKanjiFuriganaChange: ((Boolean) -> Unit)? = null,
)
