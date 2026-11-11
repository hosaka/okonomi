package cc.hosaka.okonomi.lang

import cc.hosaka.okonomi.prefs.FakePreferenceStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * The setting's read rules: the device decides for a reader who never
 * chose, a value this version cannot read counts as no choice, and
 * nothing about reading it ever writes it.
 */
class LearningLanguageTest {

    @Test
    fun `a japanese device is assumed to be learning english`() {
        assertEquals(LearningLanguage.English, defaultLearningLanguage("ja"))
    }

    @Test
    fun `any other device is assumed to be learning japanese`() {
        assertEquals(LearningLanguage.Japanese, defaultLearningLanguage("en"))
        assertEquals(LearningLanguage.Japanese, defaultLearningLanguage("fr"))
    }

    /**
     * Both devices, so the answer cannot be a constant that happens to
     * match one of them; and the writes, so the default cannot have got
     * there by being stored.
     */
    @Test
    fun `nothing stored reads as the device default and is never written`() = runTest {
        val preferences = FakePreferenceStore()

        assertEquals(LearningLanguage.English, preferences.learningLanguageFlow { "ja" }.first())
        assertEquals(LearningLanguage.Japanese, preferences.learningLanguageFlow { "en" }.first())
        assertEquals(emptyList(), preferences.writes)
    }

    @Test
    fun `a value this version does not recognise reads as the device default`() = runTest {
        val preferences = FakePreferenceStore(strings = mapOf(LEARNING_LANGUAGE_PREFERENCE to "xyz"))

        assertEquals(LearningLanguage.English, preferences.learningLanguageFlow { "ja" }.first())
        assertEquals(LearningLanguage.Japanese, preferences.learningLanguageFlow { "en" }.first())
        assertEquals(emptyList(), preferences.writes, "an unreadable value is not repaired by reading it")
    }

    @Test
    fun `a stored choice wins over the device`() = runTest {
        val preferences = FakePreferenceStore()
        preferences.setLearningLanguage(LearningLanguage.Japanese)

        assertEquals(LearningLanguage.Japanese, preferences.learningLanguageFlow { "ja" }.first())

        preferences.setLearningLanguage(LearningLanguage.English)

        assertEquals(LearningLanguage.English, preferences.learningLanguageFlow { "en" }.first())
    }
}
