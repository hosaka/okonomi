package cc.hosaka.okonomi.feature.word

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.db.EntryReading
import cc.hosaka.okonomi.db.EntrySense
import cc.hosaka.okonomi.ui.CommonWordChip
import cc.hosaka.okonomi.ui.TagChip
import cc.hosaka.okonomi.ui.furigana.FuriganaText
import cc.hosaka.okonomi.ui.furigana.alignReading
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingFourth
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_alternate_forms
import okonomi.shared.generated.resources.entry_restriction
import okonomi.shared.generated.resources.entry_section_english
import okonomi.shared.generated.resources.entry_section_reading
import org.jetbrains.compose.resources.stringResource

/** The bullet every gloss line hangs from. */
private const val GLOSS_BULLET = "- "

/** Joins the values of a restriction note inside English prose. */
private const val RESTRICTION_JOIN = ", "

/** Wide enough for a two-digit sense number plus its period. */
private val SENSE_NUMBER_WIDTH = 28.dp

/**
 * The word's own reading: the headword with its reading set above it,
 * the readings that one does not cover, and every sense with its
 * labels, glosses and notes.
 */
@Composable
fun WordTab(
    entry: EntryDetail,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "headword") {
            Headword(entry)
        }
        val otherReadings = entry.otherReadings
        if (otherReadings.isNotEmpty()) {
            item(key = "reading-header") {
                SectionHeader(stringResource(Res.string.entry_section_reading))
            }
            itemsIndexed(
                items = otherReadings,
                key = { index, _ -> "reading-$index" },
            ) { _, reading ->
                ReadingRow(reading)
            }
        }
        if (entry.senses.isNotEmpty()) {
            item(key = "english-header") {
                SectionHeader(stringResource(Res.string.entry_section_english))
            }
            itemsIndexed(
                items = entry.senses,
                key = { index, _ -> "sense-$index" },
            ) { index, sense ->
                SenseBlock(
                    sense = sense,
                    number = index + 1,
                    // The entry-level "common word" chip rides along with
                    // the first sense's chips rather than repeating on
                    // every block.
                    showCommonChip = index == 0 && entry.isCommon,
                )
            }
        }
    }
}

@Composable
private fun Headword(entry: EntryDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
    ) {
        val reading = entry.headwordReading?.text
        FuriganaText(
            segments = remember(entry.headword, reading) {
                alignReading(entry.headword, reading ?: entry.headword)
            },
            style = MaterialTheme.typography.displayMedium,
        )
        val alternates = entry.alternateForms
        if (alternates.isNotEmpty()) {
            // Rarer spellings of the same word: the reader needs to
            // recognize them, not to read them as separate entries.
            NoteText(
                stringResource(
                    Res.string.entry_alternate_forms,
                    alternates.joinToString("、") { it.text },
                ),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .padding(
                start = Dimens.contentPadding,
                end = Dimens.contentPadding,
                top = Dimens.verticalPadding,
                bottom = Dimens.verticalPaddingHalf,
            ),
    )
}

@Composable
private fun ReadingRow(reading: EntryReading) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
    ) {
        Text(
            text = reading.text,
            style = MaterialTheme.typography.titleMedium,
        )
        RestrictionNote(reading.restrictions)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenseBlock(
    sense: EntrySense,
    number: Int,
    showCommonChip: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
    ) {
        // Numbering is the only boundary between senses; without it a
        // long entry reads as one undivided list of glosses.
        Text(
            text = "$number.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(SENSE_NUMBER_WIDTH),
        )
        Column(
            modifier = Modifier
                .weight(1f),
        ) {
            if (sense.tags.isNotEmpty() || showCommonChip) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
                    verticalArrangement = Arrangement.spacedBy(Dimens.horizontalPaddingFourth),
                    modifier = Modifier
                        .padding(bottom = Dimens.horizontalPaddingFourth),
                ) {
                    if (showCommonChip) {
                        CommonWordChip()
                    }
                    sense.tags.forEach { tag ->
                        // Lowercase is this screen's presentation choice;
                        // the database keeps the source's own casing.
                        TagChip(text = tag.lowercase())
                    }
                }
            }
            sense.glosses.forEach { gloss ->
                GlossLine(gloss)
            }
            sense.info?.takeIf { it.isNotBlank() }?.let { info ->
                NoteText(info)
            }
            RestrictionNote(sense.restrictions)
        }
    }
}

/** A gloss behind its bullet, with continuation lines hanging under the text. */
@Composable
private fun GlossLine(gloss: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Text(
            text = GLOSS_BULLET,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = gloss,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f),
        )
    }
}

@Composable
private fun RestrictionNote(restrictions: List<String>) {
    if (restrictions.isEmpty()) return
    NoteText(
        stringResource(
            Res.string.entry_restriction,
            restrictions.joinToString(RESTRICTION_JOIN),
        ),
    )
}

@Composable
private fun NoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
