package cc.hosaka.okonomi.ui.labels

import androidx.compose.runtime.Composable
import cc.hosaka.okonomi.db.DictionaryTag
import okonomi.shared.generated.resources.Res
import okonomi.shared.generated.resources.allStringResources
import okonomi.shared.generated.resources.deinflection_rule_causative
import okonomi.shared.generated.resources.deinflection_rule_continuative
import okonomi.shared.generated.resources.deinflection_rule_imperative
import okonomi.shared.generated.resources.deinflection_rule_imperative_negative_slang
import okonomi.shared.generated.resources.deinflection_rule_kansai_ben
import okonomi.shared.generated.resources.deinflection_rule_n_slang
import okonomi.shared.generated.resources.deinflection_rule_negative
import okonomi.shared.generated.resources.deinflection_rule_passive
import okonomi.shared.generated.resources.deinflection_rule_potential
import okonomi.shared.generated.resources.deinflection_rule_potential_or_passive
import okonomi.shared.generated.resources.deinflection_rule_short_causative
import okonomi.shared.generated.resources.deinflection_rule_volitional
import okonomi.shared.generated.resources.deinflection_rule_volitional_slang
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The text a dictionary tag is drawn as, in the UI language: the app's
 * own `tag_<code>` string from `tags.xml`, or, for a code the app has
 * no string for (one a later dictionary adds), the dictionary's own
 * English label the tag carries.
 *
 * Resolved here, in composition, and never in state or a producer: a
 * `stringResource` call site is what recomposes when the UI language
 * changes, and a string resolved anywhere earlier would keep the
 * language it was resolved in.
 */
@Composable
fun tagLabel(tag: DictionaryTag): String {
    val resource = tagStringResource(tag.code) ?: return tag.label
    return stringResource(resource)
}

/**
 * The text a deinflection rule is drawn as in the search breadcrumb, in
 * the UI language. [rule] is the rule's name as the deinflector gives it
 * (`SearchHit.traceLabels`); a rule named by its suffix (`-た`, `-ます`)
 * has no string and is shown as it is in every language, and so is a
 * name the app does not know.
 */
@Composable
fun ruleLabel(rule: String): String {
    val resource = ruleStringResource(rule) ?: return rule
    return stringResource(resource)
}

/**
 * The app's string for the dictionary tag [code], or null when it has
 * none. The string is named `tag_<code>` with `-` written as `_`, which
 * is what a resource name can hold, and with the case kept: `iK` and
 * `ik` are different tags with different strings. Looked up by name
 * because there is one for every code the dictionary declares, kept in
 * step with it by `DictionaryLabelsTest` rather than by a hand-written
 * table here. No code may hold `_` of its own, or its name would be
 * another code's; the same test holds that too.
 */
private fun tagStringResource(code: String): StringResource? =
    Res.allStringResources[TAG_STRING_PREFIX + code.replace('-', '_')]

/**
 * The app's string for the deinflection rule named [rule], or null for a
 * rule named by its suffix, which reads the same in every language.
 * Keyed by the deinflector's own names, which are the stable keys here:
 * a trace carries nothing else. A new rule named in words needs a
 * branch here as well as its `deinflection_rule_*` string;
 * `DictionaryLabelsTest` fails until it has both.
 */
internal fun ruleStringResource(rule: String): StringResource? = when (rule) {
    "causative" -> Res.string.deinflection_rule_causative
    "short causative" -> Res.string.deinflection_rule_short_causative
    "imperative" -> Res.string.deinflection_rule_imperative
    "continuative" -> Res.string.deinflection_rule_continuative
    "negative" -> Res.string.deinflection_rule_negative
    "passive" -> Res.string.deinflection_rule_passive
    "potential" -> Res.string.deinflection_rule_potential
    "potential or passive" -> Res.string.deinflection_rule_potential_or_passive
    "volitional" -> Res.string.deinflection_rule_volitional
    "volitional slang" -> Res.string.deinflection_rule_volitional_slang
    "n-slang" -> Res.string.deinflection_rule_n_slang
    "imperative negative slang" -> Res.string.deinflection_rule_imperative_negative_slang
    "kansai-ben" -> Res.string.deinflection_rule_kansai_ben
    else -> null
}

private const val TAG_STRING_PREFIX = "tag_"
