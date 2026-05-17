package com.za869765.imagine.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import java.time.Instant
import java.time.ZoneId

// Kept for HistoryDetailScreen — eventually replace with MediaEntry navigation.
data class HistoryItem(
    val id: String,
    val date: String,
    val isVideo: Boolean = false,
    val duration: String? = null,
)

@Composable
fun HistoryScreen(
    onNavSelected: (NavTab) -> Unit,
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
                trailing = {
                    ImagineIconButton(name = "auto_awesome", onClick = {})
                },
            )
        },
        showBalanceBar = false,
        bottomNav = { ImagineBottomNav(active = NavTab.HISTORY, onTabSelected = onNavSelected) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("all", "全部 ${entries.size}"),
                    SegmentedOption("img", "圖片 $imgCount"),
                    SegmentedOption("vid", "影片 $vidCount"),
                ),
                activeId = filter,
                onSelected = { filter = it },
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (loaded && items.isEmpty()) {
                EmptyState()
                return@Column
            }

            grouped.forEach { (date, list) ->
                Text(
                    text = date,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.04.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                val rows = list.chunked(3)
                rows.forEach { row ->
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    ) {
                        row.forEach { entry ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        onItemClick(
                                            HistoryItem(
                                                id = entry.uri.toString(),
                                                date = date,
                                                isVideo = entry.isVideo,
                                                duration = entry.durationMs?.let { formatDuration(it) },
                                            ),
                                        )
                                    },
                            ) {
                                AsyncImage(
                                    model = entry.uri,
                                    contentDescription = entry.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
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
                        repeat(3 - row.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
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
