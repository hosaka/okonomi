package cc.hosaka.okonomi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingHalf

/**
 * Minimum side of a single-character target. A chip sized to one glyph
 * is smaller than a fingertip, and these always sit side by side.
 */
val characterChipMinSize: Dp = 48.dp

/**
 * One tappable character: a radical in the kanji detail overlay, a kanji
 * in the radical screen's grid.
 *
 * Shared rather than written twice. The two callers show the same thing
 * — a single character the reader taps to ask the next question about it
 * — and a second copy would drift, most likely by losing the modifier
 * ordering below.
 *
 * [onClickLabel] names the action rather than the character, because it
 * is spoken as an action hint and the chip's only text is the glyph
 * itself: "button, 心" says nothing about what the tap does. Each caller
 * supplies its own, since the two ask different questions of the same
 * kind of character.
 *
 * [onClick] is not nullable, unlike most callbacks in this app. A chip
 * exists to be tapped — an inert one is a glyph, which both callers
 * would draw as text instead — so there is no disabled case to render,
 * and an unreachable branch for one would be a path no test could
 * cover.
 */
@Composable
fun CharacterChip(
    character: String,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Clipped before the gesture, not after: `Surface` clips its own
    // content to the shape but not a modifier applied above it, so a
    // ripple attached outside that clip draws a rectangle over a rounded
    // chip. The same ordering `ListCard` documents, where the enclosing
    // `Surface` is what clips the card's ripple.
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .sizeIn(minWidth = characterChipMinSize, minHeight = characterChipMinSize)
            .clip(shape)
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = Dimens.horizontalPaddingHalf),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = character,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
