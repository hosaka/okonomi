package cc.hosaka.okonomi.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConjugatorTest {

    private fun forms(base: String, code: String): Map<FormId, Pair<String, String?>> =
        conjugate(base, code).associate { it.id to (it.affirmative to it.negative) }

    @Test
    fun `an ichidan verb produces every row of the table`() {
        assertEquals(
            listOf(
                Form(FormId.NonPast, "食べる", "食べない"),
                Form(FormId.NonPastPolite, "食べます", "食べません"),
                Form(FormId.Past, "食べた", "食べなかった"),
                Form(FormId.PastPolite, "食べました", "食べませんでした"),
                Form(FormId.Te, "食べて", "食べなくて"),
                Form(FormId.Potential, "食べられる", "食べられない"),
                Form(FormId.Passive, "食べられる", "食べられない"),
                Form(FormId.Causative, "食べさせる", "食べさせない"),
                Form(FormId.CausativePassive, "食べさせられる", "食べさせられない"),
                Form(FormId.Imperative, "食べろ", "食べるな"),
                Form(FormId.Volitional, "食べよう", null),
                Form(FormId.ConditionalBa, "食べれば", "食べなければ"),
                Form(FormId.ConditionalTara, "食べたら", "食べなかったら"),
                Form(FormId.Desiderative, "食べたい", "食べたくない"),
            ),
            conjugate("食べる", "v1"),
        )
    }

    @Test
    fun `the volitional has no negative cell`() {
        // 〜まい is literary and 食べないだろう is a paraphrase; an empty
        // cell says "no such form" where either would mislead.
        val volitional = conjugate("食べる", "v1").single { it.id == FormId.Volitional }
        assertNull(volitional.negative)
        assertEquals("行こう", forms("行く", "v5k-s")[FormId.Volitional]?.first)
        assertEquals("為よう", forms("為る", "vs-i")[FormId.Volitional]?.first)
        assertEquals("来よう", forms("来る", "vk")[FormId.Volitional]?.first)
        assertEquals("信じよう", forms("信ずる", "vz")[FormId.Volitional]?.first)
    }

    @Test
    fun `the volitional takes the o row of the final kana`() {
        assertEquals("買おう" to null, forms("買う", "v5u")[FormId.Volitional])
        assertEquals("書こう" to null, forms("書く", "v5k")[FormId.Volitional])
        assertEquals("泳ごう" to null, forms("泳ぐ", "v5g")[FormId.Volitional])
        assertEquals("話そう" to null, forms("話す", "v5s")[FormId.Volitional])
        assertEquals("待とう" to null, forms("待つ", "v5t")[FormId.Volitional])
        assertEquals("死のう" to null, forms("死ぬ", "v5n")[FormId.Volitional])
        assertEquals("遊ぼう" to null, forms("遊ぶ", "v5b")[FormId.Volitional])
        assertEquals("読もう" to null, forms("読む", "v5m")[FormId.Volitional])
        assertEquals("走ろう" to null, forms("走る", "v5r")[FormId.Volitional])
    }

    @Test
    fun `the conditionals and the desiderative follow the same stems as the rest`() {
        // Every base ending in る takes れば, whatever its class; a godan
        // verb takes the e row of its own final kana.
        assertEquals("食べれば" to "食べなければ", forms("食べる", "v1")[FormId.ConditionalBa])
        assertEquals("書けば" to "書かなければ", forms("書く", "v5k")[FormId.ConditionalBa])
        assertEquals("すれば" to "しなければ", forms("する", "vs-i")[FormId.ConditionalBa])
        assertEquals("為れば" to "為なければ", forms("為る", "vs-i")[FormId.ConditionalBa])
        assertEquals("来れば" to "来なければ", forms("来る", "vk")[FormId.ConditionalBa])
        assertEquals("信ずれば" to "信じなければ", forms("信ずる", "vz")[FormId.ConditionalBa])
        assertEquals("高ければ" to "高くなければ", forms("高い", "adj-i")[FormId.ConditionalBa])

        // たら is the past form plus ら, in every class.
        assertEquals("書いたら" to "書かなかったら", forms("書く", "v5k")[FormId.ConditionalTara])
        assertEquals("行ったら" to "行かなかったら", forms("行く", "v5k-s")[FormId.ConditionalTara])
        assertEquals("問うたら" to "問わなかったら", forms("問う", "v5u-s")[FormId.ConditionalTara])
        assertEquals("高かったら" to "高くなかったら", forms("高い", "adj-i")[FormId.ConditionalTara])

        // たい rides the continuative stem, and then inflects as an
        // い-adjective itself rather than opening a paradigm of its own.
        assertEquals("書きたい" to "書きたくない", forms("書く", "v5k")[FormId.Desiderative])
        assertEquals("食べたい" to "食べたくない", forms("食べる", "v1")[FormId.Desiderative])
        assertEquals("来たい" to "来たくない", forms("来る", "vk")[FormId.Desiderative])
        assertEquals("きたい" to "きたくない", forms("くる", "vk")[FormId.Desiderative])
    }

    @Test
    fun `kureru is the one ichidan verb whose imperative drops the ro`() {
        assertEquals("呉れ" to "呉れるな", forms("呉れる", "v1-s")[FormId.Imperative])
        assertEquals("食べろ" to "食べるな", forms("食べる", "v1")[FormId.Imperative])
    }

    @Test
    fun `the euphonic groups follow the final kana rather than the class code`() {
        assertEquals("書いて" to "書かなくて", forms("書く", "v5k")[FormId.Te])
        assertEquals("書いた" to "書かなかった", forms("書く", "v5k")[FormId.Past])
        assertEquals("泳いで" to "泳がなくて", forms("泳ぐ", "v5g")[FormId.Te])
        assertEquals("泳いだ" to "泳がなかった", forms("泳ぐ", "v5g")[FormId.Past])
        assertEquals("話して" to "話さなくて", forms("話す", "v5s")[FormId.Te])
        assertEquals("話した" to "話さなかった", forms("話す", "v5s")[FormId.Past])
        assertEquals("待って" to "待たなくて", forms("待つ", "v5t")[FormId.Te])
        assertEquals("待った" to "待たなかった", forms("待つ", "v5t")[FormId.Past])
        assertEquals("死んで" to "死ななくて", forms("死ぬ", "v5n")[FormId.Te])
        assertEquals("死んだ" to "死ななかった", forms("死ぬ", "v5n")[FormId.Past])
        assertEquals("遊んで" to "遊ばなくて", forms("遊ぶ", "v5b")[FormId.Te])
        assertEquals("読んで" to "読まなくて", forms("読む", "v5m")[FormId.Te])
        assertEquals("走って" to "走らなくて", forms("走る", "v5r")[FormId.Te])
        assertEquals("買って" to "買わなくて", forms("買う", "v5u")[FormId.Te])
    }

    @Test
    fun `the godan stem rows are the a-i-e rows of the final kana`() {
        val kau = forms("買う", "v5u")
        // う takes わ in the negative stem, never あ.
        assertEquals("買う" to "買わない", kau[FormId.NonPast])
        assertEquals("買います" to "買いません", kau[FormId.NonPastPolite])
        assertEquals("買える" to "買えない", kau[FormId.Potential])
        assertEquals("買われる" to "買われない", kau[FormId.Passive])
        assertEquals("買わせる" to "買わせない", kau[FormId.Causative])
        assertEquals("買わせられる" to "買わせられない", kau[FormId.CausativePassive])
        assertEquals("買え" to "買うな", kau[FormId.Imperative])
    }

    @Test
    fun `iku takes the te and past forms of a tsu verb rather than a ku verb`() {
        val iku = forms("行く", "v5k-s")
        assertEquals("行って" to "行かなくて", iku[FormId.Te])
        assertEquals("行った" to "行かなかった", iku[FormId.Past])
        // Everything else is a plain ku verb.
        assertEquals("行きます" to "行きません", iku[FormId.NonPastPolite])
        assertEquals("行け" to "行くな", iku[FormId.Imperative])
    }

    @Test
    fun `the yuku reading of the same class keeps the regular ku euphony`() {
        // 暮れゆく and 心ゆく carry v5k-s too, but read ゆく, where the
        // irregular forms do not apply: 暮れゆいた, never 暮れゆった.
        val kureyuku = forms("暮れゆく", "v5k-s")
        assertEquals("暮れゆいた" to "暮れゆかなかった", kureyuku[FormId.Past])
        assertEquals("暮れゆいて" to "暮れゆかなくて", kureyuku[FormId.Te])
        assertEquals("暮れゆいたら" to "暮れゆかなかったら", kureyuku[FormId.ConditionalTara])
        // A compound spelled with the kanji does take them.
        assertEquals("出て行った", forms("出て行く", "v5k-s")[FormId.Past]?.first)
        assertEquals("いった", forms("いく", "v5k-s")[FormId.Past]?.first)
    }

    @Test
    fun `aru negates to nai and so does an expression built on it`() {
        val aru = forms("ある", "v5r-i")
        assertEquals("ある" to "ない", aru[FormId.NonPast])
        assertEquals("あった" to "なかった", aru[FormId.Past])
        assertEquals("あって" to "なくて", aru[FormId.Te])
        // The polite negative is regular: ありません, not ないです.
        assertEquals("あります" to "ありません", aru[FormId.NonPastPolite])
        assertEquals("ことがない", forms("ことがある", "v5r-i")[FormId.NonPast]?.second)
        assertEquals("でない", forms("である", "v5r-i")[FormId.NonPast]?.second)
    }

    @Test
    fun `the honorific aru class takes the i stem for polite and imperative`() {
        val irassharu = forms("いらっしゃる", "v5aru")
        assertEquals("いらっしゃいます" to "いらっしゃいません", irassharu[FormId.NonPastPolite])
        assertEquals("いらっしゃいました" to "いらっしゃいませんでした", irassharu[FormId.PastPolite])
        // The imperative takes the same い stem: いらっしゃい, never
        // いらっしゃれ.
        assertEquals("いらっしゃい" to "いらっしゃるな", irassharu[FormId.Imperative])
        assertEquals("下さい", forms("下さる", "v5aru")[FormId.Imperative]?.first)
        assertEquals("仰い", forms("仰る", "v5aru")[FormId.Imperative]?.first)
        assertEquals("ご覧なさい", forms("ご覧なさる", "v5aru")[FormId.Imperative]?.first)
        // No potential, passive, causative or volitional row, though:
        // ござれる, ござらせる and ござろう are not words.
        assertEquals(
            listOf(
                FormId.NonPast,
                FormId.NonPastPolite,
                FormId.Past,
                FormId.PastPolite,
                FormId.Te,
                FormId.Imperative,
                FormId.ConditionalBa,
                FormId.ConditionalTara,
            ),
            conjugate("いらっしゃる", "v5aru").map { it.id },
        )
        // The rest is a plain ru verb.
        assertEquals("いらっしゃる" to "いらっしゃらない", irassharu[FormId.NonPast])
        assertEquals("いらっしゃって" to "いらっしゃらなくて", irassharu[FormId.Te])
    }

    @Test
    fun `the u special class keeps its u in the te and past forms`() {
        val tou = forms("問う", "v5u-s")
        assertEquals("問うて" to "問わなくて", tou[FormId.Te])
        assertEquals("問うた" to "問わなかった", tou[FormId.Past])
        assertEquals("問います" to "問いません", tou[FormId.NonPastPolite])
    }

    @Test
    fun `kuru spells its stems out in kana and holds them in kanji`() {
        val kanji = forms("来る", "vk")
        assertEquals("来る" to "来ない", kanji[FormId.NonPast])
        assertEquals("来ます" to "来ません", kanji[FormId.NonPastPolite])
        assertEquals("来た" to "来なかった", kanji[FormId.Past])
        assertEquals("来て" to "来なくて", kanji[FormId.Te])
        assertEquals("来られる" to "来られない", kanji[FormId.Potential])
        assertEquals("来させる" to "来させない", kanji[FormId.Causative])
        assertEquals("来い" to "来るな", kanji[FormId.Imperative])

        val kana = forms("くる", "vk")
        assertEquals("くる" to "こない", kana[FormId.NonPast])
        assertEquals("きます" to "きません", kana[FormId.NonPastPolite])
        assertEquals("きた" to "こなかった", kana[FormId.Past])
        assertEquals("きて" to "こなくて", kana[FormId.Te])
        assertEquals("こられる" to "こられない", kana[FormId.Potential])
        assertEquals("こさせる" to "こさせない", kana[FormId.Causative])
        assertEquals("こい" to "くるな", kana[FormId.Imperative])
    }

    @Test
    fun `the suru classes conjugate the stored form and differ in the potential`() {
        val aisuru = forms("愛する", "vs-s")
        assertEquals("愛する" to "愛しない", aisuru[FormId.NonPast])
        assertEquals("愛します" to "愛しません", aisuru[FormId.NonPastPolite])
        assertEquals("愛した" to "愛しなかった", aisuru[FormId.Past])
        assertEquals("愛して" to "愛しなくて", aisuru[FormId.Te])
        assertEquals("愛される" to "愛されない", aisuru[FormId.Passive])
        assertEquals("愛させる" to "愛させない", aisuru[FormId.Causative])
        assertEquals("愛しろ" to "愛するな", aisuru[FormId.Imperative])
        // The special class takes せる; 愛できる would be a mistake.
        assertEquals("愛せる" to "愛せない", aisuru[FormId.Potential])

        // The plain class does take できる, on the tail of an expression
        // just as on する itself.
        assertEquals("できる" to "できない", forms("する", "vs-i")[FormId.Potential])
        assertEquals("物にできる" to "物にできない", forms("物にする", "vs-i")[FormId.Potential])

        // JMdict stores する's headword as 為る, which the entry view
        // shows; the table conjugates the same string rather than
        // silently switching spelling halfway down the column.
        assertEquals(
            listOf(
                Form(FormId.NonPast, "為る", "為ない"),
                Form(FormId.NonPastPolite, "為ます", "為ません"),
                Form(FormId.Past, "為た", "為なかった"),
                Form(FormId.PastPolite, "為ました", "為ませんでした"),
                Form(FormId.Te, "為て", "為なくて"),
                Form(FormId.Potential, "出来る", "出来ない"),
                Form(FormId.Passive, "為れる", "為れない"),
                // 為せる, not 為させる: the passive above reads 為 as the
                // さ stem, and the causative has to read it the same way
                // or the column changes its mind halfway down. Both
                // spellings deinflect cleanly, so the oracle cannot
                // decide this one — see the note in Conjugator.suru.
                Form(FormId.Causative, "為せる", "為せない"),
                Form(FormId.CausativePassive, "為せられる", "為せられない"),
                Form(FormId.Imperative, "為ろ", "為るな"),
                Form(FormId.Volitional, "為よう", null),
                Form(FormId.ConditionalBa, "為れば", "為なければ"),
                Form(FormId.ConditionalTara, "為たら", "為なかったら"),
                Form(FormId.Desiderative, "為たい", "為たくない"),
            ),
            conjugate("為る", "vs-i"),
        )

        // The kana spelling is unaffected: させる throughout.
        val suru = forms("する", "vs-i")
        assertEquals("させる" to "させない", suru[FormId.Causative])
        assertEquals("させられる" to "させられない", suru[FormId.CausativePassive])
        assertEquals("される" to "されない", suru[FormId.Passive])
    }

    @Test
    fun `the su precursor class conjugates as a plain su verb when that is how it is written`() {
        // vs-c covers both 為す-style headwords ending in する and the
        // older す forms; the latter are just su godan verbs.
        assertEquals(ConjugationClass.Godan, conjugationClassOf("vs-c", "兼す"))
        assertEquals(ConjugationClass.Suru, conjugationClassOf("vs-c", "兼する"))
        val kensu = forms("兼す", "vs-c")
        assertEquals("兼す" to "兼さない", kensu[FormId.NonPast])
        assertEquals("兼して" to "兼さなくて", kensu[FormId.Te])
    }

    @Test
    fun `zuru inflects on its ji stem and takes zerareru`() {
        val shinzuru = forms("信ずる", "vz")
        assertEquals("信ずる" to "信じない", shinzuru[FormId.NonPast])
        assertEquals("信じます" to "信じません", shinzuru[FormId.NonPastPolite])
        assertEquals("信じた" to "信じなかった", shinzuru[FormId.Past])
        assertEquals("信じて" to "信じなくて", shinzuru[FormId.Te])
        assertEquals("信ぜられる" to "信ぜられない", shinzuru[FormId.Potential])
        assertEquals("信ぜられる" to "信ぜられない", shinzuru[FormId.Passive])
        assertEquals("信じさせる" to "信じさせない", shinzuru[FormId.Causative])
        assertEquals("信じろ" to "信ずるな", shinzuru[FormId.Imperative])
    }

    @Test
    fun `an i-adjective has five rows and no verb rows at all`() {
        assertEquals(
            listOf(
                Form(FormId.NonPast, "高い", "高くない"),
                Form(FormId.NonPastPolite, "高いです", "高くないです"),
                Form(FormId.Past, "高かった", "高くなかった"),
                Form(FormId.PastPolite, "高かったです", "高くなかったです"),
                Form(FormId.Te, "高くて", "高くなくて"),
                Form(FormId.ConditionalBa, "高ければ", "高くなければ"),
                Form(FormId.ConditionalTara, "高かったら", "高くなかったら"),
            ),
            conjugate("高い", "adj-i"),
        )
    }

    @Test
    fun `the yoi class borrows the yo stem when the headword is written ii`() {
        val ii = forms("いい", "adj-ix")
        // いかった is not a word; the class inflects on よ instead.
        assertEquals("いい" to "よくない", ii[FormId.NonPast])
        assertEquals("よかった" to "よくなかった", ii[FormId.Past])
        assertEquals("よくて" to "よくなくて", ii[FormId.Te])

        // A headword written with the kanji stem needs no such help.
        val yoi = forms("良い", "adj-ix")
        assertEquals("良い" to "良くない", yoi[FormId.NonPast])
        assertEquals("良かった" to "良くなかった", yoi[FormId.Past])

        // The same holds inside a compound headword.
        assertEquals("気持ちよかった", forms("気持ちいい", "adj-ix")[FormId.Past]?.first)
    }

    @Test
    fun `a suru noun is not conjugated and neither is anything else without a verb code`() {
        assertNull(conjugationClassOf("vs", "勉強"))
        assertTrue(conjugate("勉強", "vs").isEmpty())
        assertTrue(conjugations("勉強", listOf("n", "vs")).isEmpty())
        assertTrue(conjugations("本", listOf("n")).isEmpty())
        assertTrue(conjugations("とても", listOf("adv")).isEmpty())
        // An expression with no verb in it has nothing to inflect either.
        assertTrue(conjugations("よろしくお願いします", listOf("exp")).isEmpty())
    }

    @Test
    fun `an expression conjugates on its verb tail like any other verb`() {
        val tables = conjugations("気を付ける", listOf("exp", "v1"))
        assertEquals(listOf("v1"), tables.map { it.code })
        assertEquals("気を付けない", tables.single().forms.first().negative)
    }

    @Test
    fun `two senses in genuinely different classes get one table each`() {
        val tables = conjugations("厭う", listOf("v5u", "v5u-s"))
        assertEquals(listOf("v5u", "v5u-s"), tables.map { it.code })
        assertEquals("厭って", tables[0].forms.single { it.id == FormId.Te }.affirmative)
        assertEquals("厭うて", tables[1].forms.single { it.id == FormId.Te }.affirmative)
    }

    @Test
    fun `two codes that resolve to the same class produce one table`() {
        // 熟す is both v5s and vs-c, and vs-c written with す is a su
        // godan verb: the two rows would be identical.
        val tables = conjugations("熟す", listOf("v5s", "vs-c", "vi"))
        assertEquals(listOf("v5s"), tables.map { it.code })
        // v5uru names the old class of 得る, which inflects exactly as
        // the ichidan code it always travels with.
        assertEquals(listOf("v1"), conjugations("得る", listOf("v1", "v5uru")).map { it.code })
    }

    @Test
    fun `a headword that stops short of its inflecting tail yields no table`() {
        // JMdict does carry kanji forms without their okurigana. That
        // is a data case: the tab falls to its empty state rather than
        // inventing a stem or throwing.
        assertTrue(conjugate("食", "v1").isEmpty())
        assertTrue(conjugate("書", "v5k").isEmpty())
        assertTrue(conjugate("高", "adj-i").isEmpty())
        assertTrue(conjugate("勉強", "vs-s").isEmpty())
        assertTrue(conjugations("食", listOf("v1")).isEmpty())
    }
}
