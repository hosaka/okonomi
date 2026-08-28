package cc.hosaka.okonomi.ui.toolbar.util

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable

object ToolbarBehavior {
    /**
     * [canScroll] is what stops the toolbar collapsing over content that
     * cannot move under it.
     *
     * Material's own default is `{ true }`, which collapses the toolbar
     * on any nested scroll gesture whether or not the content beneath it
     * scrolls. On a screen whose content is shorter than the viewport
     * the two then disagree: the bar shrinks, the content stays where
     * the expanded bar's padding put it, and what is left is a band of
     * empty space between them. Favourites with a single saved word is
     * the case that shows it; a screen that always overflows never does,
     * which is why Settings looks right with the same default.
     *
     * Pass the hosted scrollable's own state — `canScrollForward ||
     * canScrollBackward` — so a list that cannot scroll keeps its
     * toolbar expanded.
     */
    @Composable
    fun behavior(canScroll: () -> Boolean = { true }): TopAppBarScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll = canScroll)
}
