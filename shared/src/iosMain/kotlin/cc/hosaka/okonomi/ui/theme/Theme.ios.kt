package cc.hosaka.okonomi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun appDynamicDarkColorScheme(): ColorScheme = darkColorScheme()

@Composable
actual fun appDynamicLightColorScheme(): ColorScheme = lightColorScheme()
