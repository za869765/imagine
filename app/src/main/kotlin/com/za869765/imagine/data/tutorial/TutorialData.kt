package com.za869765.imagine.data.tutorial

import android.content.Context
import com.za869765.imagine.data.storage.SeedUpdater
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 一課 = super-i「提示詞創作」一節。images 為 super-i CDN 圖片網址(由 Coil 即時載入,不打包進 APK);
// prompts 為該節原始提示詞片段(部分含說明文字,UI 標註僅供參考)。
@Serializable
data class TutorialLesson(
    val sec: Int,
    val id: Int,
    val title: String,
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val prompts: List<String> = emptyList(),
    // 與 videos 同序;由影片首格 AI 產生的「開頭主題」短標題,空字串=沒標題。
    val videoCaptions: List<String> = emptyList(),
)

// 讀取打包在 assets/tutorial_lessons.json 的課程資料(只有文字+URL,~180KB)。
// 失敗一律回空清單,絕不讓教學頁 crash。
// v1.8.0:若 filesDir/seed_override/tutorial_lessons.json 存在(「從雲端更新素材」拉的),優先讀它。
object TutorialData {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cache: List<TutorialLesson>? = null

    fun load(ctx: Context): List<TutorialLesson> {
        cache?.let { return it }
        val override = runCatching {
            val f = SeedUpdater.overrideFile(ctx, SeedUpdater.LESSON_FILE)
            if (f.exists() && f.length() > 0L) json.decodeFromString<List<TutorialLesson>>(f.readText(Charsets.UTF_8)) else null
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        val loaded = override ?: runCatching {
            val text = ctx.assets.open("tutorial_lessons.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            json.decodeFromString<List<TutorialLesson>>(text)
        }.getOrDefault(emptyList())
        cache = loaded
        return loaded
    }

    fun invalidate() { cache = null }
}
