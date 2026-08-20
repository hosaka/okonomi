package cc.hosaka.okonomi.feature.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

val LocalHomeLayout = staticCompositionLocalOf<HomeLayout> {
    error("Home layout must be initialized!")
}

sealed interface HomeLayout {
    data object Vertical : HomeLayout
    data object Horizontal : HomeLayout
}

fun homeLayoutFor(
    maxWidth: Dp,
    maxHeight: Dp,
): HomeLayout = when {
    maxHeight < maxWidth -> HomeLayout.Horizontal
    else -> HomeLayout.Vertical
}

@Composable
fun ResponsiveLayout(
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) = BoxWithConstraints(
    modifier = Modifier
        .fillMaxSize(),
) {
    val layout = homeLayoutFor(
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )
    CompositionLocalProvider(LocalHomeLayout provides layout) {
        content()
    }
}
