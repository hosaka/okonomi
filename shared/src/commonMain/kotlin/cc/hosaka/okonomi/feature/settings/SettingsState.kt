package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.common.model.Loadable
import cc.hosaka.okonomi.db.DictionaryInfo

@Immutable
data class SettingsState(
    /**
     * Ok(null) means the dictionary could not be loaded; the screen
     * shows nothing in that case (a failure never takes the screen down).
     */
    val dictionary: Loadable<DictionaryInfo?> = Loadable.Loading,
)
