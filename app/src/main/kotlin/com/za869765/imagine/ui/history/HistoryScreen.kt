package com.za869765.imagine.ui.history

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.MaterialLibrary
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.storage.PromptIndex
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.component.AppNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

// Kept for HistoryDetailScreen — eventually replace with MediaEntry navigation.
data class HistoryItem(
    val id: String,
    val date: String,
    val isVideo: Boolean = false,
    val duration: String? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onItemClick: (HistoryItem) -> Unit,
    // UI_REDESIGN_PLAN 3.2:所有作品 ↔ 素材庫 互相前往
    onOpenLibrary: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var characters by remember { mutableStateOf<Set<String>>(emptySet()) }
    // B8 多選刪除
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        entries = MediaHistory.loadAll(ctx)
        characters = MaterialLibrary.all(ctx).keys.toSet()
        loaded = true
    }

    val q = query.trim()
    val items = entries.filter { e ->
        val byFilter = when (filter) {
            "img" -> !e.isVideo
            "vid" -> e.isVideo
            "char" -> !e.isVideo && e.displayName in characters
            else -> true
        }
        val byQuery = q.isEmpty() || (e.prompt?.contains(q, true) == true)
        byFilter && byQuery
    }
    val grouped = items.groupBy { formatDate(it.addedAtSec) }
    val imgCount = entries.count { !it.isVideo }
    val vidCount = entries.count { it.isVideo }
    val charCount = entries.count { !it.isVideo && it.displayName in characters }
    // 修:選取只計入「目前可見」的項目,避免切 filter/搜尋後刪到看不見的檔(資料遺失)
    val visibleSelected = selected.intersect(items.map { it.uri.toString() }.toSet())

    fun exitSelect() { selectMode = false; selected = emptySet() }
    fun deleteSelected() {
        val toDel = visibleSelected
        scope.launch {
            withContext(Dispatchers.IO) {
                toDel.forEach { uriStr ->
                    val e = entries.firstOrNull { it.uri.toString() == uriStr } ?: return@forEach
                    runCatching {
                        e.uri.path?.let { java.io.File(it).delete() }
                        PromptIndex.remove(ctx, e.displayName)
                        MaterialLibrary.remove(ctx, e.displayName)
                    }
                }
            }
            exitSelect()
            reloadKey++
        }
    }

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                // 「歷史」→「所有作品」(UI_REDESIGN_PLAN 3.2):含成功作品;與「素材庫」(可重複使用的參考圖)區分
                title = if (selectMode) "已選 ${visibleSelected.size}" else "所有作品",
                showBack = true,
                onBackClick = { if (selectMode) exitSelect() else onBack() },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!selectMode) {
                            TextActionButton(label = "素材庫", icon = "photo_library", onClick = onOpenLibrary)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { if (selectMode) exitSelect() else selectMode = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = if (selectMode) "完成" else "選取",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
        scroll = false,
        bottomNav = null,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                placeholder = { Text("搜尋 prompt 找圖／影片") },
                leadingIcon = { ImagineIcon(name = "search", size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SegmentedTab(
                options = listOf(
                    SegmentedOption("all", "全部 ${entries.size}"),
                    SegmentedOption("img", "圖片 $imgCount"),
                    SegmentedOption("vid", "影片 $vidCount"),
                    SegmentedOption("char", "素材庫 $charCount"),
                ),
                activeId = filter,
                onSelected = {
                    // 切換篩選即清空選取並提示(UI_REDESIGN_PLAN 0.5):畫面數量與實際刪除範圍一致
                    if (selectMode && selected.isNotEmpty() && it != filter) {
                        selected = emptySet()
                        AppNotice.show("已清除選取")
                    }
                    filter = it
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )

            if (selectMode && visibleSelected.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .clickable { confirmDelete = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ImagineIcon(name = "delete", size = 18.dp, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        text = "刪除已選 ${visibleSelected.size} 項（釋放空間）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W700,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            if (loaded && items.isEmpty()) {
                // 搜尋/篩選無結果 與 全空 分開(UI_REDESIGN_PLAN 6.5)
                EmptyState(
                    filtered = q.isNotEmpty() || filter != "all",
                    onClearFilters = { query = ""; filter = "all" },
                )
                return@Column
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                grouped.forEach { (date, list) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = date,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.04.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                        )
                    }
                    items(items = list, key = { it.uri.toString() }) { entry ->
                        val key = entry.uri.toString()
                        HistoryThumbnail(
                            entry = entry,
                            isCharacter = entry.displayName in characters,
                            selectMode = selectMode,
                            isSelected = key in selected,
                            onClick = {
                                if (selectMode) {
                                    selected = if (key in selected) selected - key else selected + key
                                } else {
                                    onItemClick(
                                        HistoryItem(
                                            id = key,
                                            date = date,
                                            isVideo = entry.isVideo,
                                            duration = entry.durationMs?.let { formatDuration(it) },
                                        ),
                                    )
                                }
                            },
                            // 多選模式下長按不再變成「複製提示詞」(UI_REDESIGN_PLAN 0.5):與點擊一樣切換選取
                            onLongClick = {
                                if (!selectMode) {
                                    selectMode = true
                                    selected = setOf(key)
                                } else {
                                    selected = if (key in selected) selected - key else selected + key
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            // 永久刪除(與「移出素材庫」的可復原動作明顯區分,UI_REDESIGN_PLAN 2.4)
            title = { Text("永久刪除 ${visibleSelected.size} 個檔案？") },
            text = { Text("會永久刪除這些圖片／影片檔（無法復原），釋放儲存空間。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; deleteSelected() }) {
                    Text("永久刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryThumbnail(
    entry: MediaEntry,
    isCharacter: Boolean,
    selectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        // B7: 影片用首格縮圖(Coil 不能解影片);圖片用 AsyncImage。
        if (entry.isVideo) {
            VideoThumb(uri = entry.uri)
        } else {
            AsyncImage(
                model = entry.uri,
                contentDescription = entry.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 「已加入素材庫」標記(UI_REDESIGN_PLAN 3.2):圖示 + 文字,不再用 ⭐ 以免與範本收藏混淆
        if (isCharacter) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                ImagineIcon(name = "photo_library", size = 11.dp, fill = 1, tint = Color(0xFFEC8BD2))
                Text(text = "素材庫", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.W600)
            }
        }
        if (!entry.prompt.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    entry.prompt,
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                ImagineIcon(name = "play_arrow", size = 20.dp, fill = 1, tint = Color.White)
            }
            entry.durationMs?.let { ms ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        formatDuration(ms),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.W500,
                    )
                }
            }
        }
        // B8: 多選模式 — 暗化 + 勾選圈
        if (selectMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
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
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.4f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) ImagineIcon(name = "check", size = 16.dp, tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoThumb(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeFirstFrame(ctx, uri)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
    }
}

private fun decodeFirstFrame(ctx: Context, uri: Uri): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        val path = if (uri.scheme == "file") uri.path else null
        if (path != null) r.setDataSource(path) else r.setDataSource(ctx, uri)
        r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { r.release() }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (filtered) "找不到符合條件的作品" else "還沒有作品",
            fontSize = 16.sp,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            if (filtered) "換個關鍵字，或清除搜尋與篩選" else "完成生成後，作品會出現在這裡",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (filtered) {
            TextActionButton(label = "清除搜尋與篩選", icon = "close", onClick = onClearFilters)
        }
    }
}

private fun formatDate(epochSec: Long): String =
    Instant.ofEpochSecond(epochSec).atZone(ZoneId.systemDefault()).toLocalDate().toString()

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
