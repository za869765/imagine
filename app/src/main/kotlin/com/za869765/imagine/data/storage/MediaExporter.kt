package com.za869765.imagine.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 把生成的圖/影「帶出 app」:
 *  - saveToGallery(): 寫進系統相簿 (Pictures/Imagine、Movies/Imagine)。Q+ 用 MediaStore RELATIVE_PATH,免儲存權限。
 *  - share(): 透過 FileProvider + ACTION_SEND 叫出系統分享單,可存相簿 / 傳 LINE / 任意 app。
 *
 * src 可為 http(s) 遠端網址(生成結果卡)或 file:// 本機路徑(歷史/已存檔);兩者皆自動處理。
 * MediaSaver 仍只存 app 私有沙盒;本類是「使用者主動匯出」的唯一出口。
 */
object MediaExporter {

    private fun authority(ctx: Context) = "${ctx.packageName}.updater.fileprovider"

    private fun openStream(src: String): InputStream =
        if (src.startsWith("http")) {
            (URL(src).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 300_000
                instanceFollowRedirects = true
            }.inputStream
        } else {
            File(Uri.parse(src).path ?: src).inputStream()
        }

    /** 存進系統相簿。回傳是否成功。 */
    suspend fun saveToGallery(ctx: Context, src: String, isVideo: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = "imagine_${System.currentTimeMillis()}." + if (isVideo) "mp4" else "png"
                val resolver = ctx.contentResolver
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/png")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/Imagine" else "Pictures/Imagine")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                val item = resolver.insert(collection, values) ?: return@runCatching false
                resolver.openOutputStream(item)?.use { out -> openStream(src).use { it.copyTo(out) } }
                    ?: return@runCatching false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(item, values, null, null)
                }
                true
            }.getOrDefault(false)
        }

    /** 叫出系統分享單。遠端網址先下載到 cacheDir/share/ 再以 FileProvider 分享。回傳是否成功送出 intent。 */
    suspend fun share(ctx: Context, src: String, isVideo: Boolean): Boolean {
        val local: File? = withContext(Dispatchers.IO) {
            runCatching {
                if (src.startsWith("http")) {
                    val dir = File(ctx.cacheDir, "share").apply { if (!exists()) mkdirs() }
                    val f = File(dir, "imagine_${System.currentTimeMillis()}." + if (isVideo) "mp4" else "png")
                    openStream(src).use { input -> f.outputStream().use { input.copyTo(it) } }
                    f
                } else {
                    File(Uri.parse(src).path ?: src)
                }
            }.getOrNull()
        }
        if (local == null || !local.exists() || local.length() == 0L) return false
        return withContext(Dispatchers.Main) {
            runCatching {
                val uri = FileProvider.getUriForFile(ctx, authority(ctx), local)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = if (isVideo) "video/*" else "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(
                    Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
    }
}
