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
    // v2.8.0 往後續做：原片本機 uri，新片做好後接在它後面
    val extendBase: String? = null,
    // v2.9.0 筆電主模型:"v2"(Singularity V2)或 "q4"(官方 Q4);舊紀錄沒這欄＝當時只有 Q4
    val model: String = "q4",
    // v2.9.4 描述自動翻英文(舊紀錄沒這欄＝當時沒翻)
    val translate: Boolean = false,
    // v3.1.0 換人(mode=motion):參考影片本機 uri 與秒數
    val refVideo: String? = null,
    val refVideoSeconds: Double? = null,
    // v3.2.6 一條龍:人物長相(文生影＋筆電才有);筆電先生一張參考人像再生影片
    val autoRef: String? = null,
    // v3.2.7 白皙(只在筆電):PRISM 8 步＋胸部 LoRA＋672×1120,成品調白;舊紀錄沒這欄＝沒開
    val fair: Boolean = false,
    // v3.2.7 範本:提示詞原樣送出(不包裝、不翻譯、不加眨眼包);舊紀錄沒這欄＝照原本的包裝
    val rawPrompt: Boolean = false,
    // v3.3.1 白皙＋畫面裡有裸體男性:多掛男性人體部位 LoRA(舊紀錄沒這欄＝沒掛)
    val fairMale: Boolean = false,
)

/** 筆電工人認得的 V2 檔名(local_agent.LOCAL_V2) */
const val H3_LOCAL_V2_UNET = "Minimax-h3_Singularity_ref2va_v2_pruned_w4a8.safetensors"
/** v2.9.5 雲端(GPUtw／Colab)的 V2 檔名(h3_worker.FILE_REPO 認得,11/3 兩邊都實測過) */
const val H3_CLOUD_V2_UNET = "Minimax-h3_Singularity_ref2va_v2_pruned_int8_convrot_r2.safetensors"
/** v3.2.9 筆電也可單選 PRISM(local_agent.LOCAL_PRISM;白皙用的是同一個檔) */
const val H3_LOCAL_PRISM_UNET = "PRISM_DMAD_PDMD_w6a8_g32.safetensors"
/** v2.9.6 GPUtw 可選 PRISM(Singularity×10Eros 合併,8 步烤入;已在 /vault) */
const val H3_PRISM_UNET = "Minimax-h3_Singularity_x_10Eros-Max_PRISM_int8.safetensors"

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
        val fresh = when (val r = H3Client.estimate(prefs, pending.seconds, pending.quality, pending.route, pending.segments, pending.fair)) {
            is H3Client.Result.Ok -> r.value
            is H3Client.Result.Err -> return Outcome.LocalError("重新估算失敗，未送出：${r.message}")
        }
        H3Format.blockReason(fresh)?.let { return Outcome.Blocked(it, fresh) }
        if (H3Format.estimateChanged(confirmed, fresh)) return Outcome.EstimateChanged(fresh)

        var refVideo: H3RefVideo? = null
        if (pending.mode == "motion") {
            val uri = pending.refVideo ?: return Outcome.LocalError("換人模式要先選一支參考影片，未送出")
            onProgress("上傳參考影片…")
            val bytes = runCatching { ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() } }.getOrNull()
                ?: return Outcome.LocalError("參考影片讀不到，未送出（沒有花費）")
            if (bytes.size > 80 * 1024 * 1024) return Outcome.LocalError("參考影片超過 80MB，請先剪短，未送出")
            refVideo = when (val r = H3Client.uploadRefVideo(prefs, bytes, pending.refVideoSeconds)) {
                is H3Client.Result.Ok -> r.value
                is H3Client.Result.Err -> return Outcome.LocalError("參考影片上傳失敗，未送出：${r.message}")
            }
        }

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
            exp = buildMap<String, kotlinx.serialization.json.JsonElement> {
                val raw = pending.rawPrompt && (pending.segments == 1 || pending.beats.size >= pending.segments && pending.beats.take(pending.segments).all { it.isNotBlank() })  // 範本原樣送出(單段,或多段且每段都有內容):眨眼包與翻譯都會改提示詞,不加
                if (pending.blink && !raw) put("blink", kotlinx.serialization.json.JsonPrimitive(true))
                if (pending.translate && !raw) put("translate", kotlinx.serialization.json.JsonPrimitive(true))
                // v3.3.0 白皙三種機器都能做(筆電壓縮版 PRISM、GPUtw／Colab 完整 int8 版);換人、首幀模式機器端自動照一般設定
                if (pending.fair) put("fair", kotlinx.serialization.json.JsonPrimitive(true))
                if (pending.fair && pending.fairMale) put("fair_male", kotlinx.serialization.json.JsonPrimitive(true))
                if (pending.model == "prism" && pending.route == "gputw") {
                    put("unet", kotlinx.serialization.json.JsonPrimitive(H3_PRISM_UNET))
                    put("steps", kotlinx.serialization.json.JsonPrimitive(8))  // 8 步加速已燒進權重
                } else if (pending.model == "prism" && pending.route == "local") {
                    put("unet", kotlinx.serialization.json.JsonPrimitive(H3_LOCAL_PRISM_UNET))  // v3.2.9 筆電單選 PRISM:8 步、不掛 LoRA、不調白
                } else if (pending.model == "v2" || pending.model == "prism") {  // 雲端 Colab 沒有 PRISM，改用 V2
                    put("unet", kotlinx.serialization.json.JsonPrimitive(if (pending.route == "local") H3_LOCAL_V2_UNET else H3_CLOUD_V2_UNET))
                }
            }.ifEmpty { null },
            gpus = if (pending.route == "cloud" && pending.gpus.isNotEmpty()) pending.gpus else null,
            segments = pending.segments,
            beats = if (pending.segments > 1 && pending.beats.any { it.isNotBlank() }) pending.beats else null,
            subjectId = pending.subjectId,
            // v2.5.3 影片一律不留在 Studio；只有使用者按「補上傳 Studio」才上傳
            keepInStudio = false,
            seed = pending.seed,
            t2v = pending.mode == "t2v",
            autoRef = pending.autoRef?.trim()?.takeIf { it.isNotEmpty() && pending.mode == "t2v" && pending.route == "local" && pending.subjectId == null },
            refVideo = refVideo,
            rawPrompt = (pending.rawPrompt && (pending.segments == 1 || pending.beats.size >= pending.segments && pending.beats.take(pending.segments).all { it.isNotBlank() })).takeIf { it },
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
                            repeatVideo = pending.copy(estimate = null, extendBase = null),
                            extendBase = pending.extendBase,
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
