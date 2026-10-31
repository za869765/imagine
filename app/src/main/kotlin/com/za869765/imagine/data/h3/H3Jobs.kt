package com.za869765.imagine.data.h3

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * 最近送出的 H3 任務(存 prefs JSON,最多 30 筆)。離開任務頁後可從 H3 生成頁「最近 H3 任務」回來看進度;
 * savedUri 記錄成品已存進所有作品(避免重複下載)。
 */
object H3Jobs {
    @Serializable
    data class Job(
        val runId: String,
        val prompt: String,
        val quality: String,
        val seconds: Int,
        val estCu: Double,
        val createdAtMs: Long,
        val status: String = "queued",
        val savedUri: String? = null,
        // v2.0.7 生圖任務:kind="image"(影片任務沿用預設 "video"),meta=顯示用規格(「9:16・1K・2 張・製圖機」),
        // estNtd=預估台幣;生圖的 seconds 放張數、savedUri 放多張本機 uri(換行分隔)
        val kind: String = "video",
        val meta: String = "",
        val estNtd: Double = 0.0,
        // v2.1.9 預計完成(epoch ms;任務頁／清單輪詢時更新)
        val etaAt: Long? = null,
        // v2.3.5 keepInStudio=false：存到手機後請 Studio 刪掉；forgotten=已刪（之後不再向 Studio 查這件）
        val keepInStudio: Boolean = true,
        val forgotten: Boolean = false,
        // v2.3.6 只留在 APP 的成品之後補上傳到 Studio 了
        val reuploaded: Boolean = false,
        // v2.4.2 派給哪台機器（local／gputw_image／gputw／cloud；輪詢時跟伺服器更新）
        val route: String = "",
        // v2.5.6「繼續做相同」：repeat=這張的設定（只存手機）；groupId=同一組並排顯示
        val repeat: PendingH3Image? = null,
        val groupId: String = "",
        // v2.5.9 這件實際用的種子（完成後由伺服器回傳，只存手機）
        val seed: Long? = null,
    )

    private const val PREFS = "imagine_h3_jobs"
    private const val KEY = "jobs"
    private const val KEY_SEEDS = "saved_seeds"
    private const val MAX = 30

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Job> =
        runCatching { H3Client.json.decodeFromString<List<Job>>(prefs(ctx).getString(KEY, null) ?: "[]") }
            .getOrDefault(emptyList())

    fun find(ctx: Context, runId: String): Job? = all(ctx).firstOrNull { it.runId == runId }

    @Synchronized
    fun add(ctx: Context, job: Job) {
        // 同一 run_id(例如重送同一筆被伺服器去重)保留原本的建立時間與已存檔紀錄
        val old = all(ctx).firstOrNull { it.runId == job.runId }
        val merged = if (old != null) job.copy(createdAtMs = old.createdAtMs, savedUri = old.savedUri, status = old.status) else job
        write(ctx, (listOf(merged) + all(ctx).filterNot { it.runId == job.runId }).take(MAX))
    }

    @Synchronized
    fun update(ctx: Context, runId: String, transform: (Job) -> Job) {
        write(ctx, all(ctx).map { if (it.runId == runId) transform(it) else it })
    }

    /** v2.3.6 從清單移除（手機裡已存的成品檔不動）。 */
    @Synchronized
    fun remove(ctx: Context, runId: String) {
        write(ctx, all(ctx).filterNot { it.runId == runId })
    }

    // v2.5.9 記住的種子（不隨任務清單的 30 筆上限消失）：最新在前，最多 30 個
    fun savedSeeds(ctx: Context): List<Long> =
        prefs(ctx).getString(KEY_SEEDS, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    @Synchronized
    fun saveSeed(ctx: Context, seed: Long) {
        val list = (listOf(seed) + savedSeeds(ctx).filterNot { it == seed }).take(30)
        prefs(ctx).edit().putString(KEY_SEEDS, list.joinToString(",")).apply()
    }

    private fun write(ctx: Context, jobs: List<Job>) {
        prefs(ctx).edit().putString(KEY, H3Client.json.encodeToString(jobs)).apply()
    }
}
