/*
 * Copyright 2026 turtlekazu
 * Copyright 2026 Alex March
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.hosaka.okonomi.ui.furigana

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Furigana rendering, vendored from Furiganable's `compose-core`
 * (https://github.com/turtlekazu/Furiganable, commit 7759409, v0.3.1)
 * and reworked into project code.
 *
 * The upstream repository carries no LICENSE file; its published
 * artifacts are Apache-2.0, stated in `gradle.properties` as
 * `POM_LICENSE_NAME`/`POM_LICENSE_URL`. That is the licence this copy is
 * used under, and `feature/settings/Credits.kt` carries the attribution
 * shown in the app.
 *
 * Vendored rather than depended on because the published API takes one
 * `formattedText` string with one `color` and offers no way to style
 * part of a line, while a search row has to show a matched substring in
 * the highlight colour *and* set readings above the kanji. Some of that
 * capability was already there — the annotated string is built with
 * inline content internally — and needed only surfacing; the rest, a
 * highlight reaching inside a single kanji-with-ruby unit, is ours
 * (see [FuriganaSegment.Highlight] and [RubyUnit]).
 *
 * The other departures from upstream: readings are handed over already
 * split into segments instead of parsed out of `[漢字[かんじ]]` notation
 * (nothing in this app stores that notation — see [alignReading], which
 * derives the split), the styling parameters no caller sets are gone,
 * and the memo key is the input rather than upstream's
 * `isSystemInDarkTheme()` proxy for it.
 *
 * Upstream's `furiganaEnabled` is gone too, and that one is a product
 * decision rather than a trim: furigana is always on, and there is no
 * setting for it (Alex, 2026-08-26 — the same call as dropping romaji,
 * "I'm not making an app for beginners"). The app assumes a reader who
 * reads kana, and such a reader is not harmed by being shown one. Do not
 * restore the parameter as a missing feature; reopening it is a product
 * question, not a gap in the port.
 */

/**
 * One run of a furigana line: [text] as it is written, with [reading]
 * set above it as ruby, or null when the run reads itself and needs no
 * ruby.
 *
 * [highlight] asks for the caller's highlight style over some part of
 * the run.
 */
@Immutable
data class FuriganaSegment(
    val text: String,
    val reading: String? = null,
    val highlight: Highlight? = null,
) {
    /**
     * What of a run the caller's highlight covers.
     *
     * A run with a reading has two halves that mean different things,
     * and a match that covers one of them entirely is not the same
     * claim as one that covers part of it. 食 with た over it, matched
     * by たべ, is matched *as a unit* — た is the whole of what 食 says,
     * so both halves light. 相殺関税 with そうさいかんぜい over it,
     * matched by そうさい, is not: the run is undivided precisely
     * because nobody can say which kanji take そうさい, so lighting the
     * kanji would claim exactly the division [alignReading] refused to
     * make. Only the kana that matched light there.
     */
    @Immutable
    sealed interface Highlight {
        /** The run entire: its characters and its reading together. */
        data object Whole : Highlight

        /** These characters of [text]; the reading stays plain. */
        data class PartOfText(val range: IntRange) : Highlight

        /** These characters of [reading]; the word stays plain. */
        data class PartOfReading(val range: IntRange) : Highlight
    }

    /** The characters of [text] the highlight covers. */
    internal val highlightedText: IntRange?
        get() = when (val highlight = highlight) {
            null, is Highlight.PartOfReading -> null
            Highlight.Whole -> text.indices
            is Highlight.PartOfText -> highlight.range
        }

    /** The characters of the ruby the highlight covers. */
    internal val highlightedReading: IntRange?
        get() = when (val highlight = highlight) {
            null, is Highlight.PartOfText -> null
            Highlight.Whole -> (reading ?: text).indices
            is Highlight.PartOfReading -> highlight.range
        }
}

/** The written line, without any of its readings. */
fun List<FuriganaSegment>.plainText(): String = joinToString("") { it.text }

/** A run of characters drawn in one style: the caller's highlight, or none. */
private class Piece(val text: String, val highlighted: Boolean)

/**
 * [text] cut at the edges of [highlighted] — at most three pieces, and
 * exactly one when the range is absent or covers everything. The single
 * piece is what keeps the ordinary case drawing through exactly the same
 * one text call it always did.
 */
private fun piecesOf(text: String, highlighted: IntRange?): List<Piece> {
    if (highlighted == null || highlighted.isEmpty()) return listOf(Piece(text, highlighted = false))
    val start = highlighted.first.coerceIn(0, text.length)
    val end = (highlighted.last + 1).coerceIn(start, text.length)
    return buildList {
        if (start > 0) add(Piece(text.substring(0, start), highlighted = false))
        if (end > start) add(Piece(text.substring(start, end), highlighted = true))
        if (end < text.length) add(Piece(text.substring(end), highlighted = false))
    }
}

/**
 * Whether a reading is spread across its base in one cell per kana
 * instead of being drawn as one string.
 *
 * Cells are a layout device: text renderers distribute letter spacing
 * differently, and only explicit cells keep やま evenly placed over 山
 * on both platforms. They are used over a **single** character, and that
 * is the whole rule. Upstream spread whenever the two counts matched,
 * which over a run of several characters is a claim about which kana
 * belongs to which — 刑事 laid out as 刑=で, 事=か. [alignReading]
 * refuses to make that claim without evidence (相殺 takes そうさい
 * whole) and the renderer must not make it behind its back: 2,399
 * shipped forms arrive here as one undivided run, and the ones whose
 * reading is short enough to have triggered it are overwhelmingly the
 * jukujikun and ateji that have no per-character reading at all.
 *
 * The widths decide the rest: a reading wider than its base has nothing
 * to spread into.
 */
internal fun spreadsPerCharacter(
    text: String,
    reading: String,
    baseWidth: Float,
    rubyWidth: Float,
): Boolean = text.length == 1 && reading.length > 1 && baseWidth > rubyWidth

/** Whether the unit is in sp, the only unit the sizes here can be scaled and compared in. */
private val TextUnit.isScalablePixels: Boolean
    get() = type == TextUnitType.Sp

/** Font size for a line whose style states none. */
private const val DEFAULT_FONT_SIZE = 14f

/** Ruby is drawn at this fraction of the base font size. */
private const val RUBY_SCALE = 0.45f

/**
 * The base-to-ruby gap, and the ruby's own (negative) letter spacing,
 * as a fraction of the base font size. Tightening the ruby keeps a long
 * reading closer to the width of the kanji it sits over.
 */
private const val RUBY_GAP_SCALE = 0.03f

/**
 * A line of Japanese with its readings set above the kanji.
 *
 * The line is laid out as one [BasicText], so it wraps, ellipsizes and
 * measures like any other text; each run carrying a reading becomes an
 * inline box holding the base characters with its ruby floated above
 * them. Segments without readings are appended as ordinary text, which
 * is what keeps a character-exact highlight possible.
 *
 * [highlightStyle] is applied to whatever the segments mark with a
 * [FuriganaSegment.Highlight]; the default styles nothing. On a plain
 * run every property of it applies, because the run is ordinary
 * annotated text. On a run with a reading only what a platform text
 * view can carry survives — colour, size, typeface, weight, letter
 * spacing, font features — so a background, a decoration or a shadow
 * asked for there is silently absent on Android.
 */
@Composable
fun FuriganaText(
    segments: List<FuriganaSegment>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    highlightStyle: SpanStyle = SpanStyle(),
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    val contentColor = LocalContentColor.current
    val requestedStyle = style.merge(color = color.takeOrElse { style.color.takeOrElse { contentColor } })

    // Every size below is derived by scaling this one, and the ruby is
    // then positioned against the line height in the same unit. An em
    // font size makes those two numbers incomparable, so it is not
    // scaled at all: the default stands in and the ruby stays where the
    // arithmetic can put it.
    val fontSize = if (requestedStyle.fontSize.isScalablePixels) requestedStyle.fontSize else DEFAULT_FONT_SIZE.sp
    val rubyFontSize = fontSize * RUBY_SCALE
    val rubyGap = fontSize * RUBY_GAP_SCALE
    val rubyLetterSpacing = -fontSize * RUBY_GAP_SCALE
    // The ruby is drawn outside the base line's own box, so the line has
    // to be tall enough to hold it or consecutive lines overlap.
    val minLineHeight = (fontSize.value + rubyFontSize.value + rubyGap.value).sp
    val lineHeight = when {
        requestedStyle.lineHeight.isScalablePixels && requestedStyle.lineHeight > minLineHeight ->
            requestedStyle.lineHeight

        else -> minLineHeight
    }
    // Resolved once, for both branches, and this is the whole of what
    // keeps them level. See [CENTRED_IN_LINE].
    val mergedStyle = requestedStyle.merge(lineHeight = lineHeight).let {
        if (it.lineHeightStyle != null) it else it.copy(lineHeightStyle = CENTRED_IN_LINE)
    }

    if (segments.none { it.reading != null }) {
        // Nothing to set above anything: the line is ordinary text, and
        // paying for placeholders and inline composables to say so would
        // be a cost every kana-only entry carries for no reading. It
        // takes the same line box all the same — that is the agreement,
        // not the machinery.
        BasicText(
            text = remember(segments, highlightStyle) { plainAnnotatedString(segments, highlightStyle) },
            modifier = modifier,
            style = mergedStyle,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
        )
        return
    }

    val baseStyle = mergedStyle.merge(
        fontSize = fontSize,
        letterSpacing = if (mergedStyle.letterSpacing.isSpecified) mergedStyle.letterSpacing else 0.sp,
    )

    val density = LocalDensity.current
    val fontResolver = LocalFontFamilyResolver.current
    // baseStyle carries the resolved line height, so it keys this on its
    // own; the placeholder no longer takes one.
    val (text, inlineContent) = remember(segments, baseStyle, highlightStyle, density, fontResolver) {
        rubyAnnotatedString(
            segments = segments,
            style = baseStyle,
            highlightStyle = highlightStyle,
            rubyFontSize = rubyFontSize,
            rubyGap = rubyGap,
            rubyLetterSpacing = rubyLetterSpacing,
            density = density,
            fontResolver = fontResolver,
        )
    }

    BasicText(
        text = text,
        modifier = modifier,
        style = mergedStyle,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        inlineContent = inlineContent,
    )
}

/**
 * The line box both branches draw in, and the reference both put a
 * glyph against. Named `rubyReadyLineBox` in prose because that is what
 * it is: a line sized and aligned for a reading, whether or not this
 * particular line has one.
 *
 * Two branches drawing the same word at the same size used to disagree
 * about where on the line it went, and the disagreement was invisible
 * one line at a time. It shows the moment two of them sit side by side:
 * the Phrases tab draws each word of a sentence as its own
 * [FuriganaText] in a row, so 羊は草を食べる。put は and を on a different
 * baseline from 羊 and 草. Reported in review during the increment that
 * introduced the split, fixed only when it reached a screenshot — and
 * then only in part. The box was one of three causes; the other two
 * were the word inside a ruby unit being drawn by a second text engine
 * (see [PieceRow]) and the placeholder rewriting the line's own metrics
 * (see [rubyAnnotatedString]). Only the first is visible to a host
 * test.
 *
 * Two things had to be made to agree, and only both together are
 * enough:
 *
 * - **The height of the box.** The ruby-free path used to take the
 *   caller's line height unchanged while the ruby path raised it to
 *   the ruby floor, so a caller asking for less than the floor got two
 *   different boxes. Now the floor is applied before either branch, and
 *   a kana-only line is as tall as the kanji line beside it.
 * - **Where the glyph sits in it.** This is the half that bit. The ruby
 *   path centres its base characters — the placeholder is aligned
 *   `TextCenter` and [RubyUnit] draws into the middle of it — while
 *   ordinary text is distributed proportionally, roughly four fifths of
 *   the slack above the glyph and one fifth below. Same box, same
 *   font, two different answers, some six sp apart at the sentence
 *   size. Stating the alignment makes the ruby path's own convention
 *   the line's convention.
 *
 * Centring is also the alignment the ruby floor was always arithmetic
 * for: with the glyph centred, the reading's top lands within a hair of
 * the top of the box (`fontSize / 2 + ruby + gap` up from the middle of
 * a box `fontSize + ruby + gap` tall). Under the proportional default
 * that sum was never quite the right one. A caller that states its own
 * `lineHeightStyle` still gets it — [RubyUnit] reads the alignment back
 * and follows it — so this is a default, not an override.
 */
private val CENTRED_IN_LINE = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun plainAnnotatedString(
    segments: List<FuriganaSegment>,
    highlightStyle: SpanStyle,
): AnnotatedString = buildAnnotatedString {
    segments.forEach { segment -> appendPlain(segment, highlightStyle) }
}

/**
 * A run with no reading, styled character-exactly. A caller marking a
 * whole run does so because it split the run at the boundary itself, so
 * this usually appends one piece.
 */
private fun AnnotatedString.Builder.appendPlain(
    segment: FuriganaSegment,
    highlightStyle: SpanStyle,
) {
    piecesOf(segment.text, segment.highlightedText ?: segment.highlightedReading).forEach { piece ->
        if (piece.highlighted) {
            withStyle(highlightStyle) { append(piece.text) }
        } else {
            append(piece.text)
        }
    }
}

/**
 * The line as an annotated string plus the inline content its ruby runs
 * are drawn by. Each such run reserves a placeholder as wide as the
 * wider of base and reading, and draws both inside it: the base
 * centred, the reading translated up out of the line box.
 *
 * **The placeholder is sized and aligned so that it does not decide
 * where the line sits**, and both halves of that sentence are load
 * bearing. Android asks a replacement span for its size and lets it
 * rewrite the line's font metrics while doing so, and Compose's
 * `PlaceholderSpan.getSize` begins by assigning the line
 * `paint.getFontMetricsInt()` — the metrics of the span's own typeface —
 * before widening them to the placeholder's height. The paint there is
 * the primary font. The Japanese around it is drawn from the CJK
 * fallback, whose ascent and descent are larger and whose midpoint sits
 * some two sp lower at reading size.
 *
 * So a placeholder as tall as the line, aligned to the *text* centre,
 * used to hand the whole line the primary font's idea of where text
 * goes — and every ordinary character on that line moved with it, while
 * an ordinary line beside it kept the fallback's. 食べる's べ sat five
 * device pixels above the べ of べき in the very same sentence. Measured
 * off a screenshot rather than argued about: same glyph, same size,
 * same line, forty-pixel ink height on both, tops five pixels apart.
 *
 * Two changes together, and each is needed:
 *
 * - **An em tall, not a line tall.** A placeholder no taller than the
 *   text's own block never widens the metrics past what the real runs
 *   already claim, so the line keeps the metrics of the characters on
 *   it. The height was never doing anything else: the word inside
 *   measures itself and the reading is drawn outside the box entirely.
 * - **Aligned to the line, not to the text.** `Center` rather than
 *   `TextCenter` puts the box's middle at the middle of the line box,
 *   which is a position, not a font's opinion. The word drawn inside it
 *   then lands where [LineHeightStyle.Alignment.Center] puts ordinary
 *   text — the middle of the same box — so the two agree by measuring
 *   against the same thing rather than by two fonts happening to.
 */
private fun rubyAnnotatedString(
    segments: List<FuriganaSegment>,
    style: TextStyle,
    highlightStyle: SpanStyle,
    rubyFontSize: TextUnit,
    rubyGap: TextUnit,
    rubyLetterSpacing: TextUnit,
    density: Density,
    fontResolver: FontFamily.Resolver,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val measurer = TextMeasurer(
        defaultLayoutDirection = LayoutDirection.Ltr,
        defaultFontFamilyResolver = fontResolver,
        defaultDensity = density,
        cacheSize = 8,
    )
    val text = buildAnnotatedString {
        segments.forEachIndexed { index, segment ->
            val reading = segment.reading
            if (reading == null) {
                appendPlain(segment, highlightStyle)
                return@forEachIndexed
            }

            val rubyStyle = style.merge(
                fontSize = rubyFontSize,
                lineHeight = rubyFontSize,
                letterSpacing = rubyLetterSpacing,
            )
            // Measured with the highlight applied to whichever half
            // carries any of it: a highlight can change the weight, and
            // a box reserved for the plain width would clip the bolder
            // text. Over-reserving for a partial highlight only leaves a
            // little air.
            val baseWidth = measurer.widthInSp(
                text = segment.text,
                style = if (segment.highlightedText != null) style.merge(highlightStyle) else style,
                density = density,
            )
            val rubyWidth = measurer.widthInSp(
                text = reading,
                style = if (segment.highlightedReading != null) rubyStyle.merge(highlightStyle) else rubyStyle,
                density = density,
            )
            val perCharacter = spreadsPerCharacter(segment.text, reading, baseWidth, rubyWidth)

            val inlineId = "ruby_$index"
            appendInlineContent(inlineId, segment.text)
            inlineContent[inlineId] = InlineTextContent(
                placeholder = Placeholder(
                    width = max(baseWidth, rubyWidth).sp,
                    // The em box of the characters it stands in for,
                    // and deliberately NOT the line height; see above.
                    height = style.fontSize,
                    // Against the LINE, not against the text: Top rather
                    // than TextTop, Center rather than TextCenter.
                    placeholderVerticalAlign = when (style.lineHeightStyle?.alignment) {
                        LineHeightStyle.Alignment.Top -> PlaceholderVerticalAlign.Top
                        LineHeightStyle.Alignment.Bottom -> PlaceholderVerticalAlign.Bottom
                        else -> PlaceholderVerticalAlign.Center
                    },
                ),
                children = {
                    RubyUnit(
                        text = piecesOf(segment.text, segment.highlightedText),
                        reading = piecesOf(reading, segment.highlightedReading),
                        style = style,
                        rubyStyle = rubyStyle,
                        highlightStyle = highlightStyle,
                        rubyFontSize = rubyFontSize,
                        rubyGap = rubyGap,
                        perCharacter = perCharacter,
                    )
                },
            )
        }
    }
    return text to inlineContent
}

/**
 * One kanji-with-ruby box: the word drawn on the line, its reading
 * floated above it.
 *
 * Both halves arrive already cut into pieces, so a highlight can cover
 * part of either without touching the other. This is the capability the
 * published library has no way to express — it draws a unit in one
 * colour — and the reason a search for そうさい lights only the そうさい
 * of 相殺関税's ruby.
 */
@Composable
private fun RubyUnit(
    text: List<Piece>,
    reading: List<Piece>,
    style: TextStyle,
    rubyStyle: TextStyle,
    highlightStyle: SpanStyle,
    rubyFontSize: TextUnit,
    rubyGap: TextUnit,
    perCharacter: Boolean,
) {
    val alignment = when (style.lineHeightStyle?.alignment) {
        LineHeightStyle.Alignment.Top -> Alignment.TopCenter
        LineHeightStyle.Alignment.Bottom -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    Box(
        contentAlignment = alignment,
        // The line's own annotated string already carries these
        // characters — [appendInlineContent] appends the base text
        // behind the placeholder — so everything drawn in here is a
        // second copy of the word plus a reading no screen reader
        // should announce as a separate word. Cleared once here rather
        // than at each of the three call sites.
        modifier = Modifier
            .fillMaxSize()
            .clearAndSetSemantics { },
    ) {
        PieceRow(
            pieces = text,
            style = style,
            highlightStyle = highlightStyle,
            // The word half, and NOT through TextSpacingRemoved: this
            // is the second copy of characters the line has already
            // laid out, and it has to land exactly where the line put
            // them. A platform TextView measures a Japanese run against
            // the primary font while Compose measures it against the
            // CJK fallback, and two blocks of different heights centred
            // in one box do not share a baseline. Drawing it with the
            // same composable and the same style as an ordinary line is
            // what makes them agree, and it is the whole reason the
            // parameter exists. It was not the whole of why 羊 and 草
            // sat above は and を — see [rubyAnnotatedString] for the
            // placeholder that moved the entire line — but it is the
            // half that governs this glyph.
            tightened = false,
            // Unbounded in both directions now that the box it sits in
            // is an em tall rather than a line tall: the word measures
            // at its own line height and is centred on the box, which
            // is the line's own centre.
            modifier = Modifier.wrapContentSize(unbounded = true),
        )
        val rubyModifier = Modifier.graphicsLayer {
            translationY = -(
                style.fontSize.toPx() * 0.5f +
                    rubyFontSize.toPx() * 0.5f +
                    rubyGap.toPx() +
                    when (style.lineHeightStyle?.alignment) {
                        LineHeightStyle.Alignment.Top -> -style.fontSize.toPx() * 0.5f
                        LineHeightStyle.Alignment.Bottom -> style.fontSize.toPx() * 0.5f
                        else -> 0f
                    }
                )
        }
        if (perCharacter) {
            Row(
                modifier = rubyModifier.fillMaxWidth(),
            ) {
                // One cell per kana, so a highlight that covers part of
                // the reading follows it into the cells rather than
                // being lost on the way through.
                reading.forEach { piece ->
                    val cellStyle = rubyStyle.merge(letterSpacing = 0.sp).let {
                        if (piece.highlighted) it.merge(highlightStyle) else it
                    }
                    piece.text.forEach { character ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                text = character.toString(),
                                modifier = Modifier.wrapContentSize(unbounded = true),
                                style = cellStyle,
                                softWrap = false,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = rubyModifier) {
                PieceRow(
                    pieces = reading,
                    style = rubyStyle,
                    highlightStyle = highlightStyle,
                    // The reading half keeps the tight metrics: it is
                    // positioned against the base rather than against
                    // the line, and the vertical padding a platform
                    // text view can be told to drop is padding the
                    // ruby cannot afford.
                    tightened = true,
                    modifier = Modifier.wrapContentSize(unbounded = true),
                )
            }
        }
    }
}

/**
 * One half of a ruby unit: its pieces side by side, each in its own
 * style.
 *
 * A half with nothing highlighted, or highlighted throughout, is one
 * piece and draws through the same single text call it always did —
 * only a partial highlight puts a row around it, so the ordinary case
 * keeps the metrics it was tuned with.
 *
 * [tightened] chooses which of two text renderers draws it, and the two
 * halves want opposite things. The reading is placed against the base
 * beneath it and has no room for the vertical padding Android adds, so
 * it goes through [TextSpacingRemoved], which drops it. The word is
 * placed against the LINE — it has to sit exactly where the same
 * characters sit in an ordinary piece beside it — so it goes through
 * the same [BasicText] an ordinary piece does, with the same style, and
 * is thereby laid out by the same engine against the same font
 * fallbacks. Nothing else can guarantee they agree; two renderers
 * asked politely to match will not.
 */
@Composable
private fun PieceRow(
    pieces: List<Piece>,
    style: TextStyle,
    highlightStyle: SpanStyle,
    tightened: Boolean,
    modifier: Modifier = Modifier,
) {
    val single = pieces.singleOrNull()
    if (single != null) {
        PieceText(
            text = single.text,
            style = if (single.highlighted) style.merge(highlightStyle) else style,
            tightened = tightened,
            modifier = modifier,
        )
        return
    }
    Row(modifier = modifier) {
        pieces.forEach { piece ->
            PieceText(
                text = piece.text,
                style = if (piece.highlighted) style.merge(highlightStyle) else style,
                tightened = tightened,
                modifier = Modifier.wrapContentWidth(unbounded = true),
            )
        }
    }
}

@Composable
private fun PieceText(
    text: String,
    style: TextStyle,
    tightened: Boolean,
    modifier: Modifier = Modifier,
) {
    if (tightened) {
        TextSpacingRemoved(text = text, style = style, modifier = modifier)
    } else {
        BasicText(
            text = text,
            modifier = modifier,
            style = style,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
    }
}

/**
 * The width [text] lays out to, in sp — the unit [Placeholder] takes,
 * and the only reason this is measured at all: the placeholder has to be
 * reserved before either half of the unit is drawn.
 */
private fun TextMeasurer.widthInSp(
    text: String,
    style: TextStyle,
    density: Density,
): Float = measure(
    text = text,
    style = style,
    softWrap = false,
    overflow = TextOverflow.Visible,
).size.width.toFloat() / density.density
