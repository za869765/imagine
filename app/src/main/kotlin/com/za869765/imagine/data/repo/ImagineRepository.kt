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
    Unauthorized,        // 401
    RateLimited,         // 429
    ContentPolicy,       // policy violation
    Network,             // no connection / timeout
    Server,              // 5xx
    Unknown,
}

class ImagineRepository(private val api: XaiApi) {

    suspend fun generateImage(
        prompt: String,
        n: Int = 1,
        resolution: String? = null,
        aspectRatio: String? = null,
    ): ApiResult<List<String>> = safeCall {
        api.generateImage(
            ImageGenerationRequest(
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
        val kind = when (e.code()) {
            401 -> ErrorKind.Unauthorized
            429 -> ErrorKind.RateLimited
            in 400..499 -> ErrorKind.ContentPolicy
            in 500..599 -> ErrorKind.Server
            else -> ErrorKind.Unknown
        }
        ApiResult.Error(kind, e.message ?: "HTTP ${e.code()}")
    } catch (e: java.io.IOException) {
        ApiResult.Error(ErrorKind.Network, e.message ?: "Network error")
    } catch (e: Throwable) {
        ApiResult.Error(ErrorKind.Unknown, e.message ?: "Unknown error")
    }
}
