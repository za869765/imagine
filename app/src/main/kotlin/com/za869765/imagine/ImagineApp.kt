package com.za869765.imagine

import android.app.Application
import com.za869765.imagine.data.notify.Notifications
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.MediaMigrator
import com.za869765.imagine.lock.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ImagineApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Eagerly register the lock manager with ProcessLifecycleOwner so it
        // starts observing background/foreground transitions immediately.
        val prefs = SecurePrefs.get(this)
        AppLockManager.get(prefs)
        Notifications.ensureChannels(this)
        // v1.0.44: 一次性遷移舊版寫進 MediaStore 的 imagine_* 檔到 filesDir/media/
        appScope.launch { MediaMigrator.runIfNeeded(this@ImagineApp) }
    }
}
