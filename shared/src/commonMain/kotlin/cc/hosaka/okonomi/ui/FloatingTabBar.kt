package cc.hosaka.okonomi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One segment of a [FloatingTabBar]. [label] is already-resolved text,
 * so the bar does not care where its captions come from.
 */
data class FloatingTabBarItem(
    val label: String,
    val icon: ImageVector,
)

object FloatingTabBarDefaults {
    /**
     * A segment is exactly the Material touch target: the bar has no
     * container of its own to add a rim on top of it.
     */
    val height: Dp = 48.dp

    /** Gap between two segments, and between the row and the window edge. */
    val itemSpacing: Dp = 12.dp

    /** Inset of the row from the horizontal window edges. */
    val horizontalPadding: Dp = 16.dp

    /**
     * Past this the segments stop growing and the row centres itself, so
     * a tablet does not stretch four tabs across the whole window.
     */
    val maxWidth: Dp = 448.dp

    /** The width an unselected, icon-only segment holds. */
    val collapsedWidth: Dp = 64.dp

    val shape: Shape = RoundedCornerShape(16.dp)

    private val iconSize: Dp = 24.dp

    /** Gap between a selected segment's icon and its label. */
    private val iconLabelSpacing: Dp = 8.dp

    /** Padding inside a segment, either side of the icon and label. */
    private val segmentHorizontalPadding: Dp = 12.dp

    /**
     * Enough translucency for scrolling content to stay perceptible
     * under an unselected segment. The selected one stays opaque: it
     * carries the only label on the bar and must not have to compete
     * with a line of text passing behind it.
     */
    private const val UNSELECTED_ALPHA = 0.94f

    /**
     * Bottom content padding a scrolling tab needs so its last row can
     * be read from under the floating bar.
     */
    val contentBottomPadding: Dp
        @Composable
        get() = height + itemSpacing * 2 + bottomInset

    private val bottomInset: Dp
        @Composable
        get() = WindowInsets.systemBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    internal val segmentIconSize: Dp
        get() = iconSize

    internal val segmentIconLabelSpacing: Dp
        get() = iconLabelSpacing

    internal val segmentContentPadding: Dp
        get() = segmentHorizontalPadding

    @Composable
    internal fun containerColor(selected: Boolean): Color = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = UNSELECTED_ALPHA)
    }

    @Composable
    internal fun contentColor(selected: Boolean): Color = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * A row of segments floating above the bottom edge, switching between a
 * handful of tabs. The selected segment takes whatever width is left
 * over and shows its label; the others stay icon-only at a fixed width,
 * so four tabs fit across a phone without truncation.
 *
 * Shaped after morphe-manager's settings navigation: no container behind
 * the segments, each one its own [Surface]. A shared container would
 * have to animate its own width against the segments moving inside it,
 * which is what made this bar read as cramped.
 *
 * What actually animates, stated plainly because the code once claimed
 * more: the label fades and expands, and the two colours cross-fade.
 * The segment *widths* jump. The selected segment takes `weight(1f)`
 * and the rest a fixed [FloatingTabBarDefaults.collapsedWidth], both
 * resolved at layout in a single frame, so the moment the selection
 * moves every segment is already at its new width. The row carried a
 * [Modifier.animateContentSize] that could not change this: the row is
 * `fillMaxWidth` inside a fixed-width box, so its own size never varies
 * and the modifier had nothing to absorb. It has been removed rather
 * than replaced — making the widths animate needs the selection driving
 * a per-segment weight animation, which is a real piece of work and not
 * one this bar has been asked for.
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
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = FloatingTabBarDefaults.maxWidth)
                .fillMaxWidth()
                .padding(
                    horizontal = FloatingTabBarDefaults.horizontalPadding,
                    vertical = FloatingTabBarDefaults.itemSpacing,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FloatingTabBarDefaults.itemSpacing),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                FloatingTabBarSegment(
                    item = item,
                    selected = selected,
                    onClick = {
                        onSelect(index)
                    },
                    modifier = if (selected) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.width(FloatingTabBarDefaults.collapsedWidth)
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
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = FloatingTabBarDefaults.containerColor(selected),
        label = "floating tab container",
    )
    val contentColor by animateColorAsState(
        targetValue = FloatingTabBarDefaults.contentColor(selected),
        label = "floating tab content",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(FloatingTabBarDefaults.height)
            // One node per tab, announced as a tab with its selected
            // state, instead of four unrelated buttons.
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = item.label
            },
        shape = FloatingTabBarDefaults.shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FloatingTabBarDefaults.segmentContentPadding),
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
            // The label animates in and out inside a segment whose
            // width has already jumped to its new value; see the class
            // doc for why the widths themselves do not animate.
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(
                        modifier = Modifier
                            .width(FloatingTabBarDefaults.segmentIconLabelSpacing),
                    )
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
