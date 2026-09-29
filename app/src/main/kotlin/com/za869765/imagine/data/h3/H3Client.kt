package com.za869765.imagine.data.h3

import com.za869765.imagine.BuildConfig
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
 */
object H3Client {
    const val DEFAULT_BASE_URL = "https://h3-studio.pages.dev"

    val userAgent: String get() = "imagine-app/${BuildConfig.VERSION_NAME}"

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val message: String, val code: Int = 0) : Result<Nothing>()
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
            // 最多 9 張 × 1.5MB 圖(base64 後 ~18MB)上傳
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    fun baseUrl(prefs: SecurePrefs): String = prefs.h3BaseUrl.trim().trimEnd('/')

    /** video_url 是相對路徑(/api/video/<id>)→ 補上 base。 */
    fun absoluteUrl(prefs: SecurePrefs, path: String): String =
        if (path.startsWith("http")) path else baseUrl(prefs) + (if (path.startsWith("/")) path else "/$path")

    /** ExoPlayer / 下載用的共用 header。 */
    fun authHeaders(prefs: SecurePrefs): Map<String, String> =
        mapOf("Authorization" to "Bearer ${prefs.h3Token.orEmpty()}")

    private fun request(prefs: SecurePrefs, path: String): Request.Builder =
        Request.Builder()
            .url(baseUrl(prefs) + path)
            .header("Authorization", "Bearer ${prefs.h3Token.orEmpty()}")
            .header("User-Agent", userAgent)

    private suspend inline fun <reified T> call(prefs: SecurePrefs, req: Request): Result<T> =
        withContext(Dispatchers.IO) {
            if (prefs.h3Token.isNullOrBlank()) return@withContext Result.Err("未設定 H3 Studio 權杖")
            try {
                http.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) return@use Result.Err(errorMessage(resp, body), resp.code)
                    Result.Ok(json.decodeFromString<T>(body))
                }
            } catch (e: IOException) {
                Result.Err("無法連線 H3 Studio，請檢查網路或網址")
            } catch (e: kotlinx.serialization.SerializationException) {
                Result.Err("H3 Studio 回應格式無法解析")
            } catch (e: IllegalArgumentException) {
                Result.Err("H3 Studio 網址格式錯誤")
            }
        }

    private fun errorMessage(resp: Response, body: String): String {
        val serverMsg = runCatching { json.decodeFromString<H3ErrorBody>(body).error }.getOrNull()
        val prefix = when (resp.code) {
            401 -> "權杖無效（401）"
            403 -> "被拒絕（403，可能是 User-Agent 或權限）"
            404 -> "找不到（404）"
            409 -> "預估超過目前 CU 餘額（409）"
            413 -> "圖片太大（413）"
            in 500..599 -> "H3 Studio 伺服器錯誤（${resp.code}）"
            else -> "H3 Studio 回應 ${resp.code}"
        }
        return if (serverMsg.isNullOrBlank()) prefix else "$prefix：${serverMsg.take(200)}"
    }

    /** 不消耗 CU 的估算(同時拿餘額)。seconds 4..15、quality normal|fast。 */
    suspend fun estimate(prefs: SecurePrefs, seconds: Int, quality: String): Result<H3Estimate> =
        runCatching { request(prefs, "/api/app/estimate?seconds=$seconds&quality=$quality").get().build() }
            .fold({ call<H3Estimate>(prefs, it) }, { Result.Err("H3 Studio 網址格式錯誤") })

    /** ⚠️ 會消耗 Colab CU — 只能在使用者於確認視窗勾選並按「確認開跑」後呼叫。 */
    suspend fun submit(prefs: SecurePrefs, body: H3JobRequest): Result<H3JobCreated> {
        require(body.confirm && body.estCu > 0) { "H3 送出前必須經過確認" }
        val payload = json.encodeToString(body).toRequestBody("application/json".toMediaType())
        return runCatching { request(prefs, "/api/app/jobs").post(payload).build() }
            .fold({ call<H3JobCreated>(prefs, it) }, { Result.Err("H3 Studio 網址格式錯誤") })
    }

    suspend fun job(prefs: SecurePrefs, runId: String): Result<H3JobStatus> =
        runCatching { request(prefs, "/api/app/jobs/$runId").get().build() }
            .fold({ call<H3JobStatus>(prefs, it) }, { Result.Err("H3 Studio 網址格式錯誤") })

    suspend fun cancel(prefs: SecurePrefs, runId: String): Result<Unit> {
        val req = runCatching {
            request(prefs, "/api/runs/$runId/cancel").post("{}".toRequestBody("application/json".toMediaType())).build()
        }.getOrElse { return Result.Err("H3 Studio 網址格式錯誤") }
        return when (val r = call<H3ErrorBody>(prefs, req)) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }
    }

    /** 下載成品(帶權杖),交給 consumer 讀串流;失敗回 null。 */
    suspend fun <T> download(prefs: SecurePrefs, videoPath: String, consumer: suspend (InputStream) -> T?): T? =
        withContext(Dispatchers.IO) {
            val req = runCatching {
                Request.Builder()
                    .url(absoluteUrl(prefs, videoPath))
                    .header("Authorization", "Bearer ${prefs.h3Token.orEmpty()}")
                    .header("User-Agent", userAgent)
                    .get()
                    .build()
            }.getOrNull() ?: return@withContext null
            val dl = http.newBuilder().readTimeout(300, TimeUnit.SECONDS).build()
            runCatching {
                dl.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val stream = resp.body?.byteStream() ?: return@use null
                    consumer(stream)
                }
            }.getOrNull()
        }
}
