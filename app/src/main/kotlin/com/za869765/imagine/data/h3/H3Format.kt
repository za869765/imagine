package com.za869765.imagine.data.h3

import java.util.Locale

/** H3 顯示文字(純函式,方便檢查)。 */
object H3Format {
    fun qualityLabel(q: String): String = if (q == "fast") "快速版（4 步）" else "正常版（bf16 20 步）"

    fun cu(v: Double): String = String.format(Locale.US, "%.2f", v)

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

    /** 餘額超過這麼久沒更新就當「未知」。 */
    const val BALANCE_MAX_AGE_MS = 30L * 60 * 1000

    /**
     * 確認視窗的阻擋原因;null = 可以送出。
     * 規則:估算必須 > 0;餘額未知或超過 30 分鐘沒更新不能送;預估超過餘額不能送。
     */
    fun blockReason(e: H3Estimate, nowMs: Long = System.currentTimeMillis()): String? = when {
        e.cu <= 0.0 -> "估算結果異常（CU ≤ 0），不能送出"
        e.balance == null -> "目前 CU 餘額未知，無法確認是否足夠 — 請先按下方「更新餘額」"
        e.balanceUpdatedAt == null || nowMs - e.balanceUpdatedAt > BALANCE_MAX_AGE_MS ->
            "CU 餘額已超過 30 分鐘沒更新（${ago(e.balanceUpdatedAt, nowMs)}），視為未知 — 請先按下方「更新餘額」"
        e.cu > e.balance -> "預估 ${cu(e.cu)} CU 超過目前餘額 ${cu(e.balance)} CU，無法送出"
        else -> null
    }

    /** 這個阻擋原因能靠「更新餘額」解決嗎? */
    fun balanceStale(e: H3Estimate, nowMs: Long = System.currentTimeMillis()): Boolean =
        e.balance == null || e.balanceUpdatedAt == null || nowMs - e.balanceUpdatedAt > BALANCE_MAX_AGE_MS

    /** 送出前重新估算:CU 或 GPU 開機狀態變了就要重新確認。 */
    fun estimateChanged(old: H3Estimate, fresh: H3Estimate): Boolean =
        kotlin.math.abs(old.cu - fresh.cu) > 0.005 || old.gpuAlive != fresh.gpuAlive
}
