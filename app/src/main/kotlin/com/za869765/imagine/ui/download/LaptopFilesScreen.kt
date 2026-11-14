package com.za869765.imagine.ui.download

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.download.Downloader
import com.za869765.imagine.data.download.Downloader.Status
import com.za869765.imagine.data.h3.H3LaptopFile
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VIDEO_COLOR = Color(0xFF5B3FA8)
private val IMAGE_COLOR = Color(0xFF1E7A4C)

@Composable
private fun thumbOf(f: H3LaptopFile): ImageBitmap? = remember(f.thumb) {
    runCatching {
        val b = android.util.Base64.decode(f.thumb, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
    }.getOrNull()
}

@Composable
private fun KindTag(f: H3LaptopFile) {
    val isVideo = f.kind != "image"
    Text(
        if (isVideo) "影片" + (if (f.dur > 0) "・%.0f 秒".format(f.dur) else "") else "圖片",
        fontSize = 11.sp, fontWeight = FontWeight.W700, color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (isVideo) VIDEO_COLOR else IMAGE_COLOR).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** 一個檔的按鈕:抓／看／取消,加上刪除 */
@Composable
private fun FileActions(
    f: H3LaptopFile, t: Downloader.Task?, wasFetched: Boolean, isDeleting: Boolean, compact: Boolean,
    onFetch: () -> Unit, onOpen: (String) -> Unit, onCancel: (String) -> Unit, onDelete: () -> Unit,
) {
    if (t != null && t.status == Status.RUNNING) {
        TextButton(onClick = { onCancel(t.id) }) { Text("取消") }
    } else if (t != null && t.status == Status.DONE) {
        TextButton(onClick = { t.savedUri?.let(onOpen) }) { Text(if (f.kind == "image") "看圖片" else "看影片") }
    } else {
        TextButton(onClick = onFetch) { Text(if (wasFetched) "再抓" else if (compact) "抓" else "抓到手機") }
    }
    if (t == null || t.status != Status.RUNNING) {
        TextButton(onClick = onDelete, enabled = !isDeleting) {
            Text(if (isDeleting) "刪除中" else "刪除", color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * v3.3.3 筆電測試片分頁:列出筆電測試輸出資料夾裡的影片與圖片(新到舊),點「抓到手機」才由筆電經 Cloudflare 暫存傳過來,
 * 存進所有作品後暫存就刪掉(不經雲端硬碟)。
 * v3.4.1 清單存在手機(開分頁立刻顯示);全部／影片／圖片／未傳分類;關鍵字搜尋;清單、格狀、精簡三種檢視。
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
    val recipeBusy by Downloader.laptopRecipeBusy.collectAsState()
    val loading by Downloader.laptopLoading.collectAsState()
    val error by Downloader.laptopError.collectAsState()
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    // v3.5.0 要保留的那一天(非 null＝正在問要不要刪掉手機上其他日期的測試片)
    var confirmPurge by remember { mutableStateOf<Pair<String, Set<String>>?>(null) }
    // 0 全部、1 影片、2 圖片、3 未傳
    var kind by rememberSaveable { mutableIntStateOf(0) }
    // 0 清單、1 格狀、2 精簡
    var view by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var shown by rememberSaveable { mutableIntStateOf(60) }
    // 日期:""＝自動(最新一天);"*"＝全部;其他＝指定那一天
    var day by rememberSaveable { mutableStateOf("") }

    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("刪除筆電上的這個檔？") },
            text = { Text("$name\n\n會從筆電的測試資料夾永久刪除（釋出空間、清單不再出現），刪了救不回來。手機上已經存好的那份不受影響。") },
            confirmButton = { TextButton(onClick = { Downloader.deleteLaptop(ctx, name); confirmDelete = null }) { Text("刪除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
    confirmPurge?.let { (keepDay, keep) ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("刪掉手機上 $keepDay 以外的測試片？") },
            text = { Text("只刪手機裡存的那一份來釋放空間，$keepDay 這天的 ${keep.size} 個會留著。\n\n桌機／筆電上的檔案不會動，刪掉的會變回「未傳」，之後隨時可以再抓。你自己生成的作品不受影響。") },
            confirmButton = {
                TextButton(onClick = {
                    val (n, bytes) = Downloader.purgeLaptopExcept(ctx, keep)
                    confirmPurge = null
                    android.widget.Toast.makeText(ctx, if (n == 0) "手機上沒有其他日期的測試片" else "已刪除 $n 個，釋放 %.0f MB".format(bytes / 1e6), android.widget.Toast.LENGTH_LONG).show()
                }) { Text("刪除") }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text("取消") } },
        )
    }
    // 進分頁:先顯示手機上存的清單,再到筆電補最新的(只要一頁,幾秒)
    LaunchedEffect(Unit) {
        Downloader.initLaptop(ctx)
        Downloader.reloadLaptop(ctx)
    }

    fun taskOf(f: H3LaptopFile): Downloader.Task? = tasks.firstOrNull { it.url == Downloader.laptopUrl(f.name) }
    fun isDone(f: H3LaptopFile): Boolean {
        val t = taskOf(f)
        return f.base in fetched || (t != null && (t.status == Status.RUNNING || t.status == Status.DONE))
    }
    fun fetch(f: H3LaptopFile) {
        val t = taskOf(f)
        if (t != null) Downloader.remove(t.id)
        Downloader.start(ctx, Downloader.laptopUrl(f.name), audio = false, height = 1080, laptopName = f.name)
    }
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN) }
    fun statusText(f: H3LaptopFile): Pair<String, Boolean> {
        val t = taskOf(f)
        return when {
            t == null || t.status == Status.CANCELLED -> Pair((if (f.base in fetched) "已傳過・" else "") + "%.1f MB・%s".format(f.bytes / 1e6, fmt.format(Date(f.mtime))), false)
            t.status == Status.RUNNING -> Pair(t.stage + if (t.progress > 0f) " ${t.progress.toInt()}%" else "", false)
            t.status == Status.DONE -> Pair("已存進所有作品", false)
            else -> Pair("失敗：${t.error}", true)
        }
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "測試片", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.LAPTOP, onTabSelected = onNavSelected) },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val everything = files.orEmpty()
            val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            // 依日期分:預設只看最新一天;有打搜尋字就跨所有日期找
            fun dayOf(f: H3LaptopFile): String = f.day.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.TAIWAN).format(Date(f.mtime)) }
            val days = everything.map { dayOf(it) }.distinct().sortedDescending()
            val curDay = if (day == "") days.firstOrNull() ?: "*" else day
            val searching = words.isNotEmpty()
            val all = if (searching || curDay == "*") everything else everything.filter { dayOf(it) == curDay }
            val list = all.filter { f ->
                val kindOk = when (kind) {
                    1 -> f.kind != "image"
                    2 -> f.kind == "image"
                    3 -> !isDone(f)
                    else -> true
                }
                kindOk && words.all { w -> f.base.lowercase().contains(w) }
            }
            val nVideo = all.count { it.kind != "image" }
            val nTodo = all.count { !isDone(it) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { Downloader.reloadLaptop(ctx, full = true) }, enabled = !loading) { Text(if (loading) "讀取中…" else "整份重讀") }
                // v3.4.9 兩台各有自己的測試片資料夾:按一下輪流切換 自動 → 桌機 → 筆電
                val source = Downloader.laptopSource.collectAsState().value
                OutlinedButton(onClick = { Downloader.setLaptopSource(ctx, when (source) { "" -> "desk"; "desk" -> "local"; else -> "" }) }, enabled = !loading) {
                    Text("來源：" + when (source) { "desk" -> "桌機"; "local" -> "筆電"; else -> "自動" })
                }
                Text(
                    if (files == null) "第一次讀取，要等一下" else "手機上有 ${everything.size}／$total 個" + if (loading) "，更新中" else "",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it; shown = 60 },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜尋檔名（跨所有日期；空格隔開＝都要有）") },
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } },
            )
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (searching) "日期（搜尋中＝全部）" else "日期", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                days.take(14).forEach { d ->
                    FilterChip(selected = !searching && curDay == d, onClick = { day = d; shown = 60 }, label = { Text(d.drop(5) + " (" + everything.count { dayOf(it) == d } + ")") })
                }
                FilterChip(selected = searching || curDay == "*", onClick = { day = "*"; shown = 60 }, label = { Text("全部 ${everything.size}") })
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("不分 ${all.size}", "影片 $nVideo", "圖片 ${all.size - nVideo}", "未傳 $nTodo").forEachIndexed { i, label ->
                    FilterChip(selected = kind == i, onClick = { kind = i; shown = 60 }, label = { Text(label) })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("檢視", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("清單", "格狀大圖", "精簡").forEachIndexed { i, label ->
                    FilterChip(selected = view == i, onClick = { view = i }, label = { Text(label) })
                }
            }

            val todo = list.filter { !isDone(it) }
            Text(
                "符合的 ${list.size} 個裡：已傳 ${list.size - todo.size} 個、未傳 ${todo.size} 個",
                fontSize = 13.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface,
            )
            if (todo.isNotEmpty()) {
                OutlinedButton(onClick = { Downloader.startLaptopBatch(ctx, todo.map { it.name }) }, modifier = Modifier.fillMaxWidth()) {
                    // v3.5.0 寫明範圍:選了某一天就只抓那一天的;選「全部」或正在搜尋才是跨日期
                    val scope = if (searching) "搜尋到的" else if (curDay == "*") "全部日期" else curDay.drop(5) + " 這天"
                    Text("一鍵抓${scope}未傳的 ${todo.size} 個（共 %.0f MB，一個接一個）".format(todo.sumOf { it.bytes } / 1e6))
                }
            }
            if (!searching && curDay != "*" && everything.isNotEmpty()) {
                OutlinedButton(
                    onClick = { confirmPurge = curDay to everything.filter { dayOf(it) == curDay }.map { it.base }.toSet() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("刪掉手機上 ${curDay.drop(5)} 以外的測試片（釋放空間，之後可再抓）") }
            }

            val page = list.take(shown)
            if (view == 1) {
                // 格狀大圖:一排兩個,縮圖放大,下面檔名與按鈕
                page.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { f ->
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val bmp = thumbOf(f)
                                    Box(
                                        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                        contentAlignment = Alignment.TopStart,
                                    ) {
                                        if (bmp != null) {
                                            Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f))
                                        }
                                        Box(modifier = Modifier.padding(4.dp)) { KindTag(f) }
                                    }
                                    Text(f.base, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    val st = statusText(f)
                                    Text(st.first, fontSize = 11.sp, color = if (st.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row {
                                        FileActions(f, taskOf(f), f.base in fetched, f.name in deleting, true,
                                            onFetch = { fetch(f) }, onOpen = onOpenVideo, onCancel = { id -> Downloader.cancel(ctx, id) }, onDelete = { confirmDelete = f.name })
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
            } else if (view == 2) {
                // 精簡:一行一個,不放縮圖,一眼看最多檔名
                page.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(if (f.kind != "image") VIDEO_COLOR else IMAGE_COLOR))
                        Text(
                            (if (f.base in fetched) "✓ " else "") + f.base,
                            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        FileActions(f, taskOf(f), f.base in fetched, f.name in deleting, true,
                            onFetch = { fetch(f) }, onOpen = onOpenVideo, onCancel = { id -> Downloader.cancel(ctx, id) }, onDelete = { confirmDelete = f.name })
                    }
                }
            } else {
                // 清單:縮圖＋標籤＋檔名＋狀態
                page.forEach { f ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val bmp = thumbOf(f)
                            Box(
                                modifier = Modifier.size(width = 54.dp, height = 96.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (bmp != null) {
                                    Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 54.dp, height = 96.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                KindTag(f)
                                Text(f.base, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val st = statusText(f)
                                Text(st.first, fontSize = 12.sp, color = if (st.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                // v3.5.1 有配方的片:抓到手機後可以加進最近成品,在那裡再生成一次／調整後再生成／往後續做
                                if (f.recipe && f.base in fetched) {
                                    TextButton(
                                        onClick = { Downloader.addRecipeToResults(ctx, f) { m -> android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_LONG).show() } },
                                        enabled = f.name !in recipeBusy,
                                    ) { Text(if (f.name in recipeBusy) "加入中…" else "加入最近成品（再做／微調／續做）", fontSize = 12.sp) }
                                } else if (f.recipe) {
                                    Text("有配方：抓到手機後可加入最近成品再做", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                val t = taskOf(f)
                                if (t != null && t.status == Status.RUNNING) {
                                    if (t.progress > 0f) {
                                        LinearProgressIndicator(progress = { t.progress / 100f }, modifier = Modifier.fillMaxWidth())
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                FileActions(f, taskOf(f), f.base in fetched, f.name in deleting, false,
                                    onFetch = { fetch(f) }, onOpen = onOpenVideo, onCancel = { id -> Downloader.cancel(ctx, id) }, onDelete = { confirmDelete = f.name })
                            }
                        }
                    }
                }
            }
            if (list.isEmpty() && files != null && !loading) Text("沒有符合的檔案", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (list.size > shown) {
                OutlinedButton(onClick = { shown += 60 }, modifier = Modifier.fillMaxWidth()) { Text("再顯示 60 個（還有 ${list.size - shown} 個）") }
            }
            Text(
                "清單會存在手機裡，開分頁立刻顯示，再自動向筆電補上新檔。筆電上刪過檔或改過名，按「整份重讀」。標「有配方」的片（清單檢視才看得到按鈕）抓到手機後可以加進「最近成品」，在那裡再生成、微調或往後續做。" +
                    "點「抓到手機」才由筆電經 Cloudflare 暫存傳過來，存進「所有作品」後暫存就刪掉，不經雲端硬碟。筆電要開著、工人要在跑。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
