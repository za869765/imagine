package com.za869765.imagine.ui.longvideo

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.video.VideoMerger
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.SectionHeader
import kotlinx.coroutines.launch

// 長片組合 — 把素材庫裡生成過的短片，挑選＋排序＋直接串成一支長片(本機 MediaMuxer，不花 API)。
// P1 挑選/排序、P2 順序預覽(含時長)、P3 合成(寫回素材庫)。
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
                text = "把素材庫裡的短片串成一支長片。本機處理、不花 API；片段需同解析度/編碼才能直接串接。",
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
                        AvailRow(entry = entry, onAdd = { sequence.add(entry) })
                    }
                }
            }
        }
    }
}

// 已選片段一列:順序號 + 🎬 + 名稱/時長 + 上移/下移/移除。
@Composable
private fun SeqRow(
    order: Int,
    entry: MediaEntry,
    isFirst: Boolean,
    isLast: Boolean,
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
        Text(text = "🎬", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
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

// 可用片段一列:🎬 + 名稱/時長 + 加入。
@Composable
private fun AvailRow(entry: MediaEntry, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onAdd)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🎬", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
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
