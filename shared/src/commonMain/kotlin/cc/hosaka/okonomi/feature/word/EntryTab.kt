package cc.hosaka.okonomi.feature.word

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import cc.hosaka.okonomi.ui.CenteredBox
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_tab_forms
import okonomi.shared.generated.resources.entry_tab_kanji
import okonomi.shared.generated.resources.entry_tab_phrases
import okonomi.shared.generated.resources.entry_tab_placeholder
import okonomi.shared.generated.resources.entry_tab_word
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The tabs of the entry view, in swipe order. Only [Phrases] is still a
 * placeholder for its own increment.
 */
enum class EntryTab(
    val label: StringResource,
    val icon: ImageVector,
) {
    Word(Res.string.entry_tab_word, Icons.Outlined.Info),
    Kanji(Res.string.entry_tab_kanji, Icons.Outlined.Create),
    Forms(Res.string.entry_tab_forms, Icons.AutoMirrored.Outlined.List),
    Phrases(Res.string.entry_tab_phrases, Icons.Outlined.MailOutline),
}

/** Stand-in body for a tab whose increment has not landed yet. */
@Composable
fun PlaceholderTab(
    tab: EntryTab,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // The same CenteredBox the loading and error bodies use, so swiping
    // between tabs never shifts a centred message up or down.
    CenteredBox(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(tab.label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.entry_tab_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
