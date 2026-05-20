package com.za869765.imagine.data.storage

import android.content.Context

/**
 * 把「filename → prompt」對應存 app-private SharedPrefs。
 *
 * 之前 MediaSaver 寫進 MediaStore.MediaColumns.DESCRIPTION，但該欄位
 * Android API 29+ 已 deprecated，Samsung One UI 14/15 等系統實際讀回
 * 是 null，造成 HistoryDetail 看不到 prompt。改成 app-private prefs
 * 持久化保險。
 *
 * key = displayName (像 "imagine_20260520_195829.jpg")
 * value = prompt 原文，無長度限制 (DESCRIPTION 之前限 200 字)
 */
object PromptIndex {
    private const val PREFS = "imagine_prompt_index"

    fun put(ctx: Context, displayName: String, prompt: String) {
        if (displayName.isBlank() || prompt.isBlank()) return
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(displayName, prompt).apply()
    }

    fun get(ctx: Context, displayName: String): String? {
        if (displayName.isBlank()) return null
        return ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(displayName, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun remove(ctx: Context, displayName: String) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(displayName).apply()
    }
}
