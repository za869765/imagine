package com.za869765.imagine.data.repo

import com.za869765.imagine.data.api.XaiApi
import com.za869765.imagine.data.api.dto.ImageEditRequest
import com.za869765.imagine.data.api.dto.ImageGenerationRequest
import com.za869765.imagine.data.api.dto.ImageInput
import com.za869765.imagine.data.api.dto.VideoEditRequest
import com.za869765.imagine.data.api.dto.VideoExtensionRequest
import com.za869765.imagine.data.api.dto.VideoGenerationRequest
import com.za869765.imagine.data.api.dto.VideoInput

sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class Error(val kind: ErrorKind, val message: String) : ApiResult<Nothing>()
}

enum class ErrorKind {
    Unauthorized,        // 401 — API Key 無效或過期
    PaymentRequired,     // 402 — 餘額不足
    Forbidden,           // 403 — API Key 無此功能權限 / 帳號被停用
    NotFound,            // 404 — 找不到資源
    ContentPolicy,       // 400 — 內容審核被拒
    RateLimited,         // 429 — 短時間請求太多
    Network,             // no connection / timeout
    Server,              // 5xx
    Unknown,
}

/** UI 用的簡短 tag — 三個 Screen 共用 */
fun ErrorKind.userFriendlyTag(): String = when (this) {
    ErrorKind.Unauthorized -> "401 Key 無效"
    ErrorKind.PaymentRequired -> "402 餘額不足"
    ErrorKind.Forbidden -> "403 權限不足"
    ErrorKind.NotFound -> "404 找不到資源"
    ErrorKind.ContentPolicy -> "400 被審核"
    ErrorKind.RateLimited -> "429 太頻繁"
    ErrorKind.Server -> "伺服器錯誤"
    ErrorKind.Network -> "網路錯誤"
    ErrorKind.Unknown -> "失敗"
}

class ImagineRepository(private val api: XaiApi) {

    suspend fun generateImage(
        prompt: String,
        n: Int = 1,
        resolution: String? = null,
        aspectRatio: String? = null,
        model: String = "grok-imagine-image-quality",
    ): ApiResult<List<String>> = safeCall {
        api.generateImage(
            ImageGenerationRequest(
                model = model,
                prompt = prompt,
                n = n,
                resolution = resolution,
                aspectRatio = aspectRatio,
            ),
        ).data.mapNotNull { it.url }
    }

    suspend fun editImage(
        prompt: String,
        imageUrls: List<String>,
    ): ApiResult<List<String>> = safeCall {
        api.editImage(
            ImageEditRequest(
                prompt = prompt,
                image = imageUrls.map { ImageInput(url = it) },
            ),
        ).data.mapNotNull { it.url }
    }

    suspend fun generateVideo(
        prompt: String,
        duration: Int,
        resolution: String? = null,
        aspectRatio: String? = null,
        startingImageUrl: String? = null,
        referenceImageUrls: List<String>? = null,
    ): ApiResult<String> = safeCall {
        api.generateVideo(
            VideoGenerationRequest(
                prompt = prompt,
                duration = duration,
                resolution = resolution,
                aspectRatio = aspectRatio,
                image = startingImageUrl?.let { ImageInput(it) },
                referenceImages = referenceImageUrls?.map { ImageInput(it) },
            ),
        ).requestId
    }

    suspend fun editVideo(prompt: String, videoUrl: String): ApiResult<String> = safeCall {
        api.editVideo(VideoEditRequest(prompt = prompt, video = VideoInput(videoUrl))).requestId
    }

    suspend fun extendVideo(prompt: String, videoUrl: String): ApiResult<String> = safeCall {
        api.extendVideo(VideoExtensionRequest(prompt = prompt, video = VideoInput(videoUrl))).requestId
    }

    suspend fun pollVideoStatus(requestId: String) = safeCall {
        api.getVideoStatus(requestId)
    }

    private inline fun <T> safeCall(block: () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: retrofit2.HttpException) {
        // 細分 4xx 狀態碼,避免「全 4xx 都算審核」誤導使用者
        val kind = when (e.code()) {
            400 -> ErrorKind.ContentPolicy
            401 -> ErrorKind.Unauthorized
            402 -> ErrorKind.PaymentRequired
            403 -> ErrorKind.Forbidden
            404 -> ErrorKind.NotFound
            429 -> ErrorKind.RateLimited
            in 400..499 -> ErrorKind.Unknown    // 其他罕見 4xx
            in 500..599 -> ErrorKind.Server
            else -> ErrorKind.Unknown
        }
        // Include the response body — Retrofit's e.message() only gives "HTTP 400 ..."
        // and hides xAI's actual error text. Without this, the user sees a useless toast.
        // v1.0.54 O6: parse JSON 取 error.message，原本整段 dump 給 user 看 raw JSON 不友善
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val extracted = body?.let { extractErrorMessage(it) }
        val msg = buildString {
            append("HTTP ${e.code()}")
            if (!extracted.isNullOrBlank()) {
                append(" — ").append(extracted)
            } else if (!body.isNullOrBlank()) {
                // parse 失敗 fallback 仍給原文，至少有資訊
                append(" — ").append(body.trim().take(200))
            }
        }
        ApiResult.Error(kind, msg)
    } catch (e: java.io.IOException) {
        ApiResult.Error(ErrorKind.Network, e.message ?: "Network error")
    } catch (e: kotlinx.serialization.SerializationException) {
        ApiResult.Error(ErrorKind.Unknown, "解析失敗：${e.message ?: "Serialization error"}")
    } catch (e: kotlinx.coroutines.CancellationException) {
        // 不可吞 — 否則切頁/鎖屏時 in-flight 任務會變僵屍 coroutine，
        // 上層 while-loop 還會繼續輪詢
        throw e
    } catch (e: Throwable) {
        ApiResult.Error(ErrorKind.Unknown, "${e::class.simpleName}: ${e.message ?: "Unknown error"}")
    }

    companion object {
        private val errJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

        // v1.0.54 O6: 從 xAI 回的 error JSON 取出 error.message。
        // 預期格式: {"error":{"code":"...","message":"..."}} 或 {"error":"..."}
        // 不是 JSON / parse 失敗時回 null，caller fallback 給原文
        internal fun extractErrorMessage(body: String): String? = runCatching {
            val element = errJson.parseToJsonElement(body)
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@runCatching null
            val errEl = obj["error"] ?: return@runCatching null
            when (errEl) {
                is kotlinx.serialization.json.JsonPrimitive -> errEl.contentOrNull
                is kotlinx.serialization.json.JsonObject -> {
                    val m = errEl["message"]
                    (m as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
                }
                else -> null
            }
        }.getOrNull()
    }
}
