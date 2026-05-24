package com.za869765.imagine

import android.app.Application
import com.za869765.imagine.data.notify.Notifications
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.CrashLogger
import com.za869765.imagine.lock.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ImagineApp : Application() {
    companion object {
        // v1.0.54: app-scoped coroutine — 用在「不能被 Composable lifecycle 取消」的任務，
        // 例如 EditScreen.handleImageResult 內的下載+存檔 (B3 修法)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onCreate() {
        super.onCreate()
        // v1.0.50: 最早安裝 — 之後任何 uncaught exception 都會被寫到 filesDir/crash.log
        CrashLogger.install(this)

        // v1.0.54: 砍 v1.0.51 的 crash-loop recovery (cancelAllWorkByTag)。
        //   理由：v1.0.52 已修真正 root cause (SystemForegroundService manifest
        //   foregroundServiceType)，recovery 已成 net-harmful — start_pending flag 用 apply()
        //   async write，OS 殺 process 時 line「清 flag」根本沒落盤，下次啟動誤判
        //   「上次沒跑完」→ 自動 cancel 所有 video-poll work → 把正在跑的影片砍了。
        //   user 切走 app (memory pressure 一殺) → 回來歷史沒新影片 + 生成頁 state 空白。
        //   CrashLogger 仍在最早安裝，未來真有 crash 仍有 stack trace 可 diagnose。

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
        // v1.0.45: MediaMigrator 改由 MainActivity 在權限 UI 之後觸發，這裡不再 fire-and-forget
    }
}
