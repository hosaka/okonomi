package cc.hosaka.okonomi.lang

import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.LocaleList
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.intl.Locale
import androidx.navigation3.runtime.NavKey
import cc.hosaka.okonomi.feature.home.HomeScreen
import cc.hosaka.okonomi.feature.home.HomeSectionProbe
import cc.hosaka.okonomi.feature.home.navigation.homeSettingsItem
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.settings.SettingsRoute
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.ui.coach.CoachMarkRegistry
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.anki_card_link
import okonomi.shared.generated.resources.appearance_title
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.robolectric.annotation.Config

/**
 * What the UI language choice does to the real shell, read off the text
 * it draws: the Settings tab's label, which is 設定 in Japanese and
 * "Settings" in English. The expected strings are written out rather
 * than read through `stringResource`, which would follow the very
 * locale under test and agree with whatever was drawn.
 *
 * A "phone" is Robolectric's qualifiers, which set the process locale
 * and the configurations alike; without any, the phone is en-US.
 *
 * The override is process-wide, so each test puts the default locale
 * back as it found it, and starts cold: with no language applied in the
 * process yet.
 */
@OptIn(ExperimentalTestApi::class)
class AppLanguageOverrideUiTest : ComposeUiTestBase() {

    private var restorePhoneLanguage: () -> Unit = {}

    @BeforeTest
    fun startCold() {
        restorePhoneLanguage = snapshotPhoneLanguage()
        forgetAppliedAppLanguage()
    }

    @AfterTest
    fun putThePhoneLanguageBack() {
        restorePhoneLanguage()
        forgetAppliedAppLanguage()
    }

    @Test
    fun `a stored japanese draws the shell in japanese on an english phone`() = runComposeUiTest {
        setContent { ShellUnderTest(storedLanguage("ja")) }
        waitForIdle()

        onAllNodes(hasText(JAPANESE_SETTINGS)).assertCountEquals(1)
        onAllNodes(hasText(ENGLISH_SETTINGS)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "ja")
    fun `with nothing stored a japanese phone gets the japanese shell`() = runComposeUiTest {
        setContent { ShellUnderTest(FakePreferenceStore()) }
        waitForIdle()

        onAllNodes(hasText(JAPANESE_SETTINGS)).assertCountEquals(1)
        onAllNodes(hasText(ENGLISH_SETTINGS)).assertCountEquals(0)
    }

    /**
     * A phone in a language the app has no translation for falls back to
     * the default resources, English, and not to the other translation
     * the app happens to carry.
     */
    @Test
    @Config(qualifiers = "fr")
    fun `with nothing stored a phone in an untranslated language gets english`() = runComposeUiTest {
        setContent { ShellUnderTest(FakePreferenceStore()) }
        waitForIdle()

        onAllNodes(hasText(ENGLISH_SETTINGS)).assertCountEquals(1)
        onAllNodes(hasText(JAPANESE_SETTINGS)).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "ja")
    fun `a stored english draws the shell in english on a japanese phone`() = runComposeUiTest {
        setContent { ShellUnderTest(storedLanguage("en")) }
        waitForIdle()

        onAllNodes(hasText(ENGLISH_SETTINGS)).assertCountEquals(1)
        onAllNodes(hasText(JAPANESE_SETTINGS)).assertCountEquals(0)
    }

    /**
     * English first, and the phone's Japanese still behind it: text that
     * names no language takes its kanji style from the first CJK
     * language in the list, and with Japanese gone it is Chinese.
     */
    @Test
    @Config(qualifiers = "ja")
    fun `an english ui on a japanese phone keeps japanese behind english`() = runComposeUiTest {
        setContent { ShellUnderTest(storedLanguage("en")) }
        waitForIdle()

        val locales = LocaleList.getDefault()
        assertEquals("en", locales[0].language)
        assertTrue(
            (0 until locales.size()).any { locales[it].language == "ja" },
            "the phone's Japanese was dropped: $locales",
        )
        onAllNodes(hasText(ENGLISH_SETTINGS)).assertCountEquals(1)
    }

    /**
     * Android's own strings, Material's and Compose UI's built-in labels
     * among them, are read through the activity's resources and
     * configuration rather than Compose resources, and follow the choice
     * as well. The phone's language does not move.
     */
    @Test
    @Config(qualifiers = "ja")
    fun `android strings follow an english choice on a japanese phone`() = runComposeUiTest {
        var cancel = ""
        var configured = ""
        setContent {
            ProvideAppLanguage(storedLanguage("en")) {
                cancel = LocalContext.current.resources.getString(android.R.string.cancel)
                configured = LocalConfiguration.current.locales[0].language
            }
        }
        waitForIdle()

        assertEquals("Cancel", cancel)
        assertEquals("en", configured)
        assertEquals("ja", currentDeviceLanguage())
    }

    @Test
    fun `android strings follow a japanese choice on an english phone`() = runComposeUiTest {
        var cancel = ""
        var configured = ""
        setContent {
            ProvideAppLanguage(storedLanguage("ja")) {
                cancel = LocalContext.current.resources.getString(android.R.string.cancel)
                configured = LocalConfiguration.current.locales[0].language
            }
        }
        waitForIdle()

        assertEquals("キャンセル", cancel)
        assertEquals("ja", configured)
        assertEquals("en", currentDeviceLanguage())
    }

    /**
     * A configuration change resets the process language to the phone's.
     * In this app each one also recreates the activity, which applies
     * the choice afresh; this is the case where one does not, and the
     * new configuration alone has to put the choice back.
     */
    @Test
    fun `a configuration change puts the chosen language back`() = runComposeUiTest {
        var landscape by mutableStateOf(false)
        setContent {
            val system = LocalConfiguration.current
            val rotated = remember(landscape) {
                Configuration(system).apply {
                    orientation = if (landscape) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
                }
            }
            CompositionLocalProvider(LocalConfiguration provides rotated) {
                ProvideAppLanguage(storedLanguage("ja")) {}
            }
        }
        waitForIdle()
        assertEquals("ja", Locale.current.language)

        runOnIdle {
            LocaleList.setDefault(LocaleList.forLanguageTags("en-US"))
            landscape = true
        }
        waitForIdle()

        assertEquals("ja", Locale.current.language)
    }

    @Test
    @Config(qualifiers = "ja")
    fun `an unrecognised stored value follows the phone`() = runComposeUiTest {
        setContent { ShellUnderTest(storedLanguage("xx")) }
        waitForIdle()

        onAllNodes(hasText(JAPANESE_SETTINGS)).assertCountEquals(1)
        onAllNodes(hasText(ENGLISH_SETTINGS)).assertCountEquals(0)
    }

    /**
     * Live, with no restart, and with the reader where they were: the
     * Settings root redraws in Japanese, its text inside the scaffold's
     * subcomposition included, and so does a screen pushed over it, which
     * stays the open screen. Then System default puts the phone's
     * language back.
     *
     * A call site that merely skipped would keep the string it had, and
     * one rebuilt under a `key` crashes the saved-state holder while a
     * screen is open, so each half of `ProvideAppLanguage` fails this.
     */
    @Test
    fun `a new choice redraws the open screens in place and system default undoes it`() = runComposeUiTest {
        val preferences = FakePreferenceStore()
        var settings: HomeSectionProbe? = null
        setContent {
            ShellUnderTest(preferences, onSection = { key, probe -> if (key == homeSettingsItem.key) settings = probe })
        }
        waitForIdle()
        runOnIdle { checkNotNull(settings).select() }
        waitForIdle()
        onNodeWithText(ENGLISH_APPEARANCE_DESCRIPTION).assertExists()

        preferences.setAppLanguage(AppLanguage.Japanese)
        waitForIdle()

        onNodeWithText(JAPANESE_APPEARANCE_DESCRIPTION).assertExists()
        onNodeWithText(ENGLISH_APPEARANCE_DESCRIPTION).assertDoesNotExist()
        onAllNodes(hasText(ENGLISH_SETTINGS)).assertCountEquals(0)

        // Pushed rather than made a section's root: a root is serialized
        // into saved state as soon as it is remembered, and the probe is
        // not one of the app's registered routes.
        runOnIdle { checkNotNull(settings).controller.navigate(AppearanceTitleProbeRoute) }
        waitForIdle()
        onNodeWithText(JAPANESE_APPEARANCE).assertExists()

        preferences.setAppLanguage(AppLanguage.System)
        waitForIdle()

        onNodeWithText(ENGLISH_APPEARANCE).assertExists()
        onNodeWithText(JAPANESE_APPEARANCE).assertDoesNotExist()
        assertEquals(
            listOf<NavKey>(SettingsRoute, AppearanceTitleProbeRoute),
            checkNotNull(settings).stack(),
            "the reader was moved off the screen they were on",
        )
    }

    /**
     * A lazy list composes its items apart from the screen around it,
     * so a recomposition of the screen alone would leave them as they
     * were.
     */
    @Test
    fun `a new choice redraws the items of a lazy list`() = runComposeUiTest {
        val preferences = FakePreferenceStore()
        setContent {
            ProvideAppLanguage(preferences) {
                LazyColumn {
                    item { Text(stringResource(Res.string.appearance_title)) }
                }
            }
        }
        waitForIdle()
        onNodeWithText(ENGLISH_APPEARANCE).assertExists()

        preferences.setAppLanguage(AppLanguage.Japanese)
        waitForIdle()

        onNodeWithText(JAPANESE_APPEARANCE).assertExists()
    }

    /**
     * The suspend `getString` reads the language apart from composition,
     * and is what the Send to AnkiDroid path writes the card link with
     * (`FavouritesStateProducer`'s `ankiLinkLabel`). The send itself is
     * not driven here: this is the read it depends on.
     */
    @Test
    fun `the suspend getString the anki send uses follows the chosen language`() = runComposeUiTest {
        setContent { ProvideAppLanguage(storedLanguage("ja")) {} }
        waitForIdle()

        val link = runBlocking { getString(Res.string.anki_card_link) }

        assertEquals("Okonomiで詳しく見る", link)
    }

    /**
     * The menus in Japanese do not make the reader a Japanese speaker:
     * with nothing chosen, the language they learn is still worked out
     * from the phone (en-US here, so Japanese), not from the UI.
     */
    @Test
    fun `the learning default follows the phone under a japanese ui`() = runComposeUiTest {
        setContent { ProvideAppLanguage(storedLanguage("ja")) {} }
        waitForIdle()
        assertEquals("ja", Locale.current.language, "the override is not in effect, so nothing below is tested")

        val learning = runBlocking { FakePreferenceStore().learningLanguageFlow().first() }

        assertEquals(LearningLanguage.Japanese, learning)
    }

    /** A Japanese phone belongs to a Japanese speaker, who is learning English. */
    @Test
    @Config(qualifiers = "ja")
    fun `on a japanese phone with nothing chosen the learning default is english`() {
        val learning = runBlocking { FakePreferenceStore().learningLanguageFlow().first() }

        assertEquals(LearningLanguage.English, learning)
    }

    /** Menus in English do not make a Japanese speaker a learner of Japanese. */
    @Test
    @Config(qualifiers = "ja")
    fun `on a japanese phone with an english ui the learning default stays english`() = runComposeUiTest {
        setContent { ProvideAppLanguage(storedLanguage("en")) {} }
        waitForIdle()
        assertEquals("en", Locale.current.language, "the override is not in effect, so nothing below is tested")

        val learning = runBlocking { FakePreferenceStore().learningLanguageFlow().first() }

        assertEquals(LearningLanguage.English, learning)
    }
}

private const val JAPANESE_SETTINGS = "設定"
private const val ENGLISH_SETTINGS = "Settings"
private const val JAPANESE_APPEARANCE = "外観"
private const val ENGLISH_APPEARANCE = "Appearance"
private const val JAPANESE_APPEARANCE_DESCRIPTION = "表示言語、メインタブのヒント、ふりがな"
private const val ENGLISH_APPEARANCE_DESCRIPTION = "Language, main tab hints and furigana"

private fun storedLanguage(value: String) = FakePreferenceStore(strings = mapOf(APP_LANGUAGE_PREFERENCE to value))

/**
 * The real shell over a store the test owns; the production default
 * opens a real DataStore, which answers on a dispatcher the test clock
 * cannot wait for (see `CoachMarksUiTest`).
 */
@Composable
private fun ShellUnderTest(
    preferences: PreferenceStore,
    onSection: ((key: String, probe: HomeSectionProbe) -> Unit)? = null,
) {
    HomeScreen(
        coachMarks = remember { CoachMarkRegistry() },
        preferences = preferences,
        onSection = onSection,
    )
}

/** A screen drawing one string resource, the way every real screen does. */
@Serializable
internal data object AppearanceTitleProbeRoute : Route {
    @Composable
    override fun Content() {
        Text(stringResource(Res.string.appearance_title))
    }
}
