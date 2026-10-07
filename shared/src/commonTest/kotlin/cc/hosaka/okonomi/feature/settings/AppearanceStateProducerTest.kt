package cc.hosaka.okonomi.feature.settings

import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateViewModel
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.ui.coach.COACH_MARKS_ENABLED_PREFERENCE
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceStateProducerTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `hints are on when the key was never written`() = runTest(dispatcher) {
        val state = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = FakePreferenceStore())
            .first()

        assertEquals(true, state.showHints)
    }

    @Test
    fun `the switch reflects a stored false`() = runTest(dispatcher) {
        val preferences = FakePreferenceStore(mapOf(COACH_MARKS_ENABLED_PREFERENCE to false))

        val state = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = preferences)
            .first()

        assertEquals(false, state.showHints)
    }

    @Test
    fun `the setting is unknown until the store answers`() = runTest(dispatcher) {
        val preferences = UnansweredPreferenceStore()
        val viewModel = ScreenStateViewModel(
            initial = AppearanceState(),
        ) {
            appearanceScreenStateProducer(preferences = preferences)
        }

        val collector = launch { viewModel.state.collect {} }
        runCurrent()
        assertNull(viewModel.state.value.showHints)

        preferences.answer(false)
        runCurrent()
        assertEquals(false, viewModel.state.value.showHints)
        collector.cancel()
    }

    @Test
    fun `the callback writes the key and the state follows the store`() = runTest(dispatcher) {
        val preferences = FakePreferenceStore()
        val states = FakeScreenStateScope().appearanceScreenStateProducer(preferences = preferences)

        val onChange = assertNotNull(states.first().onShowHintsChange)
        onChange(false)

        assertEquals(listOf(COACH_MARKS_ENABLED_PREFERENCE to false), preferences.writes)
        assertEquals(false, states.first().showHints)
    }
}

/** A store whose reads emit nothing until [answer] is called. */
private class UnansweredPreferenceStore : PreferenceStore {
    private val stored = MutableStateFlow<Boolean?>(null)

    fun answer(value: Boolean) {
        stored.value = value
    }

    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = stored.filterNotNull()

    override fun setBoolean(key: String, value: Boolean) {
        stored.value = value
    }
}
