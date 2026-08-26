package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import cc.hosaka.okonomi.db.NameHit
import cc.hosaka.okonomi.db.SearchHit
import cc.hosaka.okonomi.db.TitleSegment
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import cc.hosaka.okonomi.ui.test.RecordingNavigationController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.name_type_fem
import okonomi.shared.generated.resources.name_type_surname
import okonomi.shared.generated.resources.search_clear
import okonomi.shared.generated.resources.search_names_toggle
import okonomi.shared.generated.resources.search_no_results
import okonomi.shared.generated.resources.search_options
import org.jetbrains.compose.resources.stringResource

/**
 * What the producer tests cannot see: where the name rows land on screen,
 * what a name row is made of, that tapping one does nothing, and that the
 * toggle is reachable from the field.
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

    /** Alex's ruling: a name has no entry view to open, so the row is inert. */
    @Test
    fun `a name row is not clickable and opens nothing`() = runComposeUiTest {
        val navigation = RecordingNavigationController()
        setContent {
            SearchUnderTest(hits = emptyList(), names = listOf(tanaka()), navigation = navigation)
        }

        onNodeWithText("Tanaka").assertHasNoClickAction()
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
    fun `the overflow menu on the field carries the names toggle and tapping the row flips it`() = runComposeUiTest {
        val toggled = mutableListOf<Boolean>()
        lateinit var options: String
        lateinit var names: String
        setContent {
            options = stringResource(Res.string.search_options)
            names = stringResource(Res.string.search_names_toggle)
            SearchUnderTest(
                hits = listOf(word()),
                names = emptyList(),
                namesEnabled = false,
                onNamesEnabledChange = { toggled += it },
            )
        }

        onNodeWithText(names).assertDoesNotExist()
        onNodeWithContentDescription(options).performClick()
        waitForIdle()

        onNodeWithText(names).assertIsDisplayed()
        onNodeWithText(names).performClick()
        waitForIdle()

        assertEquals(listOf(true), toggled, "the menu item must ask for the opposite of what is stored")
    }

    /**
     * The switch's own drawn state, not a hand-written semantics value
     * beside it. A decorative `Switch(checked = …)` reports nothing, so
     * a switch stuck at "off" beside names that are on would have gone
     * unnoticed by every assertion here — the state read below is the
     * very `checked` the switch renders.
     */
    @Test
    fun `the switch is drawn in the state the toggle is actually in`() = runComposeUiTest {
        val namesOn = mutableStateOf(false)
        lateinit var options: String
        setContent {
            options = stringResource(Res.string.search_options)
            SearchUnderTest(
                hits = listOf(word()),
                names = emptyList(),
                namesEnabled = namesOn.value,
                onNamesEnabledChange = { namesOn.value = it },
            )
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()
        onNode(isToggleable()).assertIsOff()

        runOnIdle { namesOn.value = true }
        waitForIdle()
        onNode(isToggleable()).assertIsOn()
    }

    /** The switch itself is a target too, not only the row around it. */
    @Test
    fun `tapping the switch toggles it`() = runComposeUiTest {
        val toggled = mutableListOf<Boolean>()
        lateinit var options: String
        setContent {
            options = stringResource(Res.string.search_options)
            SearchUnderTest(
                hits = listOf(word()),
                names = emptyList(),
                namesEnabled = false,
                onNamesEnabledChange = { toggled += it },
            )
        }

        onNodeWithContentDescription(options).performClick()
        waitForIdle()
        onNode(isToggleable()).performClick()
        waitForIdle()

        assertEquals(listOf(true), toggled)
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

    /** The clear action keeps the place it has always had; the menu joins it. */
    @Test
    fun `the clear action is still there beside the menu`() = runComposeUiTest {
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

private fun word() = SearchHit(
    entryId = 1,
    titleSegments = listOf(TitleSegment(text = "食べる")),
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
                namesEnabled = namesEnabled,
                onNamesEnabledChange = onNamesEnabledChange,
                results = SearchResultsState.Results(
                    query = query,
                    hits = hits,
                    isFallback = false,
                    names = names,
                ),
            ),
        )
    }
}
