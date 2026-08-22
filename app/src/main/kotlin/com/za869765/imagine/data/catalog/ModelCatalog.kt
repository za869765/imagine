package com.za869765.imagine.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// v1.8.0 模型目錄:對話 / 生圖 / 生影 三類,每筆帶價格 + 免費標記。
// OpenRouter 部分:打包一份快照在 assets/openrouter_catalog.json(2026-08-22 抓的),
// 使用者可在選模型的底部面板按「更新清單」重抓官方 API 存到 filesDir,之後以快取為準。
enum class ModelMode { CHAT, IMAGE, VIDEO }

// badge:free=免費 / limited_free=限時免費(Stealth 匿名預覽) / conditional_free=條件免費(:free 變體,共用池限流)
//       / variable=浮動計價(Auto Router) / paid=付費
@Serializable
data class CatalogModel(
    val id: String,
    val name: String = "",
    val vendor: String = "",
    val ctx: Long = 0,
    val maxOut: Long = 0,
    val inMod: String = "",
    val outMod: String = "",
    val inPerM: Double? = null,      // 對話:輸入 $/百萬 tokens
    val outPerM: Double? = null,     // 對話:輸出 $/百萬 tokens
    val imgIn: Double? = null,       // 對話附圖 $/張
    val unit: String = "",           // 生圖:image/token/megapixel;生影:second/token/megapixel_second
    val min: Double? = null,         // 生圖 $/張(最低檔) 或 $/百萬 tok;生影 $/秒(最低檔)
    val max: Double? = null,
    val refIn: Double? = null,       // 生圖參考圖輸入 $/張
    val note: String = "",
    val resolutions: List<String> = emptyList(),
    val aspects: List<String> = emptyList(),
    val durations: List<Int> = emptyList(),
    val nMax: Int = 1,
    val refMax: Int = 0,
    val audio: Boolean = false,
    val frameImages: Boolean = false,
    val badge: String = "paid",
    val created: Long = 0,
) {
    val isFreeish: Boolean get() = badge == "free" || badge == "limited_free" || badge == "conditional_free"
    val displayName: String get() = name.ifBlank { id }
}

@Serializable
data class OpenRouterCatalogData(
    val version: Int = 1,
    val fetchedAt: String = "",
    val source: String = "",
    val chat: List<CatalogModel> = emptyList(),
    val image: List<CatalogModel> = emptyList(),
    val video: List<CatalogModel> = emptyList(),
) {
    fun of(mode: ModelMode): List<CatalogModel> = when (mode) {
        ModelMode.CHAT -> chat
        ModelMode.IMAGE -> image
        ModelMode.VIDEO -> video
    }
}

fun badgeLabel(badge: String): String = when (badge) {
    "free" -> "免費"
    "limited_free" -> "限時免費"
    "conditional_free" -> "條件免費"
    "variable" -> "浮動計價"
    else -> "付費"
}

// 標記的補充說明(選單裡每列下方的小字)
fun badgeHint(badge: String): String = when (badge) {
    "limited_free" -> "匿名預覽期免費,隨時可能結束;輸入會交給供應商訓練"
    "conditional_free" -> ":free 變體,共用池限流(每分 20 次/每日 50 或 1000 次)"
    "variable" -> "由路由器挑模型,依實際命中的模型計價"
    else -> ""
}

private fun fmtUsd(d: Double?): String {
    if (d == null) return "?"
    if (d == 0.0) return "0"
    val s = if (d >= 1) String.format(Locale.US, "%.2f", d) else String.format(Locale.US, "%.4f", d)
    return s.trimEnd('0').trimEnd('.')
}

// 一行可讀的價格文字(選模型列 / 選單用)
fun CatalogModel.priceText(mode: ModelMode): String {
    val base = when (mode) {
        ModelMode.CHAT -> when (badge) {
            "free" -> "免費"
            "limited_free" -> "限時免費（Stealth 預覽）"
            "conditional_free" -> "條件免費（共用池限流）"
            "variable" -> "浮動（依實際路由）"
            else -> if (inPerM == null && outPerM == null) note.ifBlank { "付費" }
            else "輸入 $${fmtUsd(inPerM)}／輸出 $${fmtUsd(outPerM)}・每百萬 tokens"
        }
        ModelMode.IMAGE -> when (unit) {
            "image" -> "$${fmtUsd(min)}/張" + (if (max != null && min != null && max > min) " 起" else "")
            "token" -> "$${fmtUsd(min)}/百萬輸出 tokens"
            "megapixel" -> "$${fmtUsd(min)}/百萬像素"
            else -> if (min == null) "價格未標示" else "$${fmtUsd(min)}"
        }
        ModelMode.VIDEO -> when (unit) {
            "second" -> "$${fmtUsd(min)}/秒" + (if (max != null && min != null && max > min) " 起" else "")
            "token" -> "$${fmtUsd(min)}/百萬 video tokens"
            "megapixel_second" -> "$${fmtUsd(min)}/百萬像素·秒"
            else -> if (min == null) "價格未標示" else "$${fmtUsd(min)}"
        }
    }
    return if (mode != ModelMode.CHAT && note.isNotBlank()) "$base（$note）" else base
}

object OpenRouterCatalog {
    private const val ASSET_NAME = "openrouter_catalog.json"
    private const val CACHE_NAME = "openrouter_catalog_cache.json"
    private const val API = "https://openrouter.ai/api/v1"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile
    private var cache: OpenRouterCatalogData? = null

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun load(ctx: Context): OpenRouterCatalogData {
        cache?.let { return it }
        val fromCache = runCatching {
            val f = File(ctx.filesDir, CACHE_NAME)
            if (f.exists()) json.decodeFromString<OpenRouterCatalogData>(f.readText()) else null
        }.getOrNull()
        val data = fromCache ?: runCatching {
            val text = ctx.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            json.decodeFromString<OpenRouterCatalogData>(text)
        }.getOrDefault(OpenRouterCatalogData())
        cache = data
        return data
    }

    fun invalidate() { cache = null }

    fun models(ctx: Context, mode: ModelMode): List<CatalogModel> = load(ctx).of(mode)

    fun find(ctx: Context, mode: ModelMode, id: String): CatalogModel? =
        models(ctx, mode).firstOrNull { it.id == id }

    // 選單排序:免費類排最前(限時免費 → 免費 → 條件免費 → 浮動),付費依最低價由低到高,再依名稱
    fun sortedForPicker(list: List<CatalogModel>, mode: ModelMode): List<CatalogModel> {
        fun rank(b: String) = when (b) { "limited_free" -> 0; "free" -> 1; "conditional_free" -> 2; "variable" -> 3; else -> 4 }
        fun price(m: CatalogModel): Double = when (mode) {
            ModelMode.CHAT -> (m.inPerM ?: 0.0) + (m.outPerM ?: 0.0)
            else -> m.min ?: Double.MAX_VALUE
        }
        return list.sortedWith(compareBy<CatalogModel> { rank(it.badge) }.thenBy { price(it) }.thenBy { it.displayName })
    }

    // ── 重抓官方 API(對話 /models、生圖 /images/models + 各模型 endpoints、生影 /videos/models)──
    suspend fun refresh(ctx: Context, apiKey: String?): Result<OpenRouterCatalogData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val chat = parseChat(getJson("$API/models", apiKey))
                val imageList = getJson("$API/images/models", apiKey)
                val image = parseImages(imageList, apiKey)
                val video = parseVideos(getJson("$API/videos/models", apiKey))
                require(chat.isNotEmpty()) { "對話模型清單為空" }
                val data = OpenRouterCatalogData(
                    version = 1,
                    fetchedAt = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                    source = "openrouter.ai/api/v1 (app 內更新)",
                    chat = chat,
                    image = image,
                    video = video,
                )
                val f = File(ctx.filesDir, CACHE_NAME)
                val tmp = File(ctx.filesDir, "$CACHE_NAME.tmp")
                tmp.writeText(json.encodeToString(OpenRouterCatalogData.serializer(), data))
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
                cache = data
                data
            }
        }

    private fun getJson(url: String, apiKey: String?): JsonElement {
        val b = Request.Builder().url(url).header("Accept", "application/json")
        if (!apiKey.isNullOrBlank()) b.header("Authorization", "Bearer $apiKey")
        http.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} $url")
            val text = resp.body?.string() ?: error("空回應 $url")
            return json.parseToJsonElement(text)
        }
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    private fun JsonObject.lng(k: String): Long = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: 0L
    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
    private fun JsonObject.arr(k: String): JsonArray? = this[k] as? JsonArray
    private fun JsonArray?.strings(): List<String> = this?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

    private fun chatBadge(id: String, pin: Double?, pout: Double?): String = when {
        id.startsWith("stealth/") -> "limited_free"
        id.endsWith(":free") -> "conditional_free"
        pin == 0.0 && pout == 0.0 -> "free"
        pin == null || pin < 0 -> "variable"
        else -> "paid"
    }

    private fun parseChat(root: JsonElement): List<CatalogModel> {
        val data = root.jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val m = el as? JsonObject ?: return@mapNotNull null
            val id = m.str("id") ?: return@mapNotNull null
            val pricing = m.obj("pricing")
            val pin = pricing?.num("prompt")
            val pout = pricing?.num("completion")
            val arch = m.obj("architecture")
            CatalogModel(
                id = id,
                name = m.str("name") ?: id,
                vendor = id.substringBefore('/'),
                ctx = m.lng("context_length"),
                maxOut = m.obj("top_provider")?.lng("max_completion_tokens") ?: 0L,
                inMod = arch?.arr("input_modalities").strings().joinToString("+"),
                outMod = arch?.arr("output_modalities").strings().joinToString("+"),
                inPerM = pin?.takeIf { it >= 0 }?.let { it * 1_000_000 },
                outPerM = pout?.takeIf { it >= 0 }?.let { it * 1_000_000 },
                imgIn = pricing?.num("image")?.takeIf { it > 0 },
                badge = chatBadge(id, pin, pout),
                created = m.lng("created"),
            )
        }
    }

    // API 沒有標價的生圖模型(Krea 三款),用官網 2026-08-22 標價補
    private val IMAGE_WEB_PRICE = mapOf(
        "krea/krea-2-medium-turbo" to 0.015,
        "krea/krea-2-medium" to 0.03,
        "krea/krea-2-large" to 0.06,
    )

    private suspend fun parseImages(root: JsonElement, apiKey: String?): List<CatalogModel> = coroutineScope {
        val data = root.jsonObject["data"]?.jsonArray ?: return@coroutineScope emptyList()
        data.mapNotNull { el ->
            val m = el as? JsonObject ?: return@mapNotNull null
            val id = m.str("id") ?: return@mapNotNull null
            async(Dispatchers.IO) {
                val sp = m.obj("supported_parameters")
                val epPath = m.str("endpoints")
                val eps = runCatching {
                    if (epPath != null) getJson("https://openrouter.ai$epPath", apiKey).jsonObject.arr("endpoints") else null
                }.getOrNull()
                val perImage = ArrayList<Double>(); val perTok = ArrayList<Double>(); val perMp = ArrayList<Double>(); val refIn = ArrayList<Double>()
                eps?.forEach { e ->
                    (e as? JsonObject)?.arr("pricing")?.forEach { p ->
                        val po = p as? JsonObject ?: return@forEach
                        val c = po.num("cost_usd") ?: return@forEach
                        val billable = po.str("billable"); val unit = po.str("unit")
                        when {
                            billable == "output_image" && unit == "image" -> perImage.add(c)
                            billable == "output_image" && unit == "token" -> perTok.add(c)
                            unit == "megapixel" -> perMp.add(c)
                            (billable == "input_image" || billable == "input_reference") && unit == "image" -> refIn.add(c)
                        }
                    }
                }
                var unit = ""; var mn: Double? = null; var mx: Double? = null; var note = ""
                when {
                    perImage.isNotEmpty() -> { unit = "image"; mn = perImage.min(); mx = perImage.max() }
                    IMAGE_WEB_PRICE[id] != null -> { unit = "image"; mn = IMAGE_WEB_PRICE[id]; mx = mn; note = "網站標價" }
                    perTok.isNotEmpty() -> { unit = "token"; mn = perTok.min() * 1_000_000; mx = perTok.max() * 1_000_000 }
                    perMp.isNotEmpty() -> { unit = "megapixel"; mn = perMp.min(); mx = perMp.max() }
                }
                CatalogModel(
                    id = id,
                    name = m.str("name") ?: id,
                    vendor = id.substringBefore('/'),
                    inMod = m.obj("architecture")?.arr("input_modalities").strings().joinToString("+"),
                    unit = unit, min = mn, max = mx,
                    refIn = refIn.minOrNull(),
                    note = note,
                    resolutions = sp?.obj("resolution")?.arr("values").strings(),
                    aspects = sp?.obj("aspect_ratio")?.arr("values").strings(),
                    nMax = sp?.obj("n")?.num("max")?.toInt() ?: 1,
                    refMax = sp?.obj("input_references")?.num("max")?.toInt() ?: 0,
                    badge = "paid",
                    created = m.lng("created"),
                )
            }
        }.awaitAll()
    }

    private fun parseVideos(root: JsonElement): List<CatalogModel> {
        val data = root.jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val v = el as? JsonObject ?: return@mapNotNull null
            val id = v.str("id") ?: return@mapNotNull null
            val sk = v.obj("pricing_skus")
            val sec = HashMap<String, Double>(); val tok = HashMap<String, Double>(); val mp = HashMap<String, Double>()
            sk?.forEach { (k, value) ->
                val x = (value as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() } ?: return@forEach
                when {
                    k.startsWith("cents_per_second") || k.startsWith("cents_per_video_output_second") -> sec[k] = x / 100.0
                    k.contains("duration_seconds") -> sec[k] = x
                    k.startsWith("video_tokens") -> tok[k] = x
                    k.contains("megapixel") -> mp[k] = x / 100.0
                }
            }
            var unit = ""; var mn: Double? = null; var mx: Double? = null
            when {
                sec.isNotEmpty() -> { unit = "second"; mn = sec.values.min(); mx = sec.values.max() }
                tok.isNotEmpty() -> { unit = "token"; mn = tok.values.min() * 1_000_000; mx = tok.values.max() * 1_000_000 }
                mp.isNotEmpty() -> { unit = "megapixel_second"; mn = mp.values.min(); mx = mp.values.max() }
            }
            CatalogModel(
                id = id,
                name = v.str("name") ?: id,
                vendor = id.substringBefore('/'),
                unit = unit, min = mn, max = mx,
                audio = (v["generate_audio"] as? JsonPrimitive)?.booleanOrNull == true,
                resolutions = v.arr("supported_resolutions").strings(),
                aspects = v.arr("supported_aspect_ratios").strings(),
                durations = v.arr("supported_durations")?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull?.toInt() } ?: emptyList(),
                frameImages = v["supported_frame_images"].let { it != null && it !is kotlinx.serialization.json.JsonNull },
                badge = "paid",
                created = v.lng("created"),
            )
        }
    }
}

// xAI 固定清單(官方統一價:圖 $0.05/張、影片 $0.05/秒;對話模型價格 app 不硬寫,看後台)
object XaiCatalog {
    private val CHAT = listOf(
        CatalogModel(id = "grok-4-fast-non-reasoning", name = "Grok 4 Fast（非推理・快）", vendor = "xai", note = "付費・價格見 xAI 後台"),
        CatalogModel(id = "grok-4-fast", name = "Grok 4 Fast（推理）", vendor = "xai", note = "付費・價格見 xAI 後台"),
        CatalogModel(id = "grok-4", name = "Grok 4", vendor = "xai", note = "付費・價格見 xAI 後台"),
    )
    private val IMAGE_ASPECTS = listOf("16:9", "1:1", "9:16", "4:3", "3:4", "3:2", "2:3", "auto")
    private val IMAGE = listOf(
        CatalogModel(id = "grok-imagine-image", name = "Grok Imagine Image（快速）", vendor = "xai", unit = "image", min = 0.05, max = 0.05, resolutions = listOf("1k", "2k"), aspects = IMAGE_ASPECTS, nMax = 10),
        CatalogModel(id = "grok-imagine-image-quality", name = "Grok Imagine Image（高品質）", vendor = "xai", unit = "image", min = 0.05, max = 0.05, resolutions = listOf("1k", "2k"), aspects = IMAGE_ASPECTS, nMax = 10),
    )
    private val VIDEO = listOf(
        CatalogModel(id = "grok-imagine-video", name = "Grok Imagine Video", vendor = "xai", unit = "second", min = 0.05, max = 0.05, resolutions = listOf("480p", "720p"), aspects = listOf("16:9", "1:1", "9:16", "4:3", "3:4", "3:2", "2:3"), durations = (1..15).toList(), audio = true, frameImages = true),
        CatalogModel(id = "grok-imagine-video-1.5-preview", name = "Grok Imagine Video 1.5（preview）", vendor = "xai", unit = "second", min = 0.08, max = 0.08, note = "依官方文件", resolutions = listOf("480p", "720p"), aspects = listOf("16:9", "1:1", "9:16", "4:3", "3:4", "3:2", "2:3"), durations = (1..15).toList(), audio = true, frameImages = true),
    )

    fun models(mode: ModelMode): List<CatalogModel> = when (mode) {
        ModelMode.CHAT -> CHAT
        ModelMode.IMAGE -> IMAGE
        ModelMode.VIDEO -> VIDEO
    }
}
