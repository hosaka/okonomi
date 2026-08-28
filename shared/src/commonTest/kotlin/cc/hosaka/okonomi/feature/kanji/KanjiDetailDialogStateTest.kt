package cc.hosaka.okonomi.feature.kanji

import cc.hosaka.okonomi.db.KanjiCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two pure pieces of the detail overlay: which characters have
 * anything to show, and what showing and dismissing do to the selection.
 *
 * These carry more weight than their size suggests. The gestures that
 * call [KanjiDetailDialogState.dismiss] are `DialogProperties` defaults
 * — a tap on the scrim and a system back press — and neither can be
 * driven from a host test, so this is where the transition they cause is
 * actually checked. See [KanjiDetailDialog].
 */
class KanjiDetailDialogStateTest {

    @Test
    fun `a character with both nanori and radicals has something to show`() {
        assertTrue(character(nameReadings = listOf("ぐい"), radicals = listOf("食")).hasDetailToShow)
    }

    @Test
    fun `radicals alone are enough to show`() {
        assertTrue(character(nameReadings = emptyList(), radicals = listOf("儿")).hasDetailToShow)
    }

    @Test
    fun `nanori alone is enough to show`() {
        assertTrue(character(nameReadings = listOf("ぐい"), radicals = emptyList()).hasDetailToShow)
    }

    @Test
    fun `a character with neither has nothing to show`() {
        assertFalse(character(nameReadings = emptyList(), radicals = emptyList()).hasDetailToShow)
    }

    /**
     * Every kanjidic-less character is the empty case unless radkfile
     * happens to know it, so the predicate must not be reading
     * `hasData`: 兀 carries no kanjidic row at all and still has a
     * radical worth opening.
     */
    @Test
    fun `a character kanjidic does not carry still opens on its radical`() {
        val unknown = character(
            nameReadings = emptyList(),
            radicals = listOf("儿"),
            strokeCount = null,
        )

        assertFalse(unknown.hasData)
        assertTrue(unknown.hasDetailToShow)
    }

    /** Null is closed: it is what [KanjiDetailDialog] branches on. */
    @Test
    fun `a new state is closed`() {
        val state = KanjiDetailDialogState()

        assertNull(state.character)
    }

    @Test
    fun `show selects the character it was given`() {
        val state = KanjiDetailDialogState()
        val shoku = character(nameReadings = listOf("ぐい"), radicals = listOf("食"))

        state.show(shoku)

        assertEquals(shoku, state.character)
    }

    @Test
    fun `dismiss closes the overlay and clears the selection`() {
        val state = KanjiDetailDialogState()
        state.show(character(nameReadings = listOf("ぐい"), radicals = listOf("食")))

        state.dismiss()

        assertNull(state.character)
    }

    /**
     * Tapping a second card while the first overlay is somehow still up
     * replaces the selection rather than being ignored, so the overlay
     * can never show a character other than the one last asked for.
     */
    @Test
    fun `showing a second character replaces the first`() {
        val state = KanjiDetailDialogState()
        val shoku = character(nameReadings = listOf("ぐい"), radicals = listOf("食"))
        val sei = character(literal = "生", nameReadings = listOf("あさ"), radicals = listOf("土"))

        state.show(shoku)
        state.show(sei)

        assertEquals(sei, state.character)
    }

    @Test
    fun `dismissing a closed overlay is a no-op`() {
        val state = KanjiDetailDialogState()

        state.dismiss()

        assertNull(state.character)
    }
}

private fun character(
    nameReadings: List<String>,
    radicals: List<String>,
    literal: String = "食",
    strokeCount: Long? = 9L,
) = KanjiCharacter(
    literal = literal,
    strokeCount = strokeCount,
    grade = null,
    jlpt = null,
    freq = null,
    onReadings = listOf("ショク"),
    kunReadings = listOf("た.べる"),
    nameReadings = nameReadings,
    meanings = listOf("eat"),
    radicals = radicals,
    strokePaths = emptyList(),
)
