package cc.hosaka.okonomi.feature.phrases

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.BreakdownPos
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence
import cc.hosaka.okonomi.ui.PagingFooterState

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
 * Whether [word] is a word worth looking up — a content word rather
 * than part of the sentence's machinery.
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
 * the text clause alone. The word's dictionary form is what is asked
 * about, never the surface the sentence spells it with: 食べない is
 * 食べる's business, and asking about the inflected form would find
 * nothing at all.
 *
 * This is the dictionary's question and it decides how the word is
 * DRAWN. Whether a tap on it goes anywhere is the screen's, and it is
 * [opensSearch]; the two used to be one function and the cost was the
 * word the reader came to study drawn in the colour reserved for
 * particles.
 *
 * Pure, and stated here rather than inline in the tab, on the precedent
 * of the search's `titleFurigana`: what a reader can tap is a rule
 * worth testing without composing anything.
 */
internal fun isBreakdownWordTappable(word: BreakdownWord, pos: BreakdownPos): Boolean {
    val entryPos = word.entryId?.let { pos.byEntryId[it] }.orEmpty()
    // An entry the dictionary states no part of speech for says
    // nothing either way, and must not read as "entirely grammatical".
    if (entryPos.isNotEmpty() && entryPos.all { it in breakdownGrammaticalPos }) return false
    return pos.byText[word.text].orEmpty().none { it in breakdownGrammaticalTextPos }
}

/**
 * Whether tapping [word] takes the reader anywhere, on a tab showing
 * the examples of [wordBeingRead].
 *
 * A content word normally does. The exception is the word the entry is
 * about: a tap searches for the word's dictionary form, so on 私's own
 * examples every 私 in them would search 私 and land the reader back on
 * the page they are already reading. Only the tap goes — the word is
 * part of the sentence, is drawn like the content word it is, and keeps
 * its furigana.
 *
 * **By spelling, and not by the entry Tatoeba linked.** The spelling is
 * what a tap searches for and what the reader can see; the link is
 * neither. Deciding it by entry id would leave two identical 私 in one
 * sentence behaving differently with nothing on screen to tell them
 * apart — exactly the "inconsistency with no perceivable pattern" the
 * text clause of [isBreakdownWordTappable] exists to prevent.
 *
 * An empty [wordBeingRead] suppresses nothing, which is what a caller
 * with no entry in hand means; no breakdown word is spelled with it.
 */
internal fun opensSearch(
    word: BreakdownWord,
    tappableWords: Set<BreakdownWord>,
    wordBeingRead: String,
): Boolean = word in tappableWords && word.text != wordBeingRead

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
 * That last point is why `EntryDetail.hasSentences` exists: the tab bar
 * needs the answer before it draws, so the entry load carries it. With
 * it, [Empty] is **not reachable through the tab bar any more** — the
 * tab is hidden instead. Kept rather than deleted because the flag and
 * this state come from two different queries over the same table, and
 * if they ever disagree the reader should get the sentence rather than
 * a blank screen.
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
         * is this screen's. Membership is by value, so two occurrences
         * of one word are one member — one word inflected two ways is
         * two, because the surface is part of the word's value, and the
         * rule reads only the dictionary form and the entry id, so both
         * get the same answer.
         */
        val tappableWords: Set<BreakdownWord> = emptySet(),
        /**
         * The dictionary form of the entry these examples belong to.
         * A piece of a sentence spelled the same is drawn like any
         * other content word and opens no search; see [opensSearch].
         *
         * Empty suppresses nothing, which is what a fixture that states
         * no entry means. The producer always states one.
         */
        val wordBeingRead: String = "",
        /**
         * The part-of-speech codes of the entries the words were linked
         * to, keyed by entry id — the same answer [isBreakdownWordTappable]
         * is decided from, carried on rather than thrown away.
         *
         * The second reader is the furigana. Whether 来 may be given
         * the reading its dictionary form states is a question only the
         * paradigm can answer, and the paradigm is selected by these
         * codes; see `SentenceFurigana.kt`. Raw JMdict codes reach the
         * screen for the same reason [BreakdownPos] does not stop at
         * the producer: what a word is is the dictionary's business,
         * and both rules that read it are this screen's.
         *
         * Empty when the part-of-speech query failed, which is the same
         * run in which nothing is tappable.
         */
        val entryPos: Map<Long, List<String>> = emptyMap(),
        val onShowMore: (() -> Unit)? = null,
        val footer: PagingFooterState = PagingFooterState.None,
    ) : PhrasesTabContentState

    data class Error(
        val onRetry: (() -> Unit)? = null,
    ) : PhrasesTabContentState
}
