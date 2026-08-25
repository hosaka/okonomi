package cc.hosaka.okonomi.feature.word

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cc.hosaka.okonomi.feature.kanji.KanjiTab
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.phrases.PhrasesTab
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import cc.hosaka.okonomi.ui.test.entryDetail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_tab_forms
import okonomi.shared.generated.resources.entry_tab_kanji
import okonomi.shared.generated.resources.entry_tab_phrases
import okonomi.shared.generated.resources.entry_tab_word
import org.jetbrains.compose.resources.stringResource

/**
 * The two device-reported defects in how the tab bar answers a thumb: the
 * highlight arriving a whole swipe late, and a tab jump loading every tab
 * the pager animates past.
 */
@OptIn(ExperimentalTestApi::class)
class EntryTabGestureUiTest : ComposeUiTestBase() {

    /**
     * The reason `settledPage` was replaced by `currentPage`. Under
     * `settledPage` the highlight could not move until the drag was
     * released and the pager had finished settling, which is what made
     * the bar read as a beat behind the thumb.
     */
    @Test
    fun `dragging past halfway highlights the tab being dragged to before release`() =
        runComposeUiTest {
            val labels = GestureTabLabels()
            setContent {
                labels.read()
                EntryUnderTest()
            }

            onRoot().performTouchInput {
                down(center)
                // Past the pager's own halfway threshold, and no up():
                // the highlight has to move while the drag is still in
                // the reader's thumb.
                moveBy(Offset(-width * 0.6f, 0f))
            }
            waitForIdle()

            onNodeWithContentDescription(labels.kanji).assertIsSelected()
            onNodeWithContentDescription(labels.word).assertIsNotSelected()
        }

    /**
     * The half of the earlier decision that still holds: only the
     * threshold moved, not the rule that an abandoned gesture changes
     * nothing.
     */
    @Test
    fun `a drag released short of halfway leaves the highlight where it was`() = runComposeUiTest {
        val labels = GestureTabLabels()
        setContent {
            labels.read()
            EntryUnderTest()
        }

        onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.2f, 0f))
            up()
        }
        waitForIdle()

        onNodeWithContentDescription(labels.word).assertIsSelected()
        onNodeWithContentDescription(labels.kanji).assertIsNotSelected()
    }

    /**
     * Tapping a distant tab animates the pager through the ones between,
     * composing each of them. Only the tab the pager settles on may query
     * the dictionary.
     *
     * Read through the view model store because that is where a query
     * would have to start: every tab that loads resolves its state
     * through `produceScreenState`, which puts a view model in the store
     * under the tab's own key. No view model, no producer, no query.
     */
    @Test
    fun `a tab the pager is only passing through resolves no screen state`() = runComposeUiTest {
        val store = ViewModelStore()
        setContent {
            TabUnderTest(store) {
                KanjiTab(
                    entry = entryDetail(),
                    contentPadding = PaddingValues(),
                    loadEnabled = false,
                )
            }
        }
        waitForIdle()

        assertEquals(emptySet(), store.keys())
    }

    @Test
    fun `the tab the pager settles on resolves its screen state`() = runComposeUiTest {
        val store = ViewModelStore()
        setContent {
            TabUnderTest(store) {
                KanjiTab(
                    entry = entryDetail(),
                    contentPadding = PaddingValues(),
                    loadEnabled = true,
                )
            }
        }
        waitForIdle()

        // Without this the gated test above would pass for a tab that
        // never loads under any condition.
        assertTrue(store.keys().isNotEmpty(), "the settled tab must resolve its state")
    }

    /**
     * The same rule again, but through the real [EntryScreen] rather
     * than a tab hosted by hand: the screen decides `loadEnabled`, and
     * the store is read for what the trip actually resolved.
     *
     * What this cannot pin, and does not pretend to: the pages the pager
     * passes through mid-animation are never composed in a host test —
     * stepping the clock frame by frame does not produce them either —
     * so the pass-through the gate exists for cannot be observed here.
     * That half is held by `loadEnabled` carrying no default on
     * [KanjiTab] and [PhrasesTab], which turns dropping the argument
     * into a compile error rather than a silent regression.
     */
    @Test
    fun `tapping a distant tab resolves the state of the tab it settles on`() = runComposeUiTest {
        val store = ViewModelStore()
        val labels = GestureTabLabels()
        setContent {
            labels.read()
            EntryUnderTest(store)
        }

        onNodeWithContentDescription(labels.phrases).performClick()
        waitForIdle()

        val entryId = entryDetail().entryId
        assertTrue(
            "entry-phrases-$entryId" in store.keys(),
            "the tab the pager settled on must have resolved its state",
        )
    }

    /**
     * The other half of the gate, and the reason it is not `settledPage`
     * alone. `settledPage` only moves once the pager has come to rest,
     * so an ordinary one-tab swipe used to highlight its destination at
     * the halfway point (the bar reads `currentPage`) while the body
     * underneath stayed a spinner until the settle finished. The rare
     * three-tab tap was fixed at the cost of the common gesture.
     *
     * The gesture is deliberately left held: with the drag still in the
     * reader's thumb nothing has settled, so this passes only because
     * `targetPage` has already committed to the destination.
     */
    @Test
    fun `a one-tab swipe loads its destination before the pager settles`() = runComposeUiTest {
        val store = ViewModelStore()
        setContent {
            EntryUnderTest(store)
        }

        onRoot().performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.6f, 0f))
        }
        waitForIdle()

        val entryId = entryDetail().entryId
        assertTrue(
            "entry-kanji-$entryId" in store.keys(),
            "the tab the swipe is committed to must load while the swipe is still happening",
        )
    }

    @Test
    fun `the phrases tab is gated the same way`() = runComposeUiTest {
        val store = ViewModelStore()
        setContent {
            TabUnderTest(store) {
                PhrasesTab(
                    entry = entryDetail(),
                    contentPadding = PaddingValues(),
                    loadEnabled = false,
                )
            }
        }
        waitForIdle()

        assertEquals(emptySet(), store.keys())
    }
}

private class GestureTabLabels {
    lateinit var word: String
    lateinit var kanji: String
    lateinit var forms: String
    lateinit var phrases: String

    @Composable
    fun read() {
        word = stringResource(Res.string.entry_tab_word)
        kanji = stringResource(Res.string.entry_tab_kanji)
        forms = stringResource(Res.string.entry_tab_forms)
        phrases = stringResource(Res.string.entry_tab_phrases)
    }
}

@Composable
private fun EntryUnderTest(store: ViewModelStore = ViewModelStore()) {
    val navigation = remember { RecordingNavigationController() }
    val storeOwner = remember(store) { TestStoreOwner(store) }
    CompositionLocalProvider(
        LocalNavigationController provides navigation,
        LocalViewModelStoreOwner provides storeOwner,
    ) {
        EntryScreen(
            EntryState(
                entryId = entryDetail().entryId,
                content = EntryContentState.Ready(entry = entryDetail()),
            ),
        )
    }
}

/**
 * Hosts one tab against a store the test can read back, which is the
 * whole point: the assertion is about what the tab did or did not put
 * in it.
 */
@Composable
private fun TabUnderTest(
    store: ViewModelStore,
    content: @Composable () -> Unit,
) {
    val navigation = remember { RecordingNavigationController() }
    val storeOwner = remember(store) { TestStoreOwner(store) }
    CompositionLocalProvider(
        LocalNavigationController provides navigation,
        LocalViewModelStoreOwner provides storeOwner,
    ) {
        content()
    }
}

private class TestStoreOwner(override val viewModelStore: ViewModelStore) : ViewModelStoreOwner
