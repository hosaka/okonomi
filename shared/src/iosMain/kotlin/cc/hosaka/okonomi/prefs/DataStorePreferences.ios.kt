@file:OptIn(ExperimentalForeignApi::class)

package cc.hosaka.okonomi.prefs

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Application Support, where the dictionary copy also lives — but its own
 * file beside the dictionary's directory rather than inside it.
 *
 * Being outside that directory is not what makes it safe: provisioning
 * deletes the dictionary and its sidecar by name, which is why
 * `cc.hosaka.okonomi.user.userDatabasePath` can and does put `user.db`
 * inside it. This file simply predates that and has no reason to move.
 *
 * Unlike the dictionary this is NOT excluded from backup: a setting the
 * reader chose is not regenerable from anything in the bundle.
 */
internal actual fun preferencesFilePath(): String {
    val supportDir = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: error("Application Support directory unavailable")
    NSFileManager.defaultManager.createDirectoryAtPath(
        supportDir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return "$supportDir/$PREFERENCES_FILE_NAME"
}
