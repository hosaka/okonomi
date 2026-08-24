package cc.hosaka.okonomi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingFourth
import cc.hosaka.okonomi.ui.theme.horizontalPaddingHalf

/**
 * One segment of a [FloatingTabBar]. [label] is already-resolved text,
 * so the bar does not care where its captions come from.
 */
data class FloatingTabBarItem(
    val label: String,
    val icon: ImageVector,
)

object FloatingTabBarDefaults {
    /** Gap between the pill and the window edge it floats above. */
    val inset: Dp
        @Composable
        get() = Dimens.contentPadding

    /** Tall enough to hold a 48dp touch target plus the pill's rim. */
    val height: Dp = 56.dp

    /** The smallest a segment may be, per the Material touch-target rule. */
    val minTouchTarget: Dp = 48.dp

    private val iconSize: Dp = 18.dp

    /**
     * Enough translucency for scrolling content to stay perceptible
     * under the pill without the labels losing contrast.
     */
    private const val CONTAINER_ALPHA = 0.94f

    /**
     * Bottom content padding a scrolling tab needs so its last row can
     * be read from under the floating pill.
     */
    val contentBottomPadding: Dp
        @Composable
        get() = height + inset * 2 + bottomInset

    private val bottomInset: Dp
        @Composable
        get() = WindowInsets.systemBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    internal val containerColor: Color
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = CONTAINER_ALPHA)

    internal val segmentIconSize: Dp
        get() = iconSize
}

/**
 * A pill that floats above the bottom edge and switches between a
 * handful of tabs. The active segment is filled with the primary
 * container color and shows its label; the others stay icon-compact, so
 * four tabs fit across a narrow phone without truncation.
 *
 * The bar only reports taps: the caller owns the selection, which is
 * what keeps it in sync with a pager the user can also swipe.
 */
@Composable
fun FloatingTabBar(
    items: List<FloatingTabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(FloatingTabBarDefaults.height),
        shape = CircleShape,
        color = FloatingTabBarDefaults.containerColor,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = Dimens.horizontalPaddingFourth / 2,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = Dimens.horizontalPaddingFourth),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth / 2),
        ) {
            items.forEachIndexed { index, item ->
                FloatingTabBarSegment(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = {
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun FloatingTabBarSegment(
    item: FloatingTabBarItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        label = "floating tab container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "floating tab content",
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            // The pill is only as tall as its rim allows, so the segment
            // takes the rest: a 48dp target rather than the icon's 18dp.
            .fillMaxHeight()
            .defaultMinSize(minWidth = FloatingTabBarDefaults.minTouchTarget)
            // One node per tab, announced as a tab with its selected
            // state, instead of four unrelated buttons.
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = item.label
            },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Dimens.horizontalPaddingHalf),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                // The segment as a whole carries the label.
                contentDescription = null,
                modifier = Modifier
                    .size(FloatingTabBarDefaults.segmentIconSize),
            )
            AnimatedVisibility(visible = selected) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = Dimens.horizontalPaddingFourth),
                )
            }
        }
    }
}
