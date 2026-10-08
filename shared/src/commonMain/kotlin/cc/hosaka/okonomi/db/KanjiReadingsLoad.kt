package cc.hosaka.okonomi.db

import cc.hosaka.okonomi.ui.furigana.KanjiReadings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Every character's on and kun readings, read from the bundled
 * dictionary in one query.
 *
 * Some 37,000 short rows over 13,000 characters: read once, it is cheap
 * enough that nothing is precomputed into the dictionary for it (which
 * would take a format bump and a re-copy of the whole database on every
 * device).
 */
suspend fun DictionaryDatabase.loadKanjiReadings(): KanjiReadings {
    val builder = KanjiReadings.Builder()
    db.kanjiQueries.onAndKunReadings().awaitList().forEach { row ->
        builder.add(row.kanji, row.type, row.text)
    }
    return builder.build()
}

/**
 * Process-wide single-flight memo over [load]: the first caller loads,
 * every later one gets the same value, and a load that fails is not
 * kept, so a later call tries again.
 *
 * A failure other than cancellation calls [invalidate] before it is
 * rethrown, as the screens' own loads do: the shared dictionary handle
 * may be the thing that failed, and the next attempt should open it
 * afresh rather than fail on the same handle.
 */
internal class KanjiReadingsHolder(
    private val invalidate: suspend () -> Unit = { invalidateDictionary() },
    private val load: suspend () -> KanjiReadings,
) {
    private val mutex = Mutex()
    private var loaded: KanjiReadings? = null

    suspend fun kanjiReadings(): KanjiReadings = mutex.withLock {
        loaded ?: loadOrInvalidate().also { loaded = it }
    }

    private suspend fun loadOrInvalidate(): KanjiReadings = try {
        load()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        invalidate()
        throw failure
    }
}

private val sharedKanjiReadingsHolder = KanjiReadingsHolder {
    val database = dictionary()
    // Same reasoning as loadKanjiForWord: Default keeps the synchronous
    // SQLite work off the main thread.
    withContext(Dispatchers.Default) {
        database.loadKanjiReadings()
    }
}

/** The app's kanji readings, loaded on the first call and kept for the life of the process. */
suspend fun appKanjiReadings(): KanjiReadings = sharedKanjiReadingsHolder.kanjiReadings()
