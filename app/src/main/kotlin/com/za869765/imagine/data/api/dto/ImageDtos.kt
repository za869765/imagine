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

@Serializable
data class ImageEditRequest(
    val model: String = "grok-imagine-image-quality",
    val prompt: String,
    val image: List<ImageInput>,
    @SerialName("response_format") val responseFormat: String = "url",
)

@Serializable
data class ImageInput(
    val url: String,
    val type: String = "image_url",
)

@Serializable
data class ImageGenerationResponse(
    val created: Long,
    val data: List<ImageData>,
)

@Serializable
data class ImageData(
    val url: String? = null,
    @SerialName("b64_json") val b64Json: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null,
)
