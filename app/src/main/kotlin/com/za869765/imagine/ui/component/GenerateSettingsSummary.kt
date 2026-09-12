package com.za869765.imagine.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * GenerateSettingsSummary — 生成參數收成一列摘要(UI_REDESIGN_PLAN 1.4)。
 * 收合時只顯示「生成設定」+ 摘要文字(例:「直式 9:16・720p・6 秒」);點擊展開 content(ParamPicker 群)。
 * 展開狀態 rememberSaveable,切頁回來維持。
 */
@Composable
fun GenerateSettingsSummary(
    summary: String,
    modifier: Modifier = Modifier,
    label: String = "生成設定",
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W500,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ImagineIcon(
                name = if (expanded) "expand_less" else "expand_more",
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

// 長寬比顯示「用途 + 值」(例:直式 9:16),ParamPicker displayName 與摘要共用,不只給數字。
fun aspectLabel(ar: String): String = when (ar) {
    "9:16", "3:4", "2:3" -> "直式 $ar"
    "16:9", "4:3", "3:2" -> "橫式 $ar"
    "1:1" -> "方形 1:1"
    "auto" -> "自動"
    else -> ar
}

// 摘要值顯示規則單一來源(結果區 meta 亦可復用)。
fun describeImageSettings(resolution: String, aspect: String, count: Int): String =
    "$resolution・${aspectLabel(aspect)}・$count 張"

fun describeVideoSettings(durationSec: Int, aspect: String, resolution: String): String =
    "${aspectLabel(aspect)}・$resolution・$durationSec 秒"

// 本次預估費用(UI_REDESIGN_PLAN 6.2):只有目錄提供「每張／每秒」單價時才算,其餘(token/megapixel 計價)不顯示、不推測。
fun estimateCost(unitPrice: Double?, unit: String, count: Int): String? {
    if (unitPrice == null || unitPrice <= 0.0 || count <= 0) return null
    if (unit != "image" && unit != "second") return null
    val total = unitPrice * count
    val text = if (total >= 1.0) String.format(java.util.Locale.US, "%.2f", total)
    else String.format(java.util.Locale.US, "%.3f", total).trimEnd('0').trimEnd('.')
    return "約 $$text"
}
