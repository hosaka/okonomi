package cc.hosaka.okonomi.ui.furigana

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Dividing a run of kanji per character on kanjidic's evidence, stated in
 * the same notation as `ReadingAlignmentTest`: `[動[どう]][物[ぶつ]]` is
 * two units, `[大人[おとな]]` one.
 *
 * Every case runs through [alignReading] with the readings of
 * [kanjidicFixture] — real kanjidic rows — so what is asserted is what a
 * screen would be handed. Where the kanji are drawn over their kana is a
 * layout matter Robolectric cannot see (it lays every glyph out to zero
 * width); that is checked on a device only.
 *
 * Measured over the shipped dictionary's single-run kanji forms against
 * their first reading (116,000-odd words): about 108,700 divide, about
 * 7,300 have no partition and stay whole, three meet a character with no
 * readings, and six are ambiguous — all of them 合気 compounds plus 杜氏
 * and 木曽馬.
 */
class KanjiReadingsTest {

    private fun aligned(word: String, reading: String, readings: KanjiReadings? = kanjidicFixture): String =
        alignReading(word, reading, readings).joinToString("") { segment ->
            if (segment.reading == null) segment.text else "[${segment.text}[${segment.reading}]]"
        }

    @Test
    fun `a run its readings divide one way only is divided per kanji`() {
        assertEquals("[動[どう]][物[ぶつ]][園[えん]]", aligned("動物園", "どうぶつえん"))
        assertEquals("[相[そう]][殺[さい]][関[かん]][税[ぜい]]", aligned("相殺関税", "そうさいかんぜい"))
    }

    @Test
    fun `a kanji after the first may take its reading voiced`() {
        assertEquals("[手[て]][紙[がみ]]", aligned("手紙", "てがみ"))
        // ち and つ voice to ぢ and づ, not to じ and ず: 月 is つき, so
        // 三日月's づき is its voiced form.
        assertEquals("[三[み]][日[か]][月[づき]]", aligned("三日月", "みかづき"))
    }

    @Test
    fun `the first kanji never takes a voiced reading`() {
        // 紙 reads かみ; がみ is its voiced form and only a later kanji
        // may take that. Leading the word it is no reading at all.
        assertEquals("[紙手[がみて]]", aligned("紙手", "がみて"))
    }

    @Test
    fun `a kanji before the last may close its reading into a sokuon`() {
        assertEquals("[学[がっ]][校[こう]]", aligned("学校", "がっこう"))
        // Both bends at once: 一 closes and 杯 takes the half-voiced ぱい.
        assertEquals("[一[いっ]][杯[ぱい]]", aligned("一杯", "いっぱい"))
    }

    @Test
    fun `the last kanji never closes into a sokuon`() {
        // 学 is がく; がっ is only what it reads before another kanji.
        assertEquals("[校学[こうがっ]]", aligned("校学", "こうがっ"))
    }

    @Test
    fun `an iteration mark reads as the kanji before it`() {
        assertEquals("[人[ひと]][々[びと]]", aligned("人々", "ひとびと"))
    }

    @Test
    fun `an iteration mark with nothing before it leaves the run whole`() {
        assertNull(kanjidicFixture.divide("々人", "ひとひと"))
    }

    @Test
    fun `a jukujikun reading stays whole`() {
        assertEquals("[大人[おとな]]", aligned("大人", "おとな"))
        assertEquals("[刑事[でか]]", aligned("刑事", "でか"))
    }

    @Test
    fun `a run its readings divide two ways stays whole`() {
        // あい+き or あ+いき: 合 reads both あい and あ, 気 both き and いき.
        assertEquals("[合気[あいき]]", aligned("合気", "あいき"))
        // き+そう+ま or き+そ+うま.
        assertEquals("[木曽馬[きそうま]]", aligned("木曽馬", "きそうま"))
    }

    /**
     * 大 is read た as a name, and 気 has the kun いき, so with nanori
     * counted 大気 would read た+いき as well as たい+き and stay whole.
     * Only on and kun count, and たい+き is the one division.
     */
    @Test
    fun `name readings are never candidates`() {
        assertEquals("[大[たい]][気[き]]", aligned("大気", "たいき"))
        assertNull(candidateOf("nanori", "た"))
    }

    @Test
    fun `a character with no readings leaves the whole run whole`() {
        // ヶ reads か here, but kanjidic does not describe it.
        assertEquals("[一ヶ月[いっかげつ]]", aligned("一ヶ月", "いっかげつ"))
        // A kanji the readings were never loaded for.
        assertEquals("[葡萄[ぶどう]]", aligned("葡萄", "ぶどう"))
    }

    @Test
    fun `kana anchors are kept and only the runs between them are divided`() {
        assertEquals("[手[て]][紙[がみ]][入[い]]れ", aligned("手紙入れ", "てがみいれ"))
        // Runs of one kanji only: the anchors are as they were, and
        // there is nothing for the readings to divide.
        assertEquals(aligned("取り扱い", "とりあつかい", null), aligned("取り扱い", "とりあつかい"))
        assertEquals(aligned("食べ物", "たべもの", null), aligned("食べ物", "たべもの"))
    }

    @Test
    fun `without readings every run stays whole`() {
        assertEquals("[動物園[どうぶつえん]]", aligned("動物園", "どうぶつえん", readings = null))
        assertEquals("[学校[がっこう]]", aligned("学校", "がっこう", readings = null))
    }

    @Test
    fun `a katakana reading divides on its folded kana and keeps its own`() {
        assertEquals("[動[ドウ]][物[ブツ]]", aligned("動物", "ドウブツ"))
    }

    /**
     * The two invariants `alignReading` promises callers, which the
     * search highlight's offset arithmetic relies on: the segments spell
     * the word back exactly, and their readings are the reading's length.
     */
    @Test
    fun `divided segments spell the word and keep the reading's length`() {
        listOf(
            "動物園" to "どうぶつえん",
            "手紙入れ" to "てがみいれ",
            "学校" to "がっこう",
            "人々" to "ひとびと",
            "三日月" to "みかづき",
        ).forEach { (word, reading) ->
            val segments = alignReading(word, reading, kanjidicFixture)
            assertEquals(word, segments.plainText())
            assertEquals(reading, segments.joinToString("") { it.reading ?: it.text })
        }
    }

    @Test
    fun `kanjidic notation is reduced to what the kanji itself reads`() {
        assertEquals("た", candidateOf("kun", "た.べる"))
        assertEquals("か", candidateOf("kun", "-か"))
        assertEquals("おお", candidateOf("kun", "おお-"))
        assertEquals("おお", candidateOf("kun", "-おお.いに"))
        assertEquals("がく", candidateOf("on", "ガク"))
        assertNull(candidateOf("kun", "-"))
        assertNull(candidateOf("pinyin", "xue2"))
    }

    /**
     * Renjō that kanjidic does not list stays whole rather than divided
     * wrongly: 皇 is こう or おう, never のう, so 天皇 has no division.
     * Half-voicing after ん is not such a limit — 散歩 divides.
     */
    @Test
    fun `a sound change no candidate spells leaves the run whole`() {
        assertEquals("[天皇[てんのう]]", aligned("天皇", "てんのう"))
        assertEquals("[散[さん]][歩[ぽ]]", aligned("散歩", "さんぽ"))
    }

    /**
     * The worst case for the search: every character can take one kana
     * or two, so every way of spending a reading is a candidate path,
     * and the last kana fits none of them. Searched without remembering
     * where it has been, that is a Fibonacci number of paths — this
     * test would not finish. The readings are invented to force that
     * shape; no kanjidic character has it quite so cleanly.
     */
    @Test
    fun `a long run with no division is given up on without trying every path`() {
        val readings = KanjiReadings.Builder()
            .add("亜", "on", "ア")
            .add("亜", "kun", "ああ")
            .build()
        val run = "亜".repeat(80)

        assertNull(readings.divide(run, "あ".repeat(120) + "い"))
        assertNull(readings.divide(run, "あ".repeat(120)), "many divisions is no division")
    }
}
