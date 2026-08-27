package cc.hosaka.okonomi.user

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import cc.hosaka.okonomi.db.awaitOneOrNull
import cc.hosaka.okonomi.user.db.UserDb
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteConfiguration
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver
import com.eygraber.sqldelight.androidx.driver.SqliteJournalMode
import com.eygraber.sqldelight.androidx.driver.SqliteSync
import kotlinx.coroutines.CancellationException

/**
 * File name of the user's own database, in the app's private storage
 * beside the dictionary copy (Alex's ruling: what is the user's and what
 * is shipped should be obvious from one file listing).
 *
 * Beside it is safe rather than merely tidy, and the reason has to be
 * kept in mind by anyone editing either side. The dictionary is deleted
 * and re-copied whenever any part of its sidecar moves — the data format
 * version went 3 to 6 in a single session — so anything stored in
 * `okonomi.db` is destroyed on the next dictionary update. What makes a
 * sibling file safe is that provisioning deletes the dictionary **by
 * name** (see `resetDictionaryProvisioningIn`), never by clearing the
 * directory. Do not change that reset to a directory sweep.
 *
 * The name is also deliberately outside the dictionary's own namespace:
 * provisioning writes `okonomi.db`, `okonomi.db.version` and
 * `okonomi.db.tmp`, and nothing here collides with those.
 */
const val USER_DB_NAME = "user.db"

/**
 * Fingerprint of the user database's DDL, as a guard on the one thing
 * nobody may forget here.
 *
 * The failure mode: change a `.sq` file, ship, and every install that
 * already has a `user.db` opens a file whose DDL no longer matches the
 * code reading it. Unlike the dictionary there is no re-copy to fall back
 * on — this file is the reader's own words and cannot be regenerated from
 * anything — so the only correct answer to a schema change is a
 * migration.
 *
 * `verifyCommonMainUserDbMigration` refuses to build a schema whose
 * `.sqm` chain does not reproduce it. This one exists because it runs
 * inside `:shared:testAndroidHostTest`, which is what anyone actually
 * runs, and because its failure message is the one moment someone editing
 * the schema is told what else has to happen.
 *
 * Changed by a human, never by a generator that rewrites it — a guard
 * that re-baselines itself guards nothing.
 */
internal const val USER_DB_SCHEMA_FINGERPRINT = "4971c1f27239badb"

/**
 * SHA-256 of every checked-in schema snapshot, by the version it records.
 *
 * The snapshots in `src/commonMain/sqldelight/user/databases/` are what
 * `verifyCommonMainUserDbMigration` replays the migrations against, which
 * makes them the proof rather than an output. A snapshot for a version
 * that has been generated is a record of DDL that reached devices, so it
 * is immutable: regenerating `1.db` to match an edited `.sq` makes the
 * verification pass over a schema no installed device has.
 *
 * `schemaOutputDirectory` already points into the build directory so the
 * generate task cannot overwrite one. These hashes catch the same thing
 * done by hand, and `UserDbSchemaTest` also requires the newest snapshot
 * to match `UserDb.Schema.version`, so adding a migration forces adding a
 * snapshot rather than editing one.
 */
internal val USER_DB_SCHEMA_SNAPSHOTS: Map<Int, String> = mapOf(
    1 to "b80843dac60b6e4a2f75f1502b7053e8b46eae628b78dfa30d4949fd39084082",
)

/**
 * Absolute path of [USER_DB_NAME], with its directory created. Resolved
 * lazily, on first use, so nothing here depends on platform
 * initialization order — and in particular not on the dictionary having
 * been provisioned first.
 */
internal expect fun userDatabasePath(): String

/**
 * An open handle on the user's database. Unlike the dictionary this one
 * is written to, and unlike the dictionary it can never be regenerated
 * from anything: nothing in this file may be dropped to recover from a
 * problem.
 */
class UserDatabase internal constructor(
    val db: UserDb,
    private val driver: SqlDriver,
) : AutoCloseable {
    override fun close() {
        driver.close()
    }
}

/**
 * A `user.db` written by a newer build of the app than the one opening
 * it. Reached through a cloud backup or a device transfer from a phone
 * running a later version: the file arrives carrying a schema this code
 * has no migrations for and does not understand.
 *
 * Refusing is the only safe answer. SQLDelight only ever migrates
 * forward, so it would open such a file without comment and let older
 * queries run against newer DDL — reading columns that moved, and
 * writing rows a later upgrade would have to make sense of.
 */
class UserDatabaseDowngradeException internal constructor(
    val storedVersion: Long,
    val supportedVersion: Long,
) : IllegalStateException(
    "user.db is at schema version $storedVersion but this build only understands " +
        "$supportedVersion. It was written by a newer version of the app.",
)

/**
 * Opens (creating or migrating) the user database at [path].
 *
 * Three choices here are deliberate and none of them is the driver's
 * default.
 *
 * **Journal mode Delete, not WAL.** WAL leaves `user.db-wal` and
 * `user.db-shm` beside the database, and Android's Auto Backup and
 * iCloud both capture whatever is in the directory, at no particular
 * instant. A restored database and `-wal` that disagree lose the most
 * recent saves — on the only file in this app that cannot be rebuilt.
 * WAL buys concurrent readers during a write, which a list that takes a
 * handful of tiny writes from taps has no use for. One self-contained
 * file is worth more here than throughput.
 *
 * **Synchronous Full, not Normal.** Under WAL's Normal a committed
 * transaction can still be lost to a power cut. For saved words that is
 * the wrong trade at any price; the cost is one fsync per tap.
 *
 * **Foreign keys on.** SQLite disables them per connection by default,
 * which would leave `list_entry`'s `ON DELETE CASCADE` inert — the
 * schema would claim a guarantee nothing enforced, and deleting a list
 * once named lists exist would orphan its rows. Enabled now, while no
 * shipped install has a row that could already violate it.
 *
 * The schema is handed to the driver rather than assumed present: a
 * fresh install has no file at all, and an install whose `user_version`
 * is behind must be **migrated** through the checked-in `.sqm` files.
 * That is the opposite of the dictionary, which ships at its schema
 * version and is replaced wholesale.
 *
 * Suspending, and not merely because the driver is: the create/migrate
 * that the driver defers to the first query is forced **here**, so a
 * migration that fails fails out of the opener where the caller can see
 * it, rather than inside whichever query happened to be first — where
 * this feature's read path would have absorbed it and reported an empty
 * list.
 */
suspend fun openUserDb(path: String): UserDatabase {
    val driver = AndroidxSqliteDriver(
        driver = BundledSQLiteDriver(),
        databaseType = AndroidxSqliteDatabaseType.File(path),
        schema = UserDb.Schema,
        configuration = AndroidxSqliteConfiguration(
            isForeignKeyConstraintsEnabled = true,
            journalMode = SqliteJournalMode.Delete,
            sync = SqliteSync.Full,
        ),
    )
    return try {
        prepareUserDb(driver)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // A half-open driver must not be left holding the file; the
        // caller is going to retry.
        runCatching { driver.close() }
        throw e
    }
}

/**
 * Brings [driver] to a state the app may query, or throws.
 *
 * Separated from [openUserDb] so a host test can drive it over a driver
 * whose native library exists off-device, and so the order of the two
 * steps is stated once: the schema is made ready first (which is what
 * runs create or migrate), and the resulting `user_version` is checked
 * second. Checking first would read 0 from a database that is about to
 * be created and call every fresh install a downgrade.
 */
internal suspend fun prepareUserDb(driver: SqlDriver): UserDatabase {
    val database = UserDb(driver)
    // Any query will do; this one touches an index and returns nothing.
    // Its purpose is the create/migrate the driver runs before answering
    // it, not the answer.
    database.listQueries.listBySlug(FAVOURITES_LIST_SLUG).awaitOneOrNull()
    val stored = driver.readUserVersion()
    if (stored > UserDb.Schema.version) {
        throw UserDatabaseDowngradeException(stored, UserDb.Schema.version)
    }
    return UserDatabase(database, driver)
}

/**
 * `PRAGMA user_version` off the open driver, across both cursor shapes —
 * the app's driver answers asynchronously, a test's JDBC driver
 * synchronously inside the mapper. Same split as `awaitOneOrNull`.
 */
internal suspend fun SqlDriver.readUserVersion(): Long = executeQuery(
    identifier = null,
    sql = "PRAGMA user_version",
    mapper = { cursor ->
        when (val first = cursor.next()) {
            is QueryResult.AsyncValue -> QueryResult.AsyncValue {
                if (first.await()) cursor.getLong(0) ?: 0L else 0L
            }

            is QueryResult.Value -> QueryResult.Value(
                if (first.value) cursor.getLong(0) ?: 0L else 0L,
            )
        }
    },
    parameters = 0,
).await()
