package com.za869765.imagine.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3DownloadRequest
import com.za869765.imagine.data.h3.H3LaptopFile
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.PromptIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * v3.2.0 下載分頁:網址交給筆電工人用 yt-dlp 下載(手機不再內建下載核心,APK 回到約 5 MB)。
 * 筆電下載好切段放 Cloudflare R2 暫存 → 手機依序接回來拼好 → 請 Studio 整包刪掉(不經雲端硬碟)。
 * 影片存進 filesDir/media(「所有作品」,一律 mp4 才認得是影片);聲音存到手機「下載/Imagine」。
 * 輪詢跑在 APP 層級的 scope,切分頁不會中斷;APP 被系統關掉才會停。清單只留在這次開啟期間。
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
        val stage: String = "送出中",
        val error: String? = null,
        /** 影片:所有作品裡的 file uri;聲音:MediaStore content uri */
        val savedUri: String? = null,
        val runId: String? = null,
    )

    val tasks = MutableStateFlow<List<Task>>(emptyList())
    /** 從其他 APP 分享進來的網址,下載頁拿去預填 */
    val sharedUrl = MutableStateFlow<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** v3.3.3 筆電測試片:任務的 url 用這個前綴 + 檔名,兩個畫面各自認得自己的任務 */
    const val LAPTOP_PREFIX = "laptop:"
    fun laptopUrl(name: String) = LAPTOP_PREFIX + name
    /** 筆電測試資料夾的影片清單(新到舊);null＝還沒讀過。切分頁回來不用重讀 */
    val laptopList = MutableStateFlow<List<H3LaptopFile>?>(null)
    /** 資料夾裡總共幾個(清單一頁只給 40 個) */
    val laptopTotal = MutableStateFlow(0)

    /** v3.3.7 傳過的檔名記在手機(重開 APP 還在):清單標「已傳過」,一鍵全部只抓沒傳過的 */
    val laptopFetched = MutableStateFlow<Set<String>>(emptySet())
    private var fetchedLoaded = false
    private fun fetchedPrefs(app: Context) = app.getSharedPreferences("laptop_fetched", Context.MODE_PRIVATE)
    fun initLaptop(ctx: Context) {
        if (fetchedLoaded) return
        fetchedLoaded = true
        laptopFetched.value = fetchedPrefs(ctx.applicationContext).getStringSet("names", emptySet()).orEmpty().toSet()
        if (laptopList.value == null) {
            runCatching { H3Client.json.decodeFromString(ListSerializer(H3LaptopFile.serializer()), cacheFile(ctx.applicationContext).readText()) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }?.let { laptopList.value = it; laptopTotal.value = it.size }
        }
    }
    private fun setFetched(app: Context, f: (Set<String>) -> Set<String>) {
        initLaptop(app)
        laptopFetched.update(f)
        fetchedPrefs(app).edit().putStringSet("names", laptopFetched.value).apply()
    }

    /** 正在請筆電刪除的檔名 */
    val laptopDeleting = MutableStateFlow<Set<String>>(emptySet())
    /** 刪掉筆電測試資料夾裡的一個檔(釋出空間、清單不再出現);手機上已經存好的那份不受影響 */
    fun deleteLaptop(ctx: Context, name: String) {
        if (name in laptopDeleting.value) return
        val app = ctx.applicationContext
        val prefs = SecurePrefs.get(app)
        laptopDeleting.update { it + name }
        laptopError.value = null
        scope.launch {
            var msg: String? = "筆電兩分鐘內沒有回應，檔案可能還沒刪"
            val created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, name = name, delete = true))
            val runId = (created as? H3Client.Result.Ok)?.value?.runId
            if (runId == null) {
                msg = (created as? H3Client.Result.Err)?.message ?: "伺服器沒有回單號"
            } else {
                for (i in 0 until 60) {
                    delay(1500)
                    val s = (H3Client.downloadRun(prefs, runId) as? H3Client.Result.Ok)?.value ?: continue
                    if (s.status == "done" && s.deleted != null) {
                        laptopList.update { l -> l?.filterNot { it.name == name } }
                        laptopTotal.update { (it - 1).coerceAtLeast(0) }
                        saveCache(app)
                        setFetched(app) { it - name }
                        msg = null
                    } else if (s.status == "done") {
                        msg = "筆電工人版本還不會刪檔（手上的工作做完會自己更新），晚點再試"
                    } else if (s.status == "failed" || s.status == "cancelled") {
                        msg = "刪除失敗：" + (s.error?.take(160) ?: "")
                    } else continue
                    H3Client.forget(prefs, runId)
                    break
                }
            }
            if (msg != null) laptopError.value = msg
            laptopDeleting.update { it - name }
        }
    }

    /** v3.3.6 讀清單跑在 APP 層級(切分頁、離開畫面不會中斷,也不會留下沒收尾的單);畫面只看這兩個狀態 */
    val laptopLoading = MutableStateFlow(false)
    val laptopError = MutableStateFlow<String?>(null)
    /** v3.4.1 清單存在手機(含縮圖):開分頁立刻顯示上次的清單,再到筆電補最新的一頁;full＝整份重讀(筆電上刪過、改過名時用) */
    private fun cacheFile(app: Context) = File(app.filesDir, "laptop_list.json")
    private fun saveCache(app: Context) {
        runCatching { cacheFile(app).writeText(H3Client.json.encodeToString(ListSerializer(H3LaptopFile.serializer()), laptopList.value.orEmpty())) }
    }

    fun reloadLaptop(ctx: Context, full: Boolean = false) {
        if (laptopLoading.value) return
        laptopLoading.value = true
        laptopError.value = null
        val app = ctx.applicationContext
        scope.launch {
            laptopError.value = runCatching {
                val cached = laptopList.value.orEmpty()
                if (full || cached.isEmpty()) {
                    var acc = emptyList<H3LaptopFile>()
                    var err: String? = null
                    while (true) {
                        val (page, total, e) = fetchPage(app, acc.size)
                        if (e != null) { err = e; break }
                        acc = acc.filterNot { o -> page.any { it.name == o.name } } + page
                        laptopList.value = acc
                        laptopTotal.value = total
                        if (page.isEmpty() || acc.size >= total) break
                    }
                    if (acc.isNotEmpty()) saveCache(app)
                    err
                } else {
                    val (page, total, e) = fetchPage(app, 0)
                    if (e == null) {
                        val fresh = page.filter { n -> cached.none { it.name == n.name } }
                        laptopList.value = fresh + cached
                        laptopTotal.value = maxOf(total, fresh.size + cached.size)
                        saveCache(app)
                    }
                    e
                }
            }.getOrElse { it.message ?: "讀取失敗" }
            laptopLoading.value = false
        }
    }

    /** 向筆電要一頁清單(40 個);回(這一頁, 資料夾總數, 錯誤訊息) */
    private suspend fun fetchPage(ctx: Context, offset: Int): Triple<List<H3LaptopFile>, Int, String?> {
        val prefs = SecurePrefs.get(ctx.applicationContext)
        // 列清單可以放心重送:剛切回 APP 或剛換網路時,第一次連線常常失敗(送出用的連線不會自動重試),所以連不上就隔一下再試,最多三次
        var created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, offset = offset))
        var tries = 0
        while (tries < 2) {
            val err = created as? H3Client.Result.Err ?: break
            if (!err.uncertain || err.code != 0) break
            tries++
            delay(1500)
            created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, offset = offset))
        }
        val made = created
        val runId = when (made) {
            is H3Client.Result.Ok -> made.value.runId
            is H3Client.Result.Err -> return Triple(emptyList(), 0, made.message)
        } ?: return Triple(emptyList(), 0, "伺服器沒有回單號")
        for (i in 0 until 120) {
            delay(1000)
            val s = (H3Client.downloadRun(prefs, runId) as? H3Client.Result.Ok)?.value ?: continue
            if (s.status == "done") {
                H3Client.forget(prefs, runId)
                val page = s.files.orEmpty()
                return Triple(page, s.total ?: (offset + page.size), null)
            }
            if (s.status == "failed" || s.status == "cancelled") {
                H3Client.forget(prefs, runId)
                return Triple(emptyList(), 0, s.error?.take(200) ?: "筆電沒有回清單")
            }
        }
        H3Client.cancel(prefs, runId)
        return Triple(emptyList(), 0, "筆電兩分鐘內沒有回應，確認筆電開著、工人在跑後再試")
    }

    /** v3.3.5 一次抓好幾個筆電測試檔:一個接一個(前一個結束才送下一個),免得同時上傳把筆電網路塞住 */
    fun startLaptopBatch(ctx: Context, names: List<String>) {
        scope.launch {
            for (n in names) {
                val u = laptopUrl(n)
                if (n in laptopFetched.value || tasks.value.any { it.url == u && (it.status == Status.RUNNING || it.status == Status.DONE) }) continue
                tasks.update { list -> list.filterNot { it.url == u } }
                start(ctx, u, audio = false, height = 1080, laptopName = n)
                while (tasks.value.any { it.url == u && it.status == Status.RUNNING }) delay(1500)
            }
        }
    }

    /** @param height 影片最高畫質(1080 / 720);audio=true 時不用 */
    fun start(ctx: Context, url: String, audio: Boolean, height: Int, laptopName: String? = null) {
        val app = ctx.applicationContext
        val prefs = SecurePrefs.get(app)
        val id = UUID.randomUUID().toString()
        tasks.update { listOf(Task(id = id, url = url, audio = audio, title = laptopName ?: url)) + it }
        scope.launch {
            val mode = if (audio) "audio" else if (height == 720) "v720" else "v1080"
            val created = H3Client.submitDownload(prefs, H3DownloadRequest(url = if (laptopName != null) "" else url, mode = mode, clientRequestId = id, browse = laptopName != null, name = laptopName.orEmpty()))
            val runId = when (created) {
                is H3Client.Result.Ok -> created.value.runId
                is H3Client.Result.Err -> return@launch fail(id, created.message)
            } ?: return@launch fail(id, "伺服器沒有回單號")
            set(id) { it.copy(runId = runId, stage = "等筆電接手") }
            var misses = 0
            while (true) {
                delay(3000)
                if (isCancelled(id)) return@launch
                val s = when (val r = H3Client.downloadRun(prefs, runId)) {
                    is H3Client.Result.Ok -> r.value.also { misses = 0 }
                    is H3Client.Result.Err -> {
                        if (r.code == 404 || ++misses > 40) return@launch fail(id, r.message)
                        continue
                    }
                }
                when (s.status) {
                    "done" -> {
                        receive(app, prefs, id, runId, url, audio, s.title ?: "download", s.ext ?: if (audio) ".mp3" else ".mp4", s.parts)
                        return@launch
                    }
                    "failed" -> {
                        H3Client.forget(prefs, runId)
                        return@launch fail(id, friendly(s.error.orEmpty()))
                    }
                    "cancelled" -> {
                        H3Client.forget(prefs, runId)
                        return@launch set(id) { it.copy(status = Status.CANCELLED, stage = "已取消") }
                    }
                    "running" -> set(id) {
                        it.copy(
                            stage = s.phase ?: "筆電下載中",
                            progress = (s.pct ?: 0.0).toFloat().coerceIn(0f, 100f),
                            etaSec = s.etaSec ?: -1,
                            title = s.title ?: it.title,
                        )
                    }
                    else -> set(id) { it.copy(stage = "等筆電接手") }
                }
            }
        }
    }

    /** 把筆電切好的幾段依序接回來拼成一個檔,存好後請 Studio 刪掉暫存 */
    private suspend fun receive(app: Context, prefs: SecurePrefs, id: String, runId: String, url: String, audio: Boolean,
                                title: String, ext: String, parts: List<String>) {
        val tmp = File(app.cacheDir, "dl_$id$ext")
        try {
            if (parts.isEmpty()) throw IllegalStateException("筆電說下載好了，但沒有檔案")
            tmp.delete()
            for ((i, p) in parts.withIndex()) {
                if (isCancelled(id)) return
                set(id) { it.copy(stage = "傳回手機", progress = 100f * i / parts.size, etaSec = -1, title = title) }
                val ok = H3Client.download(prefs, p) { input -> java.io.FileOutputStream(tmp, i > 0).use { input.copyTo(it) }; true }
                if (ok != true) throw IllegalStateException("從伺服器接回第 ${i + 1} 段失敗，再試一次")
            }
            if (isCancelled(id)) return
            val saved = if (audio) saveAudio(app, tmp, title + ext) else saveVideo(app, tmp, title, url, ext)
            set(id) { it.copy(status = Status.DONE, progress = 100f, title = title, stage = "完成", savedUri = saved) }
            if (url.startsWith(LAPTOP_PREFIX)) setFetched(app) { it + url.removePrefix(LAPTOP_PREFIX) }
            H3Client.forget(prefs, runId)
        } catch (e: Throwable) {
            fail(id, e.message ?: "存檔失敗")
        } finally {
            tmp.delete()
        }
    }

    private fun fail(id: String, msg: String) = set(id) { it.copy(status = Status.FAILED, stage = "失敗", error = msg) }

    private fun isCancelled(id: String) = tasks.value.firstOrNull { it.id == id }?.status == Status.CANCELLED

    fun cancel(ctx: Context, id: String) {
        val runId = tasks.value.firstOrNull { it.id == id }?.runId
        set(id) { it.copy(status = Status.CANCELLED, stage = "已取消") }
        if (runId != null) {
            val prefs = SecurePrefs.get(ctx.applicationContext)
            scope.launch {
                H3Client.cancel(prefs, runId)
                // 筆電收到取消要幾秒才會結束,之後再把暫存整包刪掉
                repeat(20) {
                    delay(3000)
                    if (H3Client.forget(prefs, runId)) return@launch
                }
            }
        }
    }

    fun remove(id: String) {
        tasks.update { list -> list.filterNot { it.id == id && it.status != Status.RUNNING } }
    }

    private fun set(id: String, f: (Task) -> Task) {
        tasks.update { list -> list.map { if (it.id == id && it.status == Status.RUNNING) f(it) else it } } // 取消後不再被進度蓋掉
    }

    private fun saveVideo(app: Context, src: File, title: String, url: String, ext: String = ".mp4"): String {
        val dir = File(app.filesDir, "media").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // 圖片(筆電測試片分頁才會有)照原本的副檔名存,所有作品才認得是圖;其他一律 mp4
        val keep = ext.lowercase().takeIf { it in listOf(".png", ".jpg", ".jpeg", ".webp") } ?: ".mp4"
        val dst = File(dir, "imagine_${ts}_dl$keep")
        if (!src.renameTo(dst)) {
            src.copyTo(dst, overwrite = true)
        }
        // 所有作品點進去看得到來源
        PromptIndex.put(app, dst.name, if (url.startsWith(LAPTOP_PREFIX)) "筆電測試片：$title" else "下載：$title\n$url")
        return dst.toUri().toString()
    }

    private fun saveAudio(app: Context, src: File, name: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IllegalStateException("存聲音需要 Android 10 以上")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
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

    /** 筆電 yt-dlp 的錯誤訊息轉成看得懂的話 */
    private fun friendly(msg: String): String {
        val last = msg.substringAfter("ERROR:").trim()
        return when {
            msg.contains("Unsupported URL", true) -> "不支援這個網址"
            msg.contains("Sign in", true) || msg.contains("login", true) || msg.contains("cookies", true) ->
                "這則內容要登入才能下載（目前不支援登入）"
            msg.contains("Private", true) -> "這是私人內容，下載不到"
            msg.contains("429") -> "網站暫時擋太多次請求，過一陣子再試"
            msg.contains("Unable to resolve host", true) || msg.contains("getaddrinfo", true) -> "筆電網路不通"
            else -> last.take(200).ifBlank { "下載失敗" }
        }
    }
}
