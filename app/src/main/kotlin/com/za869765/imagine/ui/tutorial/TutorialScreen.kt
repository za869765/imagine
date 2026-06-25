package com.za869765.imagine.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.tutorial.TutorialData
import com.za869765.imagine.data.tutorial.TutorialLesson
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.READY_PROMPTS
import com.za869765.imagine.ui.component.ReadyPromptCard
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.util.Clipboard

// 教學範本頁 (底部第3分頁):
//   ① 精選範本 — App 內建乾淨可用 prompt,每條「複製」+「使用→生成」(圖/影分流)。
//   ② 課程圖庫 — super-i 各節範例圖 (Coil 由 CDN 即時載入,不打包) + 該節原始提示詞片段可逐條複製。
// onUsePrompt(prompt, isVideo): 由 NavHost 接,沿用既有 KEY_INIT_PROMPT 預填機制導到生成頁。
@Composable
fun TutorialScreen(
    onUsePrompt: (String, Boolean) -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onSettingsClick: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf("ready") }
    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "教學範本", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.TUTORIAL, onTabSelected = onNavSelected) },
        scroll = false,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("ready", "精選範本"),
                    SegmentedOption("gallery", "課程圖庫"),
                ),
                activeId = tab,
                onSelected = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            if (tab == "ready") {
                ReadyList(onUsePrompt, modifier = Modifier.weight(1f))
            } else {
                GalleryList(onUsePrompt, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReadyList(
    onUsePrompt: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var filter by rememberSaveable { mutableStateOf("all") }
    val ready = remember(filter) {
        READY_PROMPTS.filter {
            when (filter) {
                "image" -> it.forVideo != true
                "video" -> it.forVideo != false
                else -> true
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        SegmentedTab(
            options = listOf(
                SegmentedOption("all", "全部"),
                SegmentedOption("image", "圖片"),
                SegmentedOption("video", "影片"),
            ),
            activeId = filter,
            onSelected = { filter = it },
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ready.size) { i ->
                val ex = ready[i]
                ReadyPromptCard(
                    ex = ex,
                    onCopy = { Clipboard.copy(ctx, ex.text, toastMsg = "已複製提示詞") },
                    onUse = { onUsePrompt(ex.text, ex.forVideo == true) },
                )
            }
        }
    }
}

@Composable
private fun GalleryList(
    onUsePrompt: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lessons = remember { TutorialData.load(ctx) }
    var expanded by remember { mutableStateOf<Int?>(null) }
    if (lessons.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "課程資料載入失敗",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lessons.size) { i ->
            val lesson = lessons[i]
            LessonCard(
                lesson = lesson,
                isOpen = expanded == lesson.sec,
                onToggle = { expanded = if (expanded == lesson.sec) null else lesson.sec },
                onCopyPrompt = { p -> Clipboard.copy(ctx, p, toastMsg = "已複製提示詞") },
                onUsePrompt = { p -> onUsePrompt(p, false) },
            )
        }
    }
}

@Composable
private fun LessonCard(
    lesson: TutorialLesson,
    isOpen: Boolean,
    onToggle: () -> Unit,
    onCopyPrompt: (String) -> Unit,
    onUsePrompt: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "第${lesson.sec}節",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = lesson.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${lesson.images.size} 範例圖 · ${lesson.prompts.size} 提示詞",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ImagineIcon(
                name = if (isOpen) "expand_less" else "expand_more",
                size = 22.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isOpen) {
            if (lesson.images.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    lesson.images.forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = lesson.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
            }

            if (lesson.prompts.isNotEmpty()) {
                Text(
                    text = "原始提示詞片段（含說明，僅供參考取用）",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                lesson.prompts.forEach { p ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = p,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextActionButton(
                                label = "複製",
                                icon = "content_copy",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { onCopyPrompt(p) },
                            )
                            TextActionButton(
                                label = "使用(生圖)",
                                icon = "check",
                                onClick = { onUsePrompt(p) },
                            )
                        }
                    }
                }
            }
        }
    }
}
