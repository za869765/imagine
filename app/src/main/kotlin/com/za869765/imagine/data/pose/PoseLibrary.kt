package com.za869765.imagine.data.pose

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v2.1.6 姿勢參考庫:anhao/ai-pose-gallery-zh(1439 張人偶線稿姿勢圖,4 體系、5 視角)。
 * 圖片與資料著作權歸原作者 ギア(@giaaibusi),不得商用 → APP 不打包,開頁時從 GitHub(jsDelivr)載入、本機快取 JSON。
 * 名稱為簡體,用 [S2T] 對照表轉繁體(Windows StrConv 產生＋人工修正 后→後、墙→牆)。
 */
object PoseLibrary {
    private const val BASE = "https://cdn.jsdelivr.net/gh/anhao/ai-pose-gallery-zh@main/"
    private const val BASE_RAW = "https://raw.githubusercontent.com/anhao/ai-pose-gallery-zh/main/"
    private const val CACHE = "pose_zh.json"
    const val ADULT_SYSTEM = "雙人纏綿"

    @Serializable
    data class Raw(
        val id: String,
        @SerialName("体系") val system: String = "",
        @SerialName("姿势编号") val no: String = "",
        @SerialName("分类") val category: String = "",
        @SerialName("姿势名称") val name: String = "",
        @SerialName("角度") val angle: String = "",
        @SerialName("缩略图") val thumb: String = "",
        @SerialName("原图") val image: String = "",
    )

    data class Shot(val id: String, val angle: String, val thumbUrl: String, val imageUrl: String)

    /** 一個姿勢(同體系＋同編號),含多個視角。 */
    data class Pose(val key: String, val system: String, val no: String, val category: String, val name: String, val shots: List<Shot>) {
        val cover: Shot get() = shots.firstOrNull { it.angle == "正面" } ?: shots.first()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }
    @Volatile private var cached: List<Pose>? = null

    fun url(path: String): String = BASE + path

    /** 先用記憶體 → 本機快取檔 → 網路(jsDelivr,失敗改 raw.githubusercontent)。forceRefresh 強制重抓。 */
    suspend fun load(ctx: Context, forceRefresh: Boolean = false): Result<List<Pose>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!forceRefresh) cached?.let { return@runCatching it }
            val f = File(ctx.filesDir, CACHE)
            val text = if (!forceRefresh && f.exists() && f.length() > 1000) f.readText() else {
                val t = fetch(BASE + "data/pose-zh.json") ?: fetch(BASE_RAW + "data/pose-zh.json") ?: error("姿勢庫下載失敗，請檢查網路")
                f.writeText(t)
                t
            }
            val raws = json.decodeFromString<List<Raw>>(text)
            val poses = raws.groupBy { it.system + "|" + it.no }.map { (key, list) ->
                val r = list.first()
                Pose(
                    key = key,
                    system = S2T.conv(r.system),
                    no = r.no,
                    category = S2T.conv(r.category),
                    name = S2T.conv(r.name),
                    shots = list.map { Shot(it.id, S2T.conv(it.angle), url(it.thumb), url(it.image)) }
                        .sortedBy { ANGLE_ORDER.indexOf(it.angle).let { i -> if (i < 0) 99 else i } },
                )
            }
            cached = poses
            poses
        }
    }

    private val ANGLE_ORDER = listOf("正面", "斜側", "側面", "俯視", "仰視")

    private fun fetch(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", "imagine-app").build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    /** 下載原圖(PNG)位元組,給「當參考圖」存進素材。 */
    suspend fun downloadImage(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "imagine-app").build()).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        }.getOrNull()
    }
}

/** 姿勢庫用到的簡→繁字表(326 字中 100 字不同;StrConv 產生,后/墙/・ 人工修正)。 */
object S2T {
    private val MAP: Map<Char, Char> by lazy {
        val pairs = "业業丝絲两兩举舉亲親从從传傳体體侧側倾傾内內准準动動势勢单單卧臥压壓双雙发發叠疊呜嗚嘘噓团團图圖墙牆头頭夹夾娇嬌对對将將带帶庞龐开開弯彎强強悬懸懒懶扬揚抚撫拢攏拥擁挂掛挤擠摆擺撑撐条條构構标標桥橋横橫沟溝爱愛猫貓环環盖蓋盘盤着著礼禮窥窺竖豎紧緊约約线線绕繞绵綿缘緣缝縫缠纏翘翹耸聳胆膽脑腦脚腳脸臉视視触觸语語请請调調财財贴貼转轉边邊过過钩鉤锁鎖错錯镜鏡间間雏雛顶頂顾顧颈頸颊頰风風飘飄飞飛骑騎鸭鴨后後"
        val m = HashMap<Char, Char>()
        var i = 0
        while (i + 1 < pairs.length) { m[pairs[i]] = pairs[i + 1]; i += 2 }
        m
    }

    fun conv(s: String): String = buildString(s.length) { s.forEach { append(MAP[it] ?: it) } }
}
