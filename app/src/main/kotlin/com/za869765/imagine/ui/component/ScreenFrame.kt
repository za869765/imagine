package com.za869765.imagine.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// AppBar + content + BottomNav. (v1.0.21 砍 BillingState/XaiBalanceBar
// — Bill API 不準改成 SettingsScreen 連結到 console.x.ai 看用量)
// 保留 showBalanceBar param 純為相容 callers，不再 render anything.
//
// ⚠️ v1.0.57 教訓：scroll 預設 true (Column.verticalScroll)，內層放任何 Lazy* component
//   (LazyColumn / LazyRow / LazyVerticalGrid / LazyHorizontalGrid) 必須傳 scroll = false
//   否則 vertical scroll 嵌套會 throw IllegalStateException「infinity maximum height」。
//   v1.0.54 我把 HistoryScreen 改 LazyVerticalGrid 沒設 scroll = false → v1.0.56 一裝就閃退。
@Composable
fun ImagineScreen(
    appBar: @Composable (() -> Unit)? = { ImagineTopAppBar() },
    @Suppress("UNUSED_PARAMETER") showBalanceBar: Boolean = false,
    bottomNav: @Composable (() -> Unit)? = { ImagineBottomNav() },
    contentBackground: Color? = null,
    scroll: Boolean = true,
    // v1.0.63 bug#3: 露出 scrollState 讓 caller 能在「新結果到達」時 animateScrollTo 把
    // 結果捲進視野 — 之前內部 rememberScrollState 沒露出,第二次生成時新圖/影在 fold
    // 下方不會自動出現,使用者以為沒生成只能去歷史看。預設值不變,既有 caller 不受影響。
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable () -> Unit,
) {
    val bg = contentBackground ?: MaterialTheme.colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        appBar?.invoke()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(bg)
                .let { if (scroll) it.verticalScroll(scrollState) else it },
        ) {
            content()
        }
        bottomNav?.invoke()
    }
}
