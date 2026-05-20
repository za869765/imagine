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
