package cc.hosaka.okonomi.feature.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.hosaka.okonomi.db.EntryDetail
import cc.hosaka.okonomi.lang.Form
import cc.hosaka.okonomi.ui.CenteredMessage
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

/** Thin enough to read as a grid line rather than as a box. */
private val TABLE_BORDER = 1.dp

private val TABLE_SHAPE = RoundedCornerShape(12.dp)

/**
 * The em dash a row without a negative shows. The volitional is the
 * only such row today; screen readers get [entry_forms_no_form]
 * instead, because a dash announces as nothing at all.
 */
private const val NO_FORM = "—"

/**
 * "Causative passive" is the longest label and what the leading column
 * has to fit. Sized in multiples of the label's own font size rather
 * than in fixed dp, so the column grows with the reader's text scale
 * instead of truncating a long label into ambiguity.
 */
private const val LABEL_COLUMN_EMS = 8.5f

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
    // A form is something a learner copies into a note or a search box.
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
                    ConjugationGrid(table.forms)
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
private fun ConjugationGrid(forms: List<Form>) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val labelWidth = labelColumnWidth()
    val noForm = stringResource(Res.string.entry_forms_no_form)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Dimens.contentPadding,
                end = Dimens.contentPadding,
                bottom = Dimens.verticalPaddingHalf,
            )
            .clip(TABLE_SHAPE)
            .border(TABLE_BORDER, outline, TABLE_SHAPE),
    ) {
        GridRow(
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
            topDivider = null,
        ) {
            Spacer(modifier = Modifier.width(labelWidth))
            HeaderCell(stringResource(Res.string.entry_forms_affirmative))
            VerticalRule(outline)
            HeaderCell(stringResource(Res.string.entry_forms_negative))
        }
        forms.forEachIndexed { index, form ->
            val label = stringResource(form.id.label)
            GridRow(
                // The alternating tint is what keeps the eye on one row
                // across three columns of dense kana.
                background = if (index % 2 == 0) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                topDivider = outline,
                // Three unrelated strings per row is what a screen
                // reader would otherwise get, with nothing tying a form
                // to its label or its column. One node per row, read as
                // a sentence, is the whole relationship.
                description = stringResource(
                    Res.string.entry_forms_row_description,
                    label,
                    form.affirmative,
                    form.negative ?: noForm,
                ),
            ) {
                LabelCell(text = label, width = labelWidth)
                ValueCell(form.affirmative)
                VerticalRule(outline)
                ValueCell(form.negative ?: NO_FORM)
            }
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
private fun labelColumnWidth(): Dp {
    val fontSize = MaterialTheme.typography.labelMedium.fontSize
    return with(LocalDensity.current) { fontSize.toDp() * LABEL_COLUMN_EMS }
}

@Composable
private fun LabelCell(text: String, width: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .width(width)
            .cellPadding(),
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
private fun RowScope.ValueCell(text: String) {
    Text(
        text = text,
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
