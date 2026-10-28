package com.za869765.imagine.data.h3

import com.za869765.imagine.BuildConfig
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * H3Client — H3 Studio(h3-studio.pages.dev)APP API。
 * 網址由使用者設定,所以不走 Retrofit 固定 baseUrl,直接 OkHttp + kotlinx.serialization(同 KeyTester 做法)。
 * 每個請求帶 Authorization: Bearer <APP 權杖> 與自訂 User-Agent(Cloudflare 會擋預設 UA)。
 * ⚠️ 不掛 HttpLoggingInterceptor — 權杖不得出現在任何 log。
 * ⚠️ 權杖只送往設定的 https 網址本身(同 host),絕不送到其他 host 或明文 http。
 */
object H3Client {
    const val DEFAULT_BASE_URL = "https://h3-studio.pages.dev"

    val userAgent: String get() = "imagine-app/${BuildConfig.VERSION_NAME}"

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        /**
         * uncertain=true:網路中斷 / 5xx / 回應解析失敗 — 請求可能已被伺服器處理(送出任務時=可能已建立)。
         */
        data class Err(val message: String, val code: Int = 0, val uncertain: Boolean = false) : Result<Nothing>()
    }

    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // 送出任務專用:不自動重試(避免 OkHttp 在連線失敗時悄悄重送 POST),上傳最多 9 張圖給足時間
    private val submitHttp: OkHttpClient by lazy {
        http.newBuilder()
            .retryOnConnectionFailure(false)
            .writeTimeout(180, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private val downloadHttp: OkHttpClient by lazy {
        http.newBuilder().readTimeout(300, TimeUnit.SECONDS).build()
    }

    private const val BAD_URL = "H3 Studio 網址必須是 https:// 開頭的有效網址"

    fun baseUrl(prefs: SecurePrefs): String = prefs.h3BaseUrl.trim().trimEnd('/')

    /** 設定的網址是否可用(https + 可解析)。 */
    fun isBaseUrlValid(prefs: SecurePrefs): Boolean {
        val u = baseUrl(prefs).toHttpUrlOrNull() ?: return false
        return u.isHttps
    }

    /**
     * video_url(相對路徑 /api/video/<id>)→ 絕對網址。只接受「/」開頭的站內路徑,
     * 或與設定網址同 host 的 https 網址;其他一律 null(不會把權杖帶去別的地方)。
     */
    fun absoluteUrl(prefs: SecurePrefs, path: String): String? {
        if (!isBaseUrlValid(prefs)) return null
        val base = baseUrl(prefs).toHttpUrlOrNull() ?: return null
        if (path.startsWith("/") && !path.startsWith("//")) {
            return (baseUrl(prefs) + path).toHttpUrlOrNull()?.takeIf { it.host == base.host && it.isHttps }?.toString()
        }
        val abs = path.toHttpUrlOrNull() ?: return null
        return if (abs.isHttps && abs.host == base.host) abs.toString() else null
    }

    /** ExoPlayer 用的 header(只配合 absoluteUrl() 驗過的網址使用)。 */
    fun authHeaders(prefs: SecurePrefs): Map<String, String> =
        mapOf("Authorization" to "Bearer ${prefs.h3Token.orEmpty()}")

    private fun request(prefs: SecurePrefs, path: String): Request.Builder? {
        val url = absoluteUrl(prefs, path) ?: return null
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${prefs.h3Token.orEmpty()}")
            .header("User-Agent", userAgent)
    }

    private suspend inline fun <reified T> call(prefs: SecurePrefs, client: OkHttpClient, req: Request?): Result<T> =
        withContext(Dispatchers.IO) {
            if (prefs.h3Token.isNullOrBlank()) return@withContext Result.Err("未設定 H3 Studio 權杖")
            if (req == null) return@withContext Result.Err(BAD_URL)
            try {
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@use Result.Err(errorMessage(resp, body), resp.code, uncertain = resp.code >= 500)
                    }
                    Result.Ok(json.decodeFromString<T>(body))
                }
            } catch (e: IOException) {
                Result.Err("無法連線 H3 Studio，請檢查網路或網址", uncertain = true)
            } catch (e: kotlinx.serialization.SerializationException) {
                Result.Err("H3 Studio 回應格式無法解析", uncertain = true)
            } catch (e: IllegalArgumentException) {
                Result.Err("H3 Studio 回應格式無法解析", uncertain = true)
            }
        }

    private fun errorMessage(resp: Response, body: String): String {
        val serverMsg = runCatching { json.decodeFromString<H3ErrorBody>(body).error }.getOrNull()
        val prefix = when (resp.code) {
            401 -> "權杖無效（401）"
            403 -> "被拒絕（403，權杖無此權限或 User-Agent 被擋）"
            404 -> "找不到（404）"
            409 -> "伺服器拒絕送出（409）"
            413 -> "圖片太大（413）"
            in 500..599 -> "H3 Studio 伺服器錯誤（${resp.code}）"
            else -> "H3 Studio 回應 ${resp.code}"
        }
        return if (serverMsg.isNullOrBlank()) prefix else "$prefix：${serverMsg.take(200)}"
    }

    /** 不消耗 CU 的估算(同時拿餘額)。seconds 4..15、quality normal|fast。 */
    suspend fun estimate(prefs: SecurePrefs, seconds: Int, quality: String): Result<H3Estimate> =
        call<H3Estimate>(prefs, http, request(prefs, "/api/app/estimate?seconds=$seconds&quality=$quality")?.get()?.build())

    /** ⚠️ 會消耗 Colab CU — 只能在使用者於確認視窗勾選並按「確認開跑」後呼叫。 */
    suspend fun submit(prefs: SecurePrefs, body: H3JobRequest): Result<H3JobCreated> {
        require(body.confirm && body.estCu > 0 && body.clientRequestId.isNotBlank()) { "H3 送出前必須經過確認" }
        val payload = json.encodeToString(body).toRequestBody("application/json".toMediaType())
        return call<H3JobCreated>(prefs, submitHttp, request(prefs, "/api/app/jobs")?.post(payload)?.build())
    }

    /** 要求伺服器查一次 CU 餘額(GitHub Actions 讀 colab usage,不消耗 CU,約 30~60 秒後更新)。 */
    suspend fun refreshCu(prefs: SecurePrefs): Result<Unit> {
        val req = request(prefs, "/api/cu/refresh")?.post("{}".toRequestBody("application/json".toMediaType()))?.build()
        return when (val r = call<H3ErrorBody>(prefs, http, req)) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }
    }

    suspend fun job(prefs: SecurePrefs, runId: String): Result<H3JobStatus> =
        call<H3JobStatus>(prefs, http, request(prefs, "/api/app/jobs/$runId")?.get()?.build())

    suspend fun cancel(prefs: SecurePrefs, runId: String): Result<Unit> {
        val req = request(prefs, "/api/runs/$runId/cancel")?.post("{}".toRequestBody("application/json".toMediaType()))?.build()
        return when (val r = call<H3ErrorBody>(prefs, http, req)) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }
    }

    // ── v2.0.7 Qwen-Image 2.1 生圖(估算不花錢 → 確認 → 送單 → 輪詢)與 ✨ 優化提示詞 ──

    /** 不花錢的生圖估算:派工結果(筆電 / 製圖機 / GPUtw / Colab)+ 分鐘 / NT$ / CU + 餘額。 */
    suspend fun imageEstimate(prefs: SecurePrefs, mp: Int, count: Int, route: String): Result<H3ImageEstimate> =
        call<H3ImageEstimate>(prefs, http, request(prefs, "/api/app/images/estimate?mp=$mp&count=$count&route=${enc(route)}")?.get()?.build())

    /** ⚠️ 會開機器／花錢 — 只能在使用者於確認視窗勾選並按「確認開跑」後呼叫。 */
    suspend fun submitImage(prefs: SecurePrefs, body: H3ImageRequest): Result<H3ImageCreated> {
        require(body.confirm && body.clientRequestId.isNotBlank()) { "H3 生圖送出前必須經過確認" }
        val payload = json.encodeToString(body).toRequestBody("application/json".toMediaType())
        return call<H3ImageCreated>(prefs, submitHttp, request(prefs, "/api/app/images")?.post(payload)?.build())
    }

    suspend fun imageRun(prefs: SecurePrefs, runId: String): Result<H3ImageStatus> =
        call<H3ImageStatus>(prefs, http, request(prefs, "/api/app/images/$runId")?.get()?.build())

    /** ✨ 優化提示詞:用 H3 Studio 的導演大腦改寫並說明改了什麼(不花 CU)。 */
    suspend fun optimizePrompt(prefs: SecurePrefs, kind: String, text: String, context: Map<String, String> = emptyMap()): Result<H3Optimized> {
        val payload = json.encodeToString(H3OptimizeRequest(kind, text, context)).toRequestBody("application/json".toMediaType())
        return call<H3Optimized>(prefs, http, request(prefs, "/api/app/prompt/optimize")?.post(payload)?.build())
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    /** 下載成品位元組(帶權杖,只限站內路徑)與 content-type;失敗回 null。 */
    suspend fun downloadBytes(prefs: SecurePrefs, path: String): Pair<ByteArray, String?>? =
        withContext(Dispatchers.IO) {
            val req = request(prefs, path)?.get()?.build() ?: return@withContext null
            runCatching {
                downloadHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    bytes to resp.header("content-type")
                }
            }.getOrNull()
        }

    /** 下載成品(帶權杖,只限站內路徑),交給 consumer 讀串流;失敗回 null。 */
    suspend fun <T> download(prefs: SecurePrefs, videoPath: String, consumer: suspend (InputStream) -> T?): T? =
        withContext(Dispatchers.IO) {
            val req = request(prefs, videoPath)?.get()?.build() ?: return@withContext null
            runCatching {
                downloadHttp.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val stream = resp.body?.byteStream() ?: return@use null
                    consumer(stream)
                }
            }.getOrNull()
        }
}
