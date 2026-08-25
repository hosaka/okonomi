package cc.hosaka.okonomi.feature.forms

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.test.entryDetail
import cc.hosaka.okonomi.ui.test.entrySense
import kotlin.test.Test
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_forms_none
import okonomi.shared.generated.resources.entry_forms_suru
import org.jetbrains.compose.resources.stringResource

/**
 * A word with nothing to conjugate is reached by roughly 14,000 entries, and a
 * suru noun gets a different message from a word that simply has no forms.
 * Showing a neighbouring tab's message here would read as a bug to the user and
 * as green to the suite, so each assertion also denies the other message.
 */
@OptIn(ExperimentalTestApi::class)
class FormsEmptyStateUiTest : ComposeUiTestBase() {
    @Test
    fun `a plain noun says it has no forms to conjugate`() = runComposeUiTest {
        val messages = FormsMessages()
        setContent {
            messages.read()
            FormsUnderTest()
        }

        onNodeWithText(messages.none).assertIsDisplayed()
        onNodeWithText(messages.suru).assertDoesNotExist()
    }

    @Test
    fun `a suru noun is sent to suru rather than told it has no forms`() = runComposeUiTest {
        val messages = FormsMessages()
        setContent {
            messages.read()
            FormsUnderTest(entry = suruNoun())
        }

        onNodeWithText(messages.suru).assertIsDisplayed()
        onNodeWithText(messages.none).assertDoesNotExist()
    }
}

private class FormsMessages {
    lateinit var none: String
    lateinit var suru: String

    @Composable
    fun read() {
        none = stringResource(Res.string.entry_forms_none)
        suru = stringResource(Res.string.entry_forms_suru)
    }
}

/** 勉強 — a noun that becomes a verb only with する. */
private fun suruNoun() = entryDetail(
    headword = "勉強",
    senses = listOf(entrySense(posCodes = listOf("n", "vs"), glosses = listOf("study"))),
)

@Composable
private fun FormsUnderTest(
    entry: EntryDetail = entryDetail(),
) {
    ScreenHost {
        FormsTab(entry = entry, contentPadding = PaddingValues())
    }
}
