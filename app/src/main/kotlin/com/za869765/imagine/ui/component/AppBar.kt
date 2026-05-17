package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// AppBar: 64dp flat top bar — title left, settings icon right (default).
// Mirrors imagine-components.jsx AppBar.
@Composable
fun ImagineTopAppBar(
    title: String = "Imagine",
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            ImagineIconButton(name = "arrow_back", onClick = onBackClick)
        } else {
            Box(modifier = Modifier.width(8.dp))
        }
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.W600,
            letterSpacing = (-0.01).sp,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
        if (trailing != null) {
            trailing()
        } else {
            ImagineIconButton(name = "settings", onClick = onSettingsClick)
        }
    }
}
