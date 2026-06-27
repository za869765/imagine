package com.za869765.imagine.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.SectionHeader

// 素材生成首頁 — 兩張大卡(圖片/影片)點進去到對應生成頁。
@Composable
fun MaterialHubScreen(
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onOpenLibrary: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
) {
    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader("要產什麼素材？")
            HubCard(
                emoji = "🖼️",
                title = "圖片",
                desc = "文生圖 · 圖生圖",
                colors = listOf(Color(0xFF3A2E6E), Color(0xFF23408A)),
                onClick = onPickImage,
            )
            HubCard(
                emoji = "🎬",
                title = "影片",
                desc = "文生影 · 圖生影 · 影生影 · 延長",
                colors = listOf(Color(0xFF0F5E57), Color(0xFF244A6E)),
                onClick = onPickVideo,
            )
            LibraryButton(onClick = onOpenLibrary)
        }
    }
}

// 素材庫入口 — 比生成卡矮的橫向按鈕,放在圖片/影片下方。
@Composable
private fun LibraryButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "📁", fontSize = 26.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "素材庫",
                fontSize = 16.sp,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "角色 · 環境 · 物件 · 風格 參考圖庫",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ImagineIcon(
            name = "image",
            size = 22.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HubCard(
    emoji: String,
    title: String,
    desc: String,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 156.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = emoji, fontSize = 34.sp)
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.W800,
            color = Color.White,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = desc,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
