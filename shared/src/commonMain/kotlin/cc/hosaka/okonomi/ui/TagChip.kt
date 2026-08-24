package cc.hosaka.okonomi.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingFourth
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.common_word_badge
import org.jetbrains.compose.resources.stringResource

/**
 * How much weight a [TagChip] carries: [TagChipTone.Neutral] for the
 * many descriptive labels, [TagChipTone.Accent] for the one fact worth
 * spotting from across the row.
 */
enum class TagChipTone {
    Neutral,
    Accent,
}

/** A small tonal label token: sense tags, the common-word badge. */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: TagChipTone = TagChipTone.Neutral,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = when (tone) {
            TagChipTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHighest
            TagChipTone.Accent -> MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = when (tone) {
            TagChipTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
            TagChipTone.Accent -> MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(
                    horizontal = Dimens.horizontalPaddingFourth,
                    vertical = Dimens.horizontalPaddingFourth / 2,
                ),
        )
    }
}

/**
 * The badge marking an entry that JMdict tags as common. One composable
 * and one string, so the search row and the entry view can never drift
 * apart on what "common" looks like or is called.
 */
@Composable
fun CommonWordChip(
    modifier: Modifier = Modifier,
) {
    TagChip(
        text = stringResource(Res.string.common_word_badge),
        modifier = modifier,
        tone = TagChipTone.Accent,
    )
}
