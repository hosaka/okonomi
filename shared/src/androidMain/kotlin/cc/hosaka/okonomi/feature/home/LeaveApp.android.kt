package cc.hosaka.okonomi.feature.home

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * `moveTaskToBack` rather than `finish`: the process and its state stay,
 * and the task's root activity is not destroyed. Its effect — which app
 * comes to the front — is the system's, and not observable in a host
 * test; `HomeEntryLinkUiTest` asserts when this is called instead.
 */
@Composable
internal actual fun rememberLeaveApp(): () -> Unit {
    val activity = LocalActivity.current
    return remember(activity) { { activity?.moveTaskToBack(true) } }
}
