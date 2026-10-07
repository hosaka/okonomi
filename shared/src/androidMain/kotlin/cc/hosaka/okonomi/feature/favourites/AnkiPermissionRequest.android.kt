package cc.hosaka.okonomi.feature.favourites

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cc.hosaka.okonomi.anki.ANKIDROID_PERMISSION

/**
 * Not covered by a host test, for the reason the file dialogs in
 * `FavouritesRoute` are not: it is settled inside a launcher callback,
 * and so is the rationale check that tells a permanent refusal apart.
 * The decisions either side of it — when to ask, and what each answer
 * does — are the producer's, and tested there.
 */
@Composable
internal actual fun AnkiPermissionRequest(onResult: ((AnkiPermissionAnswer) -> Unit)?) {
    val activity = LocalActivity.current
    // The answer goes to the handler standing when it comes back, which
    // may belong to a state rebuilt while the dialog stood.
    val currentOnResult by rememberUpdatedState(onResult)
    // Saveable so a recreated activity, whose launcher is handed the
    // pending answer anyway, does not raise a second dialog over it.
    var asking by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asking = false
        val answer = when {
            granted -> AnkiPermissionAnswer.Granted
            // After a refusal Android shows a rationale only while it
            // would still ask; no rationale means it will not ask again.
            activity?.shouldShowRequestPermissionRationale(ANKIDROID_PERMISSION) == true -> AnkiPermissionAnswer.Denied
            else -> AnkiPermissionAnswer.DeniedPermanently
        }
        currentOnResult?.invoke(answer)
    }
    LaunchedEffect(onResult != null) {
        if (onResult == null) {
            // Nothing is waiting on an answer. A flag restored after the
            // process died under the dialog would otherwise stay set —
            // its answer went to nobody — and block every later ask.
            asking = false
        } else if (!asking) {
            asking = true
            launcher.launch(ANKIDROID_PERMISSION)
        }
    }
}

/** The system's page for this app, where its permissions can be changed. */
@Composable
internal actual fun rememberOpenAppSettings(): (() -> Unit)? {
    val activity = LocalActivity.current ?: return null
    return remember(activity) {
        {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
            )
        }
    }
}
