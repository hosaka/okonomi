package cc.hosaka.okonomi.feature.phrases

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.BreakdownPos
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.ui.PagingFooterState

private const val WORD_PLACEHOLDER = "%1\$s"

private const val READING_PLACEHOLDER = "%2\$s"

/**
 * One word of the breakdown as the reader sees it: the word alone when
 * it reads as itself, or the word followed by its reading.
 *
 * Pure and stated here rather than inline in the tab, on the precedent
 * of the search's `titleLine`. This is the only place the readings
 * resolved at build time become visible to anyone, so a rendering that
 * dropped them would undo the feature end to end with every other test
 * still green.
 *
 * [readingFormat] is the caller's `%1$s (%2$s)` template: bracketing a
 * reading is a typographic convention that belongs in `strings.xml`
 * beside the rest of the wording, not in the code.
 */
internal fun breakdownWordLabel(word: BreakdownWord, readingFormat: String): String {
    val reading = word.reading ?: return word.text
    return readingFormat
        .replace(WORD_PLACEHOLDER, word.text)
        .replace(READING_PLACEHOLDER, reading)
}

/**
 * The JMdict part-of-speech codes that make a word part of the
 * sentence's machinery rather than of what it says. An entry tagged
 * with nothing but these is a grammar word whichever sense the reader
 * met.
 *
 * A set of tag names and never a list of words, deliberately: the
 * particles worth filtering change when the dictionary's entries are
 * fixed, and a hand-kept word list would be wrong the day after it was
 * written.
 *
 * The cost of naming tags instead is that a *misspelt* or retired tag
 * is inert rather than wrong — it filters nothing and says nothing.
 * `cop-da` sat here until it was measured: JMdict retired it in favour
 * of `cop`, the shipped dictionary carries it zero times, and the test
 * that was supposed to guard the copula was feeding it that dead code.
 * `BreakdownPosCodesTest` now checks every code here against the
 * dictionary that ships, which is the only thing that can catch this.
 *
 * Internal rather than private so that test can read the real sets
 * instead of a copy of them, which would go stale in the same silence.
 */
internal val breakdownGrammaticalPos = setOf("prt", "cop", "aux", "aux-v", "aux-adj", "conj")

/**
 * The narrower set the text clause tests. It is not
 * [breakdownGrammaticalPos] because `aux-v` is a *secondary* sense of
 * the commonest verbs in the language — 為る and 有る both carry it — so
 * asking the text clause about auxiliaries would make する untappable,
 * which is worse than the problem the clause exists to solve. A copula
 * or particle spelled exactly like the word, on the other hand, is
 * decisive.
 */
internal val breakdownGrammaticalTextPos = setOf("prt", "cop")

/**
 * Whether tapping [word] should open a search for it.
 *
 * Two clauses, and both are needed:
 *
 * - **The entry it was linked to is entirely grammatical.** This is the
 *   honest question, and it is the only one that reaches a grammar word
 *   spelled like nothing else — なのだ, 為さい, 可き.
 * - **Something spelled exactly like it is a particle or a copula.**
 *   Tatoeba's index links single-kana particles to homographic content
 *   words — は to 葉 (`n`), に to 二 (`num`) — so the first clause alone
 *   leaves は tappable while removing を, an inconsistency with no
 *   perceivable pattern. JMdict is not wrong here and no JMdict fix
 *   would correct it; the error is in a separately maintained source.
 *
 * A word that resolved to no entry (0.4% of the corpus) is decided by
 * the text clause alone. Pure, and stated here rather than inline in
 * the tab, on the precedent of [breakdownWordLabel]: what a reader can
 * tap is a rule worth testing without composing anything.
 */
internal fun isBreakdownWordTappable(word: BreakdownWord, pos: BreakdownPos): Boolean {
    val entryPos = word.entryId?.let { pos.byEntryId[it] }.orEmpty()
    // An entry the dictionary states no part of speech for says
    // nothing either way, and must not read as "entirely grammatical".
    if (entryPos.isNotEmpty() && entryPos.all { it in breakdownGrammaticalPos }) return false
    return pos.byText[word.text].orEmpty().none { it in breakdownGrammaticalTextPos }
}

@Immutable
data class PhrasesTabState(
    val content: PhrasesTabContentState = PhrasesTabContentState.Loading,
)

/**
 * The body of the entry view's Phrases tab.
 *
 * [Empty] is its own state rather than an empty [Ready] because it is
 * the ordinary outcome, not a degraded one: the Tatoeba corpus covers
 * about 14% of the dictionary, so most entries reach it, and it must
 * read as a normal condition rather than as a failure. Unlike the Kanji
 * tab's NoKanji it cannot be settled before the query — whether a word
 * has examples is a property of the data, not of the headword.
 *
 * A null [Error.onRetry] means retrying is not offered, following the
 * project's "null callback is a disabled action" rule.
 */
@Immutable
sealed interface PhrasesTabContentState {
    data object Loading : PhrasesTabContentState

    data object Empty : PhrasesTabContentState

    /**
     * [sentences] is a page of the entry's stored set, not all of it:
     * dictgen keeps up to fifty per entry and the tab shows the first
     * [PHRASES_PAGE_SIZE], extending as the reader scrolls. A null
     * [onShowMore] means the stored set is exhausted.
     *
     * [footer] is what the reader is told below the last row, and it is
     * separate from [onShowMore] on purpose: a null callback cannot
     * distinguish "there is nothing further" from "there is something
     * further and it is arriving". An entry whose examples all fit is
     * [PagingFooterState.None] and shows nothing at all under them.
     *
     * Because a page is always a prefix of one already-ordered list,
     * extending it can never reorder a sentence the reader is looking
     * at.
     */
    data class Ready(
        val sentences: List<ExampleSentence>,
        /**
         * The breakdown words a tap opens a search for, decided by
         * [isBreakdownWordTappable] over the entry's whole stored set
         * once rather than per page.
         *
         * A set of words rather than a flag on each word: what a word
         * is is the dictionary's business, and whether it can be tapped
         * is this screen's. Two occurrences of one word are one member,
         * which is exactly what a value-equal set gives.
         */
        val tappableWords: Set<BreakdownWord> = emptySet(),
        val onShowMore: (() -> Unit)? = null,
        val footer: PagingFooterState = PagingFooterState.None,
    ) : PhrasesTabContentState

    data class Error(
        val onRetry: (() -> Unit)? = null,
    ) : PhrasesTabContentState
}
