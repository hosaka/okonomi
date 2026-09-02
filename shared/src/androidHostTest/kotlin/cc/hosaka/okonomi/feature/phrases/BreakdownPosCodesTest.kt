package cc.hosaka.okonomi.feature.phrases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Holds the tappable-word rule's part-of-speech sets to the dictionary
 * that actually ships.
 *
 * The rule is made of JMdict tag names and carries no list of words, on
 * Alex's ruling — the right call, but it has a failure mode a word list
 * does not: a tag that is misspelt, or that JMdict has retired, filters
 * nothing at all and reports nothing. It is not wrong, it is inert, and
 * every unit test around it goes on passing because they all feed the
 * rule codes typed by hand.
 *
 * That is not hypothetical. `cop-da` sat in both sets from the first
 * commit of this feature: JMdict retired it for `cop` years ago, the
 * shipped dictionary carries it zero times and does not even declare
 * it, and the test guarding the copula was feeding the rule that dead
 * code — so the guard would have stayed green with `cop` and `aux-v`
 * both deleted while だ turned blue on 13,376 sentences.
 *
 * Reads the real sets rather than a copy of them. A copy would go stale
 * in exactly the same silence this exists to break.
 *
 * The dictionary's side of that comparison is `pos-codes.tsv`, written
 * by `:tools:dictgen` from the finished database and copied here by
 * `:tools:dictgen:syncPosCodes`. It is read from the classpath, so these
 * tests need neither the 184 MB database nor a dictgen run and hold on a
 * clean checkout — which `pr-test.yml` requires, since it deliberately
 * never builds the dictionary.
 *
 * That the sidecar is committed is the design, not a compromise: a code
 * JMdict retires arrives as a reviewable line in a diff the next time the
 * dictionary is regenerated, rather than as a CI failure nobody expected.
 * Regenerate it, never hand-edit it — a hand-edited copy is the stale
 * copy this exists to prevent.
 */
class BreakdownPosCodesTest {

    private val ruleCodes: Set<String>
        get() = breakdownGrammaticalPos + breakdownGrammaticalTextPos

    @Test
    fun `every code the rule tests is one the dictionary declares`() {
        // A code JMdict never declared cannot come back from anything a
        // sense says either, so this is the cheap half and it names the
        // culprit outright.
        assertEquals(
            emptySet(),
            ruleCodes - posCodes().keys,
            "these codes are in the rule but not declared by the shipped dictionary; " +
                "JMdict does not issue them, so they filter nothing",
        )
    }

    @Test
    fun `every code the rule tests is one some sense actually carries`() {
        // Declared is not the same as used: a code JMdict still declares
        // but no longer applies to anything is just as inert in the rule.
        val used = posCodes().filterValues { carried -> carried }.keys

        assertEquals(
            emptySet(),
            ruleCodes - used,
            "these codes are declared but no sense in the shipped dictionary carries them, " +
                "so the rule clause naming them can never fire",
        )
    }

    /**
     * The other direction, and the one that would catch a set quietly
     * emptied: `prt` and `cop` are what the text clause rests on, and
     * between them they are why は and だ are inert.
     */
    @Test
    fun `the text clause still names the two codes it rests on`() {
        assertTrue("prt" in breakdownGrammaticalTextPos, "particles are the whole point")
        assertTrue("cop" in breakdownGrammaticalTextPos, "だ is a copula, not a leaf")
        assertTrue(
            breakdownGrammaticalTextPos.none { it.startsWith("aux") },
            "an auxiliary in the text clause makes 為る untappable; see the rule's own comment",
        )
        assertTrue(breakdownGrammaticalTextPos.all { it in breakdownGrammaticalPos })
    }
}

private const val POS_CODES = "pos-codes.tsv"

/**
 * The sidecar, as declared code to whether a sense carries it.
 *
 * A missing resource fails rather than skips, deliberately. A guard that
 * quietly does nothing is the exact shape of the problem this test was
 * written to remove — `cop-da` sat in the rule unused precisely because
 * nothing checked it against the data.
 */
private fun posCodes(): Map<String, Boolean> {
    val text = BreakdownPosCodesTest::class.java
        .getResourceAsStream("/$POS_CODES")
        ?.bufferedReader()
        ?.use { it.readText() }
    assertNotNull(
        text,
        "$POS_CODES is not on the test classpath; regenerate it with " +
            ":tools:dictgen:syncPosCodes and commit it",
    )
    val codes = text.lineSequence()
        .map(String::trim)
        .filterNot { it.isEmpty() || it.startsWith("#") }
        .associate { line ->
            val fields = line.split('\t', limit = 2)
            fields[0] to (fields.getOrNull(1) == "used")
        }
    assertTrue(codes.isNotEmpty(), "$POS_CODES parsed to nothing; the format has drifted")
    return codes
}
