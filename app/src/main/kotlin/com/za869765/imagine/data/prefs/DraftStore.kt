package com.za869765.imagine.data.prefs

import android.content.Context

/**
 * DraftStore — 生成頁草稿持久化(UI_REDESIGN_PLAN 0.2)。
 * rememberSaveable 只活過設定變更/系統回收;使用者主動關閉 App 再開就沒了。
 * 這裡把提示詞、影片製作方式、來源圖 uri 字串寫進 prefs,進頁時若 saveable 為空就還原。
 * 圖片頁與影片頁各自一份,互不影響。
 */
object DraftStore {
    private const val PREFS = "imagine_drafts"
    const val IMAGE_PROMPT = "image_prompt"
    const val VIDEO_PROMPT = "video_prompt"
    const val VIDEO_MODE = "video_mode"
    const val VIDEO_SOURCES = "video_sources" // '\n' 分隔

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context, key: String): String? = prefs(ctx).getString(key, null)?.takeIf { it.isNotEmpty() }

    fun save(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString(key, value).apply()
    }

    fun loadList(ctx: Context, key: String): List<String> =
        load(ctx, key)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun saveList(ctx: Context, key: String, value: List<String>) = save(ctx, key, value.joinToString("\n"))

    fun clear(ctx: Context, vararg keys: String) {
        prefs(ctx).edit().apply { keys.forEach { remove(it) } }.apply()
    }
}
