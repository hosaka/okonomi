package cc.hosaka.okonomi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable

/**
 * Material You theme of the app. The scheme follows the system
 * light/dark setting and, where the platform supports it, the
 * system accent color.
 */
@Composable
fun OkonomiTheme(
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        appDynamicDarkColorScheme()
    } else {
        appDynamicLightColorScheme()
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        content = content,
    )
}

@Composable
expect fun appDynamicDarkColorScheme(): ColorScheme

@Composable
expect fun appDynamicLightColorScheme(): ColorScheme
