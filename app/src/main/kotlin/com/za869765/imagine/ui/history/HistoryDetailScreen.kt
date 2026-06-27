package com.za869765.imagine.ui.history

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.MaterialLibrary
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaExporter
import kotlinx.coroutines.launch
import com.za869765.imagine.ui.component.CardVariant
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.util.Clipboard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryDetailScreen(
    entry: MediaEntry?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onAction: (String) -> Unit,
) {
    val ctx = LocalContext.current
    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "",
                showBack = true,
                onBackClick = onBack,
                trailing = { ImagineIconButton(name = "delete", onClick = onDelete) },
            )
        },
        showBalanceBar = false,
        bottomNav = null,
    ) {
        if (entry == null) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "找不到這筆紀錄（可能已被刪除）",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@ImagineScreen
        }

        Column(
            modifier = Modifier
                // navigationBarsPadding 把整個內容上推，避開三鍵列/手勢列遮住最底的「延長影片」鈕。
                // 手勢列高約 24-48dp，三鍵列約 48dp — 兩種都吃得到
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 真實媒體預覽
            if (entry.isVideo) {
                VideoPlayer(
                    uri = entry.uri.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 480.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            } else {
                AsyncImage(
                    model = entry.uri,
                    contentDescription = entry.prompt ?: entry.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Prompt")
                ImagineCard(pad = 14) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // bug #3/4: prompt 用 SelectionContainer 包，使用者可長按選取複製
                        SelectionContainer {
                            Text(
                                entry.prompt ?: "（沒有 prompt 紀錄 — v1.0.24 前產生的舊檔不會有；新生圖會記錄）",
                                fontSize = 14.sp,
                                color = if (entry.prompt == null)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp,
                            )
                        }
                        if (entry.prompt != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextActionButton(
                                    label = "複製",
                                    icon = "content_copy",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    onClick = {
                                        Clipboard.copy(ctx, entry.prompt, toastMsg = "已複製 prompt")
                                        onAction("copy")
                                    },
                                )
                                // 把這段 prompt 帶到文生圖頁當新起點(只帶文字,不帶媒體)
                                TextActionButton(
                                    label = "使用",
                                    icon = "check",
                                    onClick = { onAction("use_prompt") },
                                )
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("詳細資訊")
                ImagineCard(pad = 0) {
                    Column {
                        val info = buildList {
                            add("檔名" to entry.displayName)
                            add("類型" to if (entry.isVideo) "影片" else "圖片")
                            entry.durationMs?.let { add("時長" to formatDuration(it)) }
                            add("建立時間" to formatDate(entry.addedAtSec))
                        }
                        info.forEachIndexed { i, (k, v) ->
                            DetailRow(k, v, mono = k == "檔名" || k == "建立時間")
                            if (i < info.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                        }
                    }
                }
            }

            ImagineCard(pad = 0, variant = CardVariant.Filled) {
                Column {
                    // 素材庫:圖片可歸到 角色/環境/物件/風格 分類(影片不可當圖生圖輸入)
                    if (!entry.isVideo) {
                        CategoryPickerRow(ctx = ctx, name = entry.displayName)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                    val actions = if (entry.isVideo) {
                        listOf(
                            "edit" to "編輯這段",
                            "movie" to "延長影片",
                        )
                    } else {
                        listOf(
                            "edit" to "編輯這張",
                            "movie" to "動起來（生影片）",
                        )
                    }
                    actions.forEach { (icon, label) ->
                        ActionRow(icon = icon, label = label, onClick = { onAction(label) })
                        HDivider()
                    }
                    // 匯出:存進系統相簿 / 系統分享單(沿用 MediaExporter,entry.uri 為本機檔)
                    ActionRow(
                        icon = "download",
                        label = "存到相簿",
                        onClick = {
                            com.za869765.imagine.ImagineApp.appScope.launch {
                                val ok = MediaExporter.saveToGallery(ctx, entry.uri.toString(), isVideo = entry.isVideo)
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
                    HDivider()
                    ActionRow(
                        icon = "share",
                        label = "分享",
                        onClick = {
                            com.za869765.imagine.ImagineApp.appScope.launch {
                                MediaExporter.share(ctx, entry.uri.toString(), isVideo = entry.isVideo)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(uri: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 14.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (mono) FontWeight.W500 else FontWeight.W500,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CategoryPickerRow(ctx: Context, name: String) {
    var current by remember(name) { mutableStateOf(MaterialLibrary.categoryOf(ctx, name)) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = if (current != null) "素材庫分類：$current（再點同一個取消）" else "加入素材庫（當圖生圖／圖生影的參考）",
            fontSize = 15.sp,
            fontWeight = FontWeight.W500,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "標記後可在首頁「素材庫」對應分頁快速找到取用。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaterialLibrary.CATEGORIES.forEach { c ->
                val selected = current == c
                CategoryChip(label = c, selected = selected) {
                    if (selected) {
                        MaterialLibrary.remove(ctx, name)
                        current = null
                        Toast.makeText(ctx, "已移出素材庫", Toast.LENGTH_SHORT).show()
                    } else {
                        MaterialLibrary.setCategory(ctx, name, c)
                        current = c
                        Toast.makeText(ctx, "已設為「$c」素材 ⭐（素材庫可找到）", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun ActionRow(icon: String, label: String, onClick: () -> Unit) {
    // 之前漏 .clickable(onClick) 導致五個按鈕(編輯這張/動起來/當參考圖/編輯這段/
    // 延長影片)全是死的,點下去 callback 從未 trigger
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ImagineIcon(
            name = icon,
            size = 22.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.W500,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatDate(epochSec: Long): String =
    Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).format(DATE_FMT)

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
