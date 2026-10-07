package cc.hosaka.okonomi.feature.home

import androidx.compose.runtime.Composable

/**
 * Sends the app to the background without finishing it, returning the
 * reader to whatever was in front before — the app that sent a link.
 * Android moves the task to the back; iOS has no links and does nothing.
 */
@Composable
internal expect fun rememberLeaveApp(): () -> Unit
