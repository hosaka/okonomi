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
 *
 * Ported to Kotlin from Yomitan's ext/js/language/language-transformer.js
 * and ext/js/language/language-transforms.js.
 */
package cc.hosaka.okonomi.deinflect

/**
 * A single condition in the descriptor's condition lattice. Leaf conditions
 * (no [subConditions]) receive their own flag bit; parent conditions resolve
 * to the OR of their children's flags.
 */
class ConditionDescriptor(
    val type: String,
    val name: String,
    val isDictionaryForm: Boolean,
    val subConditions: List<String>? = null,
)

/**
 * An uncompiled deinflection rule. Matching is plain string comparison; the
 * upstream regex forms (`suffix$`, `^word$`) reduce to endsWith/equals here.
 */
sealed class RuleDescriptor {
    abstract val inflected: String
    abstract val deinflected: String
    abstract val conditionsIn: List<String>
    abstract val conditionsOut: List<String>

    class Suffix(
        override val inflected: String,
        override val deinflected: String,
        override val conditionsIn: List<String>,
        override val conditionsOut: List<String>,
    ) : RuleDescriptor()

    class WholeWord(
        override val inflected: String,
        override val deinflected: String,
        override val conditionsIn: List<String>,
        override val conditionsOut: List<String>,
    ) : RuleDescriptor()
}

fun suffixInflection(
    inflectedSuffix: String,
    deinflectedSuffix: String,
    conditionsIn: List<String>,
    conditionsOut: List<String>,
): RuleDescriptor {
    // An empty inflected suffix would make deinflect() append instead of
    // truncate (upstream's slice(0, -0) would misbehave the same way); no
    // such rule exists in the ported data.
    require(inflectedSuffix.isNotEmpty()) { "inflectedSuffix must not be empty" }
    return RuleDescriptor.Suffix(inflectedSuffix, deinflectedSuffix, conditionsIn, conditionsOut)
}

fun wholeWordInflection(
    inflectedWord: String,
    deinflectedWord: String,
    conditionsIn: List<String>,
    conditionsOut: List<String>,
): RuleDescriptor = RuleDescriptor.WholeWord(inflectedWord, deinflectedWord, conditionsIn, conditionsOut)

class TransformDescriptor(
    val id: String,
    val name: String,
    val rules: List<RuleDescriptor>,
)

class LanguageTransformDescriptor(
    val language: String,
    val conditions: List<ConditionDescriptor>,
    val transforms: List<TransformDescriptor>,
)

data class TraceFrame(
    val transform: String,
    val ruleIndex: Int,
    val text: String,
)

data class TransformedText(
    val text: String,
    val conditions: Long,
    val trace: List<TraceFrame>,
)

/**
 * Condition-bitmask deinflection engine ported from Yomitan's
 * LanguageTransformer. Compiles a [LanguageTransformDescriptor] into flag
 * bitmasks and performs breadth-first suffix stripping with a per-path trace
 * and cycle guard. The upstream 32-flag limit is widened to 64 via [Long].
 *
 * The class is append-only: upstream's clear() is not ported, and once wired
 * (see [JapaneseDeinflector]) an instance must not receive further
 * [addDescriptor] calls.
 */
class LanguageTransformer {

    private class CompiledRule(
        val descriptor: RuleDescriptor,
        val conditionsIn: Long,
        val conditionsOut: Long,
    ) {
        fun matches(text: String): Boolean = when (descriptor) {
            is RuleDescriptor.Suffix -> text.endsWith(descriptor.inflected)
            is RuleDescriptor.WholeWord -> text == descriptor.inflected
        }

        fun deinflect(text: String): String = when (descriptor) {
            is RuleDescriptor.Suffix ->
                text.dropLast(descriptor.inflected.length) + descriptor.deinflected
            is RuleDescriptor.WholeWord -> descriptor.deinflected
        }
    }

    private class CompiledTransform(
        val id: String,
        val name: String,
        val rules: List<CompiledRule>,
    )

    private var nextFlagIndex: Int = 0
    private val transforms = mutableListOf<CompiledTransform>()
    private val transformNamesById = mutableMapOf<String, String>()
    private val conditionTypeToConditionFlagsMap = mutableMapOf<String, Long>()
    private val partOfSpeechToConditionFlagsMap = mutableMapOf<String, Long>()

    /**
     * Compiles and registers a descriptor. Throws [IllegalArgumentException]
     * on any unresolvable condition name, mirroring upstream's constructor
     * failure semantics.
     */
    fun addDescriptor(descriptor: LanguageTransformDescriptor) {
        val conditionFlagsMap = buildConditionFlagsMap(descriptor.conditions)

        val compiled = descriptor.transforms.map { transform ->
            val rules = transform.rules.mapIndexed { index, rule ->
                val conditionFlagsIn = getConditionFlagsStrict(conditionFlagsMap, rule.conditionsIn)
                    ?: throw IllegalArgumentException("Invalid conditionsIn for transform ${transform.id}.rules[$index]")
                val conditionFlagsOut = getConditionFlagsStrict(conditionFlagsMap, rule.conditionsOut)
                    ?: throw IllegalArgumentException("Invalid conditionsOut for transform ${transform.id}.rules[$index]")
                CompiledRule(rule, conditionFlagsIn, conditionFlagsOut)
            }
            CompiledTransform(transform.id, transform.name, rules)
        }
        transforms.addAll(compiled)
        for (transform in compiled) {
            transformNamesById[transform.id] = transform.name
        }

        for (condition in descriptor.conditions) {
            val flags = conditionFlagsMap[condition.type] ?: continue
            conditionTypeToConditionFlagsMap[condition.type] = flags
            if (condition.isDictionaryForm) {
                partOfSpeechToConditionFlagsMap[condition.type] = flags
            }
        }
    }

    fun getConditionFlagsFromPartsOfSpeech(partsOfSpeech: Iterable<String>): Long =
        getConditionFlags(partOfSpeechToConditionFlagsMap, partsOfSpeech)

    fun getConditionFlagsFromConditionType(conditionType: String): Long =
        getConditionFlags(conditionTypeToConditionFlagsMap, listOf(conditionType))

    /**
     * Breadth-first deinflection. The first result is always the source text
     * itself with conditions 0 (matches anything). Each subsequent result
     * carries the conditions the next rule application must satisfy and the
     * ordered trace of transforms applied along its path.
     */
    fun transform(sourceText: String): List<TransformedText> {
        val results = ArrayList<TransformedText>()
        results.add(TransformedText(sourceText, 0L, emptyList()))
        var i = 0
        while (i < results.size) {
            val current = results[i]
            i++
            val text = current.text
            val conditions = current.conditions
            val trace = current.trace
            for (transform in transforms) {
                val id = transform.id
                for ((j, rule) in transform.rules.withIndex()) {
                    if (!conditionsMatch(conditions, rule.conditionsIn)) continue
                    if (!rule.matches(text)) continue

                    val isCycle = trace.any { frame ->
                        frame.transform == id && frame.ruleIndex == j && frame.text == text
                    }
                    // Upstream logs a warning here; the silent skip is
                    // deliberate, the path is simply not expanded further.
                    if (isCycle) continue

                    results.add(
                        TransformedText(
                            rule.deinflect(text),
                            rule.conditionsOut,
                            extendTrace(trace, TraceFrame(id, j, text)),
                        ),
                    )
                }
            }
        }
        return results
    }

    /** Display name for a transform id, or null when the id is unknown. */
    fun getTransformDisplayName(id: String): String? = transformNamesById[id]

    private fun buildConditionFlagsMap(conditions: List<ConditionDescriptor>): Map<String, Long> {
        val conditionFlagsMap = mutableMapOf<String, Long>()
        var targets = conditions
        while (targets.isNotEmpty()) {
            val nextTargets = mutableListOf<ConditionDescriptor>()
            for (target in targets) {
                val subConditions = target.subConditions
                val flags: Long
                if (subConditions == null) {
                    if (nextFlagIndex >= 64) {
                        throw IllegalArgumentException("Maximum number of conditions was exceeded")
                    }
                    flags = 1L shl nextFlagIndex
                    ++nextFlagIndex
                } else {
                    val multiFlags = getConditionFlagsStrict(conditionFlagsMap, subConditions)
                    if (multiFlags == null) {
                        nextTargets.add(target)
                        continue
                    }
                    flags = multiFlags
                }
                conditionFlagsMap[target.type] = flags
            }
            if (nextTargets.size == targets.size) {
                // Upstream reuses its flag-limit message here; a distinct one
                // is clearer.
                throw IllegalArgumentException(
                    "Cycle detected in subCondition declarations: " +
                        nextTargets.joinToString { it.type },
                )
            }
            targets = nextTargets
        }
        return conditionFlagsMap
    }

    private fun getConditionFlagsStrict(
        conditionFlagsMap: Map<String, Long>,
        conditionTypes: Iterable<String>,
    ): Long? {
        var flags = 0L
        for (conditionType in conditionTypes) {
            val conditionFlags = conditionFlagsMap[conditionType] ?: return null
            flags = flags or conditionFlags
        }
        return flags
    }

    private fun getConditionFlags(
        conditionFlagsMap: Map<String, Long>,
        conditionTypes: Iterable<String>,
    ): Long {
        var flags = 0L
        for (conditionType in conditionTypes) {
            flags = flags or (conditionFlagsMap[conditionType] ?: 0L)
        }
        return flags
    }

    private fun extendTrace(trace: List<TraceFrame>, newFrame: TraceFrame): List<TraceFrame> {
        val newTrace = ArrayList<TraceFrame>(trace.size + 1)
        newTrace.add(newFrame)
        newTrace.addAll(trace)
        return newTrace
    }

    companion object {
        /**
         * If [currentConditions] is 0, [nextConditions] is ignored and true is
         * returned. Otherwise there must be at least one shared condition.
         */
        fun conditionsMatch(currentConditions: Long, nextConditions: Long): Boolean =
            currentConditions == 0L || (currentConditions and nextConditions) != 0L
    }
}
