package cc.hosaka.okonomi.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class RomajiTest {

    private val cases = listOf(
        // Plain moras, and the long vowels the wapuro style spells out.
        "たべもの" to "tabemono",
        "そう" to "sou",
        "とうきょう" to "toukyou",
        "おおきい" to "ookii",
        // Gemination: っ doubles the consonant that follows it.
        "がっこう" to "gakkou",
        "きって" to "kitte",
        "ざっし" to "zasshi",
        "いっぱい" to "ippai",
        // Hepburn writes っち as tchi rather than doubling the c.
        "まっちゃ" to "matcha",
        // A trailing っ has no consonant to double.
        "あっ" to "a",
        // A pending っ must not leak across ん or ー into a later mora.
        "あっんか" to "anka",
        "あっーか" to "aaka",
        // ん: n, and n' where it could be read into the next mora.
        "しんゆう" to "shin'yuu",
        "きんえん" to "kin'en",
        "しんぶん" to "shinbun",
        "あんない" to "annai",
        "ほん" to "hon",
        // Digraphs.
        "きゃく" to "kyaku",
        "りょこう" to "ryokou",
        "しゅくだい" to "shukudai",
        "じしょ" to "jisho",
        // Hepburn consonants.
        "つくえ" to "tsukue",
        "ふじさん" to "fujisan",
        "ちち" to "chichi",
        // Katakana input, including the prolonged sound mark.
        "ラーメン" to "raamen",
        "コーヒー" to "koohii",
        "タベル" to "taberu",
        "ヴァイオリン" to "vaiorin",
        "フィルム" to "firumu",
        "デュエット" to "dyuetto",
        "イェーガー" to "yeegaa",
        // Half-width katakana, whose voicing marks stand on their own.
        "ﾗｰﾒﾝ" to "raamen",
        "ﾃﾞｭｴｯﾄ" to "dyuetto",
        "ﾊﾟｿｺﾝ" to "pasokon",
        // A ー with no vowel to lengthen is kept rather than dropped.
        "ーめん" to "ーmen",
        "んー" to "nー",
        "Aー" to "Aー",
        // Anything that is not kana passes through as it stands.
        "" to "",
        "ABC" to "ABC",
        "アイス・コーヒー" to "aisu・koohii",
        // Iteration marks have no reading of their own.
        "ところゞ" to "tokoroゞ",
    )

    @Test
    fun convertsKanaToWapuroHepburn() {
        // Every case is checked before failing: one bad mapping must not
        // hide the rest of the table.
        val failures = cases.mapNotNull { (kana, expected) ->
            val actual = toRomaji(kana)
            if (actual == expected) null else "$kana: expected <$expected> but was <$actual>"
        }
        if (failures.isNotEmpty()) {
            fail("${failures.size} of ${cases.size} romaji cases failed:\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun keepsTheCaseTableFreeOfDuplicates() {
        // Two rows for the same input would let a wrong expectation sit
        // next to a right one without either failing loudly.
        assertEquals(cases.size, cases.distinctBy { it.first }.size, "duplicate case in the table")
    }
}
