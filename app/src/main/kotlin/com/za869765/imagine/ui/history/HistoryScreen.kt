package com.za869765.imagine.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.util.Clipboard
import java.time.Instant
import java.time.ZoneId

// Kept for HistoryDetailScreen — eventually replace with MediaEntry navigation.
data class HistoryItem(
    val id: String,
    val date: String,
    val isVideo: Boolean = false,
    val duration: String? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onItemClick: (HistoryItem) -> Unit,
) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        entries = MediaHistory.loadAll(ctx)
        loaded = true
    }

    val items = when (filter) {
        "img" -> entries.filter { !it.isVideo }
        "vid" -> entries.filter { it.isVideo }
        else -> entries
    }
    val grouped = items.groupBy { formatDate(it.addedAtSec) }
    val imgCount = entries.count { !it.isVideo }
    val vidCount = entries.count { it.isVideo }

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "歷史",
                showBack = true,
                onBackClick = onBack,
                trailing = { Box(modifier = Modifier.size(48.dp)) },
            )
        },
        showBalanceBar = false,
        // v1.0.57: ImagineScreen 預設 scroll = true (Column.verticalScroll) 會跟內層
        // LazyVerticalGrid 的 vertical scroll 嵌套，導致 IllegalStateException
        // 「Vertically scrollable component was measured with an infinity maximum height」。
        // LazyVerticalGrid 自己有 scroll，外層關掉即可。
        scroll = false,
        bottomNav = null,
    ) {
        // v1.0.54 O3: Column + forEach → SegmentedTab 在外 + LazyVerticalGrid 為主體。
        // 避免幾百張縮圖一次全部 inflate 導致記憶體用量爆 + 首次渲染卡 1-2 秒。
        // date header 用 item span maxLineSpan 撐滿整行，thumbnails 用 items()。
        Column(modifier = Modifier.fillMaxSize()) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("all", "全部 ${entries.size}"),
                    SegmentedOption("img", "圖片 $imgCount"),
                    SegmentedOption("vid", "影片 $vidCount"),
                ),
                activeId = filter,
                onSelected = { filter = it },
                modifier = Modifier.padding(16.dp),
            )

            if (loaded && items.isEmpty()) {
                EmptyState()
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                grouped.forEach { (date, list) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = date,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.04.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                        )
                    }
                    items(items = list, key = { it.uri.toString() }) { entry ->
                        HistoryThumbnail(
                            entry = entry,
                            date = date,
                            onItemClick = onItemClick,
                            ctx = ctx,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryThumbnail(
    entry: MediaEntry,
    date: String,
    onItemClick: (HistoryItem) -> Unit,
    ctx: android.content.Context,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = {
                    onItemClick(
                        HistoryItem(
                            id = entry.uri.toString(),
                            date = date,
                            isVideo = entry.isVideo,
                            duration = entry.durationMs?.let { formatDuration(it) },
                        ),
                    )
                },
                // 長按複製 prompt — 「成功案例」可直接拿走再修
                onLongClick = {
                    val p = entry.prompt
                    if (!p.isNullOrBlank()) {
                        Clipboard.copy(ctx, p, toastMsg = "已複製 prompt")
                    } else {
                        android.widget.Toast.makeText(
                            ctx, "此項沒有 prompt 紀錄", android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ),
    ) {
        AsyncImage(
            model = entry.uri,
            contentDescription = entry.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (!entry.prompt.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    entry.prompt,
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                ImagineIcon(
                    name = "play_arrow",
                    size = 20.dp,
                    fill = 1,
                    tint = Color.White,
                )
            }
            entry.durationMs?.let { ms ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        formatDuration(ms),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.W500,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "還沒有生成紀錄",
            fontSize = 16.sp,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "生成圖片或影片後會自動出現在這裡",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDate(epochSec: Long): String =
    Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
