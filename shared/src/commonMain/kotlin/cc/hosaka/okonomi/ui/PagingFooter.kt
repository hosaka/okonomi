package cc.hosaka.okonomi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.paging_more_failed
import okonomi.shared.generated.resources.paging_more_loading
import okonomi.shared.generated.resources.paging_retry
import org.jetbrains.compose.resources.stringResource

/**
 * What sits below the last row of a list that pages.
 *
 * Modelled as its own state rather than inferred from whether a
 * "show more" callback exists: a null callback cannot tell "there is
 * nothing further" apart from "there is something further and it is on
 * its way", and those two have to look different to the reader. A page
 * being fetched used to look exactly like the end of the list, and a
 * page that failed to arrive looked like it too — the reader was told
 * nothing at all.
 *
 * [None] is the state a list with everything already on screen is in,
 * and it renders nothing whatsoever: an entry whose examples all fit
 * must show no affordance, not an idle one.
 */
@Immutable
sealed interface PagingFooterState {
    /** Everything there is to show is on screen. Renders nothing. */
    data object None : PagingFooterState

    /** A further page has been asked for and has not arrived yet. */
    data object Loading : PagingFooterState

    /**
     * A further page failed to arrive. [onRetry] asks for it again;
     * null means retrying is not offered, following the project's
     * "null callback is a disabled action" rule.
     *
     * Scrolling to the end again also retries (see [LoadMoreEffect]) —
     * this is the affordance for a reader already sitting at the end,
     * who has nowhere further to scroll.
     */
    data class Failed(
        val onRetry: (() -> Unit)? = null,
    ) : PagingFooterState
}

/**
 * Adds the footer row to a lazy list, or nothing at all for
 * [PagingFooterState.None].
 *
 * A stable key, so the footer changing state does not make the list
 * treat it as a different item and lose the reader's position.
 */
fun LazyListScope.pagingFooterItem(state: PagingFooterState) {
    if (state == PagingFooterState.None) return
    item(
        key = "paging-footer",
        contentType = "paging-footer",
    ) {
        PagingFooter(state)
    }
}

@Composable
private fun PagingFooter(state: PagingFooterState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            PagingFooterState.None -> Unit

            PagingFooterState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp),
                )
                Text(
                    text = stringResource(Res.string.paging_more_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = Dimens.contentPadding),
                )
            }

            is PagingFooterState.Failed -> {
                Text(
                    text = stringResource(Res.string.paging_more_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val retry = state.onRetry
                if (retry != null) {
                    TextButton(onClick = retry) {
                        Text(text = stringResource(Res.string.paging_retry))
                    }
                }
            }
        }
    }
}
