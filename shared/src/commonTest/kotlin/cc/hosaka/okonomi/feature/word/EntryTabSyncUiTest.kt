package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.test.entryDetail
import kotlin.test.Test
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_forms_none
import okonomi.shared.generated.resources.entry_tab_forms
import okonomi.shared.generated.resources.entry_tab_word
import org.jetbrains.compose.resources.stringResource

/**
 * The Word spec has an acceptance row saying the pager and the floating tab bar
 * agree on the selected tab, and nothing could test it: the pager, the bar and
 * their shared `settledPage` all live inside a composable.
 */
@OptIn(ExperimentalTestApi::class)
class EntryTabSyncUiTest : ComposeUiTestBase() {
    @Test
    fun `the entry opens on the word tab`() = runComposeUiTest {
        val labels = TabLabels()
        setContent {
            labels.read()
            EntryUnderTest()
        }

        onNodeWithContentDescription(labels.word).assertIsSelected()
        onNodeWithContentDescription(labels.forms).assertIsNotSelected()
    }

    @Test
    fun `tapping a tab segment selects it and leaves the previous one`() = runComposeUiTest {
        val labels = TabLabels()
        setContent {
            labels.read()
            EntryUnderTest()
        }

        onNodeWithContentDescription(labels.forms).performClick()
        waitForIdle()

        onNodeWithContentDescription(labels.forms).assertIsSelected()
        onNodeWithContentDescription(labels.word).assertIsNotSelected()
    }

    @Test
    fun `tapping a tab segment brings that tab's page into view`() = runComposeUiTest {
        val labels = TabLabels()
        setContent {
            labels.read()
            EntryUnderTest()
        }

        onNodeWithContentDescription(labels.forms).performClick()
        waitForIdle()

        onNodeWithText(labels.formsEmptyMessage).assertIsDisplayed()
    }
}

/**
 * The tab labels and the Forms page's message are string resources, so they are
 * read from the composition rather than restated as literals here.
 */
private class TabLabels {
    lateinit var word: String
    lateinit var forms: String
    lateinit var formsEmptyMessage: String

    @Composable
    fun read() {
        word = stringResource(Res.string.entry_tab_word)
        forms = stringResource(Res.string.entry_tab_forms)
        formsEmptyMessage = stringResource(Res.string.entry_forms_none)
    }
}

private fun entryState(
    entry: EntryDetail = entryDetail(),
) = EntryState(
    entryId = entry.entryId,
    content = EntryContentState.Ready(entry = entry),
)

/**
 * The tabs reach `produceScreenState`, which resolves a `ViewModel` against the
 * back stack entry, so the composition needs a store owner as well as the
 * navigation controller.
 */
@Composable
private fun EntryUnderTest(
    state: EntryState = entryState(),
) {
    ScreenHost {
        EntryScreen(state)
    }
}
