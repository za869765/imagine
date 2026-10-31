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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * v2.7.9 服裝參考（連結式）：assets/clothing_refs.json 只放 paipiange/nvzhuang 固定版本的圖片路徑，不轉存、不散佈（原作者 @giaaibusi 禁止再散布）。
 * 第一批：春夏秋冬與派對各 20 張；不收內衣、泳裝、性感類。選中的那張才下載到本機當參考圖。
 */
object ClothingGallery {
    @Serializable
    data class Item(val id: String, val cat: String, val path: String)

    @Serializable
    private data class Index(val sha: String, val items: List<Item>)

    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build() }
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: Index? = null

    private fun index(ctx: Context): Index = cache ?: json.decodeFromString<Index>(
        ctx.assets.open("clothing_refs.json").bufferedReader(Charsets.UTF_8).use { it.readText() },
    ).also { cache = it }

    fun items(ctx: Context): List<Item> = runCatching { index(ctx).items }.getOrDefault(emptyList())

    fun url(ctx: Context, item: Item): String = "https://raw.githubusercontent.com/paipiange/nvzhuang/${index(ctx).sha}/${item.path}"

    suspend fun download(ctx: Context, item: Item): Uri = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "clothing").apply { mkdirs() }
        val out = File(dir, item.id + ".webp")
        if (!out.exists() || out.length() == 0L) {
            http.newCall(Request.Builder().url(url(ctx, item)).build()).execute().use { r ->
                check(r.isSuccessful) { "下載失敗 HTTP ${r.code}" }
                val tmp = File(dir, item.id + ".tmp")
                tmp.outputStream().use { r.body!!.byteStream().copyTo(it) }
                tmp.renameTo(out)
            }
        }
        Uri.fromFile(out)
    }
}

/** 服裝參考挑選：依季節篩選，點縮圖就下載那一張並回傳。 */
@Composable
fun ClothingPickerContent(onPicked: (Uri) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all = remember { ClothingGallery.items(ctx) }
    var cat by rememberSaveable { mutableStateOf("春") }
    var busyId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImagineChip("← 素材庫", variant = ChipVariant.Outlined, onClick = onBack)
            SectionHeader("服裝參考")
        }
        Text(
            "AI 服裝概念圖，放進參考圖後在提示詞寫「人物穿著參考圖的服裝」。來源 paipiange/nvzhuang（圖片原作者 @giaaibusi），即時讀取不轉存；圖片本身禁止再散布或販售。",
            fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            all.map { it.cat }.distinct().forEach { c ->
                ImagineChip(c, variant = if (cat == c) ChipVariant.Tonal else ChipVariant.Outlined, onClick = { cat = c })
            }
        }
        error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = all.filter { it.cat == cat }, key = { it.id }) { item ->
                Box(
                    Modifier
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable(enabled = busyId == null) {
                            busyId = item.id
                            error = null
                            scope.launch {
                                runCatching { ClothingGallery.download(ctx, item) }
                                    .onSuccess { onPicked(it) }
                                    .onFailure { error = it.message ?: "下載失敗" }
                                busyId = null
                            }
                        },
                ) {
                    AsyncImage(model = ClothingGallery.url(ctx, item), contentDescription = item.id, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                    if (busyId == item.id) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
