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
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
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
    val contentPadding = PaddingValues(bottom = FloatingTabBarDefaults.contentBottomPadding)
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
    val tabBarItems = tabs.map { tab ->
        FloatingTabBarItem(
            label = stringResource(tab.label),
            icon = tab.icon,
        )
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
            when (val tab = tabs[page]) {
                EntryTab.Word -> WordTab(
                    entry = entry,
                    contentPadding = contentPadding,
                )

                else -> PlaceholderTab(
                    tab = tab,
                    contentPadding = contentPadding,
                )
            }
        }
        BottomFade()
        FloatingTabBar(
            items = tabBarItems,
            // settledPage, not currentPage: a half-swipe that snaps back
            // must not leave the pill on a tab the reader never reached.
            selectedIndex = pagerState.settledPage,
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
                )
                .padding(FloatingTabBarDefaults.inset),
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
