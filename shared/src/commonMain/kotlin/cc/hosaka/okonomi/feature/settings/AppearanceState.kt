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
)
