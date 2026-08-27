package cc.hosaka.okonomi.user

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-lifetime holder of the open user database, shaped after
 * `DictionaryHolder`: opening happens once, on the first [database]
 * call, and the handle is never closed for the life of the app.
 *
 * Single-flight, so two screens asking at the same moment cannot open two
 * connections to the same file. A failed attempt memoizes nothing, so the
 * next call retries from scratch — which is what makes a transient
 * failure (a migration interrupted by a kill, a file briefly locked)
 * recoverable without a reinstall.
 *
 * Opening runs on [Dispatchers.Default] rather than the caller's. The
 * first call does real file work — create, or a migration over every row
 * the reader has saved — and the caller is a screen's state flow, which
 * on Android can be collecting on the main thread. No common IO
 * dispatcher is available to this module; the same choice, for the same
 * reason, as the dictionary's query path.
 *
 * There is no invalidate here and no self-heal, deliberately. The
 * dictionary drops its handle and re-copies the file when a read fails,
 * because the file is regenerable; doing the same to this one would
 * delete the reader's saved words to fix a transient error.
 */
internal class UserDatabaseHolder(
    private val path: () -> String = ::userDatabasePath,
    private val open: suspend (String) -> UserDatabase = ::openUserDb,
) {
    private val mutex = Mutex()
    private var opened: UserDatabase? = null

    suspend fun database(): UserDatabase = mutex.withLock {
        opened ?: withContext(Dispatchers.Default) { open(path()) }.also { opened = it }
    }
}

private val sharedUserDatabaseHolder = UserDatabaseHolder()

/**
 * The shared app-lifetime user database handle. Suspends while the first
 * caller opens (creating or migrating) the file; rethrows failures
 * without memoizing them, so a later call retries.
 */
suspend fun userDatabase(): UserDatabase = sharedUserDatabaseHolder.database()
