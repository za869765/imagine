package com.za869765.imagine.data.storage

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * FailedJobs — 失敗的生成任務也留記錄(UI_REDESIGN_PLAN 6.5):所有作品可篩「失敗」,
 * 顯示「失敗・原因」,提供「返回修改」帶回當次提示詞;可個別或批次清除。
 * 存 prefs JSON,最多留 100 筆(舊的淘汰)。
 */
object FailedJobs {
    @Serializable
    data class FailedJob(
        val id: String,
        val atSec: Long,
        val isVideo: Boolean,
        val prompt: String,
        val settings: String,   // 當次參數摘要(例:直式 9:16・720p・6 秒)
        val reason: String,
    )

    private const val PREFS = "imagine_failed_jobs"
    private const val KEY = "jobs"
    private const val MAX = 100
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<FailedJob> =
        runCatching { json.decodeFromString<List<FailedJob>>(prefs(ctx).getString(KEY, null) ?: "[]") }
            .getOrDefault(emptyList())

    fun add(ctx: Context, isVideo: Boolean, prompt: String, settings: String, reason: String) {
        val job = FailedJob(
            id = java.util.UUID.randomUUID().toString(),
            atSec = System.currentTimeMillis() / 1000,
            isVideo = isVideo,
            prompt = prompt,
            settings = settings,
            reason = reason,
        )
        write(ctx, (listOf(job) + all(ctx)).take(MAX))
    }

    fun remove(ctx: Context, id: String) = write(ctx, all(ctx).filterNot { it.id == id })

    fun clear(ctx: Context) = write(ctx, emptyList())

    private fun write(ctx: Context, jobs: List<FailedJob>) {
        prefs(ctx).edit().putString(KEY, json.encodeToString(jobs)).apply()
    }
}
