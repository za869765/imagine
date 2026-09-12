package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// 「製作方式」單一選項:名稱 + 一句用途。
data class ModeOption(val id: String, val name: String, val desc: String)

/**
 * ModePicker — 生成頁「製作方式」N 選 1(UI_REDESIGN_PLAN 1.2)。
 * 外觀沿用 ParamPicker(12dp 圓角、1dp outline、上 label 下值),點開 ModalBottomSheet,
 * 每項顯示名稱 + 用途說明。取代影片頁橫滑 ModePill 列、圖片頁 UnderlineTab、EditScreen 三段 SegmentedTab。
 *
 * enabled=false(組合延長:方式被鎖定)時不可點、不顯示展開箭頭。
 * footnote:清單底部一行小字(例如 OpenRouter 模型不支援的方式已隱藏)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModePicker(
    options: List<ModeOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "製作方式",
    footnote: String? = null,
    enabled: Boolean = true,
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val selected = options.firstOrNull { it.id == selectedId } ?: options.firstOrNull()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .let { if (enabled) it.clickable { showSheet = true } else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selected?.name ?: "—",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!selected?.desc.isNullOrBlank()) {
                        Text(
                            text = selected!!.desc,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (enabled) {
                    ImagineIcon(
                        name = "expand_more",
                        size = 20.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.08.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                )
                options.forEach { opt ->
                    val isSelected = opt.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(opt.id)
                                scope.launch {
                                    sheetState.hide()
                                    showSheet = false
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opt.name,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.W700 else FontWeight.W600,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = opt.desc,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        if (isSelected) {
                            ImagineIcon(
                                name = "check",
                                size = 20.dp,
                                fill = 1,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (!footnote.isNullOrBlank()) {
                    Text(
                        text = footnote,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
