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

    private const val MAX_IMAGE_LONG_SIDE = 1024
    private const val JPEG_QUALITY = 85
    private const val MAX_VIDEO_BYTES = 10L * 1024 * 1024  // 10 MB
    private const val MAX_TOTAL_PIXELS = 50_000_000        // ~50M 像素 (約 7000x7000) 上限

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
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
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
        val bitmap = ctx.contentResolver.openInputStream(uri)?.use {
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
        val size = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size <= 0L || size > MAX_VIDEO_BYTES) return null
        val mime = ctx.contentResolver.getType(uri) ?: "video/mp4"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
