package com.za869765.imagine.data.storage

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File

/**
 * 角色資產 — 「角色 = 名字 + 一組定妝圖」的實體,解決純文字鎖不住臉的角色一致性問題。
 * 生成/編輯/參考圖生影可一鍵帶入整組定妝圖當 reference。
 *
 * 圖一律以 filesDir/media/ 的 displayName 參照(與 MediaHistory / MaterialLibrary 同一套),
 * 來源不論是生成結果(MediaSaver)或相簿匯入(MediaImporter)都已是 app 私有副本 —
 * 外部原圖刪了也不影響。純本機 SharedPreferences,不動生成 API。
 *
 * 儲存格式:names(StringSet)=所有角色名;char_<名字>=displayName 以 '\n' 相接
 * (保留加入順序 — 參考圖順序影響 prompt 的 <IMAGE_N> 對應)。
 */
object CharacterAssets {
    private const val PREFS = "imagine_character_assets"
    private const val NAMES = "names"
    private const val SEP = "\n"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(name: String) = "char_$name"

    /** 所有角色名(依名稱排序)。 */
    fun names(ctx: Context): List<String> =
        prefs(ctx).getStringSet(NAMES, emptySet())?.sorted() ?: emptyList()

    /** 某角色的定妝圖 displayName(保留加入順序;檔案已被刪的自動略過)。 */
    fun imagesOf(ctx: Context, name: String): List<String> {
        val raw = prefs(ctx).getString(key(name), null) ?: return emptyList()
        val dir = File(ctx.filesDir, "media")
        return raw.split(SEP).filter { it.isNotBlank() && File(dir, it).exists() }
    }

    /** 加圖進角色(新名字自動建立;重複的 displayName 不重加)。 */
    fun addImages(ctx: Context, name: String, displayNames: List<String>) {
        if (name.isBlank() || displayNames.isEmpty()) return
        val p = prefs(ctx)
        val cur = p.getString(key(name), null)
            ?.split(SEP)?.filter { it.isNotBlank() }?.toMutableList() ?: mutableListOf()
        for (n in displayNames) if (n.isNotBlank() && n !in cur) cur.add(n)
        // getStringSet 回傳值不可直接改 (Android 契約),一律 toMutableSet 取複本
        val allNames = p.getStringSet(NAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        allNames.add(name)
        p.edit()
            .putString(key(name), cur.joinToString(SEP))
            .putStringSet(NAMES, allNames)
            .apply()
    }

    /** 從角色移除一張圖(不刪圖檔;移到只剩 0 張時角色保留,可再加)。 */
    fun removeImage(ctx: Context, name: String, displayName: String) {
        val p = prefs(ctx)
        val cur = p.getString(key(name), null)
            ?.split(SEP)?.filter { it.isNotBlank() && it != displayName } ?: return
        p.edit().putString(key(name), cur.joinToString(SEP)).apply()
    }

    /** 刪除整個角色(不刪圖檔,圖仍在歷史/素材庫)。 */
    fun delete(ctx: Context, name: String) {
        val p = prefs(ctx)
        val allNames = p.getStringSet(NAMES, emptySet())?.toMutableSet() ?: mutableSetOf()
        allNames.remove(name)
        p.edit().remove(key(name)).putStringSet(NAMES, allNames).apply()
    }

    /** displayName → filesDir/media 的 file:// Uri(MediaEncoder / Coil 都吃)。 */
    fun uriOf(ctx: Context, displayName: String): Uri =
        File(File(ctx.filesDir, "media"), displayName).toUri()
}
