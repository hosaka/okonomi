package cc.hosaka.okonomi.feature.word

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.ScreenHost
import cc.hosaka.okonomi.ui.test.entryDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_save
import okonomi.shared.generated.resources.entry_saved
import okonomi.shared.generated.resources.entry_tab_forms
import okonomi.shared.generated.resources.entry_tab_kanji
import okonomi.shared.generated.resources.entry_tab_phrases
import okonomi.shared.generated.resources.entry_tab_word
import okonomi.shared.generated.resources.entry_unsaved
import org.jetbrains.compose.resources.stringResource

/**
 * The save button on the entry view. It belongs to the entry rather than
 * to a tab, which is a claim about the composition — it is drawn outside
 * the pager — and therefore only observable here.
 */
@OptIn(ExperimentalTestApi::class)
class EntrySaveButtonUiTest : ComposeUiTestBase() {

    @Test
    fun `the button is there on every tab`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            EntryUnderTest(state = savableState())
        }

        onNodeWithContentDescription(labels.save).assertIsDisplayed()
        listOf(labels.kanji, labels.forms, labels.phrases, labels.word).forEach { tab ->
            onNodeWithContentDescription(tab).performClick()
            waitForIdle()
            onNodeWithContentDescription(labels.save)
                .assertIsDisplayed()
        }
    }

    /**
     * Every tap reaches the store, including a second one landing before
     * the first has come back through it. The button asks for "the other
     * one" and the store decides which that is; a button that computed
     * true or false from what it was drawing would send the same value
     * twice here and the reader's second tap would do nothing.
     */
    @Test
    fun `every tap reaches the store even when the last write has not come back`() = runComposeUiTest {
        val labels = Labels()
        var taps = 0
        // Deliberately frozen: this is the frame between the tap and the
        // store answering, which is where the double-tap bug lived.
        setContent {
            labels.read()
            EntryUnderTest(state = savableState(isFavourite = false, onToggle = { taps++ }))
        }

        onNodeWithContentDescription(labels.save).performClick()
        waitForIdle()
        onNodeWithContentDescription(labels.save).performClick()
        waitForIdle()

        assertEquals(2, taps)
    }

    @Test
    fun `the drawn state follows the store rather than the taps`() = runComposeUiTest {
        val labels = Labels()
        // Compose state, so the button really does redraw when the store
        // answers; a plain var would leave this asserting nothing.
        val saved = mutableStateOf(false)
        setContent {
            labels.read()
            EntryUnderTest(
                state = savableState(isFavourite = saved.value, onToggle = { saved.value = !saved.value }),
            )
        }

        onNodeWithContentDescription(labels.save).assert(hasState(labels.unsaved))
        onNodeWithContentDescription(labels.save).performClick()
        waitForIdle()

        onNodeWithContentDescription(labels.save).assert(hasState(labels.saved))
    }

    /**
     * The button keeps one name and changes its spoken state. A control
     * whose name changed under the reader's thumb would be a different
     * control every time they reached for it.
     */
    @Test
    fun `the button says whether the word is saved`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            EntryUnderTest(state = savableState(isFavourite = false))
        }

        onNodeWithContentDescription(labels.save).assert(hasState(labels.unsaved))
    }

    @Test
    fun `a saved word says so`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            EntryUnderTest(state = savableState(isFavourite = true))
        }

        onNodeWithContentDescription(labels.save).assert(hasState(labels.saved))
    }

    /**
     * A body with no entry in it has nothing to save, so the button is
     * not drawn at all rather than drawn disabled.
     */
    @Test
    fun `an entry that did not load draws no button`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            EntryUnderTest(
                state = EntryState(
                    entryId = 1L,
                    content = EntryContentState.Error(onRetry = null),
                ),
            )
        }

        onNodeWithContentDescription(labels.save).assertDoesNotExist()
    }
}

private fun hasState(value: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

private fun savableState(
    isFavourite: Boolean = false,
    onToggle: () -> Unit = {},
): EntryState {
    val entry = entryDetail()
    return EntryState(
        entryId = entry.entryId,
        content = EntryContentState.Ready(entry = entry),
        isFavourite = isFavourite,
        onToggleFavourite = onToggle,
    )
}

@Composable
private fun EntryUnderTest(state: EntryState) {
    ScreenHost {
        EntryScreen(state)
    }
}

private class Labels {
    lateinit var save: String
    lateinit var saved: String
    lateinit var unsaved: String
    lateinit var word: String
    lateinit var kanji: String
    lateinit var forms: String
    lateinit var phrases: String

    @Composable
    fun read() {
        save = stringResource(Res.string.entry_save)
        saved = stringResource(Res.string.entry_saved)
        unsaved = stringResource(Res.string.entry_unsaved)
        word = stringResource(Res.string.entry_tab_word)
        kanji = stringResource(Res.string.entry_tab_kanji)
        forms = stringResource(Res.string.entry_tab_forms)
        phrases = stringResource(Res.string.entry_tab_phrases)
    }
}
