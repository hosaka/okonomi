package cc.hosaka.okonomi.feature.phrases

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
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
 */
class BreakdownPosCodesTest {

    private val ruleCodes: Set<String>
        get() = breakdownGrammaticalPos + breakdownGrammaticalTextPos

    @Test
    fun `every code the rule tests is one the dictionary declares`() {
        val declared = connection().use { it.column("SELECT code FROM tag_label") }

        // A code JMdict never declared cannot come back from anything a
        // sense says either, so this is the cheap half and it names the
        // culprit outright.
        assertEquals(
            emptySet(),
            ruleCodes - declared,
            "these codes are in the rule but not in the shipped dictionary's tag_label; " +
                "JMdict does not issue them, so they filter nothing",
        )
    }

    @Test
    fun `every code the rule tests is one some sense actually carries`() {
        // Declared is not the same as used: a code JMdict still declares
        // but no longer applies to anything is just as inert in the
        // rule. One pass over sense.pos rather than a LIKE per code.
        val used = connection().use { database ->
            database.column("SELECT pos FROM sense WHERE pos IS NOT NULL")
                .flatMapTo(mutableSetOf()) { column -> column.split(',').map { it.trim() } }
        }

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

private fun connection(): Connection = DriverManager.getConnection("jdbc:sqlite:${dictionaryFile().absolutePath}")

private fun Connection.column(sql: String): Set<String> = createStatement().use { statement ->
    statement.executeQuery(sql).use { rows ->
        buildSet {
            while (rows.next()) {
                add(rows.getString(1))
            }
        }
    }
}

/**
 * The generated dictionary. The two-candidate lookup is borrowed from
 * `PhrasesStringsTest`, because the test JVM's working directory is
 * either the module or the repository root depending on how the build
 * is invoked.
 *
 * What is NOT borrowed, and is new here: that test reads a source file
 * which is always present in a checkout, whereas this one reads a build
 * artifact. This is the only test under `shared/src` that depends on
 * `:tools:dictgen` output, so `:shared:testAndroidHostTest` now fails on
 * a clean tree until the dictionary has been generated — which it was
 * not before. Nothing in Gradle wires that ordering; the full
 * verification baseline happens to generate it via `assembleDebug`.
 *
 * A missing file fails rather than skips, deliberately. A guard that
 * quietly does nothing is the exact shape of the problem this test was
 * written to remove — `cop-da` sat in the rule unused precisely because
 * nothing checked it against the data. If the ordering ever becomes a
 * real obstacle (CI running tests without dictgen), wire the task
 * dependency; do not convert this into a skip.
 */
private fun dictionaryFile(): File {
    val candidates = listOf(
        File("../tools/dictgen/build/generated/dictionary/okonomi.db"),
        File("tools/dictgen/build/generated/dictionary/okonomi.db"),
    )
    val file = candidates.firstOrNull { it.isFile }
    assertNotNull(
        file,
        "the generated dictionary is missing; run :tools:dictgen:generateDictionary " +
            "(tried ${candidates.map { it.absolutePath }})",
    )
    return file
}
