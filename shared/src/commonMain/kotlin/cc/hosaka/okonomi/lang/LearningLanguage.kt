package cc.hosaka.okonomi.lang

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import cc.hosaka.okonomi.prefs.PreferenceStore
import cc.hosaka.okonomi.ui.furigana.LocalFuriganaShown
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The language the reader is learning, which decides how a word is
 * presented to them: one dictionary, two readers.
 *
 * [Japanese] is the reader the app was built for, an English speaker
 * learning Japanese: a word is titled by its Japanese, with furigana
 * over the kanji. [English] is a Japanese speaker learning English: a
 * word is titled by an English gloss with the Japanese beneath it, and
 * no furigana anywhere, because this reader reads kanji already.
 *
 * [storedValue] is what the setting is persisted as, spelled out rather
 * than taken from the constant's name so that renaming a constant can
 * never quietly reset every reader's choice.
 */
enum class LearningLanguage(internal val storedValue: String) {
    Japanese("japanese"),
    English("english"),
    ;

    /** Whether furigana is drawn while this is the language being learned. */
    val showsFurigana: Boolean
        get() = this == Japanese

    internal companion object {
        /** The language [stored] names, or null for anything this version does not recognise. */
        fun fromStored(stored: String): LearningLanguage? = entries.firstOrNull { it.storedValue == stored }
    }
}

/** Persisted key of Settings → Dictionary's "Learning" choice. */
internal const val LEARNING_LANGUAGE_PREFERENCE = "dictionary.learning_language"

/**
 * What [stringFlow][PreferenceStore.stringFlow] stands in for a key never
 * written: no language at all, so it parses as unrecognised and falls to
 * the device default like any other value this version cannot read.
 */
private const val NOTHING_STORED = ""

/**
 * The language a reader who never chose is assumed to be learning, from
 * the device's language code (`ja` and not `ja-JP`): a device set to
 * Japanese belongs to a Japanese speaker, who is learning English; any
 * other device to someone learning Japanese, which is what the app has
 * always assumed.
 */
fun defaultLearningLanguage(deviceLanguage: String): LearningLanguage =
    if (deviceLanguage.equals("ja", ignoreCase = true)) LearningLanguage.English else LearningLanguage.Japanese

/**
 * The phone's own language code, as [defaultLearningLanguage] takes it.
 *
 * Read from the platform's record of the phone's language, never from
 * `Locale.current`: Appearance's "Language" ([AppLanguage]) overrides
 * the process locale, and a reader who only changed the language of the
 * menus must not find the language they are learning changed with it.
 */
internal expect fun currentDeviceLanguage(): String

/**
 * The language the reader is learning: what they chose, or the device
 * default while they have not chosen.
 *
 * The default is **derived, never stored**. It is worked out again from
 * [deviceLanguage] on every read, and nothing here writes it back, so a
 * reader who never chose keeps following the device language — a Japanese
 * speaker who moves their phone to English is not pinned to the language
 * a first launch guessed. A stored value this version does not recognise
 * reads the same way, as unset, rather than as either language by
 * accident.
 */
fun PreferenceStore.learningLanguageFlow(
    deviceLanguage: () -> String = ::currentDeviceLanguage,
): Flow<LearningLanguage> =
    stringFlow(LEARNING_LANGUAGE_PREFERENCE, NOTHING_STORED)
        .map { stored -> LearningLanguage.fromStored(stored) ?: defaultLearningLanguage(deviceLanguage()) }
        .distinctUntilChanged()

/** Stores [language] as the reader's choice; it comes back through [learningLanguageFlow]. */
fun PreferenceStore.setLearningLanguage(language: LearningLanguage) {
    setString(LEARNING_LANGUAGE_PREFERENCE, language.storedValue)
}

/**
 * The language the reader is learning, for whatever draws a word. Read
 * where a row or a headword decides which language titles it; furigana
 * reads [LocalFuriganaShown] instead, which this sets alongside it.
 *
 * Japanese when nothing provides it, which is what the app drew before
 * the setting existed.
 */
val LocalLearningLanguage = staticCompositionLocalOf { LearningLanguage.Japanese }

/**
 * Provides [LocalLearningLanguage] to [content], following the setting in
 * [preferences], and [LocalFuriganaShown] with it.
 *
 * Until the store answers, the device default is provided. There is no
 * neutral value to hold instead — every word is drawn in one layout or
 * the other — and the default is exactly what an unset store answers, so
 * a reader who never chose sees no change when it does.
 *
 * Static locals: a change recomposes everything under them, which is
 * what a change of the whole presentation should do, and it happens only
 * when the reader flips the setting.
 */
@Composable
fun ProvideLearningLanguage(
    preferences: PreferenceStore,
    deviceLanguage: String = currentDeviceLanguage(),
    content: @Composable () -> Unit,
) {
    val language by remember(preferences, deviceLanguage) {
        preferences.learningLanguageFlow { deviceLanguage }
    }.collectAsState(initial = defaultLearningLanguage(deviceLanguage))
    CompositionLocalProvider(
        LocalLearningLanguage provides language,
        LocalFuriganaShown provides language.showsFurigana,
    ) {
        content()
    }
}
