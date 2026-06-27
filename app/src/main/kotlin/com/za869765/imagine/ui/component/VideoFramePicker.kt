package com.za869765.imagine.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.window.Dialog
import com.za869765.imagine.data.storage.MaterialLibrary
import com.za869765.imagine.data.storage.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// 影片畫格擷取器 — 拉桿選任一秒的畫格,可「用此格圖生影」(重做)或「存為角色(數字人)」放進素材庫角色。
// 置中卡片式 Dialog(按鈕不在螢幕底,避開手勢列)。onUseFrameForVideo(frameFileUri, prompt)。
@Composable
fun VideoFramePicker(
    uri: Uri,
    prompt: String,
    onUseFrameForVideo: (String, String) -> Unit,
    onCombineExtend: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val durationMs = remember(uri) { videoDurationMs(ctx, uri).coerceAtLeast(0L) }
    var posMs by remember { mutableStateOf(0L) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var working by remember { mutableStateOf(false) }

    // 拉桿停下 120ms 才解碼,避免每次拖曳都重解碼。
    LaunchedEffect(posMs, uri) {
        delay(120)
        frame = withContext(Dispatchers.IO) { decodeFrameAt(ctx, uri, posMs) }
    }

    suspend fun saveCurrentFrame(): String? {
        val f = frame ?: return null
        val bytes = withContext(Dispatchers.IO) {
            ByteArrayOutputStream().use { b -> f.compress(Bitmap.CompressFormat.JPEG, 92, b); b.toByteArray() }
        }
        return MediaSaver.saveImage(ctx, bytes, prompt)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
        ) {
            Text(
                text = "擷取畫格（數字人 / 來源圖）",
                fontSize = 15.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                val f = frame
                if (f != null) {
                    Image(
                        bitmap = f.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp), strokeWidth = 2.dp)
                }
            }
            if (durationMs > 0L) {
                Slider(
                    value = posMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { posMs = it.toLong() },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "第 ${"%.1f".format(posMs / 1000.0)} 秒 / ${"%.1f".format(durationMs / 1000.0)} 秒",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PickerAction(icon = "movie", label = "用此格圖生影（以此格當來源）", enabled = !working) {
                scope.launch {
                    working = true
                    val u = saveCurrentFrame()
                    working = false
                    if (u != null) {
                        onUseFrameForVideo(u, prompt)
                        onDismiss()
                    } else {
                        Toast.makeText(ctx, "擷取失敗，換個秒數試試", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            PickerAction(icon = "image", label = "存為角色素材（數字人，可重複使用）", enabled = !working) {
                scope.launch {
                    working = true
                    val u = saveCurrentFrame()
                    if (u != null) {
                        MaterialLibrary.setCategory(ctx, u.substringAfterLast('/'), MaterialLibrary.CHARACTER)
                        Toast.makeText(ctx, "已存為角色（數字人）— 素材庫「角色」分頁可找到", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "擷取失敗，換個秒數試試", Toast.LENGTH_SHORT).show()
                    }
                    working = false
                    onDismiss()
                }
            }
            PickerAction(icon = "add", label = "組合延長：接此片後（新片自動串接）", enabled = !working) {
                scope.launch {
                    working = true
                    val u = saveCurrentFrame()
                    working = false
                    if (u != null) {
                        onCombineExtend(u, uri.toString())
                        onDismiss()
                    } else {
                        Toast.makeText(ctx, "擷取失敗，換個秒數試試", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Text(
                text = "組合延長：拉到接近尾端的畫格最順；到影片頁輸入新 prompt 生成，完成會自動把原片＋新片串成長片。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PickerAction(icon: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ImagineIcon(
            name = icon,
            size = 20.dp,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
    }
}

private fun videoDurationMs(ctx: Context, uri: Uri): Long {
    val r = MediaMetadataRetriever()
    return try {
        val path = if (uri.scheme == "file") uri.path else null
        if (path != null) r.setDataSource(path) else r.setDataSource(ctx, uri)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {
        0L
    } finally {
        runCatching { r.release() }
    }
}

private fun decodeFrameAt(ctx: Context, uri: Uri, posMs: Long): Bitmap? {
    val r = MediaMetadataRetriever()
    return try {
        val path = if (uri.scheme == "file") uri.path else null
        if (path != null) r.setDataSource(path) else r.setDataSource(ctx, uri)
        r.getFrameAtTime(posMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { r.release() }
    }
}
