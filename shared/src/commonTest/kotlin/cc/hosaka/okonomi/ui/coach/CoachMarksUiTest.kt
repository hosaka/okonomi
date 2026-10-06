package cc.hosaka.okonomi.ui.coach

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import cc.hosaka.okonomi.feature.home.HomeScreen
import cc.hosaka.okonomi.feature.navigation.BackStackNavigationController
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.navigationSavedStateConfiguration
import cc.hosaka.okonomi.feature.navigation.routeEntryProvider
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.ui.test.ComposeUiTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.home_favourites_label
import okonomi.shared.generated.resources.search_clear
import okonomi.shared.generated.resources.search_options
import okonomi.shared.generated.resources.search_options_close
import org.jetbrains.compose.resources.stringResource

/**
 * The coach marks over the real shell: when the overlay is there, what it
 * laid out, that touches go through it, and that the targets report the
 * elements the arrows are meant for.
 *
 * What the marks look like is not observable here — the arrows and notes
 * are drawn on a canvas and Robolectric lays text out at zero width — so
 * where they go is pinned through the registry's last layout and in
 * [CoachMarksLayoutTest], and the look is checked on a device.
 */
@OptIn(ExperimentalTestApi::class)
class CoachMarksUiTest : ComposeUiTestBase() {

    @Test
    fun `the idle search tab lays out all three marks in portrait`() = runComposeUiTest {
        val registry = CoachMarkRegistry()
        setContent { HomeScreen(coachMarks = registry) }
        waitForIdle()

        onNodeWithTag(COACH_MARKS_TAG).assertExists()
        val layout = assertNotNull(registry.lastLayout, "the overlay laid nothing out")
        assertEquals(CoachTarget.entries.toSet(), layout.marks.map { it.target }.toSet())
    }

    @Test
    fun `the idle search tab lays out all three marks in landscape`() = runComposeUiTest {
        val registry = CoachMarkRegistry()
        setContent { Landscape { HomeScreen(coachMarks = registry) } }
        waitForIdle()

        val layout = assertNotNull(registry.lastLayout, "the overlay laid nothing out")
        assertEquals(CoachTarget.entries.toSet(), layout.marks.map { it.target }.toSet())
        val rail = assertNotNull(registry.targets[CoachTarget.Favourites])
        val arrow = layout.marks.single { it.target == CoachTarget.Favourites }.arrow
        assertTrue(arrow.end.x > rail.right && arrow.end.y in rail.top..rail.bottom, "arrow ends at ${arrow.end}, rail item at $rail")
    }

    @Test
    fun `typing hides the marks and clearing the field brings them back`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            HomeScreen()
        }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()

        onNode(hasSetTextAction()).performTextInput("た")
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertDoesNotExist()

        onNodeWithContentDescription(labels.clear).performClick()
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()
    }

    /** A blank query leaves the results idle, but the field is not empty. */
    @Test
    fun `a field holding only spaces hides the marks`() = runComposeUiTest {
        setContent { HomeScreen() }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()

        onNode(hasSetTextAction()).performTextInput("  ")
        waitForIdle()

        onNodeWithTag(COACH_MARKS_TAG).assertDoesNotExist()
    }

    /** The tap that opens the menu lands through the marks, too. */
    @Test
    fun `the marks leave while the filters menu is open and return when the button closes it`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            HomeScreen()
        }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()

        onNodeWithContentDescription(labels.options).performClick()
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertDoesNotExist()

        onNodeWithContentDescription(labels.close).performClick()
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()
    }

    @Test
    fun `the marks return when system back closes the filters menu`() = runComposeUiTest {
        val labels = Labels()
        val back = TestBackInput()
        setContent {
            labels.read()
            BackHost(back) { HomeScreen() }
        }
        waitForIdle()

        onNodeWithContentDescription(labels.options).performClick()
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertDoesNotExist()

        runOnIdle { back.back() }
        waitForIdle()

        onNodeWithContentDescription(labels.options).assertExists()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()
    }

    @Test
    fun `the field takes a tap through the marks`() = runComposeUiTest {
        setContent { HomeScreen() }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()

        onNode(hasSetTextAction()).performClick()
        waitForIdle()

        onNode(hasSetTextAction()).assertIsFocused()
    }

    /** Leaving the Search tab takes the marks with it. */
    @Test
    fun `the favourites tab takes a tap through the marks`() = runComposeUiTest {
        val labels = Labels()
        setContent {
            labels.read()
            HomeScreen()
        }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()

        onNode(favouritesTab(labels)).performClick()
        waitForIdle()

        onNode(favouritesTab(labels)).assertIsSelected()
        onNodeWithTag(COACH_MARKS_TAG).assertDoesNotExist()
    }

    /**
     * The keyboard never rises in the host runtime, so the host is handed
     * one; this pins that the host listens to what it is handed.
     */
    @Test
    fun `a raised keyboard hides the marks`() = runComposeUiTest {
        val ime = mutableStateOf(false)
        setContent {
            CoachMarksHost(
                searchSelected = true,
                atRoot = true,
                navigation = CoachNavigation.BottomBar,
                registry = remember { CoachMarkRegistry() },
                imeVisible = ime.value,
            ) {
                CoachMarksWanted(true)
            }
        }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()

        ime.value = true
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertDoesNotExist()

        ime.value = false
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()
    }

    /**
     * A search pushed over the root is idle with an empty field too — the
     * root rule alone keeps the marks off it, and off the root's targets.
     * Popping back must bring them back with every target in place.
     */
    @Test
    fun `a search pushed over the root shows no marks and popping back restores them`() = runComposeUiTest {
        val registry = CoachMarkRegistry()
        lateinit var navigation: NavigationController
        setContent {
            CoachMarksHost(
                searchSelected = true,
                atRoot = true,
                navigation = CoachNavigation.BottomBar,
                registry = registry,
            ) {
                navigation = SearchSectionUnderTest()
            }
        }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()

        runOnIdle { navigation.navigate(SearchRoute("")) }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertDoesNotExist()
        assertNull(registry.targets[CoachTarget.Filters], "a pushed search must not report the filters target")
        assertNull(registry.targets[CoachTarget.SearchField], "a pushed search must not report the field target")

        runOnIdle { navigation.pop() }
        waitForIdle()
        onNodeWithTag(COACH_MARKS_TAG).assertExists()
        assertEquals(
            setOf(CoachTarget.SearchField, CoachTarget.Filters),
            registry.targets.keys,
            "the root's targets must be back after the pop",
        )
    }

    /** Targets report window positions; the overlay draws from the host's. */
    @Test
    fun `targets are measured from the host wherever the host sits`() = runComposeUiTest {
        val registry = CoachMarkRegistry()
        var expected = Rect.Zero
        setContent {
            with(LocalDensity.current) {
                expected = Rect(12.dp.toPx(), 34.dp.toPx(), 112.dp.toPx(), 90.dp.toPx())
            }
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .padding(start = 40.dp, top = 30.dp),
            ) {
                CoachMarksHost(
                    searchSelected = true,
                    atRoot = true,
                    navigation = CoachNavigation.BottomBar,
                    registry = registry,
                ) {
                    Box(
                        modifier = Modifier
                            .offset(12.dp, 34.dp)
                            .size(100.dp, 56.dp)
                            .coachMarkTarget(CoachTarget.SearchField),
                    )
                }
            }
        }
        waitForIdle()

        val reported = assertNotNull(registry.targets[CoachTarget.SearchField])
        assertEquals(expected.left, reported.left, 0.5f, "reported $reported, expected $expected")
        assertEquals(expected.top, reported.top, 0.5f, "reported $reported, expected $expected")
    }

    /**
     * The overlay clears the semantics of everything inside it, so nothing
     * it draws, now or later, can reach a screen reader; what is left on
     * its node is the test tag and nothing a reader would announce.
     */
    @Test
    fun `the overlay clears its semantics and announces nothing`() = runComposeUiTest {
        setContent { HomeScreen() }
        waitForIdle()

        val config = onNodeWithTag(COACH_MARKS_TAG, useUnmergedTree = true).fetchSemanticsNode().config
        assertTrue(config.isClearingSemantics, "the overlay must clear its semantics")
        assertNull(config.getOrNull(SemanticsProperties.Text))
        assertNull(config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun `portrait targets are the field the filters button and the favourites tab`() = runComposeUiTest {
        val registry = CoachMarkRegistry()
        val labels = Labels()
        setContent {
            labels.read()
            HomeScreen(coachMarks = registry)
        }
        waitForIdle()

        assertTargetsReported(registry, labels)
    }

    /** Landscape draws a rail instead of a bar; its Favourites item reports too. */
    @Test
    fun `landscape targets are the field the filters button and the favourites rail item`() = runComposeUiTest {
        val registry = CoachMarkRegistry()
        val labels = Labels()
        setContent {
            labels.read()
            Landscape { HomeScreen(coachMarks = registry) }
        }
        waitForIdle()

        val targets = registry.targets
        val favourites = assertNotNull(targets[CoachTarget.Favourites])
        val field = assertNotNull(targets[CoachTarget.SearchField])
        assertTrue(favourites.right <= field.left, "a rail item sits beside the field: $favourites, $field")
        assertTargetsReported(registry, labels)
    }

    private fun ComposeUiTest.assertTargetsReported(registry: CoachMarkRegistry, labels: Labels) {
        val targets = registry.targets
        assertCoincide(
            "search field",
            assertNotNull(targets[CoachTarget.SearchField]),
            onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot,
        )
        assertCoincide(
            "filters button",
            assertNotNull(targets[CoachTarget.Filters]),
            onNodeWithContentDescription(labels.options).fetchSemanticsNode().boundsInRoot,
        )
        assertIsTabIcon(
            assertNotNull(targets[CoachTarget.Favourites]),
            onNode(favouritesTab(labels)).fetchSemanticsNode().boundsInRoot,
        )
    }

    /**
     * Favourites reports its tab's icon, not the whole item: the item's top
     * edge is the bar's own, so an arrow aimed at it stopped above the bar
     * instead of on the heart (seen on a Pixel 6). The icon is the 24dp
     * square inside the item, centred across it and above its label.
     */
    private fun ComposeUiTest.assertIsTabIcon(reported: Rect, tab: Rect) {
        val icon = with(density) { 24.dp.toPx() }
        assertTrue(
            tab.contains(reported.topLeft) && tab.contains(reported.bottomRight - Offset(1f, 1f)),
            "favourites icon reported at $reported, outside its tab at $tab",
        )
        assertEquals(icon, reported.width, 1f, "favourites reported $reported, not the icon")
        assertEquals(icon, reported.height, 1f, "favourites reported $reported, not the icon")
        assertEquals(tab.center.x, reported.center.x, 1f, "the icon is centred across its tab")
        assertTrue(reported.center.y < tab.center.y, "the icon sits above its label: $reported in $tab")
    }

    /**
     * The reported rect and the element's node contain each other's
     * centre. Not equality: the field reports its pill, whose text node is
     * inset within it.
     */
    private fun assertCoincide(name: String, reported: Rect, node: Rect) {
        assertTrue(
            reported.contains(node.center) && node.contains(reported.center),
            "$name reported at $reported, but the element is at $node",
        )
    }

    private fun favouritesTab(labels: Labels) =
        hasText(labels.favourites) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
}

/** A 640×360 window, placed at the root's origin whatever the real window is. */
@Composable
private fun Landscape(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(640.dp, 360.dp),
    ) {
        content()
    }
}

/** A Search section as `HomeScreen` builds one; see `SearchRouteUiTest`. */
@Composable
private fun SearchSectionUnderTest(): NavigationController {
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, SearchRoute())
    val controller = remember(backStack) { BackStackNavigationController(backStack) }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            rememberViewModelStoreNavEntryDecorator<NavKey>(),
        ),
        entryProvider = routeEntryProvider,
    )
    CompositionLocalProvider(LocalNavigationController provides controller) {
        NavDisplay(
            entries = entries,
            onBack = { controller.pop() },
        )
    }
    return controller
}

/** A system back the test can press. */
private class TestBackInput : NavigationEventInput() {
    fun back() = dispatchOnBackCompleted()
}

/** A back dispatcher of the test's own, wired to [back]. */
@Composable
private fun BackHost(back: TestBackInput, content: @Composable () -> Unit) {
    val owner = remember {
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher().apply {
                addInput(back)
            }
        }
    }
    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
        content()
    }
}

private class Labels {
    var clear = ""
    var options = ""
    var close = ""
    var favourites = ""

    @Composable
    fun read() {
        clear = stringResource(Res.string.search_clear)
        options = stringResource(Res.string.search_options)
        close = stringResource(Res.string.search_options_close)
        favourites = stringResource(Res.string.home_favourites_label)
    }
}
