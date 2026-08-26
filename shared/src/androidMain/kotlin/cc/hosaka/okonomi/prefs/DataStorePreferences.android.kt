package cc.hosaka.okonomi.prefs

import cc.hosaka.okonomi.db.AndroidAppContext
import java.io.File

/**
 * `filesDir/datastore`, the location DataStore's own Android helpers use,
 * so a later move to `Context.preferencesDataStore` would find the file
 * already where it expects it.
 */
internal actual fun preferencesFilePath(): String {
    val directory = File(AndroidAppContext.applicationContext.filesDir, "datastore")
    directory.mkdirs()
    return File(directory, PREFERENCES_FILE_NAME).absolutePath
}
