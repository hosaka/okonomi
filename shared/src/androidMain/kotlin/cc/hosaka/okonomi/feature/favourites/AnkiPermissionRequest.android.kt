package cc.hosaka.okonomi.feature.favourites

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import cc.hosaka.okonomi.anki.ANKIDROID_PERMISSION

/**
 * Not covered by a host test, for the reason the file dialogs in
 * `FavouritesRoute` are not: it is settled inside a launcher callback.
 * The decisions either side of it — when to ask, and what each answer
 * does — are the producer's, and tested there.
 */
@Composable
internal actual fun AnkiPermissionRequest(onResult: ((Boolean) -> Unit)?) {
    // The answer goes to the handler standing when it comes back, which
    // may belong to a state rebuilt while the dialog stood.
    val currentOnResult by rememberUpdatedState(onResult)
    // Saveable so a recreated activity, whose launcher is handed the
    // pending answer anyway, does not raise a second dialog over it.
    var asking by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asking = false
        currentOnResult?.invoke(granted)
    }
    LaunchedEffect(onResult != null) {
        if (onResult != null && !asking) {
            asking = true
            launcher.launch(ANKIDROID_PERMISSION)
        }
    }
}
