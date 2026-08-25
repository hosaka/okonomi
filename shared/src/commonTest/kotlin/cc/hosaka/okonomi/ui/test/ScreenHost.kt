package cc.hosaka.okonomi.ui.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import cc.hosaka.okonomi.feature.navigation.LocalNavigationController
import cc.hosaka.okonomi.feature.navigation.NavigationController

/**
 * Provides what a screen needs from its host: `LocalNavigationController` is a
 * `staticCompositionLocalOf` that throws when absent, and anything reaching
 * `produceScreenState` resolves a `ViewModel` and so needs a store owner too.
 */
@Composable
internal fun ScreenHost(
    navigation: NavigationController = RecordingNavigationController(),
    content: @Composable () -> Unit,
) {
    val storeOwner = remember { TestViewModelStoreOwner() }
    CompositionLocalProvider(
        LocalNavigationController provides navigation,
        LocalViewModelStoreOwner provides storeOwner,
    ) {
        content()
    }
}

private class TestViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
