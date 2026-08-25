package cc.hosaka.okonomi.feature.phrases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import kotlin.test.Test
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_phrases_empty
import okonomi.shared.generated.resources.entry_phrases_error
import org.jetbrains.compose.resources.stringResource

/**
 * Around 86% of entries reach the uncovered-entry state, so it is the common
 * case rather than an edge. It must not read as the failure state next to it:
 * "no sentences yet" and "something went wrong" mean different things to the
 * reader, and both are a [CenteredMessage] with a string swapped.
 */
@OptIn(ExperimentalTestApi::class)
class PhrasesEmptyStateUiTest : ComposeUiTestBase() {
    @Test
    fun `an entry the corpus never uses says so rather than reporting an error`() = runComposeUiTest {
        val messages = PhrasesMessages()
        setContent {
            messages.read()
            PhrasesUnderTest()
        }

        onNodeWithText(messages.empty).assertIsDisplayed()
        onNodeWithText(messages.error).assertDoesNotExist()
    }

    @Test
    fun `a failure reports an error rather than claiming the corpus is empty`() = runComposeUiTest {
        val messages = PhrasesMessages()
        setContent {
            messages.read()
            PhrasesUnderTest(content = PhrasesTabContentState.Error())
        }

        onNodeWithText(messages.error).assertIsDisplayed()
        onNodeWithText(messages.empty).assertDoesNotExist()
    }
}

private class PhrasesMessages {
    lateinit var empty: String
    lateinit var error: String

    @Composable
    fun read() {
        empty = stringResource(Res.string.entry_phrases_empty)
        error = stringResource(Res.string.entry_phrases_error)
    }
}

@Composable
private fun PhrasesUnderTest(
    content: PhrasesTabContentState = PhrasesTabContentState.Empty,
) {
    ScreenHost {
        PhrasesTabContent(
            state = PhrasesTabState(content = content),
            contentPadding = PaddingValues(),
        )
    }
}
