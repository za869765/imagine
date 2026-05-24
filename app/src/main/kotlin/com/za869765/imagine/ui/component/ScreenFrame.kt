package com.za869765.imagine.ui.component

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
                .let { if (scroll) it.verticalScroll(rememberScrollState()) else it },
        ) {
            content()
        }
        bottomNav?.invoke()
    }
}
