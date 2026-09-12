package com.za869765.imagine.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// AppBar + content + BottomNav. (v1.0.21 砍 BillingState/XaiBalanceBar;showBalanceBar 死參數已於 UI Phase 4 移除)
//
// ⚠️ v1.0.57 教訓：scroll 預設 true (Column.verticalScroll)，內層放任何 Lazy* component
//   (LazyColumn / LazyRow / LazyVerticalGrid / LazyHorizontalGrid) 必須傳 scroll = false
//   否則 vertical scroll 嵌套會 throw IllegalStateException「infinity maximum height」。
//   v1.0.54 我把 HistoryScreen 改 LazyVerticalGrid 沒設 scroll = false → v1.0.56 一裝就閃退。
@Composable
fun ImagineScreen(
    appBar: @Composable (() -> Unit)? = { ImagineTopAppBar() },
    bottomNav: @Composable (() -> Unit)? = { ImagineBottomNav() },
    contentBackground: Color? = null,
    scroll: Boolean = true,
    // v1.0.63 bug#3: 露出 scrollState 讓 caller 能在「新結果到達」時 animateScrollTo 把
    // 結果捲進視野 — 之前內部 rememberScrollState 沒露出,第二次生成時新圖/影在 fold
    // 下方不會自動出現,使用者以為沒生成只能去歷史看。預設值不變,既有 caller 不受影響。
    scrollState: ScrollState = rememberScrollState(),
    // UI_REDESIGN_PLAN 1.6:固定在內容區與 BottomNav 之間的主按鈕 slot(生成/延長/修改…)。
    // 跟內容區一起吃 ime inset → 鍵盤開啟時輸入框與主按鈕同時可見;bottomNav 為 null 時
    // (組合延長獨立頁)自己吃 navigationBars inset,不被三星手勢列擋住。
    bottomAction: (@Composable () -> Unit)? = null,
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
                // 鍵盤(IME)跳出時把內容區上推,避免遮住提示詞輸入框。
                // 因為 MainActivity 用 enableEdgeToEdge()(decorFitsSystemWindows=false),
                // manifest 的 adjustResize 不會自動縮版面 → 必須自己吃 ime inset。
                // 放在 verticalScroll 之前:捲動視窗高度=扣掉鍵盤後的可視區,
                // PromptInput 的 bringIntoView 才能把輸入框帶到鍵盤上方。
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(bg)
                    .let { if (scroll) it.verticalScroll(scrollState) else it },
            ) {
                content()
            }
            // 共用可復原 Snackbar(UI_REDESIGN_PLAN 2.4)
            SnackbarHost(hostState = UndoBar.host)
            if (bottomAction != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .let { if (bottomNav == null) it.windowInsetsPadding(WindowInsets.navigationBars) else it }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    bottomAction()
                }
            }
        }
        bottomNav?.invoke()
    }
}
