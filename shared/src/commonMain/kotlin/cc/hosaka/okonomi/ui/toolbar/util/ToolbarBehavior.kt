package cc.hosaka.okonomi.ui.toolbar.util

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable

object ToolbarBehavior {
    @Composable
    fun behavior(): TopAppBarScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
}
