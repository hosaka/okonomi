package cc.hosaka.okonomi.feature.home.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeSelectionStateTest {
    @Test
    fun `starts on search`() {
        val state = HomeSelectionState(homeNavigationItems)

        assertEquals(homeSearchItem, state.selected)
        assertFalse(state.isBackEnabled)
    }

    @Test
    fun `select switches to settings`() {
        val state = HomeSelectionState(homeNavigationItems)

        state.select(homeSettingsItem)

        assertEquals(homeSettingsItem, state.selected)
        assertTrue(state.isBackEnabled)
    }

    @Test
    fun `back on settings returns to search`() {
        val state = HomeSelectionState(homeNavigationItems)
        state.select(homeSettingsItem)

        val handled = state.onBack()

        assertTrue(handled)
        assertEquals(homeSearchItem, state.selected)
        assertFalse(state.isBackEnabled)
    }

    @Test
    fun `back on search root is left to the platform`() {
        val state = HomeSelectionState(homeNavigationItems)

        val handled = state.onBack()

        assertFalse(handled)
        assertEquals(homeSearchItem, state.selected)
    }

    @Test
    fun `saver restores the selected key`() {
        val saver = HomeSelectionState.saver(homeNavigationItems, homeSearchItem)
        val state = HomeSelectionState(homeNavigationItems)
        state.select(homeSettingsItem)

        val restored = saver.restore(requireNotNull(with(saver) { FakeSaverScope.save(state) }))

        assertEquals(homeSettingsItem, requireNotNull(restored).selected)
    }

    @Test
    fun `unknown selected key falls back to default`() {
        val state = HomeSelectionState(homeNavigationItems, initialKey = "missing")

        assertEquals(homeSearchItem, state.selected)
        assertFalse(state.isBackEnabled)
    }
}

private object FakeSaverScope : androidx.compose.runtime.saveable.SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
}
