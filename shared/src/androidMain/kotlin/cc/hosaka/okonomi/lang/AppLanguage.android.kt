package cc.hosaka.okonomi.lang

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Applies [tag] in the three places Android reads a language from, the
 * approach JetBrains documents for Compose Multiplatform on Android:
 *
 * - the process's default [LocaleList], which Compose resources read:
 *   `stringResource` through `Locale.current`, and the suspend
 *   `getString` through `java.util.Locale.getDefault()`, which
 *   [LocaleList.setDefault] sets along with it;
 * - the configuration of the activity's own resources, which Android
 *   strings are read through, Material's and Compose UI's built-in
 *   labels and accessibility state descriptions among them;
 * - [LocalConfiguration], for whatever below reads the language there.
 *
 * [LocalContext] stays the activity itself rather than a context made
 * for the language, since code below may need the activity.
 *
 * The phone's languages are the system configuration's, which nothing
 * here writes to: the activity's resources and the process default are
 * both overridden, and the activity's may share their configuration
 * with the application's.
 *
 * [LocalConfiguration] is read here so that a configuration change
 * recomposes this and the override goes back over the new
 * configuration. In this app every such change recreates the activity
 * anyway, which applies it afresh.
 */
@Composable
internal actual fun ApplyAppLanguage(tag: String?, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val resources = LocalContext.current.resources
    val locales = appLocales(tag)
    if (LocaleList.getDefault() != locales) {
        LocaleList.setDefault(locales)
    }
    if (resources.configuration.locales != locales) {
        val updated = Configuration(resources.configuration).apply { setLocales(locales) }
        // Deprecated for creating a context per configuration instead,
        // which would replace the activity in LocalContext; see above.
        @Suppress("DEPRECATION")
        resources.updateConfiguration(updated, resources.displayMetrics)
    }
    val localized = remember(configuration, locales) {
        if (configuration.locales == locales) {
            configuration
        } else {
            Configuration(configuration).apply { setLocales(locales) }
        }
    }
    CompositionLocalProvider(LocalConfiguration provides localized, content = content)
}

/** The phone's languages for no [tag]; otherwise [tag] ahead of them. */
private fun appLocales(tag: String?): LocaleList {
    val phone = Resources.getSystem().configuration.locales
    if (tag == null) return phone
    val phoneTags = phone.toLanguageTags().split(',')
    return LocaleList.forLanguageTags(overrideLanguageTags(tag, phoneTags).joinToString(","))
}
