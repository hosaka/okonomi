package cc.hosaka.okonomi.feature.navigation

import cc.hosaka.okonomi.db.isJapaneseCodePoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The longest query a text lookup opens Search with.
 *
 * The query lives in the pushed `SearchRoute`, and the route in the back
 * stack's saved state. A whole page selected and looked up could push
 * that saved Bundle past Binder's limit, and Android then crashes the app
 * with `TransactionTooLargeException` as it stops.
 */
const val SELECTION_QUERY_MAX_LENGTH = 256

/**
 * The Search query for [text] selected in an app, or null when it holds
 * nothing to search for.
 *
 * Selections come with whatever surrounded the words: line breaks, tabs,
 * runs of spaces, the ideographic space. Between two Japanese characters
 * (kana or han, as Search counts them) such a run is removed: Japanese
 * puts no spaces between words, so a run there is layout, typically a
 * line break inside a sentence, and a space would leave a query Search
 * cannot match. Anywhere else each run becomes one space, and the ends
 * are trimmed.
 *
 * Format characters are dropped wherever they are. They draw nothing
 * (zero-width space, soft hyphen, byte order mark, bidi marks), so the
 * reader cannot see them in the field, yet any one of them keeps a query
 * from matching.
 *
 * The result is capped at [SELECTION_QUERY_MAX_LENGTH], and the cap
 * never splits a surrogate pair (an emoji or a rare kanji), which would
 * leave half a character at the end of the field.
 */
fun selectionQuery(text: CharSequence): String? {
    val query = StringBuilder()
    var spaceDue = false
    var lastWasJapanese = false
    var index = 0
    // A selection can be a whole page; nothing past the cap is kept, so
    // nothing past it needs reading.
    while (index < text.length && query.length <= SELECTION_QUERY_MAX_LENGTH) {
        val start = index
        val char = text[index]
        val code = if (char.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
            ((char.code - 0xD800) shl 10) + (text[index + 1].code - 0xDC00) + 0x10000
        } else {
            char.code
        }
        index += if (code > 0xFFFF) 2 else 1
        when {
            char.isWhitespace() -> spaceDue = query.isNotEmpty()

            char.category == CharCategory.FORMAT -> Unit

            else -> {
                val isJapanese = isJapaneseCodePoint(code)
                if (spaceDue && !(lastWasJapanese && isJapanese)) query.append(' ')
                spaceDue = false
                query.appendRange(text, start, index)
                lastWasJapanese = isJapanese
            }
        }
    }
    var end = minOf(query.length, SELECTION_QUERY_MAX_LENGTH)
    if (end > 0 && query[end - 1].isHighSurrogate()) end--
    return query.substring(0, end).trimEnd().ifEmpty { null }
}

/**
 * One lookup waiting to be opened.
 *
 * @property query the tidied selection; see [selectionQuery].
 * @property fromOkonomi true when the text was selected in okonomi
 * itself, whose own text menus offer "Okonomi" too. Such a lookup is an
 * ordinary search pushed over the screen being read, not a hand-off from
 * another app: no tab switch, nothing popped, and back stays in the app.
 */
data class TextLookup(
    val query: String,
    val fromOkonomi: Boolean,
)

/**
 * Text selected in an app and handed in by the platform (Android's
 * "Okonomi" item in the selection toolbar), waiting for the shell to
 * look it up. Text that arrives before the shell is composed (a cold
 * start) waits for it. Of several waiting, only the newest is kept (the
 * channel is conflated), which the reader cannot tell from both being
 * opened in turn: each lookup from another app drops Search to its root
 * before pushing.
 *
 * A second queue beside [EntryLinks] rather than a generalised one, so
 * the shipped link code and its tests stay untouched. Ordering between
 * the two would only matter if both had something waiting before the
 * shell listens, and a cold start carries a single intent, so that
 * cannot happen.
 */
class TextLookups {
    private val pending = Channel<TextLookup>(Channel.CONFLATED)

    /** The lookups to open, each delivered once, to one collector. */
    val requests: Flow<TextLookup> = pending.receiveAsFlow()

    /**
     * Queues [text] as a query (see [selectionQuery]), selected in
     * okonomi itself when [fromOkonomi]; returns whether there was
     * anything in it to look up.
     */
    fun lookUp(text: CharSequence, fromOkonomi: Boolean = false): Boolean {
        val query = selectionQuery(text) ?: return false
        pending.trySend(TextLookup(query, fromOkonomi))
        return true
    }
}

/**
 * The app-lifetime queue the Android activity forwards selected text
 * into and `HomeScreen` opens it from.
 */
val appTextLookups: TextLookups = TextLookups()
