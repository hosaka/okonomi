package cc.hosaka.okonomi.feature.forms

import cc.hosaka.okonomi.lang.FormId
import cc.hosaka.okonomi.lang.conjugations
import cc.hosaka.okonomi.ui.furigana.FuriganaSegment
import cc.hosaka.okonomi.ui.furigana.KanjiReadings
import cc.hosaka.okonomi.ui.furigana.kanjidicFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * When a conjugation table earns furigana, and when a ruby would only
 * repeat what the row above already said.
 */
class ConjugationFuriganaTest {

    private fun rows(
        base: String,
        reading: String?,
        code: String,
        kanjiReadings: KanjiReadings? = null,
    ): List<ConjugationRow> {
        val conjugation = conjugations(base, listOf(code)).single()
        return conjugationRows(conjugation, reading, kanjiReadings)
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

    /**
     * 頭来る/あたまくる writes its shifting 来 in one run with 頭. Kept
     * whole, the run varies as a unit and every row carries あたまく,
     * あたまき or あたまこ over both kanji. Divided, 頭 reads あたま on
     * every row and drops its ruby by the rule a constant stem always
     * did, and only 来 is annotated — which is what actually shifts.
     */
    @Test
    fun `a divided stem keeps the ruby only on the kanji that shifts`() {
        val whole = rows("頭来る", "あたまくる", "vk")
        assertEquals(
            listOf(FuriganaSegment("頭来", "あたまこ"), FuriganaSegment("ない")),
            whole.single { it.id == FormId.NonPast }.negative,
        )

        val divided = rows("頭来る", "あたまくる", "vk", kanjidicFixture)
        assertEquals(
            listOf(FuriganaSegment("頭"), FuriganaSegment("来", "こ"), FuriganaSegment("ない")),
            divided.single { it.id == FormId.NonPast }.negative,
        )
        assertEquals(
            listOf(FuriganaSegment("頭"), FuriganaSegment("来", "く"), FuriganaSegment("る")),
            affirmative(divided, FormId.NonPast),
        )
    }

    /**
     * A stem that divides on some rows and not on others. 為 reads す in
     * kanjidic but never し or さ, so 頭為る's あたまする divides as
     * あたま+す while 頭為ない's あたましない has no division. Compared as
     * they come, the divided rows would say 為 never shifts and the whole
     * ones would vary as 頭為. The table falls back to undivided instead,
     * exactly as without the readings. (A constructed verb: no shipped
     * one we found has this shape, but nothing in the rule depends on
     * which verb it is.)
     */
    @Test
    fun `a stem that divides on only some rows leaves every row undivided`() {
        assertEquals(
            rows("頭為る", "あたまする", "vs-i"),
            rows("頭為る", "あたまする", "vs-i", kanjidicFixture),
        )
    }

    /**
     * A stem writing the same kanji twice with two readings: 日日 is
     * ひ+び on every row. Pooled by the character alone, 日 would read
     * both ひ and び and look like a reading that shifts; kept apart by
     * where each sits, both are constant and only 来 is annotated.
     * (Constructed, like the case above.)
     */
    @Test
    fun `a kanji written twice in a stem is compared with itself in each place`() {
        val rows = rows("日日来る", "ひびくる", "vk", kanjidicFixture)

        assertEquals(
            listOf(FuriganaSegment("日日"), FuriganaSegment("来", "こ"), FuriganaSegment("ない")),
            rows.single { it.id == FormId.NonPast }.negative,
        )
    }
}
