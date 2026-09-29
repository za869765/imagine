package com.za869765.imagine.data.h3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.za869765.imagine.data.storage.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * H3 上傳圖:長邊縮到 1600px,JPEG 品質 90 起往下降到 < 1.5MB(伺服器上限約 1.9MB)。
 * 與 MediaEncoder 分開:xAI 那邊 1024px 是硬限制,H3 要更高細節,回傳也是純 base64(不含 data: 前綴)。
 */
object H3Images {
    private const val MAX_LONG_SIDE = 1600
    private const val TARGET_BYTES = 1_500_000
    private const val MAX_TOTAL_PIXELS = 50_000_000L

    private fun openInput(ctx: Context, uri: Uri): InputStream? {
        if (uri.scheme == "file" || uri.scheme == null) {
            uri.path?.let { p -> val f = File(p); if (f.exists()) return f.inputStream() }
        }
        return runCatching { ctx.contentResolver.openInputStream(uri) }.getOrNull()
    }

    /** 草稿裡的相簿 uri 重開 App 後可能失去讀取權限 → 進頁時檢查。 */
    suspend fun isReadable(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { openInput(ctx, uri)?.use { true } ?: false }.getOrDefault(false)
    }

    /** 失敗回 null(讀不到 / 太大 / OOM)。 */
    suspend fun encode(ctx: Context, uri: Uri, index: Int): H3JobImage? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (openInput(ctx, uri) ?: return@withContext null).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            if (bounds.outWidth.toLong() * bounds.outHeight > MAX_TOTAL_PIXELS) return@withContext null

            var sample = 1
            val longSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (longSide / (sample * 2) >= MAX_LONG_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = openInput(ctx, uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null
            var bmp = scaleToMax(applyExif(decoded, readOrientation(ctx, uri)), MAX_LONG_SIDE)
            val bytes: ByteArray = try {
                var out = compressUnder(bmp)
                while (out.size >= TARGET_BYTES && maxOf(bmp.width, bmp.height) >= 600) {
                    // 品質降到底仍太大 → 再縮 80% 重來
                    val smaller = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.8).toInt(), (bmp.height * 0.8).toInt(), true)
                    if (smaller !== bmp) bmp.recycle()
                    bmp = smaller
                    out = compressUnder(bmp)
                }
                out
            } finally {
                bmp.recycle()
            }
            if (bytes.size >= 1_900_000) return@withContext null
            H3JobImage(
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mime = "image/jpeg",
                name = "image${index + 1}.jpg",
            )
        } catch (oom: OutOfMemoryError) {
            CrashLogger.record(ctx, "H3Images.OOM", oom)
            System.gc()
            null
        } catch (t: Throwable) {
            CrashLogger.record(ctx, "H3Images.fail", t)
            null
        }
    }

    // 手機相機照常以 EXIF 標方向而非真的旋轉像素;BitmapFactory 不理 EXIF → 先轉正再縮放
    private fun readOrientation(ctx: Context, uri: Uri): Int =
        runCatching {
            openInput(ctx, uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun applyExif(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return src
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        if (out !== src) src.recycle()
        return out
    }

    private fun scaleToMax(src: Bitmap, max: Int): Bitmap {
        val long = maxOf(src.width, src.height)
        if (long <= max) return src
        val ratio = max.toDouble() / long
        val out = Bitmap.createScaledBitmap(src, (src.width * ratio).toInt().coerceAtLeast(1), (src.height * ratio).toInt().coerceAtLeast(1), true)
        if (out !== src) src.recycle()
        return out
    }

    private fun compressUnder(bmp: Bitmap): ByteArray {
        var q = 90
        while (true) {
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, baos)
            val b = baos.toByteArray()
            if (b.size < TARGET_BYTES || q <= 50) return b
            q -= 10
        }
    }
}
