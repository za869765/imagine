package com.za869765.imagine.data.storage

import android.content.Context
import com.za869765.imagine.data.tutorial.TutorialData
import com.za869765.imagine.data.tutorial.TutorialLesson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * v1.8.0「從雲端更新素材」— 素材種子(material_seed.json)與課程資料(tutorial_lessons.json)
 * 不必重裝 APK:從 GitHub repo main 分支的 raw 檔拉最新版(super-i 新課程由桌面端工具
 * 收錄後 push 到 repo),存到 filesDir/seed_override/,MaterialSeed / TutorialData 優先讀它。
 * 拉失敗 / 解析失敗 → 不動既有檔,回 failure。
 */
object SeedUpdater {
    private const val RAW_BASE = "https://raw.githubusercontent.com/za869765/imagine/main/app/src/main/assets/"
    private const val DIR = "seed_override"
    const val SEED_FILE = "material_seed.json"
    const val LESSON_FILE = "tutorial_lessons.json"
    private const val PREFS = "imagine_seed_updater"
    private const val KEY_LAST = "last_updated_at"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    private val json = Json { ignoreUnknownKeys = true }

    data class Outcome(val seedTotal: Int, val seedAdded: Int, val lessonTotal: Int, val lessonAdded: Int)

    fun overrideFile(ctx: Context, name: String): File = File(File(ctx.filesDir, DIR), name)

    fun lastUpdatedAt(ctx: Context): String? =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    suspend fun update(ctx: Context): Result<Outcome> = withContext(Dispatchers.IO) {
        runCatching {
            val beforeSeed = MaterialSeed.load(ctx).map { it.url }.toHashSet()
            val beforeLesson = TutorialData.load(ctx).map { it.id }.toHashSet()

            val seedText = get(RAW_BASE + SEED_FILE)
            val seeds = json.decodeFromString<List<MaterialSeedEntry>>(seedText)
            require(seeds.isNotEmpty()) { "素材清單為空" }
            val lessonText = get(RAW_BASE + LESSON_FILE)
            val lessons = json.decodeFromString<List<TutorialLesson>>(lessonText)
            require(lessons.isNotEmpty()) { "課程清單為空" }

            val dir = File(ctx.filesDir, DIR).apply { mkdirs() }
            writeAtomic(File(dir, SEED_FILE), seedText)
            writeAtomic(File(dir, LESSON_FILE), lessonText)
            MaterialSeed.invalidate()
            TutorialData.invalidate()

            ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST, SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                .apply()

            Outcome(
                seedTotal = seeds.size,
                seedAdded = seeds.count { it.url !in beforeSeed },
                lessonTotal = lessons.size,
                lessonAdded = lessons.count { it.id !in beforeLesson },
            )
        }
    }

    private fun get(url: String): String {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.string() ?: error("空回應")
        }
    }

    private fun writeAtomic(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) error("寫入失敗 ${target.name}")
        }
    }
}
