package com.za869765.imagine.ui.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.component.BudgetBar
import com.za869765.imagine.ui.component.CardVariant
import com.za869765.imagine.ui.component.ChipVariant
import com.za869765.imagine.ui.component.ImagePlaceholder
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineChip
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.TextActionButton

@Composable
fun ComponentShowcase() {
    var prompt by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var switchOn by remember { mutableStateOf(true) }
    var activeMediaTab by remember { mutableStateOf("image") }
    var activeNavTab by remember { mutableStateOf(NavTab.GENERATE) }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine · Showcase") },
        showBudgetBar = true,
        spent = 2.85,
        bottomNav = { ImagineBottomNav(active = activeNavTab) { activeNavTab = it } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── 預算狀態（不同百分比） ──
            SectionHeader("BUDGET BAR — 預算狀態變色")
            ImagineCard(pad = 0) {
                Column {
                    BudgetBar(spent = 4.0, cap = 20.0)
                    BudgetBar(spent = 15.0, cap = 20.0)
                    BudgetBar(spent = 18.5, cap = 20.0)
                    BudgetBar(spent = 21.0, cap = 20.0)
                }
            }

            // ── Buttons ──
            SectionHeader("BUTTONS")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    label = "生 成",
                    icon = "auto_awesome",
                    onClick = { loading = !loading },
                    loading = loading,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedActionButton(label = "前往設定", icon = "settings", onClick = {})
                    OutlinedActionButton(label = "貼上", icon = "content_copy", onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextActionButton(label = "取消", onClick = {})
                    TextActionButton(label = "前往", icon = "arrow_back", onClick = {})
                }
            }

            // ── Segmented ──
            SectionHeader("SEGMENTED TAB")
            SegmentedTab(
                options = listOf(
                    SegmentedOption("image", "圖片"),
                    SegmentedOption("video", "影片"),
                ),
                activeId = activeMediaTab,
                onSelected = { activeMediaTab = it },
            )

            // ── Prompt ──
            SectionHeader("PROMPT INPUT")
            PromptInput(value = prompt, onValueChange = { prompt = it })

            // ── ParamPicker ──
            SectionHeader("PARAM PICKERS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamPicker(
                    label = "解析度", value = "1k",
                    options = listOf("1k", "2k"), onSelect = {},
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "長寬比", value = "16:9",
                    options = listOf("16:9", "1:1"), onSelect = {},
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "數量", value = "1",
                    options = listOf("1", "2", "3", "4"), onSelect = {},
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Chips ──
            SectionHeader("CHIPS")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImagineChip(label = "下載", icon = "download", variant = ChipVariant.Tonal)
                ImagineChip(label = "編輯", icon = "edit", variant = ChipVariant.Tonal)
                ImagineChip(label = "重生", variant = ChipVariant.Outlined)
            }

            // ── Cards ──
            SectionHeader("CARDS")
            ImagineCard(variant = CardVariant.Filled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Filled card",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Default container background, no border",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }
            ImagineCard(variant = CardVariant.Outlined) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Outlined card",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Transparent background, hairline border",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
            }

            // ── Switch (M3 stock) ──
            SectionHeader("SWITCH (Material 3 stock)")
            ImagineCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "啟用生物辨識",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = switchOn, onCheckedChange = { switchOn = it })
                }
            }

            // ── ImagePlaceholder ──
            SectionHeader("IMAGE / VIDEO PLACEHOLDER")
            ImagePlaceholder(label = "16:9 · 2k")
            ImagePlaceholder(aspect = 1f, label = "1:1")
            ImagePlaceholder(aspect = 16f / 9f, label = "0:08 · 720p", video = true)
        }
    }
}
