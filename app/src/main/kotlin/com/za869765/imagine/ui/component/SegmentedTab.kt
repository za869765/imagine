package com.za869765.imagine.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

data class SegmentedOption(val id: String, val label: String)

@Composable
fun SegmentedTab(
    options: List<SegmentedOption>,
    activeId: String,
    onSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    // activeColor 非 null 時，選中段背景用此模式色、文字/icon 用對比白；null 沿用 secondaryContainer
    activeColor: Color? = null,
) {
    val activeBg = activeColor ?: MaterialTheme.colorScheme.secondaryContainer
    val activeFg = if (activeColor != null) Color.White
    else MaterialTheme.colorScheme.onSecondaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(100.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(100.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, option ->
            val isActive = option.id == activeId
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (isActive) activeBg
                        else Color.Transparent
                    )
                    .clickable { onSelected(option.id) }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isActive) {
                    ImagineIcon(
                        name = "check",
                        size = 16.dp,
                        tint = activeFg,
                    )
                    Box(modifier = Modifier.padding(start = 6.dp))
                }
                Text(
                    text = option.label,
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.W600 else FontWeight.W500,
                    color = if (isActive) activeFg
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (i < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

