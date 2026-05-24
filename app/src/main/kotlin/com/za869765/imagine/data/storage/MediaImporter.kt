package com.za869765.imagine.data.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.0.46: 共用「拷貝外部 URI 進 filesDir/media/」邏輯。
 * 給 ImportActivity (ACTION_SEND / SEND_MULTIPLE) + SettingsScreen 的 PhotoPicker 用。
 *
 * v1.0.48: 回傳值從 Int 改成 List of saved filename，呼叫端拿到後可以寫 PromptIndex
 * (ImportActivity 從 EXTRA_TEXT 收 prompt 帶入)。SettingsScreen 仍可用 .size 拿 count。
 */
object MediaImporter {

    suspend fun importAll(ctx: Context, uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext emptyList()
        val destDir = File(ctx.filesDir, "media").apply { if (!exists()) mkdirs() }
        val saved = mutableListOf<String>()
        var seq = 0
        for (uri in uris) {
            val mime = ctx.contentResolver.getType(uri).orEmpty()
            val ext = pickExt(mime) ?: continue
            val name = "imagine_${timestamp()}_${seq++}.$ext"
            val dst = File(destDir, name)
            val success = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    dst.outputStream().use { input.copyTo(it) }
                } ?: throw java.io.IOException("openInputStream returned null")
                true
            }.getOrElse {
                runCatching { if (dst.exists() && dst.length() == 0L) dst.delete() }
                false
            }
            if (success) saved += name
        }
        saved
    }

    private fun pickExt(mime: String): String? = when {
        mime.startsWith("image/jpeg") -> "jpg"
        mime.startsWith("image/png") -> "png"
        mime.startsWith("image/webp") -> "webp"
        mime.startsWith("image/") -> "png"
        mime.startsWith("video/mp4") -> "mp4"
        mime.startsWith("video/webm") -> "webm"
        mime.startsWith("video/") -> "mp4"
        else -> null
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
