package cc.hosaka.okonomi

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.WindowInsets
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy

/**
 * The README's screenshots, captured by driving the real app.
 *
 * This is not an assertion suite. It is the capture flow fastlane's
 * `screenshots` lane runs, and it lives in `:androidApp` rather than
 * `:shared` because only the app APK carries the `okonomi.db` asset
 * every screen here reads.
 *
 * The lane runs it twice, once per theme. The theme itself is the
 * device's night mode, set over adb between the two runs — switching it
 * from inside the test would recreate [MainActivity] under the compose
 * rule, which declares no `configChanges` for `uiMode`. The `theme`
 * launch argument therefore only names the files; `assertThemeMatches`
 * is what stops a silently failed `cmd uimode` from producing two
 * identical sets under two different names.
 *
 * Every wait here is on content the screen can only show once the work
 * behind it finished, and then on the screen having actually been drawn.
 * Both halves are needed, and neither is a sleep.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    // The v1 entry point, which compiles with a deprecation warning pointing
    // at androidx.compose.ui.test.junit4.v2. Deliberate: v2 runs composition
    // effects on a StandardTestDispatcher, which queues them instead of
    // running them eagerly, and this drives the whole app rather than one
    // composable. The unconfined behaviour is what the flow below was
    // verified against end to end.
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        // The UiAutomator strategy grabs the whole screen through the
        // framework rather than drawing the activity's view hierarchy,
        // so the status bar is in the picture.
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    @Test
    fun captureReadmeScreenshots() {
        val theme = InstrumentationRegistry.getArguments().getString(THEME_ARGUMENT) ?: DEFAULT_THEME
        assertThemeMatches(theme)

        // The activity is launching while this runs, so the field is waited
        // for rather than asked for: onNode throws on the spot instead of
        // waiting, and the failure would name a missing semantics node
        // rather than a screen that had not composed yet.
        await("the search field", FIRST_CONTENT_TIMEOUT_MS) { anyNode(hasSetTextAction()) }
        composeRule.onNode(hasSetTextAction()).performTextInput(QUERY)
        // The only wait that rides out provisioning: the first search opens a
        // 184 MB dictionary copied out of the APK's assets.
        await("the $HEADWORD search result", FIRST_CONTENT_TIMEOUT_MS) { anyNodeWithText(HEADWORD) }
        dismissKeyboard()
        capture("1_search_$theme")

        // The entry's tab bar, not its headword: the search row behind it
        // carries the same headword and is still in the tree for the frames
        // the push takes, so waiting on the word would pass instantly and
        // capture the entry's loading spinner. The bar is drawn only once
        // the entry is loaded.
        composeRule.onAllNodesWithText(HEADWORD).onFirst().performClick()
        await("the $HEADWORD entry") { anyNodeWithContentDescription(WORD_TAB) }
        capture("2_word_$theme")

        openTab(KANJI_TAB)
        await("a kanji card") { anyNodeWithText(KANJI_MEANINGS_LABEL) }
        capture("3_kanji_$theme")

        openTab(PHRASES_TAB)
        // Scoped to this tab in both directions, and it has to be.
        //
        // A sentence card is long-pressable, which is what tells it apart
        // from the spinner and the empty state — but a kanji card is too:
        // both are the same ListCard with the same copy action, and the
        // semantics tree is searched whole. Opening Phrases animates the
        // pager from Kanji, so a long-click-only wait was satisfied by the
        // tab being left, and the shutter was one stable frame away.
        // "Meanings" appears on kanji cards and nowhere else, so its absence
        // is what says that page is gone.
        await("an example sentence card") {
            !anyNodeWithText(KANJI_MEANINGS_LABEL) &&
                anyNode(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        }
        capture("4_phrases_$theme")
    }

    /**
     * Fails when the device is not in the night mode the run was asked
     * for. Without this the `cmd uimode night` that sets it could stop
     * working and both passes would capture the same theme under two
     * names — the one failure the screenshots themselves cannot show,
     * because each set looks correct on its own.
     */
    private fun assertThemeMatches(theme: String) {
        val nightMask = composeRule.activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        val expected = when (theme) {
            "dark" -> Configuration.UI_MODE_NIGHT_YES
            "light" -> Configuration.UI_MODE_NIGHT_NO
            else -> error("Unknown $THEME_ARGUMENT launch argument \"$theme\": expected light or dark")
        }
        assertEquals(
            "Device night mode does not match the requested \"$theme\" theme. The screenshots lane " +
                "sets it with `adb shell cmd uimode night`; run by hand " +
                "(-Pandroid.testInstrumentationRunnerArguments.theme=$theme, or adb am instrument " +
                "-e theme $theme) the device has to be put in that mode first.",
            expected,
            nightMask,
        )
    }

    /**
     * Typing raises the IME, which covers half the screen. The field's
     * own IME action is what puts it away: the search screen passes no
     * `onSearch`, so the action clears focus, and losing focus is what
     * ends the input session.
     *
     * Both halves are waited for. Focus is what the app controls, and
     * the IME window is what the picture shows — it keeps animating out
     * for a few frames after focus is gone, and the insets are where
     * that is observable. The compose clock cannot see it: the IME is
     * not a compose animation.
     */
    private fun dismissKeyboard() {
        composeRule.onNode(hasSetTextAction()).performImeAction()
        await("the search field to lose focus", FOCUS_TIMEOUT_MS) {
            !anyNode(hasSetTextAction() and isFocused())
        }
        await("the keyboard to close", FOCUS_TIMEOUT_MS) { !imeVisible() }
    }

    /**
     * Whether the IME window is on screen. Below API 30 there is no way
     * to ask, so this reports it gone and the wait above passes straight
     * through; the lane runs API 35, where the answer is real.
     */
    private fun imeVisible(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return composeRule.runOnUiThread {
            composeRule.activity.window.decorView.rootWindowInsets
                ?.isVisible(WindowInsets.Type.ime()) == true
        }
    }

    /**
     * Switches to a tab, waiting for the bar to carry it first. Reaching
     * straight for the node threw a bare "expected exactly one node" that
     * named neither the tab nor what the screen was showing instead.
     */
    private fun openTab(label: String) {
        await("the $label tab") { anyNodeWithContentDescription(label) }
        composeRule.onAllNodesWithContentDescription(label).onFirst().performClick()
    }

    private fun anyNode(matcher: SemanticsMatcher): Boolean =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun anyNodeWithText(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun anyNodeWithContentDescription(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    /**
     * Waits for [condition], naming [what] when it never holds. A timeout
     * here means the screen still held a spinner, an empty state or the
     * wrong screen entirely, and capturing that is worse than failing.
     *
     * The default timeout is the short one. Only the first two waits pass
     * [FIRST_CONTENT_TIMEOUT_MS]: they are the ones that ride out the
     * dictionary being provisioned, and giving every wait that budget
     * meant a genuine breakage cost five minutes per failed wait per
     * theme before it said anything.
     */
    private fun await(
        what: String,
        timeoutMillis: Long = CONTENT_TIMEOUT_MS,
        condition: () -> Boolean,
    ) {
        try {
            composeRule.waitUntil(timeoutMillis, condition)
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Timed out after $timeoutMillis ms waiting for $what", timeout)
        }
    }

    /**
     * One screenshot, once compose has nothing left to do and the screen
     * has stopped changing.
     */
    private fun capture(name: String) {
        composeRule.waitForIdle()
        awaitStableFrame(name)
        Screengrab.screenshot(name)
    }

    /**
     * Waits until [STABLE_COMPARISONS_REQUIRED] consecutive grabs of the
     * screen match the one before them — three identical frames in a row
     * for the value of 2 this uses.
     *
     * Compose being idle is not the same as the picture being finished.
     * The rule drives composition on its own clock, but the frames it
     * produces still have to be drawn and composited, and on a
     * software-rendered emulator that lags by a visible amount: the first
     * run of this test captured spinners on screens whose content the
     * semantics tree already held, and a tab bar caught halfway through
     * its expand animation. The pixels are what the README shows, so the
     * pixels are what is waited on.
     *
     * More than one comparison, because a slow compositor can hand back
     * the same stale frame twice in a row while the next one is still
     * being drawn. [STABLE_POLL_INTERVAL_MS] between grabs, because each
     * one allocates a full-screen bitmap on the same emulator whose
     * compositor this is measuring: polling flat out competes with the
     * work it is waiting for.
     *
     * Nothing on these screens animates once its content is in — the
     * status bar is in demo mode and the search field's caret is gone
     * with its focus — so a screen that will not settle is one still
     * loading, which the content waits above are there to prevent. If it
     * happens anyway this captures at the deadline rather than failing
     * the run, and says so in the log: a half-drawn image is worth
     * having next to the reason it is half-drawn.
     */
    private fun awaitStableFrame(name: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + STABLE_FRAME_TIMEOUT_MS
        var previous: Bitmap? = null
        var matches = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val current = automation.takeScreenshot()
            if (current == null) {
                SystemClock.sleep(STABLE_POLL_INTERVAL_MS)
                continue
            }
            matches = if (previous?.sameAs(current) == true) matches + 1 else 0
            previous?.recycle()
            previous = current
            if (matches >= STABLE_COMPARISONS_REQUIRED) {
                previous.recycle()
                return
            }
            SystemClock.sleep(STABLE_POLL_INTERVAL_MS)
        }
        previous?.recycle()
        Log.w(
            TAG,
            "Screen never stopped changing within $STABLE_FRAME_TIMEOUT_MS ms; " +
                "capturing $name anyway, which may be a half-drawn frame.",
        )
    }

    private companion object {
        const val TAG = "ScreenshotTest"

        const val THEME_ARGUMENT = "theme"
        const val DEFAULT_THEME = "light"

        /** Ranks first for [QUERY], so the row is on screen without scrolling. */
        const val HEADWORD = "頑張る"
        const val QUERY = "がんば"

        /**
         * Tab labels and the kanji card's section label, in en-US. These are
         * `entry_tab_word`, `entry_tab_kanji`, `entry_tab_phrases` and
         * `entry_kanji_section_meanings` in the shared compose resources; the
         * lane captures one locale, so they are written out here rather than
         * read back through a resource accessor `:androidApp` does not have.
         */
        const val WORD_TAB = "Word"
        const val KANJI_TAB = "Kanji"
        const val PHRASES_TAB = "Phrases"
        const val KANJI_MEANINGS_LABEL = "Meanings"

        /** For the two waits that sit behind provisioning the dictionary. */
        const val FIRST_CONTENT_TIMEOUT_MS = 300_000L

        /** For every wait after it, where the database is already open. */
        const val CONTENT_TIMEOUT_MS = 60_000L

        const val FOCUS_TIMEOUT_MS = 10_000L

        const val STABLE_FRAME_TIMEOUT_MS = 30_000L
        const val STABLE_COMPARISONS_REQUIRED = 2
        const val STABLE_POLL_INTERVAL_MS = 100L
    }
}
