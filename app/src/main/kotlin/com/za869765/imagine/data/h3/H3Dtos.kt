package com.za869765.imagine.data.h3

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// v2.0.0 H3 Studio(MiniMax H3 on Colab A100)API 資料結構。欄位對齊 h3-studio functions/api/[[path]].js。

@Serializable
data class H3Affordable(
    val normal: Double? = null,
    val fast: Double? = null,
)

/** GET /api/app/estimate — balance / after / affordable 在餘額未知時為 null。 */
@Serializable
data class H3Estimate(
    val quality: String = "normal",
    @SerialName("video_seconds") val videoSeconds: Double = 0.0,
    @SerialName("cold_min") val coldMin: Double = 0.0,
    val minutes: Double = 0.0,
    val cu: Double = 0.0,
    val ntd: Double = 0.0,
    val balance: Double? = null,
    @SerialName("balance_updated_at") val balanceUpdatedAt: Long? = null,
    val after: Double? = null,
    @SerialName("affordable_seconds_after") val affordableSecondsAfter: H3Affordable? = null,
    @SerialName("gpu_alive") val gpuAlive: Boolean = false,
    // v2.1.4 GPUtw 5090 路線(int8 20 步＋Sol-Attn):扣台幣,不扣 CU
    val route: String = "cloud",
    @SerialName("ntd_balance") val ntdBalance: Double? = null,
    @SerialName("ntd_after") val ntdAfter: Double? = null,
)

@Serializable
data class H3JobImage(
    val base64: String,
    val mime: String = "image/jpeg",
    val name: String,
)

/** POST /api/app/jobs — 伺服器要求 confirm=true 且 est_cu>0,否則 400。 */
@Serializable
data class H3JobRequest(
    val prompt: String,
    val images: List<H3JobImage>,
    val seconds: Int,
    val quality: String,
    val mode: String,
    val aspect: String,
    val confirm: Boolean,
    @SerialName("est_cu") val estCu: Double,
    // v2.0.1 冪等鍵:同一次確認只產生一個 id,重送同 id 伺服器回既有任務(duplicate=true),不會重複扣 CU
    @SerialName("client_request_id") val clientRequestId: String,
    // v2.1.4 route=gputw 時送 est_ntd(est_cu 填 0);exp 帶眨眼包等(伺服器 cleanExp 白名單)
    val route: String = "cloud",
    @SerialName("est_ntd") val estNtd: Double = 0.0,
    val exp: Map<String, Boolean>? = null,
)

@Serializable
data class H3JobCreated(
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("segment_id") val segmentId: String? = null,
    // 缺 run_id 不當失敗:視為「結果不明,請看最近任務」
    @SerialName("run_id") val runId: String? = null,
    val duplicate: Boolean = false,
)

@Serializable
data class H3Event(
    val kind: String? = null,
    val message: String? = null,
    val data: JsonElement? = null,
    val ts: Long? = null,
) {
    // data 形狀依事件而異(常見 {phase, pct});非物件或缺欄位一律 null
    val phase: String?
        get() = ((data as? JsonObject)?.get("phase") as? JsonPrimitive)?.takeIf { it.isString }?.content
    val pct: Double?
        get() = ((data as? JsonObject)?.get("pct") as? JsonPrimitive)?.doubleOrNull
}

@Serializable
data class H3Worker(
    val state: String? = null,
    val gpu: String? = null,
)

/** GET /api/app/jobs/{run_id} */
@Serializable
data class H3JobStatus(
    val status: String = "queued",
    val error: String? = null,
    @SerialName("cu_start") val cuStart: Double? = null,
    @SerialName("cu_end") val cuEnd: Double? = null,
    @SerialName("last_event") val lastEvent: H3Event? = null,
    val worker: H3Worker? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val output: JsonElement? = null,
) {
    val isFinished: Boolean get() = status == "done" || status == "failed" || status == "cancelled"

    /** 已用 CU = 開跑時餘額 − 目前/結束時餘額;任一未知回 null。 */
    val cuUsed: Double?
        get() = if (cuStart != null && cuEnd != null) (cuStart - cuEnd).coerceAtLeast(0.0) else null
}

@Serializable
data class H3ErrorBody(val error: String? = null)

// ── v2.0.7 H3 Studio 生圖(Qwen-Image 2.1)與 ✨ 優化提示詞。欄位對齊 [[path]].js 的 app/images 與 app/prompt/optimize ──

/** 筆電工人心跳回報的能力(v2.1.0 機器狀態用):image=會處理圖單(舊工人沒有)、ver=工人版本。 */
@Serializable
data class H3Caps(
    val qwen: Boolean = false,
    val image: Boolean = false,
    val refmod: Boolean = false,
    val ver: String? = null,
)

/** GET /api/app/images/estimate?mp&count&route — 派工結果 + 估價(分鐘 / NT$ / CU)+ 餘額(未知時 null)。 */
@Serializable
data class H3ImageEstimate(
    val route: String = "auto",
    val reason: String = "",
    val caps: H3Caps? = null,
    val minutes: Double = 0.0,
    val ntd: Double = 0.0,
    val cu: Double = 0.0,
    val text: String = "",
    // v2.1.3 生成紀錄平均(樣本 < 20 張時 statsReady=false,伺服器用預設值)
    @SerialName("gen_seconds") val genSeconds: Double? = null,
    val samples: Int = 0,
    @SerialName("stats_ready") val statsReady: Boolean = false,
    @SerialName("local_alive") val localAlive: Boolean = false,
    @SerialName("gputw_alive") val gputwAlive: Boolean = false,
    @SerialName("gputw_image_alive") val gputwImageAlive: Boolean = false,
    // v2.1.1 製圖機待命還剩幾分(開著且閒置時才有):這段時間下單不用再付開機費
    @SerialName("gputw_image_idle_left") val gputwImageIdleLeft: Double? = null,
    @SerialName("ntd_balance") val ntdBalance: Double? = null,
    @SerialName("cu_balance") val cuBalance: Double? = null,
)

/** POST /api/app/images — 伺服器要求 confirm=true,否則 400;client_request_id 同一筆不會重複建單。 */
@Serializable
data class H3ImageRequest(
    val prompt: String,
    val aspect: String,
    val mp: Int,
    val count: Int,
    @SerialName("ref_images") val refImages: List<H3JobImage>,
    @SerialName("allow_text") val allowText: Boolean,
    val transparent: Boolean = false,
    // v2.1.3 多視角角色圖:第 1 張參考照 → 2×2 四視角(count＝格子張數 1～2)
    val sheet: Boolean = false,
    val route: String,
    val confirm: Boolean,
    @SerialName("client_request_id") val clientRequestId: String,
)

@Serializable
data class H3ImageCreated(
    @SerialName("run_id") val runId: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    val route: String? = null,
    val reason: String? = null,
    val estimate: H3ImageEstimate? = null,
    val duplicate: Boolean = false,
)

@Serializable
data class H3ImageOut(
    val id: String,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

/** GET /api/app/images/{run_id} */
@Serializable
data class H3ImageStatus(
    val status: String = "queued",
    val error: String? = null,
    val route: String? = null,
    @SerialName("last_event") val lastEvent: H3Event? = null,
    val images: List<H3ImageOut> = emptyList(),
) {
    val isFinished: Boolean get() = status == "done" || status == "failed" || status == "cancelled"
}

/** POST /api/app/prompt/optimize — kind image|video|shot|director|subject|generic。 */
@Serializable
data class H3OptimizeRequest(val kind: String, val text: String, val context: Map<String, String> = emptyMap())

@Serializable
data class H3Optimized(
    val kind: String = "",
    val optimized: String = "",
    val notes: List<String> = emptyList(),
)
