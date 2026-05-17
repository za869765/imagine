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

// AppBar + (optional) XaiBalanceBar + content + BottomNav.
@Composable
fun ImagineScreen(
    appBar: @Composable (() -> Unit)? = { ImagineTopAppBar() },
    showBalanceBar: Boolean = true,
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
        if (showBalanceBar) {
            XaiBalanceBar()
        }
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
