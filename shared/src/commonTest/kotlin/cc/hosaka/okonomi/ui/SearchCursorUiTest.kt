package cc.hosaka.okonomi.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

private const val QUERY = "食べる"

private const val PLACEHOLDER = "Search"

/**
 * Where the caret sits when the search field gets focus without a tap
 * naming a position — which is what reselecting the Search tab does.
 *
 * A plain `String`-backed `BasicTextField` has nothing tracking the
 * selection, so the caret landed at offset zero and the reader had to
 * drag it past their own query before they could refine it. The field
 * tracks a `TextFieldValue` now; these pin the two moments that can put
 * the caret in the wrong place.
 */
@OptIn(ExperimentalTestApi::class)
class SearchCursorUiTest : ComposeUiTestBase() {
    @Test
    fun `a field composed with a query already in it starts with the cursor after it`() =
        runComposeUiTest {
            setContent {
                FieldUnderTest(text = QUERY)
            }

            assertEquals(TextRange(QUERY.length), onNodeWithText(QUERY).selection())
        }

    /**
     * The matrix scenario: search, open an entry, come back, tap the
     * Search tab. Navigating away takes the focus off the field, and the
     * reselect puts it back — which is the moment the caret has to move.
     */
    @Test
    fun `focus arriving on the field moves the cursor to the end`() = runComposeUiTest {
        val field = FocusRequester()
        val elsewhere = FocusRequester()
        setContent {
            FieldUnderTest(text = QUERY, focusRequester = field, elsewhere = elsewhere)
        }

        // Focus first, because setting a selection is an action on a
        // focused field; this is also what a tap would leave behind.
        runOnIdle { field.requestFocus() }
        onNodeWithText(QUERY).performTextInputSelection(TextRange(0))
        assertEquals(TextRange(0), onNodeWithText(QUERY).selection())

        // The screen the reader navigated to takes the focus.
        runOnIdle { elsewhere.requestFocus() }
        waitForIdle()

        // Coming back is the reselect: the caret goes to the end rather
        // than staying where the previous visit left it.
        runOnIdle { field.requestFocus() }
        waitForIdle()

        assertEquals(TextRange(QUERY.length), onNodeWithText(QUERY).selection())
    }

    @Test
    fun `a query pushed in from outside brings the cursor with it`() = runComposeUiTest {
        // The producer owns the query, so a restored one arrives as a
        // changed parameter rather than through the field's own editing.
        val text = mutableStateOf("")
        setContent {
            FieldUnderTest(text = text.value)
        }
        runOnIdle { text.value = QUERY }
        waitForIdle()

        assertEquals(TextRange(QUERY.length), onNodeWithText(QUERY).selection())
    }

    /**
     * The primary input path, which nothing tested: typing.
     *
     * The query reaches the field back through the producer
     * (querySink -> combine -> stateIn), so between a keystroke and its
     * echo the field's own text and the [text] parameter disagree. The
     * field used to rebuild itself on that disagreement, with the caret
     * at the end of the string — so inserting a character in the middle
     * of a query threw the caret to the end and the next keystroke
     * landed there instead. Echoing asynchronously is what makes the
     * regression reproducible.
     */
    @Test
    fun `typing into the middle of a query leaves the cursor where it was typed`() =
        runComposeUiTest {
            val text = mutableStateOf(QUERY)
            setContent {
                // The echo is deliberately deferred: reporting straight
                // back would close the window this test is about.
                FieldUnderTest(text = text.value, onTextChange = { pending = it })
            }

            // Caret between 食 and べ, as a tap mid-string leaves it.
            onNodeWithText(QUERY).performTextInputSelection(TextRange(1))
            onNodeWithText(QUERY).performTextInput("た")

            val typed = "食た" + QUERY.drop(1)
            assertEquals(
                TextRange(2),
                onNodeWithText(typed).selection(),
                "the caret must stay after the character just typed",
            )

            // The producer's echo arrives a frame later; it must not
            // move the caret either.
            runOnIdle { text.value = pending }
            waitForIdle()

            assertEquals(TextRange(2), onNodeWithText(typed).selection())
        }

    /**
     * The same window, from the other side: a value the caller really
     * did push — the clear action — still replaces what is in the field
     * and takes the caret with it.
     */
    @Test
    fun `a clear pushed in while typing still empties the field`() = runComposeUiTest {
        val text = mutableStateOf(QUERY)
        setContent {
            FieldUnderTest(text = text.value, onTextChange = { pending = it })
        }

        onNodeWithText(QUERY).performTextInput("よ")
        runOnIdle { text.value = "" }
        waitForIdle()

        assertEquals(TextRange(0), onNodeWithText(PLACEHOLDER).selection())
    }

    private var pending: String = ""
}

private fun SemanticsNodeInteraction.selection(): TextRange? =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.TextSelectionRange)

@Composable
private fun FieldUnderTest(
    text: String,
    focusRequester: FocusRequester? = null,
    elsewhere: FocusRequester? = null,
    onTextChange: (String) -> Unit = {},
) {
    Column {
        SearchTextField(
            text = text,
            placeholder = PLACEHOLDER,
            onTextChange = onTextChange,
            focusRequester = focusRequester,
        )
        // Somewhere for the focus to go, standing in for the screen the
        // reader navigated to.
        if (elsewhere != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .focusRequester(elsewhere)
                    .focusable(),
            )
        }
    }
}
