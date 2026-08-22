package com.za869765.imagine.data.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.za869765.imagine.data.api.OpenRouterClient
import com.za869765.imagine.data.api.XaiClient
import com.za869765.imagine.data.notify.Notifications
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.repo.OpenRouterRepository
import com.za869765.imagine.data.repo.userFriendlyTag
import com.za869765.imagine.data.storage.CrashLogger
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.data.video.VideoMerger
import kotlinx.coroutines.delay

/**
 * 影片生成 polling 背景任務。
 *
 * 進來時 requestId 已是 xAI 後台跑著的任務 — UI 端先 generateVideo/editVideo/extendVideo
 * 拿到 requestId 再 enqueue 本 Worker。Worker 只負責輪詢狀態 + 下載 + 存檔 + 系統通知,
 * 不再呼叫任何 POST。
 *
 * 為什麼要 Worker:Composable scope 在切分頁/鎖屏/process 死 都會 cancel,影片任務
 * 雖然 xAI 後台仍在跑,但 UI 拿不到完成事件 → 不會自動下載存檔。改 Worker 後切背景
 * 仍能跑完,完成發系統通知。
 */
class VideoPollWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = buildForegroundInfo(0)

    override suspend fun doWork(): Result = try {
        doWorkImpl()
    } catch (t: Throwable) {
        // v1.0.51: 任何未捕的 throw (包含 setForeground 在 Android 15+ 被拒、
        // Notifications 失敗、Bitmap OOM 等) 都寫 CrashLogger + 回 Result.failure，
        // 不讓 WorkManager retry → app crash loop → 開不起來
        CrashLogger.record(applicationContext, "VideoPollWorker", t)
        runCatching {
            Notifications.cancelProgress(applicationContext, inputData.getString(KEY_REQUEST_ID).orEmpty())
            Notifications.postComplete(
                applicationContext,
                inputData.getString(KEY_REQUEST_ID).orEmpty(),
                success = false,
                message = "影片任務內部錯誤: ${t.message?.take(80) ?: t::class.simpleName}",
            )
        }
        Result.failure(workDataOf(KEY_ERROR to "internal: ${t::class.simpleName}"))
    }

    private suspend fun doWorkImpl(): Result {
        val requestId = inputData.getString(KEY_REQUEST_ID).orEmpty()
        val prompt = inputData.getString(KEY_PROMPT).orEmpty()
        if (requestId.isBlank()) {
            return Result.failure(workDataOf(KEY_ERROR to "缺 requestId"))
        }

        // 升前景 — 避免 Doze / 系統殺。Channel 已在 Application.onCreate 建好
        // v1.0.51: setForeground 在 Android 14+ 某些情境會 throw — 用 runCatching 包，
        // 失敗就以普通 background worker 跑 (Doze 可能會殺但不會 crash app)
        runCatching { setForeground(buildForegroundInfo(0)) }

        val prefs = SecurePrefs.get(applicationContext)
        // v1.8.0 OpenRouter 任務:不同輪詢端點 + 成品要帶 key 下載
        if (inputData.getString(KEY_PROVIDER) == PROVIDER_OPENROUTER) {
            return pollOpenRouter(requestId, prompt, prefs)
        }
        val repository = ImagineRepository(XaiClient.build(prefs))

        val pendingStatuses = setOf(
            "pending", "queued", "processing", "running",
            "in_progress", "starting", "generating",
        )
        val successStatuses = setOf("done", "succeeded", "completed")

        var attempts = 0
        var pollErrors = 0
        var elapsedSec = 0

        while (attempts < MAX_ATTEMPTS) {
            // 等 5 秒 — 分 5 段 1 秒 sleep,以便 elapsed timer 更新進度通知 + 響應 cancel
            repeat(POLL_INTERVAL_SEC) {
                delay(1_000)
                elapsedSec++
                if (isStopped) return Result.failure(workDataOf(KEY_ERROR to "已取消"))
            }
            // 進度通知每輪刷一次
            runCatching { setForeground(buildForegroundInfo(elapsedSec)) }
            attempts++

            when (val poll = repository.pollVideoStatus(requestId)) {
                is ApiResult.Success -> {
                    pollErrors = 0
                    val status = poll.value.status.lowercase()
                    when {
                        status in successStatuses -> {
                            val url = poll.value.video?.url
                            return if (url != null) {
                                val saved = MediaSaver.saveVideoFromUrl(applicationContext, url, prompt)
                                // 組合延長:帶了 extendBase 就把原片＋新片自動串成長片(MediaMuxer,需同解析度/編碼)
                                val extendBase = inputData.getString(KEY_EXTEND_BASE)
                                var mergedUri: String? = null
                                var doneMsg = "影片完成,已存到 App 內,打開 Imagine 看歷史"
                                if (!extendBase.isNullOrBlank() && saved != null) {
                                    mergedUri = runCatching {
                                        VideoMerger.merge(
                                            applicationContext,
                                            listOf(android.net.Uri.parse(extendBase), android.net.Uri.parse(saved)),
                                            "長片組合 組合延長",
                                        )
                                    }.getOrNull()
                                    doneMsg = if (mergedUri != null) {
                                        "組合延長完成:原片＋新片已串成長片(看歷史)"
                                    } else {
                                        "新片已生成;自動串接失敗(片段需同解析度/編碼)— 可到長片組合手動接"
                                    }
                                }
                                Notifications.cancelProgress(applicationContext, requestId)
                                Notifications.postComplete(
                                    applicationContext, requestId,
                                    success = true,
                                    message = doneMsg,
                                )
                                Result.success(
                                    workDataOf(
                                        KEY_VIDEO_URL to url,
                                        KEY_SAVED_URI to (mergedUri ?: saved),
                                    ),
                                )
                            } else {
                                Notifications.cancelProgress(applicationContext, requestId)
                                Notifications.postComplete(
                                    applicationContext, requestId,
                                    success = false,
                                    message = "影片回報 ${poll.value.status} 但沒拿到 URL(費用以 xAI 後台為準)",
                                )
                                Result.failure(workDataOf(KEY_ERROR to "回報 ${poll.value.status} 但無 URL"))
                            }
                        }
                        status in pendingStatuses -> { /* 繼續 */ }
                        else -> {
                            val errMsg = poll.value.error?.let { "\n$it" }.orEmpty()
                            Notifications.cancelProgress(applicationContext, requestId)
                            Notifications.postComplete(
                                applicationContext, requestId,
                                success = false,
                                message = "影片任務 ${poll.value.status}(費用以 xAI 後台為準)$errMsg",
                            )
                            return Result.failure(workDataOf(KEY_ERROR to "影片任務 ${poll.value.status}"))
                        }
                    }
                }
                is ApiResult.Error -> {
                    pollErrors++
                    if (pollErrors >= 3) {
                        val tag = poll.kind.userFriendlyTag()
                        Notifications.cancelProgress(applicationContext, requestId)
                        Notifications.postComplete(
                            applicationContext, requestId,
                            success = false,
                            message = "輪詢失敗:$tag",
                        )
                        return Result.failure(workDataOf(KEY_ERROR to "$tag (輪詢)"))
                    }
                }
            }
        }

        // 超時(MAX_ATTEMPTS * POLL_INTERVAL_SEC ≈ 5 分鐘)
        Notifications.cancelProgress(applicationContext, requestId)
        Notifications.postComplete(
            applicationContext, requestId,
            success = false,
            message = "等待超時(${MAX_ATTEMPTS * POLL_INTERVAL_SEC / 60} 分鐘)— 任務可能仍在 xAI 後台執行,稍後去 console.x.ai 看",
        )
        return Result.failure(workDataOf(KEY_ERROR to "等待超時"))
    }

    // v1.8.0 OpenRouter:GET /videos/{id} 直到 completed → GET /videos/{id}/content(帶 Authorization)存檔。
    // 狀態:pending / in_progress / completed / failed。成品只存本機,KEY_VIDEO_URL 回 file:// 讓畫面直接播。
    private suspend fun pollOpenRouter(requestId: String, prompt: String, prefs: SecurePrefs): Result {
        val repo = OpenRouterRepository(OpenRouterClient.build(prefs))
        var attempts = 0
        var pollErrors = 0
        var elapsedSec = 0
        while (attempts < MAX_ATTEMPTS) {
            repeat(POLL_INTERVAL_SEC) {
                delay(1_000)
                elapsedSec++
                if (isStopped) return Result.failure(workDataOf(KEY_ERROR to "已取消"))
            }
            runCatching { setForeground(buildForegroundInfo(elapsedSec)) }
            attempts++

            when (val poll = repo.pollVideo(requestId)) {
                is ApiResult.Success -> {
                    pollErrors = 0
                    when (poll.value.status.lowercase()) {
                        "completed", "succeeded", "done" -> {
                            // 成品已付費且可重抓 → 下載最多試 3 次(短暫 timeout/502 不要直接判失敗)
                            var saved: String? = null
                            for (attempt in 1..3) {
                                when (val dl = repo.downloadVideo(requestId)) {
                                    is ApiResult.Success -> saved = dl.value.use { body ->
                                        body.byteStream().use { MediaSaver.saveVideo(applicationContext, it, prompt) }
                                    }
                                    is ApiResult.Error -> { /* 重試 */ }
                                }
                                if (saved != null) break
                                if (isStopped) return Result.failure(workDataOf(KEY_ERROR to "已取消"))
                                delay(attempt * 2_000L)
                            }
                            Notifications.cancelProgress(applicationContext, requestId)
                            return if (saved != null) {
                                val savedUri: String = saved
                                // 組合延長(v1.8.1):與 xAI 分支相同,帶 extendBase 就把原片＋新片串成長片
                                val extendBase = inputData.getString(KEY_EXTEND_BASE)
                                var mergedUri: String? = null
                                var doneMsg = "影片完成(OpenRouter),已存到 App 內,打開 Imagine 看歷史"
                                if (!extendBase.isNullOrBlank()) {
                                    mergedUri = runCatching {
                                        VideoMerger.merge(
                                            applicationContext,
                                            listOf(android.net.Uri.parse(extendBase), android.net.Uri.parse(savedUri)),
                                            "長片組合 組合延長",
                                        )
                                    }.getOrNull()
                                    doneMsg = if (mergedUri != null) {
                                        "組合延長完成:原片＋新片已串成長片(看歷史)"
                                    } else {
                                        "新片已生成;自動串接失敗(片段需同解析度/編碼)— 可到長片組合手動接"
                                    }
                                }
                                Notifications.postComplete(
                                    applicationContext, requestId,
                                    success = true,
                                    message = doneMsg,
                                )
                                Result.success(workDataOf(KEY_VIDEO_URL to (mergedUri ?: savedUri), KEY_SAVED_URI to (mergedUri ?: savedUri)))
                            } else {
                                Notifications.postComplete(
                                    applicationContext, requestId,
                                    success = false,
                                    message = "影片完成但下載失敗(已重試 3 次;費用以 OpenRouter 後台為準)",
                                )
                                Result.failure(workDataOf(KEY_ERROR to "完成但下載失敗"))
                            }
                        }
                        "failed", "error", "cancelled", "canceled", "expired" -> {
                            val err = poll.value.errorText()?.let { "\n$it" }.orEmpty()
                            Notifications.cancelProgress(applicationContext, requestId)
                            Notifications.postComplete(
                                applicationContext, requestId,
                                success = false,
                                message = "影片任務 ${poll.value.status}(費用以 OpenRouter 後台為準)$err",
                            )
                            return Result.failure(workDataOf(KEY_ERROR to "影片任務 ${poll.value.status}$err"))
                        }
                        else -> { /* pending / in_progress / queued 繼續 */ }
                    }
                }
                is ApiResult.Error -> {
                    pollErrors++
                    if (pollErrors >= 3) {
                        val tag = poll.kind.userFriendlyTag()
                        Notifications.cancelProgress(applicationContext, requestId)
                        Notifications.postComplete(
                            applicationContext, requestId,
                            success = false,
                            message = "輪詢失敗:$tag",
                        )
                        return Result.failure(workDataOf(KEY_ERROR to "$tag (輪詢)"))
                    }
                }
            }
        }
        Notifications.cancelProgress(applicationContext, requestId)
        Notifications.postComplete(
            applicationContext, requestId,
            success = false,
            message = "等待超時(${MAX_ATTEMPTS * POLL_INTERVAL_SEC / 60} 分鐘)— 任務可能仍在 OpenRouter 後台執行",
        )
        return Result.failure(workDataOf(KEY_ERROR to "等待超時"))
    }

    private fun buildForegroundInfo(elapsedSec: Int): ForegroundInfo {
        val notif = Notifications.buildProgress(applicationContext, elapsedSec)
        // notification id 0 不允許 — 用 channel 預設 id
        val id = Notifications.progressId(inputData.getString(KEY_REQUEST_ID).orEmpty().ifBlank { "imagine-video" })
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notif)
        }
    }

    companion object {
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_PROMPT = "prompt"
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_SAVED_URI = "saved_uri"
        const val KEY_ERROR = "error"
        // 組合延長:原片 file:// uri;完成後 worker 把原片+新片串接
        const val KEY_EXTEND_BASE = "extend_base"

        const val POLL_INTERVAL_SEC = 5
        // v1.0.54 O7: 60 → 120，總 timeout 從 5 分鐘拉到 10 分鐘
        // (xAI 影片有時排隊 + 跑長片 > 5 分鐘，原 5 分鐘上限會誤判失敗)
        const val MAX_ATTEMPTS = 120  // 120 × 5s = 10 分鐘

        // v1.0.51: 加 tag 讓 ImagineApp.onCreate 在 crash-loop 救援時 cancelAllWorkByTag
        const val TAG_VIDEO_POLL = "video-poll"

        const val UNIQUE_WORK_PREFIX = "video-poll-"
        fun uniqueName(requestId: String) = UNIQUE_WORK_PREFIX + requestId

        // v1.8.0 供應商(xai / openrouter);舊呼叫端不帶 = xai
        const val KEY_PROVIDER = "provider"
        const val PROVIDER_XAI = "xai"
        const val PROVIDER_OPENROUTER = "openrouter"

        fun inputDataOf(
            requestId: String,
            prompt: String,
            extendBase: String? = null,
            provider: String = PROVIDER_XAI,
        ): Data =
            if (extendBase.isNullOrBlank()) {
                workDataOf(KEY_REQUEST_ID to requestId, KEY_PROMPT to prompt, KEY_PROVIDER to provider)
            } else {
                workDataOf(KEY_REQUEST_ID to requestId, KEY_PROMPT to prompt, KEY_EXTEND_BASE to extendBase, KEY_PROVIDER to provider)
            }
    }
}
