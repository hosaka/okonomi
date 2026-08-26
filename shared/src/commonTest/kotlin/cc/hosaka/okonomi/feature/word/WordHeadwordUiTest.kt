package cc.hosaka.okonomi.feature.word

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.entryDetail
import kotlin.test.Test
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
