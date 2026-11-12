package cc.hosaka.okonomi

import androidx.compose.ui.window.ComposeUIViewController
import cc.hosaka.okonomi.lang.startWithPhoneLanguage
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    // Before composing: the phone's language has to be read before the
    // reader's chosen UI language is applied over it.
    startWithPhoneLanguage()
    return ComposeUIViewController { App() }
}
