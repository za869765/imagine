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
    val model: String? = null,  // v3.2.1 "h3image"＝筆電 H3-Image
    val compare: Boolean = false,  // v3.2.3 對照:這張送出後,同一句再用另一個筆電模型做一張(同一組並排)
    val upscale: String? = null,  // v3.2.5 放大 2 倍(model＝"upscale"):"7b"＝自然、"3b"＝銳利;refs 第 1 張就是要放大的圖
)

private val QUOTED = Regex("「([^」]{1,60})」|『([^』]{1,60})』|“([^”]{1,60})”|\"([^\"]{1,60})\"")
private val SIGN_BEFORE = Regex("(?:寫著|寫|標示|標語|招牌|牌子|標題|字樣|印著|印有|顯示|文字|字幕)\\s*[:：,，]?\\s*$")
private val SIGN_AFTER = Regex("^\\s*(?:的)?(?:招牌|字樣|標語|標誌|牌子|標題|字幕|文字|logo)", RegexOption.IGNORE_CASE)
private val TEXT_WORDS = Regex("對話泡泡|對話框|對白|台詞|字幕|標題|寫著|顯示文字|加上文字|speech bubble|caption", RegexOption.IGNORE_CASE)

private val SPOKEN = Regex("(?:(?<![小傳據聽解演學遊勸如是以雖再或])說(?!話|明|服|法|完|笑|書|謊|故事|得|了|過|的|時|不)|(?<![訪請疑學顧詢提慰])問(?!題|候|號|卷|診|路|得|了|過|的|時)|講(?!話|台|解|師|座|義|電話|究|課|笑話|得|了|過|的|時)|喊(?!叫|聲|得|了)|唸(?!書|經|得|了)|大叫|回答|表示)(?:著|道)?\\s*[:：,，]?\\s*([^，。！？!?,.；;\\n「『“\"]{2,30})")
private val SPOKEN_END = Regex("然後|接著|同時|說完|之後|並且|並|一邊|鏡頭")

/** v2.8.3 引號內的文字一律當台詞,沒引號時取「說／講／喊…」後面到標點為止的文字(只在手機本地比對);招牌、標題等畫面文字除外。 */
fun h3SpeechLines(prompt: String): List<String> {
    val quoted = QUOTED.findAll(prompt).toList()
    val lines = quoted
        .filterNot { SIGN_BEFORE.containsMatchIn(prompt.substring(0, it.range.first)) || SIGN_AFTER.containsMatchIn(prompt.substring(it.range.last + 1)) }
        .map { m -> m.groupValues.drop(1).first { it.isNotEmpty() }.trim() }
    val unquoted = SPOKEN.findAll(QUOTED.replace(prompt, "。")).map { it.groupValues[1].split(SPOKEN_END)[0].trim() }.filter { it.length >= 2 }
    return (lines + unquoted).filter { it.isNotEmpty() }.distinct()
}

/** v2.8.3 提示詞看起來要文字時自動勾選「允許文字」;使用者取消打勾就不畫。 */
fun h3WantsText(prompt: String): Boolean = TEXT_WORDS.containsMatchIn(prompt) || h3SpeechLines(prompt).isNotEmpty()

/** v2.8.2 寫實照片模型不會自己畫出台詞(實測 2 張都沒有),送出時才補明確指令;畫面與紀錄仍是原提示詞。 */
private fun withSpeechBubbles(prompt: String, lines: List<String>): String =
    prompt + lines.joinToString("") { " Draw a white comic speech bubble next to the head of the speaker containing exactly the ${if (it.any { c -> c.code in 0x4E00..0x9FFF }) "Traditional Chinese " else ""}text \"$it\" in large clear black letters." } +
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
        val speech = if (pending.sheet || !pending.allowText) emptyList() else h3SpeechLines(pending.prompt)
        val req = H3ImageRequest(
            prompt = if (pending.model == "upscale") "" else if (speech.isEmpty()) pending.prompt else withSpeechBubbles(pending.prompt, speech),  // 放大用不到提示詞,不送
            aspect = pending.aspect,
            mp = pending.mp,
            count = pending.count,
            refImages = refs,
            allowText = pending.allowText,
            transparent = pending.transparent,
            sheet = pending.sheet,
            route = pending.route,
            confirm = true,
            clientRequestId = pending.clientRequestId,
            seed = pending.seed,
            model = pending.model,
            upscale = pending.upscale,
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
                            meta = if (pending.model == "upscale") "放大 2 倍・${if (pending.upscale == "3b") "銳利" else "自然"}・${H3Format.routeShort(route)}" else (if (pending.sheet) "多視角 ${pending.count * 4} 個角度" else "${pending.aspect}・${if (pending.mp == 4) "2K" else "1K"}・${pending.count} 張" + (if (pending.transparent) "・透明背景" else "")) + "・${H3Format.routeShort(route)}" + (if (pending.model == "h3image") "・H3-Image" else if (route == "local") "・Turbo" else ""),
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
