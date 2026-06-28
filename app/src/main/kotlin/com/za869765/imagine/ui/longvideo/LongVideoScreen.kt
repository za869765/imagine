package com.za869765.imagine.ui.longvideo

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.video.VideoMerger
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.VideoFramePicker
import com.za869765.imagine.data.storage.MediaExporter
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 合成長片存檔時 prompt 都以此開頭,用來辨識「已合成的長片」與一般短片素材。
private const val MERGED_PREFIX = "長片組合"

// 長片組合 — 挑素材庫短片、排序、串成長片(本機 MediaMuxer,不花 API)。
// P1 挑選/排序、P2 首楨縮圖+點縮圖預覽播放、P3 合成(完成即時跳預覽)。
// 可用片段可依 相似/最新/時長 整理;已合成的長片獨立成一區(歷史)。
@Composable
fun LongVideoScreen(
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onUsePrompt: (String) -> Unit = {},
    // 用影片某一格(file:// 圖)當圖生影來源 + 帶 prompt 去影片頁
    onUseFrameForVideo: (String, String) -> Unit = { _, _ -> },
    // 組合延長:某格當圖生影來源(frameUri)+ 原片 uri(baseVideoUri)
    onCombineExtend: (String, String) -> Unit = { _, _ -> },
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var allVideos by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var sortMode by remember { mutableStateOf("sim") }
    var showMerged by remember { mutableStateOf(false) }
    var assemblyPreview by remember { mutableStateOf(false) }
    var framePickerEntry by remember { mutableStateOf<MediaEntry?>(null) }
    val sequence = remember { mutableStateListOf<MediaEntry>() }

    LaunchedEffect(reloadKey) {
        allVideos = MediaHistory.loadAll(ctx).filter { it.isVideo }
        loaded = true
    }

    fun isMerged(e: MediaEntry) = (e.prompt ?: "").startsWith(MERGED_PREFIX)
    val merged = allVideos.filter { isMerged(it) }
    val rawAvailable = allVideos.filter { v -> !isMerged(v) && sequence.none { it.uri == v.uri } }
    val totalMs = sequence.sumOf { it.durationMs ?: 0L }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "長片組合", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.LONG_VIDEO, onTabSelected = onNavSelected) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("長片組合")
            Text(
                text = "把素材庫裡的短片串成一支長片。點縮圖可預覽播放；片段需同解析度/編碼才能直接串接，本機處理不花 API。",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 長片銜接技巧 (來自 super-i 第58節「AI 長影片」四銜接法) — 生成階段先把銜接設計好，比硬接更順。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "銜接技巧（生成階段先設計，比硬接更順）\n" +
                        "①拆段：60s 劇本拆成每 15s 一段（開場→推進→衝突→收束），逐段生再串。\n" +
                        "②影片延續影片：截上段尾 2–3s 當下段生成參考，動作慣性才接得上。\n" +
                        "③重疊銜接：下段開頭重複上段結尾情節，多一個可切點、挑最順處接。\n" +
                        "④藏卡頓：卡頓常只在一兩幀就剪掉，必要時加疊化轉場或反應鏡頭蓋過。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!loaded) {
                Text(
                    text = "載入中…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                // ── ① 已選順序 (P1 排序 + P2 預覽) ──
                SectionHeader("① 主組裝條（${sequence.size}）")
                AssemblyTrack(
                    sequence = sequence,
                    onPreviewClip = { previewUri = it },
                )
                if (sequence.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "總長 ${formatDur(totalMs)}（實際以合成為準）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF56E0D2).copy(alpha = 0.14f))
                                .clickable { assemblyPreview = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = "▶ 預覽組裝",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W700,
                                color = Color(0xFF7FE9DD),
                            )
                        }
                    }
                }

                // ── 合成 (P3) ──
                val canMerge = sequence.size >= 2 && !merging
                PrimaryButton(
                    label = if (sequence.size >= 2) "合成長片（${sequence.size} 段）" else "合成長片（至少 2 段）",
                    icon = "movie",
                    enabled = canMerge,
                    loading = merging,
                    modifier = Modifier.padding(top = 4.dp),
                    onClick = {
                        merging = true
                        val clips = sequence.map { it.uri }
                        val count = sequence.size
                        scope.launch {
                            val result = VideoMerger.merge(ctx, clips, "$MERGED_PREFIX $count 段")
                            merging = false
                            if (result != null) {
                                Toast.makeText(ctx, "已合成 $count 段並存到素材庫", Toast.LENGTH_SHORT).show()
                                previewUri = Uri.parse(result)   // #5 合成完即時跳預覽
                                sequence.clear()
                                reloadKey++
                            } else {
                                Toast.makeText(
                                    ctx,
                                    "合成失敗：片段需同解析度/編碼才能直接串接",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )

                // ── ② 可用片段 (智慧整理:相似/最新/時長) ──
                SectionHeader("② 可用片段（${rawAvailable.size}）")
                if (allVideos.none { !isMerged(it) }) {
                    Text(
                        text = "素材庫還沒有短片 — 先到「素材生成 → 影片」做幾段。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (rawAvailable.isEmpty()) {
                    Text(
                        text = "短片都加進去了。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SegmentedTab(
                        options = listOf(
                            SegmentedOption("sim", "相似"),
                            SegmentedOption("new", "最新"),
                            SegmentedOption("dur", "時長"),
                        ),
                        activeId = sortMode,
                        onSelected = { sortMode = it },
                    )
                    when (sortMode) {
                        "sim" -> {
                            // 依 prompt 相似度分組:相同主體/開頭的片段聚在一起
                            rawAvailable.groupBy { similarityKey(it) }.forEach { (key, list) ->
                                Text(
                                    text = "· $key（${list.size}）",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.W600,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                )
                                list.forEach { entry ->
                                    AvailRow(
                                        entry = entry,
                                        onAdd = { sequence.add(entry) },
                                        onPreview = { previewUri = entry.uri },
                                        onRedo = { framePickerEntry = entry },
                                    )
                                }
                            }
                        }
                        else -> {
                            val sorted = if (sortMode == "dur") {
                                rawAvailable.sortedBy { it.durationMs ?: 0L }
                            } else {
                                rawAvailable.sortedByDescending { it.addedAtSec }
                            }
                            sorted.forEach { entry ->
                                AvailRow(
                                    entry = entry,
                                    onAdd = { sequence.add(entry) },
                                    onPreview = { previewUri = entry.uri },
                                    onRedo = { framePickerEntry = entry },
                                )
                            }
                        }
                    }
                }

                // ── ③ 已合成的長片 (歷史,可摺疊;也能再加進序列繼續接) ──
                if (merged.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showMerged = !showMerged }
                            .padding(top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "③ 已合成的長片（${merged.size}）",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        ImagineIcon(
                            name = if (showMerged) "expand_less" else "expand_more",
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showMerged) {
                        merged.sortedByDescending { it.addedAtSec }.forEach { entry ->
                            AvailRow(
                                entry = entry,
                                onAdd = { sequence.add(entry) },
                                onPreview = { previewUri = entry.uri },
                                onRedo = { framePickerEntry = entry },
                            )
                        }
                    }
                }
            }
        }

        // 預覽播放 Dialog (合成短片/已選/結果共用,一次一支,關閉即釋放)
        previewUri?.let { u ->
            VideoPreviewDialog(uri = u, onDismiss = { previewUri = null })
        }
        // 預覽目前組裝狀態 — 依序播放整條(playlist),不必先合成
        if (assemblyPreview && sequence.isNotEmpty()) {
            AssemblyPreviewDialog(
                uris = sequence.map { it.uri },
                onDismiss = { assemblyPreview = false },
            )
        }
        // 影片畫格擷取器 — 選一格生影(重做)或存為角色(數字人)
        framePickerEntry?.let { e ->
            VideoFramePicker(
                uri = e.uri,
                prompt = e.prompt.orEmpty(),
                onUseFrameForVideo = onUseFrameForVideo,
                onCombineExtend = onCombineExtend,
                onDismiss = { framePickerEntry = null },
            )
        }
    }
}

// ── 主組裝條 (剪映風) — 水平排列已選片段;點縮圖預覽、長按拖曳排序、✕ 移除 ──
@Composable
private fun AssemblyTrack(
    sequence: SnapshotStateList<MediaEntry>,
    onPreviewClip: (Uri) -> Unit,
) {
    if (sequence.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "組裝條是空的 — 下方片段點「加入」放上來;條上長按可拖曳排序。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        return
    }
    val density = LocalDensity.current
    val cellW = 96.dp
    val gap = 8.dp
    val stepPx = with(density) { (cellW + gap).toPx() }
    var draggingName by remember { mutableStateOf<String?>(null) }
    var dragDx by remember { mutableStateOf(0f) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        sequence.toList().forEach { entry ->
            val isDrag = draggingName == entry.displayName
            TrackCell(
                entry = entry,
                order = sequence.indexOf(entry) + 1,
                cellWidth = cellW,
                modifier = Modifier
                    .zIndex(if (isDrag) 1f else 0f)
                    .graphicsLayer { translationX = if (isDrag) dragDx else 0f }
                    .pointerInput(entry.displayName) {
                        detectTapGestures(onTap = { onPreviewClip(entry.uri) })
                    }
                    .pointerInput(entry.displayName) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingName = entry.displayName; dragDx = 0f },
                            onDragEnd = { draggingName = null; dragDx = 0f },
                            onDragCancel = { draggingName = null; dragDx = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDx += amount.x
                                val cur = sequence.indexOfFirst { it.displayName == entry.displayName }
                                if (cur >= 0) {
                                    if (dragDx > stepPx / 2f && cur < sequence.lastIndex) {
                                        sequence.removeAt(cur); sequence.add(cur + 1, entry); dragDx -= stepPx
                                    } else if (dragDx < -stepPx / 2f && cur > 0) {
                                        sequence.removeAt(cur); sequence.add(cur - 1, entry); dragDx += stepPx
                                    }
                                }
                            },
                        )
                    },
                onRemove = {
                    val idx = sequence.indexOfFirst { it.displayName == entry.displayName }
                    if (idx >= 0) sequence.removeAt(idx)
                },
            )
        }
    }
}

@Composable
private fun TrackCell(
    entry: MediaEntry,
    order: Int,
    cellWidth: Dp,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(width = cellWidth, height = 72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        TrackThumb(uri = entry.uri)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(3.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(text = "$order", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.W700)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onRemove)
                .padding(2.dp),
        ) {
            ImagineIcon(name = "close", size = 14.dp, tint = Color.White)
        }
    }
}

@Composable
private fun TrackThumb(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeFirstFrame(ctx, uri)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ImagineIcon(name = "movie", size = 24.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// 預覽目前組裝狀態 — ExoPlayer 依序播放整條 playlist,不必先合成。
@Composable
private fun AssemblyPreviewDialog(uris: List<Uri>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(uris) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItems(uris.map { MediaItem.fromUri(it) })
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(uris) { onDispose { player.release() } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            ImagineIconButton(
                name = "close",
                tint = Color.White,
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding(),
            )
        }
    }
}

// 片段一列:首楨縮圖(點播放) + 名稱/時長 + 加入。
@Composable
private fun AvailRow(
    entry: MediaEntry,
    onAdd: () -> Unit,
    onPreview: () -> Unit,
    onRedo: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val p = entry.prompt?.trim().orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClipThumb(uri = entry.uri, onClick = onPreview)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = clipLabel(entry),
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            val d = formatDur(entry.durationMs)
            if (d.isNotEmpty()) {
                Text(text = d, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (p.isNotEmpty()) {
            // 複製此片段的提示詞
            ImagineIconButton(
                name = "content_copy",
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = { Clipboard.copy(ctx, p, toastMsg = "已複製提示詞") },
            )
        }
        // 擷取此片某一格 → 圖生影重做 / 存為角色(數字人)
        ImagineIconButton(
            name = "movie",
            size = 18.dp,
            tint = MaterialTheme.colorScheme.primary,
            onClick = { onRedo() },
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF56E0D2).copy(alpha = 0.14f))
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "加入",
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = Color(0xFF7FE9DD),
            )
        }
    }
}

// 首楨縮圖 — MediaMetadataRetriever 抽第 0 楨,點擊播放預覽。解碼在 IO,失敗顯示 🎬。
@Composable
private fun ClipThumb(uri: Uri, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeFirstFrame(ctx, uri)?.asImageBitmap() }
    }
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ImagineIcon(name = "movie", size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(2.dp),
        ) {
            ImagineIcon(name = "play_arrow", size = 16.dp, fill = 1, tint = Color.White)
        }
    }
}

// 點縮圖 → 全寬預覽播放;關閉即 release。沿用 HistoryDetail 的 ExoPlayer + PlayerView 模式。
@Composable
private fun VideoPreviewDialog(uri: Uri, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 480.dp),
            )
            // 匯出這支(合成長片/片段)到系統相簿或分享出去
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextActionButton(
                    label = "存到相簿",
                    icon = "download",
                    color = Color.White,
                    onClick = {
                        com.za869765.imagine.ImagineApp.appScope.launch {
                            val ok = MediaExporter.saveToGallery(ctx, uri.toString(), isVideo = true)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(
                                    ctx,
                                    if (ok) "已存到相簿" else "存相簿失敗，改用分享試試",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    },
                )
                TextActionButton(
                    label = "分享",
                    icon = "share",
                    color = Color.White,
                    onClick = {
                        com.za869765.imagine.ImagineApp.appScope.launch {
                            MediaExporter.share(ctx, uri.toString(), isVideo = true)
                        }
                    },
                )
            }
        }
    }
}

// 內部 file:// 用絕對路徑開最穩;其餘走 ContentResolver。抽第 0 楨。
private fun decodeFirstFrame(ctx: Context, uri: Uri): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        val path = if (uri.scheme == "file") uri.path else null
        if (path != null) r.setDataSource(path) else r.setDataSource(ctx, uri)
        r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { r.release() }
    }
}

// 相似度分組鍵 — 取 prompt 的「主體：」段或開頭前幾字,相同者視為相似聚在一起。
private fun similarityKey(entry: MediaEntry): String {
    val p = entry.prompt?.trim().orEmpty()
    if (p.isEmpty()) return "未命名"
    val core = if (p.contains("主體：")) p.substringAfter("主體：") else p
    return core.replace("\n", " ").trim().take(6).ifEmpty { "未命名" }
}

// 片段標籤:優先用 prompt 開頭，否則用檔名。
private fun clipLabel(entry: MediaEntry): String {
    val p = entry.prompt?.trim().orEmpty()
    if (p.isNotEmpty()) return p.replace("\n", " ").take(28)
    return entry.displayName
}

private fun formatDur(ms: Long?): String {
    if (ms == null || ms <= 0L) return ""
    val totalSec = (ms / 1000).toInt()
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m > 0) "$m:${s.toString().padStart(2, '0')}" else "${s}s"
}
