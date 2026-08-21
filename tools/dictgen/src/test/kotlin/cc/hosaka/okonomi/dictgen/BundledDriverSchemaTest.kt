package cc.hosaka.okonomi.dictgen

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import cc.hosaka.okonomi.db.OkonomiDb
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the app schema is accepted by the SQLite the app actually ships:
 * androidx.sqlite's bundled build (3.50.x, FTS5 compiled in), not the
 * platform SQLite or the xerial JDBC build used elsewhere in dictgen.
 */
class BundledDriverSchemaTest {

    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    private fun tempDir(): File = Files.createTempDirectory("bundled").toFile().also { tempDirs += it }

    @Test
    fun schemaCreatesCleanlyOnBundledSqlite() {
        val file = tempDir().resolve("schema.db")
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            OkonomiDb.Schema.create(SingleConnectionDriver(connection))

            connection.execSQL("INSERT INTO entry(id) VALUES (1)")
            connection.execSQL("INSERT INTO sense(id, entry_id, ord) VALUES (1, 1, 0)")
            connection.execSQL("INSERT INTO gloss(sense_id, ord, text) VALUES (1, 0, 'to eat')")
            connection.execSQL("INSERT INTO gloss_fts(gloss_fts) VALUES('rebuild')")

            connection.prepare("SELECT count(*) FROM gloss_fts WHERE gloss_fts MATCH 'eat'").use { statement ->
                assertTrue(statement.step())
                assertEquals(1L, statement.getLong(0))
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun generatedDatabaseOpensOnBundledSqlite() {
        val dataDir = tempDir()
        Fixtures.writeDataDir(dataDir)
        val out = dataDir.resolve("okonomi.db")
        Pipeline(dataDir, out).run()

        val connection = BundledSQLiteDriver().open(out.absolutePath)
        try {
            connection.prepare("PRAGMA user_version").use { statement ->
                assertTrue(statement.step())
                assertEquals(OkonomiDb.Schema.version, statement.getLong(0))
            }
            connection.prepare(
                "SELECT count(*) FROM gloss_fts WHERE gloss_fts MATCH 'eat'",
            ).use { statement ->
                assertTrue(statement.step())
                assertTrue(statement.getLong(0) > 0)
            }
        } finally {
            connection.close()
        }
    }
}

/**
 * Minimal [SqlDriver] over one androidx [SQLiteConnection]: just enough
 * for the generated Schema.create, which only issues DDL through execute.
 */
private class SingleConnectionDriver(
    private val connection: SQLiteConnection,
) : SqlDriver {
    override fun execute(
        identifier: Int?,
        sql: String,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> {
        connection.execSQL(sql)
        return QueryResult.Value(0L)
    }

    override fun <R> executeQuery(
        identifier: Int?,
        sql: String,
        mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int,
        binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> = throw UnsupportedOperationException("schema creation only")

    override fun newTransaction(): QueryResult<Transacter.Transaction> =
        throw UnsupportedOperationException("schema creation only")

    override fun currentTransaction(): Transacter.Transaction? = null

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun close() = Unit
}
