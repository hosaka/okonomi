package cc.hosaka.okonomi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import cc.hosaka.okonomi.ui.theme.Dimens

/**
 * Centres a short piece of screen-filling chrome — a spinner, an empty
 * note, an error with its retry — inside the space it is given.
 * [contentPadding] is the host's own padding (a floating bar's reserved
 * strip, for instance) so that centred content lands optically in the
 * middle of what the reader can actually see.
 */
@Composable
fun CenteredBox(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(Dimens.contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * A centred explanatory message with an optional action below it; a
 * null [action] renders the message alone.
 */
@Composable
fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    action: @Composable (() -> Unit)? = null,
) {
    CenteredBox(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.verticalPadding),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            action?.invoke()
        }
    }
}
