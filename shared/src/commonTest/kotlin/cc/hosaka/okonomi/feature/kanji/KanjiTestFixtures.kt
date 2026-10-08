package cc.hosaka.okonomi.feature.kanji

/** 食, the character the kanji UI tests are written around. */
internal const val SHOKU_LITERAL = "食"

/**
 * The opening segment of each of 食's first two KanjiVG strokes. Truncated
 * rather than whole: valid path data is all the stroke-order slot needs to
 * draw, and the full diagram lives in `KanjiCardPreview`, which as main
 * code cannot share this file.
 */
internal const val SHOKU_FIRST_STROKE = "M52.75,10.5c0.11,0.98-0.19,2.67-0.97,3.93"

internal const val SHOKU_SECOND_STROKE = "M52.75,16.25c5.09,4.8,25.71,19.61,33.7,24.9"

internal val SHOKU_STROKES = listOf(SHOKU_FIRST_STROKE, SHOKU_SECOND_STROKE)
