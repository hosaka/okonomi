package cc.hosaka.okonomi.user

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.hosaka.okonomi.user.db.UserDb
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

/**
 * The app-lifetime handle on `user.db`.
 *
 * `DictionaryHolder` has had a test since it was written; this one had
 * none, so its single-flight and its "a failed attempt memoizes nothing"
 * were claims in a comment rather than behaviour. Both matter more here
 * than they do for the dictionary: two connections to a file that takes
 * writes is a different problem to two read-only ones, and a memoized
 * failure would leave the reader's saved words unreachable for the life
 * of the process with no way to retry.
 */
class UserDatabaseHolderTest {

    private val tempDirs = mutableListOf<File>()
    private val opened = mutableListOf<UserDatabase>()

    @AfterTest
    fun cleanUp() {
        opened.forEach { runCatching { it.close() } }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private suspend fun newDatabase(): UserDatabase {
        val file = Files.createTempDirectory("holder").toFile()
            .also { tempDirs += it }
            .resolve(USER_DB_NAME)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        UserDb.Schema.create(driver).await()
        return UserDatabase(UserDb(driver), driver).also { opened += it }
    }

    @Test
    fun `opens once and hands the same handle to every later caller`() = runBlocking {
        var opens = 0
        val holder = UserDatabaseHolder(
            path = { "unused" },
            open = {
                opens++
                newDatabase()
            },
        )

        val first = holder.database()
        val second = holder.database()

        assertEquals(1, opens)
        assertSame(first, second)
    }

    @Test
    fun `a failed open memoizes nothing, so the next call tries again`() = runBlocking {
        var attempts = 0
        val holder = UserDatabaseHolder(
            path = { "unused" },
            open = {
                attempts++
                if (attempts == 1) error("migration interrupted") else newDatabase()
            },
        )

        assertFailsWith<IllegalStateException> { holder.database() }
        // The retry has to reach the opener rather than a remembered
        // failure: a store the reader cannot get back to is the worst
        // outcome available on this file.
        val database = holder.database()

        assertEquals(2, attempts)
        assertSame(database, holder.database())
    }

    @Test
    fun `concurrent first callers open one database between them`() = runBlocking {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val opens = AtomicInteger()
        val holder = UserDatabaseHolder(
            path = { "unused" },
            open = {
                val now = active.incrementAndGet()
                maxActive.getAndUpdate { known -> max(known, now) }
                try {
                    opens.incrementAndGet()
                    // Long enough for the second caller to contend.
                    Thread.sleep(20)
                    newDatabase()
                } finally {
                    active.decrementAndGet()
                }
            },
        )

        val (first, second) = coroutineScope {
            val a = async(Dispatchers.IO) { holder.database() }
            val b = async(Dispatchers.IO) { holder.database() }
            a.await() to b.await()
        }

        assertEquals(1, opens.get(), "two connections to a file that takes writes is not one")
        assertEquals(1, maxActive.get(), "opens must never overlap")
        assertSame(first, second)
    }
}
