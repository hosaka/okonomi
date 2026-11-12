package cc.hosaka.okonomi.feature.settings

import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.feature.navigation.state.ScreenStateViewModel
import cc.hosaka.okonomi.lang.APP_LANGUAGE_PREFERENCE
import cc.hosaka.okonomi.lang.AppLanguage
import cc.hosaka.okonomi.lang.LEARNING_LANGUAGE_PREFERENCE
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.lang.setLearningLanguage
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.ui.coach.COACH_MARKS_ENABLED_PREFERENCE
import cc.hosaka.okonomi.ui.furigana.PER_KANJI_FURIGANA_PREFERENCE
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * A device not set to Japanese, so a store with no learning language in
 * it reads as learning Japanese, with furigana shown — whatever language
 * the machine running the tests is set to.
 */
private val ENGLISH_DEVICE: () -> String = { "en" }

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
            .appearanceScreenStateProducer(preferences = FakePreferenceStore(), deviceLanguage = ENGLISH_DEVICE)
            .first()

        assertEquals(true, state.showHints)
    }

    @Test
    fun `the switch reflects a stored false`() = runTest(dispatcher) {
        val preferences = FakePreferenceStore(mapOf(COACH_MARKS_ENABLED_PREFERENCE to false))

        val state = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = preferences, deviceLanguage = ENGLISH_DEVICE)
            .first()

        assertEquals(false, state.showHints)
    }

    @Test
    fun `the setting is unknown until the store answers`() = runTest(dispatcher) {
        val preferences = UnansweredPreferenceStore()
        val viewModel = ScreenStateViewModel(
            initial = AppearanceState(),
        ) {
            appearanceScreenStateProducer(preferences = preferences, deviceLanguage = ENGLISH_DEVICE)
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
        val states = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = preferences, deviceLanguage = ENGLISH_DEVICE)

        val onChange = assertNotNull(states.first().onShowHintsChange)
        onChange(false)

        assertEquals(listOf<Pair<String, Any>>(COACH_MARKS_ENABLED_PREFERENCE to false), preferences.writes)
        assertEquals(false, states.first().showHints)
    }

    @Test
    fun `per-kanji furigana is on when the key was never written`() = runTest(dispatcher) {
        val state = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = FakePreferenceStore(), deviceLanguage = ENGLISH_DEVICE)
            .first()

        assertEquals(true, state.perKanjiFurigana)
    }

    @Test
    fun `the furigana callback writes its own key and the state follows the store`() = runTest(dispatcher) {
        val preferences = FakePreferenceStore()
        val states = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = preferences, deviceLanguage = ENGLISH_DEVICE)

        val onChange = assertNotNull(states.first().onPerKanjiFuriganaChange)
        onChange(false)

        assertEquals(listOf<Pair<String, Any>>(PER_KANJI_FURIGANA_PREFERENCE to false), preferences.writes)
        val state = states.first()
        assertEquals(false, state.perKanjiFurigana)
        assertEquals(true, state.showHints, "the hints switch is a different setting")
    }

    /**
     * Learning English, nothing has furigana, so the per-kanji switch is
     * shown off and cannot be moved — and the reader's stored "on" is not
     * touched. Forcing it off on disk would be the easy way to show it
     * off, and would lose the choice.
     */
    @Test
    fun `learning english shows the per-kanji switch off and disabled without writing it`() = runTest(dispatcher) {
        val preferences = FakePreferenceStore(
            initial = mapOf(PER_KANJI_FURIGANA_PREFERENCE to true),
            strings = mapOf(LEARNING_LANGUAGE_PREFERENCE to "english"),
        )

        val state = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = preferences, deviceLanguage = ENGLISH_DEVICE)
            .first()

        assertEquals(false, state.perKanjiFurigana)
        assertNull(state.onPerKanjiFuriganaChange, "a switch for furigana that is not drawn cannot be moved")
        assertTrue(state.furiganaHidden)
        assertEquals(emptyList(), preferences.writes)
    }

    /**
     * The other half of the same rule: back to Japanese, the reader's own
     * choice is back. One collection across the change, so a producer that
     * read the language once and kept it would stay hidden here.
     */
    @Test
    fun `learning japanese again restores the stored switch`() = runTest(dispatcher) {
        val preferences = FakePreferenceStore(
            initial = mapOf(PER_KANJI_FURIGANA_PREFERENCE to true),
            strings = mapOf(LEARNING_LANGUAGE_PREFERENCE to "english"),
        )
        val states = mutableListOf<AppearanceState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            FakeScreenStateScope()
                .appearanceScreenStateProducer(preferences = preferences, deviceLanguage = ENGLISH_DEVICE)
                .collect { states += it }
        }
        runCurrent()
        assertTrue(states.last().furiganaHidden)
        assertEquals(false, states.last().perKanjiFurigana)

        preferences.setLearningLanguage(LearningLanguage.Japanese)
        runCurrent()

        val state = states.last()
        assertEquals(true, state.perKanjiFurigana)
        assertNotNull(state.onPerKanjiFuriganaChange)
        assertFalse(state.furiganaHidden)
        assertEquals(
            listOf<Pair<String, Any>>(LEARNING_LANGUAGE_PREFERENCE to "japanese"),
            preferences.writes,
            "only the language was written; the switch never was",
        )
    }

    @Test
    fun `the language callback writes its key and the state follows the store`() = runTest(dispatcher) {
        val preferences = FakePreferenceStore()
        val states = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = preferences, deviceLanguage = ENGLISH_DEVICE)

        val onChange = assertNotNull(states.first().onAppLanguageChange)
        onChange(AppLanguage.Japanese)

        assertEquals(listOf<Pair<String, Any>>(APP_LANGUAGE_PREFERENCE to "ja"), preferences.writes)
        assertEquals(AppLanguage.Japanese, states.first().appLanguage)
    }

    /**
     * One collection across a recognised value and an unrecognised one,
     * so the fallback is seen to come from the stored value rather than
     * from a state that never read it.
     */
    @Test
    fun `an unrecognised stored language reads as system default`() = runTest(dispatcher) {
        val preferences = FakePreferenceStore(strings = mapOf(APP_LANGUAGE_PREFERENCE to "ja"))
        val states = mutableListOf<AppearanceState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            FakeScreenStateScope()
                .appearanceScreenStateProducer(preferences = preferences, deviceLanguage = ENGLISH_DEVICE)
                .collect { states += it }
        }
        runCurrent()
        assertEquals(AppLanguage.Japanese, states.last().appLanguage)

        preferences.setString(APP_LANGUAGE_PREFERENCE, "xx")
        runCurrent()

        assertEquals(AppLanguage.System, states.last().appLanguage)
    }

    /** A reader who never chose, on a Japanese device, is learning English. */
    @Test
    fun `a japanese device with nothing chosen hides furigana`() = runTest(dispatcher) {
        val state = FakeScreenStateScope()
            .appearanceScreenStateProducer(preferences = FakePreferenceStore(), deviceLanguage = { "ja" })
            .first()

        assertTrue(state.furiganaHidden)
        assertEquals(false, state.perKanjiFurigana)
    }
}

/**
 * A store whose Boolean reads emit nothing until [answer] is called. Its
 * strings read as unset at once: what is held back is the switch.
 */
private class UnansweredPreferenceStore : PreferenceStore {
    private val stored = MutableStateFlow<Boolean?>(null)

    fun answer(value: Boolean) {
        stored.value = value
    }

    override fun booleanFlow(key: String, default: Boolean): Flow<Boolean> = stored.filterNotNull()

    override fun setBoolean(key: String, value: Boolean) {
        stored.value = value
    }

    override fun stringFlow(key: String, default: String): Flow<String> = flowOf(default)

    override fun setString(key: String, value: String) = Unit
}
