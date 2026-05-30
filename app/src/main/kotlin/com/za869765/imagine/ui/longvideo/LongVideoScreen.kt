package com.za869765.imagine.ui.longvideo

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 長片組合 — 把素材庫裡生成過的短片，挑選＋排序＋直接串成一支長片(本機 MediaMuxer，不花 API)。
// P1 挑選/排序、P2 順序預覽(首楨縮圖 + 點縮圖播放預覽)、P3 合成(寫回素材庫)。
@Composable
fun LongVideoScreen(
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var allVideos by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var preview by remember { mutableStateOf<MediaEntry?>(null) }
    val sequence = remember { mutableStateListOf<MediaEntry>() }

    LaunchedEffect(reloadKey) {
        allVideos = MediaHistory.loadAll(ctx).filter { it.isVideo }
        loaded = true
    }

    val available = allVideos.filter { v -> sequence.none { it.uri == v.uri } }
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

            if (!loaded) {
                Text(
                    text = "載入中…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                // ── ① 已選順序 (P1 排序 + P2 預覽) ──
                Text(
                    text = "① 已選順序（${sequence.size}）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (sequence.isEmpty()) {
                    Text(
                        text = "還沒選片段 — 從下方「可用片段」加入，至少 2 段才能合成。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    sequence.forEachIndexed { i, entry ->
                        SeqRow(
                            order = i + 1,
                            entry = entry,
                            isFirst = i == 0,
                            isLast = i == sequence.lastIndex,
                            onPreview = { preview = entry },
                            onUp = {
                                if (i > 0) {
                                    val tmp = sequence[i - 1]
                                    sequence[i - 1] = sequence[i]
                                    sequence[i] = tmp
                                }
                            },
                            onDown = {
                                if (i < sequence.lastIndex) {
                                    val tmp = sequence[i + 1]
                                    sequence[i + 1] = sequence[i]
                                    sequence[i] = tmp
                                }
                            },
                            onRemove = { sequence.removeAt(i) },
                        )
                    }
                    Text(
                        text = "預估總長：${formatDur(totalMs)}（實際以合成結果為準）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // ── ③ 合成 (P3) ──
                val canMerge = sequence.size >= 2 && !merging
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (canMerge) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = canMerge) {
                            merging = true
                            val clips = sequence.map { it.uri }
                            val count = sequence.size
                            scope.launch {
                                val result = VideoMerger.merge(ctx, clips, "長片組合 $count 段")
                                merging = false
                                if (result != null) {
                                    Toast.makeText(
                                        ctx,
                                        "已合成 $count 段，存到素材庫（右上齒輪 → 素材庫查看）",
                                        Toast.LENGTH_LONG,
                                    ).show()
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
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (merging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "  合成中…",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = if (sequence.size >= 2) "🎬 合成長片（${sequence.size} 段）"
                            else "🎬 合成長片（至少 2 段）",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                            color = if (canMerge) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── ② 可用片段 ──
                Text(
                    text = "② 可用片段（${available.size}）",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (allVideos.isEmpty()) {
                    Text(
                        text = "素材庫還沒有影片 — 先到「素材生成 → 影片」做幾段。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (available.isEmpty()) {
                    Text(
                        text = "所有影片都加進去了。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    available.forEach { entry ->
                        AvailRow(
                            entry = entry,
                            onAdd = { sequence.add(entry) },
                            onPreview = { preview = entry },
                        )
                    }
                }
            }
        }

        // 預覽播放 Dialog (一次一支,關閉即釋放 player)
        preview?.let { p ->
            VideoPreviewDialog(uri = p.uri, onDismiss = { preview = null })
        }
    }
}

// 已選片段一列:順序號 + 首楨縮圖(點播放) + 名稱/時長 + 上移/下移/移除。
@Composable
private fun SeqRow(
    order: Int,
    entry: MediaEntry,
    isFirst: Boolean,
    isLast: Boolean,
    onPreview: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$order.",
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp),
        )
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
                Text(
                    text = d,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconBtn("expand_less", enabled = !isFirst, onClick = onUp)
        IconBtn("expand_more", enabled = !isLast, onClick = onDown)
        IconBtn("close", enabled = true, onClick = onRemove)
    }
}

// 可用片段一列:首楨縮圖(點播放) + 名稱/時長 + 加入。
@Composable
private fun AvailRow(entry: MediaEntry, onAdd: () -> Unit, onPreview: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
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
                Text(
                    text = d,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = "加入",
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
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
            Text(text = "🎬", fontSize = 16.sp)
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
        Box(
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
        }
    }
}

@Composable
private fun IconBtn(icon: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp),
    ) {
        ImagineIcon(
            name = icon,
            size = 20.dp,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.outlineVariant,
        )
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
