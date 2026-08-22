package com.za869765.imagine.data.storage

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MaterialSeedEntry(val url: String, val category: String)

/**
 * 內建素材種子 — 由課程範例圖的視覺分類產生(角色/環境/風格/物件),打包在
 * assets/material_seed.json(只有 CDN URL + 分類,不佔 APK 空間)。
 * 素材庫每個分類分頁的「內建課程素材」區用它;點圖可當圖生圖/圖生影的參考
 * (http(s) URL 由生成端 passthrough,不需先下載)。失敗一律回空,絕不讓素材庫 crash。
 * v1.8.0:若 filesDir/seed_override/material_seed.json 存在(「從雲端更新素材」拉下來的),優先讀它。
 */
object MaterialSeed {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<MaterialSeedEntry>? = null

    fun load(ctx: Context): List<MaterialSeedEntry> {
        cache?.let { return it }
        val override = runCatching {
            val f = SeedUpdater.overrideFile(ctx, SeedUpdater.SEED_FILE)
            if (f.exists() && f.length() > 0L) json.decodeFromString<List<MaterialSeedEntry>>(f.readText(Charsets.UTF_8)) else null
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        val loaded = override ?: runCatching {
            val text = ctx.assets.open("material_seed.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            json.decodeFromString<List<MaterialSeedEntry>>(text)
        }.getOrDefault(emptyList())
        cache = loaded
        return loaded
    }

    // 雲端更新後清快取,下次 load 重讀
    fun invalidate() { cache = null }

    fun urlsIn(ctx: Context, category: String): List<String> {
        val hidden = HiddenSeed.all(ctx) // 使用者批次刪掉的同步過濾
        return load(ctx).filter { it.category == category && it.url !in hidden }.map { it.url }
    }
}
