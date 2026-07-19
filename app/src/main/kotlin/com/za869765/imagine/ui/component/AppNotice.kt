package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// app 內建提示浮層 — 取代系統 Toast。
// One UI(Android 13+)在 app 沒有通知權限時會把系統 Toast 整個吃掉(本 app 未申請
// POST_NOTIFICATIONS),「存相簿」等回饋因此完全看不到。改走 app 內浮層,不依賴系統管道。
// 用法:任何地方呼叫 AppNotice.show("已存到相簿")。
// host 掛兩處:MainActivity 根層(蓋全部畫面) + FullscreenImageViewer(Dialog 視窗蓋住根層,要自己掛一份)。
object AppNotice {
    private var seq = 0L
    val current = mutableStateOf<Pair<Long, String>?>(null)
    fun show(msg: String) {
        current.value = ++seq to msg
    }
}

@Composable
fun AppNoticeHost() {
    val notice = AppNotice.current.value
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(1800)
            if (AppNotice.current.value == notice) AppNotice.current.value = null
        }
    }
    if (notice == null) return
    // 無 clickable/pointerInput → 不攔截觸控,浮層期間畫面照常可操作。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF141418).copy(alpha = 0.94f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(
                text = notice.second,
                color = Color(0xFFECECF0),
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
            )
        }
    }
}
