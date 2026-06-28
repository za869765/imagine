package com.za869765.imagine.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CardVariant { Filled, Outlined }

@Composable
fun ImagineCard(
    modifier: Modifier = Modifier,
    pad: Int = 16,
    variant: CardVariant = CardVariant.Filled,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = when (variant) {
        CardVariant.Filled -> MaterialTheme.colorScheme.surfaceContainerHigh
        CardVariant.Outlined -> Color.Transparent
    }
    val border = when (variant) {
        // 設計稿:卡片都帶 1px 淡邊框(實心卡較淡、外框卡較明顯)
        CardVariant.Outlined -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        CardVariant.Filled -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = shape,
        color = bg,
        border = border,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(pad.dp),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

// Small caps section label between card groups — UI.md §6.10 examples
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 1.0.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}
