package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable

/**
 * Asks the reader for AnkiDroid access whenever [onResult] is non-null,
 * and hands their answer to it. Remembered at the route's root, for the
 * reason the file dialogs are: the system permission dialog is another
 * activity and must not be tied to the menu that led to it.
 *
 * A no-op on iOS, where nothing ever asks.
 */
@Composable
internal expect fun AnkiPermissionRequest(onResult: ((Boolean) -> Unit)?)
