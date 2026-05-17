package com.za869765.imagine.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.component.CardVariant
import com.za869765.imagine.ui.component.ImagePlaceholder
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.TextActionButton

@Composable
fun HistoryDetailScreen(
    item: HistoryItem,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onAction: (String) -> Unit,
) {
    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "",
                showBack = true,
                onBackClick = onBack,
                trailing = { ImagineIconButton(name = "edit", onClick = onDelete) },
            )
        },
        showBalanceBar = false,
        bottomNav = null,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ImagePlaceholder(
                aspect = if (item.isVideo) 16f / 9f else 3f / 4f,
                video = item.isVideo,
                label = if (item.isVideo) (item.duration ?: "0:08") else "2k · 16:9",
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Prompt")
                ImagineCard(pad = 14) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "A cyberpunk night market in Taipei, neon lights, cinematic lighting, hyper-detailed, 35mm film grain",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 22.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextActionButton(
                                label = "複製",
                                icon = "content_copy",
                                onClick = { onAction("copy") },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("詳細資訊")
                ImagineCard(pad = 0) {
                    Column {
                        val info = listOf(
                            "解析度" to "2k",
                            "長寬比" to "16:9",
                            "生成時間" to "2026-05-17 14:30",
                            "花費" to "$0.05",
                        )
                        info.forEachIndexed { i, (k, v) ->
                            DetailRow(k, v, mono = k == "花費")
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
                    val actions = if (item.isVideo) {
                        listOf(
                            "edit" to "編輯這段",
                            "movie" to "延長影片",
                            "download" to "下載到相簿（已存）",
                        )
                    } else {
                        listOf(
                            "edit" to "編輯這張",
                            "movie" to "動起來（生影片）",
                            "image" to "當參考圖",
                            "download" to "下載到相簿（已存）",
                        )
                    }
                    actions.forEachIndexed { i, (icon, label) ->
                        ActionRow(icon = icon, label = label, onClick = { onAction(label) })
                        if (i < actions.size - 1) {
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
    }
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
            fontWeight = if (mono) FontWeight.W600 else FontWeight.W500,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ActionRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
