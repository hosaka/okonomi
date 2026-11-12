package cc.hosaka.okonomi.ui.furigana

import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.hosaka.okonomi.ui.theme.OkonomiTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.compose.ui.text.intl.LocaleList as ComposeLocaleList

/**
 * On API 28 and up the reading half of a ruby unit leaves Compose: it is
 * drawn by a platform [TextView] (see [FontPaddingFreeText]), which reads
 * no `TextStyle` of its own accord. The word under it stays Compose text
 * (see `RubyUnit`), so the kanji shapes never pass through that view.
 * What carrying the style's locale across buys is agreement: the kana
 * reading comes from the same CJK fallback typeface and metrics as the
 * word it sits over, instead of from whatever the process default picks —
 * the phone's languages, or Settings → Language. That matters most on OEM
 * phones whose Chinese fallback is a different font family.
 *
 * The phone is English only, so a view left at its default is not
 * Japanese and the outcomes differ. Each case that claims a locale
 * asserts that first, because on a Japanese phone it could not fail.
 *
 * The view is found through the hosting view hierarchy, since an
 * `AndroidView` never reaches the semantics tree (see
 * [RubyRenderingTest], which runs below API 28 for that reason). What
 * the locale does to the glyphs stays invisible: Robolectric draws none.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "en-rUS")
@OptIn(ExperimentalTestApi::class)
class RubyTextLocaleTest {

    @Test
    fun `a reading drawn in a text view takes the japanese locale of the theme`() = runComposeUiTest {
        lateinit var host: View
        setContent {
            host = LocalView.current
            OkonomiTheme {
                FuriganaText(segments = NAOSU)
            }
        }
        waitForIdle()

        assertPhoneHasNoJapanese()
        assertEquals("ja", host.readingView().textLocales.toLanguageTags())
    }

    /**
     * A style that names no locale leaves the view at its default
     * rather than at a Japanese the view decided on for itself: the
     * theme is where the choice is made.
     */
    @Test
    fun `a reading whose style names no locale keeps the view's default`() = runComposeUiTest {
        lateinit var host: View
        setContent {
            host = LocalView.current
            MaterialTheme {
                FuriganaText(segments = NAOSU)
            }
        }
        waitForIdle()

        assertPhoneHasNoJapanese()
        assertEquals(LocaleList.getDefault(), host.readingView().textLocales)
    }

    /**
     * The view is kept across updates, so a style that stops naming a
     * locale has to put the default back rather than leave the last one.
     */
    @Test
    fun `a reading whose style drops its locale goes back to the default`() = runComposeUiTest {
        var style by mutableStateOf(TextStyle(localeList = ComposeLocaleList("ja")))
        lateinit var host: View
        setContent {
            host = LocalView.current
            MaterialTheme {
                FuriganaText(segments = NAOSU, style = style)
            }
        }
        waitForIdle()
        assertPhoneHasNoJapanese()
        val reading = host.readingView()
        assertEquals("ja", reading.textLocales.toLanguageTags(), "the reading starts in Japanese")

        style = TextStyle()
        waitForIdle()

        // Without the same view the stale locale could not be observed.
        assertSame(reading, host.readingView(), "the view is updated, not replaced")
        assertEquals(LocaleList.getAdjustedDefault(), reading.textLocales)
    }

    /**
     * An empty list names no locale, and the platform throws on one.
     * Drawn through the view directly, so the claim is about the view
     * alone.
     */
    @Test
    fun `an empty locale list leaves the view at its default`() = runComposeUiTest {
        lateinit var host: View
        setContent {
            host = LocalView.current
            FontPaddingFreeText(text = READING, style = TextStyle(localeList = ComposeLocaleList(emptyList())))
        }
        waitForIdle()

        assertEquals(LocaleList.getAdjustedDefault(), host.readingView().textLocales)
    }

    /**
     * At API 30, where `android.os.LocaleList` still throws on a repeated
     * locale. API 36 drops the repeat instead, leaving the tag list `ja,`,
     * so the suite's usual SDK would catch a missing de-duplication only
     * by that, and never see the crash older phones would.
     */
    @Test
    @Config(sdk = [30])
    fun `a repeated locale is set once`() = runComposeUiTest {
        lateinit var host: View
        setContent {
            host = LocalView.current
            FontPaddingFreeText(text = READING, style = TextStyle(localeList = ComposeLocaleList("ja,ja")))
        }
        waitForIdle()

        assertPhoneHasNoJapanese()
        assertEquals("ja", host.readingView().textLocales.toLanguageTags())
    }
}

private const val READING = "なお"

/** 直す, with なお over 直: the reading is what the text view draws. */
private val NAOSU = listOf(FuriganaSegment("直", READING), FuriganaSegment("す"))

/**
 * What every claim of a Japanese view rests on: with Japanese anywhere in
 * the phone's list, a view left at its default could pass for one.
 */
private fun assertPhoneHasNoJapanese() {
    val phone = LocaleList.getDefault()
    assertTrue(
        (0 until phone.size()).none { phone[it].language == "ja" },
        "the phone must have no Japanese for this to mean anything: $phone",
    )
}

/** The one text view on screen drawing [READING]. */
private fun View.readingView(): TextView = rootView.textViews().single { it.text.toString() == READING }

private fun View.textViews(): List<TextView> = buildList {
    val view = this@textViews
    if (view is TextView) add(view)
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) addAll(view.getChildAt(index).textViews())
    }
}
