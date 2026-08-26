package cc.hosaka.okonomi.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * The size Japanese is read at, and the line it needs.
 *
 * Not "the search row size" or "the sentence size": it is one decision
 * about the language, applied wherever the app puts Japanese in front of
 * a reader to be read rather than glanced at. The Phrases tab's example
 * sentences and the search results' headwords are both that, and Alex
 * asked for them to match after seeing the first (2026-08-26).
 *
 * 22sp, and the reason is the furigana rather than the kanji. Ruby is
 * drawn at 0.45 of the base size, so a 16sp line sets its readings at
 * 7.2sp — a decoration that says a reading exists without being one.
 * At 22sp the ruby lands at 9.9sp and can actually be read, which is the
 * only reason it is on the screen.
 *
 * The line height is twice the size, and that is not a rhythm choice.
 * `FuriganaText` floats each reading above the line it belongs to,
 * reaching `fontSize / 2 + ruby + gap` — 21.6sp here — above the centre
 * of the characters. A line box shorter than 43.1sp lets a wrapped
 * line's ruby collide with the line above it, so 44 is the floor and not
 * a preference. The two move together, which is why they are applied
 * together and never separately.
 *
 * Both are in sp and both scale with the reader's text size. Anything
 * that has to hold still in dp — a minimum tap target, say — is stated
 * in dp beside its own use.
 */
private val JAPANESE_READING_FONT_SIZE = 22.sp

private val JAPANESE_READING_LINE_HEIGHT = 44.sp

/**
 * This style at the size Japanese is read at, keeping everything else
 * the caller chose — its typeface, weight and colour.
 *
 * One call rather than two constants, so that a caller cannot take the
 * size without the line height it needs; see above for why that pairing
 * is load-bearing.
 */
fun TextStyle.atJapaneseReadingSize(): TextStyle = copy(
    fontSize = JAPANESE_READING_FONT_SIZE,
    lineHeight = JAPANESE_READING_LINE_HEIGHT,
)
