package com.za869765.imagine.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoGenerationRequest(
    val model: String = "grok-imagine-video",
    val prompt: String,
    val duration: Int? = null,
    @SerialName("aspect_ratio") val aspectRatio: String? = null,
    val resolution: String? = null,
    val image: ImageInput? = null,
    @SerialName("reference_images") val referenceImages: List<ImageInput>? = null,
)

@Serializable
data class VideoEditRequest(
    val model: String = "grok-imagine-video",
    val prompt: String,
    val video: VideoInput,
)

@Serializable
data class VideoExtensionRequest(
    val model: String = "grok-imagine-video",
    val prompt: String,
    val video: VideoInput,
)

@Serializable
data class VideoInput(
    val url: String,
    val type: String = "video_url",
)

@Serializable
data class VideoGenerationResponse(
    @SerialName("request_id") val requestId: String,
)

@Serializable
data class VideoStatusResponse(
    val status: String,                 // "pending" / "done" / "expired" / "failed"
    val progress: Int? = null,
    val video: VideoResult? = null,
    val error: String? = null,
)

@Serializable
data class VideoResult(
    val url: String,
)
