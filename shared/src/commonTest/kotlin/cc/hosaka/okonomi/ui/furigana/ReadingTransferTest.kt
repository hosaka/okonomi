package cc.hosaka.okonomi.ui.furigana

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Carrying a headword's reading onto the form a sentence writes.
 *
 * The dictionary states readings for dictionary forms, and sentences do
 * not use dictionary forms. Nothing else in the app can say what 食べない
 * reads as: [alignReading] divides 食べる against たべる, and this is
 * what decides how much of that division 食べない is entitled to.
 *
 * The failure this guards is not a crash but a lie — a reading placed
 * over characters nobody said it belonged to. ２０歳 with はたち spread
 * over it looks exactly as authoritative as 食べない with た over 食, and
 * only one of them is true.
 */
class ReadingTransferTest {

    private fun rendered(segments: List<FuriganaSegment>): String = segments.joinToString("") { segment ->
        segment.reading?.let { "[${segment.text}[$it]]" } ?: segment.text
    }

    @Test
    fun `a word the sentence writes unchanged keeps the whole alignment`() {
        assertEquals(
            "[果物[くだもの]]",
            rendered(transferReading(word = "果物", reading = "くだもの", surface = "果物")),
        )
    }

    @Test
    fun `a reading carries onto the stem an inflection leaves alone`() {
        // The matrix row: 食べる/たべる seen as 食べない.
        assertEquals(
            "[食[た]]べない",
            rendered(transferReading(word = "食べる", reading = "たべる", surface = "食べない")),
        )
        assertEquals(
            "[食[た]]べます",
            rendered(transferReading(word = "食べる", reading = "たべる", surface = "食べます")),
        )
    }

    @Test
    fun `a surface written in kana takes no reading at all`() {
        // 直ぐに seen as すぐに. The reading すぐに is the headword's, and
        // the surface repeats none of the headword's kanji, so there is
        // nothing to carry and nothing is claimed.
        assertEquals(
            "すぐに",
            rendered(transferReading(word = "直ぐに", reading = "すぐに", surface = "すぐに")),
        )
    }

    /**
     * The surface respells the okurigana and the extra kana is the tail
     * of the reading, so carrying it reads that mora twice: みつも+もり,
     * あらわ+われる, めざま+まし. The characters matched; the division did
     * not survive them.
     */
    @Test
    fun `a surface that respells its okurigana forward takes no reading`() {
        assertEquals(
            "見積もり",
            rendered(transferReading(word = "見積り", reading = "みつもり", surface = "見積もり")),
        )
        assertEquals(
            "現われる",
            rendered(transferReading(word = "現れる", reading = "あらわれる", surface = "現われる")),
        )
        assertEquals(
            "目覚まし時計",
            rendered(transferReading(word = "目覚し時計", reading = "めざましどけい", surface = "目覚まし時計")),
        )
    }

    /**
     * The mirror image: the surface drops okurigana the headword had, so
     * the kanji must absorb it. 分る reads わかる, and 分 takes わか — but
     * the division that produced 分=わ was derived from the かる that is
     * no longer there.
     */
    @Test
    fun `a surface that drops okurigana takes no reading`() {
        assertEquals(
            "分る",
            rendered(transferReading(word = "分かる", reading = "わかる", surface = "分る")),
        )
        // The one-run version of the same error: 話 reads はなし whole, so
        // 話し would read はなし+し.
        assertEquals(
            "話し",
            rendered(transferReading(word = "話", reading = "はなし", surface = "話し")),
        )
    }

    /**
     * A surface continuing into kanji is not an okurigana disagreement.
     * 達 says nothing about how 私 reads, and わたし is still what the
     * dictionary stated for it — this is the commonest shape in the
     * corpus that keeps its ruby under the boundary rule.
     */
    @Test
    fun `a surface that writes the okurigana as kanji keeps its reading`() {
        assertEquals(
            "[私[わたし]]達",
            rendered(transferReading(word = "私たち", reading = "わたしたち", surface = "私達")),
        )
    }

    /**
     * Okurigana that merely differs is not a reason to refuse anything.
     * Inflection rewrites it on nearly every verb in the corpus — 飲む
     * written 飲み, 持つ written 持っている, 高い written 高く — and 行 does
     * read い in all of 行く's rows. Refusing on difference alone cost
     * some seventeen thousand correct readings and caught nothing the
     * two boundary tests above do not.
     */
    @Test
    fun `okurigana that differs without being respelled keeps its reading`() {
        assertEquals("[行[い]]った", rendered(transferReading(word = "行く", reading = "いく", surface = "行った")))
        assertEquals("[飲[の]]み", rendered(transferReading(word = "飲む", reading = "のむ", surface = "飲み")))
        assertEquals("[高[たか]]く", rendered(transferReading(word = "高い", reading = "たかい", surface = "高く")))
        assertEquals(
            "[持[も]]っている",
            rendered(transferReading(word = "持つ", reading = "もつ", surface = "持っている")),
        )
    }

    /**
     * The one shape a pair of spellings cannot judge, stated here so the
     * boundary is explicit rather than assumed. 来る divides as 来=く and
     * 来ない reads こない, and nothing about the strings 来る, くる and
     * 来ない says so — く comes across, and it is wrong.
     *
     * This is why `SentenceFurigana.kt` asks the conjugator first: with
     * the entry's `vk` in hand the same surface renders 来[こ]ない, and
     * that is where the case is really settled. Nothing on the Phrases
     * tab reaches this function for a word whose paradigm is known.
     */
    @Test
    fun `an irregular stem is beyond what two spellings can settle`() {
        assertEquals("[来[く]]ない", rendered(transferReading(word = "来る", reading = "くる", surface = "来ない")))
    }

    @Test
    fun `a surface spelled differently takes no reading`() {
        // The 3.1% that cannot transfer. Every one of these would be a
        // plausible-looking ruby over characters that never had one.
        assertEquals(
            "２０歳",
            rendered(transferReading(word = "二十歳", reading = "はたち", surface = "２０歳")),
        )
        assertEquals(
            "10分",
            rendered(transferReading(word = "十分", reading = "じゅっぷん", surface = "10分")),
        )
        assertEquals(
            "馬鹿が移る",
            rendered(transferReading(word = "バカが移る", reading = "ばかがうつる", surface = "馬鹿が移る")),
        )
    }

    @Test
    fun `an undivided run comes across whole or not at all`() {
        // 相殺 takes そうさい as one unit because nobody can say which of
        // its kanji takes which half. A surface repeating the whole run
        // is entitled to the whole reading...
        assertEquals(
            "[相殺[そうさい]]した",
            rendered(transferReading(word = "相殺", reading = "そうさい", surface = "相殺した")),
        )
        // ...and one repeating part of it gets nothing, rather than the
        // split the alignment declined to make.
        assertEquals(
            "相",
            rendered(transferReading(word = "相殺", reading = "そうさい", surface = "相")),
        )
    }

    @Test
    fun `a run the surface repeats keeps its reading even when more follows`() {
        assertEquals(
            "[大人[おとな]]たち",
            rendered(transferReading(word = "大人", reading = "おとな", surface = "大人たち")),
        )
    }

    @Test
    fun `leading okurigana alone is not a transfer`() {
        // お repeats and 茶 does not, so the only thing that could come
        // across says nothing. One plain run is the honest answer, and
        // it is what the caller can render without special cases.
        assertEquals(
            "お茶",
            rendered(transferReading(word = "お茶漬け", reading = "おちゃづけ", surface = "お茶")),
        )
    }

    @Test
    fun `an empty surface claims nothing`() {
        assertEquals(emptyList(), transferReading(word = "食べる", reading = "たべる", surface = ""))
    }

    @Test
    fun `the segments always spell the surface back exactly`() {
        // The invariant the sentence renderer depends on: the line is
        // drawn from these, and a transfer that dropped or invented a
        // character would rewrite the sentence.
        listOf(
            Triple("食べる", "たべる", "食べない"),
            Triple("二十歳", "はたち", "２０歳"),
            Triple("直ぐに", "すぐに", "すぐに"),
            Triple("果物", "くだもの", "果物"),
            Triple("大人", "おとな", "大人たち"),
            Triple("見積り", "みつもり", "見積もり"),
            Triple("分かる", "わかる", "分る"),
            Triple("私たち", "わたしたち", "私達"),
        ).forEach { (word, reading, surface) ->
            assertEquals(
                surface,
                transferReading(word, reading, surface).plainText(),
                "$word/$reading over $surface",
            )
        }
    }
}
