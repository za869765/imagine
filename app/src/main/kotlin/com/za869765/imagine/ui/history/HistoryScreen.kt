package com.za869765.imagine.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.component.ImagePlaceholder
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab

data class HistoryItem(
    val id: String,
    val date: String,
    val isVideo: Boolean = false,
    val duration: String? = null,
)

private val SAMPLE_HISTORY = listOf(
    HistoryItem("1", "2026-05-17"),
    HistoryItem("2", "2026-05-17", isVideo = true, duration = "0:08"),
    HistoryItem("3", "2026-05-17"),
    HistoryItem("4", "2026-05-17"),
    HistoryItem("5", "2026-05-17"),
    HistoryItem("6", "2026-05-17"),
    HistoryItem("7", "2026-05-16", isVideo = true, duration = "0:10"),
    HistoryItem("8", "2026-05-16"),
    HistoryItem("9", "2026-05-16"),
    HistoryItem("10", "2026-05-16"),
    HistoryItem("11", "2026-05-16"),
    HistoryItem("12", "2026-05-16", isVideo = true, duration = "0:05"),
    HistoryItem("13", "2026-05-15"),
    HistoryItem("14", "2026-05-15"),
    HistoryItem("15", "2026-05-15"),
)

@Composable
fun HistoryScreen(
    onNavSelected: (NavTab) -> Unit,
    onItemClick: (HistoryItem) -> Unit,
) {
    var filter by remember { mutableStateOf("all") }
    val items = when (filter) {
        "img" -> SAMPLE_HISTORY.filter { !it.isVideo }
        "vid" -> SAMPLE_HISTORY.filter { it.isVideo }
        else -> SAMPLE_HISTORY
    }
    val grouped = items.groupBy { it.date }

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "歷史",
                trailing = {
                    ImagineIconButton(name = "auto_awesome", onClick = {})
                },
            )
        },
        showBudgetBar = false,
        bottomNav = { ImagineBottomNav(active = NavTab.HISTORY, onTabSelected = onNavSelected) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("all", "全部 ${SAMPLE_HISTORY.size}"),
                    SegmentedOption("img", "圖片 ${SAMPLE_HISTORY.count { !it.isVideo }}"),
                    SegmentedOption("vid", "影片 ${SAMPLE_HISTORY.count { it.isVideo }}"),
                ),
                activeId = filter,
                onSelected = { filter = it },
            )
            Spacer(modifier = Modifier.height(20.dp))

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
                // 3-column grid emulated via manual rows since we're already in Column
                val rows = list.chunked(3)
                rows.forEach { row ->
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    ) {
                        row.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onItemClick(item) },
                            ) {
                                ImagePlaceholder(
                                    aspect = 1f,
                                    video = item.isVideo,
                                )
                                if (item.isVideo && item.duration != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            item.duration,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.W500,
                                        )
                                    }
                                }
                            }
                        }
                        // Pad with empty cells if row has fewer than 3
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
