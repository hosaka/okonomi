package cc.hosaka.okonomi.feature.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class EntryLinksTest {

    @Test
    fun `an entry link yields its id`() {
        assertEquals(1_547_720L, parseEntryLink("okonomi://entry/1547720"))
    }

    @Test
    fun `the scheme and host are matched ignoring case as URIs are`() {
        assertEquals(42L, parseEntryLink("OKONOMI://Entry/42"))
    }

    @Test
    fun `anything but one positive decimal id is not an entry link`() {
        listOf(
            "okonomi://entry/",
            "okonomi://entry/-5",
            "okonomi://entry/+5",
            "okonomi://entry/0",
            "okonomi://entry/12a",
            "okonomi://entry/1.5",
            "okonomi://entry/ 12",
            "okonomi://entry/12/extra",
            "okonomi://entry/12?x=1",
            "okonomi://entry/12#top",
            "okonomi://entry/99999999999999999999",
            "okonomi://kanji/12",
            "okonomi://entry12",
            "https://entry/12",
            "",
        ).forEach { link ->
            assertNull(parseEntryLink(link), link)
        }
    }

    @Test
    fun `a link offered before anything listens is delivered once something does`() = runTest {
        val links = EntryLinks()

        assertTrue(links.open("okonomi://entry/7"))

        assertEquals(7L, links.requests.first())
    }

    @Test
    fun `a malformed link is refused and queues nothing`() = runTest {
        val links = EntryLinks()

        assertFalse(links.open("okonomi://entry/nope"))
        assertTrue(links.open("okonomi://entry/8"))

        assertEquals(8L, links.requests.first())
    }
}
