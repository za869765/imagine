package com.za869765.imagine.data.repo

import android.util.Base64
import com.za869765.imagine.data.api.OpenRouterApi
import com.za869765.imagine.data.api.dto.ChatMessage
import com.za869765.imagine.data.api.dto.OrChatRequest
import com.za869765.imagine.data.api.dto.OrCreditsData
import com.za869765.imagine.data.api.dto.OrFrameImage
import com.za869765.imagine.data.api.dto.OrImageRef
import com.za869765.imagine.data.api.dto.OrImageRequest
import com.za869765.imagine.data.api.dto.OrImageUrl
import com.za869765.imagine.data.api.dto.OrKeyData
import com.za869765.imagine.data.api.dto.OrVideoRequest
import com.za869765.imagine.data.api.dto.OrVideoStatus
import okhttp3.ResponseBody

/**
 * v1.8.0 OpenRouter 資料層 — 對話 / 生圖 / 生影 / 額度。
 * 回傳沿用 ApiResult / ErrorKind(UI 既有錯誤卡片、toast 不用改)。
 */
class OpenRouterRepository(private val api: OpenRouterApi) {

    data class ChatReply(
        val content: String,
        val cost: Double?,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val model: String?,
    )

    // 多輪對話(帶完整歷史)。有些推理模型 content 空白只剩 reasoning → 退回 reasoning 文字而非「空回應」。
    suspend fun chat(
        messages: List<ChatMessage>,
        model: String,
        temperature: Double? = null,
    ): ApiResult<ChatReply> = safeCall {
        val resp = api.chat(OrChatRequest(model = model, messages = messages, temperature = temperature))
        val msg = resp.choices.firstOrNull()?.message
        val text = msg?.content?.takeIf { it.isNotBlank() }
            ?: msg?.reasoning?.takeIf { it.isNotBlank() }
            ?: error("空回應")
        ChatReply(
            content = text,
            cost = resp.usage?.cost,
            promptTokens = resp.usage?.promptTokens,
            completionTokens = resp.usage?.completionTokens,
            model = resp.model,
        )
    }

    class ImageOut(val bytes: ByteArray?, val url: String?, val mediaType: String?)
    data class ImageBatch(val images: List<ImageOut>, val cost: Double?)

    suspend fun generateImage(
        model: String,
        prompt: String,
        n: Int = 1,
        aspectRatio: String? = null,
        resolution: String? = null,
        referenceUrls: List<String>? = null,
    ): ApiResult<ImageBatch> = safeCall {
        val resp = api.generateImage(
            OrImageRequest(
                model = model,
                prompt = prompt,
                n = n.takeIf { it > 1 },
                aspectRatio = aspectRatio,
                resolution = resolution,
                inputReferences = referenceUrls?.takeIf { it.isNotEmpty() }
                    ?.map { OrImageRef(imageUrl = OrImageUrl(it)) },
            ),
        )
        val outs = resp.data.mapNotNull { d ->
            val bytes = d.b64Json?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
            if (bytes == null && d.url.isNullOrBlank()) null
            else ImageOut(bytes, d.url, d.mediaType)
        }
        ImageBatch(outs, resp.usage?.cost)
    }

    // 送出生影任務 → 回 job id(之後交給 VideoPollWorker 輪詢 + 下載)
    suspend fun submitVideo(
        model: String,
        prompt: String,
        duration: Int? = null,
        resolution: String? = null,
        aspectRatio: String? = null,
        firstFrameUrl: String? = null,
        referenceUrls: List<String>? = null,
    ): ApiResult<String> = safeCall {
        api.generateVideo(
            OrVideoRequest(
                model = model,
                prompt = prompt,
                duration = duration,
                resolution = resolution,
                aspectRatio = aspectRatio,
                frameImages = firstFrameUrl?.let { listOf(OrFrameImage(imageUrl = OrImageUrl(it))) },
                inputReferences = referenceUrls?.takeIf { it.isNotEmpty() }
                    ?.map { OrImageRef(imageUrl = OrImageUrl(it)) },
            ),
        ).id
    }

    suspend fun pollVideo(id: String): ApiResult<OrVideoStatus> = safeCall { api.videoStatus(id) }

    // 成品串流(呼叫端負責 close body)
    suspend fun downloadVideo(id: String, index: Int = 0): ApiResult<ResponseBody> = safeCall {
        api.videoContent(id, index)
    }

    suspend fun credits(): ApiResult<OrCreditsData> = safeCall {
        api.credits().data ?: error("空回應")
    }

    suspend fun keyInfo(): ApiResult<OrKeyData> = safeCall {
        api.keyInfo().data ?: error("空回應")
    }

    // 與 ImagineRepository.safeCall 同一套狀態碼對應(那邊是 private inline,這裡自帶一份)
    private inline fun <T> safeCall(block: () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: retrofit2.HttpException) {
        val kind = when (e.code()) {
            400 -> ErrorKind.ContentPolicy
            401 -> ErrorKind.Unauthorized
            402 -> ErrorKind.PaymentRequired
            403 -> ErrorKind.Forbidden
            404 -> ErrorKind.NotFound
            429 -> ErrorKind.RateLimited
            in 400..499 -> ErrorKind.Unknown
            in 500..599 -> ErrorKind.Server
            else -> ErrorKind.Unknown
        }
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val extracted = body?.let { ImagineRepository.extractErrorMessage(it) }
        val msg = buildString {
            append("HTTP ${e.code()}")
            if (!extracted.isNullOrBlank()) append(" — ").append(extracted)
            else if (!body.isNullOrBlank()) append(" — ").append(body.trim().take(200))
        }
        ApiResult.Error(kind, msg)
    } catch (e: java.io.IOException) {
        ApiResult.Error(ErrorKind.Network, e.message ?: "Network error")
    } catch (e: kotlinx.serialization.SerializationException) {
        ApiResult.Error(ErrorKind.Unknown, "解析失敗：${e.message ?: "Serialization error"}")
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        ApiResult.Error(ErrorKind.Unknown, "${e::class.simpleName}: ${e.message ?: "Unknown error"}")
    }
}
