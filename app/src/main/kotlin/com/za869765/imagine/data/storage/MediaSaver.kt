package com.za869765.imagine.data.storage

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.0.31 起：媒體一律寫到 app-private internal storage (`ctx.filesDir/media/`)，
 * 系統相簿/檔案總管/媒體掃描皆讀不到，解除安裝自動清除。
 *
 * 舊版本(v1.0.30 以前)寫進 MediaStore 的 Pictures/Imagine、Movies/Imagine 仍在
 * 系統相簿，但 App 內 History 不再顯示 — 使用者需自己到相簿清理舊資料。
 *
 * 回傳值是 file:// URI 字串，Coil / ExoPlayer / AsyncImage 直接吃。
 */
object MediaSaver {

    // ⚠️ STABLE CONTRACT — 改這個值 = 清空所有使用者既有歷史。
    // 同名 const 也存在於 MediaHistory.DIR 跟 MediaMigrator 的目標路徑，要改一起改 +
    // 寫遷移把舊目錄拷到新目錄，否則 in-place upgrade 後 History 會空掉。
    private const val DIR = "media"

    // v1.0.54 B2: AtomicInteger 防同秒多檔撞名 (兩張 imagine_YYYYMMDD_HHMMSS.png 後寫蓋前寫)
    private val seq = java.util.concurrent.atomic.AtomicInteger(0)

    private fun mediaDir(ctx: Context): File =
        File(ctx.filesDir, DIR).apply { if (!exists()) mkdirs() }

    // v1.0.54 B2: 加毫秒 + AtomicInteger counter，徹底防同秒撞名
    private fun timestamp(): String {
        val ms = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val n = seq.incrementAndGet() and 0xFFF  // 12-bit counter，足夠避免 burst 撞名
        return "${ms}_${n.toString(16)}"
    }

    suspend fun saveImage(
        ctx: Context,
        bytes: ByteArray,
        prompt: String,
    ): String? = withContext(Dispatchers.IO) {
        val filename = "imagine_${timestamp()}.png"
        writeFile(ctx, filename, prompt) { it.write(bytes) }
    }

    // v1.0.54 O1: 加 1 次 retry，網路 hiccup 不會直接丟掉影片
    suspend fun saveImageFromUrl(ctx: Context, url: String, prompt: String): String? =
        withContext(Dispatchers.IO) {
            downloadWithRetry(maxAttempts = 2) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode !in 200..299) return@downloadWithRetry null
                val mime = conn.contentType?.takeIf { it.startsWith("image/") } ?: "image/png"
                val ext = if (mime.contains("jpeg")) "jpg" else "png"
                val filename = "imagine_${timestamp()}.$ext"
                conn.inputStream.use { stream ->
                    writeFile(ctx, filename, prompt) { stream.copyTo(it) }
                }
            }
        }

    suspend fun saveVideoFromUrl(ctx: Context, url: String, prompt: String): String? =
        withContext(Dispatchers.IO) {
            downloadWithRetry(maxAttempts = 2) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 300_000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode !in 200..299) return@downloadWithRetry null
                conn.inputStream.use { stream -> saveVideo(ctx, stream, prompt) }
            }
        }

    // v1.0.54 O1: 共用 retry helper — 一次重試應付網路 hiccup 但不過度 retry 浪費
    private inline fun downloadWithRetry(maxAttempts: Int, block: () -> String?): String? {
        var lastResult: String? = null
        for (attempt in 1..maxAttempts) {
            lastResult = runCatching { block() }.getOrNull()
            if (lastResult != null) return lastResult
        }
        return lastResult
    }

    suspend fun saveVideo(
        ctx: Context,
        stream: InputStream,
        prompt: String,
    ): String? = withContext(Dispatchers.IO) {
        val filename = "imagine_${timestamp()}.mp4"
        writeFile(ctx, filename, prompt) { stream.copyTo(it) }
    }

    // v1.0.77 長片組合: MediaMuxer 需要實體輸出檔路徑 → 給 media 目錄下一個新 .mp4 檔
    // (命名沿用同一套防撞名規則),合成完成後再 registerSaved() 登記 prompt;失敗就刪殘檔。
    fun newVideoFile(ctx: Context): File = File(mediaDir(ctx), "imagine_${timestamp()}.mp4")

    fun registerSaved(ctx: Context, file: File, prompt: String): String? =
        if (file.exists() && file.length() > 0L) {
            PromptIndex.put(ctx, file.name, prompt)
            file.toUri().toString()
        } else {
            runCatching { if (file.exists()) file.delete() }
            null
        }

    private inline fun writeFile(
        ctx: Context,
        filename: String,
        prompt: String,
        block: (java.io.OutputStream) -> Unit,
    ): String? {
        val file = File(mediaDir(ctx), filename)
        return try {
            file.outputStream().use(block)
            PromptIndex.put(ctx, filename, prompt)
            file.toUri().toString()
        } catch (t: Throwable) {
            // 寫一半失敗的孤兒 file 清掉，避免 History 列出 0 byte 殘檔
            runCatching { if (file.exists()) file.delete() }
            null
        }
    }
}
