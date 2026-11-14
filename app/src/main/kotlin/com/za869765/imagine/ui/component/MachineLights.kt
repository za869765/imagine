package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3MachineBoard
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.coroutines.delay

/**
 * v3.4.8 標題列燈號：桌機工人、筆電工人、Colab、GPUtw 四台的狀態，Colab 與 GPUtw 另外顯示餘額。
 * 綠＝在線、空閒可接單；黃＝生成中或開機中；紅＝離線或關機。
 * 全 APP 共用一份資料（每個畫面的標題列都看同一份），畫面看得到時約每 30 秒向 H3 Studio 查一次，不花錢。
 */
object MachineLights {
    var board by mutableStateOf<H3MachineBoard?>(null)
    @Volatile var fetchedAt = 0L
}

private val LightGreen = Color(0xFF2E9E5B)
private val LightYellow = Color(0xFFE0A100)
private val LightRed = Color(0xFFD64545)

private fun lightColor(state: String?): Color = when (state) {
    "idle", "standby" -> LightGreen
    "busy", "booting" -> LightYellow
    else -> LightRed
}

@Composable
private fun Light(label: String, state: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(lightColor(state), CircleShape))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

/** 兩行小字：上行桌機、筆電；下行 Colab 餘額、GPUtw 餘額。沒設 H3 權杖或還沒查到資料時不佔位置。 */
@Composable
fun MachineLightsRow() {
    val prefs = SecurePrefs.get(LocalContext.current)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        if (!prefs.isH3TokenSet) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // 同時有好幾個標題列在畫面上（換頁動畫）時，25 秒內只查一次
                if (System.currentTimeMillis() - MachineLights.fetchedAt > 25_000) {
                    MachineLights.fetchedAt = System.currentTimeMillis()
                    (H3Client.machines(prefs) as? H3Client.Result.Ok)?.value?.let { MachineLights.board = it }
                }
                delay(30_000)
            }
        }
    }
    val b = MachineLights.board ?: return
    fun st(route: String): String? = b.machines.firstOrNull { it.route == route }?.state
    val cu = b.cuBalance?.let { " %.0fCU".format(it) } ?: ""
    val ntd = b.ntdBalance?.let { " $%.0f".format(it) } ?: ""
    Column(
        modifier = Modifier.padding(end = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Light("桌機", st("desk"))
            Light("筆電", st("local"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Light("Colab$cu", st("cloud"))
            Light("GPUtw$ntd", st("gputw"))
        }
    }
}
