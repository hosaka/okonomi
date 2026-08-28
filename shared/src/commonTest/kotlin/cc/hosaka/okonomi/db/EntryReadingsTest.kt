package cc.hosaka.okonomi.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which reading is set over the headword, and which are left for the
 * Reading section to list.
 *
 * The rule is what keeps the ruby honest: a reading JMdict states for
 * some other spelling, or for no spelling at all, is not a reading of
 * this headword, and drawn over it asserts something the dictionary
 * denies. It is stated here rather than in a UI test because it is a
 * fact about the entry — and because a UI test on Android cannot see a
 * ruby at all (see `RubyRenderingTest`).
 */
class EntryReadingsTest {

    private fun entry(
        headword: String = "相殺",
        forms: List<String> = listOf(headword),
        readings: List<EntryReading>,
    ) = EntryDetail(
        entryId = 1L,
        headword = headword,
        forms = forms.map { EntryForm(it, isCommon = false) },
        readings = readings,
        senses = emptyList(),
        isCommon = false,
        commonRank = 1L,
        hasSentences = true,
    )

    private fun reading(
        text: String,
        restrictions: List<String> = emptyList(),
        noKanji: Boolean = false,
    ) = EntryReading(text = text, restrictions = restrictions, isCommon = false, noKanji = noKanji)

    @Test
    fun `the first reading is set over the headword and is not listed again`() {
        val entry = entry(readings = listOf(reading("そうさい"), reading("そうさつ")))

        assertEquals("そうさい", entry.headwordReading?.text)
        assertEquals(listOf("そうさつ"), entry.otherReadings.map { it.text })
    }

    @Test
    fun `an entry with one reading has nothing left to list`() {
        val entry = entry(readings = listOf(reading("そうさい")))

        assertEquals("そうさい", entry.headwordReading?.text)
        assertEquals(emptyList(), entry.otherReadings)
    }

    /**
     * 叢立ち and 総立ち share an entry, and そうだち is stated for 総立ち
     * alone. Over 叢立ち it would read 叢立 as そうだ.
     */
    @Test
    fun `a reading restricted to another spelling is not the headword's`() {
        val entry = entry(
            headword = "叢立ち",
            forms = listOf("叢立ち", "総立ち"),
            readings = listOf(
                reading("そうだち", restrictions = listOf("総立ち")),
                reading("むらだち", restrictions = listOf("叢立ち")),
            ),
        )

        assertEquals("むらだち", entry.headwordReading?.text)
        assertEquals(listOf("そうだち"), entry.otherReadings.map { it.text })
    }

    /**
     * `re_nokanji` says the reading belongs to no written form at all —
     * 刻々's ギザギザ, where the kanji themselves read こくこく.
     */
    @Test
    fun `a re_nokanji reading is never set over the kanji`() {
        val entry = entry(
            headword = "刻々",
            readings = listOf(reading("ギザギザ", noKanji = true), reading("こくこく")),
        )

        assertEquals("こくこく", entry.headwordReading?.text)
        assertEquals(listOf("ギザギザ"), entry.otherReadings.map { it.text })
    }

    /**
     * A word written in kana is its own reading, and there is no written
     * form for a restriction to be against — `re_nokanji` on the only
     * reading of such an entry must not leave the headword unreadable.
     */
    @Test
    fun `a kana headword takes its own reading whatever the flags say`() {
        val entry = entry(
            headword = "ラーメン",
            forms = emptyList(),
            readings = listOf(reading("ラーメン", noKanji = true)),
        )

        assertEquals("ラーメン", entry.headwordReading?.text)
        assertEquals(emptyList(), entry.otherReadings)
    }

    @Test
    fun `an entry stating no reading for its headword gets none and lists them all`() {
        val entry = entry(
            headword = "叢立ち",
            forms = listOf("叢立ち", "総立ち"),
            readings = listOf(reading("そうだち", restrictions = listOf("総立ち"))),
        )

        assertNull(entry.headwordReading)
        assertEquals(listOf("そうだち"), entry.otherReadings.map { it.text })
    }
}
