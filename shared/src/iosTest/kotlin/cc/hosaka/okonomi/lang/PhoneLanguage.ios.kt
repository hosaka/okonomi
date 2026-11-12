package cc.hosaka.okonomi.lang

import platform.Foundation.NSUserDefaults

/** The phone's languages are what is left with no app-domain override. */
internal actual fun snapshotPhoneLanguage(): () -> Unit = {
    NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
}
