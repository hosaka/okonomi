package cc.hosaka.okonomi

import android.app.Application
import cc.hosaka.okonomi.db.AndroidAppContext

class OkonomiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Application.onCreate runs before any activity, receiver or
        // service in this process, so every entry point sees an
        // initialized holder.
        AndroidAppContext.initialize(applicationContext)
    }
}
