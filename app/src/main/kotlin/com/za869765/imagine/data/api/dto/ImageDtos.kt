package com.za869765.imagine.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImageGenerationRequest(
    val model: String = "grok-imagine-image-quality",
    val prompt: String,
    val n: Int = 1,
    @SerialName("aspect_ratio") val aspectRatio: String? = null,
    val resolution: String? = null,
    @SerialName("response_format") val responseFormat: String = "url",
)

// v1.7.3: xAI /images/edits 的正確形狀(2026-07-16 對線上 API 實測):
// 單圖 = image:{url,type} 單物件、多圖 = images:[{url,type}]。
// 舊的 image:[{...}] 會被 422 拒收(「image[0] 應為 string 不是 map」)。
@Serializable
data class ImageEditRequest(
    val model: String = "grok-imagine-image-quality",
    val prompt: String,
    val images: List<ImageInput>,
    @SerialName("response_format") val responseFormat: String = "url",
)

@Serializable
data class ImageEditSingleRequest(
    val model: String = "grok-imagine-image-quality",
    val prompt: String,
    val image: ImageInput,
    @SerialName("response_format") val responseFormat: String = "url",
)

@Serializable
data class ImageInput(
    val url: String,
    val type: String = "image_url",
)

@Serializable
data class ImageGenerationResponse(
    val created: Long? = null,
    val data: List<ImageData> = emptyList(),
)

@Serializable
data class ImageData(
    val url: String? = null,
    @SerialName("b64_json") val b64Json: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null,
)
