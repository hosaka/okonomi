package cc.hosaka.okonomi.feature.favourites

import androidx.compose.runtime.Composable

@Composable
internal actual fun AnkiPermissionRequest(onResult: ((AnkiPermissionAnswer) -> Unit)?) = Unit

@Composable
internal actual fun rememberOpenAppSettings(): (() -> Unit)? = null
