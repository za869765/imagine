package com.za869765.imagine.data.api

import com.za869765.imagine.data.prefs.SecurePrefs
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class AuthInterceptor(private val prefs: SecurePrefs) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = prefs.apiKey
            ?: throw IOException("API Key 未設定")
        val req = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        return chain.proceed(req)
    }
}
