package com.za869765.imagine.ui.component

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * UndoBar — 全 App 共用 Snackbar host(UI_REDESIGN_PLAN 2.4):可復原的動作(移出素材庫、移除片段)
 * 不再彈確認視窗,改為做完立刻顯示「復原」。host 由 ImagineScreen 統一渲染於內容區下方。
 */
object UndoBar {
    val host = SnackbarHostState()

    fun show(
        scope: CoroutineScope,
        message: String,
        actionLabel: String = "復原",
        onAction: () -> Unit,
    ) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}
