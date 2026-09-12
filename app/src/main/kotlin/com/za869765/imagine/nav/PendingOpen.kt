package com.za869765.imagine.nav

import androidx.compose.runtime.mutableStateOf

/**
 * PendingOpen — 通知點擊直達作品(UI_REDESIGN_PLAN 6.7)。
 * MainActivity 在 onCreate / onNewIntent 讀 intent extra 寫進來,ImagineRoot 觀察到就導到作品詳情並清空。
 */
object PendingOpen {
    const val EXTRA_OPEN_URI = "open_history_uri"
    val uri = mutableStateOf<String?>(null)
}
