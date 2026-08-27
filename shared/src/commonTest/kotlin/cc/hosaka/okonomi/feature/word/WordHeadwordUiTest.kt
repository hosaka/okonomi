package cc.hosaka.okonomi.feature.word

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.entryDetail
import kotlin.test.Test
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_section_reading
import org.jetbrains.compose.resources.stringResource

/**
 * The Word tab's Reading section: it lists what the headword's own ruby
 * does not already show, which for the common case of a single reading
 * is nothing at all.
 *
 * Only the section is asserted here. Whether the ruby is drawn, and
 * which string it is, cannot be seen on this SDK — the ruby goes
 * through a platform `TextView` — and asserting the *absence* of a
 * reading here would be worse than nothing: it passes for the reason the
 * ruby is invisible, and would pass more easily if the ruby were
 * deleted. `RubyRenderingTest` holds that end on the SDK where it is
 * visible, and `EntryReadingsTest` holds which reading is chosen.
 */
@OptIn(ExperimentalTestApi::class)
class WordHeadwordUiTest : ComposeUiTestBase() {

    @Test
    fun `an entry with one reading shows no reading section`() = runComposeUiTest {
        lateinit var heading: String
        setContent {
            heading = stringResource(Res.string.entry_section_reading)
            WordTab(
                entry = entryDetail(
                    headword = "相殺",
                    forms = listOf(EntryForm("相殺", isCommon = true)),
                    readings = listOf(EntryReading("そうさい", emptyList(), isCommon = true)),
                ),
                contentPadding = PaddingValues(),
            )
        }

        onNodeWithText("相殺").assertIsDisplayed()
        onNodeWithText(heading).assertDoesNotExist()
    }

    @Test
    fun `a second reading is listed while the first stays over the headword`() = runComposeUiTest {
        lateinit var heading: String
        setContent {
            heading = stringResource(Res.string.entry_section_reading)
            WordTab(
                entry = entryDetail(
                    headword = "相殺",
                    forms = listOf(EntryForm("相殺", isCommon = true)),
                    readings = listOf(
                        EntryReading("そうさい", emptyList(), isCommon = true),
                        EntryReading("そうさつ", emptyList(), isCommon = false),
                    ),
                ),
                contentPadding = PaddingValues(),
            )
        }

        onNodeWithText(heading).assertIsDisplayed()
        // The second reading is listed; the first is the headword's ruby
        // and is not repeated as a row (EntryReadingsTest).
        onNodeWithText("そうさつ").assertIsDisplayed()
    }

    /**
     * The headword's line box is tall enough to hold the reading drawn
     * above it, which is the geometry the clipped dakuten came down to:
     * `displayMedium`'s own 52sp box leaves 3.5sp above 45sp characters
     * where the ruby reaches 21.6sp, so the rest was drawn above the
     * composable and lost to the top edge of this tab's `LazyColumn`.
     *
     * The requirement is stated here from the two scales the renderer
     * places the ruby with — ruby at 0.45 of the base, gap at 0.03,
     * the characters centred, so the box must be `1 + 2 * (0.45 + 0.03)`
     * times the font size — rather than read back from
     * `rubyReadyLineHeight`, so this cannot pass by agreeing with a
     * formula that has itself gone wrong. Dropping `withRoomForRuby`
     * from the headword leaves the box at 1.48 times the font size and
     * fails it.
     *
     * The clipped pixels themselves are not visible here; see
     * `RubyRoomTest`.
     */
    @Test
    fun `the headword's line box holds the reading drawn above it`() = runComposeUiTest {
        setContent {
            WordTab(
                entry = entryDetail(
                    headword = "葡萄棚",
                    forms = listOf(EntryForm("葡萄棚", isCommon = false)),
                    readings = listOf(EntryReading("ぶどうだな", emptyList(), isCommon = false)),
                ),
                contentPadding = PaddingValues(),
            )
        }

        val style = onNodeWithText("葡萄棚").textLayout().layoutInput.style
        val required = style.fontSize.value * (1f + 2f * (0.45f + 0.03f))
        assertTrue(
            style.lineHeight.value >= required - 0.001f,
            "the headword needs a ${required}sp line box for its ruby, got ${style.lineHeight}",
        )
    }

    /**
     * And a headword with no reading to draw does not pay for the room.
     * ありがとう aligns to segments carrying no ruby at all, so the
     * taller box would be 43.2sp of empty page above and below a word
     * that will never have anything over it.
     *
     * Asserted against the same requirement the case above uses, so the
     * pair states one rule from both sides rather than two numbers.
     */
    @Test
    fun `a headword with no reading takes the ordinary line box`() = runComposeUiTest {
        setContent {
            WordTab(
                entry = entryDetail(
                    headword = "ありがとう",
                    forms = emptyList(),
                    readings = listOf(EntryReading("ありがとう", emptyList(), isCommon = true)),
                ),
                contentPadding = PaddingValues(),
            )
        }

        val style = onNodeWithText("ありがとう").textLayout().layoutInput.style
        val roomForRuby = style.fontSize.value * (1f + 2f * (0.45f + 0.03f))
        assertTrue(
            style.lineHeight.value < roomForRuby,
            "nothing is drawn above these characters, got a ${style.lineHeight} box",
        )
    }

    /**
     * A word written in kana alone is its own reading. It gets no ruby
     * and no section repeating the headword underneath it.
     */
    @Test
    fun `a kana headword stands alone`() = runComposeUiTest {
        lateinit var heading: String
        setContent {
            heading = stringResource(Res.string.entry_section_reading)
            WordTab(
                entry = entryDetail(
                    headword = "ラーメン",
                    forms = emptyList(),
                    readings = listOf(EntryReading("ラーメン", emptyList(), isCommon = false)),
                ),
                contentPadding = PaddingValues(),
            )
        }

        onNodeWithText("ラーメン").assertIsDisplayed()
        onNodeWithText(heading).assertDoesNotExist()
    }
}

/**
 * The layout the node reports, which is where the resolved line height
 * can be read; the semantics tree itself carries only the string.
 */
private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    val action = fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)
    assertTrue(action?.action?.invoke(results) == true, "the node reports no text layout")
    return results.first()
}
