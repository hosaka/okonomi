package cc.hosaka.okonomi.feature.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.ui.CenteredMessage
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import cc.hosaka.okonomi.ui.furigana.FuriganaText
import cc.hosaka.okonomi.ui.furigana.plainText
import cc.hosaka.okonomi.ui.ListCard
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.horizontalPaddingHalf
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_forms_affirmative
import okonomi.shared.generated.resources.entry_forms_negative
import okonomi.shared.generated.resources.entry_forms_no_form
import okonomi.shared.generated.resources.entry_forms_none
import okonomi.shared.generated.resources.entry_forms_row_description
import okonomi.shared.generated.resources.entry_forms_suru
import org.jetbrains.compose.resources.stringResource

/**
 * Thickness of the rules between cells. Since the table lost its outer
 * outline to the card panel, this is now only ever a grid line.
 */
private val TABLE_BORDER = 1.dp

/**
 * The em dash a row without a negative shows. The volitional is the
 * only such row today; screen readers get [entry_forms_no_form]
 * instead, because a dash announces as nothing at all.
 */
private const val NO_FORM = "—"


/**
 * The forms of the word, one table per conjugable class. Everything the
 * tab shows is computed from the entry the screen already holds, so it
 * draws on first composition — there is nothing here to wait for and
 * nothing that can fail.
 */
@Composable
fun FormsTab(
    entry: EntryDetail,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state = produceFormsTabState(
        entryId = entry.entryId,
        // The surface string carries the okurigana the paradigms
        // inflect, so the headword is what conjugates — the same text
        // the Word tab shows at the top of the entry.
        base = entry.headword,
        // The same paradigms run over the reading are what tell the
        // table which of its stems shift; see [conjugationRows].
        reading = entry.headwordReading?.text,
        posCodes = entry.posCodes,
    )
    FormsTabContent(
        state = state.value,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun FormsTabContent(
    state: FormsTabState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    when (val content = state.content) {
        is FormsTabContentState.NotConjugable -> CenteredMessage(
            text = stringResource(
                if (content.takesSuru) Res.string.entry_forms_suru else Res.string.entry_forms_none,
            ),
            modifier = modifier,
            contentPadding = contentPadding,
        )

        is FormsTabContentState.Ready -> ConjugationTables(
            tables = content.tables,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

@Composable
private fun ConjugationTables(
    tables: List<ConjugationTable>,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // A form is something a learner copies into a note or a search box,
    // and here that is still text SELECTION rather than the long press
    // every list card takes. Deliberate, not an oversight: the useful
    // unit in this table is ONE form - 食べさせられる, not the twelve rows
    // around it - and a long press on the card could only copy the whole
    // table. The cards here take no long press, so nothing competes for
    // the gesture and selection still works. If a long press ever comes
    // to this tab it should copy the row under the finger, which is a
    // different feature from the one the lists have.
    SelectionContainer {
        LazyColumn(
            modifier = modifier
                .fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            tables.forEachIndexed { index, table ->
                item(key = "heading-$index") {
                    // Lowercase is this screen's presentation choice, as
                    // on the Word tab's chips; the database keeps the
                    // source's own casing.
                    TableHeading(table.className.lowercase())
                }
                item(key = "table-$index") {
                    ConjugationGrid(table.rows)
                }
            }
        }
    }
}

@Composable
private fun TableHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .semantics { heading() }
            .padding(
                start = Dimens.contentPadding,
                end = Dimens.contentPadding,
                top = Dimens.verticalPadding,
                bottom = Dimens.verticalPaddingHalf,
            ),
    )
}

@Composable
private fun ConjugationGrid(rows: List<ConjugationRow>) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val noForm = stringResource(Res.string.entry_forms_no_form)
    // The same panel every list row sits in, so a tab of tables and a tab
    // of rows read as one app. It replaces the outline this table used to
    // draw around itself: the panel already separates it from the page,
    // and a border on top of that was the thing making the Forms tab look
    // like a different screen. Zero content padding because the rows band
    // the full width of the panel - that is what the tint is for.
    ListCard(contentPadding = 0.dp) {
        GridRow(
            background = MaterialTheme.colorScheme.surfaceContainerHighest,
            topDivider = null,
        ) {
            HeaderCell(stringResource(Res.string.entry_forms_affirmative))
            VerticalRule(outline)
            HeaderCell(stringResource(Res.string.entry_forms_negative))
        }
        rows.forEach { row ->
            val label = stringResource(row.id.label)
            ConjugationEntry(
                label = label,
                affirmative = row.affirmative,
                negative = row.negative ?: listOf(FuriganaSegment(NO_FORM)),
                outline = outline,
                // Three unrelated strings per row is what a screen
                // reader would otherwise get, with nothing tying a form
                // to its label or its column. One node per row, read as
                // a sentence, is the whole relationship — and that is
                // unchanged by the label moving above the forms: the
                // grouping was always the description's job, never the
                // layout's.
                // A ruby is a reading aid for the eye; a screen reader
                // already gets the word, so the description carries the
                // written form alone.
                description = stringResource(
                    Res.string.entry_forms_row_description,
                    label,
                    row.affirmative.plainText(),
                    row.negative?.plainText() ?: noForm,
                ),
            )
        }
    }
}

/**
 * One conjugation: its name on a band spanning the table, and beneath it
 * the affirmative and negative sharing the width equally.
 *
 * The name used to be a third column, which cost the forms a third of
 * the table to a word like "Causative passive" and left dense kana
 * cramped into what remained. Above them it costs a line of height,
 * which the tab has to spare, and gives each form half the table.
 *
 * The band is tinted rather than the rows being striped alternately.
 * Zebra existed to hold the eye across three columns; with two columns
 * and a named band above each pair, the banding IS the grouping, and
 * stripes on top of it would be a second, competing rhythm.
 *
 * The whole group is one semantics node — the label is not separately
 * focusable — so a screen reader still hears "Non-past, 食べる,
 * 食べない" rather than a loose label followed by two orphan forms.
 */
@Composable
private fun ConjugationEntry(
    label: String,
    affirmative: List<FuriganaSegment>,
    negative: List<FuriganaSegment>,
    outline: Color,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        HorizontalRule(outline)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .cellPadding(),
        )
        HorizontalRule(outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Lets the column rule run the full height of whichever
                // form wrapped furthest.
                .height(IntrinsicSize.Min),
        ) {
            ValueCell(affirmative)
            VerticalRule(outline)
            ValueCell(negative)
        }
    }
}

/**
 * One row of the grid, under the rule that separates it from the row
 * above. The rule goes on top rather than underneath so the last row
 * does not double up against the table's own border.
 * [IntrinsicSize.Min] lets the column rule run the full height of
 * whichever cell wrapped furthest.
 */
@Composable
private fun GridRow(
    background: Color,
    topDivider: Color?,
    description: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Column {
        if (topDivider != null) {
            HorizontalRule(topDivider)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .height(IntrinsicSize.Min)
                .then(
                    if (description == null) {
                        Modifier
                    } else {
                        Modifier.clearAndSetSemantics { contentDescription = description }
                    },
                ),
            content = content,
        )
    }
}

@Composable
private fun HorizontalRule(color: Color) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(TABLE_BORDER)
            .background(color),
    )
}

@Composable
private fun VerticalRule(color: Color) {
    Spacer(
        modifier = Modifier
            .fillMaxHeight()
            .width(TABLE_BORDER)
            .background(color),
    )
}



@Composable
private fun RowScope.HeaderCell(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .weight(1f)
            .cellPadding(),
    )
}

@Composable
private fun RowScope.ValueCell(segments: List<FuriganaSegment>) {
    FuriganaText(
        segments = segments,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .weight(1f)
            .cellPadding(),
    )
}

@Composable
private fun Modifier.cellPadding(): Modifier = padding(
    horizontal = Dimens.horizontalPaddingHalf,
    vertical = Dimens.verticalPaddingHalf,
)
