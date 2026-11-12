package cc.hosaka.okonomi.feature.home

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cc.hosaka.okonomi.db.appKanjiReadings
import cc.hosaka.okonomi.feature.home.navigation.HomeNavigationItem
import cc.hosaka.okonomi.feature.home.navigation.HomeSelectAction
import cc.hosaka.okonomi.feature.home.navigation.HomeSelectionState
import cc.hosaka.okonomi.feature.home.navigation.LocalHomeReselect
import cc.hosaka.okonomi.feature.home.navigation.homeFavouritesItem
import cc.hosaka.okonomi.feature.home.navigation.homeNavigationItems
import cc.hosaka.okonomi.feature.home.navigation.homeSearchItem
import cc.hosaka.okonomi.feature.home.navigation.homeSelectAction
import cc.hosaka.okonomi.feature.home.navigation.isAtRoot
import cc.hosaka.okonomi.feature.home.navigation.popToRootCount
import cc.hosaka.okonomi.feature.home.navigation.rememberHomeSelectionState
import cc.hosaka.okonomi.feature.navigation.BackStackNavigationController
import cc.hosaka.okonomi.feature.navigation.EntryLinks
import cc.hosaka.okonomi.feature.navigation.appEntryLinks
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController
import cc.hosaka.okonomi.feature.navigation.Route
import cc.hosaka.okonomi.feature.navigation.TextLookups
import cc.hosaka.okonomi.feature.navigation.appTextLookups
import cc.hosaka.okonomi.feature.navigation.navigationSavedStateConfiguration
import cc.hosaka.okonomi.feature.navigation.routeEntryProvider
import cc.hosaka.okonomi.feature.search.SearchRoute
import cc.hosaka.okonomi.feature.word.EntryRoute
import cc.hosaka.okonomi.lang.ProvideAppLanguage
import cc.hosaka.okonomi.lang.ProvideLearningLanguage
import cc.hosaka.okonomi.lang.currentDeviceLanguage
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.prefs.appPreferences
import cc.hosaka.okonomi.ui.coach.COACH_MARKS_ENABLED_DEFAULT
import cc.hosaka.okonomi.ui.coach.COACH_MARKS_ENABLED_PREFERENCE
import cc.hosaka.okonomi.ui.coach.CoachMarkRegistry
import cc.hosaka.okonomi.ui.coach.CoachMarksHost
import cc.hosaka.okonomi.ui.coach.CoachNavigation
import cc.hosaka.okonomi.ui.coach.CoachTarget
import cc.hosaka.okonomi.ui.coach.coachMarkTarget
import cc.hosaka.okonomi.ui.furigana.KanjiReadings
import cc.hosaka.okonomi.ui.furigana.ProvideKanjiReadings
import org.jetbrains.compose.resources.stringResource

/**
 * How long the navigation affordance takes to get out of a pushed
 * screen's way. The consumed bottom inset animates on the same tween,
 * so the content never jumps by the inset height mid-transition.
 */
private const val NAVIGATION_ANIMATION_MILLIS = 220

/**
 * How long one screen takes to replace another. This is the value
 * `NavDisplay`'s own default fade used, kept deliberately: the push and
 * the toolbar back already looked right at it, and the fix below is
 * about the transitions that did not match, not about retiming the one
 * that did.
 */
private const val SCREEN_TRANSITION_MILLIS = 700

/**
 * One specification for every screen transition.
 *
 * `NavDisplay` ships three separate defaults, and two of them disagree:
 * push and pop are a 700ms cross fade, while a predictive (system or
 * gesture) back is `fadeIn(spring(stiffness = 1600f))` against
 * `scaleOut(targetScale = 0.7f)`. On a device that reads as two
 * different apps — the toolbar arrow dissolves the screen, the back
 * gesture snaps it away and shrinks it. Handing all three the same fade
 * is what makes the two backs feel like one gesture with two triggers.
 *
 * What this does not reach: Android 14+ draws the predictive back
 * preview itself. The system's window-level scale and slide, the edge
 * affordance, the progress curve the gesture is interpolated along and
 * the cross-activity animation when the gesture would leave the app are
 * all outside the composition and cannot be specified from here. What is
 * ours is the transition between two of our screens, which now has one
 * definition; a gesture cancelled halfway is likewise the system's to
 * unwind. Deliberately not fought with a custom gesture handler.
 */
private val screenTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        fadeIn(tween(SCREEN_TRANSITION_MILLIS)) togetherWith
            fadeOut(tween(SCREEN_TRANSITION_MILLIS))
    }

/**
 * The same fade again, in the shape the predictive-back parameter wants.
 * The `Int` it is handed is the edge the gesture started from, which a
 * transition with no direction in it has no use for.
 */
private val predictiveScreenTransitionSpec:
    AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = { _ ->
        screenTransitionSpec()
    }

/**
 * The shell of the app: a navigation bar (portrait) or a navigation
 * rail (landscape) that switches between the top-level sections,
 * each of which keeps its own back stack.
 *
 * Over the idle Search tab the shell draws the coach marks, which point
 * at Search's field and filters button and at the shell's own Favourites
 * tab.
 */
@Composable
fun HomeScreen(
    items: List<HomeNavigationItem> = homeNavigationItems,
) {
    HomeScreen(
        items = items,
        coachMarks = remember { CoachMarkRegistry() },
    )
}

/**
 * Opens what other apps hand okonomi: each entry link [links] delivers
 * and each text from another app [lookups] delivers. Both are opened
 * the same way: the Search section is selected, dropped back to its
 * root and a marked route pushed over it (the entry, or a search for
 * the text), so back returns to an empty Search rather than to wherever
 * the reader had been. Without a Search section neither is opened.
 *
 * Text selected in okonomi itself is not a hand-off: the reader is still
 * reading the screen it came from. Its search is pushed, unmarked, over
 * that screen in whichever section is selected, so back returns to it.
 *
 * The pushed route stays marked only while the reader stays on Search.
 * Switching to another tab means they have taken up okonomi on its own
 * terms, so the mark is cleared and back on that route is an ordinary
 * pop from then on.
 */
@Composable
private fun OpenHandOffs(
    links: EntryLinks,
    lookups: TextLookups,
    selection: HomeSelectionState,
    sections: List<HomeSection>,
) {
    val currentSections by rememberUpdatedState(sections)
    LaunchedEffect(links, selection) {
        links.requests.collect { entryId ->
            openOverSearchRoot(EntryRoute(entryId, openedFromLink = true), selection, currentSections)
        }
    }
    LaunchedEffect(lookups, selection) {
        lookups.requests.collect { lookup ->
            if (lookup.fromOkonomi) {
                currentSections.firstOrNull { it.item.key == selection.selectedKey }
                    ?.controller
                    ?.navigate(SearchRoute(lookup.query))
            } else {
                openOverSearchRoot(SearchRoute(lookup.query, openedFromSelection = true), selection, currentSections)
            }
        }
    }
    LaunchedEffect(selection.selectedKey) {
        if (selection.selectedKey == homeSearchItem.key) return@LaunchedEffect
        val search = currentSections.firstOrNull { it.item.key == homeSearchItem.key } ?: return@LaunchedEffect
        clearHandOffMarks(search.backStack)
    }
}

/** Selects the Search section, drops it to its root and pushes [route]. */
private fun openOverSearchRoot(
    route: Route,
    selection: HomeSelectionState,
    sections: List<HomeSection>,
) {
    val search = sections.firstOrNull { it.item.key == homeSearchItem.key } ?: return
    selection.switchTo(search.item)
    search.popToRoot()
    search.controller.navigate(route)
}

/**
 * [route] as an ordinary route of its kind, or null when it carries no
 * hand-off mark: a link-opened entry or a search opened from another
 * app's selection.
 */
private fun withoutHandOffMark(route: NavKey): NavKey? = when {
    route is EntryRoute && route.openedFromLink -> route.copy(openedFromLink = false)
    route is SearchRoute && route.openedFromSelection -> route.copy(openedFromSelection = false)
    else -> null
}

/**
 * Makes every hand-off route on [backStack] an ordinary one, in place.
 * One already there as an ordinary route is dropped instead: two equal
 * keys on one stack is what navigation must never hold (see
 * `BackStackNavigationController.navigate`).
 */
private fun clearHandOffMarks(backStack: NavBackStack<NavKey>) {
    for (index in backStack.indices.reversed()) {
        val ordinary = withoutHandOffMark(backStack[index]) ?: continue
        if (ordinary in backStack) backStack.removeAt(index) else backStack[index] = ordinary
    }
}

/**
 * What a test can see and drive of one section; see [HomeScreen]'s
 * `onSection`. [select] switches to the section the way a tab tap does,
 * which a test needs because the bar is hidden while a screen is pushed.
 */
internal class HomeSectionProbe(
    val controller: NavigationController,
    val stack: () -> List<NavKey>,
    val select: () -> Unit,
)

/**
 * [HomeScreen] with its seams handed in: the registry the coach marks
 * report into, so tests can read where the targets landed; the entry
 * links and the text lookups to open; [leaveApp], what system back on a
 * link-opened entry or a looked-up search calls; [onSection], which
 * hands a test each section's navigation and back stack;
 * [preferences], where the UI language, the coach marks' on/off
 * setting, the language the reader is learning and the per-kanji
 * furigana setting are read from; [loadKanjiReadings], what that
 * furigana divides kanji runs with (see [ProvideShellSettings], which
 * the whole shell is drawn inside); and [deviceLanguage], the language
 * code a reader who never chose a learning language is defaulted from,
 * so a test does not depend on the language of the machine it runs on.
 */
@Composable
internal fun HomeScreen(
    items: List<HomeNavigationItem> = homeNavigationItems,
    coachMarks: CoachMarkRegistry,
    entryLinks: EntryLinks = appEntryLinks,
    textLookups: TextLookups = appTextLookups,
    leaveApp: () -> Unit = rememberLeaveApp(),
    onSection: ((key: String, probe: HomeSectionProbe) -> Unit)? = null,
    preferences: PreferenceStore = appPreferences(),
    loadKanjiReadings: suspend () -> KanjiReadings = ::appKanjiReadings,
    deviceLanguage: String = currentDeviceLanguage(),
) {
    require(items.isNotEmpty()) { "Home needs at least one section" }
    require(items.distinctBy { it.key }.size == items.size) { "Home section keys must be unique" }
    // Decorators are shared by all sections; the entries of every
    // section stay remembered even while another section is shown,
    // so switching tabs never counts as popping an entry and the
    // per-entry saved state and view models are kept.
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>(
            viewModelStoreOwner = rememberEntryViewModelStoreOwner(),
        ),
    )
    val sections = items.map { item ->
        key(item.key) {
            rememberHomeSection(
                item = item,
                entryDecorators = entryDecorators,
            )
        }
    }
    val selection = rememberHomeSelectionState(items)
    OpenHandOffs(entryLinks, textLookups, selection, sections)
    if (onSection != null) {
        SideEffect {
            sections.forEach { section ->
                onSection(
                    section.item.key,
                    HomeSectionProbe(
                        controller = section.controller,
                        stack = { section.backStack.toList() },
                        select = { selection.switchTo(section.item) },
                    ),
                )
            }
        }
    }
    val selectedItem = selection.selected
    val selectedSection = sections.first { it.item.key == selectedItem.key }
    // The shell owns both the selection and the per-section back
    // stacks, so it is the only place that can tell a "go back to the
    // top of this section" tap from a "focus the section" tap.
    val onSelect = { item: HomeNavigationItem ->
        val section = sections.first { it.item.key == item.key }
        when (
            homeSelectAction(
                tappedKey = item.key,
                selectedKey = selection.selectedKey,
                depth = section.depth,
            )
        ) {
            HomeSelectAction.Switch -> selection.switchTo(item)
            HomeSelectAction.SignalReselect -> selection.signalReselect(item)
            HomeSelectAction.PopToRoot -> section.popToRoot()
        }
    }

    // System back on a section root returns to the default section.
    // Registered before the NavDisplay so that the display's own
    // handler, which pops within a section, takes priority while its
    // back stack is deeper than the root.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selection.isBackEnabled,
        onBackCompleted = {
            selection.onBack()
        },
    )

    // A pushed screen owns the whole window: the shell's own navigation
    // affordance animates away while the active section is deeper than
    // its root and comes back when the stack pops to it.
    val showNavigation = isAtRoot(selectedSection.depth)

    // False until the store answers, not the default: a reader who
    // switched the marks off must never see them flash in on a cold start.
    val coachMarksEnabled by remember(preferences) {
        preferences.booleanFlow(COACH_MARKS_ENABLED_PREFERENCE, COACH_MARKS_ENABLED_DEFAULT)
    }.collectAsState(initial = false)

    ProvideShellSettings(
        preferences = preferences,
        loadKanjiReadings = loadKanjiReadings,
        deviceLanguage = deviceLanguage,
    ) {
        ResponsiveLayout {
            val horizontalInsets = WindowInsets.systemBars
                .union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            CoachMarksHost(
                enabled = coachMarksEnabled,
                searchSelected = selectedItem.key == homeSearchItem.key,
                atRoot = showNavigation,
                navigation = when (LocalHomeLayout.current) {
                    HomeLayout.Vertical -> CoachNavigation.BottomBar
                    HomeLayout.Horizontal -> CoachNavigation.Rail
                },
                registry = coachMarks,
            ) {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(horizontalInsets),
                ) {
                    val layout = LocalHomeLayout.current
                    if (layout is HomeLayout.Horizontal) {
                        AnimatedVisibility(
                            visible = showNavigation,
                            enter = expandHorizontally(tween(NAVIGATION_ANIMATION_MILLIS)) +
                                fadeIn(tween(NAVIGATION_ANIMATION_MILLIS)),
                            exit = shrinkHorizontally(tween(NAVIGATION_ANIMATION_MILLIS)) +
                                fadeOut(tween(NAVIGATION_ANIMATION_MILLIS)),
                        ) {
                            HomeNavigationRail(
                                items = items,
                                selectedItem = selectedItem,
                                onSelect = onSelect,
                                enabled = showNavigation,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        val bottomInset = WindowInsets.systemBars
                            .union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues()
                            .calculateBottomPadding()
                        // The bar below handles the bottom insets while it is
                        // there; with it gone the pushed screen owns that edge and
                        // needs them back. Handing them over in the same tween the
                        // bar animates in keeps the content from jumping by the
                        // inset height at either end of the transition.
                        val consumedBottom by animateDpAsState(
                            targetValue = if (layout is HomeLayout.Vertical && showNavigation) bottomInset else 0.dp,
                            animationSpec = tween(NAVIGATION_ANIMATION_MILLIS),
                            label = "consumed bottom inset",
                        )
                        HomeNavigationContent(
                            section = selectedSection,
                            leaveApp = leaveApp,
                            reselectCount = selection.reselectionsOf(selectedItem.key),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .consumeWindowInsets(PaddingValues(bottom = consumedBottom)),
                        )
                        if (layout is HomeLayout.Vertical) {
                            AnimatedVisibility(
                                visible = showNavigation,
                                enter = expandVertically(tween(NAVIGATION_ANIMATION_MILLIS)) +
                                    fadeIn(tween(NAVIGATION_ANIMATION_MILLIS)),
                                exit = shrinkVertically(tween(NAVIGATION_ANIMATION_MILLIS)) +
                                    fadeOut(tween(NAVIGATION_ANIMATION_MILLIS)),
                            ) {
                                HomeNavigationBar(
                                    items = items,
                                    selectedItem = selectedItem,
                                    onSelect = onSelect,
                                    // A bar on its way out must not switch section
                                    // under the screen that just pushed over it.
                                    enabled = showNavigation,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The settings the whole shell is drawn under. Outermost, the language
 * the app's text is in ([ProvideAppLanguage]): nothing is drawn until
 * it is known, and a new one redraws everything. Then everything that
 * decides how a word is drawn, for every section at once: the language
 * the reader is learning, and with it whether furigana is shown
 * ([ProvideLearningLanguage]), and the kanji readings that furigana
 * divides its runs with ([ProvideKanjiReadings]).
 */
@Composable
private fun ProvideShellSettings(
    preferences: PreferenceStore,
    loadKanjiReadings: suspend () -> KanjiReadings,
    deviceLanguage: String,
    content: @Composable () -> Unit,
) {
    ProvideAppLanguage(preferences = preferences) {
        // Outside the kanji readings, which stop dividing while furigana
        // is hidden and read that from here.
        ProvideLearningLanguage(preferences = preferences, deviceLanguage = deviceLanguage) {
            ProvideKanjiReadings(preferences = preferences, load = loadKanjiReadings, content = content)
        }
    }
}

/**
 * The owner the entries' view models are kept under: the current
 * [LocalViewModelStoreOwner]'s store, and nothing else of it.
 *
 * Handed the activity itself, `rememberViewModelStoreNavEntryDecorator`
 * builds its store provider remembered on the owner's creation extras.
 * A `ComponentActivity` puts its intent's extras in those, and
 * `Intent.getExtras()` returns a new `Bundle` on every read, so while the
 * intent carries extras (every forwarded lookup does, and `MainActivity`
 * keeps it with `setIntent`; so does a link with any extra) each
 * recomposition of the shell built a new provider. Disposing the old one
 * cleared every entry's store: every screen lost its state, Search's root
 * its typed query, on every lookup.
 *
 * Not being a `HasDefaultViewModelProviderFactory`, this owner gives the
 * decorator constant empty extras and the default factory. That is all
 * the app's one view model needs (`ScreenStateViewModel`, built by an
 * initializer); a `SavedStateHandle` would not be available through it.
 */
@Composable
private fun rememberEntryViewModelStoreOwner(): ViewModelStoreOwner {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) {
        "HomeScreen needs a ViewModelStoreOwner for its entries' view models"
    }
    return remember(owner) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore
                get() = owner.viewModelStore
        }
    }
}

/**
 * A top-level section with its own back stack, navigation
 * controller and decorated entries.
 */
private class HomeSection(
    val item: HomeNavigationItem,
    val backStack: NavBackStack<NavKey>,
    val controller: NavigationController,
    val entries: List<NavEntry<NavKey>>,
) {
    val depth: Int
        get() = backStack.size

    /** Drops every pushed screen, leaving the section's root route. */
    fun popToRoot() {
        repeat(popToRootCount(depth)) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}

@Composable
private fun rememberHomeSection(
    item: HomeNavigationItem,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
): HomeSection {
    val backStack = rememberNavBackStack(navigationSavedStateConfiguration, item.route)
    val controller = remember(backStack) {
        BackStackNavigationController(backStack)
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = entryDecorators,
        entryProvider = routeEntryProvider,
    )
    return remember(item, backStack, controller, entries) {
        HomeSection(
            item = item,
            backStack = backStack,
            controller = controller,
            entries = entries,
        )
    }
}

@Composable
private fun HomeNavigationContent(
    section: HomeSection,
    leaveApp: () -> Unit,
    reselectCount: Int,
    modifier: Modifier = Modifier,
) {
    // Each section gets its own display, so switching tabs is a
    // plain swap rather than a transition between unrelated stacks.
    key(section.item.key) {
        CompositionLocalProvider(
            LocalNavigationController provides section.controller,
            LocalHomeReselect provides reselectCount,
        ) {
            NavDisplay(
                entries = section.entries,
                modifier = modifier
                    .fillMaxSize(),
                transitionSpec = screenTransitionSpec,
                popTransitionSpec = screenTransitionSpec,
                predictivePopTransitionSpec = predictiveScreenTransitionSpec,
                onBack = {
                    section.controller.pop()
                },
            )
            LeaveFromHandOff(section, leaveApp)
        }
    }
}

/**
 * System back on a route another app handed over goes back to that app
 * rather than to okonomi's Search: from the entry a link opened, to the
 * app that sent the link (AnkiDroid); from the search a text selection
 * opened, to the app the text was selected in. The section is dropped to
 * its root first, so the next launch shows Search and not a stale
 * screen, and then [leaveApp] sends okonomi's task to the back.
 *
 * Only while that route is the top of its section: a screen pushed over
 * it pops normally first. Registered after the section's NavDisplay so
 * it outranks the display's own pop — the later handler wins, the same
 * ordering the section-root handler in [HomeScreen] relies on the other
 * way round. A screen's own handler still outranks this one (the search
 * filters menu's registers once the screen's state has loaded), so back
 * with a lookup's menu open closes the menu; `HomeEntryLinkUiTest` holds
 * that. The header arrow on either route is a plain pop and never
 * reaches this.
 */
@Composable
private fun LeaveFromHandOff(
    section: HomeSection,
    leaveApp: () -> Unit,
) {
    val top = section.backStack.lastOrNull()
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = top != null && withoutHandOffMark(top) != null,
        onBackCompleted = {
            section.popToRoot()
            leaveApp()
        },
    )
}

@Composable
private fun HomeNavigationRail(
    items: List<HomeNavigationItem>,
    selectedItem: HomeNavigationItem,
    onSelect: (HomeNavigationItem) -> Unit,
    enabled: Boolean = true,
) {
    val verticalInsets = WindowInsets.systemBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Vertical)
    WideNavigationRail(
        modifier = Modifier
            .fillMaxHeight(),
        windowInsets = verticalInsets,
    ) {
        items.forEach { item ->
            key(item.key) {
                val selected = item.key == selectedItem.key
                WideNavigationRailItem(
                    icon = {
                        NavigationIcon(
                            selected = selected,
                            icon = item.icon,
                            iconSelected = item.iconSelected,
                            modifier = Modifier
                                .favouritesCoachMarkTarget(item),
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(item.label),
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    },
                    selected = selected,
                    railExpanded = false,
                    enabled = enabled,
                    onClick = {
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeNavigationBar(
    items: List<HomeNavigationItem>,
    selectedItem: HomeNavigationItem,
    onSelect: (HomeNavigationItem) -> Unit,
    enabled: Boolean = true,
) {
    ShortNavigationBar {
        items.forEach { item ->
            key(item.key) {
                val selected = item.key == selectedItem.key
                ShortNavigationBarItem(
                    icon = {
                        NavigationIcon(
                            selected = selected,
                            icon = item.icon,
                            iconSelected = item.iconSelected,
                            modifier = Modifier
                                .favouritesCoachMarkTarget(item),
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(item.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            // Default style does not fit on devices with small
                            // screens.
                            fontSize = 10.sp,
                        )
                    },
                    selected = selected,
                    enabled = enabled,
                    onClick = {
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    selected: Boolean,
    icon: ImageVector,
    iconSelected: ImageVector,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = selected,
        modifier = modifier,
    ) {
        val vector = if (it) {
            iconSelected
        } else {
            icon
        }
        Icon(vector, null)
    }
}

/**
 * The Favourites tab's icon, in the bar or the rail, is what its coach
 * mark points at: the icon rather than the whole item, whose top edge is
 * the bar's own, so an arrow aimed at the item stopped above the bar
 * instead of on the heart.
 */
private fun Modifier.favouritesCoachMarkTarget(item: HomeNavigationItem): Modifier =
    if (item.key == homeFavouritesItem.key) coachMarkTarget(CoachTarget.Favourites) else this
