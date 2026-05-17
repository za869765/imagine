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

// Standard chrome — AppBar + BudgetBar + content + BottomNav
// Mirrors imagine-components.jsx Screen
@Composable
fun ImagineScreen(
    appBar: @Composable (() -> Unit)? = { ImagineTopAppBar() },
    showBudgetBar: Boolean = true,
    spent: Double = 2.85,
    budgetCap: Double = 20.0,
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
        if (showBudgetBar) {
            BudgetBar(spent = spent, cap = budgetCap)
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
