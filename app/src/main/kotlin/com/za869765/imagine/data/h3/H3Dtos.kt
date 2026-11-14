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
    // v2.1.9 排在該機器最後面的預計完成(分鐘、epoch ms)
    @SerialName("eta_min") val etaMin: Double? = null,
    @SerialName("eta_at") val etaAt: Long? = null,
    // v3.2.7 白皙:筆電有沒有白皙要用的模型檔(沒有＝按鈕不能用);fair＝這次預估是不是照白皙算
    @SerialName("fair_available") val fairAvailable: Boolean? = null,
    val fair: Boolean = false,
)

@Serializable
data class H3ImportRequest(
    val kind: String, val base64: String = "", val mime: String = "", val name: String = "", val prompt: String, val seconds: Int = 0,
    // v2.4.1 影片先串流上傳（PUT /api/app/upload）拿到 key，再帶 upload_key 匯入
    @SerialName("upload_key") val uploadKey: String? = null,
)

@Serializable
data class H3UploadResult(val ok: Boolean = false, val key: String? = null, val size: Long = 0)

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
    // v2.9.0 exp 也帶 unet(筆電選 V2)
    val exp: Map<String, JsonElement>? = null,
    // v2.1.5 超高畫質可用的 Colab GPU 範圍(伺服器照 G4→H100→A100 優先順序自動挑,排不到不扣 CU)
    val gpus: List<String>? = null,
    // v2.1.8 長片:segments 段接續(每段 seconds 秒);beats=每段自己寫的內容(空字串＝交給導演大腦拆);subject_id=資產庫角色(鎖臉鎖聲)
    val segments: Int = 1,
    val beats: List<String>? = null,
    @SerialName("subject_id") val subjectId: String? = null,
    // v2.5.0 勾「不留在 Studio」→ 伺服器標 app_only，Studio 專案列表不顯示
    @SerialName("keep_in_studio") val keepInStudio: Boolean = true,
    // v2.5.9 固定種子(null=隨機);長片每段依序 +1
    val seed: Long? = null,
    // v2.9.0 文生影:不放圖、不綁角色,只用文字(伺服器拿掉參考圖節點)
    val t2v: Boolean = false,
    // v3.2.6 一條龍:人物長相(只在筆電、文生影、沒綁角色時送);筆電先用 Turbo 生一張參考人像再拿它生影片
    @SerialName("auto_ref") val autoRef: String? = null,
    // v3.1.0 換人(mode=motion):先 POST /api/app/refvideo 拿到 file_id
    @SerialName("ref_video") val refVideo: H3RefVideo? = null,
    // v3.2.7 範本:整段提示詞原樣送給模型(伺服器不轉對白標記、不包成官方格式);只在單段有效
    @SerialName("raw_prompt") val rawPrompt: Boolean? = null,
)

@Serializable
data class H3RefVideo(
    @SerialName("file_id") val fileId: String,
    val seconds: Double? = null,
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
    val eta: H3Eta? = null,
    val route: String? = null,
    /** v2.4.4 長片機器端沒串成完整版時，各段影片網址（依段落順序），APP 自己接 */
    @SerialName("segment_urls") val segmentUrls: List<String>? = null,
    val seed: Long? = null,
    /** v2.9.4 工人在本機把中文描述翻成英文的紀錄(含翻好的 [Shot] 句),給使用者自己對照 */
    val translated: String? = null,
    /** v3.2.6 一條龍:筆電先生的那張參考人像(站內路徑;還沒生好或不是一條龍＝null) */
    @SerialName("ref_image_url") val refImageUrl: String? = null,
    /** v3.2.7 白皙:調白前的原片(站內路徑);video_url 是調白後。不是白皙的單＝null */
    @SerialName("raw_video_url") val rawVideoUrl: String? = null,
) {
    val isFinished: Boolean get() = status == "done" || status == "failed" || status == "cancelled"

    /** 已用 CU = 開跑時餘額 − 目前/結束時餘額;任一未知回 null。 */
    val cuUsed: Double?
        get() = if (cuStart != null && cuEnd != null) (cuStart - cuEnd).coerceAtLeast(0.0) else null
}

@Serializable
data class H3ErrorBody(val error: String? = null)

/** v2.9.8 GET /api/app/r2usage — Cloudflare R2 用量(依資料夾分類)＋每日快照 */
@Serializable
data class H3R2Cat(val bytes: Long = 0, val objects: Int = 0)

@Serializable
data class H3R2Day(val day: String = "", val bytes: Long = 0, val objects: Int = 0)

@Serializable
data class H3R2Usage(
    val bytes: Long = 0,
    val objects: Int = 0,
    val cats: Map<String, H3R2Cat> = emptyMap(),
    val truncated: Boolean = false,
    val at: Long = 0,
    val gb: Double = 0.0,
    @SerialName("free_gb") val freeGb: Double = 10.0,
    @SerialName("est_usd_month") val estUsdMonth: Double = 0.0,
    val days: List<H3R2Day> = emptyList(),
    /** v2.9.9 Studio 作品 7 天沒動就清 R2 成品;快到期的(剩 2 天內)列在這 */
    val expiring: List<H3R2Expiring> = emptyList(),
    @SerialName("keep_days") val keepDays: Int = 7,
)

@Serializable
data class H3R2Expiring(val id: String = "", val name: String = "", val segs: Int = 0, @SerialName("days_left") val daysLeft: Int = 0)

/** v2.9.7 筆電 Gemma 對話(POST /api/app/chat → GET /api/app/chat/{id}) */
@Serializable
data class H3ChatMessage(val role: String, val content: String, val images: List<String>? = null)  // v3.0.4 images＝JPEG base64(只有筆電 Gemma 看圖用)

@Serializable
data class H3ChatRequest(val messages: List<H3ChatMessage>, val model: String = "gemma")  // v3.1.6 model＝gemma／ornith／defiant

@Serializable
data class H3ChatCreated(val id: String? = null, val busy: Boolean = false)

@Serializable
data class H3ChatStatus(val status: String = "queued", val text: String? = null, val error: String? = null, val busy: Boolean = false,
    @SerialName("wait_min") val waitMin: Double? = null, @SerialName("start_at") val startAt: Long? = null)  // v3.0.5 筆電在生成時預計幾點開始回答

/** v3.2.0 下載分頁:筆電代下載(POST /api/app/download → GET /api/app/download/{id});parts＝依序拼起來的站內路徑 */
@Serializable
data class H3DownloadRequest(
    val url: String,
    val mode: String,
    val confirm: Boolean = true,
    @SerialName("client_request_id") val clientRequestId: String,
    /** v3.3.3 筆電測試片:browse=true 且 name 空＝列清單;name 有值＝把那一支傳回手機 */
    val browse: Boolean = false,
    val name: String = "",
    /** v3.3.5 清單一頁 40 個,從第幾個開始 */
    val offset: Int = 0,
    /** v3.3.7 browse＋name＋delete＝刪掉筆電測試資料夾裡這個檔 */
    val delete: Boolean = false,
    /** v3.4.9 測試片來源:"desk"＝桌機、"local"＝筆電;不帶＝伺服器決定(桌機在線就桌機) */
    val source: String? = null,
    /** v3.5.1 browse＋name＋recipe＝只要這支片的配方(提示詞、秒數、種子、參考圖檔名) */
    val recipe: Boolean = false,
)

@Serializable
/** kind＝video／image;dur＝影片秒數;thumb＝小縮圖(JPEG 的 base64),舊版工人沒有這三欄 */
data class H3LaptopFile(val name: String = "", val bytes: Long = 0, val mtime: Long = 0, val kind: String = "video", val dur: Double = 0.0, val thumb: String = "",
                        /** v3.4.5 所在的日期資料夾(YYYY-MM-DD);name 可能帶「日期/」前綴 */
                        val day: String = "",
                        /** v3.5.1 這支片有留配方(可加進最近成品再做、微調、續做) */
                        val recipe: Boolean = false) {
    /** 不含日期資料夾的檔名(顯示、比對「已傳」用) */
    val base: String get() = name.substringAfterLast('/')
}

@Serializable
data class H3DownloadCreated(@SerialName("run_id") val runId: String? = null)

@Serializable
data class H3DownloadStatus(
    val status: String = "queued",
    val error: String? = null,
    val phase: String? = null,
    val pct: Double? = null,
    @SerialName("eta_sec") val etaSec: Long? = null,
    val title: String? = null,
    val ext: String? = null,
    val bytes: Long? = null,
    val parts: List<String> = emptyList(),
    val files: List<H3LaptopFile>? = null,
    val total: Int? = null,
    val deleted: String? = null,
    val recipe: H3Recipe? = null,
)

/** v3.5.1 測試片的配方:送件當下的設定。refs＝參考圖檔名(到測試資料夾的 _recipes/refs/ 拿) */
@Serializable
data class H3Recipe(
    val prompt: String = "",
    val beats: List<String> = emptyList(),
    val seconds: Int = 5,
    val segments: Int = 1,
    val quality: String = "normal",
    val mode: String = "ref",
    val aspect: String = "9:16",
    val seed: Long? = null,
    @SerialName("raw_prompt") val rawPrompt: Boolean = false,
    val fair: Boolean = false,
    @SerialName("fair_male") val fairMale: Boolean = false,
    val route: String = "desk",
    val refs: List<String> = emptyList(),
)

/** GET /api/app/subjects — H3 Studio 資產庫角色(face_locked＝已建臉部模型;voice＝有參考音)。 */
@Serializable
data class H3Subject(val id: String, val name: String, val desc: String = "", @SerialName("face_locked") val faceLocked: Boolean = false, val voice: Boolean = false, val thumb: String? = null)

@Serializable
data class H3SubjectList(val subjects: List<H3Subject> = emptyList())

/** v2.1.9 單一任務預計完成:etaAt＝epoch ms;ahead＝前面還有幾個。 */
@Serializable
data class H3Eta(@SerialName("eta_min") val etaMin: Double = 0.0, @SerialName("eta_at") val etaAt: Long = 0, val ahead: Int = 0)

/** GET /api/app/machines — 每台機器:state=idle|standby|busy|booting|off|offline,label 例「生成中・排隊 2 個」「待機倒數・6 分後關機」。 */
@Serializable
data class H3Machine(
    val route: String,
    val name: String,
    val cost: String = "",
    val state: String = "off",
    val label: String = "",
    val detail: String = "",
    val queue: Int = 0,
    val running: Int = 0,
    val waiting: Int = 0,
    @SerialName("wait_min") val waitMin: Double = 0.0,
    @SerialName("standby_left_min") val standbyLeftMin: Double? = null,
)

@Serializable
data class H3MachineBoard(
    val machines: List<H3Machine> = emptyList(),
    val alerts: List<String> = emptyList(),
    // v3.4.8 標題列燈號用：Colab 剩幾 CU、GPUtw 剩幾元（伺服器不知道時是 null）
    @SerialName("cu_balance") val cuBalance: Double? = null,
    @SerialName("ntd_balance") val ntdBalance: Double? = null,
)

// ── v2.0.7 H3 Studio 生圖(Qwen-Image 2.1)與 ✨ 優化提示詞。欄位對齊 [[path]].js 的 app/images 與 app/prompt/optimize ──

/** 筆電工人心跳回報的能力(v2.1.0 機器狀態用):image=會處理圖單(舊工人沒有)、ver=工人版本。 */
@Serializable
data class H3Caps(
    val qwen: Boolean = false,
    val image: Boolean = false,
    val refmod: Boolean = false,
    val ver: String? = null,
    @SerialName("qwen_2k") val qwen2k: Boolean = false,  // v3.2.2 筆電 Turbo 可直接做 2K
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
    @SerialName("eta_min") val etaMin: Double? = null,
    @SerialName("eta_at") val etaAt: Long? = null,
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
    val seed: Long? = null,
    // v3.2.1 "h3image"＝筆電 MiniMax-H3-Image(2K、6 步);null＝原本的 Qwen;v3.2.5 "upscale"＝放大 2 倍
    val model: String? = null,
    // v3.2.5 放大用哪個 SeedVR2 模型:"7b"＝自然、"3b"＝銳利(model＝"upscale" 時才有)
    val upscale: String? = null,
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
    val eta: H3Eta? = null,
    val seed: Long? = null,
) {
    val isFinished: Boolean get() = status == "done" || status == "failed" || status == "cancelled"
}

/** POST /api/app/prompt/optimize — kind image|video|shot|director|subject|generic。 */
@Serializable
data class H3OptimizeRequest(val kind: String, val text: String, val context: Map<String, String> = emptyMap(),
    // v3.0.0 優化大腦:gemma(筆電,預設)/claude(筆電 Claude 訂閱)/claude_api/openai/grok/gemini;job_id＝筆電處理中輪詢用
    val brain: String = "gemma", @SerialName("job_id") val jobId: String? = null)

@Serializable
data class H3Optimized(
    val kind: String = "",
    val optimized: String = "",
    val notes: List<String> = emptyList(),
    val pending: String? = null,
    val brain: String? = null,
)
