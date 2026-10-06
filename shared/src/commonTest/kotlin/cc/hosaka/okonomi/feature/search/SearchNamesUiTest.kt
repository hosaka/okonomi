package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cc.hosaka.okonomi.db.NameHit
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.PagingFooterState
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.name_type_fem
import okonomi.shared.generated.resources.name_type_surname
import okonomi.shared.generated.resources.paging_more_failed
import okonomi.shared.generated.resources.search_clear
import okonomi.shared.generated.resources.search_filters_default
import okonomi.shared.generated.resources.search_filters_names_on
import okonomi.shared.generated.resources.search_names_toggle
import okonomi.shared.generated.resources.search_no_results
import okonomi.shared.generated.resources.search_options
import okonomi.shared.generated.resources.search_options_close
import org.jetbrains.compose.resources.stringResource

/**
 * What the producer tests cannot see: where the name rows land on screen,
 * what a name row is made of, that tapping one leads nowhere, and how
 * the filters button that carries the toggle behaves.
 */
@OptIn(ExperimentalTestApi::class)
class SearchNamesUiTest : ComposeUiTestBase() {

    @Test
    fun `names are drawn below every word result`() = runComposeUiTest {
        setContent {
            SearchUnderTest(hits = listOf(word()), names = listOf(tanaka()))
        }

        onNodeWithText("食べる").assertIsDisplayed()
        onNodeWithText("Tanaka").assertIsDisplayed()

        val wordTop = onNodeWithText("食べる").fetchSemanticsNode().boundsInRoot.top
        val nameTop = onNodeWithText("Tanaka").fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            nameTop > wordTop,
            "a word result must never be pushed below a name: word at $wordTop, name at $nameTop",
        )
    }

    @Test
    fun `a name row shows its chips and its romanisation`() = runComposeUiTest {
        lateinit var surname: String
        lateinit var fem: String
        setContent {
            surname = stringResource(Res.string.name_type_surname)
            fem = stringResource(Res.string.name_type_fem)
            SearchUnderTest(hits = emptyList(), names = listOf(tanaka(), michiko()))
        }

        onNodeWithText(surname).assertIsDisplayed()
        onNodeWithText(fem).assertIsDisplayed()
        onNodeWithText("Tanaka").assertIsDisplayed()
        // The kana-only name has no kanji to set a reading over, so the
        // reading itself is the headword.
        onNodeWithText("みちこ").assertIsDisplayed()
    }

    /**
     * Alex's ruling: a name has no entry view to open, so a tap on the
     * row must not take the reader anywhere.
     *
     * The row is no longer INERT, and that is a deliberate later change:
     * every list card now answers a touch, and a long press on this one
     * copies the name. So the row carries a click action for the ripple.
     * What the ruling actually forbids is it LEADING somewhere, which is
     * what the navigation assertion pins — and the row is still not
     * announced as a button, so nothing tells a screen-reader user to
     * expect a destination.
     */
    @Test
    fun `a name row leads nowhere and is not announced as a button`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            SearchUnderTest(hits = emptyList(), names = listOf(tanaka()), navigation = navigation)
        }

        onNodeWithText("Tanaka").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button).not(),
        )
        onNodeWithText("Tanaka").performClick()
        waitForIdle()

        assertEquals(emptyList(), navigation.navigated, "a name row must lead nowhere")
    }

    /**
     * Names on with nothing matching is still "no results" — the toggle
     * adds a place to look, not a reason to keep an empty list on screen.
     */
    @Test
    fun `no words and no names is still the empty state`() = runComposeUiTest {
        lateinit var empty: String
        setContent {
            empty = stringResource(Res.string.search_no_results)
            SearchUnderTest(hits = emptyList(), names = emptyList())
        }

        onNodeWithText(empty).assertIsDisplayed()
    }

    @Test
    fun `the filters button opens a menu carrying the names pill and tapping it asks for the opposite`() = runComposeUiTest {
        val toggled = mutableListOf<Boolean>()
        val labels = FilterLabels()
        setContent {
            labels.read()
            SearchUnderTest(
                hits = listOf(word()),
                names = emptyList(),
                namesEnabled = false,
                onNamesEnabledChange = { toggled += it },
            )
        }

        onNode(namesPill(labels)).assertDoesNotExist()
        onNodeWithContentDescription(labels.options).performClick()
        waitForIdle()

        onNode(namesPill(labels)).assertIsDisplayed()
        onNode(namesPill(labels)).performClick()
        waitForIdle()

        assertEquals(listOf(true), toggled, "the pill must ask for the opposite of what is stored")
    }

    /**
     * Toggling a filter leaves the menu open, so several can be set in one
     * visit. Each tap reads the stored value afresh, so two taps ask for
     * opposite things rather than the same one twice.
     */
    @Test
    fun `the menu stays open across toggles and each toggle asks for the opposite of what is stored`() = runComposeUiTest {
        val namesOn = mutableStateOf(false)
        val toggled = mutableListOf<Boolean>()
        val labels = FilterLabels()
        setContent {
            labels.read()
            SearchUnderTest(
                hits = listOf(word()),
                names = emptyList(),
                namesEnabled = namesOn.value,
                onNamesEnabledChange = {
                    toggled += it
                    namesOn.value = it
                },
            )
        }

        onNodeWithContentDescription(labels.options).performClick()
        waitForIdle()
        onNode(namesPill(labels)).performClick()
        waitForIdle()
        onNode(namesPill(labels)).assertIsDisplayed()
        onNode(namesPill(labels)).performClick()
        waitForIdle()

        assertEquals(listOf(true, false), toggled)
        onNode(namesPill(labels)).assertIsDisplayed()
        onNodeWithContentDescription(labels.close).assertIsOn()
    }

    /**
     * The pill's on/off is the stored value, read as a switch's state —
     * not its colour, which no assertion here could see, and not a value
     * fixed beside it.
     */
    @Test
    fun `the names pill reports the state the toggle is actually in`() = runComposeUiTest {
        val namesOn = mutableStateOf(false)
        val labels = FilterLabels()
        setContent {
            labels.read()
            SearchUnderTest(
                hits = listOf(word()),
                names = emptyList(),
                namesEnabled = namesOn.value,
                onNamesEnabledChange = { namesOn.value = it },
            )
        }

        onNodeWithContentDescription(labels.options).performClick()
        waitForIdle()
        onNode(namesPill(labels))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertIsOff()

        runOnIdle { namesOn.value = true }
        waitForIdle()
        onNode(namesPill(labels)).assertIsOn()
    }

    /**
     * The button is named for what a tap on it does next, so while the
     * menu is open it says it closes the options rather than repeating
     * the name it had when it opened them.
     */
    @Test
    fun `the button closes the menu again and its label says which way a tap goes`() = runComposeUiTest {
        val labels = FilterLabels()
        setContent {
            labels.read()
            SearchUnderTest(hits = listOf(word()), names = emptyList())
        }

        onNodeWithContentDescription(labels.close).assertDoesNotExist()
        onNodeWithContentDescription(labels.options).performClick()
        waitForIdle()
        onNode(namesPill(labels)).assertIsDisplayed()
        onNodeWithContentDescription(labels.options).assertDoesNotExist()

        onNodeWithContentDescription(labels.close).performClick()
        waitForIdle()

        onNode(namesPill(labels)).assertDoesNotExist()
        onNodeWithContentDescription(labels.close).assertDoesNotExist()
        onNodeWithContentDescription(labels.options).assertIsOff()
    }

    /**
     * Back closes an open menu and goes no further: the handler behind it
     * stands in for the shell's, which on the Search root would switch to
     * the default tab and leave the menu open on a tab no longer shown.
     * With the menu closed, back is not this screen's to take.
     */
    @Test
    fun `system back closes an open menu and only an open one`() = runComposeUiTest {
        val labels = FilterLabels()
        val back = TestBackInput()
        var shellBacks = 0
        setContent {
            labels.read()
            BackHost(back = back, onShellBack = { shellBacks++ }) {
                SearchUnderTest(hits = listOf(word()), names = emptyList())
            }
        }

        onNodeWithContentDescription(labels.options).performClick()
        waitForIdle()
        onNode(namesPill(labels)).assertIsDisplayed()

        runOnIdle { back.back() }
        waitForIdle()

        onNode(namesPill(labels)).assertDoesNotExist()
        assertEquals(0, shellBacks, "back with the menu open must close it, not leave the tab")

        runOnIdle { back.back() }
        waitForIdle()

        assertEquals(1, shellBacks, "back with the menu closed belongs to the shell")
    }

    @Test
    fun `with names on and the menu closed the button carries a badge and says names are on`() = runComposeUiTest {
        val labels = FilterLabels()
        setContent {
            labels.read()
            SearchUnderTest(hits = listOf(word()), names = emptyList(), namesEnabled = true)
        }

        onNodeWithTag(SEARCH_FILTERS_BADGE_TAG).assertExists()
        onNodeWithContentDescription(labels.options)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, labels.namesOn))

        // Open, the menu shows the pill itself; the dot would repeat it.
        onNodeWithContentDescription(labels.options).performClick()
        waitForIdle()
        onNodeWithTag(SEARCH_FILTERS_BADGE_TAG).assertDoesNotExist()
    }

    @Test
    fun `with names off the button carries no badge and says the search is the default`() = runComposeUiTest {
        val labels = FilterLabels()
        setContent {
            labels.read()
            SearchUnderTest(hits = listOf(word()), names = emptyList(), namesEnabled = false)
        }

        onNodeWithContentDescription(labels.options).assertIsDisplayed()
        onNodeWithTag(SEARCH_FILTERS_BADGE_TAG).assertDoesNotExist()
        onNodeWithContentDescription(labels.options)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, labels.defaultSearch))
    }

    /** A floating action button has no disabled state, so there is none. */
    @Test
    fun `with no toggle callback there is no filters button`() = runComposeUiTest {
        val labels = FilterLabels()
        setContent {
            labels.read()
            SearchUnderTest(hits = listOf(word()), names = emptyList(), onNamesEnabledChange = null)
        }

        onNodeWithText("食べる").assertIsDisplayed()
        onNodeWithContentDescription(labels.options).assertDoesNotExist()
    }

    /**
     * The field carries no options control any more, in either branch:
     * the only node labelled as search options is the button, and it sits
     * in the bottom-end corner, below the field rather than inside it.
     */
    @Test
    fun `the field has no options control and the button sits at the bottom end`() = runComposeUiTest {
        val labels = FilterLabels()
        val pushed = mutableStateOf(false)
        setContent {
            labels.read()
            SearchUnderTest(
                hits = listOf(word()),
                names = emptyList(),
                onBack = if (pushed.value) ({}) else null,
            )
        }

        for (isPushed in listOf(false, true)) {
            runOnIdle { pushed.value = isPushed }
            waitForIdle()
            val root = onRoot().fetchSemanticsNode().boundsInRoot
            val field = onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
            onAllNodesWithContentDescription(labels.options).assertCountEquals(1)
            val button = onNodeWithContentDescription(labels.options).fetchSemanticsNode().boundsInRoot
            assertTrue(button.top >= field.bottom, "pushed=$isPushed: button at $button overlaps the field at $field")
            assertTrue(
                root.right - button.right < button.width && root.bottom - button.bottom < button.height,
                "pushed=$isPushed: button at $button is not in the bottom-end corner of $root",
            )
        }
    }

    /**
     * Scrolled to its end, the list's last row and its paging footer sit
     * fully above the button rather than under it.
     */
    @Test
    fun `the end of a long list scrolls clear of the button`() = runComposeUiTest {
        val labels = FilterLabels()
        val hits = (1..30).map { word(id = it.toLong(), text = "語$it") }
        setContent {
            labels.read()
            SearchUnderTest(
                hits = hits,
                names = emptyList(),
                footer = PagingFooterState.Failed(onRetry = {}),
            )
        }

        onNode(hasScrollToIndexAction()).performScrollToIndex(hits.size)
        waitForIdle()

        val button = onNodeWithContentDescription(labels.options).fetchSemanticsNode().boundsInRoot
        val lastRow = onNodeWithText("語30").fetchSemanticsNode().boundsInRoot
        val footer = onNodeWithText(labels.pagingFailed).fetchSemanticsNode().boundsInRoot
        assertTrue(lastRow.bottom <= button.top, "last row at $lastRow runs under the button at $button")
        assertTrue(footer.bottom <= button.top, "footer at $footer runs under the button at $button")
    }

    /**
     * One JMnedict entry becomes several rows — 田中 and 田仲 share an
     * id and differ only in their spelling — so a key of anything less
     * than the whole row makes the list throw "Key was already used".
     * Two rows from one entry is the shape that catches it.
     */
    @Test
    fun `two rows from one entry both render`() = runComposeUiTest {
        setContent {
            SearchUnderTest(
                hits = emptyList(),
                names = listOf(
                    NameHit(5000001, "田中", "たなか", listOf("surname"), "Tanaka"),
                    NameHit(5000001, "田仲", "たなか", listOf("surname"), "Tanaka"),
                ),
            )
        }

        onNodeWithText("田中").assertIsDisplayed()
        onNodeWithText("田仲").assertIsDisplayed()
    }

    /** The clear action keeps the place it has always had on the field. */
    @Test
    fun `the clear action is still on the field`() = runComposeUiTest {
        var cleared = 0
        lateinit var clear: String
        setContent {
            clear = stringResource(Res.string.search_clear)
            SearchUnderTest(hits = listOf(word()), names = emptyList(), onClear = { cleared++ })
        }

        onNodeWithContentDescription(clear).performClick()
        waitForIdle()

        assertEquals(1, cleared)
    }
}

private fun word(id: Long = 1, text: String = "食べる") = SearchHit(
    entryId = id,
    titleSegments = listOf(TitleSegment(text = text)),
    traceLabels = emptyList(),
    senseLines = listOf("to eat"),
    isCommon = false,
)

private fun tanaka() = NameHit(
    id = 5000001,
    kanji = "田中",
    reading = "たなか",
    types = listOf("surname"),
    romanisation = "Tanaka",
)

private fun michiko() = NameHit(
    id = 5000004,
    kanji = null,
    reading = "みちこ",
    types = listOf("fem"),
    romanisation = "Michiko",
)

@Composable
private fun SearchUnderTest(
    hits: List<SearchHit>,
    names: List<NameHit>,
    namesEnabled: Boolean = false,
    onNamesEnabledChange: ((Boolean) -> Unit)? = {},
    onClear: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    footer: PagingFooterState = PagingFooterState.None,
    navigation: RecordingNavigationController = RecordingNavigationController(),
) {
    val query = "たなか"
    CompositionLocalProvider(
        LocalNavigationController provides navigation,
    ) {
        SearchScreen(
            SearchState(
                query = query,
                onQueryChange = {},
                onClear = onClear,
                onBack = onBack,
                namesEnabled = namesEnabled,
                onNamesEnabledChange = onNamesEnabledChange,
                results = SearchResultsState.Results(
                    query = query,
                    hits = hits,
                    isFallback = false,
                    names = names,
                    footer = footer,
                ),
            ),
        )
    }
}

private class FilterLabels {
    var options = ""
    var close = ""
    var names = ""
    var namesOn = ""
    var defaultSearch = ""
    var pagingFailed = ""

    @Composable
    fun read() {
        options = stringResource(Res.string.search_options)
        close = stringResource(Res.string.search_options_close)
        names = stringResource(Res.string.search_names_toggle)
        namesOn = stringResource(Res.string.search_filters_names_on)
        defaultSearch = stringResource(Res.string.search_filters_default)
        pagingFailed = stringResource(Res.string.paging_more_failed)
    }
}

/**
 * The Names pill while the menu is open. Collapsed, the menu keeps the
 * pill composed but clears its semantics, text included, so this matches
 * nothing then — which is what "closed" looks like to a test.
 */
private fun namesPill(labels: FilterLabels) = isToggleable() and hasText(labels.names)

/** A system back the test can press. */
private class TestBackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
}

/**
 * A dispatcher of the test's own, with a handler registered ahead of the
 * screen's that stands in for the shell's back (on the Search root, a
 * switch to the default tab). Registered first, it is the one the
 * screen's handler takes priority over, the same as in `HomeScreen`.
 */
@Composable
private fun BackHost(
    back: TestBackInput,
    onShellBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val owner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher().apply {
                addInput(back)
            }
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
        NavigationBackHandler(
            state = rememberNavigationEventState(NavigationEventInfo.None),
            isBackEnabled = true,
            onBackCompleted = onShellBack,
        )
        content()
    }
}
