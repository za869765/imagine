package com.za869765.imagine.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// v1.8.0 OpenRouter (https://openrouter.ai/api/v1) — 對話 / 生圖 / 生影 三條線的 DTO。
// 欄位全部給預設值:OpenRouter 各家上游回應形狀不一,寧可 null 也不要 SerializationException。

// ── usage(每次回應都帶 cost,美元)──
@Serializable
data class OrUsageInclude(val include: Boolean = true)

@Serializable
data class OrUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    val cost: Double? = null,
)

// ── chat ──
@Serializable
data class OrChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    // 要 OpenRouter 在回應 usage 內附 cost(不帶就沒有 cost 欄)
    val usage: OrUsageInclude? = OrUsageInclude(),
)

@Serializable
data class OrChatOutMessage(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null,
)

@Serializable
data class OrChatChoice(
    val message: OrChatOutMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class OrChatResponse(
    val id: String? = null,
    val model: String? = null,
    val provider: String? = null,
    val choices: List<OrChatChoice> = emptyList(),
    val usage: OrUsage? = null,
)

// ── images(POST /images,回 base64)──
@Serializable
data class OrImageUrl(val url: String)

@Serializable
data class OrImageRef(
    val type: String = "image_url",
    @SerialName("image_url") val imageUrl: OrImageUrl,
)

@Serializable
data class OrImageRequest(
    val model: String,
    val prompt: String,
    val n: Int? = null,
    @SerialName("aspect_ratio") val aspectRatio: String? = null,
    val resolution: String? = null,
    @SerialName("input_references") val inputReferences: List<OrImageRef>? = null,
)

@Serializable
data class OrImageData(
    @SerialName("b64_json") val b64Json: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val url: String? = null,
)

@Serializable
data class OrImageResponse(
    val created: Long? = null,
    val data: List<OrImageData> = emptyList(),
    val usage: OrUsage? = null,
)

// ── videos(POST /videos → 202 job;GET /videos/{id} 輪詢;GET /videos/{id}/content 下載)──
@Serializable
data class OrFrameImage(
    val type: String = "image_url",
    @SerialName("image_url") val imageUrl: OrImageUrl,
    @SerialName("frame_type") val frameType: String = "first_frame",
)

@Serializable
data class OrVideoRequest(
    val model: String,
    val prompt: String,
    val duration: Int? = null,
    val resolution: String? = null,
    @SerialName("aspect_ratio") val aspectRatio: String? = null,
    @SerialName("frame_images") val frameImages: List<OrFrameImage>? = null,
    @SerialName("input_references") val inputReferences: List<OrImageRef>? = null,
)

@Serializable
data class OrVideoSubmit(
    val id: String,
    @SerialName("polling_url") val pollingUrl: String? = null,
    val status: String? = null,
)

@Serializable
data class OrVideoStatus(
    val id: String? = null,
    val status: String = "pending",   // pending / in_progress / completed / failed
    @SerialName("unsigned_urls") val unsignedUrls: List<String> = emptyList(),
    // 官方文件:failed 時看 error;形狀可能是字串或物件 → JsonElement 自己拆
    val error: JsonElement? = null,
    val usage: OrUsage? = null,
) {
    fun errorText(): String? = when (val e = error) {
        null -> null
        is JsonPrimitive -> e.content.takeIf { it.isNotBlank() }
        is JsonObject -> (e["message"] as? JsonPrimitive)?.content ?: e.toString()
        else -> e.toString()
    }
}

// ── credits / key ──
@Serializable
data class OrCredits(val data: OrCreditsData? = null)

@Serializable
data class OrCreditsData(
    @SerialName("total_credits") val totalCredits: Double? = null,
    @SerialName("total_usage") val totalUsage: Double? = null,
) {
    val remaining: Double? get() = totalCredits?.let { it - (totalUsage ?: 0.0) }
}

@Serializable
data class OrKeyInfo(val data: OrKeyData? = null)

@Serializable
data class OrKeyData(
    val label: String? = null,
    @SerialName("is_free_tier") val isFreeTier: Boolean? = null,
    val usage: Double? = null,
    @SerialName("usage_daily") val usageDaily: Double? = null,
)
