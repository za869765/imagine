package com.za869765.imagine.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class MediaEntry(
    val uri: Uri,
    val displayName: String,
    val addedAtSec: Long,
    val isVideo: Boolean,
    val durationMs: Long? = null,
    val prompt: String? = null,
)

/**
 * v1.0.31 起：從 `ctx.filesDir/media/` 列舉(app-private sandbox)，不再 query MediaStore。
 * 舊版本寫進系統相簿的檔案不再列出 — 使用者得手動到 Photos/Gallery 清理。
 */
object MediaHistory {
    // ⚠️ STABLE CONTRACT — 跟 MediaSaver.DIR 必須一致；改了 History 會讀不到既有檔。
    private const val DIR = "media"

    // v1.0.54 O2: video duration cache，避免每進 History 都對所有影片重 decode metadata
    // (MediaMetadataRetriever.setDataSource 每檔 50-500ms，幾十支影片時 list 卡 1-2 秒)
    private const val DURATION_CACHE = "imagine_video_duration"

    suspend fun loadAll(ctx: Context): List<MediaEntry> = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, DIR)
        if (!dir.exists()) return@withContext emptyList()
        val durationCache = ctx.applicationContext
            .getSharedPreferences(DURATION_CACHE, Context.MODE_PRIVATE)
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("imagine_") }
            ?.mapNotNull { file ->
                val name = file.name
                val ext = name.substringAfterLast('.', "").lowercase()
                val isVideo = ext == "mp4"
                MediaEntry(
                    uri = file.toUri(),
                    displayName = name,
                    addedAtSec = file.lastModified() / 1000,
                    isVideo = isVideo,
                    durationMs = if (isVideo) getOrReadDuration(file, durationCache) else null,
                    prompt = PromptIndex.get(ctx, name),
                )
            }
            ?.sortedByDescending { it.addedAtSec }
            ?: emptyList()
    }

    suspend fun findByUri(ctx: Context, uri: Uri): MediaEntry? = withContext(Dispatchers.IO) {
        loadAll(ctx).firstOrNull { it.uri == uri }
    }

    // v1.0.54 O2: 先 cache 看，沒 hit 才 decode + 寫回 cache
    private fun getOrReadDuration(
        file: File,
        cache: android.content.SharedPreferences,
    ): Long? {
        val key = file.name
        val cached = cache.getLong(key, -1L)
        if (cached >= 0L) return cached.takeIf { it > 0 }
        val ms = readVideoDuration(file)
        // 寫 -1 表示「讀過但失敗」避免下次重讀；> 0 寫實際值
        cache.edit().putLong(key, ms ?: -1L).apply()
        return ms
    }

    private fun readVideoDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
