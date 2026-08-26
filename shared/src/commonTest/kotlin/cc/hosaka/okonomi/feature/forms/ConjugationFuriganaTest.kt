package cc.hosaka.okonomi.feature.forms

import cc.hosaka.okonomi.lang.FormId
import cc.hosaka.okonomi.lang.conjugations
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When a conjugation table earns furigana, and when a ruby would only
 * repeat what the row above already said.
 */
class ConjugationFuriganaTest {

    private fun rows(base: String, reading: String?, code: String): List<ConjugationRow> {
        val conjugation = conjugations(base, listOf(code)).single()
        return conjugationRows(conjugation, reading)
    }

    private fun affirmative(rows: List<ConjugationRow>, id: FormId): List<FuriganaSegment> =
        rows.single { it.id == id }.affirmative

    private fun readingOf(rows: List<ConjugationRow>, id: FormId): String? =
        affirmative(rows, id).firstOrNull { it.reading != null }?.reading

    /**
     * 為る is the case the whole feature exists for: 為 reads す, し and
     * さ depending on the row, and nothing on screen said so.
     */
    @Test
    fun `a stem that shifts across the table is annotated on every row that carries it`() {
        val rows = rows("為る", "する", "vs-i")

        assertEquals("す", readingOf(rows, FormId.NonPast))
        assertEquals("し", readingOf(rows, FormId.Past))
        assertEquals("さ", readingOf(rows, FormId.Passive))
        assertEquals("さ", readingOf(rows, FormId.Causative))
        assertEquals("す", readingOf(rows, FormId.ConditionalBa))
        assertEquals(
            listOf(FuriganaSegment("為", "し"), FuriganaSegment("ない")),
            rows.single { it.id == FormId.NonPast }.negative,
        )
    }

    /**
     * 出来る sits in the same table as 為 and reads でき in both the rows
     * it appears in, so the rule leaves it plain even while the table
     * around it is annotated.
     */
    @Test
    fun `a stem that is constant is left plain even in an annotated table`() {
        val rows = rows("為る", "する", "vs-i")

        assertNull(readingOf(rows, FormId.Potential))
        assertEquals(listOf(FuriganaSegment("出来る")), affirmative(rows, FormId.Potential))
    }

    /**
     * 食べる reads 食 as た in all fourteen rows. A ruby saying so
     * fourteen times is noise, and the table stays as it was.
     */
    @Test
    fun `a table whose stem never shifts carries no furigana at all`() {
        val rows = rows("食べる", "たべる", "v1")

        assertTrue(
            rows.all { row -> (row.affirmative + row.negative.orEmpty()).all { it.reading == null } },
            "食べる gains nothing from a ruby repeating た on every row",
        )
    }

    @Test
    fun `a table with no reading to conjugate is plain`() {
        val rows = rows("食べる", null, "v1")

        assertEquals(listOf(FuriganaSegment("食べる")), affirmative(rows, FormId.NonPast))
        assertEquals(FormId.entries, rows.map { it.id })
    }

    /**
     * A reading that does not inflect the way the written form does
     * cannot be paired with it row by row, and a table of invented
     * readings is worse than a table of none.
     */
    @Test
    fun `a reading in a different class is refused rather than aligned`() {
        val rows = rows("食べる", "くう", "v1")

        assertTrue(rows.all { row -> row.affirmative.all { it.reading == null } })
    }

    @Test
    fun `every row still spells its forms exactly`() {
        val rows = rows("為る", "する", "vs-i")
        val plain = rows.associate { row -> row.id to row.affirmative.joinToString("") { it.text } }

        assertEquals("為る", plain[FormId.NonPast])
        assertEquals("為ました", plain[FormId.PastPolite])
        assertEquals("為せられる", plain[FormId.CausativePassive])
    }
}
