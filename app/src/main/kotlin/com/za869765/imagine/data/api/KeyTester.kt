package com.za869765.imagine.data.api

import com.za869765.imagine.data.prefs.ApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * KeyTester — 儲存 API Key 前「測試連線」(UI_REDESIGN_PLAN 6.1)。
 * 用最輕的唯讀端點打一次:xAI GET /v1/models、OpenRouter GET /api/v1/key。
 * 結果分三種文案:金鑰格式錯誤(本地判斷,不打網路)/ 金鑰無效(401/403)/ 無法連線(網路例外),不共用「設定失敗」。
 */
object KeyTester {
    sealed class Outcome(val message: String, val ok: Boolean) {
        object Valid : Outcome("連線成功，金鑰有效", true)
        object BadFormat : Outcome("金鑰格式錯誤", false)
        object Invalid : Outcome("金鑰無效（伺服器拒絕，401/403）", false)
        object Network : Outcome("無法連線，請檢查網路後再試", false)
        class Http(code: Int) : Outcome("伺服器回應 $code，稍後再試", false)
    }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun test(provider: ApiProvider, key: String): Outcome = withContext(Dispatchers.IO) {
        if (!key.startsWith(provider.keyPrefix, ignoreCase = true) || key.length <= 8 || key.any { it.isWhitespace() }) {
            return@withContext Outcome.BadFormat
        }
        val url = if (provider == ApiProvider.OPENROUTER) "https://openrouter.ai/api/v1/key" else "https://api.x.ai/v1/models"
        val req = Request.Builder().url(url).header("Authorization", "Bearer $key").get().build()
        try {
            http.newCall(req).execute().use { resp ->
                when {
                    resp.isSuccessful -> Outcome.Valid
                    resp.code == 401 || resp.code == 403 -> Outcome.Invalid
                    else -> Outcome.Http(resp.code)
                }
            }
        } catch (_: java.io.IOException) {
            Outcome.Network
        }
    }
}
