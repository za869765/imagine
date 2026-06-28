package com.za869765.imagine.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

// 素材生成首頁(重設計 Frame 1):兩張主卡(圖片/影片,大、優先)+ 兩條工具列(素材庫/Grok,次要)。
@Composable
fun MaterialHubScreen(
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenGrok: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader("要產什麼素材？")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryGenCard(
                    modifier = Modifier.weight(1f),
                    icon = "image",
                    title = "圖片",
                    tags = listOf("文生圖", "圖生圖"),
                    cardBg = Color(0xFF14182A),
                    base = Color(0xFF6E8BFF),
                    iconColor = Color(0xFF9DB0FF),
                    onClick = onPickImage,
                )
                PrimaryGenCard(
                    modifier = Modifier.weight(1f),
                    icon = "movie",
                    title = "影片",
                    tags = listOf("文生影", "圖生影", "延長"),
                    cardBg = Color(0xFF0F2422),
                    base = Color(0xFF2BD4C6),
                    iconColor = Color(0xFF56E0D2),
                    onClick = onPickVideo,
                )
            }
            SectionHeader("工具")
            ToolTile(
                icon = "photo_library",
                iconColor = Color(0xFFEC8BD2),
                iconBg = Color(0xFFE06AC0).copy(alpha = 0.15f),
                title = "素材庫",
                subtitle = "角色・環境・物件・風格 參考圖庫",
                trailing = "chevron_right",
                onClick = onOpenLibrary,
            )
            ToolTile(
                icon = "forum",
                iconColor = Color(0xFFAEB6C6),
                iconBg = Color(0xFF8A94A6).copy(alpha = 0.16f),
                title = "提示詞諮詢",
                badge = "Grok",
                subtitle = "開啟 grok.com 網頁版（帳號登入）",
                trailing = "language",
                onClick = onOpenGrok,
            )
        }
    }
}

// 主卡:左上彩色 icon 磚 + 右上柔光暈 + 左下大標題 + 標籤 chip 群。
@Composable
private fun PrimaryGenCard(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    tags: List<String>,
    cardBg: Color,
    base: Color,
    iconColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 168.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, base.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        // 右上柔光暈
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 28.dp, y = (-28).dp)
                .size(90.dp)
                .background(
                    Brush.radialGradient(listOf(base.copy(alpha = 0.22f), Color.Transparent)),
                ),
        )
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(base.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                ImagineIcon(name = icon, size = 26.dp, fill = 1, tint = iconColor)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.W900,
                color = Color(0xFFECECF0),
                modifier = Modifier.padding(top = 14.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 9.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = tag,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.W500,
                        color = iconColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(base.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

// 工具列:彩色 icon 磚 + 標題(可帶 badge)+ 副標 + 右側箭頭。
@Composable
private fun ToolTile(
    icon: String,
    iconColor: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    trailing: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF17181D))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            ImagineIcon(name = icon, size = 24.dp, fill = 1, tint = iconColor)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.W700,
                    color = Color(0xFFECECF0),
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W500,
                        color = Color(0xFFAEB6C6),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFAEB6C6).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.W500,
                color = Color(0xFF9A9AA6),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        ImagineIcon(
            name = trailing,
            size = if (trailing == "chevron_right") 16.dp else 20.dp,
            tint = Color(0xFF5A5B66),
        )
    }
}
