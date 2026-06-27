package com.za869765.imagine

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.za869765.imagine.data.notify.Notifications
import com.za869765.imagine.data.storage.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// SingletonImageLoader.Factory:給 Coil 全域用的 ImageLoader,設大容量磁碟快取 →
// 課程圖/範例圖載過一次就快取到本機,之後秒開、可離線。network fetcher 由 coil-network-okhttp
// 透過 serviceLoader(預設開)自動帶入,不會因自訂 loader 而失去網路載入。
class ImagineApp : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.10)
                    .build()
            }
            .build()

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
            Notifications.ensureChannels(this)
        } catch (t: Throwable) {
            CrashLogger.record(this, "ImagineApp.init", t)
            // 不 propagate — app 仍要起得來，主功能可能 degraded
        }
        // v1.0.45: MediaMigrator 改由 MainActivity 在權限 UI 之後觸發，這裡不再 fire-and-forget
    }
}
