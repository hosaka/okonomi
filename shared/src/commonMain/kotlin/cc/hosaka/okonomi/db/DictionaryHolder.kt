package cc.hosaka.okonomi.db

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-lifetime holder of the open dictionary database. Provisioning and
 * opening happen once, on the first [dictionary] call; every later call
 * returns the same handle without touching the disk. The handle is never
 * closed for the life of the app.
 *
 * Single-flight: concurrent first calls are serialized on one mutex, so
 * provisioning and opening can never run twice. A failed attempt leaves
 * nothing memoized, so the next call retries from scratch.
 */
internal class DictionaryHolder(
    private val provision: suspend () -> String = ::provisionDictionary,
    private val open: (String) -> DictionaryDatabase = ::openOkonomiDb,
) {
    private val mutex = Mutex()
    private var opened: DictionaryDatabase? = null

    suspend fun dictionary(): DictionaryDatabase = mutex.withLock {
        opened ?: open(provision()).also { opened = it }
    }

    /**
     * Drops the memoized handle so the next [dictionary] call
     * provisions and opens again. Part of the self-heal paths; never
     * called in normal operation.
     *
     * The old handle is deliberately NOT closed: another thread may
     * still be mid-query on it, and pulling the connection out from
     * under an in-flight query is not exception-safe. The database is
     * read-only and this path is rare, so the abandoned handle is
     * simply left for the process to reclaim.
     */
    suspend fun invalidate() {
        mutex.withLock {
            opened = null
        }
    }
}

private val sharedDictionaryHolder = DictionaryHolder()

/**
 * The shared app-lifetime dictionary handle. Suspends while the first
 * caller provisions and opens the database; rethrows provisioning or
 * open failures without memoizing them, so a later call retries.
 */
suspend fun dictionary(): DictionaryDatabase = sharedDictionaryHolder.dictionary()

internal suspend fun invalidateDictionary() {
    sharedDictionaryHolder.invalidate()
}
