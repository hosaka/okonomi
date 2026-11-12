package cc.hosaka.okonomi.lang

import android.os.LocaleList

internal actual fun snapshotPhoneLanguage(): () -> Unit {
    val locales = LocaleList.getDefault()
    return { LocaleList.setDefault(locales) }
}
