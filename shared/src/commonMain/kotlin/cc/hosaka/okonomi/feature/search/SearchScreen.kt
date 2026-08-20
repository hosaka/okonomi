package cc.hosaka.okonomi.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.hosaka.okonomi.ui.SearchTextField
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.search_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    state: SearchState,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.ime),
                ),
        ) {
            SearchTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.verticalPaddingHalf),
                text = state.query,
                placeholder = stringResource(Res.string.search_placeholder),
                onTextChange = state.onQueryChange,
                onClear = state.onClear,
            )
        }
    }
}
