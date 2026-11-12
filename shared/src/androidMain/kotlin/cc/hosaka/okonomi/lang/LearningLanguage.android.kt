package cc.hosaka.okonomi.lang

import android.content.res.Resources

/**
 * The system configuration's first language. Nothing in the app moves
 * it: [ApplyAppLanguage] changes the process's default locale, which is
 * what `Locale.current` reports, and leaves this alone.
 */
internal actual fun currentDeviceLanguage(): String =
    Resources.getSystem().configuration.locales.get(0)?.language.orEmpty()
