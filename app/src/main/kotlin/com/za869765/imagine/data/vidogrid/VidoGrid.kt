package com.za869765.imagine.data.vidogrid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.data.prefs.DraftStore
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * v3.1.3 VidoGrid(第三方轉接站)生影:Grok Imagine Video 1.5、Veo 3.1／3.1 Fast。
 * 流程:POST multipart → 回 uuid → 每 6 秒 GET /history/{uuid}(status 2 完成、3 失敗、-2 審核擋下)→ generated_video[].video_url 存進作品。
 * 免費試用帳號會被拒(403 PREMIUM_PLAN_REQUIRED),要先儲值。key 只存加密 prefs。
 */
object VidoGrid {
    private const val BASE = "https://api.vidogrid.ai/uapi/v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).build()

    /** model:"grok-1.5-lite"、"grok-1.5"、"veo-3.1-fast"、"veo-3.1" */
    data class Spec(
        val model: String,
        val prompt: String,
        val resolution: String,
        val aspect: String,       // 9:16 / 16:9 / 1:1
        val seconds: Int,
        val imageMode: String = "frame",  // Veo:frame(首尾幀)/ingredient(素材)
        val muted: Boolean = false,
    )

    sealed class R<out T> {
        data class Ok<T>(val v: T) : R<T>()
        data class Err(val msg: String) : R<Nothing>()
    }

    private fun req(key: String, path: String) = Request.Builder().url(BASE + path).header("x-api-key", key).header("Accept", "application/json")

    private fun errText(code: Int, body: String): String {
        val d = runCatching { (json.parseToJsonElement(body) as JsonObject)["detail"] }.getOrNull()
        val ec = ((d as? JsonObject)?.get("error_code") as? JsonPrimitive)?.content
        val em = ((d as? JsonObject)?.get("error_message") as? JsonPrimitive)?.content ?: (d as? JsonPrimitive)?.content
        val hint = when (ec) {
            "PREMIUM_PLAN_REQUIRED" -> "免費試用帳號不能用 API，要先儲值"
            "NOT_ENOUGH_CREDIT", "NOT_ENOUGH_AND_LOCK_CREDIT" -> "點數不夠"
            "HIGH_TRAFFIC_ERROR", "MODEL_UNAVAILABLE" -> "VidoGrid 現在太忙，稍後再試"
            else -> null
        }
        return listOfNotNull("VidoGrid $code", ec, hint ?: em).joinToString("・").take(200)
    }

    suspend fun account(key: String): R<String> = withContext(Dispatchers.IO) {
        try {
            http.newCall(req(key, "/account").get().build()).execute().use { r ->
                val b = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@withContext R.Err(errText(r.code, b))
                val o = json.parseToJsonElement(b) as JsonObject
                val plan = (o["plan_id"] as? JsonPrimitive)?.content
                val c = o["user_credit"] as? JsonObject
                val av = (c?.get("available_credit") as? JsonPrimitive)?.content
                R.Ok("方案 ${if (plan == "FP0001") "免費試用（API 不能用）" else plan}・可用點數 $av")
            }
        } catch (e: IOException) { R.Err("連不到 VidoGrid") } catch (e: Exception) { R.Err("回應看不懂") }
    }

    /** 送出;images 是壓好的 JPEG。回傳 uuid 與預扣點數。 */
    suspend fun submit(key: String, s: Spec, images: List<ByteArray>): R<Pair<String, Int?>> = withContext(Dispatchers.IO) {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("prompt", s.prompt)
        val jpg = "image/jpeg".toMediaType()
        val path = if (s.model.startsWith("grok-")) {
            val lite = s.model == "grok-1.5-lite"  // Lite:最多 1 張(當首幀)、只能 6／10／15 秒
            b.addFormDataPart("resolution", s.resolution)
            b.addFormDataPart("aspect_ratio", when (s.aspect) { "9:16" -> "portrait"; "1:1" -> "square"; else -> "landscape" })
            b.addFormDataPart("duration", s.seconds.toString())
            b.addFormDataPart("mode", "custom")
            b.addFormDataPart("skip_audio", s.muted.toString())
            images.take(if (lite) 1 else 5).forEachIndexed { i, x -> b.addFormDataPart("files", "image${i + 1}.jpg", x.toRequestBody(jpg)) }
            if (lite) "/video-gen/grok-imagine-video-1.5-lite" else "/video-gen/grok-imagine-video-1.5"
        } else {
            b.addFormDataPart("model", s.model)
            b.addFormDataPart("resolution", s.resolution)
            b.addFormDataPart("aspect_ratio", if (s.aspect == "16:9") "16:9" else "9:16")
            b.addFormDataPart("duration", s.seconds.toString())
            b.addFormDataPart("mode_image", s.imageMode)
            images.take(if (s.imageMode == "ingredient") 3 else 2).forEachIndexed { i, x -> b.addFormDataPart("ref_images", "image${i + 1}.jpg", x.toRequestBody(jpg)) }
            "/video-gen/veo"
        }
        try {
            http.newCall(req(key, path).post(b.build()).build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@withContext R.Err(errText(r.code, body))
                val o = json.parseToJsonElement(body) as JsonObject
                val uuid = (o["uuid"] as? JsonPrimitive)?.content ?: return@withContext R.Err("VidoGrid 沒回傳編號，請到它的網站看是否已建立")
                R.Ok(uuid to (o["estimated_credit"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt())
            }
        } catch (e: IOException) { R.Err("送出時網路中斷（可能已建立，請到 VidoGrid 網站確認，不要馬上重送）") }
    }

    data class State(val status: Int, val pct: Int?, val error: String?, val videoUrl: String?, val usedCredit: Int?)

    suspend fun history(key: String, uuid: String): R<State> = withContext(Dispatchers.IO) {
        try {
            http.newCall(req(key, "/history/$uuid").get().build()).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) return@withContext R.Err(errText(r.code, body))
                val o = json.parseToJsonElement(body) as JsonObject
                fun str(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                val vids = o["generated_video"] as? JsonArray
                val url = vids?.firstNotNullOfOrNull { ((it as? JsonObject)?.get("video_url") as? JsonPrimitive)?.content?.takeIf { u -> u.startsWith("http") } }
                R.Ok(State(str("status")?.toIntOrNull() ?: 1, str("status_percentage")?.toDoubleOrNull()?.toInt(),
                    listOfNotNull(str("error_code"), str("error_message")).joinToString("・").ifBlank { null }, url, str("used_credit")?.toDoubleOrNull()?.toInt()))
            }
        } catch (e: IOException) { R.Err("連不到 VidoGrid") } catch (e: Exception) { R.Err("回應看不懂") }
    }
}

/** App 層的 VidoGrid 任務清單:切頁、關畫面都照樣輪詢、做好自動存進作品;未完成的重開 App 會接著查。 */
object VidoGridJobs {
    @Serializable
    data class Job(val uuid: String, val prompt: String, val label: String, val createdAt: Long, val credit: Int? = null,
                   val status: Int = 0, val pct: Int? = null, val error: String? = null, val savedUri: String? = null)

    private const val KEY = "vidogrid_jobs"
    private val json = Json { ignoreUnknownKeys = true }
    var jobs by mutableStateOf<List<Job>>(emptyList())
        private set
    private var loaded = false
    private val polling = HashSet<String>()

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        jobs = DraftStore.loadList(ctx, KEY).mapNotNull { runCatching { json.decodeFromString<Job>(it) }.getOrNull() }
        jobs.filter { it.savedUri == null && it.status in 0..2 }.forEach { poll(ctx, it.uuid) }
    }

    private fun update(ctx: Context, uuid: String, f: (Job) -> Job) {
        jobs = jobs.map { if (it.uuid == uuid) f(it) else it }
        DraftStore.saveList(ctx, KEY, jobs.take(30).map { json.encodeToString(Job.serializer(), it) })
    }

    fun add(ctx: Context, job: Job) {
        jobs = listOf(job) + jobs
        update(ctx, job.uuid) { it }
        poll(ctx, job.uuid)
    }

    fun remove(ctx: Context, uuid: String) {
        jobs = jobs.filter { it.uuid != uuid }
        DraftStore.saveList(ctx, KEY, jobs.map { json.encodeToString(Job.serializer(), it) })
    }

    private fun poll(ctx0: Context, uuid: String) {
        val ctx = ctx0.applicationContext
        if (!polling.add(uuid)) return
        ImagineApp.appScope.launch(Dispatchers.Main) {
            try {
                var misses = 0
                while (true) {
                    val key = SecurePrefs.get(ctx).vidogridKey ?: return@launch
                    when (val r = VidoGrid.history(key, uuid)) {
                        is VidoGrid.R.Ok -> {
                            misses = 0
                            val s = r.v
                            update(ctx, uuid) { it.copy(status = s.status, pct = s.pct, error = s.error, credit = s.usedCredit ?: it.credit) }
                            if (s.status == 3 || s.status == -2) return@launch
                            if (s.status == 2 && s.videoUrl != null) {
                                val j = jobs.firstOrNull { it.uuid == uuid } ?: return@launch
                                val uri = MediaSaver.saveVideoFromUrl(ctx, s.videoUrl, "[VidoGrid] " + j.prompt)
                                update(ctx, uuid) { it.copy(savedUri = uri, error = if (uri == null) "成品下載失敗，可到 VidoGrid 網站下載" else null) }
                                return@launch
                            }
                        }
                        is VidoGrid.R.Err -> if (++misses >= 20) { update(ctx, uuid) { it.copy(error = r.msg) }; return@launch }
                    }
                    delay(6_000)
                }
            } finally { polling.remove(uuid) }
        }
    }
}
