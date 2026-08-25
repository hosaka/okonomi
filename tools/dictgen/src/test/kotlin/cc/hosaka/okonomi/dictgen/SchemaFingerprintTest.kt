package cc.hosaka.okonomi.dictgen

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import cc.hosaka.okonomi.db.OkonomiDb
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Makes a schema change impossible to ship silently.
 *
 * The app copies a prebuilt database and re-copies it only when the
 * version sidecar changes. The sidecar's schema component cannot move —
 * SQLDelight derives it from migration files and this project has none
 * by policy — so [DICTIONARY_FORMAT_VERSION] is the only re-copy signal
 * there is, and nothing forced anyone to bump it. Change a `.sq` file,
 * forget, and every device keeps a database whose DDL no longer matches
 * the code reading it. A missing index is slow; a renamed column is a
 * crash.
 *
 * So the DDL is hashed and compared with a value checked in beside that
 * counter. The assertion is not interesting in itself; the failure
 * message is, because it is the one moment someone editing the schema
 * is told what else has to happen.
 */
class SchemaFingerprintTest {

    @Test
    fun schemaMatchesTheCheckedInFingerprint() {
        val statements = schemaStatements()
        val actual = fingerprintOf(statements)
        assertEquals(
            DICTIONARY_SCHEMA_FINGERPRINT,
            actual,
            """
            |
            |The database schema changed (${statements.size} DDL statements).
            |
            |Two things to do, in this order:
            |
            |1. Bump DICTIONARY_FORMAT_VERSION in Pipeline.kt, currently
            |   $DICTIONARY_FORMAT_VERSION. It is the ONLY thing that makes a device that
            |   already has a database copy the new one. Skip it and every
            |   existing install keeps the old DDL forever: a dropped index
            |   is merely slow, a renamed or missing column crashes the app.
            |   Do NOT add a migration file instead — nothing runs migrations
            |   here, the database is replaced wholesale.
            |
            |2. Set DICTIONARY_SCHEMA_FINGERPRINT to:
            |
            |       $actual
            |
            |If you did not mean to change the schema, this is telling you
            |that you did.
            |
            """.trimMargin(),
        )
    }

    /**
     * The guard has to be boring: a fingerprint that moved on its own
     * would be retuned into uselessness the first time it cried wolf.
     * Statement order is normalised away, so a reshuffle of the `.sq`
     * files is not a schema change, and the hash is a pure function of
     * the text.
     */
    @Test
    fun theFingerprintIsStable() {
        assertEquals(fingerprintOf(schemaStatements()), fingerprintOf(schemaStatements()))
    }

    @Test
    fun theFingerprintNoticesAChangedStatement() {
        val statements = schemaStatements()
        val altered = statements.dropLast(1) + "CREATE INDEX imaginary_idx ON entry(is_common)"

        assertEquals(statements.size, altered.size)
        kotlin.test.assertNotEquals(
            fingerprintOf(statements),
            fingerprintOf(altered),
            "a guard that cannot fail is not a guard",
        )
    }
}

/**
 * The DDL [OkonomiDb.Schema] would execute, captured rather than run.
 *
 * Taken from the schema itself and not from a created database's
 * `sqlite_master`: the latter also carries the shadow tables FTS5
 * builds for itself, whose exact text belongs to whichever SQLite build
 * happens to be on the test classpath. That would make the guard fail
 * on a SQLite upgrade, which is not a schema change and is exactly the
 * kind of false alarm that gets a guard deleted.
 */
private fun schemaStatements(): List<String> {
    val driver = RecordingDriver()
    OkonomiDb.Schema.create(driver)
    return driver.statements
}

/**
 * Whitespace collapsed and statements sorted, so the fingerprint tracks
 * what the schema *is* rather than how the `.sq` files are laid out or
 * what order SQLDelight emitted them in, then SHA-256'd. Sixteen hex
 * characters: this detects an accident, it does not resist an adversary.
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
