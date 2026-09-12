package com.za869765.imagine.data.storage

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * StorageStats — 設定「儲存與資料」的空間資訊與快取清理(UI_REDESIGN_PLAN 6.3)。
 * 作品 = filesDir/media(永久);快取 = cacheDir(Coil image_cache、share 暫存、其他),清除不影響作品。
 */
object StorageStats {
    data class Stats(val mediaBytes: Long, val cacheBytes: Long, val freeBytes: Long)

    suspend fun compute(ctx: Context): Stats = withContext(Dispatchers.IO) {
        Stats(
            mediaBytes = dirSize(File(ctx.filesDir, "media")),
            cacheBytes = dirSize(ctx.cacheDir),
            freeBytes = freeBytes(ctx),
        )
    }

    /** 清除 cacheDir 全部內容(Coil 磁碟快取、分享暫存);回傳釋放的 bytes。作品目錄不動。 */
    suspend fun clearCache(ctx: Context): Long = withContext(Dispatchers.IO) {
        val before = dirSize(ctx.cacheDir)
        ctx.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        runCatching {
            val loader = coil3.SingletonImageLoader.get(ctx)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        before - dirSize(ctx.cacheDir)
    }

    fun freeBytes(ctx: Context): Long =
        runCatching { StatFs(ctx.filesDir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)

    /** 目前是否走 Wi-Fi(或乙太網);「僅 Wi-Fi 下載」判斷用。 */
    fun isOnWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun format(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
