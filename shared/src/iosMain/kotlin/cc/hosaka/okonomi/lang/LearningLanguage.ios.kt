package cc.hosaka.okonomi.lang

/**
 * The first of the phone's languages as recorded before any override
 * was applied (see [phoneLanguages]); empty if the phone lists none.
 */
internal actual fun currentDeviceLanguage(): String = phoneLanguages().firstOrNull()?.let(::languageOf).orEmpty()
