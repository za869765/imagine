package com.za869765.imagine.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * v1.0.49: 把本地 file URI 轉成 xAI Imagine API 吃的 data URI (base64)，
 * 並先 downscale + recompress 避免大檔 OOM 閃退。
 *
 * Bug 背景：之前 GenerateVideoScreen.encodeImage 跟 EditScreen.encodeMedia 都
 * 直接 readBytes + Base64.encodeToString。對手機相機 JPEG (4032x3024 ~5MB)
 * 一次 alloc base64 string (UTF-16 ~13.4MB) + concat prefix + JSON 序列化 + OkHttp
 * upload buffer = 短時間 50-100MB+ allocation → OOM crash。
 *
 * 修法：圖走 BitmapFactory inSampleSize 降到 max long-side 1024px、
 * JPEG quality 85 recompress、再 base64。影片限 10MB 否則回 null (呼叫端 toast)。
 *
 * http(s) URI 直接回原字串 — xAI API 對自家生成的 https URL 不要再 base64 re-encode。
 */
object MediaEncoder {

    enum class Kind { Image, Video }

    private const val MAX_IMAGE_LONG_SIDE = 1024
    private const val JPEG_QUALITY = 85
    private const val MAX_VIDEO_BYTES = 10L * 1024 * 1024  // 10 MB

    suspend fun encodeForApi(ctx: Context, uri: Uri, kind: Kind): String? =
        withContext(Dispatchers.IO) {
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") return@withContext uri.toString()
            when (kind) {
                Kind.Image -> encodeImage(ctx, uri)
                Kind.Video -> encodeVideo(ctx, uri)
            }
        }

    private fun encodeImage(ctx: Context, uri: Uri): String? = runCatching {
        // 1. probe bounds (不真載入 pixel buffer)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return@runCatching null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        // 2. 計算 sample size — 長邊壓到 MAX_IMAGE_LONG_SIDE 以內
        var sample = 1
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        while (longSide / sample > MAX_IMAGE_LONG_SIDE) sample *= 2

        // 3. 真 decode (sample 後記憶體用量降到 1/sample²)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return@runCatching null

        // 4. JPEG quality 85 recompress
        val baos = ByteArrayOutputStream()
        val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        bitmap.recycle()
        if (!compressed) return@runCatching null
        val bytes = baos.toByteArray()

        // 5. base64
        "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    private fun encodeVideo(ctx: Context, uri: Uri): String? = runCatching {
        // 影片不好 transcode，先以「限大小」處理；超過 toast 提示使用者
        // (呼叫端看到 null 會跳「讀取來源失敗」)
        val size = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size <= 0L || size > MAX_VIDEO_BYTES) return@runCatching null
        val mime = ctx.contentResolver.getType(uri) ?: "video/mp4"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()
}
