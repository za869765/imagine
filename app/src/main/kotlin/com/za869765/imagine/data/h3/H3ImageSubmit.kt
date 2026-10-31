package com.za869765.imagine.data.h3

import android.content.Context
import android.net.Uri
import com.za869765.imagine.data.prefs.SecurePrefs

/**
 * v2.0.7 估算當下凍結的 H3 生圖參數(確認視窗看到什麼就送什麼)。
 * clientRequestId:每次「新估算」產生一次;重送同一筆沿用同一個 → 伺服器去重,不會重複建單。
 */
@kotlinx.serialization.Serializable
data class PendingH3Image(
    val prompt: String,
    val refs: List<String>,
    val aspect: String,
    val mp: Int,
    val count: Int,
    val route: String,
    val allowText: Boolean,
    val transparent: Boolean = false,
    val sheet: Boolean = false,
    val clientRequestId: String,
    val estimate: H3ImageEstimate?,
    val groupId: String? = null,
    val seed: Long? = null,
)

private val SPEECH_LINE = Regex("(?:說|講|喊|問|答|表示|叫|唸|念|道|嘆|台詞|對白)(?:著|道)?\\s*[:：,，]?\\s*[「『“\"]([^」』”\"]{1,60})[」』”\"]")

/** v2.8.2 提示詞裡「說／講／喊…」後面引號內的台詞(只在手機本地比對)。 */
fun h3SpeechLines(prompt: String): List<String> =
    SPEECH_LINE.findAll(prompt).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()

/** v2.8.2 寫實照片模型不會自己畫出台詞(實測 2 張都沒有),送出時才補明確指令;畫面與紀錄仍是原提示詞。 */
private fun withSpeechBubbles(prompt: String, lines: List<String>): String =
    prompt + lines.joinToString("") { " Draw a white comic speech bubble next to the head of the speaker containing exactly the Traditional Chinese text \"$it\" in large clear black letters." } +
        " Do not draw quotation marks. The spoken text appears only inside the speech bubbles, nowhere else. Do not add any other text that was not requested."

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
        val speech = if (pending.sheet) emptyList() else h3SpeechLines(pending.prompt)
        val req = H3ImageRequest(
            prompt = if (speech.isEmpty()) pending.prompt else withSpeechBubbles(pending.prompt, speech),
            aspect = pending.aspect,
            mp = pending.mp,
            count = pending.count,
            refImages = refs,
            allowText = pending.allowText || speech.isNotEmpty(),
            transparent = pending.transparent,
            sheet = pending.sheet,
            route = pending.route,
            confirm = true,
            clientRequestId = pending.clientRequestId,
            seed = pending.seed,
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
                            meta = (if (pending.sheet) "多視角 ${pending.count * 4} 個角度" else "${pending.aspect}・${if (pending.mp == 4) "2K" else "1K"}・${pending.count} 張" + (if (pending.transparent) "・透明背景" else "")) + "・${H3Format.routeShort(route)}",
                            estNtd = r.value.estimate?.ntd ?: est.ntd,
                            etaAt = r.value.estimate?.etaAt ?: est.etaAt,
                            keepInStudio = false,  // v2.5.5 存進手機後就請伺服器刪除
                            route = route,
                            repeat = pending.copy(estimate = null),
                            groupId = pending.groupId ?: runId,
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
