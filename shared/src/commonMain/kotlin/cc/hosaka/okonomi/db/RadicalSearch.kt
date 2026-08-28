package cc.hosaka.okonomi.db

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One kanji on the radical screen: the character itself and the two
 * kanjidic2 columns the grid's order is built from.
 *
 * Neither number is drawn — the grid shows characters — but both are
 * carried because they are what the SQL sorted by, so a reader of this
 * type can see why one character precedes another without going back to
 * the query. [strokeCount] is not nullable: `kanji.stroke_count` is NOT
 * NULL and the lookup inner-joins `kanji`, so a row either supplies one
 * or is not in the result at all.
 */
@Immutable
data class KanjiHit(
    val literal: String,
    val strokeCount: Long,
    val freq: Long?,
)

/**
 * Every kanji the dictionary builds from [radical], in the grid's order.
 *
 * The whole set, in one read, with no offset and no limit: the payload
 * is single characters and the worst radical in the shipped data yields
 * 1,337 of them, which a recycling grid holds without paging. See
 * `kanjiContainingRadical` in kanji.sq for the order and for why no
 * index is added to reach it.
 *
 * Reached only by tapping a radical. Nothing here is wired to the search
 * field, so no query a reader types can issue this.
 *
 * This is where the argument is checked, once: the receiver form below
 * takes whatever it is handed, so a caller cannot reach the database
 * through a guard that is only stated twice.
 */
suspend fun kanjiContainingRadical(radical: String): List<KanjiHit> {
    require(radical.isNotEmpty()) { "radical must not be empty" }
    val database = dictionary()
    // Same reasoning as loadKanjiForWord: no common IO dispatcher exists
    // here and the query is short-lived, so Default keeps the
    // synchronous SQLite work off the main thread.
    return withContext(Dispatchers.Default) {
        database.kanjiContainingRadical(radical)
    }
}

/**
 * The database half, on an explicit receiver so a test can seed a file
 * and read it back without the app-lifetime handle.
 *
 * The radical is passed through exactly as radkfile stated it — the same
 * representative kanji the chip showed, never a CJK Radical Supplement
 * rewrite of it — because that is what `kanji_radical.radical` holds.
 *
 * Unguarded on purpose: the empty-argument check belongs to the public
 * entry above, which is the only way production code reaches this.
 */
suspend fun DictionaryDatabase.kanjiContainingRadical(radical: String): List<KanjiHit> =
    db.kanjiQueries.kanjiContainingRadical(radical).awaitList().map {
        KanjiHit(literal = it.literal, strokeCount = it.stroke_count, freq = it.freq)
    }
