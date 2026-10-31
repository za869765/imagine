package com.za869765.imagine.ui.generate

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3Format
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.h3.H3Machine
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// v2.3.0 成品集中看（Claude Design v3 稿）：生成頁一排縮圖、成品頁（全部／圖片／影片＋狀態）、點縮圖直接全螢幕放大

private val AMBER = Color(0xFFE0A100)
private val BLUE = Color(0xFF4A8FE7)
private val GREEN = Color(0xFF2E9E5B)
private val RED = Color(0xFFD9534F)
private val VIDEO_FRAME = Color(0xFFA77BFF)

private fun H3Jobs.Job.isDone() = status == "done"
private fun H3Jobs.Job.isFailed() = status == "failed" || status == "cancelled"
private fun H3Jobs.Job.isRunning() = status in listOf("running", "launching", "setup")
/** v2.4.2 這件派給哪台：新任務記 route；舊任務從規格字尾（「…・製圖機」）推 */
private fun H3Jobs.Job.machine(): String? =
    route.takeIf { it.isNotBlank() && it != "auto" }?.let { H3Format.routeShort(it) }
        ?: meta.substringAfterLast("・", "").takeIf { it in listOf("筆電", "製圖機", "GPUtw 5090", "Colab", "5090", "GPUtw") }

/** v2.4.4 做完存進手機的進度：saving＝下載／串接中、failed＝上次失敗（不再自動重抓，等使用者點縮圖重試） */
private val saveState = mutableStateMapOf<String, String>()

/** 查一次 Studio 拿成品網址並存進所有作品；同一件同時只跑一次。回傳是否存好。 */
private suspend fun saveJobOutputs(app: android.content.Context, prefs: SecurePrefs, id: String): Boolean {
    val j = H3Jobs.find(app, id) ?: return false
    if (j.firstUri() != null) return true
    if (saveState[id] == "saving") return false
    saveState[id] = "saving"
    val ok = if (j.kind == "image") {
        val r = (H3Client.imageRun(prefs, id) as? H3Client.Result.Ok)?.value
        r != null && r.images.isNotEmpty() && saveH3ImagesToHistory(app, prefs, id, r.images, j.prompt).isNotEmpty()
    } else {
        val r = (H3Client.job(prefs, id) as? H3Client.Result.Ok)?.value
        val v = r?.videoUrl
        val segs = r?.segmentUrls
        when {
            v != null -> saveH3ToHistory(app, prefs, id, v, j.prompt) != null
            !segs.isNullOrEmpty() -> saveH3SegmentsToHistory(app, prefs, id, segs, j.prompt) != null
            else -> false
        }
    }
    if (ok) saveState.remove(id) else saveState[id] = "failed"
    return ok
}

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
    // v2.4.0 Cloudflare 用量異常（h3-cron 每小時檢查）
    H3UsageAlerts.alerts.takeIf { it.isNotEmpty() }?.let { a ->
        Text(
            "⚠️ Cloudflare 用量異常：" + a.joinToString("；"),
            fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFFFFD7DA),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).background(Color(0xFF5B1D22), RoundedCornerShape(10.dp)).padding(10.dp),
        )
    }
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

/** v2.3.1 縮圖裡的影片：靜音、循環、自動播（一眼分得出是影片）；離開畫面就釋放。 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun MutedLoopPreview(uri: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}

/** 一格成品：完成＝縮圖（影片靜音循環預覽＋▶）；生成中黃框、排隊中藍框寫預計完成；失敗紅框。 */
@Composable
private fun ResultTile(job: H3Jobs.Job, modifier: Modifier, onClick: () -> Unit) {
    val uri = job.firstUri()
    val c = job.statusColor()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (job.isDone() && uri != null) Color(0xFF1C1C25) else c.copy(alpha = 0.14f))
            // v2.3.2 做完的影片紫色粗框、圖片灰色細框（網路慢預覽還沒動也分得出來）
            .border(
                if (job.isDone() && job.kind == "image") 1.dp else 2.dp,
                when {
                    !job.isDone() -> c
                    job.kind == "image" -> MaterialTheme.colorScheme.outlineVariant
                    else -> VIDEO_FRAME
                },
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick),
    ) {
        if (job.isDone() && uri != null) {
            AsyncImage(model = uri, contentDescription = job.prompt, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (job.kind != "image") MutedLoopPreview(uri, Modifier.fillMaxSize())
            job.machine()?.let { m ->
                Text(
                    m, fontSize = 10.sp, color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
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
                Text((if (job.kind == "image") "圖片" else "影片") + (job.machine()?.let { "・$it" } ?: ""), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    when {
                        job.isDone() -> if (saveState[job.runId] == "failed") "存檔失敗\n點一下重試" else "完成・存檔中…"
                        job.isFailed() -> H3Format.statusLabel(job.status)
                        else -> H3Format.statusLabel(job.status) + (rememberEtaPct(job.runId, job.createdAtMs, job.etaAt)?.let { "・約 $it%" } ?: "") + "\n" + (job.etaAt?.let { H3Format.eta(it, now = rememberNowMs(), sep = "\n") } ?: "預計時間計算中")
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
    val navBottom = rememberHostNavBottom()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = navBottom),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("H3 成品", fontSize = 22.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(
                    "關閉", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(20.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .clickable(onClick = onDismiss).padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
            ResultsGalleryContent(jobs, onTap)
        }
    }
}

/** 成品篩選＋三欄縮圖格；全螢幕對話框和「最近成品」分頁共用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultsGalleryContent(jobs: List<H3Jobs.Job>, onTap: (H3Jobs.Job) -> Unit) {
    var kind by remember { mutableStateOf("all") }
    var st by remember { mutableStateOf("all") }
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
            LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.runId }) { j -> ResultTile(j, Modifier.fillMaxWidth().aspectRatio(0.75f)) { onTap(j) } }
            }
}

/** v2.5.4 底部分頁「最近成品」。 */
@Composable
fun H3ResultsScreen(
    prefs: SecurePrefs,
    onOpenImageJob: (String) -> Unit,
    onOpenVideoJob: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (com.za869765.imagine.ui.component.NavTab) -> Unit,
) {
    com.za869765.imagine.ui.component.ImagineScreen(
        appBar = { com.za869765.imagine.ui.component.ImagineTopAppBar(title = "最近成品", onSettingsClick = onSettingsClick) },
        bottomNav = { com.za869765.imagine.ui.component.ImagineBottomNav(active = com.za869765.imagine.ui.component.NavTab.RESULTS, onTabSelected = onNavSelected) },
        scroll = false,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            H3ResultsSection(prefs, onOpenImageJob, onOpenVideoJob, asPage = true)
        }
    }
}

/**
 * 點縮圖直接全螢幕放大：上下滑看其他完成的成品；圖片可雙指縮放（放大時不翻頁）、影片直接播。
 * v2.3.6 動作加多（存相簿／分享／複製・沿用提示詞／動起來／補上傳或移出 Studio／從清單移除／詳情），
 * 全部避開螢幕圓角與手勢列：外框吃系統安全區再內縮，按鈕至少 52dp 高。
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultViewerDialog(
    items: List<H3Jobs.Job>,
    start: Int,
    onDismiss: () -> Unit,
    onOpenJob: (H3Jobs.Job) -> Unit,
    onReusePrompt: ((String) -> Unit)? = null,
    onAnimate: ((String, String) -> Unit)? = null,
    onGrokEdit: ((String, String) -> Unit)? = null,
    onChanged: () -> Unit = {},
) {
    if (items.isEmpty()) return
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.size - 1)) { items.size }
    val scales = remember { mutableStateMapOf<Int, Float>() }
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var showFull by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(setOf<String>()) }
    var rev by remember { mutableStateOf(0) }  // 補上傳／移出 Studio 後重讀這件的最新狀態
    var editOpen by remember { mutableStateOf(false) }
    var editJob by remember { mutableStateOf<H3Jobs.Job?>(null) }
    var editText by remember { mutableStateOf("") }
    var editEst by remember { mutableStateOf<com.za869765.imagine.data.h3.H3ImageEstimate?>(null) }
    var editRoute by remember { mutableStateOf(prefs.h3ImgRoute) }  // v2.4.6 修改時自己挑機器
    // v2.4.7 能選的都給選：引擎（H3／Grok）、比例、畫質、張數；開視窗時帶原圖的比例畫質
    var editGrok by remember { mutableStateOf(false) }
    var editAspect by remember { mutableStateOf("9:16") }
    var editMp by remember { mutableStateOf(1) }
    var editCount by remember { mutableStateOf(1) }
    val navBottom = rememberHostNavBottom()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // decorFitsSystemWindows=false 才讀得到手勢列／狀態列高度（同 FullscreenImageViewer），按鈕才不會掉到手勢列底下
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = navBottom),
        ) {
            val base = items[pager.currentPage]
            val cur = remember(base.runId, rev) { H3Jobs.find(ctx, base.runId) ?: base }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF26262F)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("✕", fontSize = 18.sp, color = Color.White) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        "${pager.currentPage + 1} / ${items.size}・" + (if (cur.kind == "image") "圖片" else "影片 ${cur.seconds} 秒"),
                        fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.W600,
                    )
                    Text(
                        java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(cur.createdAtMs)) +
                            (if (cur.meta.isNotBlank()) "・${cur.meta}" else "") +
                            when { cur.reuploaded -> "・已補上傳 Studio"; cur.forgotten -> "・只在手機"; else -> "・Studio 有一份" },
                        fontSize = 12.sp, color = Color(0xFFA9A5B8), maxLines = 1,
                    )
                }
            }
            VerticalPager(state = pager, userScrollEnabled = (scales[pager.currentPage] ?: 1f) <= 1.01f, modifier = Modifier.weight(1f)) { page ->
                val j = items[page]
                val uri = j.firstUri()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        j.runId in gone -> Text("已從清單移除", color = Color.White)
                        uri == null -> Text("成品還在存進手機，稍候再開", color = Color.White)
                        j.kind == "image" -> com.za869765.imagine.ui.component.ZoomableImage(uri) { s -> scales[page] = s }
                        page == pager.currentPage -> H3VideoPlayer(url = uri, headers = emptyMap(), userAgent = "imagine", modifier = Modifier.fillMaxSize())
                        else -> AsyncImage(model = uri, contentDescription = j.prompt, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Text(
                cur.prompt.ifBlank { "（沒有提示詞）" } + if (!showFull && cur.prompt.length > 60) "　▾ 展開" else "",
                fontSize = 13.sp, lineHeight = 18.sp, maxLines = if (showFull) 8 else 2, color = Color(0xFFCFCBDB),
                modifier = Modifier.fillMaxWidth().clickable { showFull = !showFull }.padding(horizontal = 8.dp, vertical = 8.dp),
            )
            msg?.let { Text(it, fontSize = 12.sp, color = Color(0xFF9FE3B0), modifier = Modifier.padding(horizontal = 8.dp)) }
            val uri = cur.firstUri()
            val acts = buildList<Pair<String, () -> Unit>> {
                if (cur.kind == "image" && uri != null) add("修改" to { editJob = cur; editText = ""; editEst = null; editRoute = prefs.h3ImgRoute
                    editGrok = false; aspectMpOf(cur, prefs).let { (a, m) -> editAspect = a; editMp = m }; editCount = 1; editOpen = true })
                if (uri != null) add("存到相簿" to {
                    confirm = "把這件存到手機相簿？" to {
                        busy = "存到相簿"
                        scope.launch {
                            val ok = cur.savedUri.orEmpty().split('\n').filter { it.isNotBlank() }
                                .map { com.za869765.imagine.data.storage.MediaExporter.saveToGallery(ctx, it, cur.kind != "image") }.all { it }
                            msg = if (ok) "已存到手機相簿" else "存到相簿失敗"
                            busy = null
                        }
                        Unit
                    }
                })
                // v2.6.0 有種子就複製種子；舊作品沒有種子紀錄才維持複製提示詞
                val copySeed = cur.seed
                add((if (copySeed != null) "複製種子" else "複製提示詞") to {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    if (copySeed != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("seed", copySeed.toString()))
                        msg = "種子已複製"
                    } else {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("prompt", cur.prompt))
                        msg = "提示詞已複製"
                    }
                })
                if (onReusePrompt != null && cur.prompt.isNotBlank()) add("沿用提示詞" to { onReusePrompt(cur.prompt) })
                if (onAnimate != null && cur.kind == "image" && uri != null) add("動起來" to { onAnimate(uri, cur.prompt) })
                if (cur.forgotten && !cur.reuploaded && uri != null) add("補上傳 Studio" to {
                    confirm = "把這件補上傳到 Studio？\n成品會離開手機、傳到雲端。" to {
                        busy = "補上傳 Studio"
                        scope.launch {
                            msg = reuploadToStudio(ctx, prefs, cur)
                            busy = null; rev++; onChanged()
                        }
                        Unit
                    }
                })
                if (!cur.forgotten) add("移出 Studio" to {
                    confirm = "把 Studio 那份刪掉、只留在手機？\n雲端硬碟的檔會移到垃圾桶（30 天內可救回）。之後可再按「補上傳 Studio」。" to {
                        busy = "移出 Studio"
                        scope.launch {
                            val ok = H3Client.forget(prefs, cur.runId)
                            if (ok) H3Jobs.update(ctx, cur.runId) { it.copy(forgotten = true, keepInStudio = false) }
                            msg = if (ok) "已從 Studio 移除，只留在手機" else "移除失敗（可能還沒做完或網路問題）"
                            busy = null; rev++; onChanged()
                        }
                        Unit
                    }
                })
                add("任務詳情" to { onOpenJob(cur) })
                add("從清單移除" to {
                    confirm = "從最近成品移除這件？\n手機「所有作品」裡的檔案不會刪，Studio 也不受影響。" to {
                        H3Jobs.remove(ctx, cur.runId); gone = gone + cur.runId; msg = "已從清單移除"; onChanged()
                    }
                })
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 4,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 20.dp),
            ) {
                acts.forEach { (label, act) ->
                    Box(
                        modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C1C25))
                            .clickable(enabled = busy == null && cur.runId !in gone, onClick = act),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (busy == label) "處理中…" else label, fontSize = 13.sp, color = Color.White, maxLines = 1) }
                }
            }
        }
        val ej = editJob
        if (editOpen && ej != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { if (busy == null) editOpen = false },
                title = { Text("修改這張圖") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it; editEst = null },
                            placeholder = { Text("例：衣服換成白色襯衫、背景改成海邊") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        @Composable
                        fun chips(title: String, opts: List<Pair<String, String>>, sel: String, pick: (String) -> Unit) {
                            Text(title, fontSize = 13.sp, fontWeight = FontWeight.W600)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                opts.forEach { (id, name) ->
                                    androidx.compose.material3.FilterChip(selected = sel == id, onClick = { pick(id); editEst = null }, label = { Text(name, fontSize = 12.sp) })
                                }
                            }
                        }
                        chips("用什麼修", listOfNotNull("h3" to "H3（Qwen）", if (onGrokEdit != null) "grok" to "Grok" else null),
                            if (editGrok) "grok" else "h3") { editGrok = it == "grok" }
                        if (editGrok) {
                            Text(
                                "按「到 Grok 修圖」會帶這張圖和你寫的內容到 Grok 修圖頁，在那邊選參數再送出（花 Grok 額度）。",
                                fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "用這張當參考圖送 H3 生圖（${H3Format.imageSize(editAspect, editMp)}），臉、姿勢、其他沒提到的地方照原圖。",
                                fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            chips("用哪台機器修", listOf("auto" to "自動", "local" to "筆電（免費）", "gputw_image" to "製圖機 約NT$8", "gputw" to "影片機順便", "cloud" to "Colab"), editRoute) { editRoute = it }
                            chips("比例", listOf("9:16", "16:9", "1:1", "3:4", "4:3").map { it to it }, editAspect) { editAspect = it }
                            chips("畫質", listOf("1" to "1K", "4" to "2K"), editMp.toString()) { editMp = it.toInt() }
                            chips("張數", (1..4).map { "$it" to "$it 張" }, editCount.toString()) { editCount = it.toInt() }
                        }
                        if (!editGrok) editEst?.let { Text(it.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        enabled = (editGrok || editText.isNotBlank()) && busy == null,
                        onClick = {
                            val est = editEst
                            if (editGrok) {
                                editOpen = false
                                onGrokEdit?.invoke(ej.firstUri()!!, editText.trim())
                                return@TextButton
                            }
                            busy = "修改"
                            if (est == null) {
                                scope.launch {
                                    when (val r = H3Client.imageEstimate(prefs, editMp, editCount, editRoute)) {
                                        is H3Client.Result.Ok -> editEst = r.value
                                        is H3Client.Result.Err -> msg = "估算失敗：${r.message}"
                                    }
                                    busy = null
                                }
                            } else {
                                val pending = com.za869765.imagine.data.h3.PendingH3Image(
                                    prompt = editText.trim() + "。其餘（人物臉孔、髮型、姿勢、構圖）保持與參考圖相同。",
                                    refs = listOf(ej.firstUri()!!), aspect = editAspect, mp = editMp, count = editCount, route = editRoute,
                                    allowText = false, clientRequestId = java.util.UUID.randomUUID().toString(), estimate = est,
                                )
                                val app = ctx.applicationContext
                                ImagineApp.appScope.launch {
                                    val out = runCatching { com.za869765.imagine.data.h3.H3ImageSubmit.run(app, prefs, pending) {} }
                                        .getOrElse { com.za869765.imagine.data.h3.H3Submit.Outcome.Uncertain(it.message ?: "未預期錯誤") }
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        msg = when (out) {
                                            is com.za869765.imagine.data.h3.H3Submit.Outcome.Created -> "已送出修改，做好會出現在最近成品"
                                            is com.za869765.imagine.data.h3.H3Submit.Outcome.Conflict -> "沒送出：${out.message}"
                                            is com.za869765.imagine.data.h3.H3Submit.Outcome.Rejected -> "沒送出：${out.message}"
                                            is com.za869765.imagine.data.h3.H3Submit.Outcome.Uncertain -> "不確定有沒有送出：${out.message}（看最近成品）"
                                            is com.za869765.imagine.data.h3.H3Submit.Outcome.LocalError -> "沒送出：${out.message}"
                                            else -> "預估變了，請再按一次修改"
                                        }
                                        busy = null; editOpen = false; onChanged()
                                    }
                                }
                            }
                        },
                    ) { Text(if (editGrok) "到 Grok 修圖" else if (busy == "修改") "處理中…" else if (editEst == null) "估算" else "開始修改") }
                },
                dismissButton = { androidx.compose.material3.TextButton(enabled = busy == null, onClick = { editOpen = false }) { Text("取消") } },
            )
        }
        confirm?.let { (text, act) ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirm = null },
                text = { Text(text) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { confirm = null; act() }) { Text("確定") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("取消") } },
            )
        }
    }
}

/**
 * v2.3.8 底部要讓開的高度：從 APP 主畫面（不是對話框）量導覽列／手勢區，對話框裡量到的常是 0；
 * 至少 56dp（三鍵導覽列 48dp＋餘裕）。
 */
@Composable
internal fun rememberHostNavBottom(): androidx.compose.ui.unit.Dp {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(view) {
        val px = androidx.core.view.ViewCompat.getRootWindowInsets(view)?.let { ins ->
            maxOf(
                ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom,
                ins.getInsets(androidx.core.view.WindowInsetsCompat.Type.mandatorySystemGestures()).bottom,
            )
        } ?: 0
        maxOf(with(density) { px.toDp() } + 12.dp, 56.dp)
    }
}

/** 從任務規格（「9:16・2K・1 張・筆電」）取比例與畫質，取不到用生圖頁設定。 */
private fun aspectMpOf(j: H3Jobs.Job, prefs: SecurePrefs): Pair<String, Int> {
    val a = j.meta.substringBefore("・").trim()
    val aspect = if (a in listOf("9:16", "16:9", "1:1", "3:4", "4:3")) a else prefs.h3ImgAspect
    val mp = when { "2K" in j.meta -> 4; "1K" in j.meta -> 1; else -> prefs.h3ImgMp }
    return aspect to mp
}

/** 補上傳：圖片壓成 JPEG（長邊 1600）一張一張傳；影片串流上傳（上限 100MB）。回傳給使用者看的結果。 */
private suspend fun reuploadToStudio(ctx: android.content.Context, prefs: SecurePrefs, j: H3Jobs.Job): String {
    val uris = j.savedUri.orEmpty().split('\n').filter { it.isNotBlank() }
    if (uris.isEmpty()) return "手機裡找不到這件成品"
    var ok = 0
    for ((i, u) in uris.withIndex()) {
        val req = if (j.kind == "image") {
            val im = com.za869765.imagine.data.h3.H3Images.encode(ctx, Uri.parse(u), i) ?: continue
            com.za869765.imagine.data.h3.H3ImportRequest("image", im.base64, im.mime, "APP補上傳_" + j.prompt.take(16), j.prompt)
        } else {
            // v2.4.1 影片先串流上傳到 Studio 的 R2 暫存（上限 100MB），再請 Studio 轉存雲端硬碟
            when (val up = H3Client.uploadRaw(prefs, ctx, Uri.parse(u), "video/mp4")) {
                is H3Client.Result.Ok -> com.za869765.imagine.data.h3.H3ImportRequest(kind = "video", prompt = j.prompt, seconds = j.seconds, uploadKey = up.value.key)
                is H3Client.Result.Err -> return "影片上傳失敗：${up.message}"
            }
        }
        if (H3Client.importToStudio(prefs, req) is H3Client.Result.Ok) ok++
    }
    if (ok == 0) return "補上傳失敗，請稍後再試"
    H3Jobs.update(ctx, j.runId) { it.copy(reuploaded = true) }
    return if (j.kind == "image") "已補上傳 $ok 張到 Studio（作品「Imagine APP 生圖」）" else "已補上傳到 Studio（新作品「Imagine APP：${j.prompt.take(20)}」）"
}

/**
 * 成品區的資料與動作（兩個生成頁共用）：讀 H3Jobs 全部任務（圖片＋影片），沒做完的每 30 秒更新狀態與預計完成，
 * 做完還沒存進手機的自動下載存進所有作品（縮圖、放大都讀本機檔）。
 */
@Composable
fun H3ResultsSection(
    prefs: SecurePrefs,
    onOpenImageJob: (String) -> Unit,
    onOpenVideoJob: (String) -> Unit,
    onReusePrompt: ((String) -> Unit)? = null,
    onAnimate: ((String, String) -> Unit)? = null,
    onGrokEdit: ((String, String) -> Unit)? = null,
    asPage: Boolean = false,
) {
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
                val app = ctx.applicationContext
                for (id in todo) {
                    val j = H3Jobs.find(ctx, id) ?: continue
                    if (j.kind == "image") {
                        val r = (H3Client.imageRun(prefs, id) as? H3Client.Result.Ok)?.value ?: continue
                        H3Jobs.update(ctx, id) { it.copy(status = r.status, etaAt = r.eta?.etaAt ?: it.etaAt, route = r.route ?: it.route) }
                        if (r.status == "done" && r.images.isNotEmpty() && j.firstUri() == null && saveState[id] == null) {
                            ImagineApp.appScope.launch { saveJobOutputs(app, prefs, id) }
                        }
                    } else {
                        val r = (H3Client.job(prefs, id) as? H3Client.Result.Ok)?.value ?: continue
                        H3Jobs.update(ctx, id) { it.copy(status = r.status, etaAt = r.eta?.etaAt ?: it.etaAt, route = r.route ?: it.route) }
                        if (r.status == "done" && j.firstUri() == null && saveState[id] == null) {
                            ImagineApp.appScope.launch { saveJobOutputs(app, prefs, id) }
                        }
                    }
                    jobs = H3Jobs.all(ctx)
                }
                // v2.3.5 設了「不留在 Studio」但上次刪失敗的，再試一次
                for (j in H3Jobs.all(ctx).filter { !it.keepInStudio && !it.forgotten && !it.savedUri.isNullOrBlank() }) forgetInStudioIfAsked(app, prefs, j.runId)
                // 有剛做完正在存的就 5 秒後再看（存好縮圖馬上出現），其餘 30 秒
                // v2.4.3 生成中的每 10 秒查一次（做好約 10 秒內就出現；原本 30 秒，看起來比實際慢）；排隊中的 30 秒
                delay(if (jobs.any { it.isDone() && it.firstUri() == null && saveState[it.runId] != "failed" }) 5_000 else if (jobs.any { it.isRunning() }) 10_000 else 30_000)
                jobs = H3Jobs.all(ctx)
            }
        }
    }
    val openJob: (H3Jobs.Job) -> Unit = { j -> if (j.kind == "image") onOpenImageJob(j.runId) else onOpenVideoJob(j.runId) }
    val tap: (H3Jobs.Job) -> Unit = { j ->
        val done = jobs.filter { it.isDone() && it.firstUri() != null }
        val i = done.indexOfFirst { it.runId == j.runId }
        when {
            i >= 0 -> viewer = done to i
            // v2.4.4 做完卻沒存到手機（例如長片機器端沒串成完整版）：點一下重抓／在手機上串接
            j.isDone() && saveState[j.runId] != "saving" -> {
                saveState.remove(j.runId)
                android.widget.Toast.makeText(ctx, "重新存檔中…", android.widget.Toast.LENGTH_SHORT).show()
                ImagineApp.appScope.launch {
                    val ok = saveJobOutputs(ctx.applicationContext, prefs, j.runId)
                    jobs = H3Jobs.all(ctx)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(ctx, if (ok) "已存好" else "還是存不了，稍後再點一次", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            else -> openJob(j)
        }
    }
    if (asPage) {
        if (jobs.isEmpty()) Text("還沒有成品。到「素材生成」用 H3 生圖或生影片，做好會出現在這裡。", fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else ResultsGalleryContent(jobs, tap)
    } else ResultsStrip(jobs, onOpenAll = { showAll = true }, onTap = tap)
    if (showAll) ResultsGalleryDialog(jobs, onDismiss = { showAll = false }, onTap = tap)
    viewer?.let { (list, i) ->
        ResultViewerDialog(
            list, i,
            onDismiss = { viewer = null; jobs = H3Jobs.all(ctx) },
            onOpenJob = { viewer = null; showAll = false; openJob(it) },
            onReusePrompt = onReusePrompt?.let { f -> { p: String -> viewer = null; showAll = false; f(p) } },
            onAnimate = onAnimate?.let { f -> { u: String, p: String -> viewer = null; showAll = false; f(u, p) } },
            onGrokEdit = onGrokEdit?.let { f -> { u: String, p: String -> viewer = null; showAll = false; f(u, p) } },
            onChanged = { jobs = H3Jobs.all(ctx) },
        )
    }
}
