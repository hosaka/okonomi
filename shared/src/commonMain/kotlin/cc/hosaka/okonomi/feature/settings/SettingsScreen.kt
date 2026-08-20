package cc.hosaka.okonomi.feature.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.ui.ScaffoldColumn
import cc.hosaka.okonomi.ui.toolbar.LargeToolbar
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarBehavior
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    state: SettingsState,
) {
    val scrollBehavior = ToolbarBehavior.behavior()
    ScaffoldColumn(
        topAppBarScrollBehavior = scrollBehavior,
        topBar = {
            LargeToolbar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_title),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        // The settings content is deferred work; the screen is
        // intentionally empty below the toolbar for now.
    }
}
