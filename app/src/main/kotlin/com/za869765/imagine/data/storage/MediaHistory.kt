package com.za869765.imagine.data.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaEntry(
    val uri: Uri,
    val displayName: String,
    val addedAtSec: Long,
    val isVideo: Boolean,
    val durationMs: Long? = null,
    val prompt: String? = null,
)

object MediaHistory {
    // MediaSaver writes files named "imagine_<timestamp>.{png|jpg|mp4}".
    // The backslash escapes the underscore so SQLite doesn't treat it as a wildcard.
    private const val SELECTION = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'imagine\\_%' ESCAPE '\\'"

    suspend fun loadAll(ctx: Context): List<MediaEntry> = withContext(Dispatchers.IO) {
        (loadImages(ctx) + loadVideos(ctx)).sortedByDescending { it.addedAtSec }
    }

    suspend fun findByUri(ctx: Context, uri: Uri): MediaEntry? = withContext(Dispatchers.IO) {
        loadAll(ctx).firstOrNull { it.uri == uri }
    }

    private fun loadImages(ctx: Context): List<MediaEntry> {
        val list = mutableListOf<MediaEntry>()
        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DESCRIPTION,  // 舊版寫進去 (Samsung 通常回 null)
        )
        ctx.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            proj, SELECTION, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val descCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DESCRIPTION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol).orEmpty()
                // 先試 MediaStore.DESCRIPTION (寫入沒被擋的舊機型)，沒有就走 PromptIndex
                val prompt = c.getString(descCol)?.takeIf { it.isNotBlank() }
                    ?: PromptIndex.get(ctx, name)
                list += MediaEntry(
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    displayName = name,
                    addedAtSec = c.getLong(dateCol),
                    isVideo = false,
                    prompt = prompt,
                )
            }
        }
        return list
    }

    private fun loadVideos(ctx: Context): List<MediaEntry> {
        val list = mutableListOf<MediaEntry>()
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DESCRIPTION,  // 同上 — Samsung 多半回 null
        )
        ctx.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            proj, SELECTION, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val descCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DESCRIPTION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol).orEmpty()
                val prompt = c.getString(descCol)?.takeIf { it.isNotBlank() }
                    ?: PromptIndex.get(ctx, name)
                list += MediaEntry(
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                    displayName = name,
                    addedAtSec = c.getLong(dateCol),
                    isVideo = true,
                    durationMs = c.getLong(durCol),
                    prompt = prompt,
                )
            }
        }
        return list
    }
}
