package com.za869765.imagine.ui.hub

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.MaterialLibrary
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.storage.MediaImporter
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import kotlinx.coroutines.launch

// 素材庫 — 把生成/匯入的圖依 角色/環境/物件/風格 分類收藏,點圖直接當圖生圖/圖生影的參考。
// onUseImage(uri, asVideo): false → 編輯/圖生圖(EDIT image),true → 圖生影(GENERATE_VIDEO);沿用 KEY_INIT_MEDIA。
@OptIn(ExperimentalFoundationApi::class)
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
    var preview by remember { mutableStateOf<MediaEntry?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        entries = MediaHistory.loadAll(ctx)
        tagged = MaterialLibrary.all(ctx)
        loaded = true
    }

    // 從安卓相簿匯入照片 → 拷貝進 filesDir/media → 標記為目前分類。
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
    val counts = remember(tagged, imageNames) {
        MaterialLibrary.CATEGORIES.associateWith { c ->
            tagged.count { it.value == c && it.key in imageNames }
        }
    }
    val shown = remember(cat, tagged, entries) {
        entries.filter { !it.isVideo && tagged[it.displayName] == cat }
    }

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
            // 匯入鈕 — 一律可見,匯入進「目前分類」。
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
                text = "點圖 → 當圖生圖／圖生影的參考。在「歷史」點任一張圖也能設定分類。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )

            if (loaded && shown.isEmpty()) {
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
                    items(items = shown, key = { it.uri.toString() }) { entry ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { preview = entry },
                        ) {
                            AsyncImage(
                                model = entry.uri,
                                contentDescription = entry.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    val e = preview
    if (e != null) {
        MaterialPreviewDialog(
            entry = e,
            currentCat = cat,
            onUseImage = { asVideo -> onUseImage(e.uri.toString(), asVideo); preview = null },
            onRecategorize = { newCat ->
                MaterialLibrary.setCategory(ctx, e.displayName, newCat)
                Toast.makeText(ctx, "已改分類到「$newCat」", Toast.LENGTH_SHORT).show()
                reloadKey++
                preview = null
            },
            onRemove = {
                MaterialLibrary.remove(ctx, e.displayName)
                Toast.makeText(ctx, "已移出素材庫", Toast.LENGTH_SHORT).show()
                reloadKey++
                preview = null
            },
            onDismiss = { preview = null },
        )
    }
}

@Composable
private fun MaterialPreviewDialog(
    entry: MediaEntry,
    currentCat: String,
    onUseImage: (Boolean) -> Unit,
    onRecategorize: (String) -> Unit,
    onRemove: () -> Unit,
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
                model = entry.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 420.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Text(
                text = "用這張素材做什麼？",
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            ActionRow(icon = "edit", label = "用這張生圖（編輯／重繪）") { onUseImage(false) }
            ActionRow(icon = "movie", label = "用這張生影（圖生影）") { onUseImage(true) }

            Text(
                text = "改分類",
                fontSize = 12.sp,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MaterialLibrary.CATEGORIES.forEach { c ->
                    CatChip(label = c, selected = c == currentCat) { onRecategorize(c) }
                }
            }
            ActionRow(icon = "close", label = "移出素材庫") { onRemove() }
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
