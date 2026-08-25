package cc.hosaka.okonomi.ui

import androidx.compose.ui.platform.UriHandler

/**
 * Opens the URL in the browser. The failure is swallowed deliberately:
 * a device without a browser must never crash the screen, and the app
 * has no logging or snackbar infrastructure yet to surface it, so the
 * tap simply has no effect.
 *
 * Lifted out of the settings screen when the credits list needed the
 * same behaviour. One copy rather than two: an attribution link that
 * crashes on one screen and not the other would be the worst of both.
 */
fun UriHandler.openSafely(uri: String) {
    try {
        openUri(uri)
    } catch (e: Exception) {
        // Deliberately swallowed, see above.
    }
}
