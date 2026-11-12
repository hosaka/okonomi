package cc.hosaka.okonomi.lang

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import cc.hosaka.okonomi.prefs.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.appearance_language_system
import okonomi.shared.generated.resources.language_name_english
import okonomi.shared.generated.resources.language_name_japanese
import org.jetbrains.compose.resources.StringResource

/**
 * The language okonomi's own text is drawn in, which is Settings →
 * Appearance's "Language" choice: the phone's language ([System]), or
 * one of the translations by name.
 *
 * This list is the menu. A new translation is a `values-xx` folder of
 * its own plus one entry here, and `TranslationCompletenessTest` holds
 * that folder to every string the English one has.
 *
 * It decides nothing but the UI's language. The language the reader is
 * learning is a separate setting ([LearningLanguage]), and its default
 * follows the phone, never this.
 *
 * [tag] is the language the strings are read in, null for the phone's
 * own. It is a bare language code (`ja`, never `ja-JP` or `zh-Hant`):
 * it names the `values-[tag]` folder, and Compose resources look a
 * region or script up under a qualifier of its own. It is also what the
 * choice is stored as: a language code names a language for good, so no
 * constant can be renamed out from under a stored value.
 */
enum class AppLanguage(
    internal val tag: String?,
    /** What the menu calls this choice; each language by its own name. */
    internal val displayName: StringResource,
) {
    System(null, Res.string.appearance_language_system),
    English("en", Res.string.language_name_english),
    Japanese("ja", Res.string.language_name_japanese),
    ;

    internal val storedValue: String
        get() = tag ?: SYSTEM_STORED

    internal companion object {
        /** The choice [stored] names; [System] for anything this version does not recognise. */
        fun fromStored(stored: String): AppLanguage = entries.firstOrNull { it.storedValue == stored } ?: System
    }
}

/** Persisted key of Settings → Appearance's "Language" choice. */
internal const val APP_LANGUAGE_PREFERENCE = "appearance.app_language"

/** What [AppLanguage.System] is stored as, once the reader has picked it. */
private const val SYSTEM_STORED = "system"

/**
 * The reader's choice of UI language. A key never written, and a value
 * this version does not recognise, both read as [AppLanguage.System]:
 * the phone's language is what the app showed before the setting existed.
 */
fun PreferenceStore.appLanguageFlow(): Flow<AppLanguage> =
    stringFlow(APP_LANGUAGE_PREFERENCE, SYSTEM_STORED)
        .map(AppLanguage::fromStored)
        .distinctUntilChanged()

/** Stores [language] as the reader's choice; it comes back through [appLanguageFlow]. */
fun PreferenceStore.setAppLanguage(language: AppLanguage) {
    setString(APP_LANGUAGE_PREFERENCE, language.storedValue)
}

/**
 * Draws [content] in the UI language chosen in [preferences], following
 * the choice live.
 *
 * Nothing is drawn until the language is known. Drawing in the phone's
 * language meanwhile would flash it at a reader who chose another one.
 * Within a process, the language last applied is known already, so it
 * is drawn at once and only a cold start waits for the store: a rotation
 * recreates the activity, and with it this, but must not blank a frame.
 *
 * Compose Multiplatform resources have no per-composition language, so
 * the choice is applied to the whole process ([ApplyAppLanguage]), and
 * then everything below is made to recompose: a `stringResource` reads
 * the process language again only when its call site recomposes, and a
 * call site that skipped would keep the string it had.
 *
 * Recomposed, not rebuilt. Drawing [content] under a `key` of the
 * language would rebuild it, but the shell keeps its back stacks and
 * their saved-state holder above this: the rebuilt screen registers its
 * saved state while the old one's is still registered, and the switch
 * crashes. A static local's new value is what forces the recomposition
 * instead; see [LocalAppliedAppLanguage].
 */
@Composable
fun ProvideAppLanguage(
    preferences: PreferenceStore,
    content: @Composable () -> Unit,
) {
    val language by remember(preferences) {
        preferences.appLanguageFlow()
    }.collectAsState(initial = lastAppliedAppLanguage)
    val chosen = language ?: return
    lastAppliedAppLanguage = chosen
    ApplyAppLanguage(chosen.tag) {
        CompositionLocalProvider(LocalAppliedAppLanguage provides chosen) {
            content()
        }
    }
}

/**
 * The language [ProvideAppLanguage] last applied in this process, which
 * the next one to be composed starts from; null before the first.
 */
private var lastAppliedAppLanguage: AppLanguage? = null

/**
 * Forgets [lastAppliedAppLanguage], as a new process would. For tests,
 * which share one process: each starts from a cold start this way.
 */
internal fun forgetAppliedAppLanguage() {
    lastAppliedAppLanguage = null
}

/**
 * The language [ProvideAppLanguage] last applied. Nothing reads it. It
 * is provided because a static local's new value recomposes everything
 * under its provider without skipping, subcompositions such as a
 * scaffold's content included, and that is how every `stringResource`
 * comes to read the newly applied language while all state is kept.
 */
private val LocalAppliedAppLanguage = staticCompositionLocalOf<AppLanguage?> { null }

/**
 * Makes [tag] the language every string resource is read in, for the
 * whole process, or puts the phone's own languages back when it is null,
 * and draws [content] under it.
 *
 * Applied during composition, before [content] reads any string, and
 * again whenever [ProvideAppLanguage] recomposes, so it does nothing
 * when the language is already in place. Re-applying matters on Android,
 * where the system resets the process language on every configuration
 * change.
 *
 * The phone's other languages stay behind the chosen one
 * ([overrideLanguageTags]). Text that names no language of its own draws
 * kanji in the style of the first CJK language in the list, so a
 * Japanese phone showing English must still list Japanese, or its kanji
 * come out in the Chinese font.
 *
 * It must never move what [currentDeviceLanguage] reports, which the
 * learning language's default is taken from.
 */
@Composable
internal expect fun ApplyAppLanguage(tag: String?, content: @Composable () -> Unit)

/**
 * The language list an override applies: [chosenTag] first, then the
 * phone's own languages ([phoneTags], in order) without repeats.
 *
 * Where the phone lists the chosen language itself, its own tag for it
 * goes first in place of the bare code and is not repeated, so a reader
 * whose phone says `en-GB` keeps British English formats.
 */
internal fun overrideLanguageTags(chosenTag: String, phoneTags: List<String>): List<String> {
    val chosen = phoneTags.firstOrNull { languageOf(it) == chosenTag } ?: chosenTag
    return (listOf(chosen) + phoneTags).filter { it.isNotEmpty() }.distinct()
}

/** The language code of a language tag, as Android and iOS both write them: `ja` of `ja-JP`. */
internal fun languageOf(tag: String): String = tag.substringBefore('-').lowercase()
