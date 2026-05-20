package com.za869765.imagine.data.update

import com.za869765.imagine.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Check GitHub Releases for a newer version of this APK and return an UpdateInfo
 * if remote tag's versionCode > BuildConfig.VERSION_CODE.
 *
 * Repo (`za869765/imagine`) is private → uses fine-grained PAT from SecurePrefs,
 * scope = Contents:read. Empty PAT = skip the check (user must side-load manually).
 *
 * Release tag convention (workflow auto-publish): `v<versionName>+<versionCode>`,
 *   e.g. `v1.0.16+17`. versionCode is parsed from the suffix after `+`.
 */
object UpdateChecker {
    private const val OWNER = "za869765"
    private const val REPO = "imagine"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        // repo 已改 public (v1.0.29 起)，匿名 access 就能讀 release info + 下載 asset，
        // 不再需要使用者輸入 PAT。
        val req = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val release = json.decodeFromString<Release>(body)
                val remoteCode = parseVersionCode(release.tagName) ?: return@withContext null
                if (remoteCode <= BuildConfig.VERSION_CODE) return@withContext null
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext null
                UpdateInfo(
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    latestVersionCode = remoteCode,
                    latestVersionName = release.name.ifBlank { release.tagName },
                    tagName = release.tagName,
                    apkUrl = apkAsset.url,
                    apkName = apkAsset.name,
                    apkSize = apkAsset.size,
                    releaseNotes = release.body.orEmpty(),
                )
            }
        }.getOrNull()
    }

    // tag "v1.0.16+17" → 17;  fallback parse 末段純數字
    private fun parseVersionCode(tag: String): Int? {
        val plusIdx = tag.lastIndexOf('+')
        if (plusIdx >= 0 && plusIdx < tag.length - 1) {
            return tag.substring(plusIdx + 1).toIntOrNull()
        }
        // tag 不含 + 的話從 versionName parse 重組失敗 — 不更新比誤更新安全
        return null
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        val name: String = "",
        val body: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    // 對 private repo，下載要走 GitHub asset API endpoint (這裡的 `url` field)
    // 配 `Accept: application/octet-stream` 才會收到 302 → signed URL；
    // `browser_download_url` 是 web 用，直接 GET 對 private repo 會回 404 even with PAT.
    @Serializable
    private data class Asset(
        val name: String,
        val url: String,
        val size: Long = 0,
    )
}

data class UpdateInfo(
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val tagName: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val releaseNotes: String,
)
