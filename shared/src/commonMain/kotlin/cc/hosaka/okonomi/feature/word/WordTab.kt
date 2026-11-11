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
import cc.hosaka.okonomi.lang.LearningLanguage
import cc.hosaka.okonomi.lang.LocalLearningLanguage
import cc.hosaka.okonomi.ui.CommonWordChip
import cc.hosaka.okonomi.ui.TagChip
import cc.hosaka.okonomi.ui.furigana.FuriganaText
import cc.hosaka.okonomi.ui.furigana.LocalFuriganaShown
import cc.hosaka.okonomi.ui.furigana.LocalKanjiReadings
import cc.hosaka.okonomi.ui.furigana.alignReading
import cc.hosaka.okonomi.ui.furigana.withRoomForRuby
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
 *
 * [titleGloss] is the English the headword is titled by for a reader
 * learning English (see [LocalLearningLanguage]); null, or a reader
 * learning Japanese, titles it by the word itself. A reader learning
 * English is shown no readings at all, so the Reading section is left
 * out for them too.
 */
@Composable
fun WordTab(
    entry: EntryDetail,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    titleGloss: String? = null,
) {
    // Not for a reader learning English: the section lists the readings
    // the headword's ruby does not show, and with no ruby drawn it would
    // show 今日's こんにち while きょう appeared nowhere. No reading
    // anywhere is the rule for this reader.
    val otherReadings = if (LocalLearningLanguage.current == LearningLanguage.English) {
        emptyList()
    } else {
        entry.otherReadings
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "headword") {
            Headword(entry, titleGloss)
        }
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

/**
 * The headword, titled one of two ways.
 *
 * For a reader learning Japanese, the word with its reading over it. For
 * one learning English, the English the reader tapped ([titleGloss]) is
 * the headword, and the word is the smaller line beneath it, written
 * alone: this reader reads the kanji, so no reading is drawn anywhere —
 * not as ruby, and not as a Reading row added to make up for it (Alex,
 * 2026-11-11). An entry with no English to title it by keeps the
 * Japanese headword, without its ruby.
 */
@Composable
private fun Headword(entry: EntryDetail, titleGloss: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.contentPadding,
                vertical = Dimens.verticalPaddingHalf,
            ),
    ) {
        val englishTitle = titleGloss.takeIf { LocalLearningLanguage.current == LearningLanguage.English }
        if (englishTitle != null) {
            Text(
                text = englishTitle,
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = entry.headword,
                style = MaterialTheme.typography.headlineMedium,
            )
        } else {
            JapaneseHeadword(entry)
        }
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
private fun JapaneseHeadword(entry: EntryDetail) {
    val reading = entry.headwordReading?.text
    val kanjiReadings = LocalKanjiReadings.current
    val segments = remember(entry.headword, reading, kanjiReadings) {
        alignReading(entry.headword, reading ?: entry.headword, kanjiReadings)
    }
    val style = MaterialTheme.typography.displayMedium
    FuriganaText(
        segments = segments,
        // displayMedium's own 52sp line box is less than half what a
        // 45sp reading needs above the characters, so the ruby was
        // drawn above the composable — and this is the top row of a
        // LazyColumn, which clips its viewport 8dp higher up. 葡萄棚
        // arrived with the top of its dakuten cut off. Stated rather
        // than measured into a constant: see [withRoomForRuby].
        //
        // Asked for only where a reading is actually drawn, and on
        // the same tests FuriganaText itself branches on. ありがとう
        // and every other kana headword aligns to segments carrying
        // no reading at all, and while furigana is hidden no headword
        // carries one on screen; 43.2sp of room for a reading that
        // will never be drawn is a hole in the page.
        style = if (LocalFuriganaShown.current && segments.any { it.reading != null }) {
            style.withRoomForRuby()
        } else {
            style
        },
    )
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
