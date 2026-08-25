package cc.hosaka.okonomi.feature.phrases

import androidx.compose.runtime.Immutable
import cc.hosaka.okonomi.db.BreakdownWord
import cc.hosaka.okonomi.db.ExampleSentence

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

    data class Ready(
        val sentences: List<ExampleSentence>,
    ) : PhrasesTabContentState

    data class Error(
        val onRetry: (() -> Unit)? = null,
    ) : PhrasesTabContentState
}
