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
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.h3.H3LaptopFile
import com.za869765.imagine.data.h3.H3Recipe
import com.za869765.imagine.data.h3.H3RecipeApply
import com.za869765.imagine.data.h3.PendingH3
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
    /** v3.4.9 測試片來源:""＝自動(桌機在線就桌機)、"desk"＝桌機、"local"＝筆電;換來源就整份重讀 */
    val laptopSource = MutableStateFlow("")
    private fun src(): String? = laptopSource.value.ifBlank { null }
    fun setLaptopSource(ctx: Context, s: String) {
        laptopSource.value = s
        reloadLaptop(ctx, full = true)
    }

    /** v3.3.7 傳過的檔名記在手機(重開 APP 還在):清單標「已傳過」,一鍵全部只抓沒傳過的 */
    val laptopFetched = MutableStateFlow<Set<String>>(emptySet())
    private var fetchedLoaded = false
    private fun fetchedPrefs(app: Context) = app.getSharedPreferences("laptop_fetched", Context.MODE_PRIVATE)
    fun initLaptop(ctx: Context) {
        if (fetchedLoaded) return
        fetchedLoaded = true
        laptopFetched.value = fetchedPrefs(ctx.applicationContext).getStringSet("names", emptySet()).orEmpty().toSet()
        backfillClipRecipes(ctx)
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

    /**
     * v3.5.0 釋放手機空間:刪掉手機裡「不在 keep 這批」的測試片。只刪手機上存的那份,桌機／筆電上的檔不動,
     * 刪掉的會變回「未傳」,之後可以再抓。回傳(刪了幾個, 釋放幾 bytes)。
     */
    fun purgeLaptopExcept(ctx: Context, keep: Set<String>): Pair<Int, Long> {
        val app = ctx.applicationContext
        val keepStems = keep.map { it.substringBeforeLast('.') }.toSet()
        val gone = mutableSetOf<String>()
        var n = 0
        var bytes = 0L
        File(app.filesDir, "media").listFiles()?.forEach { f ->
            val title = clipTitleOf(app, f.name) ?: return@forEach  // 只動測試片,自己生成的作品不碰
            if (title in keep || title in keepStems || title.substringBeforeLast('.') in keepStems) return@forEach
            val len = f.length()
            if (f.delete()) {
                n++
                bytes += len
                PromptIndex.remove(app, f.name)
                clipPrefs(app).edit().remove("t:${f.name}").remove("r:${f.name}").apply()
                gone += title
                gone += title.substringBeforeLast('.')
            }
        }
        if (gone.isNotEmpty()) setFetched(app) { s -> s.filterNot { it in gone || it.substringBeforeLast('.') in gone }.toSet() }
        tasks.update { l -> l.filterNot { it.status == Status.DONE && it.url.startsWith(LAPTOP_PREFIX) && it.url.removePrefix(LAPTOP_PREFIX).substringAfterLast('/') !in keep } }
        return n to bytes
    }

    /** v3.5.6 手機上的測試片:檔名 → 測試片標題、配方(存在手機,詳情頁用) */
    private fun clipPrefs(app: Context) = app.getSharedPreferences("test_clip_index", Context.MODE_PRIVATE)
    fun clipTitleOf(ctx: Context, displayName: String): String? =
        clipPrefs(ctx.applicationContext).getString("t:$displayName", null)
            ?: PromptIndex.get(ctx, displayName)?.takeIf { it.startsWith("筆電測試片：") }?.removePrefix("筆電測試片：")?.trim()
    fun clipRecipeOf(ctx: Context, displayName: String): H3Recipe? =
        clipPrefs(ctx.applicationContext).getString("r:$displayName", null)?.let { runCatching { H3Client.json.decodeFromString(H3Recipe.serializer(), it) }.getOrNull() }
    /** 配方的提示詞:多段每段標出來(存進 PromptIndex,讓所有作品搜尋得到) */
    fun recipeText(r: H3Recipe): String {
        val segs = r.beats.ifEmpty { listOf(r.prompt) }
        return if (segs.size == 1) segs[0] else segs.mapIndexed { i, s -> "【第 ${i + 1} 段・${r.seconds} 秒】\n$s" }.joinToString("\n\n")
    }
    val clipRecipeBusy = MutableStateFlow<Set<String>>(emptySet())
    /** 向測試片那台要這支片的配方,存起來;onDone(有沒有拿到, 訊息) 在主執行緒 */
    /** 這支片確定沒有配方(早期沒編號的片):詳情頁直接寫,不放按鈕 */
    fun clipHasNoRecipe(ctx: Context, displayName: String): Boolean {
        if (clipPrefs(ctx.applicationContext).getBoolean("n:$displayName", false)) return true
        val title = clipTitleOf(ctx, displayName) ?: return false
        val hit = laptopList.value?.firstOrNull { it.base.substringBeforeLast('.') == title }
        return hit != null && !hit.recipe
    }
    /** 原片是哪台做的(檔名第二段的第一個字:L 筆電、R 桌機、C Colab、G GPUtw) */
    fun machineOf(title: String): Char? = title.split('_').getOrNull(1)?.firstOrNull()?.uppercaseChar()
    fun reproduceRoute(title: String?): Pair<String, String> = when (title?.let { machineOf(it) }) {
        'L' -> "local" to ""
        'R' -> "desk" to ""
        'C' -> "desk" to "原片是 Colab 做的，換到桌機不會一模一樣；要重現請把機器改成超高畫質（Colab，會花 CU）"
        'G' -> "desk" to "原片是 GPUtw 做的，換到桌機不會一模一樣；要重現請把機器改成高畫質（GPUtw，會花台幣）"
        else -> "desk" to ""
    }

    /** 一次取配方:回 1＝拿到、0＝確定沒有、-1＝這次沒拿到(機器忙、版本舊、逾時),可以再試 */
    private suspend fun tryRecipeOnce(app: Context, prefs: SecurePrefs, displayName: String, clipName: String, source: String?): Pair<Int, String> {
        val created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, name = clipName, recipe = true, source = source))
        val runId = (created as? H3Client.Result.Ok)?.value?.runId ?: return -1 to ((created as? H3Client.Result.Err)?.message ?: "伺服器沒有回單號")
        try {
            for (i in 0 until 60) {
                delay(1500)
                val s = (H3Client.downloadRun(prefs, runId) as? H3Client.Result.Ok)?.value ?: continue
                if (s.status == "done") {
                    val r = s.recipe ?: return -1 to "那台的工人還沒更新到會給配方的版本"
                    clipPrefs(app).edit().putString("r:$displayName", H3Client.json.encodeToString(H3Recipe.serializer(), r)).remove("n:$displayName").apply()
                    PromptIndex.put(app, displayName, recipeText(r))
                    return 1 to "已讀到配方"
                }
                if (s.status == "failed" || s.status == "cancelled") {
                    val e = s.error.orEmpty()
                    return if ("沒有留下配方" in e) 0 to "這支片沒有配方（較早的片或不是用腳本送的）" else -1 to (e.take(120).ifBlank { "取配方失敗" })
                }
            }
            return -1 to "那台兩分鐘內沒有回應"
        } finally {
            H3Client.forget(prefs, runId)
        }
    }

    /** 取這支片的配方:先問目前的來源(自動＝桌機優先),沒拿到再問筆電;都沒拿到就隔一分鐘再試,最多三輪 */
    fun fetchClipRecipe(ctx: Context, displayName: String, clipName: String, onDone: ((Boolean, String) -> Unit)? = null) {
        val app = ctx.applicationContext
        if (displayName in clipRecipeBusy.value) return
        clipRecipeBusy.update { it + displayName }
        scope.launch {
            var ok = false
            var msg = "讀不到配方，稍後再試"
            try {
                val prefs = SecurePrefs.get(app)
                val sources = listOf(src(), "local", "desk").distinct()
                rounds@ for (round in 0 until 3) {
                    var definitiveNone = 0
                    for (s in sources) {
                        val (code, m) = tryRecipeOnce(app, prefs, displayName, clipName, s)
                        msg = m
                        if (code == 1) { ok = true; break@rounds }
                        if (code == 0) definitiveNone++
                    }
                    if (definitiveNone == sources.size) { clipPrefs(app).edit().putBoolean("n:$displayName", true).apply(); break }
                    if (round < 2) delay(60_000)
                }
            } catch (e: Throwable) { msg = "讀配方失敗：" + (e.message ?: "") }
            clipRecipeBusy.update { it - displayName }
            if (onDone != null) kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(ok, msg) }
        }
    }

    /** v3.5.7 手機上已經有、還沒有配方的測試片:背景一支一支補讀(每次開 APP 跑一輪) */
    private var backfillStarted = false
    fun backfillClipRecipes(ctx: Context) {
        if (backfillStarted) return
        backfillStarted = true
        val app = ctx.applicationContext
        scope.launch {
            val files = File(app.filesDir, "media").listFiles().orEmpty()
            for (f in files) {
                val title = clipTitleOf(app, f.name) ?: continue
                if (clipRecipeOf(app, f.name) != null || clipHasNoRecipe(app, f.name)) continue
                fetchClipRecipe(app, f.name, "$title.mp4")
                while (f.name in clipRecipeBusy.value) delay(2000)
            }
        }
    }
    /** 整組套用:參考圖抓到手機後寫進草稿,生影頁一打開就照配方設好(段數、每段原文、秒數、參考圖、白皙),機器先放桌機 */
    fun prepareRecipeApply(ctx: Context, r: H3Recipe, title: String?, onReady: (String?) -> Unit) {
        val app = ctx.applicationContext
        scope.launch {
            var err: String? = null
            val sources = mutableListOf<String>()
            try {
                val prefs = SecurePrefs.get(app)
                val dir = File(app.filesDir, "recipe_refs").apply { mkdirs() }
                for (name in r.refs) {
                    val dst = File(dir, name.substringAfterLast('/'))
                    if (!dst.exists() || dst.length() == 0L) {
                        val bytes = fetchSmall(prefs, "_recipes/refs/" + dst.name) ?: throw IllegalStateException("參考圖抓不回來（桌機或筆電要開著）")
                        dst.writeBytes(bytes)
                    }
                    sources += dst.toUri().toString()
                }
                val (route, note) = reproduceRoute(title)
                val a = H3RecipeApply(prompt = r.prompt, beats = r.beats, seconds = r.seconds, fair = r.fair, fairMale = r.fairMale, sources = sources, route = route, note = note)
                com.za869765.imagine.data.prefs.DraftStore.save(app, RECIPE_APPLY_DRAFT, H3Client.json.encodeToString(H3RecipeApply.serializer(), a))
            } catch (e: Throwable) { err = e.message ?: "準備失敗" }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onReady(err) }
        }
    }
    const val RECIPE_APPLY_DRAFT = "h3_recipe_apply"

    /** v3.5.1 正在把哪幾支測試片加進最近成品 */
    val laptopRecipeBusy = MutableStateFlow<Set<String>>(emptySet())

    /** 手機上這支測試片存在哪(所有作品裡的 file uri);沒抓過回 null */
    private fun phoneCopy(app: Context, base: String): String? {
        val stem = base.substringBeforeLast('.')
        return File(app.filesDir, "media").listFiles()?.firstOrNull { f ->
            val t = clipTitleOf(app, f.name)
            t != null && (t == stem || t == base)
        }?.toUri()?.toString()
    }

    /** 向測試片那台要一個小檔(配方的參考圖):回傳內容,失敗回 null;用完請伺服器刪暫存 */
    private suspend fun fetchSmall(prefs: SecurePrefs, name: String): ByteArray? {
        val created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, name = name, source = src()))
        val runId = (created as? H3Client.Result.Ok)?.value?.runId ?: return null
        try {
            for (i in 0 until 80) {
                delay(1500)
                val s = (H3Client.downloadRun(prefs, runId) as? H3Client.Result.Ok)?.value ?: continue
                if (s.status == "failed" || s.status == "cancelled") return null
                if (s.status == "done" && s.parts.isNotEmpty()) {
                    val out = java.io.ByteArrayOutputStream()
                    for (p in s.parts) {
                        val ok = H3Client.download(prefs, p) { input -> input.copyTo(out); true }
                        if (ok != true) return null
                    }
                    return out.toByteArray()
                }
            }
            return null
        } finally {
            H3Client.forget(prefs, runId)
        }
    }

    /**
     * v3.5.1 把一支有配方的測試片加進「最近成品」:之後就能比照自己生成的成品「再生成一次／調整後再生成／往後續做」。
     * 要先把這支片抓到手機(最近成品要顯示它)。配方與參考圖從測試片那台取回,參考圖存在手機。onDone 回一句結果(在主執行緒)。
     */
    fun addRecipeToResults(ctx: Context, f: H3LaptopFile, onDone: (String) -> Unit) {
        if (f.name in laptopRecipeBusy.value) return
        val app = ctx.applicationContext
        val prefs = SecurePrefs.get(app)
        laptopRecipeBusy.update { it + f.name }
        scope.launch {
            var msg = "取配方失敗，稍後再試"
            try {
                val saved = phoneCopy(app, f.base)
                if (saved == null) {
                    msg = "請先把這支片抓到手機，再加入最近成品"
                } else {
                    val created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, name = f.name, recipe = true, source = src()))
                    val runId = (created as? H3Client.Result.Ok)?.value?.runId
                    var rec: H3Recipe? = null
                    if (runId == null) {
                        msg = (created as? H3Client.Result.Err)?.message ?: msg
                    } else {
                        for (i in 0 until 60) {
                            delay(1500)
                            val s = (H3Client.downloadRun(prefs, runId) as? H3Client.Result.Ok)?.value ?: continue
                            if (s.status == "done") { rec = s.recipe; if (rec == null) msg = "那台的工人還沒更新到會給配方的版本，晚點再試"; break }
                            if (s.status == "failed" || s.status == "cancelled") { msg = "取配方失敗：" + (s.error?.take(120) ?: ""); break }
                        }
                        H3Client.forget(prefs, runId)
                    }
                    val r = rec
                    if (r != null) {
                        val dir = File(app.filesDir, "recipe_refs").apply { mkdirs() }
                        val sources = mutableListOf<String>()
                        for (name in r.refs) {
                            val dst = File(dir, name.substringAfterLast('/'))
                            if (!dst.exists() || dst.length() == 0L) {
                                val bytes = fetchSmall(prefs, "_recipes/refs/" + dst.name) ?: throw IllegalStateException("參考圖抓不回來")
                                dst.writeBytes(bytes)
                            }
                            sources += dst.toUri().toString()
                        }
                        val stem = f.base.substringBeforeLast('.')
                        val pending = PendingH3(
                            prompt = r.prompt, sources = sources, seconds = r.seconds, quality = r.quality, mode = r.mode, aspect = r.aspect,
                            // v3.5.2 舊片的配方可能是筆電／GPUtw／Colab 做的:再做一律先放桌機(主力、免費),其他機器由使用者自己改選
                            route = reproduceRoute(stem).first,
                            segments = r.segments, beats = r.beats, clientRequestId = UUID.randomUUID().toString(), estimate = null, seed = r.seed,
                            fair = r.fair, rawPrompt = r.rawPrompt, fairMale = r.fairMale,
                        )
                        H3Jobs.add(app, H3Jobs.Job(
                            runId = "clip_" + Integer.toHexString(stem.hashCode()), prompt = r.prompt, quality = r.quality, seconds = r.seconds * r.segments, estCu = 0.0,
                            createdAtMs = f.mtime, status = "done", savedUri = saved, kind = "video", meta = "測試片：$stem", keepInStudio = false, forgotten = true,
                            route = reproduceRoute(stem).first, seed = r.seed, finishedAtMs = f.mtime, sources = sources, repeatVideo = pending,
                        ))
                        msg = "已加入「最近成品」，可以再生成一次、調整後再生成、往後續做"
                    }
                }
            } catch (e: Throwable) {
                msg = "加入失敗：" + (e.message ?: "")
            }
            laptopRecipeBusy.update { it - f.name }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(msg) }
        }
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
            val created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, name = name, delete = true, source = src()))
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
                        setFetched(app) { it - name.substringAfterLast('/') }
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
                        // 檔案被搬進日期資料夾後路徑會變:同檔名的舊項目拿掉,免得重複
                        val kept = cached.filter { o -> fresh.none { it.base == o.base } }
                        laptopList.value = fresh + kept
                        laptopTotal.value = maxOf(total, fresh.size + kept.size)
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
        var created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, offset = offset, source = src()))
        var tries = 0
        while (tries < 2) {
            val err = created as? H3Client.Result.Err ?: break
            if (!err.uncertain || err.code != 0) break
            tries++
            delay(1500)
            created = H3Client.submitDownload(prefs, H3DownloadRequest(url = "", mode = "v1080", clientRequestId = UUID.randomUUID().toString(), browse = true, offset = offset, source = src()))
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
                if (n.substringAfterLast('/') in laptopFetched.value || tasks.value.any { it.url == u && (it.status == Status.RUNNING || it.status == Status.DONE) }) continue
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
            val created = H3Client.submitDownload(prefs, H3DownloadRequest(url = if (laptopName != null) "" else url, mode = mode, clientRequestId = id, browse = laptopName != null, name = laptopName.orEmpty(), source = if (laptopName != null) src() else null))
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
            if (url.startsWith(LAPTOP_PREFIX)) setFetched(app) { it + url.removePrefix(LAPTOP_PREFIX).substringAfterLast('/') }
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
        PromptIndex.put(app, dst.name, if (url.startsWith(LAPTOP_PREFIX)) "測試片：$title" else "下載：$title\n$url")
        if (url.startsWith(LAPTOP_PREFIX)) {
            clipPrefs(app).edit().putString("t:${dst.name}", title).apply()
            fetchClipRecipe(app, dst.name, url.removePrefix(LAPTOP_PREFIX))  // v3.5.6 有配方就順便存下來(沒有也沒關係)
        }
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
