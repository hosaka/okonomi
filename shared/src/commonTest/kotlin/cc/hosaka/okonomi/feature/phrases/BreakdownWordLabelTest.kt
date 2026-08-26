package cc.hosaka.okonomi.feature.phrases

import cc.hosaka.okonomi.db.BreakdownWord
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one place the readings resolved at build time become visible.
 *
 * dictgen goes to some length to give every kanji word a reading —
 * resolving each word to an entry so 行った reads いった — and all of
 * that is undone by a renderer that shows the word alone. That failure
 * has no other test: the pipeline would still write the readings, the
 * loader would still parse them, and nothing but a reader would notice
 * they had stopped appearing. So the rule lives in a pure function and
 * is stated here, on the precedent of `titleFurigana`/`TitleFuriganaTest`.
 */
class BreakdownWordLabelTest {

    /** The shipped `entry_phrases_word_reading` template. */
    private val format = "%1\$s (%2\$s)"

    @Test
    fun `a word with a reading is shown with it`() {
        assertEquals(
            "学校 (がっこう)",
            breakdownWordLabel(BreakdownWord("学校", "がっこう"), format),
        )
    }

    @Test
    fun `a word written in kana is shown alone`() {
        // Not a gap in the data: it reads as itself, and bracketing it
        // with a copy of itself would be noise on every particle.
        assertEquals(
            "どんな",
            breakdownWordLabel(BreakdownWord("どんな", null), format),
        )
    }

    @Test
    fun `the reading's brackets come from the template rather than the code`() {
        // A locale that writes readings differently changes
        // entry_phrases_word_reading and nothing else.
        assertEquals(
            "学校【がっこう】",
            breakdownWordLabel(BreakdownWord("学校", "がっこう"), "%1\$s【%2\$s】"),
        )
    }
}
