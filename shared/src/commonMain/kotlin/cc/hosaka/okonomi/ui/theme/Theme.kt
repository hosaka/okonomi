package cc.hosaka.okonomi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList

/**
 * Material You theme of the app. The scheme follows the system
 * light/dark setting and, where the platform supports it, the
 * system accent color. Every text style is set in Japanese; see
 * [OkonomiTypography].
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
        typography = OkonomiTypography,
        content = content,
    )
}

/**
 * Material's default typography with the Japanese locale on every
 * style, the emphasized ones included. Nothing else about a style
 * changes: sizes, weights, line heights and colours are Material's.
 *
 * Why at all: Han characters are shared between Japanese and Chinese
 * but drawn differently — 直, 骨 and 写 each have a Japanese shape and
 * a Chinese one. A style that names no locale leaves the choice to the
 * process, and Android's font fallback follows the first CJK language
 * in the phone's list. On a phone whose first CJK language is not
 * Japanese, or that lists none, the kanji come from a non-Japanese
 * font — Simplified Chinese on stock Android — and a Japanese
 * dictionary draws Chinese shapes.
 *
 * Stated here once, the locale reaches text whose style comes from
 * `MaterialTheme.typography` or `LocalTextStyle`, and every `copy()`
 * and `atJapaneseReadingSize()` of those keeps it. It does not reach
 * `BasicText` or `BasicTextField` called without a style (they draw in
 * `TextStyle.Default`), nor a subtree that provides a `LocalTextStyle`
 * built from scratch.
 *
 * English text is meant to be unaffected: the locale chooses between
 * CJK fallback fonts, and Latin glyphs come from the primary font
 * whatever it says. On Android the paint locale also feeds the line
 * breaker, though, so Japanese lines may now break by Japanese rules.
 * Glyph shapes and wrapping are invisible to Robolectric and are
 * checked on a device, not by the tests.
 *
 * It is on the styles and deliberately not on semantics or
 * `AnnotatedString` spans: a screen reader takes those as the language
 * to speak in, and would read English glosses in a Japanese voice.
 *
 * A future UI language written in another CJK script — Chinese,
 * say — would have its own labels drawn in Japanese shapes by this,
 * and needs it revisited: the locale would then have to follow the UI
 * language for UI text and stay Japanese for the dictionary's.
 */
private val OkonomiTypography: Typography = Typography().inLocale(LocaleList("ja"))

private fun Typography.inLocale(locale: LocaleList): Typography {
    fun TextStyle.inLocale() = copy(localeList = locale)
    return copy(
        displayLarge = displayLarge.inLocale(),
        displayMedium = displayMedium.inLocale(),
        displaySmall = displaySmall.inLocale(),
        headlineLarge = headlineLarge.inLocale(),
        headlineMedium = headlineMedium.inLocale(),
        headlineSmall = headlineSmall.inLocale(),
        titleLarge = titleLarge.inLocale(),
        titleMedium = titleMedium.inLocale(),
        titleSmall = titleSmall.inLocale(),
        bodyLarge = bodyLarge.inLocale(),
        bodyMedium = bodyMedium.inLocale(),
        bodySmall = bodySmall.inLocale(),
        labelLarge = labelLarge.inLocale(),
        labelMedium = labelMedium.inLocale(),
        labelSmall = labelSmall.inLocale(),
        displayLargeEmphasized = displayLargeEmphasized.inLocale(),
        displayMediumEmphasized = displayMediumEmphasized.inLocale(),
        displaySmallEmphasized = displaySmallEmphasized.inLocale(),
        headlineLargeEmphasized = headlineLargeEmphasized.inLocale(),
        headlineMediumEmphasized = headlineMediumEmphasized.inLocale(),
        headlineSmallEmphasized = headlineSmallEmphasized.inLocale(),
        titleLargeEmphasized = titleLargeEmphasized.inLocale(),
        titleMediumEmphasized = titleMediumEmphasized.inLocale(),
        titleSmallEmphasized = titleSmallEmphasized.inLocale(),
        bodyLargeEmphasized = bodyLargeEmphasized.inLocale(),
        bodyMediumEmphasized = bodyMediumEmphasized.inLocale(),
        bodySmallEmphasized = bodySmallEmphasized.inLocale(),
        labelLargeEmphasized = labelLargeEmphasized.inLocale(),
        labelMediumEmphasized = labelMediumEmphasized.inLocale(),
        labelSmallEmphasized = labelSmallEmphasized.inLocale(),
    )
}

@Composable
expect fun appDynamicDarkColorScheme(): ColorScheme

@Composable
expect fun appDynamicLightColorScheme(): ColorScheme
