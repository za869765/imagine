package com.za869765.imagine.data.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaSaver {

    private const val ALBUM = "Imagine"

    suspend fun saveImage(
        ctx: Context,
        bytes: ByteArray,
        prompt: String,
    ): String? = withContext(Dispatchers.IO) {
        val filename = "imagine_${timestamp()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            put(MediaStore.Images.Media.DESCRIPTION, prompt.take(200))
        }

        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null

        runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri.toString()
        }.getOrNull()
    }

    suspend fun saveImageFromUrl(ctx: Context, url: String, prompt: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode !in 200..299) return@runCatching null
                conn.inputStream.use { stream ->
                    val mime = conn.contentType?.takeIf { it.startsWith("image/") } ?: "image/png"
                    saveImageStream(ctx, stream, prompt, mime)
                }
            }.getOrNull()
        }

    suspend fun saveVideoFromUrl(ctx: Context, url: String, prompt: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 300_000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode !in 200..299) return@runCatching null
                conn.inputStream.use { stream -> saveVideo(ctx, stream, prompt) }
            }.getOrNull()
        }

    private suspend fun saveImageStream(
        ctx: Context,
        stream: InputStream,
        prompt: String,
        mime: String,
    ): String? = withContext(Dispatchers.IO) {
        val ext = if (mime.contains("jpeg")) "jpg" else "png"
        val filename = "imagine_${timestamp()}.$ext"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            put(MediaStore.Images.Media.DESCRIPTION, prompt.take(200))
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> stream.copyTo(out) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri.toString()
        }.getOrNull()
    }

    suspend fun saveVideo(
        ctx: Context,
        stream: InputStream,
        prompt: String,
    ): String? = withContext(Dispatchers.IO) {
        val filename = "imagine_${timestamp()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            put(MediaStore.Video.Media.DESCRIPTION, prompt.take(200))
        }

        val resolver = ctx.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext null

        runCatching {
            resolver.openOutputStream(uri)?.use { out -> stream.copyTo(out) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri.toString()
        }.getOrNull()
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
