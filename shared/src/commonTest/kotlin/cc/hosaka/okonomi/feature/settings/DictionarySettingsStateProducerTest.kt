package cc.hosaka.okonomi.feature.settings

import cc.hosaka.okonomi.feature.navigation.state.FakeScreenStateScope
import cc.hosaka.okonomi.lang.LEARNING_LANGUAGE_PREFERENCE
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.prefs.FakePreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class DictionarySettingsStateProducerTest {

    /**
     * The device default is what is shown, and showing it writes nothing:
     * a reader who never chose keeps following the device.
     */
    @Test
    fun `a reader who never chose is shown the device default and nothing is written`() = runTest {
        val preferences = FakePreferenceStore()

        val onJapaneseDevice = FakeScreenStateScope()
            .dictionarySettingsScreenStateProducer(preferences = preferences, deviceLanguage = { "ja" })
            .first()
        val onEnglishDevice = FakeScreenStateScope()
            .dictionarySettingsScreenStateProducer(preferences = preferences, deviceLanguage = { "en" })
            .first()

        assertEquals(LearningLanguage.English, onJapaneseDevice.learningLanguage)
        assertEquals(LearningLanguage.Japanese, onEnglishDevice.learningLanguage)
        assertEquals(emptyList(), preferences.writes)
    }

    @Test
    fun `a choice writes the key and the state follows the store`() = runTest {
        val preferences = FakePreferenceStore()
        val states = FakeScreenStateScope()
            .dictionarySettingsScreenStateProducer(preferences = preferences, deviceLanguage = { "en" })

        val onChange = assertNotNull(states.first().onLearningLanguageChange)
        onChange(LearningLanguage.English)

        assertEquals(listOf<Pair<String, Any>>(LEARNING_LANGUAGE_PREFERENCE to "english"), preferences.writes)
        assertEquals(LearningLanguage.English, states.first().learningLanguage)
    }
}
