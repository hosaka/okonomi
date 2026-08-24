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

        state.switchTo(homeSettingsItem)

        assertEquals(homeSettingsItem, state.selected)
        assertTrue(state.isBackEnabled)
    }

    @Test
    fun `back on settings returns to search`() {
        val state = HomeSelectionState(homeNavigationItems)
        state.switchTo(homeSettingsItem)

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
    fun `reselecting the active section increments its counter and keeps the selection`() {
        val state = HomeSelectionState(homeNavigationItems)
        assertEquals(0, state.reselectionsOf(homeSearchItem.key))

        state.signalReselect(homeSearchItem)
        state.signalReselect(homeSearchItem)

        assertEquals(2, state.reselectionsOf(homeSearchItem.key))
        assertEquals(homeSearchItem, state.selected)
        assertEquals(0, state.reselectionsOf(homeSettingsItem.key))
    }

    @Test
    fun `switching sections is not a reselect`() {
        val state = HomeSelectionState(homeNavigationItems)

        state.switchTo(homeSettingsItem)

        assertEquals(0, state.reselectionsOf(homeSettingsItem.key))
        assertEquals(0, state.reselectionsOf(homeSearchItem.key))

        state.signalReselect(homeSettingsItem)
        assertEquals(1, state.reselectionsOf(homeSettingsItem.key))

        state.switchTo(homeSearchItem)
        assertEquals(0, state.reselectionsOf(homeSearchItem.key))
        assertEquals(1, state.reselectionsOf(homeSettingsItem.key))
    }

    @Test
    fun `tapping another section switches to it`() {
        assertEquals(
            HomeSelectAction.Switch,
            homeSelectAction(tappedKey = homeSettingsItem.key, selectedKey = homeSearchItem.key, depth = 1),
        )
        assertEquals(
            HomeSelectAction.Switch,
            homeSelectAction(tappedKey = homeSettingsItem.key, selectedKey = homeSearchItem.key, depth = 3),
        )
    }

    @Test
    fun `a section showing only its root has nothing to pop`() {
        assertTrue(isAtRoot(0))
        assertTrue(isAtRoot(1))
        assertFalse(isAtRoot(2))
        assertEquals(0, popToRootCount(0))
        assertEquals(0, popToRootCount(1))
        // The obvious "pop once" shape would leave the user mid-stack.
        assertEquals(1, popToRootCount(2))
        assertEquals(3, popToRootCount(4))
    }

    @Test
    fun `tapping the active section pops to its root before signalling a reselect`() {
        // An entry screen is pushed: the tap goes back to the section
        // root and is consumed there, no focus on that tap.
        assertEquals(
            HomeSelectAction.PopToRoot,
            homeSelectAction(tappedKey = homeSearchItem.key, selectedKey = homeSearchItem.key, depth = 3),
        )
        // Tapping again, now at the root, focuses the search field.
        assertEquals(
            HomeSelectAction.SignalReselect,
            homeSelectAction(tappedKey = homeSearchItem.key, selectedKey = homeSearchItem.key, depth = 1),
        )
    }

    @Test
    fun `entering composition syncs the handled counter without focusing`() {
        // A tap that uncovers the screen pops instead of incrementing,
        // so re-entering composition never has a reselect to replay.
        assertEquals(HomeReselectDecision(handled = 3, focus = false), resolveHomeReselect(current = 3, handled = null))
        assertEquals(HomeReselectDecision(handled = 0, focus = false), resolveHomeReselect(current = 0, handled = null))
    }

    @Test
    fun `an increment observed while composed focuses`() {
        assertEquals(HomeReselectDecision(handled = 1, focus = true), resolveHomeReselect(current = 1, handled = 0))
        assertEquals(HomeReselectDecision(handled = 5, focus = true), resolveHomeReselect(current = 5, handled = 2))
    }

    @Test
    fun `an unchanged counter does nothing`() {
        assertEquals(HomeReselectDecision(handled = 2, focus = false), resolveHomeReselect(current = 2, handled = 2))
    }

    @Test
    fun `a counter behind the handled value resyncs without focusing`() {
        // Process-death restore resets the counter to zero while a
        // persisted handled value could be higher; never focus then.
        assertEquals(HomeReselectDecision(handled = 0, focus = false), resolveHomeReselect(current = 0, handled = 4))
    }

    @Test
    fun `saver restores the selected key`() {
        val saver = HomeSelectionState.saver(homeNavigationItems, homeSearchItem)
        val state = HomeSelectionState(homeNavigationItems)
        state.switchTo(homeSettingsItem)

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
