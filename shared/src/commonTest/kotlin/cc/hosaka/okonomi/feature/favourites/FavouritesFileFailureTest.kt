package cc.hosaka.okonomi.feature.favourites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

/**
 * The two file-system edges of export and import, which the file dialogs
 * themselves put out of reach: no host test can drive a system save or
 * open dialog, so the failure policy is tested through the seam the
 * launcher callbacks call rather than through the launchers.
 *
 * What is deliberately NOT claimed here: that the launchers call these.
 * That wiring is only exercised by running the app.
 */
class FavouritesFileFailureTest {

    private class Reports {
        val messages = mutableListOf<String>()
        val causes = mutableListOf<Throwable?>()
        fun reporter(): (String, Throwable?) -> Unit = { message, cause ->
            messages += message
            causes += cause
        }
    }

    @Test
    fun `a file that cannot be written is reported rather than thrown`() = runTest {
        val reports = Reports()
        val failure = IllegalStateException("no space left on device")

        writeExport(json = "{}", report = reports.reporter()) { throw failure }

        assertEquals(1, reports.messages.size)
        assertTrue(reports.messages.single().contains("exported"), reports.messages.toString())
        assertEquals(failure, reports.causes.single())
    }

    @Test
    fun `a write that lands is not reported`() = runTest {
        val reports = Reports()
        var written: String? = null

        writeExport(json = "{\"version\":1}", report = reports.reporter()) { written = it }

        assertEquals("{\"version\":1}", written)
        assertTrue(reports.messages.isEmpty(), reports.messages.toString())
    }

    @Test
    fun `a file that cannot be read imports as text no file could hold`() = runTest {
        val reports = Reports()
        val failure = IllegalStateException("permission denied")

        val text = readImport(report = reports.reporter()) { throw failure }

        // Empty text is not a readable export either, so it reaches the
        // reader as the same "could not be read" dialog a malformed file
        // does. FavouritesTransferTest's `empty text is refused` is what
        // holds up that half of the claim.
        assertEquals("", text)
        assertEquals(failure, reports.causes.single())
    }

    @Test
    fun `a file that reads is handed on untouched and unreported`() = runTest {
        val reports = Reports()

        val text = readImport(report = reports.reporter()) { "{\"version\":1,\"entries\":[7]}" }

        assertEquals("{\"version\":1,\"entries\":[7]}", text)
        assertTrue(reports.messages.isEmpty(), reports.messages.toString())
    }

    @Test
    fun `cancellation is not swallowed by either of them`() = runTest {
        // The catch that swallows failures must not also swallow the
        // coroutine being cancelled: that would make a cancelled export
        // look like a completed one and keep the scope alive.
        val reports = Reports()

        assertFailsWith<CancellationException> {
            writeExport(json = "{}", report = reports.reporter()) { throw CancellationException("cancelled") }
        }
        assertFailsWith<CancellationException> {
            readImport(report = reports.reporter()) { throw CancellationException("cancelled") }
        }
        assertTrue(reports.messages.isEmpty(), reports.messages.toString())
    }
}
