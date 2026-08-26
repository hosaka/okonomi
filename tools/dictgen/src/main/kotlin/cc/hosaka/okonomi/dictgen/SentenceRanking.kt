package cc.hosaka.okonomi.dictgen

/**
 * Highest number of example sentences one entry keeps. The Phrases tab
 * shows the first thirty and pages through the rest, so the stored set
 * has to be larger than one screenful; the cap is still what keeps the
 * sentence tables from dwarfing the dictionary, because は is a word in
 * 99,947 of the corpus's sentences, 為る in 26,704 and 事 in 8,669.
 *
 * Was 10 while the tab showed everything it loaded. Raising it to 50
 * took entry_sentence from 122,407 links to 255,563 — 133,156 further
 * links, plus the sentence text those links keep alive — which is the
 * price of the tab no longer stopping at ten on the words a learner
 * looks up most.
 *
 * Raising it again has a limit nothing in either module would warn
 * about. The app's `loadBreakdownPos` asks the dictionary about a whole
 * entry's breakdown in one `IN (...)`, one bind variable per distinct
 * word, and SQLite's default `SQLITE_MAX_VARIABLE_NUMBER` is 32,766.
 * The widest entry in the shipped corpus (2076730, "ばかり") spends 361
 * of them, so there is roughly 90x headroom and no chunking is needed —
 * but a cap raised past about 4,500 sentences per entry would turn that
 * into a runtime "too many SQL variables" on one screen, with nothing
 * failing at build time to say so.
 */
const val SENTENCES_PER_ENTRY = 50

/**
 * The length band an example is preferred to fall in, in characters.
 *
 * Tuning values, and the first thing to revisit if the examples read
 * badly. The corpus median is 17 characters and its 90th percentile 27,
 * so this band is where ordinary sentences sit: below it they stop
 * being sentences (死ね！ is four characters and was, under pure
 * shortest-first, the leading example for 死ぬ), and above it they stop
 * being quick to read.
 *
 * A preference, never a filter — [SENTENCE_ORDER] sorts out-of-band
 * sentences after in-band ones rather than discarding them, so an entry
 * whose only examples are short or long still shows them.
 */
const val READABLE_LENGTH_MIN = 8

/** See [READABLE_LENGTH_MIN]. */
const val READABLE_LENGTH_MAX = 20

/**
 * One candidate example for one entry, before the cap is applied.
 *
 * [checked] is Tatoeba's tilde as it applies to THIS entry: the mark
 * sits on individual words, so a sentence an editor verified as an
 * example of 犬 says nothing about its worth as an example of 食べる.
 */
data class SentenceLink(
    val sentenceId: Long,
    val length: Int,
    val checked: Boolean,
    /** The near-duplicate key of the sentence; see [SentenceKey]. */
    val key: String,
) {
    val inReadableBand: Boolean
        get() = length in READABLE_LENGTH_MIN..READABLE_LENGTH_MAX
}

/**
 * Which sentences an entry keeps, and in what order:
 *
 * 1. Inside the readable length band ahead of outside it.
 * 2. Verified for this entry ahead of unverified.
 * 3. Shorter ahead of longer, so the crisper of two equally good
 *    sentences still wins.
 * 4. By sentence id, so the choice is stable from one run to the next.
 *
 * Pure ascending length, which this replaced, produced a monotonous set
 * — 食べる's ten were all 7 to 9 characters — and led 死ぬ with 死ね！.
 */
val SENTENCE_ORDER: Comparator<SentenceLink> = compareBy<SentenceLink> { !it.inReadableBand }
    .thenByDescending { it.checked }
    .thenBy { it.length }
    .thenBy { it.sentenceId }

/**
 * Collapses sentences that differ only in how they end.
 *
 * The corpus carries near-identical pairs — 教室で食べるの。 and
 * 教室で食べるの？ — and showing both wastes two of an entry's slots on
 * one sentence. Trailing punctuation is stripped and the rest
 * compared exactly: deliberately not fuzzy matching, which would start
 * silently discarding sentences that genuinely differ.
 */
object SentenceKey {

    private const val SENTENCE_FINAL_MARKS = "。．.！!？?…‥～~"

    fun of(japanese: String): String =
        japanese.trimEnd { it.isWhitespace() || it in SENTENCE_FINAL_MARKS }
}

/**
 * The best [limit] sentences seen so far for one entry, with
 * near-duplicates collapsed.
 *
 * Bounded on purpose. Collecting every candidate and sorting at the end
 * would mean holding 1.17 million links and sorting a 99,947-element
 * list for は alone; here both the memory and the work per candidate
 * stay proportional to the cap, and the common case for a frequent word
 * — a sentence no better than the ones already kept — costs one
 * comparison.
 */
class TopSentences(private val limit: Int) {

    private val kept = ArrayList<SentenceLink>(limit + 1)
    private val byKey = HashMap<String, SentenceLink>()

    fun offer(candidate: SentenceLink) {
        val duplicate = byKey[candidate.key]
        when {
            duplicate != null -> {
                if (SENTENCE_ORDER.compare(candidate, duplicate) >= 0) return
                kept.remove(duplicate)
                byKey.remove(duplicate.key)
            }

            kept.size == limit && SENTENCE_ORDER.compare(candidate, kept.last()) >= 0 -> return
        }
        insert(candidate)
        byKey[candidate.key] = candidate
        if (kept.size > limit) {
            byKey.remove(kept.removeAt(kept.lastIndex).key)
        }
    }

    /** The kept sentences, best first. */
    fun ordered(): List<SentenceLink> = kept.toList()

    private fun insert(link: SentenceLink) {
        val found = kept.binarySearch(link, SENTENCE_ORDER)
        kept.add(if (found < 0) -(found + 1) else found, link)
    }
}
