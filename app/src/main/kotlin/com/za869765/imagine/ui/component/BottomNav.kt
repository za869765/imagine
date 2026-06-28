package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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

enum class NavTab(val id: String, val icon: String, val label: String) {
    MATERIAL("material", "auto_awesome", "素材生成"),
    LONG_VIDEO("long_video", "movie", "長片組合"),
    TUTORIAL("tutorial", "lightbulb", "教學範本"),
}

@Composable
fun ImagineBottomNav(
    active: NavTab = NavTab.MATERIAL,
    onTabSelected: (NavTab) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        // 設計稿:頂部 1px 淡分隔線
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(top = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavTab.values().forEach { tab ->
                val isActive = tab == active
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                // 設計稿 active 丸 = 主色紫 16% 透明
                                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        ImagineIcon(
                            name = tab.icon,
                            size = 23.dp,
                            fill = if (isActive) 1 else 0,
                            tint = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = tab.label,
                        fontSize = 10.5.sp,
                        fontWeight = if (isActive) FontWeight.W700 else FontWeight.W500,
                        color = if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
