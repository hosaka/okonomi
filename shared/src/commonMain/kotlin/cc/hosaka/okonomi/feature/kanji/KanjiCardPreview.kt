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
 * character with no optional metadata, a long kun list, and a character
 * kanjidic does not carry. Between them they show the whole section
 * order — on, kun, meanings, then the chip row — and every section's
 * absent case. Nanori and the radicals are not among them any more:
 * they live in the overlay, previewed by [KanjiDetailContentPreview].
 * None of this is reachable from a preview of the tab itself, which
 * needs a database. Three carry real KanjiVG stroke data and one
 * carries none, so the filled slot and the dashed empty one are both on
 * screen at once; the animation itself needs a running device.
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

/**
 * The overlay's surface, without the `Dialog` window around it — the
 * window needs a running host, the content does not.
 *
 * Every row of the matrix that opens anything: 食 carries a nanori and a
 * radical, 生 the long nanori list beside three radicals, 兀 is the
 * radicals-only row, and [nanoriOnly] the nanori-only one — where the
 * whole radicals section is omitted rather than drawn empty, which is
 * the layout no other fixture here can show. The remaining fixture, 腺,
 * has neither field and so never opens this at all. The chips take no
 * tap here; where they go needs a navigation controller.
 */
@Composable
@Preview
fun KanjiDetailContentPreview() {
    OkonomiTheme {
        Surface {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.contentPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.verticalPaddingHalf),
            ) {
                KanjiDetailContent(everyField, onRadicalClick = {})
                KanjiDetailContent(longReadingsAndRadicals, onRadicalClick = {})
                KanjiDetailContent(noCharacterData, onRadicalClick = {})
                KanjiDetailContent(nanoriOnly, onRadicalClick = {})
            }
        }
    }
}

/*
 * Real KanjiVG path data, copied out of its 098df/0817a/0751f files. A
 * made-up curve would preview a diagram that is not the one the app
 * draws; these are the exact strings the generated dictionary stores,
 * on KanjiVG's 109x109 viewBox and in its stroke order.
 */

private val shokuStrokes = listOf(
    "M52.75,10.5c0.11,0.98-0.19,2.67-0.97,3.93C45,25.34,31.75,41.19,14,51.5",
    "M52.75,16.25c5.09,4.8,25.71,19.61,33.7,24.9c2.68,1.78,5.37,2.79,8.55,3.35",
    "M52.25,29.25c1,1,1.5,2.25,1.5,3.5c0,2,0,3,0,5.5",
    "M38,40c0.83,0.47,2.19,1,3.86,0.83c9.39-0.96,21.95-2.76,23.25-2.84c1.67-0.1,3.14,0.88,3.11,2.53C68.2,41.8,67,53.25,66.34,62.4c-0.07,0.94-0.13,1.36-0.13,1.99",
    "M40.83,51.73C47.25,51.25,59.5,50,66,49.75",
    "M40.69,63.9c7.04-0.52,16.55-1.62,24.6-2.04",
    "M38.25,40.25c1.12,1.12,1.5,2.62,1.5,4c0,9.12,0,43.62,0,47.25c0,4,1,4.88,4.12,2.88c2.93-1.87,6.75-5.25,10.88-8.38",
    "M74,64c0.25,1.25,0.09,2.57-0.75,3.5c-3.5,3.88-4.5,4.88-7.25,7.5",
    "M51.5,71C55.75,71,77,90,81,92.75c2.49,1.71,4.62,2.62,7.5,3.5",
)

private val senStrokes = listOf(
    "M16.96,18.99c0.84,0.84,0.96,2.01,0.96,3.05C17.88,58.75,17.38,79,9.25,91.89",
    "M18.62,19.81c3.53-0.69,9.45-1.72,12-2.09c3.1-0.45,4.18,1.08,4.16,3.9c-0.05,7.71-0.22,47.78-0.22,64.94c0,12.61-5.63,3.55-7.25,2.04",
    "M18.62,38.95c4.19-0.35,10.76-1.45,15.05-1.82",
    "M18.07,58.15c3.45-0.08,10.84-1.28,15.53-1.7",
    "M66.23,12c0,1.12-0.3,1.96-0.81,2.75c-1.92,3-2.85,4.42-5.92,8.12",
    "M47.84,25.22c0.97,0.97,1.13,2.1,1.46,3.39c0.94,3.74,2.36,10.51,3.27,15.63c0.37,2.08,0.66,3.88,0.81,5.1",
    "M49.28,25.77c3.78-0.48,27.59-3.27,33.46-3.86c3.7-0.37,4.82,1.26,4.24,4.71c-0.47,2.8-1.49,8.92-2.77,15.25c-0.36,1.78-0.74,3.58-1.14,5.32",
    "M52.14,36.24c9.73-0.99,24.11-2.36,32.94-2.92",
    "M54.19,47.41c9.47-1,19.84-1.9,27.95-2.31",
    "M66.65,51.42c1.09,1.09,1.27,2.7,1.27,4.38c0,12.21-0.04,31.05-0.04,35.2c0,6.5-3.62,4-7.2-0.29",
    "M43,64.39c1.18,0.18,2.54,0.38,3.75,0.11c2.25-0.5,8.2-2.33,10.17-3.05s3.62,1.07,2.96,2.67c-2.66,6.49-7.13,17.63-18.38,25.38",
    "M89.5,53.75c0,1-0.36,1.89-0.86,2.59C85.12,61.25,82.2,64.1,77,68",
    "M69.62,64c5.75,7.75,11.88,15,17.74,20.39c2.54,2.34,4.01,3.86,7.69,5.44",
)

private val seiStrokes = listOf(
    "M31.26,25.89c0.36,1.36,0.35,2.65-0.05,3.79c-2.34,6.69-7.24,17.22-14.96,24.19",
    "M31.13,40.67c2.37,0.33,4.03,0.07,5.64-0.12c9.5-1.1,25.15-4.12,35.35-5.83c2.51-0.42,4.86-0.73,7.38-0.33",
    "M52.31,12.63c1.28,1.28,2.01,3.12,2.01,5.23c0,4.01,0,65.14,0,69.77",
    "M29.38,64.03c2.64,0.67,5.38,0.31,8.04-0.02C49.45,62.51,62.16,61,72.5,59.86c2.38-0.26,4.99-0.76,7.38-0.23",
    "M15.75,90.25c3.04,0.75,6.21,0.94,8.4,0.8C40.62,90,68.12,86.5,83.3,85.75c3.63-0.18,7.68,0,10.07,0.73",
)

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
    strokePaths = shokuStrokes,
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
    strokePaths = senStrokes,
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
    strokePaths = seiStrokes,
)

/**
 * Nanori with no radicals, the one matrix row none of the four real
 * fixtures covers: radkfile knows all of these characters, and radkfile
 * covers fewer characters than kanjidic does, so the pairing exists in
 * the shipped data. Derived from [everyField] rather than attributed to
 * a named character, because which characters radkfile is missing is
 * not something this file can check.
 */
private val nanoriOnly = everyField.copy(radicals = emptyList())

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
    // KanjiVG has no file for this character, so its slot stays the
    // dashed empty state: the fourth preview is the only place that
    // shape is visible now that the others are filled.
    strokePaths = emptyList(),
)
