package cc.hosaka.okonomi.ui.furigana

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import cc.hosaka.okonomi.ui.theme.atJapaneseReadingSize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The box a single line's reading has to be drawn inside.
 *
 * The numbers below are worked out by hand from the two scales
 * `FuriganaText` places the ruby with — ruby at 0.45 of the base, gap at
 * 0.03 — rather than read back from the function, so a change to the
 * formula fails here instead of agreeing with itself. The characters sit
 * centred in the line box, so the box offers half its height above their
 * middle, and the ruby's top reaches `fontSize / 2 + ruby + gap` above
 * that middle: twice the reach is the box.
 *
 * What this cannot see is a pixel being cut. The ruby is positioned by a
 * `graphicsLayer` translation over font metrics and drawn, on Android,
 * inside a platform `TextView`; neither the offset nor the ink reaches
 * anything a host test can measure, and the clipping itself happened at
 * the edge of a `LazyColumn` on a device. The geometry is what is
 * pinned. The pixels want Alex's eyes.
 */
class RubyRoomTest {

    @Test
    fun `the ruby-ready line height is twice the ruby's reach above the middle`() {
        // 45 / 2 + 45 * 0.45 + 45 * 0.03 = 44.1, doubled.
        assertEquals(88.2f, rubyReadyLineHeight(45.sp).value, 0.001f)
        // 22 / 2 + 22 * 0.45 + 22 * 0.03 = 21.56, doubled.
        assertEquals(43.12f, rubyReadyLineHeight(22.sp).value, 0.001f)
    }

    /**
     * The Word tab's headword, which is where this went wrong: 45sp text
     * in `displayMedium`'s own 52sp line box, which offers 3.5sp above
     * the characters where the reading reaches 21.6sp.
     */
    @Test
    fun `a line height short of the reading is raised to hold it`() {
        val style = TextStyle(fontSize = 45.sp, lineHeight = 52.sp).withRoomForRuby()

        assertEquals(88.2f, style.lineHeight.value, 0.001f)
        assertEquals(45f, style.fontSize.value, 0.001f, "only the line height moves")
    }

    /**
     * The 22sp reading size already states 44sp, and this must leave it
     * exactly alone — the two are one decision (see `atJapaneseReadingSize`)
     * and raising 44 to 43.12 would be lowering it.
     */
    @Test
    fun `the japanese reading size already has the room and is left alone`() {
        val style = TextStyle(fontSize = 16.sp, lineHeight = 24.sp).atJapaneseReadingSize()

        assertEquals(style, style.withRoomForRuby())
        assertEquals(44f, style.withRoomForRuby().lineHeight.value, 0.001f)
    }

    /**
     * A style that states no line height at all falls to the floor
     * rather than to whatever the platform would pick, which is the
     * shape a caller reaches by using a bare `TextStyle`.
     */
    @Test
    fun `a style stating no line height is given one`() {
        val style = TextStyle(fontSize = 22.sp).withRoomForRuby()

        assertEquals(43.12f, style.lineHeight.value, 0.001f)
    }

    /**
     * An em font size takes no part in this arithmetic — it cannot be
     * compared with a line height in sp — so such a style is handed back
     * untouched rather than given a line height computed from a number
     * that does not mean what it looks like.
     */
    @Test
    fun `an em font size is left untouched`() {
        val style = TextStyle(fontSize = 2.em, lineHeight = 24.sp)

        assertEquals(style, style.withRoomForRuby())
    }

    /**
     * An em *line height* is the opposite case and is replaced, however
     * generous it looks: `2.em` on a 45sp style is 90sp, more than the
     * 88.2sp needed, but [FuriganaText] takes a line height it cannot
     * read in sp as unstated and falls to its own floor. Handing the
     * caller their em back would hand them the clip back with it.
     */
    @Test
    fun `an em line height is replaced with the one FuriganaText can use`() {
        val style = TextStyle(fontSize = 45.sp, lineHeight = 2.em).withRoomForRuby()

        assertEquals(88.2f, style.lineHeight.value, 0.001f)
        assertEquals(TextUnitType.Sp, style.lineHeight.type, "and in the unit the renderer compares in")
    }
}
