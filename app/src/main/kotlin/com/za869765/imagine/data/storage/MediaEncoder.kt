package com.za869765.imagine.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * v1.0.49: 把本地 file URI 轉成 xAI Imagine API 吃的 data URI (base64)，
 * 並先 downscale + recompress 避免大檔 OOM 閃退。
 *
 * v1.0.50 強化：
 * - 明確 inPreferredConfig = ARGB_8888 避免回 HARDWARE bitmap (compress 會炸)
 * - 預檢 totalPixels 上限 50M (超過拒絕，避免 native decode 階段 OOM)
 * - 顯式 OutOfMemoryError catch (runCatching 對 native SIGABRT 接不到，
 *   但能擋 Java/Kotlin heap OOM)
 * - 失敗寫 Log.e + CrashLogger 之後使用者能 share 給開發者
 *
 * http(s) URI 直接回原字串 — xAI API 對自家生成的 https URL 不要再 base64 re-encode。
 */
object MediaEncoder {

    private const val TAG = "MediaEncoder"

    enum class Kind { Image, Video }

    // v1.0.91: 1536 → 1024 回退。v1.0.54 曾為「保留細節」把上限拉到 1536，但 xAI Imagine
    // 圖生影/圖片編輯會以 HTTP 400「像素太大無法使用」拒收 1536 的輸入圖。1024 是先前穩定值,
    // 且 xAI 自家「1k」生圖本就約 1024px → i2v 必收;480p/720p 影片與一般編輯用 1024 細節已足夠。
    // ⚠️ 不要再往上調,會重現「像素太大」。
    private const val MAX_IMAGE_LONG_SIDE = 1024
    private const val JPEG_QUALITY = 85
    private const val MAX_VIDEO_BYTES = 10L * 1024 * 1024  // 10 MB
    private const val MAX_TOTAL_PIXELS = 50_000_000        // ~50M 像素 (約 7000x7000) 上限

    // 素材庫圖 uri 是 file://(MediaHistory 用 file.toUri())，contentResolver.openInputStream
    // 對 app 自家 filesDir 的 file:// 不可靠 → 回 null。file:// / 無 scheme 先走 java.io.File，
    // 其餘(content:// 等)才走 contentResolver。
    private fun openInput(ctx: Context, uri: Uri): java.io.InputStream? {
        if (uri.scheme == "file" || uri.scheme == null) {
            uri.path?.let { p -> val f = java.io.File(p); if (f.exists()) return f.inputStream() }
        }
        return runCatching { ctx.contentResolver.openInputStream(uri) }.getOrNull()
    }

    suspend fun encodeForApi(ctx: Context, uri: Uri, kind: Kind): String? =
        withContext(Dispatchers.IO) {
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return@withContext uri.toString()
            try {
                when (kind) {
                    Kind.Image -> encodeImage(ctx, uri)
                    Kind.Video -> encodeVideo(ctx, uri)
                }
            } catch (oom: OutOfMemoryError) {
                Log.e(TAG, "encodeForApi OOM for $uri", oom)
                CrashLogger.record(ctx, "MediaEncoder.OOM", oom)
                System.gc()
                null
            } catch (t: Throwable) {
                Log.e(TAG, "encodeForApi failed for $uri", t)
                CrashLogger.record(ctx, "MediaEncoder.fail", t)
                null
            }
        }

    private fun encodeImage(ctx: Context, uri: Uri): String? {
        // 1. probe bounds (不真載入 pixel buffer)
        // ⚠️ v1.0.92 修：inJustDecodeBounds 模式 decodeStream 一律回 null(只填 bounds,不回
        // bitmap)。所以「開檔失敗」必須判 openInput 本身;絕不能把 use{} 的回傳值 ?: return null
        // —— 那會讓「合法圖」也被當失敗,使下方尺寸檢查永遠到不了。這正是 v1.0.50 起所有本地圖
        // (file://素材庫 / content://相簿)都「讀取起始圖失敗」的元兇。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val probe = openInput(ctx, uri) ?: return null
        probe.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 1.5 預檢總 pixel 數 — 太大直接拒絕，避免 native decode SIGABRT
        val totalPixels = bounds.outWidth.toLong() * bounds.outHeight.toLong()
        if (totalPixels > MAX_TOTAL_PIXELS) {
            Log.w(TAG, "image too large: ${bounds.outWidth}x${bounds.outHeight} = $totalPixels px")
            return null
        }

        // 2. 計算 sample size — 長邊壓到 MAX_IMAGE_LONG_SIDE 以內
        var sample = 1
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (longSide / sample > MAX_IMAGE_LONG_SIDE) sample *= 2

        // 3. 真 decode (sample 後記憶體用量降到 1/sample²)
        // 明確 ARGB_8888 避免 HARDWARE config (HARDWARE bitmap 不能 compress)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        val bitmap = openInput(ctx, uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        // 4. JPEG quality 85 recompress
        val baos = ByteArrayOutputStream()
        val compressed = try {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        } finally {
            bitmap.recycle()
        }
        if (!compressed) return null
        val bytes = baos.toByteArray()

        // 5. base64
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun encodeVideo(ctx: Context, uri: Uri): String? {
        // 影片不好 transcode，先以「限大小」處理；超過 toast 提示使用者
        // (呼叫端看到 null 會跳「讀取來源失敗」)
        // file:// (素材庫) 走 File.length() 較穩；其餘走 contentResolver。
        val localFile = if (uri.scheme == "file" || uri.scheme == null) {
            uri.path?.let { java.io.File(it) }?.takeIf { it.exists() }
        } else null
        val size = localFile?.length()
            ?: ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size <= 0L || size > MAX_VIDEO_BYTES) {
            Log.w(TAG, "video too large or unreadable: size=$size, max=$MAX_VIDEO_BYTES")
            return null
        }
        val mime = ctx.contentResolver.getType(uri) ?: "video/mp4"
        // v1.0.54: explicit OOM catch — 10MB video → base64 ~14MB string → 加 prefix/JSON
        // 序列化/upload buffer 仍可能在低記憶體手機 OOM。外層 encodeForApi 雖有 catch，
        // 顯式 catch + 明確 log 方便 diagnose
        return try {
            val bytes = (localFile?.inputStream() ?: openInput(ctx, uri))?.use { it.readBytes() }
                ?: return null
            "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "encodeVideo OOM (size=$size)", oom)
            System.gc()
            null
        }
    }
}
