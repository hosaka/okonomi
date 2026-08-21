@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package cc.hosaka.okonomi.db

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileManagerItemReplacementUsingNewMetadataOnly
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.numberWithBool
import platform.Foundation.writeToFile

/**
 * iOS provisioning of the bundled dictionary.
 *
 * Xcode wiring (pending, to be done on a macOS host): add the
 * `tools/dictgen/build/generated/dictionary` output files `okonomi.db`
 * and `okonomi.db.version` to the iosApp target's Copy Bundle Resources
 * build phase (or a Run Script phase invoking
 * `./gradlew :tools:dictgen:generateDictionary` and copying the two
 * files into the app bundle). Until then the bundle lookup fails and the
 * caller degrades to the non-crashing load-failure path.
 *
 * The copied database lives in Application Support and is excluded from
 * iCloud/iTunes backup since it is regenerable from the bundled asset.
 */
internal actual suspend fun provisionDictionaryUnlocked(): String = withContext(Dispatchers.IO) {
    val fileManager = NSFileManager.defaultManager
    val bundledDb = bundledResourcePath(DICTIONARY_DB_NAME)
    val bundledSidecar = readText(bundledResourcePath(DICTIONARY_SIDECAR_NAME))
        ?: error("Bundled dictionary sidecar is unreadable")

    val targetDir = dictionaryDirectory() ?: error("Application Support directory unavailable")
    requireFileOperation("Failed to create $targetDir") { errorPtr ->
        fileManager.createDirectoryAtPath(
            targetDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = errorPtr,
        )
    }

    val dbPath = "$targetDir/$DICTIONARY_DB_NAME"
    val sidecarPath = "$targetDir/$DICTIONARY_SIDECAR_NAME"
    val upToDate = fileManager.fileExistsAtPath(dbPath) &&
        readText(sidecarPath)?.trim() == bundledSidecar.trim()
    if (!upToDate) {
        // Same interruption contract as Android: the persisted sidecar is
        // deleted first and rewritten last, and the database only appears
        // under its final name once the copy completed.
        fileManager.removeItemAtPath(sidecarPath, error = null)
        val tmpPath = "$dbPath.tmp"
        fileManager.removeItemAtPath(tmpPath, error = null)
        requireFileOperation("Failed to copy the bundled dictionary to $tmpPath") { errorPtr ->
            fileManager.copyItemAtPath(bundledDb, toPath = tmpPath, error = errorPtr)
        }
        if (fileManager.fileExistsAtPath(dbPath)) {
            // Atomic swap: the previous complete file stays readable until
            // the new one takes its place.
            requireFileOperation("Failed to replace $dbPath with the fresh copy") { errorPtr ->
                fileManager.replaceItemAtURL(
                    NSURL.fileURLWithPath(dbPath),
                    withItemAtURL = NSURL.fileURLWithPath(tmpPath),
                    backupItemName = null,
                    options = NSFileManagerItemReplacementUsingNewMetadataOnly,
                    resultingItemURL = null,
                    error = errorPtr,
                )
            }
        } else {
            // A rename to a non-existent name is a single atomic step.
            requireFileOperation("Failed to move the copied dictionary to $dbPath") { errorPtr ->
                fileManager.moveItemAtPath(tmpPath, toPath = dbPath, error = errorPtr)
            }
        }
        if (!writeText(sidecarPath, bundledSidecar)) {
            error("Failed to persist the dictionary sidecar to $sidecarPath")
        }
        excludeFromBackup(dbPath)
        excludeFromBackup(sidecarPath)
    }
    dbPath
}

actual fun resetDictionaryProvisioning() {
    val targetDir = dictionaryDirectory() ?: return
    val fileManager = NSFileManager.defaultManager
    // Sidecar first: if only one delete lands, a missing sidecar still
    // forces a fresh copy on the next provisioning run.
    fileManager.removeItemAtPath("$targetDir/$DICTIONARY_SIDECAR_NAME", error = null)
    fileManager.removeItemAtPath("$targetDir/$DICTIONARY_DB_NAME", error = null)
}

private fun dictionaryDirectory(): String? {
    val supportDir = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: return null
    return "$supportDir/dictionary"
}

private inline fun requireFileOperation(
    message: String,
    crossinline operation: (CPointer<ObjCObjectVar<NSError?>>) -> Boolean,
) {
    memScoped {
        val nsError = alloc<ObjCObjectVar<NSError?>>()
        if (!operation(nsError.ptr)) {
            error("$message: ${nsError.value?.localizedDescription ?: "unknown error"}")
        }
    }
}

private fun bundledResourcePath(name: String): String =
    NSBundle.mainBundle.pathForResource(name, ofType = null)
        ?: error("$name is not in the app bundle; wire the dictgen output into Copy Bundle Resources")

private fun readText(path: String): String? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    return NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
}

private fun writeText(path: String, content: String): Boolean {
    val data = NSString.create(string = content).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
    return data.writeToFile(path, atomically = true)
}

private fun excludeFromBackup(path: String) {
    memScoped {
        val nsError = alloc<ObjCObjectVar<NSError?>>()
        val excluded = NSURL.fileURLWithPath(path).setResourceValue(
            NSNumber.numberWithBool(true),
            forKey = NSURLIsExcludedFromBackupKey,
            error = nsError.ptr,
        )
        if (!excluded) {
            // Not worth failing provisioning over; the file is merely
            // backed up when it should not be.
            println(
                "okonomi: could not exclude $path from backup: " +
                    (nsError.value?.localizedDescription ?: "unknown error"),
            )
        }
    }
}
