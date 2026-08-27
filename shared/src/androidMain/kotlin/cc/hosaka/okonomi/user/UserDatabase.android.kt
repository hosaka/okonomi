package cc.hosaka.okonomi.user

import cc.hosaka.okonomi.db.AndroidAppContext
import java.io.File

/**
 * `filesDir`, the same directory the provisioned dictionary copy lives
 * in. Provisioning only ever deletes `okonomi.db` and its sidecar by
 * name, so this file survives every re-copy.
 */
internal actual fun userDatabasePath(): String =
    File(AndroidAppContext.applicationContext.filesDir, USER_DB_NAME).absolutePath
