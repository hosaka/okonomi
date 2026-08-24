package cc.hosaka.okonomi.feature.libraries

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.common.model.Loadable
import com.mikepenz.aboutlibraries.Libs

@Immutable
data class LibrariesState(
    /**
     * Ok(null) means the library list could not be loaded and the screen
     * shows an error text (a failure never takes the screen down); an
     * Ok list that is empty shows the empty state instead.
     */
    val libraries: Loadable<Libs?> = Loadable.Loading,
)
