package cc.hosaka.okonomi.ui.toolbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.hosaka.okonomi.ui.toolbar.util.ToolbarColors

@Composable
fun LargeToolbar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val containerColor = ToolbarColors.containerColor()
    val scrolledContainerColor = ToolbarColors.scrolledContainerColor(containerColor)
    LargeFlexibleTopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        colors = TopAppBarDefaults.topAppBarColors()
            .copy(
                containerColor = containerColor,
                scrolledContainerColor = scrolledContainerColor,
            ),
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}
