package com.za869765.imagine.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3R2Usage
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineCard
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * v2.9.8 設定 › API 與用量:Cloudflare R2 用量監控。
 * 免費額度條(每月 10 GB)、預估月費、依資料夾分類、每日趨勢折線(伺服器每天留一筆快照,最多 120 天)。
 */
@Composable
fun R2UsageCard(prefs: SecurePrefs) {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<H3R2Usage?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    fun load(fresh: Boolean) {
        if (loading || !prefs.isH3TokenSet) return
        loading = true
        scope.launch {
            when (val r = H3Client.r2Usage(prefs, fresh)) {
                is H3Client.Result.Ok -> { data = r.value; err = null }
                is H3Client.Result.Err -> err = r.message
            }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load(false) }

    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    ImagineCard(pad = 14) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("☁ Cloudflare R2 用量", fontSize = 15.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(if (loading) "更新中…" else "重新整理", fontSize = 13.sp, color = accent, modifier = Modifier.clickable { load(true) }.padding(4.dp))
            }
            if (!prefs.isH3TokenSet) { Text("要先在上面填 H3 Studio 權杖", fontSize = 12.sp, color = muted); return@Column }
            err?.let { Text("讀取失敗:$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            val d = data ?: run { if (err == null) Text("讀取中…", fontSize = 12.sp, color = muted); return@Column }

            // 免費額度條
            val frac = (d.gb / d.freeGb).coerceIn(0.0, 1.0).toFloat()
            Text("已用 ${fmtBytes(d.bytes)} / 免費 ${d.freeGb.toInt()} GB・${d.objects} 個檔" + if (d.truncated) "(檔案太多,只算前 5 萬個)" else "",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(track)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceAtLeast(0.01f)).background(if (frac >= 0.9f) MaterialTheme.colorScheme.error else accent))
            }
            Text(
                if (d.estUsdMonth > 0) "預估超出免費額度的月費:約 US$${String.format(Locale.US, "%.3f", d.estUsdMonth)}(每 GB 每月 US$0.015)"
                else "在免費額度內,儲存不用錢(讀寫次數也有免費額度,下載流量免費)",
                fontSize = 12.sp, color = muted, lineHeight = 17.sp,
            )

            // 分類
            if (d.cats.isNotEmpty()) {
                Text("依用途", fontSize = 13.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
                val max = d.cats.values.maxOf { it.bytes }.coerceAtLeast(1)
                d.cats.entries.sortedByDescending { it.value.bytes }.forEach { (k, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(k, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.38f))
                        Box(Modifier.weight(0.37f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(track)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((v.bytes.toFloat() / max).coerceAtLeast(0.02f)).background(accent))
                        }
                        Text("${fmtBytes(v.bytes)}・${v.objects}", fontSize = 11.sp, color = muted, modifier = Modifier.weight(0.25f))
                    }
                }
            }

            // 每日趨勢
            Text("每日用量趨勢" + if (d.days.size < 2) "(要累積兩天以上才畫得出線)" else "(${d.days.first().day} ～ ${d.days.last().day})",
                fontSize = 13.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 4.dp))
            if (d.days.size >= 2) {
                val peak = d.days.maxOf { it.bytes }.coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val n = d.days.size
                    val pts = d.days.mapIndexed { i, x -> Offset(size.width * i / (n - 1), size.height * (1f - x.bytes.toFloat() / peak) * 0.92f + size.height * 0.04f) }
                    drawLine(track, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 2f)
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    drawPath(path, accent, style = Stroke(width = 4f))
                    pts.forEach { drawCircle(accent, radius = 5f, center = it) }
                }
                Row { Text("最高 ${fmtBytes(peak)}", fontSize = 11.sp, color = muted, modifier = Modifier.weight(1f)); Text("今天 ${fmtBytes(d.days.last().bytes)}", fontSize = 11.sp, color = muted) }
            }
            Text("每 30 分鐘最多重算一次;每天留一筆紀錄。只統計 H3 Studio 用的那個 bucket。", fontSize = 11.sp, color = muted, lineHeight = 16.sp)
        }
    }
}

private fun fmtBytes(b: Long): String = when {
    b >= 1_000_000_000 -> String.format(Locale.US, "%.2f GB", b / 1e9)
    b >= 1_000_000 -> String.format(Locale.US, "%.1f MB", b / 1e6)
    b >= 1_000 -> String.format(Locale.US, "%.0f KB", b / 1e3)
    else -> "$b B"
}
