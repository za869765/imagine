package com.za869765.imagine.ui.hub

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.HiddenSeed
import com.za869765.imagine.data.storage.MaterialLibrary
import com.za869765.imagine.data.storage.MaterialSeed
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaExporter
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.storage.MediaImporter
import com.za869765.imagine.data.tutorial.TutorialData
import com.za869765.imagine.ui.component.FullscreenImageViewer
import com.za869765.imagine.ui.component.FullscreenVideoPlayer
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.ViewerAction
import kotlinx.coroutines.launch

// 素材庫 — 角色/環境/物件/風格 四分頁。每頁顯示「我的素材」(自己生成/匯入,可標分類) +
// 「內建課程素材」(由課程範例圖視覺分類而來的 CDN 圖,點圖直接當圖生圖/圖生影參考)。
private const val VIDEO_CAT = "影片" // 素材庫第 5 分頁:課程影片(UI 專屬,非 MaterialLibrary 圖片標記分類)

@Composable
fun MaterialLibraryScreen(
    onUseImage: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cat by rememberSaveable { mutableStateOf(MaterialLibrary.CHARACTER) }
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var tagged by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }   // 我的素材
    var seedIndex by remember { mutableStateOf<Int?>(null) }      // 內建素材
    var recatName by remember { mutableStateOf<String?>(null) }   // B4: 改分類目標(讓使用者選)
    var removeName by remember { mutableStateOf<String?>(null) }  // B4: 移出前確認
    var reloadKey by remember { mutableStateOf(0) }
    // 內建課程素材「長按進批次刪除」;刪除=HiddenSeed.hide,課程圖庫同步隱藏
    var seedSelect by remember { mutableStateOf(false) }
    var selectedSeeds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(reloadKey) {
        entries = MediaHistory.loadAll(ctx)
        tagged = MaterialLibrary.all(ctx)
        loaded = true
    }

    val seedAll = remember { MaterialSeed.load(ctx) }
    val seedCounts = remember(seedAll) {
        MaterialLibrary.CATEGORIES.associateWith { c -> seedAll.count { it.category == c } }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val saved = MediaImporter.importAll(ctx, uris)
                saved.forEach { MaterialLibrary.setCategory(ctx, it, cat) }
                Toast.makeText(ctx, "已匯入 ${saved.size} 張到「$cat」", Toast.LENGTH_SHORT).show()
                reloadKey++
            }
        }
    }
    fun pickFromAlbum() =
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // #2:影片分類 — 把課程圖庫的影片(CDN)全抓進素材庫,點播放。
    val courseVideos = remember {
        TutorialData.load(ctx).flatMap { lesson ->
            lesson.videos.mapIndexed { i, url ->
                url to (lesson.videoCaptions.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "範例影片")
            }
        }
    }
    var videoPlayUrl by remember { mutableStateOf<String?>(null) }

    val imageNames = remember(entries) { entries.filter { !it.isVideo }.map { it.displayName }.toSet() }
    val counts = remember(tagged, imageNames, seedCounts, courseVideos) {
        MaterialLibrary.CATEGORIES.associateWith { c ->
            tagged.count { it.value == c && it.key in imageNames } + (seedCounts[c] ?: 0)
        } + (VIDEO_CAT to courseVideos.size)
    }
    val shown = remember(cat, tagged, entries) {
        entries.filter { !it.isVideo && tagged[it.displayName] == cat }
    }
    val seedUrls = remember(cat, seedAll) { seedAll.filter { it.category == cat }.map { it.url } }
    val videoSeeds = remember(cat, courseVideos, reloadKey) {
        if (cat == VIDEO_CAT) courseVideos.filter { it.first !in HiddenSeed.all(ctx) } else emptyList()
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "素材庫", showBack = true, onBackClick = onBack, onSettingsClick = onSettingsClick) },
        showBalanceBar = false,
        bottomNav = null,
        scroll = false,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 分類改成可橫滑的計數膠囊(角色 12 / 環境 8 …),取代等寬分段控制
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (MaterialLibrary.CATEGORIES + VIDEO_CAT).forEach { c ->
                    CategoryCountPill(
                        label = c,
                        count = counts[c] ?: 0,
                        selected = cat == c,
                        onClick = { cat = c; seedSelect = false; selectedSeeds = emptySet() },
                    )
                }
            }
            // 匯入橫幅:紫調(設計稿);影片分頁不顯示(不從相簿匯入影片素材)
            if (cat != VIDEO_CAT) Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF211C30))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                    .clickable { pickFromAlbum() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                ImagineIcon(name = "add_photo_alternate", size = 21.dp, tint = Color(0xFFC9B8FF))
                Text(
                    text = "從相簿匯入到「$cat」",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = Color(0xFFC9B8FF),
                )
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = "最多 20 張",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "點圖放大看(雙指縮放/左右滑),底部可直接當圖生圖/圖生影。內建素材由課程範例圖自動分類;長按可批次移除(課程圖庫同步)。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )

            if (seedSelect) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            .clickable { seedSelect = false; selectedSeeds = emptySet() }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                    ) {
                        Text("取消", fontSize = 14.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selectedSeeds.isNotEmpty()) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable(enabled = selectedSeeds.isNotEmpty()) {
                                HiddenSeed.hide(ctx, selectedSeeds)
                                Toast.makeText(ctx, "已移除 ${selectedSeeds.size} 張,課程圖庫同步隱藏", Toast.LENGTH_SHORT).show()
                                selectedSeeds = emptySet()
                                seedSelect = false
                                reloadKey++
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ImagineIcon(
                            name = "delete",
                            size = 18.dp,
                            tint = if (selectedSeeds.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (selectedSeeds.isEmpty()) "點選要移除的內建素材"
                            else "刪除已選 ${selectedSeeds.size} 張（課程同步）",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W700,
                            color = if (selectedSeeds.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (loaded && shown.isEmpty() && seedUrls.isEmpty() && videoSeeds.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (cat == VIDEO_CAT) "課程影片載入中或無資料。"
                        else "「$cat」還沒有素材。\n從相簿匯入,或在生成結果／歷史把圖設為此分類。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (shown.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("我的素材 ${shown.size}") }
                        items(items = shown, key = { "me_" + it.uri.toString() }) { entry ->
                            GridCell(model = entry.uri) { previewIndex = shown.indexOf(entry) }
                        }
                    }
                    if (seedUrls.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("內建課程素材 ${seedUrls.size}") }
                        items(items = seedUrls, key = { "seed_$it" }) { url ->
                            GridCell(
                                model = url,
                                selectMode = seedSelect,
                                selected = url in selectedSeeds,
                                onLongClick = {
                                    seedSelect = true
                                    selectedSeeds = selectedSeeds + url
                                },
                            ) {
                                if (seedSelect) {
                                    selectedSeeds = if (url in selectedSeeds) selectedSeeds - url else selectedSeeds + url
                                } else {
                                    seedIndex = seedUrls.indexOf(url)
                                }
                            }
                        }
                    }
                    if (videoSeeds.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("課程影片 ${videoSeeds.size}") }
                        items(items = videoSeeds, key = { "vid_" + it.first }) { (url, caption) ->
                            VideoSeedCell(caption = caption) { videoPlayUrl = url }
                        }
                    }
                }
            }
        }
    }

    // 影片分類:點影片磚 → 全螢幕播放
    videoPlayUrl?.let { url ->
        FullscreenVideoPlayer(url = url, onDismiss = { videoPlayUrl = null })
    }

    // 我的素材檢視:生圖/生影/改分類/移出。
    val mi = previewIndex
    if (mi != null && mi in shown.indices) {
        val urls = shown.map { it.uri.toString() }
        FullscreenImageViewer(
            urls = urls,
            startIndex = mi,
            onDismiss = { previewIndex = null },
            actions = listOf(
                ViewerAction("image", "生圖") { url -> onUseImage(url, false); previewIndex = null },
                ViewerAction("movie", "生影") { url -> onUseImage(url, true); previewIndex = null },
                ViewerAction("download", "存相簿") { url ->
                    com.za869765.imagine.ImagineApp.appScope.launch {
                        val ok = MediaExporter.saveToGallery(ctx, url, isVideo = false)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(ctx, if (ok) "已存到相簿" else "存相簿失敗,改用分享試試", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                ViewerAction("sell", "改分類") { url ->
                    recatName = shown.firstOrNull { it.uri.toString() == url }?.displayName
                    previewIndex = null
                },
                ViewerAction("visibility_off", "移出素材庫", destructive = true) { url ->
                    removeName = shown.firstOrNull { it.uri.toString() == url }?.displayName
                    previewIndex = null
                },
            ),
        )
    }

    // 內建素材檢視:只給 生圖/生影(內建參考,不可移除/改分類)。
    val si = seedIndex
    if (si != null && si in seedUrls.indices) {
        FullscreenImageViewer(
            urls = seedUrls,
            startIndex = si,
            onDismiss = { seedIndex = null },
            actions = listOf(
                ViewerAction("image", "生圖") { url -> onUseImage(url, false); seedIndex = null },
                ViewerAction("movie", "生影") { url -> onUseImage(url, true); seedIndex = null },
                ViewerAction("download", "存相簿") { url ->
                    com.za869765.imagine.ImagineApp.appScope.launch {
                        val ok = MediaExporter.saveToGallery(ctx, url, isVideo = false)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(ctx, if (ok) "已存到相簿" else "存相簿失敗,改用分享試試", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ),
        )
    }

    // B4: 改分類 — 讓使用者選要改去哪一類(不再自動跳下一個)
    recatName?.let { name ->
        Dialog(onDismissRequest = { recatName = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(16.dp),
            ) {
                Text("改分類到", fontSize = 15.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
                MaterialLibrary.CATEGORIES.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                MaterialLibrary.setCategory(ctx, name, c)
                                Toast.makeText(ctx, "已改分類到「$c」", Toast.LENGTH_SHORT).show()
                                reloadKey++
                                recatName = null
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c, fontSize = 14.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
    // B4: 移出前先確認(只移出素材庫,不刪圖檔)
    removeName?.let { name ->
        AlertDialog(
            onDismissRequest = { removeName = null },
            title = { Text("移出素材庫？") },
            text = { Text("只會把這張從素材庫移出，不會刪除圖片（歷史仍在）。") },
            confirmButton = {
                TextButton(onClick = {
                    MaterialLibrary.remove(ctx, name)
                    Toast.makeText(ctx, "已移出素材庫", Toast.LENGTH_SHORT).show()
                    reloadKey++
                    removeName = null
                }) { Text("移出") }
            },
            dismissButton = { TextButton(onClick = { removeName = null }) { Text("取消") } },
        )
    }
}

// 分類計數膠囊:選中=主色紫淡底+紫框,計數用 mono。
@Composable
private fun CategoryCountPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(100.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "$count",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.07f)))
    }
}

// #2 課程影片磚:中央青色播放圈 + 底部字幕(開頭主題);點 → 全螢幕播放。CDN 影片不抓縮圖(省流量)。
@Composable
private fun VideoSeedCell(caption: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF56E0D2).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            ImagineIcon(name = "play_arrow", size = 24.dp, fill = 1, tint = Color(0xFF56E0D2))
        }
        Text(
            text = caption,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = Color.White,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    model: Any,
    selectMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (selectMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else Color.Black.copy(alpha = 0.15f),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.4f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) ImagineIcon(name = "check", size = 16.dp, tint = Color.White)
            }
        }
    }
}
