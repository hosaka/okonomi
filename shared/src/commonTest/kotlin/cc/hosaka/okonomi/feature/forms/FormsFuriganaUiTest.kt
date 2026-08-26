package cc.hosaka.okonomi.feature.forms

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.EntryForm
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.test.entryDetail
import cc.hosaka.okonomi.ui.test.entrySense
import kotlin.test.Test

/**
 * The Forms table drawn through the real tab, with and without
 * furigana. A cell carrying a reading is laid out as inline content —
 * on Android a platform `TextView` inside the line — so this is also
 * where that path is exercised at all: the rule deciding which cells
 * get one is [ConjugationFuriganaTest]'s.
 *
 * The forms themselves are what the reader copies out of the table, so
 * every assertion is on the written form. Setting a reading over it must
 * not change it.
 */
@OptIn(ExperimentalTestApi::class)
class FormsFuriganaUiTest : ComposeUiTestBase() {

    @Test
    fun `an irregular verb still shows its forms with readings over them`() = runComposeUiTest {
        setContent {
            FormsUnderTest(entry = suru())
        }

        cell("為る").assertIsDisplayed()
        cell("為ない").assertIsDisplayed()
        cell("為せられる").assertExists()
        // The potential's stem is constant across the table and stays plain.
        cell("出来る").assertExists()
    }

    @Test
    fun `a regular verb's table is unchanged`() = runComposeUiTest {
        setContent {
            FormsUnderTest(entry = taberu())
        }

        cell("食べる").assertIsDisplayed()
        cell("食べさせられる").assertExists()
    }
}

/**
 * A cell of the table. The unmerged tree is the only place they are:
 * each row replaces its children's semantics with one description read
 * as a sentence, so a screen reader gets the row rather than three
 * unrelated strings. Rows past the first screenful are composed — the
 * grid is a single lazy item — but not displayed, so a cell far down
 * the table is asserted to exist rather than to be on screen.
 */
private fun SemanticsNodeInteractionsProvider.cell(text: String) =
    onNodeWithText(text, useUnmergedTree = true)

private fun suru() = entryDetail(
    headword = "為る",
    forms = listOf(EntryForm("為る", isCommon = true)),
    readings = listOf(EntryReading("する", emptyList(), isCommon = true)),
    senses = listOf(entrySense(posCodes = listOf("vs-i"), glosses = listOf("to do"))),
)

private fun taberu() = entryDetail(
    headword = "食べる",
    forms = listOf(EntryForm("食べる", isCommon = true)),
    readings = listOf(EntryReading("たべる", emptyList(), isCommon = true)),
    senses = listOf(entrySense(posCodes = listOf("v1"), glosses = listOf("to eat"))),
)

@Composable
private fun FormsUnderTest(entry: EntryDetail) {
    ScreenHost {
        FormsTab(entry = entry, contentPadding = PaddingValues())
    }
}
