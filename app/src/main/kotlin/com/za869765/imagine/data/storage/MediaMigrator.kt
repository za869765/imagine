package com.za869765.imagine.data.storage

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v1.0.44 起：一次性遷移舊版 (v1.0.30 以前) 寫進系統相簿
 * Pictures/Imagine + Movies/Imagine 內的 imagine_*.{png,jpg,mp4}，
 * 拷貝 (不是搬移) 到 v1.0.31+ 用的 `ctx.filesDir/media/`，
 * 讓使用者升版後 History 還看得到舊資料。
 *
 * 原檔保留在系統相簿不動 — 避免要跳系統權限 UI 才能刪別 app owner 之外的條目。
 * SharedPrefs flag 標記跑過後不再執行，避免每次啟動空轉 query。
 *
 * 舊檔的 prompt 在 v1.0.25 之前是寫到 MediaStore.DESCRIPTION，Samsung 機讀回常 null，
 * 所以遷過來的舊檔 History 多半 prompt 欄為空 — 無解，接受。
 */
object MediaMigrator {
    private const val PREFS = "imagine_migration"
    private const val KEY_DONE = "migrated_v1_31_done"

    suspend fun runIfNeeded(ctx: Context) = withContext(Dispatchers.IO) {
        val sp = ctx.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_DONE, false)) return@withContext

        val destDir = File(ctx.filesDir, "media").apply { if (!exists()) mkdirs() }
        runCatching {
            copyBucket(ctx, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, destDir)
            copyBucket(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, destDir)
        }
        // 不論成功幾筆都 mark done。失敗 (SecurityException / 26-28 沒 READ_EXTERNAL_STORAGE)
        // 重跑也不會多救到，反而每次啟動白費 query。
        sp.edit().putBoolean(KEY_DONE, true).apply()
    }

    private fun copyBucket(ctx: Context, baseUri: Uri, destDir: File) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        // Q+ 自動以 owner_package_name scope，撈不到別 app 的。
        // 舊版 (API 28-) 會撈到全部，靠下方 startsWith 再過濾。
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'imagine_%'"
        ctx.contentResolver.query(baseUri, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                if (!name.startsWith("imagine_")) continue
                val dst = File(destDir, name)
                if (dst.exists() && dst.length() > 0) continue
                val uri = Uri.withAppendedPath(baseUri, c.getLong(idCol).toString())
                runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        dst.outputStream().use { input.copyTo(it) }
                    }
                }.onFailure {
                    runCatching { if (dst.exists() && dst.length() == 0L) dst.delete() }
                }
            }
        }
    }
}
