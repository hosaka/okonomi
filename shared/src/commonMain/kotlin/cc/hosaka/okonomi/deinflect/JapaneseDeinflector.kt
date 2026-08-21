/*
 * Copyright (C) 2024-2026  Yomitan Authors
 * Copyright (C) 2026  Okonomi Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cc.hosaka.okonomi.deinflect

/**
 * One deinflection candidate: the candidate dictionary form, the condition
 * flags a dictionary entry must satisfy (0 matches anything), and the ordered
 * chain of transform ids that produced it.
 */
data class Deinflection(
    val text: String,
    val conditions: Long,
    val trace: List<String>,
)

/**
 * Public deinflection API: the [LanguageTransformer] engine wired with the
 * complete Japanese descriptor. The wired engine is kept private so nothing
 * can call [LanguageTransformer.addDescriptor] on it after construction.
 */
object JapaneseDeinflector {

    private val transformer: LanguageTransformer = LanguageTransformer().apply {
        addDescriptor(japaneseTransforms)
    }

    /**
     * Returns all deinflection candidates for [text]. The first candidate is
     * always the input itself with conditions 0. A candidate is compatible
     * with a dictionary entry when
     * `conditions == 0L || (conditions and entryFlags) != 0L`,
     * where entryFlags comes from [posCodesToConditionFlags].
     *
     * Duplicate candidate texts are possible (the same form can be reached
     * through multiple derivation paths, each with its own conditions and
     * trace); deduplication is the lookup site's concern.
     */
    fun deinflect(text: String): List<Deinflection> =
        transformer.transform(text).map { transformed ->
            Deinflection(
                text = transformed.text,
                conditions = transformed.conditions,
                trace = transformed.trace.map { it.transform },
            )
        }

    /**
     * Human-readable display name for a transform id. Unknown ids fall back
     * to the id itself, mirroring upstream's getUserFacingInflectionRules.
     */
    fun ruleDisplayName(id: String): String = transformer.getTransformDisplayName(id) ?: id

    /** True when [id] is a transform of the ported descriptor. */
    internal fun isKnownTransform(id: String): Boolean =
        transformer.getTransformDisplayName(id) != null

    /** Flags for any condition type in the lattice; 0 for unknown types. */
    internal fun conditionFlagsForConditionType(conditionType: String): Long =
        transformer.getConditionFlagsFromConditionType(conditionType)

    /** Flags restricted to dictionary-form condition types; 0 for others. */
    internal fun conditionFlagsForPartsOfSpeech(partsOfSpeech: Iterable<String>): Long =
        transformer.getConditionFlagsFromPartsOfSpeech(partsOfSpeech)
}
