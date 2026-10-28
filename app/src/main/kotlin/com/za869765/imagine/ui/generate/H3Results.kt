package com.za869765.imagine.ui.generate

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3Format
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.h3.H3Machine
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.coroutines.delay

// v2.3.0 成品集中看（Claude Design v3 稿）：生成頁一排縮圖、成品頁（全部／圖片／影片＋狀態）、點縮圖直接全螢幕放大

private val AMBER = Color(0xFFE0A100)
private val BLUE = Color(0xFF4A8FE7)
private val GREEN = Color(0xFF2E9E5B)
private val RED = Color(0xFFD9534F)

private fun H3Jobs.Job.isDone() = status == "done"
private fun H3Jobs.Job.isFailed() = status == "failed" || status == "cancelled"
private fun H3Jobs.Job.isRunning() = status in listOf("running", "launching", "setup")
private fun H3Jobs.Job.firstUri(): String? = savedUri?.split('\n')?.firstOrNull { it.isNotBlank() }
private fun H3Jobs.Job.statusColor(): Color = when {
    isDone() -> GREEN
    isFailed() -> RED
    isRunning() -> AMBER
    else -> BLUE
}

/** 機器狀態縮成一排小晶片（綠＝待機/空閒、黃＝生成中/開機、灰＝關機），點一下展開詳細。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MachineChips(board: List<H3Machine>, routes: List<String>, highlight: String? = null) {
    val list = routes.mapNotNull { r -> board.firstOrNull { it.route == r } }
    if (list.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val short = mapOf("local" to "筆電", "gputw" to "影片機", "gputw_image" to "製圖機", "cloud" to "Colab")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            modifier = Modifier.clickable { open = !open },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            list.forEach { m ->
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .border(if (m.route == highlight) 2.dp else 1.dp, if (m.route == highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(machineDotColor(m.state), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("${short[m.route] ?: m.name} ${m.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(if (open) "收起 ▴" else "詳細 ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 7.dp))
        }
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                list.forEach { m -> Text("${m.name}：${m.label}。${m.detail}", fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
}

/** 一格成品：完成＝縮圖（影片抓畫格＋▶）；生成中黃框、排隊中藍框寫預計完成；失敗紅框。 */
@Composable
private fun ResultTile(job: H3Jobs.Job, modifier: Modifier, onClick: () -> Unit) {
    val uri = job.firstUri()
    val c = job.statusColor()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (job.isDone() && uri != null) Color(0xFF1C1C25) else c.copy(alpha = 0.14f))
            .border(if (job.isDone()) 1.dp else 2.dp, if (job.isDone()) MaterialTheme.colorScheme.outlineVariant else c, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        if (job.isDone() && uri != null) {
            AsyncImage(model = uri, contentDescription = job.prompt, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (job.kind != "image") {
                Text(
                    "▶ ${job.seconds} 秒",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        } else {
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(if (job.kind == "image") "圖片" else "影片", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        job.isDone() -> "完成・存檔中…"
                        job.isFailed() -> H3Format.statusLabel(job.status)
                        else -> H3Format.statusLabel(job.status) + "\n" + (job.etaAt?.let { H3Format.eta(it, now = rememberNowMs(), sep = "
") } ?: "預計時間計算中")
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = c,
                )
            }
        }
    }
}

/** 生成頁底部：最近成品一排縮圖（圖片＋影片），右邊「全部」開成品頁。 */
@Composable
fun ResultsStrip(jobs: List<H3Jobs.Job>, onOpenAll: () -> Unit, onTap: (H3Jobs.Job) -> Unit) {
    if (jobs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        androidx.compose.material3.HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("最近成品", fontSize = 18.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text("全部 ${jobs.size} ▸", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onOpenAll).padding(8.dp))
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            jobs.take(10).forEach { j -> ResultTile(j, Modifier.width(96.dp).height(128.dp)) { onTap(j) } }
        }
    }
}

/** 成品頁：全部／圖片／影片＋狀態篩選，三欄縮圖格。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsGalleryDialog(jobs: List<H3Jobs.Job>, onDismiss: () -> Unit, onTap: (H3Jobs.Job) -> Unit) {
    var kind by remember { mutableStateOf("all") }
    var st by remember { mutableStateOf("all") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("H3 成品", fontSize = 22.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text("關閉", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp))
            }
            val nImg = jobs.count { it.kind == "image" }
            com.za869765.imagine.ui.component.SegmentedTab(
                options = listOf(
                    com.za869765.imagine.ui.component.SegmentedOption("all", "全部 ${jobs.size}"),
                    com.za869765.imagine.ui.component.SegmentedOption("img", "圖片 $nImg"),
                    com.za869765.imagine.ui.component.SegmentedOption("vid", "影片 ${jobs.size - nImg}"),
                ),
                activeId = kind,
                onSelected = { kind = it },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to ("全部" to MaterialTheme.colorScheme.primary), "run" to ("生成中" to AMBER), "queue" to ("排隊中" to BLUE), "done" to ("完成" to GREEN), "fail" to ("失敗" to RED)).forEach { (id, p) ->
                    Text(
                        p.first,
                        fontSize = 13.sp,
                        color = p.second,
                        modifier = Modifier
                            .border(if (st == id) 2.dp else 1.dp, if (st == id) p.second else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { st = id }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            val shown = jobs.filter { j ->
                (kind == "all" || (kind == "img") == (j.kind == "image")) && when (st) {
                    "run" -> j.isRunning()
                    "queue" -> !j.isRunning() && !j.isDone() && !j.isFailed()
                    "done" -> j.isDone()
                    "fail" -> j.isFailed()
                    else -> true
                }
            }
            if (shown.isEmpty()) Text("這個分類還沒有成品", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.runId }) { j -> ResultTile(j, Modifier.fillMaxWidth().aspectRatio(0.75f)) { onTap(j) } }
            }
        }
    }
}

/** 點縮圖直接全螢幕放大：左右滑看其他完成的成品；圖片可雙指縮放、影片直接播。 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ResultViewerDialog(items: List<H3Jobs.Job>, start: Int, onDismiss: () -> Unit, onOpenJob: (H3Jobs.Job) -> Unit) {
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.size - 1)) { items.size }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("✕", fontSize = 20.sp, color = Color.White, modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp))
                val cur = items[pager.currentPage]
                Text(
                    "${pager.currentPage + 1} / ${items.size}・" + (if (cur.kind == "image") "圖片" else "影片 ${cur.seconds} 秒"),
                    fontSize = 14.sp,
                    color = Color(0xFFCFCBDB),
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                val j = items[page]
                val uri = j.firstUri()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        uri == null -> Text("成品還在存進手機，稍候再開", color = Color.White)
                        j.kind == "image" -> com.za869765.imagine.ui.component.ZoomableImage(uri) {}
                        page == pager.currentPage -> H3VideoPlayer(url = uri, headers = emptyMap(), userAgent = "imagine", modifier = Modifier.fillMaxSize())
                        else -> AsyncImage(model = uri, contentDescription = j.prompt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            val cur = items[pager.currentPage]
            Text(cur.prompt, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 2, color = Color(0xFFCFCBDB), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                listOf(
                    "分享" to {
                        cur.firstUri()?.let { u ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = if (cur.kind == "image") "image/*" else "video/*"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(u))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ctx.startActivity(Intent.createChooser(send, "分享成品"))
                        }
                        Unit
                    },
                    "任務詳情" to { onOpenJob(cur) },
                ).forEach { (label, act) ->
                    Box(
                        modifier = Modifier.weight(1f).height(52.dp).background(Color(0xFF1C1C25), RoundedCornerShape(14.dp)).clickable(onClick = act),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, fontSize = 14.sp, color = Color.White) }
                }
            }
        }
    }
}

/**
 * 成品區的資料與動作（兩個生成頁共用）：讀 H3Jobs 全部任務（圖片＋影片），沒做完的每 30 秒更新狀態與預計完成，
 * 做完還沒存進手機的自動下載存進所有作品（縮圖、放大都讀本機檔）。
 */
@Composable
fun H3ResultsSection(prefs: SecurePrefs, onOpenImageJob: (String) -> Unit, onOpenVideoJob: (String) -> Unit) {
    val ctx = LocalContext.current
    var jobs by remember { mutableStateOf(H3Jobs.all(ctx)) }
    var showAll by remember { mutableStateOf(false) }
    var viewer by remember { mutableStateOf<Pair<List<H3Jobs.Job>, Int>?>(null) }
    val owner = LocalLifecycleOwner.current
    val todo = jobs.filter { !it.isFailed() && (!it.isDone() || it.firstUri() == null) }.map { it.runId }
    LaunchedEffect(owner, todo) {
        if (todo.isEmpty() || !prefs.isH3TokenSet) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                for (id in todo) {
                    val j = H3Jobs.find(ctx, id) ?: continue
                    if (j.kind == "image") {
                        val r = (H3Client.imageRun(prefs, id) as? H3Client.Result.Ok)?.value ?: continue
                        H3Jobs.update(ctx, id) { it.copy(status = r.status, etaAt = r.eta?.etaAt ?: it.etaAt) }
                        if (r.status == "done" && r.images.isNotEmpty() && j.firstUri() == null) saveH3ImagesToHistory(ctx.applicationContext, prefs, id, r.images, j.prompt)
                    } else {
                        val r = (H3Client.job(prefs, id) as? H3Client.Result.Ok)?.value ?: continue
                        H3Jobs.update(ctx, id) { it.copy(status = r.status, etaAt = r.eta?.etaAt ?: it.etaAt) }
                        val v = r.videoUrl
                        if (r.status == "done" && v != null && j.firstUri() == null) saveH3ToHistory(ctx.applicationContext, prefs, id, v, j.prompt)
                    }
                }
                jobs = H3Jobs.all(ctx)
                delay(30_000)
            }
        }
    }
    val openJob: (H3Jobs.Job) -> Unit = { j -> if (j.kind == "image") onOpenImageJob(j.runId) else onOpenVideoJob(j.runId) }
    val tap: (H3Jobs.Job) -> Unit = { j ->
        val done = jobs.filter { it.isDone() && it.firstUri() != null }
        val i = done.indexOfFirst { it.runId == j.runId }
        if (i >= 0) viewer = done to i else openJob(j)
    }
    ResultsStrip(jobs, onOpenAll = { showAll = true }, onTap = tap)
    if (showAll) ResultsGalleryDialog(jobs, onDismiss = { showAll = false }, onTap = tap)
    viewer?.let { (list, i) -> ResultViewerDialog(list, i, onDismiss = { viewer = null }, onOpenJob = { viewer = null; showAll = false; openJob(it) }) }
}
