/*
 * Copyright (C) 2023-2026  Yomitan Authors
 * Copyright (C) 2020-2022  Yomichan Authors
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
 * The corpus runner semantics are ported from Yomitan's
 * test/fixtures/language-transformer-test.js.
 */
package cc.hosaka.okonomi.deinflect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class JapaneseDeinflectorTest {

    // Counts derived from the upstream sources at Yomitan commit
    // 77e200428902abf4fa48284df92da7af3dcb4162 (2026-08-18); they make silent
    // omissions in the ported data fail loudly.
    private companion object {
        const val EXPECTED_CORPUS_ROWS = 1406
        const val EXPECTED_CORPUS_CATEGORIES = 33
        const val EXPECTED_TRANSFORMS = 55
        const val EXPECTED_RULES = 889
    }

    @Test
    fun corpusIsComplete() {
        assertEquals(EXPECTED_CORPUS_CATEGORIES, japaneseTransformsCorpus.size)
        assertEquals(EXPECTED_CORPUS_ROWS, japaneseTransformsCorpus.sumOf { it.rows.size })
    }

    @Test
    fun ruleSetIsComplete() {
        assertEquals(EXPECTED_TRANSFORMS, japaneseTransforms.transforms.size)
        assertEquals(EXPECTED_RULES, japaneseTransforms.transforms.sumOf { it.rules.size })
    }

    @Test
    fun everyCorpusReasonResolvesToAPortedTransform() {
        val unresolved = japaneseTransformsCorpus
            .asSequence()
            .flatMap { it.rows }
            .flatMap { it.reasons.orEmpty() }
            .distinct()
            .filterNot { JapaneseDeinflector.isKnownTransform(it) }
            .toList()
        assertTrue(unresolved.isEmpty(), "Corpus reasons without a ported transform: $unresolved")
    }

    @Test
    fun everyCorpusRuleTagResolvesToConditionFlags() {
        val unresolved = japaneseTransformsCorpus
            .asSequence()
            .flatMap { it.rows }
            .mapNotNull { it.rule }
            .distinct()
            .filter { JapaneseDeinflector.conditionFlagsForConditionType(it) == 0L }
            .toList()
        assertTrue(unresolved.isEmpty(), "Corpus rule tags without condition flags: $unresolved")
    }

    @Test
    fun corpus() {
        val failures = mutableListOf<String>()
        for (category in japaneseTransformsCorpus) {
            for (row in category.rows) {
                val has = hasTermReasons(row.source, row.term, row.rule, row.reasons)
                if (has != category.valid) {
                    val expectation = if (category.valid) "should derive" else "should NOT derive"
                    failures.add(
                        "[${category.category}] ${row.source} $expectation ${row.term}" +
                            " (rule=${row.rule}, reasons=${row.reasons})",
                    )
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} corpus rows failed:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun namedCaseTabenakattaSurfacesTaberu() {
        val entryFlags = posCodesToConditionFlags("v1,vt")
        val candidate = JapaneseDeinflector.deinflect("食べなかった")
            .firstOrNull { it.text == "食べる" && it.trace == listOf("negative", "-た") }
            ?: fail("食べなかった did not derive 食べる with trace [negative, -た]")
        assertTrue(
            LanguageTransformer.conditionsMatch(candidate.conditions, entryFlags),
            "食べる candidate must be compatible with a v1 dictionary entry",
        )
    }

    @Test
    fun firstCandidateIsTheIdentity() {
        for (input in listOf("食べなかった", "abc", "")) {
            val first = JapaneseDeinflector.deinflect(input).first()
            assertEquals(input, first.text)
            assertEquals(0L, first.conditions)
            assertTrue(first.trace.isEmpty())
        }
    }

    @Test
    fun textWithNoApplicableRulesYieldsOnlyTheIdentity() {
        val candidates = JapaneseDeinflector.deinflect("abc")
        assertEquals(1, candidates.size)
        assertEquals("abc", candidates.first().text)
    }

    @Test
    fun ruleDisplayNameResolvesNamesAndFallsBackToTheId() {
        // The eight kansai-ben transforms are the only id != name cases.
        assertEquals("kansai-ben", JapaneseDeinflector.ruleDisplayName("kansai-ben -く"))
        assertEquals("kansai-ben", JapaneseDeinflector.ruleDisplayName("kansai-ben negative"))
        assertEquals("-ます", JapaneseDeinflector.ruleDisplayName("-ます"))
        assertEquals("no such transform", JapaneseDeinflector.ruleDisplayName("no such transform"))
    }

    @Test
    fun posMappingMapsEveryDocumentedCodeToItsTargetConditionFlags() {
        // One entry per code in the PosMapping KDoc table.
        val documentedMapping = mapOf(
            "v1" to "v1",
            "v1-s" to "v1",
            "v5u" to "v5",
            "v5u-s" to "v5",
            "v5k" to "v5",
            "v5k-s" to "v5",
            "v5g" to "v5",
            "v5s" to "v5",
            "v5t" to "v5",
            "v5n" to "v5",
            "v5b" to "v5",
            "v5m" to "v5",
            "v5r" to "v5",
            "v5r-i" to "v5",
            "v5aru" to "v5",
            "vk" to "vk",
            "vs" to "vs",
            "vs-i" to "vs",
            "vs-s" to "vs",
            "vz" to "vz",
            "adj-i" to "adj-i",
            "adj-ix" to "adj-i",
            // An auxiliary adjective inflects as an adjective, and the
            // Forms tab conjugates it as one.
            "aux-adj" to "adj-i",
            // The old uru class inflects on its え stem like an ichidan
            // verb, which is the paradigm the conjugator gives it.
            "v5uru" to "v1",
            "v-unspec" to "v",
        )
        for ((code, conditionType) in documentedMapping) {
            val expected = JapaneseDeinflector.conditionFlagsForConditionType(conditionType)
            assertNotEquals(0L, expected, "condition type $conditionType must have flags")
            assertEquals(expected, posCodesToConditionFlags(code), "pos code $code")
        }
    }

    @Test
    fun posMappingCombinesCodesAndIgnoresUnknownOnes() {
        val v1Flags = JapaneseDeinflector.conditionFlagsForConditionType("v1")
        val v5Flags = JapaneseDeinflector.conditionFlagsForConditionType("v5")
        assertEquals(v5Flags, posCodesToConditionFlags("v5r,vt"))
        assertEquals(v1Flags or v5Flags, posCodesToConditionFlags("v1, v5k-s, n, exp"))

        // The precursor su class is written both ways (兼する, 兼す), so
        // it carries the union its two conjugation paradigms need.
        val vsFlags = JapaneseDeinflector.conditionFlagsForConditionType("vs")
        assertEquals(vsFlags or v5Flags, posCodesToConditionFlags("vs-c"))

        // Unknown codes, including the archaic verb classes, contribute 0.
        assertEquals(0L, posCodesToConditionFlags("n,exp,adj-na"))
        assertEquals(0L, posCodesToConditionFlags("v2a-s,v4r,vn,vr"))
        assertEquals(0L, posCodesToConditionFlags(null))
        assertEquals(0L, posCodesToConditionFlags(""))
    }

    @Test
    fun constructionFailsOnUnresolvableConditionsIn() {
        val descriptor = LanguageTransformDescriptor(
            language = "xx",
            conditions = listOf(ConditionDescriptor("a", "A", isDictionaryForm = true)),
            transforms = listOf(
                TransformDescriptor(
                    id = "t",
                    name = "t",
                    rules = listOf(suffixInflection("x", "y", listOf("nope"), listOf("a"))),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            LanguageTransformer().addDescriptor(descriptor)
        }
    }

    @Test
    fun constructionFailsOnUnresolvableConditionsOut() {
        val descriptor = LanguageTransformDescriptor(
            language = "xx",
            conditions = listOf(ConditionDescriptor("a", "A", isDictionaryForm = true)),
            transforms = listOf(
                TransformDescriptor(
                    id = "t",
                    name = "t",
                    rules = listOf(suffixInflection("x", "y", listOf("a"), listOf("nope"))),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            LanguageTransformer().addDescriptor(descriptor)
        }
    }

    @Test
    fun constructionFailsBeyondSixtyFourConditionFlags() {
        val conditions = (0 until 65).map { index ->
            ConditionDescriptor("c$index", "C$index", isDictionaryForm = false)
        }
        val descriptor = LanguageTransformDescriptor(
            language = "xx",
            conditions = conditions,
            transforms = emptyList(),
        )
        assertFailsWith<IllegalArgumentException> {
            LanguageTransformer().addDescriptor(descriptor)
        }
    }

    @Test
    fun constructionFailsOnSubConditionDependencyCycle() {
        val descriptor = LanguageTransformDescriptor(
            language = "xx",
            conditions = listOf(
                ConditionDescriptor("a", "A", isDictionaryForm = false, subConditions = listOf("b")),
                ConditionDescriptor("b", "B", isDictionaryForm = false, subConditions = listOf("a")),
            ),
            transforms = emptyList(),
        )
        val failure = assertFailsWith<IllegalArgumentException> {
            LanguageTransformer().addDescriptor(descriptor)
        }
        assertTrue(
            failure.message.orEmpty().contains("Cycle"),
            "cycle failure must carry its distinct message, was: ${failure.message}",
        )
    }

    /**
     * Port of hasTermReasons from Yomitan's language-transformer-test.js:
     * a match requires the exact candidate text, conditions compatible with
     * the dictionary rule tag's flags (when a tag is given), and a trace equal
     * to the expected reasons (when reasons are given).
     */
    private fun hasTermReasons(
        source: String,
        expectedTerm: String,
        expectedConditionName: String?,
        expectedReasons: List<String>?,
    ): Boolean {
        for (candidate in JapaneseDeinflector.deinflect(source)) {
            if (candidate.text != expectedTerm) continue
            if (expectedConditionName != null) {
                val expectedConditions =
                    JapaneseDeinflector.conditionFlagsForConditionType(expectedConditionName)
                if (!LanguageTransformer.conditionsMatch(candidate.conditions, expectedConditions)) continue
            }
            if (expectedReasons != null && candidate.trace != expectedReasons) continue
            return true
        }
        return false
    }
}
