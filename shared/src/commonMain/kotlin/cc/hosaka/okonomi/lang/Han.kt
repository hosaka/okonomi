package cc.hosaka.okonomi.lang

/**
 * True for a codepoint that is a kanji the app treats as its own
 * character: one that routes a query to the Japanese search path, and
 * one that earns a card in the entry view's Kanji tab. Both consumers
 * read this single rule so they cannot drift apart.
 *
 * This is deliberately NARROWER than the JVM's
 * `Character.UnicodeScript.HAN`, which dictgen uses to fill
 * `entry_kanji`. The JVM predicate also calls the iteration mark 々
 * (U+3005), the ideographic number zero 〇 (U+3007), the Hangzhou
 * numerals (U+3021..U+3029), CJK Radicals Supplement and Kangxi
 * Radicals Han. Those are marks and radical forms rather than
 * characters a learner looks up, so they get no card here.
 *
 * The divergence is harmless because the two answer different
 * questions. dictgen's rows feed `entry_kanji`, which only ever backs
 * `wordsContainingKanji` — a row for 々 is a lookup key nothing asks
 * for, not a wrong answer. This predicate decides what the reader sees.
 */
fun isHanCodePoint(code: Int): Boolean = when (code) {
    in 0x3400..0x4DBF, // CJK unified ideographs extension A
    in 0x4E00..0x9FFF, // CJK unified ideographs
    in 0xF900..0xFAFF, // CJK compatibility ideographs
    // Extensions B through H plus the compatibility supplement, as one
    // span: the unassigned gaps inside it hold no character any source
    // can produce, and a character outside the dictionary degrades to a
    // card with no data rather than to a wrong one.
    in 0x20000..0x323AF,
    -> true

    else -> false
}

/**
 * The distinct Han characters of [text] in order of first appearance,
 * each as its own string so a supplementary-plane character stays whole.
 * Kana, punctuation, iteration marks and latin are skipped, so 食べ物
 * yields 食 and 物 and 日々 yields 日 alone.
 */
fun hanCharacters(text: String): List<String> {
    val characters = LinkedHashSet<String>()
    forEachCodePoint(text) { code ->
        if (isHanCodePoint(code)) {
            characters += codePointToString(code)
        }
        true
    }
    return characters.toList()
}

/**
 * Runs [action] over the codepoints of [text], pairing surrogates, and
 * stops as soon as it returns false.
 */
internal fun forEachCodePoint(
    text: String,
    action: (Int) -> Boolean,
) {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        val code: Int
        if (char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
            code = ((char.code - 0xD800) shl 10) + (text[index + 1].code - 0xDC00) + 0x10000
            index += 2
        } else {
            code = char.code
            index += 1
        }
        if (!action(code)) return
    }
}

private const val SUPPLEMENTARY_BASE = 0x10000

private fun codePointToString(code: Int): String = if (code < SUPPLEMENTARY_BASE) {
    code.toChar().toString()
} else {
    val offset = code - SUPPLEMENTARY_BASE
    charArrayOf(
        (0xD800 + (offset shr 10)).toChar(),
        (0xDC00 + (offset and 0x3FF)).toChar(),
    ).concatToString()
}
