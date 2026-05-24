package com.za869765.imagine.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.za869765.imagine.BuildConfig
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object XaiClient {
    private const val BASE_URL = "https://api.x.ai/"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    // v1.0.54 O8: cache OkHttpClient + Retrofit by API key hash。每進 screen 都
    // remember(prefs) { XaiClient.build(prefs) } 之前會 rebuild 整個 stack (含 connection
    // pool + thread pool)。AuthInterceptor 內部讀 prefs.apiKey 動態取，所以同一個 client
    // 可以一直用，除非 key 真的換了。
    @Volatile
    private var cachedApi: XaiApi? = null
    @Volatile
    private var cachedKeyHash: Int = 0

    fun build(prefs: SecurePrefs): XaiApi {
        val keyHash = (prefs.apiKey ?: "").hashCode()
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

    private fun buildNew(prefs: SecurePrefs): XaiApi {
        // BASIC level 印 method/URL/protocol，不含 headers（Authorization 不會洩漏），
        // 但 release 仍無須 log → 包 DEBUG 一律 NONE
        val log = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
        val http = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(prefs))
            .addInterceptor(log)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(XaiApi::class.java)
    }
}
