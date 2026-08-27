package cc.hosaka.okonomi.user

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import cc.hosaka.okonomi.user.db.UserDb
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Makes a change to the user schema impossible to ship silently.
 *
 * The dictionary has the same guard for the opposite reason: its file is
 * replaced wholesale, so a forgotten version bump leaves a device on the
 * old DDL. This file is never replaced, so a forgotten migration leaves a
 * device on the old DDL *with the reader's words in it* and nothing that
 * can repair it. The correct response to this test failing is a migration
 * file, never a new fingerprint on its own.
 *
 * The assertion is not interesting in itself; the failure message is.
 */
class UserDbSchemaTest {

    @Test
    fun `the user schema matches the checked-in fingerprint`() = runTest {
        val statements = schemaStatements()
        val actual = fingerprintOf(statements)
        assertEquals(
            USER_DB_SCHEMA_FINGERPRINT,
            actual,
            """
            |
            |The user database schema changed (${statements.size} DDL statements,
            |current version ${UserDb.Schema.version}).
            |
            |This database holds the reader's saved words. It is never replaced
            |and there is no re-copy to fall back on, so the ONLY correct answer
            |to a schema change is a migration.
            |
            |1. Write shared/src/commonMain/sqldelight/user/<n>.sqm, where <n> is
            |   the version it migrates FROM. Adding it moves
            |   UserDb.Schema.version, which is the point: existing installs run
            |   the migration and keep their rows.
            |
            |2. Publish a snapshot for the NEW version. Run
            |       ./gradlew :shared:generateCommonMainUserDbSchema
            |   and copy the file it writes into build/generated/userDbSchema/
            |   into src/commonMain/sqldelight/user/databases/ under its new
            |   number, then add its SHA-256 to USER_DB_SCHEMA_SNAPSHOTS.
            |
            |   Do NOT modify an existing snapshot. Each one records DDL that
            |   reached devices, and it is what
            |   :shared:verifyCommonMainUserDbMigration replays the migrations
            |   against. Rewriting 1.db to match an edited .sq makes the
            |   verification pass over a schema no installed device has, which
            |   is the failure this guard exists to prevent.
            |
            |3. Set USER_DB_SCHEMA_FINGERPRINT to:
            |
            |       $actual
            |
            |If you did not mean to change the schema, this is telling you that
            |you did.
            |
            """.trimMargin(),
        )
    }

    /**
     * The guard has to be boring: a fingerprint that moved on its own
     * would be retuned into uselessness the first time it cried wolf.
     */
    @Test
    fun `the fingerprint is stable`() = runTest {
        assertEquals(fingerprintOf(schemaStatements()), fingerprintOf(schemaStatements()))
    }

    @Test
    fun `the fingerprint notices a changed statement`() = runTest {
        val statements = schemaStatements()
        val altered = statements.dropLast(1) + "CREATE INDEX imaginary_idx ON list_entry(created_at)"

        assertEquals(statements.size, altered.size)
        assertNotEquals(
            fingerprintOf(statements),
            fingerprintOf(altered),
            "a guard that cannot fail is not a guard",
        )
    }

    /**
     * The snapshots are the proof, not an output.
     *
     * `verifyCommonMainUserDbMigration` replays the migrations against
     * these files, so a snapshot rewritten to match an edited `.sq` makes
     * that verification pass over a schema no device has — done by hand,
     * that is a two-minute path from "the guard caught me" to "the guard
     * agrees with me". `schemaOutputDirectory` already points into the
     * build directory so the generate task cannot do it; this is what
     * catches it being done deliberately.
     */
    @Test
    fun `every checked-in schema snapshot is byte-for-byte the one that was published`() {
        val files = snapshotFiles()

        assertEquals(
            USER_DB_SCHEMA_SNAPSHOTS.keys.sorted(),
            files.keys.sorted(),
            "every snapshot in $SNAPSHOT_DIRECTORY must be declared in USER_DB_SCHEMA_SNAPSHOTS, " +
                "and every declared one must be present",
        )
        files.forEach { (version, file) ->
            assertEquals(
                USER_DB_SCHEMA_SNAPSHOTS.getValue(version),
                sha256Of(file),
                "$version.db has changed. A published snapshot records DDL that reached devices " +
                    "and is immutable: if the schema moved, write a migration and publish a NEW " +
                    "snapshot beside this one rather than rewriting it.",
            )
        }
    }

    /**
     * A migration moves the schema version, and the new version needs a
     * snapshot of its own or the next change has nothing to verify
     * against. Requiring the newest snapshot to be the current version is
     * what turns "add a migration" into "add a migration and publish its
     * snapshot".
     */
    @Test
    fun `the newest snapshot records the schema version in force`() {
        assertEquals(
            UserDb.Schema.version,
            USER_DB_SCHEMA_SNAPSHOTS.keys.max().toLong(),
            "UserDb.Schema.version moved without a snapshot for it; see " +
                "`the user schema matches the checked-in fingerprint` for the whole procedure",
        )
    }
}

/**
 * Where the published snapshots live, relative to the `:shared` project
 * directory — which is the working directory Gradle runs these tests in.
 */
private const val SNAPSHOT_DIRECTORY = "src/commonMain/sqldelight/user/databases"

private fun snapshotFiles(): Map<Int, File> {
    val directory = File(SNAPSHOT_DIRECTORY)
    assertTrue(
        directory.isDirectory,
        "no snapshot directory at ${directory.absolutePath}; this test reads it relative to the " +
            "project directory and cannot check anything without it",
    )
    return directory.listFiles().orEmpty()
        .filter { it.name.endsWith(".db") }
        .associateBy { file ->
            file.name.removeSuffix(".db").toIntOrNull()
                ?: throw AssertionError("$file is not named <version>.db")
        }
}

private fun sha256Of(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .joinToString("") { byte -> byte.toInt().and(0xFF).toString(16).padStart(2, '0') }

/**
 * The DDL [UserDb.Schema] would execute, captured rather than run — the
 * same trick `SchemaFingerprintTest` plays on the dictionary, and for the
 * same reason: a created database's `sqlite_master` also carries whatever
 * the SQLite build on the classpath writes for itself, which is not a
 * schema change and is exactly the false alarm that gets a guard deleted.
 */
private suspend fun schemaStatements(): List<String> {
    val driver = RecordingDriver()
    UserDb.Schema.create(driver).await()
    return driver.statements
}

/**
 * Whitespace collapsed and statements sorted, so the fingerprint tracks
 * what the schema *is* rather than how the `.sq` files are laid out, then
 * SHA-256'd. Sixteen hex characters: this detects an accident, it does
 * not resist an adversary.
 */
private fun fingerprintOf(statements: List<String>): String {
    val canonical = statements
        .map { it.replace(WHITESPACE, " ").trim() }
        .sorted()
        .joinToString("\n")
    val digest = MessageDigest.getInstance("SHA-256").digest(canonical.encodeToByteArray())
    return digest.joinToString("") { byte -> byte.toInt().and(0xFF).toString(16).padStart(2, '0') }
        .take(FINGERPRINT_LENGTH)
}

private val WHITESPACE = Regex("\\s+")

private const val FINGERPRINT_LENGTH = 16

/** Collects the schema's DDL instead of executing it. */
private class RecordingDriver : SqlDriver {
    val statements = mutableListOf<String>()

    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        statements += sql
        return QueryResult.Value(0L)
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = throw UnsupportedOperationException("schema capture only")

    override fun newTransaction(): QueryResult<Transacter.Transaction> =
        throw UnsupportedOperationException("schema capture only")

    override fun currentTransaction(): Transacter.Transaction? = null

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun close() = Unit
}
