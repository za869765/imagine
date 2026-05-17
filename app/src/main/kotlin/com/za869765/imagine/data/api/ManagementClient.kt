package com.za869765.imagine.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

// Separate client from XaiClient because management uses a different host
// and a different bearer (the management key, not the Imagine API key).
object ManagementClient {
    private const val BASE_URL = "https://management-api.x.ai/"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun build(managementKey: String): ManagementApi {
        val log = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val auth = okhttp3.Interceptor { chain ->
            if (managementKey.isBlank()) throw IOException("Management Key 未設定")
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $managementKey")
                .build()
            chain.proceed(req)
        }
        val http = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(log)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(ManagementApi::class.java)
    }
}
