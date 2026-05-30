package com.za869765.imagine.data.storage

import android.content.Context

/**
 * 角色庫 — 把某些「圖片」檔(以 displayName 為鍵)標記為「角色參考圖」。
 * 純本機 SharedPreferences,不動生成 API;素材庫用它做「角色」分頁篩選 + ⭐ 標記,
 * 重用走既有的「當參考圖 / 編輯這張 / 動起來」流程(圖生圖 / 圖生影輸入)。
 */
object CharacterStore {
    private const val PREFS = "imagine_characters"
    private const val KEY = "names"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 回傳複本 — getStringSet 的回傳值不可直接改 (Android 契約)。
    fun all(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun isCharacter(ctx: Context, name: String): Boolean = all(ctx).contains(name)

    /** 切換並回傳切換後狀態 (true = 現在是角色)。 */
    fun toggle(ctx: Context, name: String): Boolean {
        val cur = all(ctx).toMutableSet()
        val nowChar = if (cur.contains(name)) {
            cur.remove(name); false
        } else {
            cur.add(name); true
        }
        prefs(ctx).edit().putStringSet(KEY, cur).apply()
        return nowChar
    }

    // 刪檔時一併清掉(與 PromptIndex.remove 同步),避免 prefs 累積已不存在檔案的孤兒鍵。
    fun remove(ctx: Context, name: String) {
        val cur = all(ctx).toMutableSet()
        if (cur.remove(name)) prefs(ctx).edit().putStringSet(KEY, cur).apply()
    }
}
