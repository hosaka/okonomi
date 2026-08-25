package cc.hosaka.okonomi.feature.word

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.feature.forms.FormsTab
import cc.hosaka.okonomi.feature.kanji.KanjiTab
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.phrases.PhrasesTab
import cc.hosaka.okonomi.ui.CenteredBox
import cc.hosaka.okonomi.ui.CenteredMessage
import cc.hosaka.okonomi.ui.FloatingTabBar
import cc.hosaka.okonomi.ui.FloatingTabBarDefaults
import cc.hosaka.okonomi.ui.FloatingTabBarItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_back
import okonomi.shared.generated.resources.entry_error
import okonomi.shared.generated.resources.entry_retry
import org.jetbrains.compose.resources.stringResource

/**
 * The entry view: four swipeable tabs under a floating tab bar that
 * owns the bottom edge (the home shell hides its own navigation bar
 * while this screen is pushed). Content-first by design — a back arrow
 * above the headword instead of a toolbar.
 *
 * Loading and failure are screen-level states rather than Word-tab
 * states: an entry that did not load has nothing for any tab to show,
 * and a placeholder promising a future feature would be a strange thing
 * to leave standing next to an error.
 */
@Composable
fun EntryScreen(
    state: EntryState,
    modifier: Modifier = Modifier,
) {
    // Remembered, not rebuilt: this instance is the contentPadding of
    // every tab's lazy list, and handing those a fresh equal-but-new
    // PaddingValues on each recomposition costs a remeasure per frame
    // under scroll.
    val bottomPadding = FloatingTabBarDefaults.contentBottomPadding
    val contentPadding = remember(bottomPadding) { PaddingValues(bottom = bottomPadding) }
    Surface(
        modifier = modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
        ) {
            EntryBackButton()
            when (val content = state.content) {
                EntryContentState.Loading -> CenteredBox(
                    modifier = Modifier
                        .weight(1f),
                    contentPadding = contentPadding,
                ) {
                    CircularProgressIndicator()
                }

                is EntryContentState.Error -> CenteredMessage(
                    text = stringResource(Res.string.entry_error),
                    modifier = Modifier
                        .weight(1f),
                    contentPadding = contentPadding,
                    action = content.onRetry?.let { retry ->
                        {
                            TextButton(onClick = retry) {
                                Text(text = stringResource(Res.string.entry_retry))
                            }
                        }
                    },
                )

                is EntryContentState.Ready -> EntryTabs(
                    entry = content.entry,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EntryBackButton() {
    val navigation = LocalNavigationController.current
    IconButton(
        onClick = {
            navigation.pop()
        },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(Res.string.entry_back),
        )
    }
}

@Composable
private fun EntryTabs(
    entry: EntryDetail,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tabs = EntryTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val labels = tabs.map { tab -> stringResource(tab.label) }
    val tabBarItems = remember(labels) {
        tabs.mapIndexed { index, tab ->
            FloatingTabBarItem(
                label = labels[index],
                icon = tab.icon,
            )
        }
    }
    // One animation at a time: rapid taps must not leave two scrolls
    // fighting over the pager.
    val scrollJob = remember { ScrollJobHolder() }
    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize(),
        ) { page ->
            // Tapping Phrases from Word animates the pager through Kanji
            // and Forms, composing each of them on the way. Gating the
            // tabs that read the database is what keeps that one tap to
            // one query instead of three: an intermediate page renders
            // its spinner and asks the dictionary for nothing. Forms is
            // not gated because it computes its tables from the entry
            // already in hand and never touches the database.
            //
            // Two pages open the gate, and both are needed:
            //
            // - settledPage, the page the pager has come to rest on.
            // - targetPage, the page it intends to settle on. During a
            //   drag this becomes the destination as soon as the gesture
            //   is committed, and during animateScrollToPage it is the
            //   FINAL destination for the whole animation — it does not
            //   sweep through the pages in between the way currentPage
            //   does, which is what keeps the three-tab tap to one query.
            //
            // settledPage alone was the regression: on an ordinary
            // one-tab swipe the bar highlighted the destination at the
            // halfway point (the bar reads currentPage) while its body
            // stayed gated to a spinner until the settle animation
            // finished. The rare tap was fixed at the cost of the common
            // gesture. See EntryTabGestureUiTest, which pins both.
            val loadEnabled = page == pagerState.settledPage || page == pagerState.targetPage
            when (tabs[page]) {
                EntryTab.Word -> WordTab(
                    entry = entry,
                    contentPadding = contentPadding,
                )

                EntryTab.Kanji -> KanjiTab(
                    entry = entry,
                    contentPadding = contentPadding,
                    loadEnabled = loadEnabled,
                )

                EntryTab.Forms -> FormsTab(
                    entry = entry,
                    contentPadding = contentPadding,
                )

                EntryTab.Phrases -> PhrasesTab(
                    entry = entry,
                    contentPadding = contentPadding,
                    loadEnabled = loadEnabled,
                )
            }
        }
        BottomFade()
        FloatingTabBar(
            items = tabBarItems,
            // currentPage, not settledPage. This reverses the earlier
            // decision, which read settledPage so that a half-swipe
            // snapping back could not leave the highlight on a tab the
            // reader never reached. On a device that costs a whole swipe
            // of latency: the segment only lights up after the pager has
            // finished animating, so the bar always looks a beat behind
            // the thumb. currentPage flips at the pager's own ~50%
            // threshold, which is the point the gesture is committed,
            // and a drag released short of it never moves it. Do not
            // change this back without a device in hand.
            selectedIndex = pagerState.currentPage,
            onSelect = { page ->
                scrollJob.job?.cancel()
                scrollJob.job = coroutineScope.launch {
                    pagerState.animateScrollToPage(page)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom),
                ),
        )
    }
}

/** Holds the tab-tap scroll so a newer tap can cancel the older one. */
private class ScrollJobHolder {
    var job: Job? = null
}

/**
 * Fades the content out under the floating bar, so a line scrolling
 * past it reads as passing behind the bar rather than being clipped by
 * it.
 */
@Composable
private fun BoxScope.BottomFade() {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(FloatingTabBarDefaults.contentBottomPadding)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, surface),
                ),
            ),
    )
}
