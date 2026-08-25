package cc.hosaka.okonomi.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One case per covered class, seeded from the headword the dictionary
 * actually ships — 有る rather than ある, 為る rather than する, 居らっしゃる
 * rather than いらっしゃる — because a hand-picked kana base tests the
 * paradigm against its author's assumptions instead of against the
 * data. [ConjugationCorpusTest] sweeps far wider; this file is where
 * each class's rows are stated in full and where the deinflector's own
 * gaps are named one cell at a time.
 *
 * Every cell either has a derivation back to its base that names the
 * transform its row is called after (see `ConjugationOracle`), or is
 * listed in the case's [Case.blind] map with the exact text it should
 * have. The map is the exception list and the correctness assertion at
 * once, on purpose: `assertFalse(roundTrips)` alone gets easier to
 * satisfy the worse the text is, and passed happily for a prohibitive
 * rendered 食べるぬ.
 */
class ConjugationRoundTripTest {

    /**
     * [blind] names the cells the ported rule set cannot reach, each
     * with the text it must nonetheless have. A cell absent from it is
     * required to derive.
     */
    private data class Case(
        val base: String,
        val code: String,
        val blind: Map<Cell, String> = emptyMap(),
    )

    private fun blind(vararg cells: Pair<Cell, String>) = cells.toMap()

    private infix fun FormId.aff(text: String) = Cell(this, Polarity.Affirmative) to text

    private infix fun FormId.neg(text: String) = Cell(this, Polarity.Negative) to text

    private val cases = listOf(
        Case("食べる", "v1", blind(FormId.Imperative neg "食べるな")),
        // くれる's imperative is 呉れ, which the rule set reaches only
        // along the continuative rule (れ ← れる); no derivation of it
        // names the imperative transform its row is called after.
        Case(
            base = "呉れる",
            code = "v1-s",
            blind = blind(
                FormId.Imperative aff "呉れ",
                FormId.Imperative neg "呉れるな",
            ),
        ),
        Case("買う", "v5u", blind(FormId.Imperative neg "買うな")),
        Case("書く", "v5k", blind(FormId.Imperative neg "書くな")),
        Case("泳ぐ", "v5g", blind(FormId.Imperative neg "泳ぐな")),
        Case("話す", "v5s", blind(FormId.Imperative neg "話すな")),
        Case("待つ", "v5t", blind(FormId.Imperative neg "待つな")),
        Case("死ぬ", "v5n", blind(FormId.Imperative neg "死ぬな")),
        Case("遊ぶ", "v5b", blind(FormId.Imperative neg "遊ぶな")),
        Case("読む", "v5m", blind(FormId.Imperative neg "読むな")),
        Case("走る", "v5r", blind(FormId.Imperative neg "走るな")),
        Case("行く", "v5k-s", blind(FormId.Imperative neg "行くな")),
        // The old uru class, which conjugates as an ichidan verb and is
        // mapped to the ichidan condition flag to match.
        Case("得る", "v5uru", blind(FormId.Imperative neg "得るな")),
        // 問う is on upstream's list of う verbs with て/た forms in う;
        // 厭う is tagged v5u-s and is not, which is why the corpus sweep
        // treats the whole class's て/た as unreachable and this case
        // does not.
        Case(
            base = "問う",
            code = "v5u-s",
            blind = blind(
                FormId.Imperative neg "問うな",
            ),
        ),
        // The headword the dictionary ships for ある, in kanji. Its
        // negatives are forms of ない, a separate entry, so nothing
        // leads back from them; its polite negative is regular.
        Case(
            base = "有る",
            code = "v5r-i",
            blind = blind(
                FormId.NonPast neg "ない",
                FormId.Past neg "なかった",
                FormId.Te neg "なくて",
                FormId.ConditionalBa neg "なければ",
                FormId.ConditionalTara neg "なかったら",
            ),
        ),
        // An expression built on ある negates on the same tail.
        Case(
            base = "花も実も有る",
            code = "v5r-i",
            blind = blind(
                FormId.NonPast neg "花も実もない",
                FormId.Past neg "花も実もなかった",
                FormId.Te neg "花も実もなくて",
                FormId.ConditionalBa neg "花も実もなければ",
                FormId.ConditionalTara neg "花も実もなかったら",
            ),
        ),
        // The v5aru headword as shipped: upstream's honorific polite
        // forms are whole-word rules listing いらっしゃいます literally, so
        // the kanji spelling has nothing to match.
        Case(
            base = "居らっしゃる",
            code = "v5aru",
            blind = blind(
                FormId.NonPastPolite aff "居らっしゃいます",
                FormId.NonPastPolite neg "居らっしゃいません",
                FormId.PastPolite aff "居らっしゃいました",
                FormId.PastPolite neg "居らっしゃいませんでした",
                FormId.Imperative aff "居らっしゃい",
                FormId.Imperative neg "居らっしゃるな",
            ),
        ),
        // 下さる is on upstream's list, so its four polite rows do come
        // back — which is what keeps the carve-out above honest. Its
        // imperative is ください, the everyday word, and no rule reaches
        // it: the imperative transform knows only 下され for this shape.
        Case(
            base = "下さる",
            code = "v5aru",
            blind = blind(
                FormId.Imperative aff "下さい",
                FormId.Imperative neg "下さるな",
            ),
        ),
        Case("来る", "vk", blind(FormId.Imperative neg "来るな")),
        Case("ピンと来る", "vk", blind(FormId.Imperative neg "ピンと来るな")),
        Case("持ってくる", "vk", blind(FormId.Imperative neg "持ってくるな")),
        // JMdict's headword for する is 為る, and six of this table's
        // rows are spelled with 為 rather than し.
        Case(
            base = "為る",
            code = "vs-i",
            blind = blind(
                FormId.Potential aff "出来る",
                FormId.Potential neg "出来ない",
                FormId.Imperative neg "為るな",
            ),
        ),
        Case("物にする", "vs-i", blind(FormId.Imperative neg "物にするな")),
        // The 漢語 special class takes 愛せる, not 愛できる. Upstream's
        // する rules know only できる/出来る, so the correct form has no way
        // back — writing 愛できる to satisfy the oracle would be teaching
        // a mistake.
        Case(
            base = "愛する",
            code = "vs-s",
            blind = blind(
                FormId.Potential aff "愛せる",
                FormId.Potential neg "愛せない",
                FormId.Imperative neg "愛するな",
            ),
        ),
        // The precursor class written the older way, as a plain す verb.
        Case("兼す", "vs-c", blind(FormId.Imperative neg "兼すな")),
        Case("信ずる", "vz", blind(FormId.Imperative neg "信ずるな")),
        Case(
            base = "高い",
            code = "adj-i",
            blind = blind(
                FormId.NonPastPolite aff "高いです",
                FormId.NonPastPolite neg "高くないです",
                FormId.PastPolite aff "高かったです",
                FormId.PastPolite neg "高くなかったです",
            ),
        ),
        // A katakana headword takes hiragana inflections, which is what
        // people write and what the rule set cannot reverse: it appends
        // a hiragana い and lands on ウザい, never on ウザイ.
        Case(
            base = "ウザイ",
            code = "adj-i",
            blind = blind(
                FormId.NonPast neg "ウザくない",
                FormId.NonPastPolite aff "ウザイです",
                FormId.NonPastPolite neg "ウザくないです",
                FormId.Past aff "ウザかった",
                FormId.Past neg "ウザくなかった",
                FormId.PastPolite aff "ウザかったです",
                FormId.PastPolite neg "ウザくなかったです",
                FormId.Te aff "ウザくて",
                FormId.Te neg "ウザくなくて",
                FormId.ConditionalBa aff "ウザければ",
                FormId.ConditionalBa neg "ウザくなければ",
                FormId.ConditionalTara aff "ウザかったら",
                FormId.ConditionalTara neg "ウザくなかったら",
            ),
        ),
        // An auxiliary adjective inflects exactly as adj-i does.
        Case(
            base = "たい",
            code = "aux-adj",
            blind = blind(
                FormId.NonPastPolite aff "たいです",
                FormId.NonPastPolite neg "たくないです",
                FormId.PastPolite aff "たかったです",
                FormId.PastPolite neg "たくなかったです",
            ),
        ),
        // いい borrows the よ of よい: every inflected cell is spelled
        // with a stem the headword does not contain, so none of them
        // lead back to it.
        Case(
            base = "気持ちいい",
            code = "adj-ix",
            blind = blind(
                FormId.NonPast neg "気持ちよくない",
                FormId.NonPastPolite aff "気持ちいいです",
                FormId.NonPastPolite neg "気持ちよくないです",
                FormId.Past aff "気持ちよかった",
                FormId.Past neg "気持ちよくなかった",
                FormId.PastPolite aff "気持ちよかったです",
                FormId.PastPolite neg "気持ちよくなかったです",
                FormId.Te aff "気持ちよくて",
                FormId.Te neg "気持ちよくなくて",
                FormId.ConditionalBa aff "気持ちよければ",
                FormId.ConditionalBa neg "気持ちよくなければ",
                FormId.ConditionalTara aff "気持ちよかったら",
                FormId.ConditionalTara neg "気持ちよくなかったら",
            ),
        ),
        // The same class written with the kanji stem needs no such
        // help, and comes back cleanly.
        Case(
            base = "居心地の良い",
            code = "adj-ix",
            blind = blind(
                FormId.NonPastPolite aff "居心地の良いです",
                FormId.NonPastPolite neg "居心地の良くないです",
                FormId.PastPolite aff "居心地の良かったです",
                FormId.PastPolite neg "居心地の良くなかったです",
            ),
        ),
    )

    @Test
    fun `every cell either derives back to its base or is a named exception with a pinned text`() {
        for (case in cases) {
            val forms = conjugate(case.base, case.code)
            assertTrue(forms.isNotEmpty(), "${case.base} (${case.code}) produced no forms at all")
            val seen = mutableSetOf<Cell>()
            for (form in forms) {
                for ((cell, text) in cellsOf(form)) {
                    seen += cell
                    // The non-past affirmative is the base itself, and
                    // the identity candidate deinflects to it with
                    // conditions 0, which match anything. There is no
                    // assertion here that could fail; the text is
                    // checked instead.
                    if (cell == Cell(FormId.NonPast, Polarity.Affirmative)) {
                        assertEquals(case.base, text, "the non-past affirmative is the headword itself")
                        continue
                    }
                    val expected = case.blind[cell]
                    if (expected != null) {
                        assertEquals(expected, text, "${case.base} (${case.code}) $cell")
                        assertNull(
                            derivationOf(text, case.base, case.code, cell),
                            "$text (${case.base}, ${case.code}, $cell) now derives back: the rule set has grown " +
                                "a way home, so this exception is stale and should go",
                        )
                    } else {
                        assertNotNull(
                            derivationOf(text, case.base, case.code, cell),
                            "$text (${case.base}, ${case.code}, $cell) has no derivation naming its own transform",
                        )
                    }
                }
            }
            assertEquals(
                emptySet(),
                case.blind.keys - seen,
                "${case.base} (${case.code}) names exceptions for cells it does not produce",
            )
        }
    }

    @Test
    fun `every covered class is seeded from a headword the dictionary ships`() {
        val covered = cases.mapNotNull { conjugationClassOf(it.code, it.base) }.toSet()
        assertEquals(
            ConjugationClass.entries.toSet(),
            covered,
            "a paradigm with no seeded case is a paradigm nothing states in full",
        )
        val shipped = conjugationCorpus.map { it.headword }.toSet()
        val invented = cases.map { it.base }.filterNot { it in shipped }
        assertEquals(emptyList(), invented, "these bases are not headwords in the shipped dictionary")
    }
}
