package cc.hosaka.okonomi.user

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cc.hosaka.okonomi.db.DICTIONARY_DB_NAME
import cc.hosaka.okonomi.db.DICTIONARY_SIDECAR_NAME
import cc.hosaka.okonomi.db.awaitList
import cc.hosaka.okonomi.db.awaitOne
import cc.hosaka.okonomi.db.provisionDictionaryInto
import cc.hosaka.okonomi.db.resetDictionaryProvisioningIn
import cc.hosaka.okonomi.user.db.UserDb
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The reason `user.db` may live beside `okonomi.db` at all, asserted
 * rather than assumed.
 *
 * The dictionary is thrown away and re-copied whenever any component of
 * its sidecar moves — the data format version went 3 to 6 in a single
 * session — so anything the dictionary's own housekeeping can reach is
 * destroyed on the next update. What makes a sibling file safe is that
 * both the re-copy and the self-heal reset name the files they delete.
 * The moment either of them becomes a directory sweep, the reader's saved
 * words go with the dictionary, and these tests are what stands between
 * that change and shipping it.
 */
class FavouritesSurviveDictionaryUpdateTest {

    private val tempDirs = mutableListOf<File>()
    private val opened = mutableListOf<UserDatabase>()

    @AfterTest
    fun cleanUp() {
        opened.forEach { it.close() }
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun sharedDir(): File =
        Files.createTempDirectory("beside").toFile().also { tempDirs += it }

    /** A user database in [directory], holding one saved entry. */
    private suspend fun seedFavourite(directory: File, entryId: Long): File {
        val file = File(directory, USER_DB_NAME)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        UserDb.Schema.create(driver).await()
        val database = UserDatabase(UserDb(driver), driver).also { opened += it }
        database.db.listQueries.insertList(
            slug = FAVOURITES_LIST_SLUG,
            name = FAVOURITES_LIST_NAME,
            ord = 0,
            created_at = 1L,
        )
        val listId = database.db.listQueries.listBySlug(FAVOURITES_LIST_SLUG).awaitOne().id
        database.db.list_entryQueries.addToList(
            list_id = listId,
            entry_id = entryId,
            ord = 1,
            created_at = 1L,
        )
        // Closed before provisioning runs, so what is asserted afterwards
        // is what is on disk rather than what a live connection is
        // holding in memory.
        database.close()
        return file
    }

    private suspend fun savedEntryIds(file: File): List<Long> {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        val database = UserDatabase(UserDb(driver), driver).also { opened += it }
        val listId = database.db.listQueries.listBySlug(FAVOURITES_LIST_SLUG).awaitOne().id
        return database.db.list_entryQueries.entriesInList(listId).awaitList()
    }

    @Test
    fun `a dictionary format bump re-copies the dictionary and leaves the user database alone`() = runTest {
        val directory = sharedDir()
        val userDb = seedFavourite(directory, entryId = 1_358_280L)

        provisionDictionaryInto(
            targetDir = directory,
            readBundledSidecar = { "2026-08-26:1:6" },
            openBundledDb = { ByteArrayInputStream("dictionary v6".encodeToByteArray()) },
        )
        // The case the format version exists for, and the one that runs
        // every time the dictionary is regenerated.
        provisionDictionaryInto(
            targetDir = directory,
            readBundledSidecar = { "2026-08-26:1:7" },
            openBundledDb = { ByteArrayInputStream("dictionary v7".encodeToByteArray()) },
        )

        assertEquals(
            "dictionary v7",
            File(directory, DICTIONARY_DB_NAME).readText(),
            "the dictionary must actually have been replaced, or this proves nothing",
        )
        assertTrue(userDb.isFile, "the user database must survive a dictionary update")
        assertEquals(listOf(1_358_280L), savedEntryIds(userDb))
    }

    @Test
    fun `the corrupt-copy reset deletes the dictionary by name and leaves the user database alone`() = runTest {
        val directory = sharedDir()
        val userDb = seedFavourite(directory, entryId = 1_358_280L)
        provisionDictionaryInto(
            targetDir = directory,
            readBundledSidecar = { "2026-08-26:1:6" },
            openBundledDb = { ByteArrayInputStream("dictionary v6".encodeToByteArray()) },
        )

        resetDictionaryProvisioningIn(directory)

        assertFalse(File(directory, DICTIONARY_DB_NAME).exists(), "the reset must have done its own job")
        assertFalse(File(directory, DICTIONARY_SIDECAR_NAME).exists())
        assertTrue(userDb.isFile, "the reset must never reach the user database")
        assertEquals(listOf(1_358_280L), savedEntryIds(userDb))
    }
}
