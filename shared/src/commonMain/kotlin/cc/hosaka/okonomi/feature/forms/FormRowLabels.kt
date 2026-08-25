package cc.hosaka.okonomi.feature.forms

import cc.hosaka.okonomi.lang.FormId
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
 * What each row is called in the leading column.
 *
 * This lives beside nothing else on purpose: it is the only place row
 * identity meets reader-facing text, so swapping two entries here would
 * mislabel every table in the app while every other test stayed green.
 * `FormRowLabelsTest` pins the whole mapping.
 */
internal val FormId.label: StringResource
    get() = when (this) {
        FormId.NonPast -> Res.string.entry_forms_row_non_past
        FormId.NonPastPolite -> Res.string.entry_forms_row_non_past_polite
        FormId.Past -> Res.string.entry_forms_row_past
        FormId.PastPolite -> Res.string.entry_forms_row_past_polite
        FormId.Te -> Res.string.entry_forms_row_te
        FormId.Potential -> Res.string.entry_forms_row_potential
        FormId.Passive -> Res.string.entry_forms_row_passive
        FormId.Causative -> Res.string.entry_forms_row_causative
        FormId.CausativePassive -> Res.string.entry_forms_row_causative_passive
        FormId.Imperative -> Res.string.entry_forms_row_imperative
        FormId.Volitional -> Res.string.entry_forms_row_volitional
        FormId.ConditionalBa -> Res.string.entry_forms_row_conditional_ba
        FormId.ConditionalTara -> Res.string.entry_forms_row_conditional_tara
        FormId.Desiderative -> Res.string.entry_forms_row_desiderative
    }
