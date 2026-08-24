package cc.hosaka.okonomi.feature.kanji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.ui.TagChip
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingFourth
import cc.hosaka.okonomi.ui.theme.horizontalPaddingHalf
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_kanji_frequency
import okonomi.shared.generated.resources.entry_kanji_grade
import okonomi.shared.generated.resources.entry_kanji_jlpt
import okonomi.shared.generated.resources.entry_kanji_no_data
import okonomi.shared.generated.resources.entry_kanji_section_kun
import okonomi.shared.generated.resources.entry_kanji_section_meanings
import okonomi.shared.generated.resources.entry_kanji_section_name
import okonomi.shared.generated.resources.entry_kanji_section_on
import okonomi.shared.generated.resources.entry_kanji_section_radical
import okonomi.shared.generated.resources.entry_kanji_stroke_order
import okonomi.shared.generated.resources.entry_kanji_strokes
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Side of the square the KanjiVG stroke-order diagram will occupy. It is
 * the diagram's own size, independent of the column that holds it.
 */
private val STROKE_ORDER_SIZE = 88.dp

/**
 * Floor for the leading column, not a fixed width: the literal is set at
 * display scale, so the column has to be free to grow with the reader's
 * font scale rather than clip it.
 */
private val GLYPH_COLUMN_MIN_WIDTH = 88.dp

private val PLACEHOLDER_BORDER = 1.dp

private val PLACEHOLDER_CORNER = 12.dp

/** Dash and gap of the stroke-order slot's outline. */
private val PLACEHOLDER_DASH = 6.dp

/** Joins the values of one reading or meaning line. */
private const val READING_JOIN = "・"

private const val MEANING_JOIN = ", "

private const val RADICAL_JOIN = " "

/**
 * One character of the headword: its literal and stroke-order slot on
 * the leading edge, and beside them its readings, meanings and
 * radicals, with the metadata chips closing the card.
 */
@Composable
fun KanjiCard(
    character: KanjiCharacter,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .padding(Dimens.contentPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingHalf),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = GLYPH_COLUMN_MIN_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.verticalPaddingHalf),
            ) {
                Text(
                    text = character.literal,
                    style = MaterialTheme.typography.displayMedium,
                )
                StrokeOrderPlaceholder()
            }
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.verticalPaddingHalf),
            ) {
                if (!character.hasData) {
                    // A character kanjidic does not carry is a real state
                    // of the shipped data: say so rather than leaving the
                    // card looking half-rendered.
                    Text(
                        text = stringResource(Res.string.entry_kanji_no_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LabelledLine(
                    label = stringResource(Res.string.entry_kanji_section_on),
                    values = character.onReadings,
                    join = READING_JOIN,
                )
                LabelledLine(
                    label = stringResource(Res.string.entry_kanji_section_kun),
                    values = character.kunReadings,
                    join = READING_JOIN,
                )
                LabelledLine(
                    label = stringResource(Res.string.entry_kanji_section_name),
                    values = character.nameReadings,
                    join = READING_JOIN,
                )
                LabelledLine(
                    label = stringResource(Res.string.entry_kanji_section_meanings),
                    values = character.meanings,
                    join = MEANING_JOIN,
                )
                LabelledLine(
                    label = stringResource(Res.string.entry_kanji_section_radical),
                    values = character.radicals,
                    join = RADICAL_JOIN,
                )
                // Secondary to the readings and meanings above, so the
                // chips close the card rather than heading it.
                MetadataChips(character)
            }
        }
    }
}

/**
 * The square the KanjiVG stroke-order diagram will occupy, drawn at its
 * final size now so importing the diagrams later is a drop-in rather
 * than a re-layout of every card. The dashed outline is what marks it as
 * a slot waiting to be filled; a filled blank would read as a diagram
 * that failed to load.
 */
@Composable
private fun StrokeOrderPlaceholder() {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val borderPx = with(density) { PLACEHOLDER_BORDER.toPx() }
    val cornerPx = with(density) { PLACEHOLDER_CORNER.toPx() }
    val dashPx = with(density) { PLACEHOLDER_DASH.toPx() }
    Box(
        modifier = Modifier
            .size(STROKE_ORDER_SIZE)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(
                        width = borderPx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx)),
                    ),
                )
            }
            .padding(Dimens.horizontalPaddingFourth),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.entry_kanji_stroke_order),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Stroke count whenever the character has kanjidic data; grade, JLPT
 * level and frequency rank only when the source states them, which most
 * characters do not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataChips(character: KanjiCharacter) {
    val strokeCount = character.strokeCount ?: return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
        verticalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
    ) {
        MetadataChip(
            pluralStringResource(
                Res.plurals.entry_kanji_strokes,
                strokeCount.toInt(),
                strokeCount.toInt(),
            ),
        )
        character.grade?.let { grade ->
            MetadataChip(stringResource(Res.string.entry_kanji_grade, grade.toInt()))
        }
        character.jlpt?.let { jlpt ->
            // kanjidic2's former four-level scale, shown as stored:
            // Alex's call, and the reason no N-level appears anywhere
            // near it. See the note in strings.xml.
            MetadataChip(stringResource(Res.string.entry_kanji_jlpt, jlpt.toInt()))
        }
        character.freq?.let { freq ->
            MetadataChip(stringResource(Res.string.entry_kanji_frequency, freq.toInt()))
        }
    }
}

@Composable
private fun MetadataChip(text: String) {
    // Lowercase is this screen's presentation choice, the same rule the
    // Word tab applies to its sense tags; the strings keep their
    // natural casing.
    TagChip(text = text.lowercase())
}

/** A labelled line, omitted entirely when the source states nothing. */
@Composable
private fun LabelledLine(
    label: String,
    values: List<String>,
    join: String,
) {
    if (values.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            // Readings keep the okurigana dot kanjidic writes them with
            // (く.う), which marks where the character ends in the word.
            text = values.joinToString(join),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
