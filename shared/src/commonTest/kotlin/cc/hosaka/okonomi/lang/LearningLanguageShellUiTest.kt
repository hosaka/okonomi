package cc.hosaka.okonomi.lang

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.feature.home.HomeScreen
import cc.hosaka.okonomi.feature.home.HomeSectionProbe
import cc.hosaka.okonomi.feature.home.navigation.homeSearchItem
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.ui.coach.CoachMarkRegistry
import cc.hosaka.okonomi.ui.furigana.LocalFuriganaShown
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable

/**
 * The shell is where the learning language is provided, so a screen it
 * hosts has to see it — and see it change. A shell that forgot would
 * leave the whole app drawn for a reader learning Japanese, with every
 * test of the screens still green, since they provide the language
 * themselves.
 */
@OptIn(ExperimentalTestApi::class)
class LearningLanguageShellUiTest : ComposeUiTestBase() {

    @Test
    fun `a screen the shell hosts sees the learning language and follows it`() = runComposeUiTest {
        probed = null
        val preferences = FakePreferenceStore(strings = mapOf(LEARNING_LANGUAGE_PREFERENCE to "english"))
        var search: HomeSectionProbe? = null
        setContent {
            HomeScreen(
                coachMarks = remember { CoachMarkRegistry() },
                preferences = preferences,
                onSection = { key, probe -> if (key == homeSearchItem.key) search = probe },
                // Not Japanese, so English is the stored choice rather
                // than the device default.
                deviceLanguage = "en",
            )
        }
        waitForIdle()

        // Pushed rather than made a section's root: a root is serialized
        // into saved state as soon as it is remembered, and the probe is
        // not one of the app's registered routes.
        runOnIdle { checkNotNull(search).controller.navigate(LearningLanguageProbeRoute) }
        waitForIdle()

        assertEquals(Probed(LearningLanguage.English, furiganaShown = false), probed)

        preferences.setLearningLanguage(LearningLanguage.Japanese)
        waitForIdle()

        assertEquals(Probed(LearningLanguage.Japanese, furiganaShown = true), probed)
    }
}

/** What [LearningLanguageProbeRoute] was last drawn with. */
private data class Probed(val language: LearningLanguage, val furiganaShown: Boolean)

private var probed: Probed? = null

/** A screen that only records the locals it was drawn with. */
@Serializable
internal data object LearningLanguageProbeRoute : Route {
    @Composable
    override fun Content() {
        probed = Probed(LocalLearningLanguage.current, LocalFuriganaShown.current)
    }
}
