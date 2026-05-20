package com.za869765.imagine.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Download an APK from a GitHub Releases asset URL and hand it off to
 * Android's system PackageInstaller via FileProvider + ACTION_VIEW.
 *
 * Caller must keep the same SecurePrefs PAT used by [UpdateChecker] — the
 * `browser_download_url` of a private repo asset only resolves with a valid
 * `Authorization: Bearer <PAT>` header (GitHub returns 302 to a signed S3 URL).
 */
object Installer {
    enum class Stage { Idle, Downloading, Verifying, Launching, Error }

    data class Progress(
        val stage: Stage,
        val downloaded: Long = 0,
        val total: Long = 0,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(Progress(Stage.Idle))
    val state: StateFlow<Progress> = _state

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun downloadAndLaunch(
        ctx: Context,
        info: UpdateInfo,
        pat: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (pat.isBlank()) {
            _state.value = Progress(Stage.Error, message = "GitHub PAT 未設定")
            return@withContext Result.failure(IllegalStateException("PAT missing"))
        }
        val cacheRoot = File(ctx.cacheDir, "updates").apply { mkdirs() }
        // 清理舊 APK，避免 cache 堆爆
        cacheRoot.listFiles()?.forEach { runCatching { it.delete() } }
        val outFile = File(cacheRoot, info.apkName.ifBlank { "imagine-update.apk" })

        _state.value = Progress(Stage.Downloading, total = info.apkSize)
        try {
            val req = Request.Builder()
                .url(info.apkUrl)
                .header("Authorization", "Bearer $pat")
                .header("Accept", "application/octet-stream") // GitHub asset 必要：純文字 vs binary
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val msg = "HTTP ${resp.code} ${resp.message}"
                    _state.value = Progress(Stage.Error, message = msg)
                    return@withContext Result.failure(RuntimeException(msg))
                }
                val total = resp.body?.contentLength()?.takeIf { it > 0 } ?: info.apkSize
                val src = resp.body!!.byteStream()
                val sink = outFile.outputStream()
                val buf = ByteArray(64 * 1024)
                var downloaded = 0L
                src.use { input ->
                    sink.use { output ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            downloaded += n
                            _state.value = Progress(Stage.Downloading, downloaded, total)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            _state.value = Progress(Stage.Error, message = e.message ?: e::class.simpleName)
            return@withContext Result.failure(e)
        }

        _state.value = Progress(Stage.Verifying)
        if (outFile.length() == 0L) {
            _state.value = Progress(Stage.Error, message = "下載檔案為空")
            return@withContext Result.failure(RuntimeException("empty APK"))
        }

        _state.value = Progress(Stage.Launching)
        try {
            val uri: Uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.updater.fileprovider",
                outFile,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(intent)
            _state.value = Progress(Stage.Idle)
            Result.success(Unit)
        } catch (e: Throwable) {
            _state.value = Progress(Stage.Error, message = "啟動安裝失敗: ${e.message}")
            Result.failure(e)
        }
    }

    fun reset() {
        _state.value = Progress(Stage.Idle)
    }
}
