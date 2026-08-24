package cc.hosaka.okonomi.lang

/**
 * Kana to a wapuro-flavoured Hepburn display romanization.
 *
 * "Wapuro-flavoured" means no macrons and no other diacritics, so a
 * long vowel is written as the plain sequence it is spelled with (そう
 * is "sou", not "sō") — close to what a Japanese IME accepts back.
 * Hepburn spellings are kept for the consonants a reader expects them
 * on (し "shi", つ "tsu", じ "ji", ちゃ "cha").
 *
 * This is a reading aid, not a round-trip transliteration: ー is
 * resolved to the vowel it lengthens (ラーメン → "raamen"), and ぢ/づ
 * are written "ji"/"zu" like じ/ず, so typing the output back does not
 * always reproduce the input.
 *
 * Full-width and half-width katakana are both accepted and converted
 * through their hiragana equivalents. Anything else — spaces, middle
 * dots, iteration marks, latin text — passes through unchanged.
 */
fun toRomaji(kana: String): String {
    val source = normalizeKana(kana)
    val out = StringBuilder()
    var index = 0
    var pendingSokuon = false
    var lastVowel: Char? = null
    while (index < source.size) {
        val char = source[index]
        when {
            char == SOKUON -> {
                pendingSokuon = true
                index++
            }

            char == MORAIC_N -> {
                out.append('n')
                // n' keeps ん apart from the な row and from ny-: without
                // it しんゆう and しにゅう would both read "shinyuu".
                if (needsApostrophe(source.getOrNull(index + 1))) {
                    out.append('\'')
                }
                // ん can carry no gemination and lengthens no vowel, so
                // neither state may survive it.
                pendingSokuon = false
                lastVowel = null
                index++
            }

            char == PROLONGED_SOUND_MARK -> {
                // With no vowel to lengthen (start of string, after ん or
                // after non-kana) the mark is kept rather than dropped:
                // a character that vanishes reads as a converter bug.
                out.append(lastVowel ?: char)
                pendingSokuon = false
                index++
            }

            else -> {
                val digraph = if (index + 1 < source.size) {
                    val pair = "" + char + source[index + 1]
                    SYLLABLES[pair]?.let { pair to it }
                } else {
                    null
                }
                val syllable = digraph?.second ?: SYLLABLES["" + char]
                if (syllable == null) {
                    // Not kana (latin text, iteration marks, punctuation):
                    // emit it as it stands and forget the vowel so a
                    // following ー cannot lengthen across it.
                    out.append(char)
                    pendingSokuon = false
                    lastVowel = null
                    index++
                } else {
                    if (pendingSokuon) {
                        out.append(geminateOf(syllable))
                        pendingSokuon = false
                    }
                    out.append(syllable)
                    lastVowel = syllable.lastOrNull()?.takeIf { it in VOWELS }
                    index += if (digraph != null) 2 else 1
                }
            }
        }
    }
    // A trailing っ (a glottal stop at the end of an exclamation) has no
    // consonant to double; it is simply not written.
    return out.toString()
}

private const val SOKUON = 'っ'
private const val MORAIC_N = 'ん'
private const val PROLONGED_SOUND_MARK = 'ー'
private const val VOWELS = "aiueo"

/**
 * The input as hiragana: full-width katakana is shifted down to its
 * hiragana equivalent, half-width katakana is folded to it (its
 * standalone voicing marks combine with the character before them), and
 * everything else — the prolonged sound mark, iteration marks,
 * punctuation, latin text — is left exactly as it stands.
 */
private fun normalizeKana(text: String): List<Char> {
    val normalized = ArrayList<Char>(text.length)
    text.forEach { char ->
        val previous = normalized.lastOrNull()
        val combined = if (isVoicingMark(char) && previous != null) voiced(previous, char) else null
        if (combined != null) {
            normalized[normalized.lastIndex] = combined
        } else {
            normalized += toHiragana(fromHalfwidth(char))
        }
    }
    return normalized
}

private fun toHiragana(char: Char): Char = when (char.code) {
    // The katakana block mirrors hiragana at a fixed offset, up to ヶ.
    in 0x30A1..0x30F6 -> (char.code - 0x60).toChar()
    else -> char
}

/** Half-width katakana (U+FF66-U+FF9D) to its full-width form. */
private fun fromHalfwidth(char: Char): Char {
    val index = char.code - 0xFF66
    return if (index in HALFWIDTH_KATAKANA.indices) HALFWIDTH_KATAKANA[index] else char
}

/** The voiced (or semi-voiced) form of [char], or null when it has none. */
private fun voiced(char: Char, mark: Char): Char? {
    val (plain, marked) = if (mark == SEMI_VOICING_MARK) {
        SEMI_VOICEABLE to SEMI_VOICED
    } else {
        VOICEABLE to VOICED
    }
    val index = plain.indexOf(char)
    return if (index >= 0) marked[index] else null
}

/** The standalone half-width voicing marks, U+FF9E and U+FF9F. */
private fun isVoicingMark(char: Char): Boolean = char == VOICING_MARK || char == SEMI_VOICING_MARK

private const val VOICING_MARK = 'ﾞ'
private const val SEMI_VOICING_MARK = 'ﾟ'

private const val HALFWIDTH_KATAKANA =
    "ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン"

private const val VOICEABLE = "かきくけこさしすせそたちつてとはひふへほう"
private const val VOICED = "がぎぐげござじずぜぞだぢづでどばびぶべぼゔ"
private const val SEMI_VOICEABLE = "はひふへほ"
private const val SEMI_VOICED = "ぱぴぷぺぽ"

/**
 * The consonant a doubled consonant (っ) contributes before [syllable].
 * Hepburn writes っち as "tchi" rather than doubling the "c" of "chi".
 */
private fun geminateOf(syllable: String): String = when {
    syllable.startsWith("ch") -> "t"
    syllable.first() in VOWELS -> ""
    else -> syllable.take(1)
}

/** ん takes an apostrophe before a vowel or y, where it could be misread. */
private fun needsApostrophe(next: Char?): Boolean {
    if (next == null) return false
    val syllable = SYLLABLES["" + next] ?: return false
    return syllable.first() in VOWELS || syllable.first() == 'y'
}

private val SYLLABLES: Map<String, String> = buildMap {
    put("あ", "a"); put("い", "i"); put("う", "u"); put("え", "e"); put("お", "o")
    put("か", "ka"); put("き", "ki"); put("く", "ku"); put("け", "ke"); put("こ", "ko")
    put("が", "ga"); put("ぎ", "gi"); put("ぐ", "gu"); put("げ", "ge"); put("ご", "go")
    put("さ", "sa"); put("し", "shi"); put("す", "su"); put("せ", "se"); put("そ", "so")
    put("ざ", "za"); put("じ", "ji"); put("ず", "zu"); put("ぜ", "ze"); put("ぞ", "zo")
    put("た", "ta"); put("ち", "chi"); put("つ", "tsu"); put("て", "te"); put("と", "to")
    put("だ", "da"); put("ぢ", "ji"); put("づ", "zu"); put("で", "de"); put("ど", "do")
    put("な", "na"); put("に", "ni"); put("ぬ", "nu"); put("ね", "ne"); put("の", "no")
    put("は", "ha"); put("ひ", "hi"); put("ふ", "fu"); put("へ", "he"); put("ほ", "ho")
    put("ば", "ba"); put("び", "bi"); put("ぶ", "bu"); put("べ", "be"); put("ぼ", "bo")
    put("ぱ", "pa"); put("ぴ", "pi"); put("ぷ", "pu"); put("ぺ", "pe"); put("ぽ", "po")
    put("ま", "ma"); put("み", "mi"); put("む", "mu"); put("め", "me"); put("も", "mo")
    put("や", "ya"); put("ゆ", "yu"); put("よ", "yo")
    put("ら", "ra"); put("り", "ri"); put("る", "ru"); put("れ", "re"); put("ろ", "ro")
    put("わ", "wa"); put("ゐ", "wi"); put("ゑ", "we"); put("を", "wo")
    put("ゔ", "vu")
    // Small kana standing on their own (a digraph consumed them first).
    put("ぁ", "a"); put("ぃ", "i"); put("ぅ", "u"); put("ぇ", "e"); put("ぉ", "o")
    put("ゃ", "ya"); put("ゅ", "yu"); put("ょ", "yo"); put("ゎ", "wa")
    put("ゕ", "ka"); put("ゖ", "ke")

    put("きゃ", "kya"); put("きゅ", "kyu"); put("きょ", "kyo"); put("きぇ", "kye")
    put("ぎゃ", "gya"); put("ぎゅ", "gyu"); put("ぎょ", "gyo")
    put("しゃ", "sha"); put("しゅ", "shu"); put("しょ", "sho"); put("しぇ", "she")
    put("じゃ", "ja"); put("じゅ", "ju"); put("じょ", "jo"); put("じぇ", "je")
    put("ちゃ", "cha"); put("ちゅ", "chu"); put("ちょ", "cho"); put("ちぇ", "che")
    put("ぢゃ", "ja"); put("ぢゅ", "ju"); put("ぢょ", "jo")
    put("にゃ", "nya"); put("にゅ", "nyu"); put("にょ", "nyo")
    put("ひゃ", "hya"); put("ひゅ", "hyu"); put("ひょ", "hyo")
    put("びゃ", "bya"); put("びゅ", "byu"); put("びょ", "byo")
    put("ぴゃ", "pya"); put("ぴゅ", "pyu"); put("ぴょ", "pyo")
    put("みゃ", "mya"); put("みゅ", "myu"); put("みょ", "myo")
    put("りゃ", "rya"); put("りゅ", "ryu"); put("りょ", "ryo")
    // Loanword combinations, written in katakana but normalized to
    // hiragana before the lookup.
    put("ふぁ", "fa"); put("ふぃ", "fi"); put("ふぇ", "fe"); put("ふぉ", "fo"); put("ふゅ", "fyu")
    put("てぃ", "ti"); put("でぃ", "di"); put("とぅ", "tu"); put("どぅ", "du")
    put("うぃ", "wi"); put("うぇ", "we"); put("うぉ", "wo")
    put("ゔぁ", "va"); put("ゔぃ", "vi"); put("ゔぇ", "ve"); put("ゔぉ", "vo")
    put("つぁ", "tsa"); put("つぃ", "tsi"); put("つぇ", "tse"); put("つぉ", "tso")
    put("いぇ", "ye"); put("てゅ", "tyu"); put("でゅ", "dyu"); put("ゔゅ", "vyu"); put("ふょ", "fyo")
}
