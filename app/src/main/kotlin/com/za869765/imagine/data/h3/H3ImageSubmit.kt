package com.za869765.imagine.data.h3

import android.content.Context
import android.net.Uri
import com.za869765.imagine.data.prefs.SecurePrefs

/**
 * v2.0.7 估算當下凍結的 H3 生圖參數(確認視窗看到什麼就送什麼)。
 * clientRequestId:每次「新估算」產生一次;重送同一筆沿用同一個 → 伺服器去重,不會重複建單。
 */
data class PendingH3Image(
    val prompt: String,
    val refs: List<String>,
    val aspect: String,
    val mp: Int,
    val count: Int,
    val route: String,
    val allowText: Boolean,
    val clientRequestId: String,
    val estimate: H3ImageEstimate?,
)

/**
 * 送出 H3 生圖(會開機器／花錢)。呼叫端必須已取得使用者勾選確認,且要在 app 層 scope 執行。
 * 順序:壓縮參考圖 → POST(不自動重試)→ 記入最近任務。機器由伺服器當下決定,回應帶實際派工結果。
 */
object H3ImageSubmit {
    suspend fun run(
        ctx: Context,
        prefs: SecurePrefs,
        pending: PendingH3Image,
        onProgress: (String) -> Unit,
    ): H3Submit.Outcome {
        val est = pending.estimate ?: return H3Submit.Outcome.LocalError("缺少估算，未送出")

        val refs = ArrayList<H3JobImage>()
        for ((i, s) in pending.refs.withIndex()) {
            onProgress("壓縮參考圖 ${i + 1}/${pending.refs.size}…")
            val img = H3Images.encode(ctx, Uri.parse(s), i)
                ?: return H3Submit.Outcome.LocalError("第 ${i + 1} 張參考圖讀取或壓縮失敗，未送出（沒有花費）")
            refs.add(img)
        }

        onProgress("送出中…")
        val req = H3ImageRequest(
            prompt = pending.prompt,
            aspect = pending.aspect,
            mp = pending.mp,
            count = pending.count,
            refImages = refs,
            allowText = pending.allowText,
            route = pending.route,
            confirm = true,
            clientRequestId = pending.clientRequestId,
        )
        return when (val r = H3Client.submitImage(prefs, req)) {
            is H3Client.Result.Ok -> {
                val runId = r.value.runId
                if (runId.isNullOrBlank()) {
                    H3Submit.Outcome.Uncertain("伺服器沒有回傳任務編號")
                } else {
                    val route = r.value.route ?: est.route
                    H3Jobs.add(
                        ctx,
                        H3Jobs.Job(
                            runId = runId,
                            prompt = pending.prompt,
                            quality = "",
                            seconds = pending.count,
                            estCu = r.value.estimate?.cu ?: est.cu,
                            createdAtMs = System.currentTimeMillis(),
                            kind = "image",
                            meta = "${pending.aspect}・${if (pending.mp == 4) "2K" else "1K"}・${pending.count} 張・${H3Format.routeShort(route)}",
                            estNtd = r.value.estimate?.ntd ?: est.ntd,
                        ),
                    )
                    if (route == "gputw_image") H3MachineWatch.start(ctx) // v2.1.2 通知列倒數關機
                    H3Submit.Outcome.Created(runId, r.value.duplicate)
                }
            }
            is H3Client.Result.Err -> when {
                r.uncertain -> H3Submit.Outcome.Uncertain(r.message)
                r.code == 409 -> H3Submit.Outcome.Conflict(r.message)
                else -> H3Submit.Outcome.Rejected(r.message)
            }
        }
    }
}
