package com.za869765.imagine.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 影片背景任務的輔助文字(硬約束:兩句必須保留)
const val GENERATING_HINT_BACKGROUND =
    "可切背景／鎖屏，完成會發通知；請勿從「最近應用程式」滑掉 Imagine，否則背景工作會中斷"

/**
 * GeneratingCard — 生成中狀態卡(UI_REDESIGN_PLAN 2.1):只顯示真實階段 + 已等待時間 + 不定進度條。
 * 不再依秒數估算百分比(舊卡封頂 97% 會長時間停在同一數值)。
 * stage:「已送出」「等待結果」「下載中」等,由 caller 依 Worker progress 映射。
 */
@Composable
fun GeneratingCard(
    stage: String,
    elapsedSec: Int,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    ImagineCard(pad = 20, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                )
                Text(
                    text = stage,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
