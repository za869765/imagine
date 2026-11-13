package com.za869765.imagine.ui.download

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.download.Downloader
import com.za869765.imagine.data.download.Downloader.Status
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v3.3.3 筆電測試片分頁:列出筆電測試輸出資料夾裡的影片(新到舊),點「抓到手機」才由筆電經 Cloudflare 暫存傳過來,
 * 存進所有作品後暫存就刪掉(不經雲端硬碟)。清單只有檔名、大小、時間,不分類也不標記。
 */
@Composable
fun LaptopFilesScreen(
    onNavSelected: (NavTab) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val tasks by Downloader.tasks.collectAsState()
    val files by Downloader.laptopList.collectAsState()
    val total by Downloader.laptopTotal.collectAsState()
    val fetched by Downloader.laptopFetched.collectAsState()
    val deleting by Downloader.laptopDeleting.collectAsState()
    var confirmDelete by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    confirmDelete?.let { name ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("刪除筆電上的這個檔？") },
            text = { Text("$name\n\n會從筆電的測試資料夾永久刪除（釋出空間、清單不再出現），刪了救不回來。手機上已經存好的那份不受影響。") },
            confirmButton = { TextButton(onClick = { Downloader.deleteLaptop(ctx, name); confirmDelete = null }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
    val loading by Downloader.laptopLoading.collectAsState()
    val error by Downloader.laptopError.collectAsState()

    fun reload(more: Boolean = false) = Downloader.reloadLaptop(ctx, more)
    LaunchedEffect(Unit) { Downloader.initLaptop(ctx); if (files == null) reload() }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "筆電測試片", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.LAPTOP, onTabSelected = onNavSelected) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { reload() }, enabled = !loading) { Text(if (loading) "讀取中…" else "重新整理") }
                files?.let { Text("共 $total 個，已列出 ${it.size} 個", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(
                "筆電測試輸出資料夾裡的影片與圖片，新的在上面，一次列 40 個。點「抓到手機」才會由筆電經 Cloudflare 暫存傳過來，存進「所有作品」後暫存就刪掉，不經雲端硬碟。" +
                    "筆電要開著、工人要在跑；筆電正在生影時也能抓。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            files?.let { list ->
                val todo = list.filter { f -> f.name !in fetched && tasks.none { it.url == Downloader.laptopUrl(f.name) && (it.status == Status.RUNNING || it.status == Status.DONE) } }
                Text("列出的 ${list.size} 個裡：已傳 ${list.size - todo.size} 個、未傳 ${todo.size} 個", fontSize = 13.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                if (todo.isNotEmpty()) {
                    OutlinedButton(onClick = { Downloader.startLaptopBatch(ctx, todo.map { it.name }) }, modifier = Modifier.fillMaxWidth()) {
                        Text("一鍵抓未傳的 ${todo.size} 個（共 %.0f MB，一個接一個）".format(todo.sumOf { it.bytes } / 1e6))
                    }
                }
            }
            error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }

            val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN) }
            files?.forEach { f ->
                val t = tasks.firstOrNull { it.url == Downloader.laptopUrl(f.name) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val isVideo = f.kind != "image"
                        val bmp = remember(f.thumb) {
                            runCatching {
                                val b = android.util.Base64.decode(f.thumb, android.util.Base64.DEFAULT)
                                android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
                            }.getOrNull()
                        }
                        Box(
                            modifier = Modifier.size(width = 54.dp, height = 96.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bmp != null) Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 54.dp, height = 96.dp))
                            else Text(if (isVideo) "影片" else "圖片", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            // 種類標籤:影片紫底帶秒數,圖片綠底
                            Text(
                                if (isVideo) "影片" + (if (f.dur > 0) "・%.0f 秒".format(f.dur) else "") else "圖片",
                                fontSize = 11.sp, fontWeight = FontWeight.W700, color = Color.White,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isVideo) Color(0xFF5B3FA8) else Color(0xFF1E7A4C)).padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                            Text(f.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val running = t != null && t.status == Status.RUNNING
                            val sub = when {
                                t == null || t.status == Status.CANCELLED -> (if (f.name in fetched) "已傳過・" else "") + "%.1f MB・%s".format(f.bytes / 1e6, fmt.format(Date(f.mtime)))
                                t.status == Status.RUNNING -> t.stage + if (t.progress > 0f) " ${t.progress.toInt()}%" else ""
                                t.status == Status.DONE -> "已存進所有作品"
                                else -> "失敗：${t.error}"
                            }
                            Text(
                                sub,
                                fontSize = 12.sp,
                                color = if (t != null && t.status == Status.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (t != null && running) {
                                if (t.progress > 0f) {
                                    LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth())
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (t != null && t.status == Status.RUNNING) {
                                TextButton(onClick = { Downloader.cancel(ctx, t.id) }) { Text("取消") }
                            } else if (t != null && t.status == Status.DONE) {
                                TextButton(onClick = { t.savedUri?.let(onOpenVideo) }) { Text(if (f.kind == "image") "看圖片" else "看影片") }
                            } else {
                                TextButton(onClick = {
                                    if (t != null) Downloader.remove(t.id)
                                    Downloader.start(ctx, Downloader.laptopUrl(f.name), audio = false, height = 1080, laptopName = f.name)
                                }) { Text(if (f.name in fetched) "再抓一次" else "抓到手機") }
                            }
                            if (t == null || t.status != Status.RUNNING) {
                                TextButton(onClick = { confirmDelete = f.name }, enabled = f.name !in deleting) {
                                    Text(if (f.name in deleting) "刪除中" else "刪除", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            files?.let {
                if (it.isEmpty() && !loading) Text("資料夾裡目前沒有檔案", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (it.size < total) OutlinedButton(onClick = { reload(more = true) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("再列出 40 個（還有 ${total - it.size} 個）") }
            }
        }
    }
}
