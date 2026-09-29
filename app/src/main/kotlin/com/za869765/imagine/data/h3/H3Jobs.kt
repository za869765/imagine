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
    )

    private const val PREFS = "imagine_h3_jobs"
    private const val KEY = "jobs"
    private const val MAX = 30

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Job> =
        runCatching { H3Client.json.decodeFromString<List<Job>>(prefs(ctx).getString(KEY, null) ?: "[]") }
            .getOrDefault(emptyList())

    fun find(ctx: Context, runId: String): Job? = all(ctx).firstOrNull { it.runId == runId }

    fun add(ctx: Context, job: Job) = write(ctx, (listOf(job) + all(ctx).filterNot { it.runId == job.runId }).take(MAX))

    fun update(ctx: Context, runId: String, transform: (Job) -> Job) =
        write(ctx, all(ctx).map { if (it.runId == runId) transform(it) else it })

    private fun write(ctx: Context, jobs: List<Job>) {
        prefs(ctx).edit().putString(KEY, H3Client.json.encodeToString(jobs)).apply()
    }
}
