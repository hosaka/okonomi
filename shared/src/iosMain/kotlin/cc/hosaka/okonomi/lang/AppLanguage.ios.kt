package cc.hosaka.okonomi.lang

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

/** The user default iOS reads an app's preferred languages from. */
private const val APPLE_LANGUAGES = "AppleLanguages"

/** The phone's preferred languages, as first read in this process; see [phoneLanguages]. */
private var recordedPhoneLanguages: List<String>? = null

/**
 * Clears the override a previous launch left behind, then records the
 * phone's languages. `MainViewController` calls this before anything
 * reads the locale.
 *
 * The override lives in the app domain of the user defaults, which
 * persists across launches. Left in place, it would be what the next
 * launch reads as the phone's languages, and the learning language's
 * default would follow the menus after all.
 */
internal fun startWithPhoneLanguage() {
    NSUserDefaults.standardUserDefaults.removeObjectForKey(APPLE_LANGUAGES)
    recordedPhoneLanguages = preferredLanguages()
}

/**
 * The phone's preferred languages, as recorded at launch. Code run
 * without `MainViewController`, tests among it, records them on first
 * use instead, which comes before any override is applied.
 */
internal fun phoneLanguages(): List<String> =
    recordedPhoneLanguages ?: preferredLanguages().also { recordedPhoneLanguages = it }

private fun preferredLanguages(): List<String> = NSLocale.preferredLanguages.filterIsInstance<String>()

/**
 * Sets or removes the app-domain `AppleLanguages`, which
 * `NSLocale.preferredLanguages` reads ahead of the phone's own list, and
 * which both `stringResource` and the suspend `getString` read on iOS.
 * Removing it leaves the phone's list in charge again. This is the
 * approach JetBrains documents for Compose Multiplatform.
 *
 * The app-domain `AppleLanguages` belongs to this setting alone. The iOS
 * app declares no `CFBundleLocalizations`, so iOS offers no per-app
 * language of its own that [startWithPhoneLanguage] could wipe. That
 * would change if localizations were ever declared.
 *
 * Nothing is written when the app domain already holds what would be,
 * since this runs on every recomposition of `ProvideAppLanguage`.
 */
@Composable
internal actual fun ApplyAppLanguage(tag: String?, content: @Composable () -> Unit) {
    val wanted = tag?.let { overrideLanguageTags(it, phoneLanguages()) }
    if (!appDomainHolds(wanted)) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (wanted == null) {
            defaults.removeObjectForKey(APPLE_LANGUAGES)
        } else {
            defaults.setObject(wanted, forKey = APPLE_LANGUAGES)
        }
    }
    content()
}

/**
 * Whether the app domain's `AppleLanguages` is [wanted] already, null
 * meaning absent. False when the domain cannot be named, which is only
 * a process with no bundle identifier: then it is written regardless.
 */
private fun appDomainHolds(wanted: List<String>?): Boolean {
    val bundle = NSBundle.mainBundle.bundleIdentifier ?: return false
    val domain = NSUserDefaults.standardUserDefaults.persistentDomainForName(bundle)
    val stored = (domain?.get(APPLE_LANGUAGES) as? List<*>)?.map { it as? String }
    return stored == wanted
}
