package cc.hosaka.okonomi.feature.forms

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * One conjugation is one thing to a screen reader.
 *
 * The table shows a conjugation's name on a band above the affirmative
 * and negative rather than in a column beside them. That is a layout
 * choice, and the point of these tests is that it stays only a layout
 * choice: the name must not become a node of its own, because a reader
 * moving through the table would then hear "Non-past" and have to
 * remember it while two unlabelled forms arrive separately.
 *
 * There was no test over this contract when the label moved out of its
 * column, which is exactly when it could have been lost — the existing
 * Forms tests assert on cell text in the UNMERGED tree, so they pass
 * either way. These read the merged tree, which is what an accessibility
 * service actually walks.
 */
@OptIn(ExperimentalTestApi::class)
class FormsRowSemanticsUiTest : ComposeUiTestBase() {

    @Test
    fun `a conjugation is announced as its name and both of its forms together`() = runComposeUiTest {
        setContent {
            FormsUnderTest(entry = taberu())
        }

        // Exactly the sentence entry_forms_row_description builds, and
        // exact rather than substring on purpose: "Non-past" is a prefix
        // of "Non-past polite", so a loose match would pass on the wrong
        // row and would still pass if the two were merged.
        onNodeWithContentDescription("Non-past. Affirmative: 食べる. Negative: 食べない.")
            .assertExists()
    }

    @Test
    fun `the conjugation name is not a node a reader lands on by itself`() = runComposeUiTest {
        setContent {
            FormsUnderTest(entry = taberu())
        }

        // Merged tree: the band's text is cleared into the group's
        // description, so nothing in the walked tree carries it alone.
        // If the label ever escapes its group this finds it.
        onAllNodes(hasText("Non-past")).assertCountEquals(0)
    }
}

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
