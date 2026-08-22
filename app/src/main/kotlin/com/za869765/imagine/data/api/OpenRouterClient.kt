package com.za869765.imagine.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.za869765.imagine.BuildConfig
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

// v1.8.0: OpenRouter client — 與 XaiClient 同一套(key hash 快取 + 動態讀 prefs),
// 但 base URL / key / 附加 header 都不同,分開一個 object 避免互相汙染單槽快取。
object OpenRouterClient {
    const val BASE_URL = "https://openrouter.ai/api/v1/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile
    private var cachedApi: OpenRouterApi? = null
    @Volatile
    private var cachedKeyHash: Int = 0

    fun build(prefs: SecurePrefs): OpenRouterApi {
        val keyHash = (prefs.openRouterKey ?: "").hashCode()
        val current = cachedApi
        if (current != null && cachedKeyHash == keyHash) return current
        synchronized(this) {
            val recheck = cachedApi
            if (recheck != null && cachedKeyHash == keyHash) return recheck
            val api = buildNew(prefs)
            cachedApi = api
            cachedKeyHash = keyHash
            return api
        }
    }

    private class OrAuthInterceptor(private val prefs: SecurePrefs) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val key = prefs.openRouterKey ?: throw IOException("OpenRouter API Key 未設定")
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                // OpenRouter 建議的 app 歸屬 header(排行榜用,非必要)
                .addHeader("HTTP-Referer", "https://github.com/za869765/imagine")
                .addHeader("X-Title", "Imagine")
                .build()
            return chain.proceed(req)
        }
    }

    private fun buildNew(prefs: SecurePrefs): OpenRouterApi {
        val log = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
        // 生圖(同步回 base64)可能跑到 1~2 分鐘 → read timeout 拉到 180s
        val http = OkHttpClient.Builder()
            .addInterceptor(OrAuthInterceptor(prefs))
            .addInterceptor(log)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(OpenRouterApi::class.java)
    }
}
