package com.za869765.imagine.data.storage

import android.content.Context

/**
 * 素材庫標記 — 把某張「圖片」(以 displayName 為鍵) 歸到一個分類:角色 / 環境 / 物件 / 風格。
 * 純本機 SharedPreferences,不動生成 API。素材庫頁 (MaterialLibraryScreen) 用它做分頁瀏覽 + 角標;
 * 取用走既有「編輯這張(圖生圖) / 動起來(圖生影)」流程 (KEY_INIT_MEDIA)。
 *
 * 取代舊的二元 CharacterStore — 首次讀取會把舊「角色」集合 (imagine_characters/names)
 * 一次性遷移成「角色」分類,使用者既有的 ⭐ 標記不會消失。
 *
 * 設計:一張圖只屬於一個分類 (setCategory 會先從其他分類移除)。每個分類各存一個 StringSet
 * (key = cat_<分類>),SharedPrefs 原生支援、不需序列化。
 */
object MaterialLibrary {
    const val CHARACTER = "角色"
    const val ENVIRONMENT = "環境"
    const val OBJECT = "物件"
    const val STYLE = "風格"
    val CATEGORIES = listOf(CHARACTER, ENVIRONMENT, OBJECT, STYLE)

    private const val PREFS = "imagine_material_library"
    private const val MIGRATED = "migrated_from_character_store"

    // 舊二元角色庫 (CharacterStore) — 遷移來源
    private const val OLD_PREFS = "imagine_characters"
    private const val OLD_KEY = "names"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(category: String) = "cat_$category"

    private fun migrateIfNeeded(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(MIGRATED, false)) return
        val old = ctx.applicationContext
            .getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
            .getStringSet(OLD_KEY, emptySet())?.toSet() ?: emptySet()
        val e = p.edit()
        if (old.isNotEmpty()) {
            val cur = p.getStringSet(key(CHARACTER), emptySet())?.toMutableSet() ?: mutableSetOf()
            cur.addAll(old)
            e.putStringSet(key(CHARACTER), cur)
        }
        e.putBoolean(MIGRATED, true).apply()
    }

    /** displayName → 分類 (未標記的圖不在 map 內)。 */
    fun all(ctx: Context): Map<String, String> {
        migrateIfNeeded(ctx)
        val p = prefs(ctx)
        val map = HashMap<String, String>()
        for (cat in CATEGORIES) {
            p.getStringSet(key(cat), emptySet())?.forEach { map[it] = cat }
        }
        return map
    }

    fun categoryOf(ctx: Context, name: String): String? = all(ctx)[name]

    fun namesIn(ctx: Context, category: String): Set<String> {
        migrateIfNeeded(ctx)
        return prefs(ctx).getStringSet(key(category), emptySet())?.toSet() ?: emptySet()
    }

    /** 設成某分類 (一張圖只屬一類,會先從其他分類移除)。 */
    fun setCategory(ctx: Context, name: String, category: String) {
        migrateIfNeeded(ctx)
        val p = prefs(ctx)
        val e = p.edit()
        for (cat in CATEGORIES) {
            // getStringSet 回傳值不可直接改 (Android 契約),一律 toMutableSet 取複本
            val set = p.getStringSet(key(cat), emptySet())?.toMutableSet() ?: mutableSetOf()
            val changed = if (cat == category) set.add(name) else set.remove(name)
            if (changed) e.putStringSet(key(cat), set)
        }
        e.apply()
    }

    /** 從素材庫整個移除 (各分類都清掉)。刪檔時一併呼叫,避免孤兒鍵。 */
    fun remove(ctx: Context, name: String) {
        migrateIfNeeded(ctx)
        val p = prefs(ctx)
        val e = p.edit()
        for (cat in CATEGORIES) {
            val set = p.getStringSet(key(cat), emptySet())?.toMutableSet() ?: mutableSetOf()
            if (set.remove(name)) e.putStringSet(key(cat), set)
        }
        e.apply()
    }

    fun isTagged(ctx: Context, name: String): Boolean = categoryOf(ctx, name) != null
}
