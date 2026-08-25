package cc.hosaka.okonomi.feature.forms

import cc.hosaka.okonomi.lang.FormId
import kotlin.test.Test
import kotlin.test.assertEquals
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.entry_forms_row_causative
import okonomi.shared.generated.resources.entry_forms_row_causative_passive
import okonomi.shared.generated.resources.entry_forms_row_conditional_ba
import okonomi.shared.generated.resources.entry_forms_row_conditional_tara
import okonomi.shared.generated.resources.entry_forms_row_desiderative
import okonomi.shared.generated.resources.entry_forms_row_imperative
import okonomi.shared.generated.resources.entry_forms_row_non_past
import okonomi.shared.generated.resources.entry_forms_row_non_past_polite
import okonomi.shared.generated.resources.entry_forms_row_passive
import okonomi.shared.generated.resources.entry_forms_row_past
import okonomi.shared.generated.resources.entry_forms_row_past_polite
import okonomi.shared.generated.resources.entry_forms_row_potential
import okonomi.shared.generated.resources.entry_forms_row_te
import okonomi.shared.generated.resources.entry_forms_row_volitional
import org.jetbrains.compose.resources.StringResource

/**
 * The row-identity to label map, stated a second time so the first one
 * cannot drift. Swapping Potential and Passive there would mislabel
 * every table in the app while the conjugator's own tests, which never
 * see a label, stayed green.
 */
class FormRowLabelsTest {

    private val expected: List<Pair<FormId, StringResource>> = listOf(
        FormId.NonPast to Res.string.entry_forms_row_non_past,
        FormId.NonPastPolite to Res.string.entry_forms_row_non_past_polite,
        FormId.Past to Res.string.entry_forms_row_past,
        FormId.PastPolite to Res.string.entry_forms_row_past_polite,
        FormId.Te to Res.string.entry_forms_row_te,
        FormId.Potential to Res.string.entry_forms_row_potential,
        FormId.Passive to Res.string.entry_forms_row_passive,
        FormId.Causative to Res.string.entry_forms_row_causative,
        FormId.CausativePassive to Res.string.entry_forms_row_causative_passive,
        FormId.Imperative to Res.string.entry_forms_row_imperative,
        FormId.Volitional to Res.string.entry_forms_row_volitional,
        FormId.ConditionalBa to Res.string.entry_forms_row_conditional_ba,
        FormId.ConditionalTara to Res.string.entry_forms_row_conditional_tara,
        FormId.Desiderative to Res.string.entry_forms_row_desiderative,
    )

    @Test
    fun `every row is labelled, and labelled as itself`() {
        assertEquals(FormId.entries, expected.map { it.first }, "a row was added without a label")
        for ((id, resource) in expected) {
            assertEquals(resource, id.label, "$id")
        }
    }

    @Test
    fun `no two rows share a label`() {
        val labels = FormId.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size, "two rows would be indistinguishable in the table")
    }
}
