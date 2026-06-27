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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.tutorial.TutorialData
import com.za869765.imagine.data.tutorial.TutorialLesson
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.InlineVideoPlayer
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.PromptExample
import com.za869765.imagine.ui.component.READY_PROMPTS
import com.za869765.imagine.ui.component.ReadyPromptCard
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.component.usageOf
import com.za869765.imagine.ui.util.Clipboard

// 範本分類 (依 tag 歸類,不動 PromptExample 資料)。
private val PEOPLE_TAGS = setOf(
    "電影級真實人像", "情緒敘事肖像", "古裝人物", "現代人像", "室內人物", "多人物",
    "優雅長者", "親子日常", "黑白人像", "時尚雜誌", "職場專業", "校園青春", "情侶雙人", "音樂現場",
)
private val SCENE_TAGS = setOf(
    "大透視環境", "室內自然光", "風景", "雪景", "夜市煙火", "旅遊風情", "海島度假", "雨天街景",
)

private fun categoryOf(ex: PromptExample): String = when {
    ex.category.isNotEmpty() -> ex.category
    ex.forVideo == true || ex.tag.contains("影片") -> "影片"
    ex.tag in PEOPLE_TAGS -> "人物"
    ex.tag in SCENE_TAGS -> "場景"
    else -> "主題"
}

private val CATEGORIES = listOf("全部", "★ 收藏", "古裝", "現代")

// 教學範本頁 (底部第3分頁):搜尋 + 精選範本(分類/收藏/複製/使用→生成) + 課程圖庫(圖→生成、影片範例、prompt 複製)。
// onUsePrompt(prompt, usage) usage=t2i/t2v/i2v;i2v 會讓影片頁進圖生影模式。onUseImage(url, asVideo) 由 NavHost 接。
@Composable
fun TutorialScreen(
    onUsePrompt: (String, String) -> Unit,
    onUseImage: (String, Boolean) -> Unit,
    onUseVideo: (String, String) -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    var tab by rememberSaveable { mutableStateOf("ready") }
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by remember { mutableStateOf(prefs.favoriteTemplates.toSet()) }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "教學範本", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.TUTORIAL, onTabSelected = onNavSelected) },
        scroll = false,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("搜尋範本 / 課程 / 提示詞") },
                leadingIcon = {
                    ImagineIcon(name = "search", size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SegmentedTab(
                options = listOf(
                    SegmentedOption("ready", "精選範本"),
                    SegmentedOption("gallery", "課程圖庫"),
                ),
                activeId = tab,
                onSelected = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Text(
                text = "點「複製」或「使用→生成」直接開始;課程圖庫點圖可動起來/重繪。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (tab == "ready") {
                ReadyList(
                    query = query,
                    favorites = favorites,
                    onToggleFavorite = { t ->
                        favorites = if (t in favorites) favorites - t else favorites + t
                        prefs.favoriteTemplates = favorites.toList()
                    },
                    onUsePrompt = onUsePrompt,
                    modifier = Modifier.weight(1f),
                )
            } else {
                GalleryList(
                    query = query,
                    onUsePrompt = onUsePrompt,
                    onUseImage = onUseImage,
                    onUseVideo = onUseVideo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReadyList(
    query: String,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onUsePrompt: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var mode by rememberSaveable { mutableStateOf("t2i") } // 文生圖 t2i / 文生影 t2v / 圖生影 i2v 分開選
    var cat by rememberSaveable { mutableStateOf("全部") }
    val q = query.trim()
    val filtered = remember(q, cat, favorites, mode) {
        READY_PROMPTS.filter { ex ->
            val matchMode = usageOf(ex) == mode
            val matchQ = q.isEmpty() || ex.tag.contains(q, true) || ex.text.contains(q, true)
            val matchCat = when (cat) {
                "全部" -> true
                "★ 收藏" -> ex.tag in favorites
                else -> categoryOf(ex) == cat
            }
            matchMode && matchQ && matchCat
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        SegmentedTab(
            options = listOf(
                SegmentedOption("t2i", "文生圖"),
                SegmentedOption("t2v", "文生影"),
                SegmentedOption("i2v", "圖生影"),
            ),
            activeId = mode,
            onSelected = { mode = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CATEGORIES.forEach { c ->
                CatChip(label = c, selected = cat == c) { cat = c }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "沒有符合的範本，換個關鍵字或分類試試。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(filtered.size) { i ->
                val ex = filtered[i]
                ReadyPromptCard(
                    ex = ex,
                    onCopy = { Clipboard.copy(ctx, ex.text, toastMsg = "已複製提示詞") },
                    onUse = { onUsePrompt(ex.text, usageOf(ex)) },
                    isFavorite = ex.tag in favorites,
                    onToggleFavorite = { onToggleFavorite(ex.tag) },
                )
            }
        }
    }
}

@Composable
private fun GalleryList(
    query: String,
    onUsePrompt: (String, String) -> Unit,
    onUseImage: (String, Boolean) -> Unit,
    onUseVideo: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val lessons = remember { TutorialData.load(ctx) }
    val q = query.trim()
    val filtered = remember(q) {
        if (q.isEmpty()) lessons else lessons.filter { it.title.contains(q, true) }
    }
    var expanded by remember { mutableStateOf<Int?>(null) }
    var playingVideo by remember { mutableStateOf<String?>(null) }
    var pendingImage by remember { mutableStateOf<String?>(null) }

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
        if (filtered.isEmpty()) {
            item {
                Text(
                    text = "沒有符合的課程，換個關鍵字試試。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(filtered.size) { i ->
            val lesson = filtered[i]
            LessonCard(
                lesson = lesson,
                isOpen = expanded == lesson.sec,
                playingVideo = playingVideo,
                onToggle = {
                    expanded = if (expanded == lesson.sec) null else lesson.sec
                    playingVideo = null
                },
                onImageTap = { pendingImage = it },
                onPlayVideo = { playingVideo = it },
                onUseVideo = onUseVideo,
                onCopyPrompt = { p -> Clipboard.copy(ctx, p, toastMsg = "已複製提示詞") },
                onUsePrompt = { p -> onUsePrompt(p, "t2i") },
            )
        }
    }

    // 圖片：先進預覽 (放大看圖) → 再選 重繪 / 動起來。
    val img = pendingImage
    if (img != null) {
        ImagePreviewDialog(
            url = img,
            onAnimate = { onUseImage(img, true); pendingImage = null },
            onEdit = { onUseImage(img, false); pendingImage = null },
            onDismiss = { pendingImage = null },
        )
    }
}

// 範例圖預覽 — 放大看圖,下方選「重繪/編輯」或「動起來(圖生影)」。
@Composable
private fun ImagePreviewDialog(
    url: String,
    onAnimate: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Text(
                text = "用這張範例圖做什麼？",
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            ActionRow(icon = "edit", label = "重繪／編輯（以此圖為來源）", onClick = onEdit)
            ActionRow(icon = "play_arrow", label = "動起來（以此圖生成影片）", onClick = onAnimate)
        }
    }
}

@Composable
private fun CatChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionRow(icon: String, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ImagineIcon(name = icon, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LessonCard(
    lesson: TutorialLesson,
    isOpen: Boolean,
    playingVideo: String?,
    onToggle: () -> Unit,
    onImageTap: (String) -> Unit,
    onPlayVideo: (String) -> Unit,
    onUseVideo: (String, String) -> Unit,
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
                    text = "${lesson.images.size} 圖 · ${lesson.videos.size} 影片 · ${lesson.prompts.size} 提示詞",
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
                Text(
                    text = "範例圖（點圖→動起來/重繪）",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
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
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onImageTap(url) },
                        )
                    }
                }
            }

            if (lesson.videos.isNotEmpty()) {
                Text(
                    text = "示範影片",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                lesson.videos.forEach { url ->
                    if (playingVideo == url) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            InlineVideoPlayer(
                                url = url,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                            )
                            ActionRow(icon = "edit", label = "影片修改（以此影片重繪／影生影）") {
                                onUseVideo(url, "video")
                            }
                            ActionRow(icon = "play_arrow", label = "影片延長（接續這支影片）") {
                                onUseVideo(url, "extend")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onPlayVideo(url) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImagineIcon(name = "play_arrow", size = 20.dp, fill = 1, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "播放範例影片",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
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
