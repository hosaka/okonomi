package cc.hosaka.okonomi.feature.navigation

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Android side of links and lookups, which both activities are thin
 * calls into: what `ProcessTextActivity` forwards, and what
 * `MainActivity` hands the shell.
 *
 * Robolectric for real `Intent`s and extras. What it cannot show is
 * where Android puts each activity, whether the selection toolbar offers
 * "Okonomi", and what `Activity.getReferrer` reports; those are checked
 * on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AndroidHandOffsTest {

    private val mainActivity = ComponentName("cc.hosaka.okonomi", "cc.hosaka.okonomi.MainActivity")

    /** What the selection toolbar starts `ProcessTextActivity` with. */
    private fun selection(text: CharSequence?) = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .apply { if (text != null) putExtra(Intent.EXTRA_PROCESS_TEXT, text) }

    private fun link(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    private fun forward(text: CharSequence, fromOkonomi: Boolean = false): Intent =
        assertNotNull(processTextForward(selection(text), mainActivity, fromOkonomi))

    /**
     * Null when [requests] has nothing waiting. Virtual time, so the wait
     * costs nothing; the queues are conflated, so anything queued would be
     * the one thing `first()` could see.
     */
    private suspend fun <T> nextOrNull(requests: Flow<T>): T? = withTimeoutOrNull(1.seconds) { requests.first() }

    @Test
    fun `the forward names MainActivity and carries the tidied query into a new task`() {
        val forward = forward(" 食べ\nた ")

        assertEquals(mainActivity, forward.component)
        assertEquals(Intent.ACTION_PROCESS_TEXT, forward.action)
        assertEquals("食べた", forward.getStringExtra(Intent.EXTRA_PROCESS_TEXT))
        assertTrue(forward.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `the forward says whether the text was selected in okonomi itself`() {
        // Defaults opposite to the expected value, so a missing extra fails.
        assertTrue(forward("食べた", fromOkonomi = true).getBooleanExtra(EXTRA_FROM_OKONOMI, false))
        assertFalse(forward("食べた", fromOkonomi = false).getBooleanExtra(EXTRA_FROM_OKONOMI, true))
    }

    @Test
    fun `a blank or missing selection forwards nothing`() {
        assertNull(processTextForward(selection("  \n"), mainActivity, fromOkonomi = false))
        assertNull(processTextForward(selection(null), mainActivity, fromOkonomi = false))
    }

    @Test
    fun `a selection replayed from Recents forwards nothing`() {
        val replay = selection("食べた").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        assertNull(processTextForward(replay, mainActivity, fromOkonomi = false))
    }

    @Test
    fun `a link reaches the link queue and nothing else`() = runTest {
        val links = EntryLinks()
        val lookups = TextLookups()

        handOffToShell(link("okonomi://entry/1360010"), startedForResult = false, links, lookups)

        assertEquals(1_360_010L, nextOrNull(links.requests))
        assertNull(nextOrNull(lookups.requests))
    }

    @Test
    fun `a forwarded lookup reaches the lookup queue with its from-okonomi flag`() = runTest {
        val lookups = TextLookups()

        handOffToShell(forward("食べた", fromOkonomi = true), startedForResult = false, EntryLinks(), lookups)
        assertEquals(TextLookup("食べた", fromOkonomi = true), nextOrNull(lookups.requests))

        handOffToShell(forward("飲む", fromOkonomi = false), startedForResult = false, EntryLinks(), lookups)
        assertEquals(TextLookup("飲む", fromOkonomi = false), nextOrNull(lookups.requests))
    }

    @Test
    fun `a lookup in an activity started for a result queues nothing`() = runTest {
        val lookups = TextLookups()

        handOffToShell(forward("食べた"), startedForResult = true, EntryLinks(), lookups)

        assertNull(nextOrNull(lookups.requests))
    }

    @Test
    fun `a link or lookup replayed from Recents queues nothing`() = runTest {
        val links = EntryLinks()
        val lookups = TextLookups()

        handOffToShell(
            link("okonomi://entry/1360010").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY),
            startedForResult = false,
            links,
            lookups,
        )
        handOffToShell(
            forward("食べた").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY),
            startedForResult = false,
            links,
            lookups,
        )

        assertNull(nextOrNull(links.requests))
        assertNull(nextOrNull(lookups.requests))
    }
}
