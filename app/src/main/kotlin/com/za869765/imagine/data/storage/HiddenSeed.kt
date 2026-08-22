package com.za869765.imagine.data.storage

import android.content.Context

/**
 * 使用者從「內建課程素材」批次刪掉的 CDN 網址(圖/影皆可)。純本機 SharedPreferences。
 * 因為內建素材與課程圖庫是同一批 super-i CDN 資源(打包在 assets,不能真的刪),用一個
 * 「隱藏網址集合」做軟刪除:MaterialSeed(素材庫內建區)與 TutorialData 的課程圖庫都過濾掉它,
 * 達到「素材庫刪掉 → 課程圖庫也同步消失」。
 * v1.8.0:加 unhide(去留審查頁可把丟棄的撿回來)。
 */
object HiddenSeed {
    private const val PREFS = "imagine_hidden_seed"
    private const val KEY = "hidden_urls"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun hide(ctx: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val cur = all(ctx).toMutableSet()
        cur.addAll(urls)
        prefs(ctx).edit().putStringSet(KEY, cur).apply()
    }

    fun unhide(ctx: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val cur = all(ctx).toMutableSet()
        cur.removeAll(urls.toSet())
        prefs(ctx).edit().putStringSet(KEY, cur).apply()
    }
}

/**
 * v1.8.0 去留審查「保留」清單。三態:保留(在這裡)/ 丟棄(在 HiddenSeed)/ 未決定(兩邊都沒有)。
 * 保留本身不影響顯示,只是讓審查頁知道哪些已經看過、可以只看「未決定」。
 */
object SeedReview {
    private const val PREFS = "imagine_seed_review"
    private const val KEY = "kept_urls"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun kept(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun keep(ctx: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val cur = kept(ctx).toMutableSet()
        cur.addAll(urls)
        prefs(ctx).edit().putStringSet(KEY, cur).apply()
    }

    fun unkeep(ctx: Context, urls: Collection<String>) {
        if (urls.isEmpty()) return
        val cur = kept(ctx).toMutableSet()
        cur.removeAll(urls.toSet())
        prefs(ctx).edit().putStringSet(KEY, cur).apply()
    }
}
