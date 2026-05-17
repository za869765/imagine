package com.za869765.imagine.data.api

import com.za869765.imagine.data.api.dto.ImageEditRequest
import com.za869765.imagine.data.api.dto.ImageGenerationRequest
import com.za869765.imagine.data.api.dto.ImageGenerationResponse
import com.za869765.imagine.data.api.dto.VideoEditRequest
import com.za869765.imagine.data.api.dto.VideoExtensionRequest
import com.za869765.imagine.data.api.dto.VideoGenerationRequest
import com.za869765.imagine.data.api.dto.VideoGenerationResponse
import com.za869765.imagine.data.api.dto.VideoStatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface XaiApi {

    @POST("v1/images/generations")
    suspend fun generateImage(@Body req: ImageGenerationRequest): ImageGenerationResponse

    @POST("v1/images/edits")
    suspend fun editImage(@Body req: ImageEditRequest): ImageGenerationResponse

    @POST("v1/videos/generations")
    suspend fun generateVideo(@Body req: VideoGenerationRequest): VideoGenerationResponse

    @POST("v1/videos/edits")
    suspend fun editVideo(@Body req: VideoEditRequest): VideoGenerationResponse

    @POST("v1/videos/extensions")
    suspend fun extendVideo(@Body req: VideoExtensionRequest): VideoGenerationResponse

    @GET("v1/videos/{requestId}")
    suspend fun getVideoStatus(@Path("requestId") requestId: String): VideoStatusResponse
}
