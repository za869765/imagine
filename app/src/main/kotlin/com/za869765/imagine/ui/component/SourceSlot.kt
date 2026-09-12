package com.za869765.imagine.ui.component

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SourceSlot — 生成/編輯頁的「來源」區塊(UI_REDESIGN_PLAN 1.3)。
 * 只在模式需要來源時由 caller 渲染;統一三種狀態:
 *   - 空:虛線框「選擇圖片／影片」(整塊可點 → onPick)
 *   - 已選(單張):縮圖 + 「更換」「移除」文字鈕(取代右上 ✕)
 *   - 已選(多張):縮圖列(每張 ✕)+ 「新增」文字鈕(未達上限時)
 * locked=true(組合延長尾格):不顯示任何更換/移除,只顯示 note。
 * extra:標題列右側附加內容(例如「帶入角色定妝圖」chip)。
 */
@Composable
fun SourceSlot(
    title: String,
    uris: List<Uri>,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    isVideo: Boolean = false,
    emptyLabel: String = if (isVideo) "選擇影片" else "選擇圖片",
    emptyHint: String = "點此從素材庫或相簿選取",
    maxCount: Int = 1,
    onRemove: ((Int) -> Unit)? = null,
    locked: Boolean = false,
    note: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionHeader(title)
            extra?.invoke()
        }
        if (uris.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable(onClick = onPick),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ImagineIcon(
                        name = "add_photo_alternate", size = 26.dp, fill = 1,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = emptyLabel,
                        fontSize = 15.sp, fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = emptyHint,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                uris.forEachIndexed { index, uri ->
                    Box(modifier = Modifier.size(96.dp)) {
                        SourceThumb(uri = uri, isVideo = isVideo, modifier = Modifier.size(96.dp))
                        // 多張時每張右上 ✕(觸控區 36dp,圖示 16dp);單張改用下方「移除」文字鈕
                        if (!locked && maxCount > 1 && onRemove != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                                    .clickable { onRemove(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ImagineIcon(name = "close", size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                if (!locked && maxCount > 1 && uris.size < maxCount) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .clickable(onClick = onPick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ImagineIcon(name = "add", size = 24.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("新增", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!locked && maxCount == 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextActionButton(label = "更換", icon = "refresh", onClick = onPick)
                    if (onRemove != null) {
                        TextActionButton(
                            label = "移除",
                            icon = "close",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onRemove(0) },
                        )
                    }
                }
            }
        }
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

// 來源縮圖:圖片走 Coil;影片用 MediaMetadataRetriever 抽第一格(IO thread),抽不到顯示灰底 + 影片圖示。
@Composable
fun SourceThumb(uri: Uri, isVideo: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val base = modifier
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    if (!isVideo) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
        return
    }
    val ctx = LocalContext.current
    var frame by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        frame = withContext(Dispatchers.IO) {
            val r = MediaMetadataRetriever()
            try {
                val path = if (uri.scheme == "file") uri.path else null
                if (path != null) r.setDataSource(path) else r.setDataSource(ctx, uri)
                r.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { r.release() }
            }
        }
    }
    Box(modifier = base, contentAlignment = Alignment.Center) {
        val f = frame
        if (f != null) {
            Image(
                bitmap = f.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp),
            )
        } else {
            ImagineIcon(name = "movie", size = 28.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
