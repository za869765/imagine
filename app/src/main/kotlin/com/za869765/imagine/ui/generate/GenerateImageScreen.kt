package com.za869765.imagine.ui.generate

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.CardVariant
import com.za869765.imagine.ui.component.ChipVariant
import com.za869765.imagine.ui.component.ImagePlaceholder
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineChip
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.theme.LocalBudgetColors

@Composable
fun GenerateImageScreen(
    onSwitchToVideo: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onGenerate: (prompt: String, resolution: String, aspectRatio: String, n: Int) -> Unit,
    showLastResult: Boolean = true,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val budgetColors = LocalBudgetColors.current

    var prompt by remember { mutableStateOf("") }
    var resolution by remember { mutableStateOf("1k") }
    var aspectRatio by remember { mutableStateOf("16:9") }
    var n by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }

    val estimated = 0.05 * n
    val remaining = (prefs.budgetCap - prefs.spent).coerceAtLeast(0.0)
    val affordable = remaining >= estimated

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.GENERATE, onTabSelected = onNavSelected) },
        spent = prefs.spent,
        budgetCap = prefs.budgetCap,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("image", "圖片"),
                    SegmentedOption("video", "影片"),
                ),
                activeId = "image",
                onSelected = { if (it == "video") onSwitchToVideo() },
            )

            PromptInput(value = prompt, onValueChange = { prompt = it })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamPicker(
                    label = "解析度",
                    value = resolution,
                    options = listOf("1k", "2k"),
                    onSelect = { resolution = it },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "長寬比",
                    value = aspectRatio,
                    options = listOf("16:9", "1:1", "9:16", "4:3", "3:4", "3:2", "2:3", "auto"),
                    onSelect = { aspectRatio = it },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "數量",
                    value = n.toString(),
                    options = (1..4).map { it.toString() },
                    onSelect = { n = it.toIntOrNull() ?: 1 },
                    modifier = Modifier.weight(1f),
                )
            }

            // 預估費用 Card
            ImagineCard(pad = 14) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "預估費用",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W500,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$" + "%.2f".format(estimated),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "剩餘預算",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W500,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$" + "%.2f".format(remaining),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = FontFamily.Monospace,
                            color = if (affordable) budgetColors.ok else budgetColors.high,
                        )
                    }
                }
            }

            PrimaryButton(
                label = "生 成",
                icon = "auto_awesome",
                loading = loading,
                enabled = prompt.isNotBlank() && affordable,
                onClick = {
                    loading = true
                    onGenerate(prompt, resolution, aspectRatio, n)
                },
            )

            if (showLastResult) {
                LastResultSection()
            }
        }
    }
}

@Composable
private fun LastResultSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .padding(top = 8.dp),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth()) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color(0xFFCAC4D0),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
        }
        Text(
            text = "上次結果",
            fontSize = 11.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = 0.08.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .padding(top = 8.dp),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth()) {
                drawLine(
                    color = androidx.compose.ui.graphics.Color(0xFFCAC4D0),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
        }
    }

    ImagineCard(pad = 0, variant = CardVariant.Filled) {
        Column {
            ImagePlaceholder(aspect = 16f / 9f, label = "2k · 16:9", cornerRadius = 0.dp)
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "\"Cyberpunk night market in Taipei, neon...\"",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp,
                )
                Text(
                    text = "16:9 · 2k · 2026-05-17 14:30",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    ImagineChip(label = "下載", icon = "download", variant = ChipVariant.Tonal)
                    ImagineChip(label = "編輯", icon = "edit", variant = ChipVariant.Tonal)
                    ImagineChip(label = "動起來", icon = "movie", variant = ChipVariant.Tonal)
                }
            }
        }
    }
}
