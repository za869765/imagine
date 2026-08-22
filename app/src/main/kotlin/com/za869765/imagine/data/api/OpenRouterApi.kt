package com.za869765.imagine.data.api

import com.za869765.imagine.data.api.dto.OrChatRequest
import com.za869765.imagine.data.api.dto.OrChatResponse
import com.za869765.imagine.data.api.dto.OrCredits
import com.za869765.imagine.data.api.dto.OrImageRequest
import com.za869765.imagine.data.api.dto.OrImageResponse
import com.za869765.imagine.data.api.dto.OrKeyInfo
import com.za869765.imagine.data.api.dto.OrVideoRequest
import com.za869765.imagine.data.api.dto.OrVideoStatus
import com.za869765.imagine.data.api.dto.OrVideoSubmit
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

// v1.8.0 OpenRouter REST(base https://openrouter.ai/api/v1/)
interface OpenRouterApi {

    @POST("chat/completions")
    suspend fun chat(@Body req: OrChatRequest): OrChatResponse

    @POST("images")
    suspend fun generateImage(@Body req: OrImageRequest): OrImageResponse

    @POST("videos")
    suspend fun generateVideo(@Body req: OrVideoRequest): OrVideoSubmit

    @GET("videos/{id}")
    suspend fun videoStatus(@Path("id") id: String): OrVideoStatus

    // 影片成品必須帶 Authorization 才抓得到 → 走同一個 client 串流下載
    @Streaming
    @GET("videos/{id}/content")
    suspend fun videoContent(@Path("id") id: String, @Query("index") index: Int = 0): ResponseBody

    @GET("credits")
    suspend fun credits(): OrCredits

    @GET("key")
    suspend fun keyInfo(): OrKeyInfo
}
