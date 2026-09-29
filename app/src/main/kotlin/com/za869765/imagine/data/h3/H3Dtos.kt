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
