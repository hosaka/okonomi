package cc.hosaka.okonomi.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Minimal application context holder, initialized from
 * OkonomiApplication.onCreate. No DI framework by design.
 */
object AndroidAppContext {
    private var context: Context? = null

    val applicationContext: Context
        get() = checkNotNull(context) {
            "AndroidAppContext is not initialized. OkonomiApplication.onCreate must run " +
                "before the dictionary database is touched; check the manifest's " +
                "android:name if this ever fires."
        }

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
}

internal actual suspend fun provisionDictionaryUnlocked(): String = withContext(Dispatchers.IO) {
    val context = AndroidAppContext.applicationContext
    provisionDictionaryInto(
        targetDir = context.filesDir,
        readBundledSidecar = {
            context.assets.open(DICTIONARY_SIDECAR_NAME).use { it.readBytes().decodeToString() }
        },
        openBundledDb = { context.assets.open(DICTIONARY_DB_NAME) },
    ).absolutePath
}

actual fun resetDictionaryProvisioning() {
    resetDictionaryProvisioningIn(AndroidAppContext.applicationContext.filesDir)
}

internal fun resetDictionaryProvisioningIn(targetDir: File) {
    // Sidecar first: if only one delete lands, a missing sidecar still
    // forces a fresh copy on the next provisioning run.
    File(targetDir, DICTIONARY_SIDECAR_NAME).delete()
    File(targetDir, DICTIONARY_DB_NAME).delete()
}

/**
 * Pure copy/staleness logic behind [provisionDictionaryUnlocked],
 * separated from the Android asset APIs so host tests can drive it with
 * fakes. Callers must hold the provisioning mutex.
 *
 * The database is only ever visible in two complete states: the copy
 * streams into a `.tmp` file that is atomically renamed over the target,
 * and the persisted sidecar is deleted first and rewritten last, so a
 * process death anywhere in between forces a fresh copy on next launch.
 */
internal fun provisionDictionaryInto(
    targetDir: File,
    readBundledSidecar: () -> String,
    openBundledDb: () -> InputStream,
): File {
    val db = File(targetDir, DICTIONARY_DB_NAME)
    val sidecar = File(targetDir, DICTIONARY_SIDECAR_NAME)
    val bundledSidecar = readBundledSidecar().trim()
    if (db.isFile && sidecar.isFile && sidecar.readText().trim() == bundledSidecar) {
        return db
    }
    sidecar.delete()
    val tmp = File(targetDir, "$DICTIONARY_DB_NAME.tmp")
    tmp.delete()
    try {
        openBundledDb().use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            Files.move(tmp.toPath(), db.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), db.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } catch (e: Exception) {
        tmp.delete()
        throw e
    }
    sidecar.writeText(bundledSidecar)
    return db
}
