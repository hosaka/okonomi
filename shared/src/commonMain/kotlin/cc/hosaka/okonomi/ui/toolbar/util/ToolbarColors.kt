package cc.hosaka.okonomi.ui.toolbar.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified

object ToolbarColors {
    @Composable
    fun containerColor(): Color = MaterialTheme.colorScheme.surface

    @Composable
    fun scrolledContainerColor(
        containerColor: Color,
    ): Color {
        if (
            containerColor == Color.Transparent ||
            containerColor.isUnspecified
        ) {
            return containerColor
        }

        return MaterialTheme.colorScheme.surfaceContainerHigh
    }
}
