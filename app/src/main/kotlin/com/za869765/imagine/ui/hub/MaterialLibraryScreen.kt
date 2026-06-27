package com.za869765.imagine.ui.hub

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.MaterialLibrary
import com.za869765.imagine.data.storage.MaterialSeed
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.storage.MediaImporter
import com.za869765.imagine.ui.component.FullscreenImageViewer
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.ViewerAction
import kotlinx.coroutines.launch

// 素材庫 — 角色/環境/物件/風格 四分頁。每頁顯示「我的素材」(自己生成/匯入,可標分類) +
// 「內建課程素材」(由課程範例圖視覺分類而來的 CDN 圖,點圖直接當圖生圖/圖生影參考)。
@Composable
fun MaterialLibraryScreen(
    onUseImage: (String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cat by rememberSaveable { mutableStateOf(MaterialLibrary.CHARACTER) }
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var tagged by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loaded by remember { mutableStateOf(false) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }   // 我的素材
    var seedIndex by remember { mutableStateOf<Int?>(null) }      // 內建素材
    var reloadKey by remember { mutableStateOf(0) }

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

    val imageNames = remember(entries) { entries.filter { !it.isVideo }.map { it.displayName }.toSet() }
    val counts = remember(tagged, imageNames, seedCounts) {
        MaterialLibrary.CATEGORIES.associateWith { c ->
            tagged.count { it.value == c && it.key in imageNames } + (seedCounts[c] ?: 0)
        }
    }
    val shown = remember(cat, tagged, entries) {
        entries.filter { !it.isVideo && tagged[it.displayName] == cat }
    }
    val seedUrls = remember(cat, seedAll) { seedAll.filter { it.category == cat }.map { it.url } }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "素材庫", showBack = true, onBackClick = onBack) },
        showBalanceBar = false,
        bottomNav = null,
        scroll = false,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SegmentedTab(
                options = MaterialLibrary.CATEGORIES.map { c -> SegmentedOption(c, "$c ${counts[c] ?: 0}") },
                activeId = cat,
                onSelected = { cat = it },
                modifier = Modifier.padding(16.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { pickFromAlbum() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ImagineIcon(name = "add_photo_alternate", size = 20.dp, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    text = "從相簿匯入到「$cat」",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text(
                text = "點圖放大看(雙指縮放/左右滑),底部可直接當圖生圖/圖生影。內建素材由課程範例圖自動分類。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )

            if (loaded && shown.isEmpty() && seedUrls.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "「$cat」還沒有素材。\n從相簿匯入,或在生成結果／歷史把圖設為此分類。",
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
                            GridCell(model = url) { seedIndex = seedUrls.indexOf(url) }
                        }
                    }
                }
            }
        }
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
                ViewerAction("refresh", "改分類") { url ->
                    shown.firstOrNull { it.uri.toString() == url }?.let { en ->
                        val cats = MaterialLibrary.CATEGORIES
                        val next = cats[(cats.indexOf(cat) + 1) % cats.size]
                        MaterialLibrary.setCategory(ctx, en.displayName, next)
                        Toast.makeText(ctx, "已改分類到「$next」", Toast.LENGTH_SHORT).show()
                        reloadKey++
                    }
                    previewIndex = null
                },
                ViewerAction("close", "移出") { url ->
                    shown.firstOrNull { it.uri.toString() == url }?.let { en ->
                        MaterialLibrary.remove(ctx, en.displayName)
                        Toast.makeText(ctx, "已移出素材庫", Toast.LENGTH_SHORT).show()
                        reloadKey++
                    }
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
            ),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.W700,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun GridCell(model: Any, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
