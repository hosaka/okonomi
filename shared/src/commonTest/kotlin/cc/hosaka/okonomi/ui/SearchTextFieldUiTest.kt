package cc.hosaka.okonomi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.search_clear
import org.jetbrains.compose.resources.stringResource

private const val QUERY = "食べる"

private const val PLACEHOLDER = "Search"

/** A second, distinguishable value, so two fields can be told apart. */
private const val BARE = "見る"

/**
 * The shared field's two slots and its clear action, tested here rather
 * than only through the screens that use it: both search branches and
 * the radical bar are built on this one component, and the radical bar
 * can only assert what it does *not* draw.
 *
 * The absence assertions over there are exactly why the presence ones
 * belong here. `RadicalScreenUiTest` asserts no node carries
 * [SEARCH_FIELD_ICON_TAG] and no node carries the clear description —
 * both of which would pass just as happily with the tag deleted from
 * the icon, or with the clear action removed from the component
 * altogether. These pin the other side of each claim.
 */
@OptIn(ExperimentalTestApi::class)
class SearchTextFieldUiTest : ComposeUiTestBase() {

    @Composable
    private fun FieldUnderTest(
        text: String = QUERY,
        onTextChange: ((String) -> Unit)? = {},
        onClear: (() -> Unit)? = onTextChange?.let { { it("") } },
    ) {
        SearchTextField(
            text = text,
            placeholder = PLACEHOLDER,
            onTextChange = onTextChange,
            onClear = onClear,
        )
    }

    /** The default slot, which every search in the app renders. */
    @Test
    fun `a field draws the search icon by default`() = runComposeUiTest {
        setContent {
            FieldUnderTest()
        }

        onNodeWithTag(SEARCH_FIELD_ICON_TAG, useUnmergedTree = true).assertExists()
    }

    /** And a caller can put nothing there, which the radical bar does. */
    @Test
    fun `an empty leading slot draws no search icon`() = runComposeUiTest {
        setContent {
            SearchTextField(
                text = QUERY,
                placeholder = PLACEHOLDER,
                onTextChange = {},
                leading = {},
            )
        }

        onNodeWithTag(SEARCH_FIELD_ICON_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * The gap after the icon belongs to the slot, not to the Row. Left
     * on the Row it was emitted for a slot that drew nothing, so the
     * radical bar carried a hole where the icon would have been and its
     * title began 16dp further in than the pill's own padding.
     *
     * Both edges of that claim are pinned, because a "starts further
     * left" comparison holds just as well with the gap emitted twice as
     * with it emitted once. The icon's own start IS the pill's start
     * padding, so an empty slot must put its text exactly there; and the
     * editable field must still clear the icon by a gap rather than butt
     * against it.
     *
     * Positions rather than typography: Robolectric lays every glyph out
     * to zero width, but where a node is placed is layout and is
     * measured the same either way.
     */
    @Test
    fun `an empty leading slot leaves no gap where the icon was`() = runComposeUiTest {
        setContent {
            Column {
                SearchTextField(
                    text = QUERY,
                    placeholder = PLACEHOLDER,
                    onTextChange = {},
                )
                SearchTextField(
                    text = BARE,
                    placeholder = PLACEHOLDER,
                    onTextChange = {},
                    leading = {},
                )
            }
        }

        val icon = onNodeWithTag(SEARCH_FIELD_ICON_TAG, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val withIcon = onNodeWithText(QUERY).fetchSemanticsNode().boundsInRoot.left
        val withoutIcon = onNodeWithText(BARE).fetchSemanticsNode().boundsInRoot.left

        assertEquals(
            icon.left,
            withoutIcon,
            absoluteTolerance = 0.5f,
            message = "an empty slot must start the text at the pill's own padding, where the icon starts",
        )
        assertTrue(
            withIcon > icon.right,
            "the icon must keep its gap before the query: text at $withIcon, icon ends at ${icon.right}",
        )
    }

    @Test
    fun `the clear action is drawn and fires while the field has text`() = runComposeUiTest {
        var clears = 0
        lateinit var label: String
        setContent {
            label = stringResource(Res.string.search_clear)
            FieldUnderTest(onClear = { clears++ })
        }

        onNodeWithContentDescription(label).assertIsDisplayed()
        onNodeWithContentDescription(label).performClick()

        assertEquals(1, clears)
    }

    /**
     * It used to be drawn disabled here — an affordance for an action
     * that does not exist, on the one caller that has no way to clear
     * anything.
     */
    @Test
    fun `no clear action is drawn when there is nothing to clear`() = runComposeUiTest {
        lateinit var label: String
        setContent {
            label = stringResource(Res.string.search_clear)
            FieldUnderTest(onTextChange = null, onClear = null)
        }

        onNodeWithContentDescription(label).assertDoesNotExist()
    }

    /**
     * The corner the two conditions exist for separately: a caller can
     * pass a clear callback without a text one, and a clear button on a
     * bar nothing can type into is the affordance the read-only bar must
     * never grow.
     */
    @Test
    fun `no clear action is drawn on a field that cannot be typed into`() = runComposeUiTest {
        lateinit var label: String
        setContent {
            label = stringResource(Res.string.search_clear)
            FieldUnderTest(onTextChange = null, onClear = {})
        }

        onNodeWithContentDescription(label).assertDoesNotExist()
    }
}
