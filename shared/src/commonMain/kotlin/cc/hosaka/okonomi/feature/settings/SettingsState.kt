package cc.hosaka.okonomi.feature.settings

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.common.model.Loadable
import com.mikepenz.aboutlibraries.Libs

@Immutable
data class SettingsState(
    val libraries: Loadable<Libs> = Loadable.Loading,
)
