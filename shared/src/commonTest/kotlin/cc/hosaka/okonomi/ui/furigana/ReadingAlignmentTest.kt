package cc.hosaka.okonomi.ui.furigana

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The alignment rules, stated in the notation the readings would be
 * written in if the dictionary carried them: `[食[た]]べる` is 食 with た
 * over it followed by plain べる.
 *
 * Measured over the shipped dictionary — all 177,412 primary kanji
 * forms against each entry's first reading — this engine divides 31.2%
 * of them at their kana (55,363), gives 68.6% a whole-word reading
 * (121,760: a single run of kanji with nothing to divide it), and falls
 * back on 0.16% (289 words). The fallback rate is the number that
 * matters: it is where there were kana to anchor on and they did not
 * settle the split. What is left there is genuine ambiguity — 五つ子 is
 * いつ+つ+ご or い+つ+つご — and a handful of words whose reading simply
 * disagrees with their spelling (茹でタコ/ゆでだこ, where the reading
 * voices the タ).
 */
class ReadingAlignmentTest {

    private fun aligned(word: String, reading: String): String =
        alignReading(word, reading).joinToString("") { segment ->
            if (segment.reading == null) segment.text else "[${segment.text}[${segment.reading}]]"
        }

    @Test
    fun `trailing okurigana leaves only the kanji annotated`() {
        assertEquals("[食[た]]べる", aligned("食べる", "たべる"))
    }

    @Test
    fun `a run of kanji with no okurigana takes the whole reading`() {
        assertEquals("[相殺[そうさい]]", aligned("相殺", "そうさい"))
    }

    /**
     * The case the whole fallback exists for. 大人 is おとな with nothing
     * to divide it on, and お/と/な cannot be handed to 大 and 人 without
     * inventing readings neither character has.
     */
    @Test
    fun `an irregular reading is annotated whole rather than split`() {
        assertEquals("[大人[おとな]]", aligned("大人", "おとな"))
        assertEquals("[一日[ついたち]]", aligned("一日", "ついたち"))
    }

    @Test
    fun `a kana word carries no furigana at all`() {
        assertEquals("たべる", aligned("たべる", "たべる"))
    }

    @Test
    fun `kana inside the word is matched in place and the kanji takes the rest`() {
        assertEquals("[為[な]]さる", aligned("為さる", "なさる"))
        assertEquals("お[好[この]]み[焼[や]]き", aligned("お好み焼き", "おこのみやき"))
        assertEquals("[立[た]]ち[上[あ]]がる", aligned("立ち上がる", "たちあがる"))
    }

    @Test
    fun `a word that opens with kanji and closes with kana still divides`() {
        assertEquals("[額[がく]]が[少[すく]]ない", aligned("額が少ない", "がくがすくない"))
    }

    /**
     * The trailing okurigana is anchored at the end of the reading, not
     * searched for from the front. 意味合い ends in い and so does the
     * *start* of いみあい; a forward search finds that one, hands 意味合
     * an empty reading and collapses the word.
     */
    @Test
    fun `okurigana that also opens the reading is anchored at the end`() {
        assertEquals("[意味合[いみあ]]い", aligned("意味合い", "いみあい"))
    }

    /**
     * ヶ is written like a small katakana ke and read か, が or こ. Read
     * as kana it would look for itself in the reading, never find it,
     * and take the word down with it; counted as needing a reading it
     * simply joins the run beside it.
     */
    @Test
    fun `the small ke is not treated as kana that reads itself`() {
        assertEquals("[何ヶ月[なんかげつ]]", aligned("何ヶ月", "なんかげつ"))
        assertEquals("[霞ヶ関[かすみがせき]]", aligned("霞ヶ関", "かすみがせき"))
    }

    @Test
    fun `katakana matches the hiragana of the reading and a long mark its vowel`() {
        assertEquals("ドイツ[語[ご]]", aligned("ドイツ語", "ドイツご"))
        assertEquals("プー[太郎[たろう]]", aligned("プー太郎", "ぷうたろう"))
    }

    @Test
    fun `several kanji runs each take the reading between their kana`() {
        assertEquals("[火[ひ]]の[粉[こ]]", aligned("火の粉", "ひのこ"))
        assertEquals("[上[あ]]がり[下[さ]]がり", aligned("上がり下がり", "あがりさがり"))
    }

    /**
     * いきさき carries two き, and the second one cannot be the
     * okurigana of 行き because it would leave 先 with no reading at
     * all. Carrying each candidate through to the end, rather than
     * giving up on the first repeated anchor, is what turns that from
     * an ambiguity into an answer.
     */
    @Test
    fun `a repeated anchor that leaves a later kanji empty is not a candidate`() {
        assertEquals("[行[い]]き[先[さき]]", aligned("行き先", "いきさき"))
        assertEquals("[思[おも]]い[入[い]]れ", aligned("思い入れ", "おもいいれ"))
    }

    /**
     * When two divisions both survive, the reading genuinely does not
     * say which is right — 五つ子 is いつ+つ+ご, but い+つ+つご divides it
     * just as well — so nothing is claimed.
     */
    @Test
    fun `an ambiguous interior anchor falls back to the whole word`() {
        assertEquals("[五つ子[いつつご]]", aligned("五つ子", "いつつご"))
        assertEquals("[好き嫌い[すききらい]]", aligned("好き嫌い", "すききらい"))
    }

    @Test
    fun `a reading that cannot be matched annotates the whole word`() {
        // The reading disagrees with the okurigana it would have to
        // anchor on, so there is nothing to divide on.
        assertEquals("[食べる[くう]]", aligned("食べる", "くう"))
    }

    @Test
    fun `an empty reading leaves the word plain`() {
        assertEquals("食べる", aligned("食べる", ""))
    }

    /**
     * A division can line the kana up and still be impossible: っつら
     * opens on a sokuon, which continues the mora before it and so
     * cannot open a reading. Two shipped words divide that way, and both
     * are better off with the coarse ruby.
     */
    @Test
    fun `a run reading that could not be read is refused`() {
        assertEquals("[泣き面[なきっつら]]", aligned("泣き面", "なきっつら"))
    }

    @Test
    fun `a sokuon that genuinely belongs to the word is still matched in place`() {
        assertEquals("[真[ま]]っ[赤[か]]", aligned("真っ赤", "まっか"))
        assertEquals("[引[ひ]]っ[越[こ]]し", aligned("引っ越し", "ひっこし"))
    }

    /**
     * The invariant callers actually depend on. Exact spelling holds for
     * the word; for the reading only the length does, because a kana run
     * that matched after folding keeps its own spelling — プー太郎 says
     * プーたろう back. `titleFurigana` maps highlight offsets through
     * these, and offsets need the length.
     */
    @Test
    fun `the segments spell the word back and preserve the reading's length`() {
        val cases = listOf(
            "食べる" to "たべる",
            "お好み焼き" to "おこのみやき",
            "大人" to "おとな",
            "為さる" to "なさる",
            "額が少ない" to "がくがすくない",
            "ドイツ語" to "ドイツご",
            "プー太郎" to "ぷうたろう",
            "サボる" to "さぼる",
            "五つ子" to "いつつご",
        )
        cases.forEach { (word, reading) ->
            val segments = alignReading(word, reading)
            assertEquals(word, segments.joinToString("") { it.text }, word)
            assertEquals(
                reading.length,
                segments.sumOf { (it.reading ?: it.text).length },
                "$word reads $reading",
            )
        }
    }

    @Test
    fun `a folded kana run keeps its own spelling rather than the reading's`() {
        assertEquals(
            listOf(FuriganaSegment("プー"), FuriganaSegment("太郎", "たろう")),
            alignReading("プー太郎", "ぷうたろう"),
        )
    }

    /**
     * The mark stands for the vowel of the mora before it, not for any
     * vowel: ソース is そうす only if ー may be read う after ソ, which it
     * may not.
     */
    @Test
    fun `a long mark matches only the vowel it lengthens`() {
        // ー after ソ is お, so そおす reads it and そうす does not.
        assertEquals("ソース", aligned("ソース", "そおす"))
        assertEquals("[ソース[そうす]]", aligned("ソース", "そうす"))
    }
}
