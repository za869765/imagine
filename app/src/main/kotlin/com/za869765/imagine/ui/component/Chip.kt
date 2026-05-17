package com.za869765.imagine.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ChipVariant { Tonal, Outlined }

@Composable
fun ImagineChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    variant: ChipVariant = ChipVariant.Tonal,
    onClick: (() -> Unit)? = null,
) {
    val (bg, fg, border) = when (variant) {
        ChipVariant.Tonal -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            null,
        )
        ChipVariant.Outlined -> Triple(
            Color.Transparent,
            MaterialTheme.colorScheme.onSurface,
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        )
    }

    val shape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(shape)
            .background(bg)
            .let { if (border != null) it.border(border, shape) else it }
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            ImagineIcon(name = icon, size = 16.dp, tint = fg)
        }
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = FontWeight.W600,
        )
    }
}
