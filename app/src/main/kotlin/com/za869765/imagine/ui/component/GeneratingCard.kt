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
import kotlin.math.exp

// 影片背景任務的輔助文字(硬約束:兩句必須保留)
const val GENERATING_HINT_BACKGROUND =
    "可切背景／鎖屏，完成會發通知；請勿從「最近應用程式」滑掉 Imagine，否則背景工作會中斷"

/**
 * v2.5.2 依秒數估算百分比:預估時間內走到 90%,超過後越走越慢逼近 99%,
 * 不會像舊卡封頂 97% 那樣卡死在同一數值。xAI／OpenRouter 不回真實進度,所以標「約」。
 */
private fun estimatedPct(elapsedSec: Int, estSec: Int): Int {
    val t = elapsedSec.toDouble() / estSec
    val pct = if (t <= 1.0) 90 * t else 99 - 9 * exp(1 - t)
    return pct.toInt().coerceIn(1, 99)
}

/**
 * GeneratingCard — 生成中狀態卡(UI_REDESIGN_PLAN 2.1):真實階段 + 已等待時間 + 進度條。
 * 給 estSec 時加顯示估算百分比、進度條跟著走;沒給維持不定進度條。
 * stage:「已送出」「等待結果」「下載中」等,由 caller 依 Worker progress 映射。
 */
@Composable
fun GeneratingCard(
    stage: String,
    elapsedSec: Int,
    modifier: Modifier = Modifier,
    hint: String? = null,
    estSec: Int? = null,
) {
    val pct = estSec?.let { estimatedPct(elapsedSec, it) }
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
            if (pct != null) {
                Text(
                    text = "約 $pct%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.primary,
                )
                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
            }
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
