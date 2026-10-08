package cc.hosaka.okonomi.ui.furigana

/**
 * kanjidic's readings for the characters the furigana tests use, copied
 * from the shipped dictionary's `kanji_reading` table: each line is one
 * character's rows of one type, space-separated, in kanjidic's order.
 *
 * Real rows, not readings written for the test: a rule tested against
 * readings the test invented passes for readings no character has. On
 * and kun are the full sets. Nanori are left out except 大's, which are
 * kept on purpose — 大 is a name read た, and that reading must never
 * reach a word (see `KanjiReadingsTest`).
 */
internal val kanjidicFixture: KanjiReadings = KanjiReadings.Builder().apply {
    rows("一", "kun", "ひと- ひと.つ")
    rows("一", "on", "イチ イツ")
    rows("三", "kun", "み み.つ みっ.つ")
    rows("三", "on", "サン ゾウ")
    rows("事", "kun", "こと つか.う つか.える")
    rows("事", "on", "ジ ズ")
    rows("五", "kun", "いつ いつ.つ")
    rows("五", "on", "ゴ")
    rows("人", "kun", "ひと -り -と")
    rows("人", "on", "ジン ニン")
    rows("位", "kun", "くらい ぐらい")
    rows("位", "on", "イ")
    rows("入", "kun", "い.る -い.る -い.り い.れる -い.れ はい.る")
    rows("入", "on", "ニュウ ジュ")
    rows("刑", "on", "ケイ")
    rows("動", "kun", "うご.く うご.かす")
    rows("動", "on", "ドウ")
    rows("合", "kun", "あ.う -あ.う あ.い あい- -あ.い -あい あ.わす あ.わせる -あ.わせる")
    rows("合", "on", "ゴウ ガッ カッ")
    rows("園", "kun", "その")
    rows("園", "on", "エン")
    rows("大", "kun", "おお- おお.きい -おお.いに")
    rows("大", "nanori", "うふ お おう た たかし とも はじめ ひろ ひろし まさ まさる もと わ")
    rows("大", "on", "ダイ タイ")
    rows("天", "kun", "あまつ あめ あま-")
    rows("天", "on", "テン")
    rows("子", "kun", "こ -こ ね")
    rows("子", "on", "シ ス ツ")
    rows("学", "kun", "まな.ぶ")
    rows("学", "on", "ガク")
    rows("手", "kun", "て て- -て た-")
    rows("手", "on", "シュ ズ")
    rows("散", "kun", "ち.る ち.らす -ち.らす ち.らかす ち.らかる ち.らばる ばら ばら.ける")
    rows("散", "on", "サン")
    rows("日", "kun", "ひ -び -か")
    rows("日", "on", "ニチ ジツ")
    rows("曽", "kun", "かつ かつて すなわち")
    rows("曽", "on", "ソウ ソ ゾウ")
    rows("月", "kun", "つき")
    rows("月", "on", "ゲツ ガツ")
    rows("木", "kun", "き こ-")
    rows("木", "on", "ボク モク")
    rows("来", "kun", "く.る きた.る きた.す き.たす き.たる き こ")
    rows("来", "on", "ライ タイ")
    rows("杯", "kun", "さかずき")
    rows("杯", "on", "ハイ")
    rows("校", "on", "コウ キョウ")
    rows("歩", "kun", "ある.く あゆ.む")
    rows("歩", "on", "ホ ブ フ")
    rows("殺", "kun", "ころ.す -ごろ.し そ.ぐ あや.める")
    rows("殺", "on", "サツ サイ セツ")
    rows("気", "kun", "いき き")
    rows("気", "on", "キ ケ")
    rows("為", "kun", "ため な.る な.す す.る たり つく.る なり")
    rows("為", "on", "イ")
    rows("物", "kun", "もの もの-")
    rows("物", "on", "ブツ モツ")
    rows("皇", "on", "コウ オウ")
    rows("相", "kun", "あい-")
    rows("相", "on", "ソウ ショウ")
    rows("税", "on", "ゼイ")
    rows("紙", "kun", "かみ")
    rows("紙", "on", "シ")
    rows("見", "kun", "み.る み.える み.せる")
    rows("見", "on", "ケン")
    rows("関", "kun", "せき -ぜき かか.わる からくり かんぬき")
    rows("関", "on", "カン")
    rows("頭", "kun", "あたま かしら -がしら かぶり")
    rows("頭", "on", "トウ ズ ト")
    rows("食", "kun", "く.う く.らう た.べる は.む")
    rows("食", "on", "ショク ジキ")
    rows("馬", "kun", "うま ま")
    rows("馬", "on", "バ メ マ ボ モ")
}.build()

private fun KanjiReadings.Builder.rows(kanji: String, type: String, texts: String) {
    texts.split(' ').forEach { add(kanji, type, it) }
}
