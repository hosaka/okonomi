package cc.hosaka.okonomi.ui.furigana

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The one place the renderer could undo the aligner's caution.
 *
 * Laying a reading out in one cell per kana says each kana belongs over
 * one character. Over a single character that is a way of spacing;
 * over a run of them it is a division, and the aligner handed that run
 * over precisely because it would not divide it.
 *
 * Widths are given rather than measured — the decision is arithmetic,
 * and a UI test could not reach it anyway: the host runner measures
 * every glyph to nothing, so the width clause is never true there.
 */
class SpreadsPerCharacterTest {

    private val wide = 20f
    private val narrow = 8f

    @Test
    fun `a reading over one character is spread across it`() {
        // 山 with やま over it: two kana, and room for them.
        assertTrue(spreadsPerCharacter("山", "やま", baseWidth = wide, rubyWidth = narrow))
    }

    /**
     * The defect this exists for. 刑事 reads でか as a whole — 刑 is not
     * で and 事 is not か — and matching counts is not evidence of
     * anything. 2,399 shipped forms reach the renderer as one run, and
     * the short readings that would trigger a count match are the
     * jukujikun that have no per-character reading: 馬酔木/あせび,
     * 如何/どう, 発条/ばね.
     */
    @Test
    fun `a reading over a run of characters is never divided among them`() {
        listOf(
            "刑事" to "でか",
            "如何" to "どう",
            "馬酔木" to "あせび",
            "発条" to "ばね",
        ).forEach { (word, reading) ->
            assertFalse(
                spreadsPerCharacter(word, reading, baseWidth = wide, rubyWidth = narrow),
                "$word reads $reading as a whole",
            )
        }
    }

    @Test
    fun `a single kana over a single character is drawn as it is`() {
        assertFalse(spreadsPerCharacter("食", "た", baseWidth = wide, rubyWidth = narrow))
    }

    @Test
    fun `a reading wider than its base has nothing to spread into`() {
        assertFalse(spreadsPerCharacter("志", "こころざし", baseWidth = narrow, rubyWidth = wide))
    }
}
