package cc.hosaka.okonomi.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.ui.theme.Dimens

val screenMaxWidth = 768.dp

/**
 * A [Scaffold] that hosts a lazily laid out list. The scaffold's inner
 * padding (top bar, window insets) is forwarded to the list as content
 * padding, so the items scroll under the toolbar.
 */
@Composable
fun ScaffoldLazyColumn(
    modifier: Modifier = Modifier,
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    listVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    listState: LazyListState = rememberLazyListState(),
    listContent: LazyListScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentWindowInsets),
            verticalArrangement = listVerticalArrangement,
            contentPadding = contentPadding.plusScreenPadding(),
            state = listState,
        ) {
            listContent()
        }
    }
}

/**
 * A [Scaffold] that hosts a scrollable column. The scaffold's inner
 * padding (top bar, window insets) is applied around the column.
 */
@Composable
fun ScaffoldColumn(
    modifier: Modifier = Modifier,
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    columnModifier: Modifier = Modifier,
    columnVerticalArrangement: Arrangement.Vertical = Arrangement.Top,
    columnHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    columnScrollState: ScrollState = rememberScrollState(),
    columnContent: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentWindowInsets)
                .verticalScroll(columnScrollState)
                .padding(contentPadding.plusScreenPadding()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = columnModifier
                    .widthIn(max = screenMaxWidth),
                verticalArrangement = columnVerticalArrangement,
                horizontalAlignment = columnHorizontalAlignment,
            ) {
                columnContent()
            }
        }
    }
}

/**
 * Adds the shared vertical screen padding on top of a scaffold's inner
 * padding, so that content using its own scrollable container gets the
 * same spacing as [ScaffoldColumn] and [ScaffoldLazyColumn].
 *
 * Remembered rather than rebuilt: the result is a lazy list's
 * contentPadding, and a fresh equal-but-new instance on each
 * recomposition costs that list a remeasure per frame under scroll.
 */
@Composable
internal fun PaddingValues.plusScreenPadding(): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val screenPadding = Dimens.contentPadding
    return remember(this, layoutDirection, screenPadding) {
        PaddingValues(
            start = calculateStartPadding(layoutDirection),
            top = calculateTopPadding() + screenPadding,
            end = calculateEndPadding(layoutDirection),
            bottom = calculateBottomPadding() + screenPadding,
        )
    }
}
