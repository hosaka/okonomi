package cc.hosaka.okonomi.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter

/**
 * How close to the end of the list the reader has to get before the
 * next page is asked for. Enough rows to cover the time one page takes
 * to arrive, so a steady scroll never runs out of list, and few enough
 * that opening a list does not immediately fetch a page nobody reached.
 */
private const val LOAD_MORE_THRESHOLD = 5

/**
 * Asks for the next page while [listState] is scrolled within a few
 * rows of the end of its content. A null [onLoadMore] means there is no
 * next page, following the project's "null callback is a disabled
 * action" rule.
 *
 * A `snapshotFlow` over the layout rather than a sentinel item at the
 * end of the list. A sentinel has to be composed to fire, which ties
 * "load more" to how tall the rows happen to be, and it fires once per
 * list content — so an extension that failed could never be retried by
 * scrolling to the end again. Reading the layout instead re-asks
 * whenever the reader moves, which is exactly when asking again is
 * wanted.
 *
 * [onLoadMore] is therefore required to be idempotent *in effect*: it is
 * invoked again on every change to the last visible index or the item
 * count while the end is in reach, and two of those landing before the
 * page they asked for does must still add one page, not two. The
 * producers satisfy this by computing the next page boundary from the
 * state the callback was built for rather than by incrementing it, so
 * repeated calls name the same boundary.
 *
 * The one callback that does increment is the search producer's retry
 * after a failed extension: the boundary it wants is the one already
 * asked for, so something else has to change for the flow to emit at
 * all. It is still idempotent in effect — the searches it starts are
 * latest-wins, so repeated calls collapse into one page.
 */
@Composable
fun LoadMoreEffect(
    listState: LazyListState,
    onLoadMore: (() -> Unit)?,
) {
    // Kept out of the effect's keys on purpose: producers hand out a
    // fresh lambda per emission, and restarting the effect for each one
    // would re-read a layout that has not caught up with the page just
    // added and ask for another.
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) to layout.totalItemsCount
        }
            .filter { (lastVisible, total) -> total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD }
            .collect { currentOnLoadMore?.invoke() }
    }
}
