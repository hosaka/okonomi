package cc.hosaka.okonomi.feature.search

import androidx.compose.runtime.Immutable

@Immutable
data class SearchState(
    val query: String = "",
    val onQueryChange: ((String) -> Unit)? = null,
    val onClear: (() -> Unit)? = null,
)
