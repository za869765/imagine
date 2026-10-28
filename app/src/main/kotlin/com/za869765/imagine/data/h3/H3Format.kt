package com.za869765.imagine.data.h3

import java.util.Locale

/** H3 顯示文字(純函式,方便檢查)。 */
object H3Format {
    fun qualityLabel(q: String): String = if (q == "fast") "快速版（4 步）" else "正常版（bf16 20 步）"

    // v2.1.4 生影三級(使用者 10/28 定):超高畫質＝bf16(Colab)、高畫質＝int8(GPUtw 5090)、低畫質＝Q4(筆電,免費)
    val VIDEO_TIERS = listOf("high", "ultra", "low")
    fun tierRoute(t: String): String = when (t) { "ultra" -> "cloud"; "low" -> "local"; else -> "gputw" }
    fun tierQuality(t: String): String = when (t) { "low" -> "fast"; else -> "normal" }
    fun tierName(t: String): String = when (t) {
        "ultra" -> "超高畫質・bf16（Colab A100）"
        "low" -> "低畫質・Q4 草稿（筆電，免費）"
        else -> "高畫質・int8（GPUtw 5090，建議）"
    }
    fun tierDesc(t: String): String = when (t) {
        "ultra" -> "官方 bf16 20 步・畫質基準・每 5 秒約 10 分・扣 CU"
        "low" -> "Q4 GGUF＋Acc 8 步、544×928・每 5 秒約 10 分・最長 8 秒・筆電要開著"
        else -> "int8 未剪枝混合版 20 步＋Sol-Attn・每 5 秒約 2.8 分・扣台幣・經濟實用"
    }

    fun cu(v: Double): String = String.format(Locale.US, "%.2f", v)

    /** v2.1.9 台北時間 HH:mm。 */
    fun clock(ms: Long): String = java.text.SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Taipei") }.format(java.util.Date(ms))

    /** 「預計 01:23 完成（約 12 分）」;etaAt 已過就寫「快好了」。 */
    fun eta(etaAt: Long?, etaMin: Double? = null): String {
        if (etaAt == null || etaAt <= 0) return ""
        val left = ((etaAt - System.currentTimeMillis()) / 60_000.0)
        return if (left <= 0.5) "預計 ${clock(etaAt)} 完成（快好了）" else "預計 ${clock(etaAt)} 完成（約 ${kotlin.math.ceil(left).toInt()} 分）"
    }

    fun ntd(v: Double): String = String.format(Locale.US, "%.1f", v)

    fun minutes(v: Double): String = String.format(Locale.US, "%.1f", v)

    /** 還能生成的秒數:無條件捨去到整秒,負數當 0。 */
    fun seconds(v: Double?): String = if (v == null) "未知" else "${v.coerceAtLeast(0.0).toLong()} 秒"

    /** 餘額更新時間(epoch ms)→「3 分鐘前」。 */
    fun ago(updatedAtMs: Long?, nowMs: Long = System.currentTimeMillis()): String {
        if (updatedAtMs == null || updatedAtMs <= 0) return "更新時間未知"
        val sec = ((nowMs - updatedAtMs) / 1000).coerceAtLeast(0)
        return when {
            sec < 60 -> "剛剛更新"
            sec < 3600 -> "${sec / 60} 分鐘前更新"
            sec < 86400 -> "${sec / 3600} 小時前更新"
            else -> "${sec / 86400} 天前更新"
        }
    }

    fun statusLabel(s: String): String = when (s) {
        "queued" -> "排隊中"
        "launching" -> "GPU 開機中"
        "running" -> "生成中"
        "done" -> "完成"
        "failed" -> "失敗"
        "cancelled" -> "已取消"
        else -> s
    }

    // ── v2.0.7 生圖:派工機器與尺寸 ──
    val IMAGE_ROUTES = listOf("auto", "local", "gputw_image", "gputw", "cloud")

    fun routeLabel(r: String): String = when (r) {
        "auto" -> "自動（筆電免費 → 開著的機器 → 製圖機）"
        "local" -> "筆電 3070（免費，一張約 1～2 分）"
        "gputw_image" -> "製圖機（GPUtw 只搬 Qwen 權重，按時計費）"
        "gputw" -> "GPUtw 5090（影片機順便做）"
        "cloud" -> "Colab A100（花 CU）"
        else -> r
    }

    /** v2.1.0 確認視窗用的機器名(比 routeShort 完整)。 */
    fun routeName(r: String?): String = when (r) {
        "auto" -> "自動"
        "local" -> "筆電 3070"
        "gputw_image" -> "製圖機（GPUtw 5090）"
        "gputw" -> "GPUtw 5090（影片機）"
        "cloud" -> "Colab A100"
        else -> r ?: "—"
    }

    fun routeShort(r: String?): String = when (r) {
        "auto" -> "自動"
        "local" -> "筆電"
        "gputw_image" -> "製圖機"
        "gputw" -> "GPUtw 5090"
        "cloud" -> "Colab"
        else -> r ?: "—"
    }

    /** Qwen 尺寸(對齊伺服器 QWEN_SIZES:約 1 MP;2K 各乘 2)。 */
    fun imageSize(aspect: String, mp: Int): String {
        val (w, h) = when (aspect) {
            "16:9" -> 1344 to 768
            "1:1" -> 1024 to 1024
            "3:4" -> 896 to 1152
            "4:3" -> 1152 to 896
            else -> 768 to 1344
        }
        val k = if (mp == 4) 2 else 1
        return "${w * k}×${h * k}"
    }

    /** 餘額超過這麼久沒更新就當「未知」。 */
    const val BALANCE_MAX_AGE_MS = 30L * 60 * 1000

    /**
     * 確認視窗的阻擋原因;null = 可以送出。
     * 規則:估算必須 > 0;餘額未知或超過 30 分鐘沒更新不能送;預估超過餘額不能送。
     */
    fun blockReason(e: H3Estimate, nowMs: Long = System.currentTimeMillis()): String? = when {
        // v2.1.4 GPUtw:扣台幣,只擋超過 GPUtw 餘額
        e.route == "local" -> if (!e.gpuAlive) "筆電工人不在線，低畫質（免費）現在做不了" else null
        e.route == "gputw" -> if (e.ntdBalance != null && e.ntd > e.ntdBalance) "預估 NT$${ntd(e.ntd)} 超過 GPUtw 餘額 NT$${ntd(e.ntdBalance)}" else null
        e.cu <= 0.0 -> "估算結果異常（CU ≤ 0），不能送出"
        e.balance == null -> "目前 CU 餘額未知，無法確認是否足夠 — 請先按下方「更新餘額」"
        e.balanceUpdatedAt == null || nowMs - e.balanceUpdatedAt > BALANCE_MAX_AGE_MS ->
            "CU 餘額已超過 30 分鐘沒更新（${ago(e.balanceUpdatedAt, nowMs)}），視為未知 — 請先按下方「更新餘額」"
        e.cu > e.balance -> "預估 ${cu(e.cu)} CU 超過目前餘額 ${cu(e.balance)} CU，無法送出"
        else -> null
    }

    /** 這個阻擋原因能靠「更新餘額」解決嗎? */
    fun balanceStale(e: H3Estimate, nowMs: Long = System.currentTimeMillis()): Boolean =
        e.route != "gputw" && e.balance == null || e.balanceUpdatedAt == null || nowMs - e.balanceUpdatedAt > BALANCE_MAX_AGE_MS

    /** 送出前重新估算:CU 或 GPU 開機狀態變了就要重新確認。 */
    fun estimateChanged(old: H3Estimate, fresh: H3Estimate): Boolean =
        kotlin.math.abs(old.cu - fresh.cu) > 0.005 || kotlin.math.abs(old.ntd - fresh.ntd) >= 1.0 || old.gpuAlive != fresh.gpuAlive
}
