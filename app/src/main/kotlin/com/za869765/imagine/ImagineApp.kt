package com.za869765.imagine

import android.app.Application
import com.za869765.imagine.data.notify.Notifications
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.lock.AppLockManager

class ImagineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Eagerly register the lock manager with ProcessLifecycleOwner so it
        // starts observing background/foreground transitions immediately.
        val prefs = SecurePrefs.get(this)
        AppLockManager.get(prefs)
        Notifications.ensureChannels(this)
        // v1.0.45: MediaMigrator 改由 MainActivity 在權限 UI 之後觸發，這裡不再 fire-and-forget
    }
}
