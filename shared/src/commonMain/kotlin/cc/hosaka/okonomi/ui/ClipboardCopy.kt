package cc.hosaka.okonomi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

/**
 * Puts plain text on the system clipboard.
 *
 * Deliberately built on `LocalClipboardManager`, which Compose marks
 * deprecated in favour of `LocalClipboard`. The replacement cannot be
 * used from common code at Compose Multiplatform 1.11.1: its
 * `setClipEntry` takes a `ClipEntry`, which is an `expect class` with no
 * common way to build one from a string, and the only thing that reads
 * one back — `ClipEntry.readPlainText()` — is `internal expect`. So the
 * modern API is reachable only from `androidMain`/`iosMain`, and taking
 * it would mean an `expect`/`actual` pair to copy a string.
 *
 * Verified against the resolved `org.jetbrains.compose.ui:ui:1.11.1`
 * commonMain klib metadata rather than assumed. Revisit when Compose
 * ships a common `ClipEntry` factory; this file is the only seam that
 * would change.
 *
 * There is no in-app confirmation on purpose. Android shows its own
 * clipboard notice, and every caller reaches this through a long press,
 * which `combinedClickable` already answers with haptic feedback. iOS
 * shows nothing, and that is a known gap rather than an oversight.
 */
@Composable
fun rememberClipboardCopy(): (String) -> Unit {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    return remember(clipboard) {
        { text ->
            @Suppress("DEPRECATION")
            clipboard.setText(AnnotatedString(text))
        }
    }
}
