package com.za869765.imagine.data.h3

import android.content.Context
import android.net.Uri
import com.za869765.imagine.data.prefs.SecurePrefs

/**
 * 估算當下凍結的送出參數(確認視窗看到什麼就送什麼)。
 * clientRequestId:每次「新估算」產生一次;重送同一筆已確認的送出沿用同一個 → 伺服器去重,不會重複扣 CU。
 */
@kotlinx.serialization.Serializable
data class PendingH3(
    val prompt: String,
    val sources: List<String>,
    val seconds: Int,
    val quality: String,
    val mode: String,
    val aspect: String,
    val route: String = "cloud",
    val blink: Boolean = true,
    val gpus: List<String> = emptyList(),
    val segments: Int = 1,
    val beats: List<String> = emptyList(),
    val subjectId: String? = null,
    val subjectName: String? = null,
    val clientRequestId: String,
    val estimate: H3Estimate?,
    val seed: Long? = null,
)

/**
 * 送出 H3 任務(會花 CU)。呼叫端必須已取得使用者勾選確認,且要在 app 層 scope 執行
 * (畫面被關掉也不會漏記已建立的任務)。
 * 順序:壓縮圖片 → 送出前重新估算(CU / GPU 狀態變了就中止要求重新確認)→ POST(不自動重試)→ 記入最近任務。
 */
object H3Submit {
    sealed class Outcome {
        data class Created(val runId: String, val duplicate: Boolean) : Outcome()
        /** 重新估算後 CU 或 GPU 狀態變了 → 用新估算重新確認 */
        data class EstimateChanged(val fresh: H3Estimate) : Outcome()
        /** 重新估算後被擋(餘額未知/過舊/不足) */
        data class Blocked(val message: String, val fresh: H3Estimate) : Outcome()
        /** 伺服器 409(估算變動/餘額問題)→ 重新估算 */
        data class Conflict(val message: String) : Outcome()
        /** 伺服器明確拒絕(4xx),沒有建立任務 */
        data class Rejected(val message: String) : Outcome()
        /** 網路中斷 / 5xx / 回應無法解析 / 沒回 run_id — 可能已建立 */
        data class Uncertain(val message: String) : Outcome()
        /** 本機錯誤(圖片讀不到等),沒有送出 */
        data class LocalError(val message: String) : Outcome()
    }

    suspend fun run(
        ctx: Context,
        prefs: SecurePrefs,
        pending: PendingH3,
        onProgress: (String) -> Unit,
    ): Outcome {
        val confirmed = pending.estimate ?: return Outcome.LocalError("缺少估算，未送出")

        val images = ArrayList<H3JobImage>()
        for ((i, s) in pending.sources.withIndex()) {
            onProgress("壓縮圖片 ${i + 1}/${pending.sources.size}…")
            val img = H3Images.encode(ctx, Uri.parse(s), i)
                ?: return Outcome.LocalError("第 ${i + 1} 張圖讀取或壓縮失敗，未送出（沒有花費）")
            images.add(img)
        }

        onProgress("送出前重新估算…")
        val fresh = when (val r = H3Client.estimate(prefs, pending.seconds, pending.quality, pending.route, pending.segments)) {
            is H3Client.Result.Ok -> r.value
            is H3Client.Result.Err -> return Outcome.LocalError("重新估算失敗，未送出：${r.message}")
        }
        H3Format.blockReason(fresh)?.let { return Outcome.Blocked(it, fresh) }
        if (H3Format.estimateChanged(confirmed, fresh)) return Outcome.EstimateChanged(fresh)

        onProgress("送出中…")
        val req = H3JobRequest(
            prompt = pending.prompt,
            images = images,
            seconds = pending.seconds,
            quality = pending.quality,
            mode = pending.mode,
            aspect = pending.aspect,
            confirm = true,
            estCu = confirmed.cu,
            clientRequestId = pending.clientRequestId,
            route = pending.route,
            estNtd = confirmed.ntd,
            exp = if (pending.blink) mapOf("blink" to true) else null,
            gpus = if (pending.route == "cloud" && pending.gpus.isNotEmpty()) pending.gpus else null,
            segments = pending.segments,
            beats = if (pending.segments > 1 && pending.beats.any { it.isNotBlank() }) pending.beats else null,
            subjectId = pending.subjectId,
            // v2.5.3 影片一律不留在 Studio；只有使用者按「補上傳 Studio」才上傳
            keepInStudio = false,
            seed = pending.seed,
        )
        return when (val r = H3Client.submit(prefs, req)) {
            is H3Client.Result.Ok -> {
                val runId = r.value.runId
                if (runId.isNullOrBlank()) {
                    Outcome.Uncertain("伺服器沒有回傳任務編號")
                } else {
                    H3Jobs.add(
                        ctx,
                        H3Jobs.Job(
                            runId = runId,
                            prompt = pending.prompt,
                            quality = pending.quality,
                            seconds = pending.seconds,
                            estCu = confirmed.cu,
                            createdAtMs = System.currentTimeMillis(),
                            etaAt = confirmed.etaAt,
                            keepInStudio = false,
                            route = pending.route,
                            sources = pending.sources,
                            subjectId = pending.subjectId,
                            repeatVideo = pending.copy(estimate = null),
                        ),
                    )
                    Outcome.Created(runId, r.value.duplicate)
                }
            }
            is H3Client.Result.Err -> when {
                r.uncertain -> Outcome.Uncertain(r.message)
                r.code == 409 -> Outcome.Conflict(r.message)
                else -> Outcome.Rejected(r.message)
            }
        }
    }
}
