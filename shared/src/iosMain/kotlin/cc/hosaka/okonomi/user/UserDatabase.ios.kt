@file:OptIn(ExperimentalForeignApi::class)

package cc.hosaka.okonomi.user

import cc.hosaka.okonomi.db.dictionaryDirectory
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager

/**
 * The dictionary's own directory under Application Support, so the two
 * files sit side by side. Safe because `resetDictionaryProvisioning`
 * removes `okonomi.db` and its sidecar by name rather than emptying the
 * directory.
 *
 * **That safety is unguarded on this platform, and it is the riskier of
 * the two layouts.** On Android `user.db` is a sibling in `filesDir`, a
 * directory provisioning does not own; here it is *inside* the directory
 * provisioning does own, so the only thing between it and a
 * `removeItemAtPath(targetDir)` is that nobody has written one.
 * `FavouritesSurviveDictionaryUpdateTest` pins exactly that property, by
 * mutation, against the **Android** actuals — the equivalent iOS test
 * cannot run on a Linux host and does not exist. Anyone editing
 * `DictionaryDatabase.ios.kt` is on their own honour: delete by name.
 *
 * The directory is created here as well as by dictionary provisioning:
 * the Favourites tab can be opened on a launch where nothing has asked
 * for the dictionary yet.
 *
 * Deliberately NOT excluded from iCloud backup. The dictionary is, since
 * it is regenerable from the app bundle; the reader's saved words are the
 * one thing in this app that is not.
 */
internal actual fun userDatabasePath(): String {
    val directory = dictionaryDirectory() ?: error("Application Support directory unavailable")
    NSFileManager.defaultManager.createDirectoryAtPath(
        directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return "$directory/$USER_DB_NAME"
}
