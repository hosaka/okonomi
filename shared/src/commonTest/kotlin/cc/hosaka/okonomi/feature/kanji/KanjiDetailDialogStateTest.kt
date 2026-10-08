package cc.hosaka.okonomi.feature.kanji

import androidx.compose.runtime.saveable.SaverScope
import cc.hosaka.okonomi.db.KanjiCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure pieces of the detail overlay: which characters have anything
 * to show, what showing and dismissing do to the selection, which
 * character a selection resolves to in a list, and what the selection
 * saves across activity recreation.
 *
 * The dismiss tests carry more weight than their size suggests. One of
 * the two gestures that call [KanjiDetailDialogState.dismiss] is a
 * system back press, which arrives through `dismissOnBackPress` and
 * cannot be dispatched from a host test, so this is where the transition
 * it causes is actually checked. The other, a tap outside the surface,
 * is [KanjiDetailDialog]'s own composable and is driven directly in
 * `KanjiDetailDialogUiTest`.
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

    /** Null is closed: it is what [KanjiDetailDialog] is handed then. */
    @Test
    fun `a new state is closed`() {
        val state = KanjiDetailDialogState()

        assertNull(state.selectedLiteral)
        assertNull(state.characterIn(listOf(shoku)))
    }

    @Test
    fun `show selects the character it was given`() {
        val state = KanjiDetailDialogState()

        state.show(shoku)

        assertEquals("食", state.selectedLiteral)
        assertEquals(shoku, state.characterIn(listOf(sei, shoku)))
    }

    @Test
    fun `dismiss closes the overlay and clears the selection`() {
        val state = KanjiDetailDialogState()
        state.show(shoku)

        state.dismiss()

        assertNull(state.selectedLiteral)
        assertNull(state.characterIn(listOf(shoku)))
    }

    /**
     * Tapping a second card while the first overlay is somehow still up
     * replaces the selection rather than being ignored, so the overlay
     * can never show a character other than the one last asked for.
     */
    @Test
    fun `showing a second character replaces the first`() {
        val state = KanjiDetailDialogState()

        state.show(shoku)
        state.show(sei)

        assertEquals(sei, state.characterIn(listOf(shoku, sei)))
    }

    @Test
    fun `dismissing a closed overlay is a no-op`() {
        val state = KanjiDetailDialogState()

        state.dismiss()

        assertNull(state.selectedLiteral)
    }

    /**
     * The shown character is the list's own, not a copy taken at the
     * tap: a list that reloads with new data for the same literal shows
     * the new data.
     */
    @Test
    fun `the shown character is read from the list it is resolved against`() {
        val state = KanjiDetailDialogState()
        state.show(shoku)
        val reloaded = character(
            literal = shoku.literal,
            nameReadings = listOf("あき"),
            radicals = listOf("人"),
        )

        assertEquals(reloaded, state.characterIn(listOf(reloaded)))
    }

    /** What a rotation with the overlay open saves, and what it reopens. */
    @Test
    fun `an open overlay saves its literal and restores to the same character`() {
        val state = KanjiDetailDialogState()
        state.show(shoku)

        val saved = with(KanjiDetailDialogState.Saver) { AcceptingSaverScope.save(state) }
        val restored = KanjiDetailDialogState.Saver.restore(requireNotNull(saved))

        // Bundle-safe is the claim: only a String is saved.
        assertTrue(saved is String)
        assertEquals("食", saved)
        assertEquals(shoku, requireNotNull(restored).characterIn(listOf(sei, shoku)))
    }

    /**
     * A closed overlay saves nothing, so a rotation leaves it closed:
     * `rememberSaveable` builds a fresh state when nothing was saved.
     */
    @Test
    fun `a closed overlay saves nothing`() {
        val saved = with(KanjiDetailDialogState.Saver) {
            AcceptingSaverScope.save(KanjiDetailDialogState())
        }

        assertNull(saved)
    }

    /** Dismissal has to reach saved state, or a rotation would reopen it. */
    @Test
    fun `a dismissed overlay saves nothing`() {
        val state = KanjiDetailDialogState()
        state.show(shoku)
        state.dismiss()

        val saved = with(KanjiDetailDialogState.Saver) { AcceptingSaverScope.save(state) }

        assertNull(saved)
    }

    @Test
    fun `a restored literal the list does not carry resolves to closed and is cleared`() {
        val state = KanjiDetailDialogState(initialLiteral = "食")

        assertNull(state.characterIn(listOf(sei)))
        state.dismissIfUnresolved(listOf(sei))

        assertNull(state.selectedLiteral)
    }

    /**
     * The list carries the literal, but its character has nothing for
     * the overlay to hold. The literal matching is not enough.
     */
    @Test
    fun `a restored literal whose character has nothing to show resolves to closed and is cleared`() {
        val state = KanjiDetailDialogState(initialLiteral = "食")
        val empty = listOf(character(nameReadings = emptyList(), radicals = emptyList()))

        assertNull(state.characterIn(empty))
        state.dismissIfUnresolved(empty)

        assertNull(state.selectedLiteral)
    }

    /** The clearing only touches what cannot be shown. */
    @Test
    fun `a selection the list can show is kept`() {
        val state = KanjiDetailDialogState(initialLiteral = "食")

        state.dismissIfUnresolved(listOf(shoku))

        assertEquals("食", state.selectedLiteral)
    }
}

/**
 * Its radical, 人, differs from its literal on purpose, so an assertion
 * on 食 can never be satisfied by a radical instead.
 */
private val shoku = character(nameReadings = listOf("ぐい"), radicals = listOf("人"))

private val sei = character(literal = "生", nameReadings = listOf("あさ"), radicals = listOf("土"))

private object AcceptingSaverScope : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
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
