package com.za869765.imagine

import android.content.Context
import android.app.Application
import androidx.work.WorkManager
import com.za869765.imagine.data.notify.Notifications
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.CrashLogger
import com.za869765.imagine.data.work.VideoPollWorker
import com.za869765.imagine.lock.AppLockManager

class ImagineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // v1.0.50: 最早安裝 — 之後任何 uncaught exception 都會被寫到 filesDir/crash.log
        CrashLogger.install(this)

        // v1.0.51: crash-loop 救援 — 上次啟動沒跑完 onCreate (中途被 WorkManager retry
        // crash 殺) → 自動 cancel 所有 video-poll work，避免 retry → crash → retry 死循環
        // 讓 app 永遠開不起來。trade-off: 還在跑的影片會被取消，但 stability 優先。
        val startPrefs = getSharedPreferences("imagine_start", Context.MODE_PRIVATE)
        val didNotFinish = startPrefs.getBoolean("start_pending", false)
        startPrefs.edit().putBoolean("start_pending", true).apply()
        if (didNotFinish) {
            CrashLogger.record(
                this,
                "recovery",
                RuntimeException("previous onCreate did not finish; cancelling all video-poll work"),
            )
            try {
                WorkManager.getInstance(this).cancelAllWorkByTag(VideoPollWorker.TAG_VIDEO_POLL)
            } catch (t: Throwable) {
                CrashLogger.record(this, "recovery.cancelAllWorkByTag", t)
            }
        }

        try {
            // Eagerly register the lock manager with ProcessLifecycleOwner so it
            // starts observing background/foreground transitions immediately.
            val prefs = SecurePrefs.get(this)
            AppLockManager.get(prefs)
            Notifications.ensureChannels(this)
        } catch (t: Throwable) {
            CrashLogger.record(this, "ImagineApp.init", t)
            // 不 propagate — app 仍要起得來，主功能可能 degraded
        }

        // onCreate 跑完了 — 清 pending flag
        startPrefs.edit().putBoolean("start_pending", false).apply()
        // v1.0.45: MediaMigrator 改由 MainActivity 在權限 UI 之後觸發，這裡不再 fire-and-forget
    }
}
