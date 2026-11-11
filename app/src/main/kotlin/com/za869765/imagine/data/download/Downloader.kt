package com.za869765.imagine.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.za869765.imagine.data.storage.PromptIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * v3.1.9 下載分頁:手機內建 yt-dlp(youtubedl-android,含 Python 與 FFmpeg)。
 * 影片存進 filesDir/media(「所有作品」,一律 mp4 才認得是影片);聲音存到手機「下載/Imagine」。
 * 下載跑在 APP 層級的 scope,切分頁不會中斷;APP 被系統關掉才會停。清單只留在這次開啟期間。
 */
object Downloader {
    enum class Status { RUNNING, DONE, FAILED, CANCELLED }

    data class Task(
        val id: String,
        val url: String,
        val audio: Boolean,
        val title: String = "",
        val progress: Float = 0f,
        val etaSec: Long = -1,
        val status: Status = Status.RUNNING,
        val stage: String = "準備中",
        val error: String? = null,
        /** 影片:所有作品裡的 file uri;聲音:MediaStore content uri */
        val savedUri: String? = null,
    )

    val tasks = MutableStateFlow<List<Task>>(emptyList())
    val coreVersion = MutableStateFlow<String?>(null)
    val updating = MutableStateFlow(false)
    /** 從其他 APP 分享進來的網址,下載頁拿去預填 */
    val sharedUrl = MutableStateFlow<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initLock = Mutex()
    private var inited = false
    private const val SP = "downloader"
    private const val KEY_LAST_UPDATE = "last_core_update"
    private const val UPDATE_EVERY_MS = 3L * 24 * 3600 * 1000 // 網站常改版,3 天自動更新一次核心

    /** 第一次用才解壓 Python(約數秒);順便清掉上次沒做完的暫存,核心太舊就先更新 */
    private suspend fun ensureInit(ctx: Context) = initLock.withLock {
        if (inited) return@withLock
        val app = ctx.applicationContext
        File(app.cacheDir, "dl").deleteRecursively()
        YoutubeDL.init(app)
        FFmpeg.init(app)
        inited = true
        val sp = app.getSharedPreferences(SP, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - sp.getLong(KEY_LAST_UPDATE, 0) > UPDATE_EVERY_MS) {
            runCatching { doUpdate(app) }
        }
        coreVersion.value = YoutubeDL.version(app)
    }

    private fun doUpdate(app: Context): YoutubeDL.UpdateStatus? {
        updating.value = true
        try {
            val r = YoutubeDL.updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
            app.getSharedPreferences(SP, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            coreVersion.value = YoutubeDL.version(app)
            return r
        } finally {
            updating.value = false
        }
    }

    /** 下載頁打開時讀版本(會順便初始化) */
    fun warmUp(ctx: Context) {
        scope.launch { runCatching { ensureInit(ctx) } }
    }

    /** 手動更新下載核心;回傳給畫面顯示的結果文字 */
    suspend fun updateCore(ctx: Context): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (tasks.value.any { it.status == Status.RUNNING }) return@withContext "下載中不能更新，等做完再按"
        runCatching {
            ensureInit(ctx)
            when (doUpdate(ctx.applicationContext)) {
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "已經是最新版"
                else -> "已更新到 ${coreVersion.value ?: "最新版"}"
            }
        }.getOrElse { "更新失敗：${it.message?.take(120) ?: "連不上 GitHub"}" }
    }

    /** @param height 影片最高畫質(1080 / 720);audio=true 時不用 */
    fun start(ctx: Context, url: String, audio: Boolean, height: Int) {
        val app = ctx.applicationContext
        val id = UUID.randomUUID().toString()
        tasks.update { listOf(Task(id = id, url = url, audio = audio, title = url)) + it }
        scope.launch {
            val work = File(app.cacheDir, "dl/$id")
            try {
                ensureInit(app)
                if (isCancelled(id)) return@launch // 準備中就按了取消
                set(id) { it.copy(stage = "下載中") }
                work.mkdirs()
                val req = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("-o", work.absolutePath + "/%(title).120B.%(ext)s")
                    if (audio) {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        addOption("--audio-quality", "0")
                    } else {
                        // 優先 H.264+AAC 的 mp4:手機與「所有作品」播放器都順;沒有才用其他編碼再合成 mp4
                        addOption("-S", "vcodec:h264,res:$height,acodec:m4a")
                        addOption("--merge-output-format", "mp4")
                        addOption("--remux-video", "mp4")
                    }
                }
                YoutubeDL.execute(req, id) { p, eta, line ->
                    set(id) {
                        it.copy(
                            progress = p.coerceIn(0f, 100f),
                            etaSec = eta,
                            stage = when {
                                line.contains("[Merger]") || line.contains("[VideoRemuxer]") -> "合成中"
                                line.contains("[ExtractAudio]") -> "轉成 MP3"
                                else -> it.stage
                            },
                        )
                    }
                }
                if (isCancelled(id)) return@launch
                val out = work.listFiles()
                    ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
                    ?.maxByOrNull { it.length() }
                    ?: throw IllegalStateException("下載完成但找不到檔案")
                val title = out.nameWithoutExtension
                val saved = if (audio) saveAudio(app, out) else saveVideo(app, out, title, url)
                set(id) { it.copy(status = Status.DONE, progress = 100f, title = title, stage = "完成", savedUri = saved) }
            } catch (e: YoutubeDL.CanceledException) {
                set(id) { it.copy(status = Status.CANCELLED, stage = "已取消") }
            } catch (e: Throwable) {
                // 取消時 process 被殺,有時會以一般錯誤結束
                if (!isCancelled(id)) set(id) { it.copy(status = Status.FAILED, stage = "失敗", error = friendly(e)) }
            } finally {
                work.deleteRecursively()
            }
        }
    }

    private fun isCancelled(id: String) = tasks.value.firstOrNull { it.id == id }?.status == Status.CANCELLED

    fun cancel(id: String) {
        set(id) { it.copy(status = Status.CANCELLED, stage = "已取消") }
        runCatching { YoutubeDL.destroyProcessById(id) }
    }

    fun remove(id: String) {
        tasks.update { list -> list.filterNot { it.id == id && it.status != Status.RUNNING } }
    }

    private fun set(id: String, f: (Task) -> Task) {
        tasks.update { list -> list.map { if (it.id == id && it.status == Status.RUNNING) f(it) else it } } // 取消後不再被進度蓋掉
    }

    private fun saveVideo(app: Context, src: File, title: String, url: String): String {
        val dir = File(app.filesDir, "media").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dst = File(dir, "imagine_${ts}_dl.mp4")
        if (!src.renameTo(dst)) {
            src.copyTo(dst, overwrite = true)
        }
        // 所有作品點進去看得到來源
        PromptIndex.put(app, dst.name, "下載：$title\n$url")
        return dst.toUri().toString()
    }

    private fun saveAudio(app: Context, src: File): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IllegalStateException("存聲音需要 Android 10 以上")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, src.name)
            put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Imagine")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val cr = app.contentResolver
        val uri: Uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("無法寫入手機「下載」資料夾")
        try {
            cr.openOutputStream(uri)?.use { o -> src.inputStream().use { it.copyTo(o) } }
                ?: throw IllegalStateException("無法寫入手機「下載」資料夾")
            cr.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Throwable) {
            cr.delete(uri, null, null)
            throw e
        }
        return uri.toString()
    }

    private fun friendly(e: Throwable): String {
        val msg = e.message.orEmpty()
        val last = msg.lineSequence().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()
        return when {
            msg.contains("Unsupported URL", true) -> "不支援這個網址"
            msg.contains("Sign in", true) || msg.contains("login", true) || msg.contains("cookies", true) ->
                "這則內容要登入才能下載（下載分頁目前不支援登入）"
            msg.contains("Private", true) -> "這是私人內容，下載不到"
            msg.contains("429") -> "網站暫時擋太多次請求，過一陣子再試"
            msg.contains("Unable to resolve host", true) || msg.contains("Network is unreachable", true) -> "網路不通"
            else -> (last ?: msg).take(200).ifBlank { "下載失敗" } + "（可以先按「更新下載核心」再試）"
        }
    }
}
