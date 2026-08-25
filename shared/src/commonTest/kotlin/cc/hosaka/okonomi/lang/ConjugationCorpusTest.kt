package cc.hosaka.okonomi.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sweep: every entry of [conjugationCorpus] conjugated as the Forms
 * tab would conjugate it, with every cell fed back through the
 * deinflector.
 *
 * This is the test that finds real bugs. Hand-picked per-class cases
 * pass a paradigm its own author's assumptions back; the dictionary
 * does not. 有る instead of ある, 画餅に帰する tagged `v5s` while ending in
 * る, ウザイ in katakana, 居らっしゃる in kanji — none of those shapes
 * occur to anyone writing test cases by hand, and every one of them
 * broke the first version of this conjugator.
 *
 * See `ConjugationOracle` for what the round-trip can and cannot
 * decide. Over-generation in particular is invisible to it, so the
 * counts below stand in: a class that grows a row it should not have
 * changes the number of rows its entries produce.
 */
class ConjugationCorpusTest {

    @Test
    fun `every cell of every corpus entry deinflects back to its headword`() {
        val failures = mutableListOf<String>()
        var checked = 0
        for (entry in conjugationCorpus) {
            for (conjugation in conjugations(entry.headword, entry.posCodes)) {
                for (form in conjugation.forms) {
                    for ((cell, text) in cellsOf(form)) {
                        if (oracleIsBlind(conjugation.conjugationClass, entry.headword, cell)) continue
                        checked++
                        if (derivationOf(text, entry.headword, conjugation.code, cell) == null) {
                            failures += "${entry.entryId} ${entry.headword} [${conjugation.code}] $cell -> $text"
                        }
                    }
                }
            }
        }
        assertTrue(
            checked > 25_000,
            "the sweep is meant to cover the shipped dictionary's shapes; only $checked cells were checked",
        )
        assertEquals(emptyList(), failures.take(40), "$checked cells checked, ${failures.size} without a derivation")
    }

    @Test
    fun `a conjugable entry always produces a full table, never a half of one`() {
        val failures = mutableListOf<String>()
        for (entry in conjugationCorpus) {
            val conjugable = entry.posCodes.filter { conjugationClassOf(it, entry.headword) != null }
            val tables = conjugations(entry.headword, entry.posCodes)
            if (conjugable.isEmpty()) {
                if (tables.isNotEmpty()) {
                    failures += "${entry.headword}: no conjugable code but ${tables.size} tables"
                }
                continue
            }
            if (tables.isEmpty()) {
                failures += "${entry.headword} $conjugable: conjugable but no table"
                continue
            }
            for (table in tables) {
                val expected = expectedRows(table.conjugationClass)
                assertEquals(
                    expected,
                    table.forms.map { it.id },
                    "${entry.headword} [${table.code}] produced the wrong rows",
                )
                if (table.forms.any { it.affirmative.isBlank() }) {
                    failures += "${entry.headword} [${table.code}]: a blank cell"
                }
            }
        }
        assertEquals(emptyList(), failures.take(40))
    }

    /**
     * The negative controls: a code that names a paradigm the headword
     * cannot take must yield nothing. 画餅に帰する is tagged `v5s` and
     * ends in る; keying the row off the spelling alone turned that
     * into 画餅に帰すらない, which is the failure this pins.
     */
    @Test
    fun `a headword that disagrees with its code produces no table for that code`() {
        val mismatched = conjugationCorpus.filter { entry ->
            entry.posCodes.any { code ->
                code in conjugablePosCodes && conjugationClassOf(code, entry.headword) == null
            }
        }
        assertTrue(mismatched.size >= 8, "the corpus is meant to carry negative controls; found ${mismatched.size}")
        for (entry in mismatched) {
            val dropped = entry.posCodes.filter { it in conjugablePosCodes && conjugationClassOf(it, entry.headword) == null }
            for (code in dropped) {
                assertEquals(
                    emptyList(),
                    conjugate(entry.headword, code),
                    "${entry.headword} does not end in the kana $code names",
                )
            }
        }
    }

    /**
     * Over-generation is what the oracle cannot see, so the row set of
     * each class is pinned by hand. ある and the honorific class stop
     * after the conditionals; adjectives never reach the verb rows.
     */
    private fun expectedRows(conjugationClass: ConjugationClass): List<FormId> = when (conjugationClass) {
        ConjugationClass.AdjI,
        ConjugationClass.AdjIx,
        ConjugationClass.GodanAru,
        -> listOf(
            FormId.NonPast,
            FormId.NonPastPolite,
            FormId.Past,
            FormId.PastPolite,
            FormId.Te,
            FormId.ConditionalBa,
            FormId.ConditionalTara,
        )

        // The one class with an imperative and nothing else derived:
        // ください is everyday, ござれる and ござろう are not words.
        ConjugationClass.GodanHonorific -> listOf(
            FormId.NonPast,
            FormId.NonPastPolite,
            FormId.Past,
            FormId.PastPolite,
            FormId.Te,
            FormId.Imperative,
            FormId.ConditionalBa,
            FormId.ConditionalTara,
        )

        else -> FormId.entries
    }
}
