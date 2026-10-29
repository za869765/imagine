package com.za869765.imagine.ui.component

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v2.3.4 姿勢庫（連結式）：即時讀 ai-pose-gallery.pages.dev（POSE-DB-01，ギア 製作）的清單與縮圖，
 * 選中的那張才下載原寸 PNG 到 filesDir/poses 當參考圖。不轉存、不散佈（對方規定禁止再散佈）。
 */
object PoseGallery {
    const val BASE = "https://ai-pose-gallery.pages.dev/"

    data class Pose(val id: String, val line: String, val no: String, val cat: String, val name: String, val angle: String, val thumb: String, val full: String)

    val LINES = linkedMapOf("全身1人・標準系" to "全身・一般", "全身1人・セクシー系" to "全身・性感", "上半身1人" to "上半身", "2人絡み" to "雙人")
    val ANGLES = linkedMapOf("正面" to "正面", "斜め" to "斜角", "横" to "側面", "上から" to "俯視", "下から" to "仰視")
    private val CATS = mapOf(
        "立ち" to "站姿", "座り" to "坐姿", "寝そべり" to "躺臥", "しゃがみ" to "蹲姿", "前かがみ" to "前傾", "膝立ち" to "跪立",
        "膝立ち・四つん這い" to "跪姿・趴跪", "横座り・女の子座り" to "側坐・鴨子坐", "のけぞり・反り" to "後仰", "ひねり" to "扭身",
        "かわいい系" to "可愛", "セクシー・露骨寄り系" to "性感・大膽", "胸寄せ・谷間強調系" to "擠胸", "顔寄せ系" to "臉靠近", "特殊" to "特殊",
        "密着" to "貼身", "立位" to "立位", "立位密着" to "站立貼身", "座位" to "坐位", "座位密着" to "坐姿貼身", "対面座位" to "面對面坐位",
        "背面座位" to "背向坐位", "正常位" to "正常位", "後背位" to "後背位", "騎乗位" to "騎乘位", "側位" to "側位",
    )
    fun catZh(c: String) = CATS[c] ?: c

    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }
    private var cache: List<Pose>? = null

    /** 讀清單：網頁裡的 const D=[...]；網頁快取一天，連不上時用舊快取。 */
    suspend fun load(ctx: Context): List<Pose> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        val f = File(ctx.cacheDir, "pose_gallery.html")
        val html = runCatching {
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < 86_400_000L) f.readText()
            else http.newCall(Request.Builder().url(BASE).build()).execute().use { r ->
                check(r.isSuccessful) { "HTTP ${r.code}" }
                r.body!!.string().also { f.writeText(it) }
            }
        }.getOrElse { if (f.exists()) f.readText() else throw it }
        val arr = Regex("""const D=(\[.*?\]);""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: error("姿勢庫格式變了，讀不到清單")
        Json.parseToJsonElement(arr).jsonArray.map { e ->
            val o = e.jsonObject
            fun s(k: String) = o[k]?.jsonPrimitive?.content.orEmpty()
            Pose(s("id"), s("系統"), s("ポーズ番号"), s("分類"), s("ポーズ名"), s("アングル"), BASE + s("サムネ"), BASE + s("原寸"))
        }.also { cache = it }
    }

    /** 下載選中的原寸 PNG（只這一張），回傳本機檔 Uri 給參考圖欄位用。 */
    suspend fun download(ctx: Context, p: Pose): Uri = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "poses").apply { mkdirs() }
        val out = File(dir, p.id + ".png")
        if (!out.exists() || out.length() == 0L) {
            http.newCall(Request.Builder().url(p.full).build()).execute().use { r ->
                check(r.isSuccessful) { "下載失敗 HTTP ${r.code}" }
                val tmp = File(dir, p.id + ".tmp")
                tmp.outputStream().use { r.body!!.byteStream().copyTo(it) }
                tmp.renameTo(out)
            }
        }
        Uri.fromFile(out)
    }
}

/** 姿勢庫挑選：分類→動作類型→角度篩選，點縮圖就下載那一張並回傳。 */
@Composable
fun PosePickerContent(onPicked: (Uri) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf<List<PoseGallery.Pose>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var line by rememberSaveable { mutableStateOf("全身1人・標準系") }
    var cat by rememberSaveable { mutableStateOf<String?>(null) }
    var angle by rememberSaveable { mutableStateOf("正面") }
    var busyId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { PoseGallery.load(ctx) }.onSuccess { all = it }.onFailure { error = it.message ?: "讀不到姿勢庫" }
    }
    val cats = all.filter { it.line == line }.map { it.cat }.distinct()
    val shown = all.filter { it.line == line && it.angle == angle && (cat == null || it.cat == cat) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImagineChip("← 素材庫", variant = ChipVariant.Outlined, onClick = onBack)
            SectionHeader("姿勢庫（人偶參考）")
        }
        Text(
            "灰色無臉人偶線稿，放進參考圖讓人物照這個姿勢擺；臉、服裝、場景另外寫或放照片。" +
                "來源 ai-pose-gallery.pages.dev（POSE-DB-01・ギア 製作），即時讀取不轉存；不可用於真人或未成年。",
            fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PoseGallery.LINES.forEach { (k, v) ->
                ImagineChip(v, variant = if (line == k) ChipVariant.Tonal else ChipVariant.Outlined, onClick = { line = k; cat = null })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ImagineChip("全部", variant = if (cat == null) ChipVariant.Tonal else ChipVariant.Outlined, onClick = { cat = null })
            cats.forEach { c -> ImagineChip(PoseGallery.catZh(c), variant = if (cat == c) ChipVariant.Tonal else ChipVariant.Outlined, onClick = { cat = c }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PoseGallery.ANGLES.forEach { (k, v) ->
                ImagineChip(v, variant = if (angle == k) ChipVariant.Tonal else ChipVariant.Outlined, onClick = { angle = k })
            }
        }
        when {
            error != null -> Text("讀不到姿勢庫：$error", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            all.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                Text("${shown.size} 個姿勢・點一下加入參考圖", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = shown, key = { it.id }) { p ->
                        Box(
                            Modifier
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .clickable(enabled = busyId == null) {
                                    busyId = p.id
                                    scope.launch {
                                        runCatching { PoseGallery.download(ctx, p) }
                                            .onSuccess { onPicked(it) }
                                            .onFailure { error = it.message ?: "下載失敗" }
                                        busyId = null
                                    }
                                },
                        ) {
                            AsyncImage(model = p.thumb, contentDescription = p.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                            Text(
                                "${p.no}・${PoseGallery.catZh(p.cat)}",
                                fontSize = 10.sp, color = Color.White,
                                modifier = Modifier.align(Alignment.BottomStart).background(Color(0xAA000000)).padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                            if (busyId == p.id) CircularProgressIndicator(Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        }
    }
}
