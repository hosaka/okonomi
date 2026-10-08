package cc.hosaka.okonomi.ui.furigana

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
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.serialization.Serializable

/**
 * What every furigana site reads [LocalKanjiReadings] from: the
 * readings while Appearance's switch is on, nothing while it is off or
 * before they have loaded, and nothing when they cannot load.
 */
@OptIn(ExperimentalTestApi::class)
class ProvideKanjiReadingsUiTest : ComposeUiTestBase() {

    @Test
    fun `the readings are provided while the setting is on and withdrawn while off`() = runComposeUiTest {
        val preferences = FakePreferenceStore()
        var provided: KanjiReadings? = null
        setContent {
            ProvideKanjiReadings(preferences = preferences, load = { kanjidicFixture }) {
                provided = LocalKanjiReadings.current
            }
        }

        waitUntil { provided != null }
        assertSame(kanjidicFixture, provided, "the setting defaults to on")

        preferences.setBoolean(PER_KANJI_FURIGANA_PREFERENCE, false)
        waitForIdle()
        assertNull(provided, "off draws every run whole")

        preferences.setBoolean(PER_KANJI_FURIGANA_PREFERENCE, true)
        waitUntil { provided != null }
        assertSame(kanjidicFixture, provided)
    }

    @Test
    fun `nothing is loaded while the setting is off`() = runComposeUiTest {
        val preferences = FakePreferenceStore(mapOf(PER_KANJI_FURIGANA_PREFERENCE to false))
        var loads = 0
        var provided: KanjiReadings? = kanjidicFixture
        setContent {
            ProvideKanjiReadings(preferences = preferences, load = { loads++; kanjidicFixture }) {
                provided = LocalKanjiReadings.current
            }
        }

        waitForIdle()
        assertEquals(0, loads)
        assertNull(provided)
    }

    /**
     * A load that fails is the app as it was before the readings existed,
     * not a crash. The readings are provided first, so a provider that
     * kept the last good value, or never withdrew one, would leave them
     * in place; and every retry fails as well.
     */
    @Test
    fun `a load that fails leaves every run whole`() = runComposeUiTest {
        val preferences = FakePreferenceStore()
        var failing = false
        var attempts = 0
        var provided: KanjiReadings? = null
        val load: suspend () -> KanjiReadings = {
            if (failing) {
                attempts++
                error("no dictionary")
            }
            kanjidicFixture
        }
        setContent {
            ProvideKanjiReadings(preferences = preferences, load = load) {
                provided = LocalKanjiReadings.current
            }
        }
        waitUntil { provided != null }

        failing = true
        preferences.setBoolean(PER_KANJI_FURIGANA_PREFERENCE, false)
        waitForIdle()
        preferences.setBoolean(PER_KANJI_FURIGANA_PREFERENCE, true)
        waitUntil(timeoutMillis = 120_000) { attempts == 1 + KANJI_READINGS_RETRY_DELAYS_MILLIS.size }
        waitForIdle()

        assertNull(provided)
    }

    /**
     * On a first launch the dictionary can still be being copied when
     * the readings are first asked for. A failure must not leave every
     * run whole for the rest of the session.
     *
     * The first three attempts fail. Under this harness the provider's
     * producer can start twice before it settles (measured: the same
     * keys, two runs), and a fresh start makes a new attempt of its own,
     * so failing only once proved nothing — it passed with the retries
     * deleted. Three failures outlast any restart; only retrying reaches
     * the fourth attempt.
     */
    @Test
    fun `a load that keeps failing for a while is retried until it succeeds`() = runComposeUiTest {
        var attempts = 0
        var provided: KanjiReadings? = null
        val load: suspend () -> KanjiReadings = {
            attempts++
            if (attempts <= 3) error("dictionary still being copied")
            kanjidicFixture
        }
        val preferences = FakePreferenceStore()
        setContent {
            ProvideKanjiReadings(preferences = preferences, load = load) {
                provided = LocalKanjiReadings.current
            }
        }

        waitUntil(timeoutMillis = 120_000) { provided != null }
        assertSame(kanjidicFixture, provided)
    }

    /**
     * The app root is where the readings are provided, so a screen the
     * shell hosts has to see them. A shell that forgot would leave every
     * screen's furigana whole with every test of those screens still
     * green, since they provide the readings themselves.
     */
    @Test
    fun `a screen the shell hosts sees the readings`() = runComposeUiTest {
        probedReadings = null
        var search: HomeSectionProbe? = null
        setContent {
            HomeScreen(
                coachMarks = remember { CoachMarkRegistry() },
                preferences = remember { FakePreferenceStore() },
                onSection = { key, probe -> if (key == homeSearchItem.key) search = probe },
                loadKanjiReadings = { kanjidicFixture },
            )
        }
        waitForIdle()

        // Pushed rather than made a section's root: a root is serialized
        // into saved state as soon as it is remembered, and the probe is
        // not one of the app's registered routes.
        runOnIdle { checkNotNull(search).controller.navigate(KanjiReadingsProbeRoute) }
        waitForIdle()

        assertSame(kanjidicFixture, probedReadings)
    }
}

/** What [KanjiReadingsProbeRoute] last saw. */
private var probedReadings: KanjiReadings? = null

/** A screen that only records the readings it was drawn with. */
@Serializable
internal data object KanjiReadingsProbeRoute : Route {
    @Composable
    override fun Content() {
        probedReadings = LocalKanjiReadings.current
    }
}
