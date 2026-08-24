package cc.hosaka.okonomi.feature.kanji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import cc.hosaka.okonomi.db.KanjiCharacter
import cc.hosaka.okonomi.ui.theme.Dimens
import cc.hosaka.okonomi.ui.theme.verticalPaddingHalf
import cc.hosaka.okonomi.ui.theme.OkonomiTheme

/**
 * The four shapes a card takes, in one pass: every field populated, a
 * character with no optional metadata, a long kun list beside a
 * multi-radical line, and a character kanjidic does not carry. Between
 * them they show the whole section order — on, kun, nanori, meanings,
 * radicals, then the chip row — and every section's absent case. None
 * of this is reachable from a preview of the tab itself, which needs a
 * database.
 */
@Composable
@Preview
fun KanjiCardPreview() {
    OkonomiTheme {
        Surface {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.contentPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.verticalPaddingHalf),
            ) {
                KanjiCard(everyField)
                KanjiCard(noOptionalMetadata)
                KanjiCard(longReadingsAndRadicals)
                KanjiCard(noCharacterData)
            }
        }
    }
}

private val everyField = KanjiCharacter(
    literal = "食",
    strokeCount = 9,
    grade = 2,
    jlpt = 4,
    freq = 382,
    onReadings = listOf("ショク", "ジキ"),
    kunReadings = listOf("く.う", "く.らう", "た.べる", "は.む"),
    nameReadings = listOf("ぐい"),
    meanings = listOf("eat", "food"),
    radicals = listOf("食"),
)

private val noOptionalMetadata = KanjiCharacter(
    literal = "腺",
    strokeCount = 13,
    grade = null,
    jlpt = null,
    freq = null,
    onReadings = listOf("セン"),
    kunReadings = emptyList(),
    nameReadings = emptyList(),
    meanings = listOf("gland"),
    radicals = emptyList(),
)

private val longReadingsAndRadicals = KanjiCharacter(
    literal = "生",
    strokeCount = 5,
    grade = 1,
    jlpt = 4,
    freq = 29,
    onReadings = listOf("セイ", "ショウ"),
    kunReadings = listOf(
        "い.きる", "い.かす", "い.ける", "う.まれる", "う.まれ",
        "お.う", "は.える", "は.やす", "き", "なま",
    ),
    nameReadings = listOf("あさ", "いく", "うぶ", "なば", "みぶ"),
    meanings = listOf("life", "genuine", "birth"),
    radicals = listOf("丿", "土", "生"),
)

private val noCharacterData = KanjiCharacter(
    literal = "兀",
    strokeCount = null,
    grade = null,
    jlpt = null,
    freq = null,
    onReadings = emptyList(),
    kunReadings = emptyList(),
    nameReadings = emptyList(),
    meanings = emptyList(),
    radicals = listOf("儿"),
)
