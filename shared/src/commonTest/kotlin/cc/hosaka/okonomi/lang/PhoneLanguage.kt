package cc.hosaka.okonomi.lang

/**
 * Records the process language as the phone has it and returns what puts
 * it back: the default `LocaleList` on Android, the app-domain
 * `AppleLanguages` on iOS. For tests that apply a UI language, which is
 * process-wide and would otherwise carry into every test after them.
 */
internal expect fun snapshotPhoneLanguage(): () -> Unit
