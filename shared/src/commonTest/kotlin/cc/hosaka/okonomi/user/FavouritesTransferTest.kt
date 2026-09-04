package cc.hosaka.okonomi.user

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The export file format, on its own: no file dialog, no database, no
 * screen. Everything the I/O matrix says about the bytes is decided
 * here, so the rest of the feature only has to move a string around.
 */
class FavouritesTransferTest {

    @Test
    fun `a list survives being written out and read back in the same order`() {
        val ids = listOf(1_207_610L, 1_000_220L, 42L)

        assertEquals(ids, decodeFavourites(encodeFavourites(ids)))
    }

    /**
     * The matrix's dangling-id row, from the file's side. An id the
     * bundled dictionary no longer carries never reaches this codec as
     * anything special, and it must not be treated as anything special:
     * the export is the reader's saved ids, not the ones that resolved.
     */
    @Test
    fun `an id no dictionary carries is written like any other`() {
        val json = encodeFavourites(listOf(9_999_999L))

        assertTrue(json.contains("9999999"), json)
        assertEquals(listOf(9_999_999L), decodeFavourites(json))
    }

    @Test
    fun `the written file says which version it is and what the list is called`() {
        val json = encodeFavourites(listOf(1L), name = "Favourites")

        assertTrue(json.contains("\"version\""), json)
        assertTrue(json.contains("Favourites"), json)
    }

    @Test
    fun `empty text is refused`() {
        // Not a curiosity: this is exactly what a file the system could
        // not read reaches the decoder as, so the "could not be read"
        // dialog depends on it. Were empty text ever to decode into an
        // empty list, a failed read would present the overwrite warning
        // instead, and confirming it would clear the reader's list.
        assertNull(decodeFavourites(""))
        assertNull(decodeFavourites("   \n  "))
    }

    @Test
    fun `text that is not json is refused`() {
        assertNull(decodeFavourites("not json at all"))
    }

    @Test
    fun `a file with no entries at all is refused`() {
        assertNull(decodeFavourites("""{"version":1,"name":"x"}"""))
    }

    @Test
    fun `a version this app does not know is refused`() {
        assertNull(decodeFavourites("""{"version":2,"name":"x","entries":[1]}"""))
    }

    @Test
    fun `a file that does not say its version is refused`() {
        assertNull(decodeFavourites("""{"name":"x","entries":[1]}"""))
    }

    @Test
    fun `an entry that is not a number is refused`() {
        assertNull(decodeFavourites("""{"version":1,"name":"x","entries":["nine"]}"""))
    }

    /**
     * The one thing `version` exists for: a later format has to be
     * refusable, and refusing it must not depend on the rest of the file
     * being unreadable. Here everything else parses perfectly.
     */
    @Test
    fun `a later version is refused even when the rest of the file is readable`() {
        assertNull(decodeFavourites("""{"version":2,"name":"x","entries":[1,2,3]}"""))
    }

    @Test
    fun `keys this version does not know are ignored rather than fatal`() {
        assertEquals(
            listOf(1L),
            decodeFavourites("""{"version":1,"name":"x","entries":[1],"colour":"red"}"""),
        )
    }

    /** The list's name is written, and then deliberately thrown away on the way back in. */
    @Test
    fun `a file with no name is still importable`() {
        assertEquals(listOf(1L), decodeFavourites("""{"version":1,"entries":[1]}"""))
    }

    @Test
    fun `a repeated id is kept once in the place it first appeared`() {
        assertEquals(
            listOf(5L, 9L),
            decodeFavourites("""{"version":1,"name":"x","entries":[5,5,9]}"""),
        )
    }

    /** Empty is a list, not a failure: it is what importing an emptied list means. */
    @Test
    fun `an empty entries list is a valid empty list`() {
        assertEquals(
            emptyList(),
            decodeFavourites("""{"version":1,"name":"x","entries":[]}"""),
        )
    }
}
