package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable

/**
 * Asks the reader for AnkiDroid access whenever [onResult] is non-null,
 * and hands their answer to it — telling a refusal Android would ask
 * again from a permanent one. Remembered at the route's root, for the
 * reason the file dialogs are: the system permission dialog is another
 * activity and must not be tied to the menu that led to it.
 *
 * A no-op on iOS, where nothing ever asks.
 */
@Composable
internal expect fun AnkiPermissionRequest(onResult: ((AnkiPermissionAnswer) -> Unit)?)

/**
 * Opens okonomi's page in the system settings, where a permission
 * refused for good can still be granted; null where there is none (iOS).
 */
@Composable
internal expect fun rememberOpenAppSettings(): (() -> Unit)?
