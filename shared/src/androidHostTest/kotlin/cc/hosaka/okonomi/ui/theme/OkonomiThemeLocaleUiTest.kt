package cc.hosaka.okonomi.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every style the theme hands out asks for Japanese, so that kanji take
 * their Japanese shapes on a phone whose first CJK language is not
 * Japanese, or that lists none (see `OkonomiTypography`).
 *
 * The styles are found by reflection over [Typography]'s getters rather
 * than listed by hand, so a style the theme forgets — or one a later
 * Material release adds, which the theme has never heard of — fails
 * here instead of quietly drawing its kanji in Chinese shapes.
 *
 * What the locale does to the glyphs is not assertable here at all:
 * Robolectric draws none. The claim is the locale the styles carry.
 */
@OptIn(ExperimentalTestApi::class)
class OkonomiThemeLocaleUiTest : ComposeUiTestBase() {

    @Test
    fun `every typography style carries the japanese locale`() = runComposeUiTest {
        lateinit var typography: Typography
        setContent {
            OkonomiTheme { typography = MaterialTheme.typography }
        }
        waitForIdle()

        val styles = typography.textStyles()
        // The guard on the reflection itself: with nothing found, the
        // assertion below would pass over an empty map.
        assertTrue(
            "bodyLarge" in styles && "bodyLargeEmphasized" in styles,
            "both the baseline and the emphasized styles are enumerated: ${styles.keys}",
        )
        assertEquals(
            emptySet(),
            styles.filterValues { it.localeList != JAPANESE }.keys,
            "styles without the Japanese locale",
        )
    }

    /**
     * The locale is the only change: a size, weight or line height
     * drifting from Material's would move every screen at once. The
     * baseline is what `MaterialExpressiveTheme` hands out when given no
     * typography, which is what the app drew in before the locale.
     */
    @Test
    fun `nothing but the locale differs from material's typography`() = runComposeUiTest {
        lateinit var material: Typography
        lateinit var typography: Typography
        setContent {
            MaterialExpressiveTheme { material = MaterialTheme.typography }
            OkonomiTheme { typography = MaterialTheme.typography }
        }
        waitForIdle()

        assertEquals(
            material.textStyles().mapValues { (_, style) -> style.copy(localeList = JAPANESE) },
            typography.textStyles(),
        )
    }

    /** What a plain `Text`, and `FuriganaText`'s default, are drawn in. */
    @Test
    fun `the ambient text style carries the japanese locale`() = runComposeUiTest {
        lateinit var style: TextStyle
        setContent {
            OkonomiTheme { style = LocalTextStyle.current }
        }
        waitForIdle()

        assertEquals(JAPANESE, style.localeList)
    }
}

private val JAPANESE = LocaleList("ja")

/** Every [TextStyle] this typography exposes, keyed by property name. */
private fun Typography.textStyles(): Map<String, TextStyle> =
    Typography::class.java.methods
        .filter { method ->
            method.parameterCount == 0 &&
                method.returnType == TextStyle::class.java &&
                !Modifier.isStatic(method.modifiers)
        }
        .associate { getter ->
            getter.name.removePrefix("get").replaceFirstChar { it.lowercaseChar() } to getter.invoke(this) as TextStyle
        }
