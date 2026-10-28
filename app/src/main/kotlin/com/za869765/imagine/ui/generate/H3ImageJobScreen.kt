package com.za869765.imagine.ui.generate

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3Format
import com.za869765.imagine.data.h3.H3ImageOut
import com.za869765.imagine.data.h3.H3ImageStatus
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.MediaExporter
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.ui.component.AppNotice
import com.za869765.imagine.ui.component.FullscreenImageViewer
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.MediaAction
import com.za869765.imagine.ui.component.MediaActionBar
import com.za869765.imagine.ui.component.MediaActionItem
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.component.viewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val h3ImgSaveMutex = Mutex()
private val h3ImgSavedByRun = HashMap<String, List<String>>()

/**
 * 下載成品圖存進所有作品(標 [H3])。全域互斥 + 已存紀錄(H3Jobs / 記憶體)→ 同一任務只下載、只入庫一次。
 * 回本機 uri 清單(下載失敗的張略過)。
 */
private suspend fun saveH3ImagesToHistory(
    ctx: android.content.Context,
    prefs: SecurePrefs,
    runId: String,
    images: List<H3ImageOut>,
    prompt: String,
): List<String> = h3ImgSaveMutex.withLock {
    h3ImgSavedByRun[runId]?.let { return@withLock it }
    H3Jobs.find(ctx, runId)?.savedUri?.takeIf { it.isNotBlank() }?.let { saved ->
        val list = saved.split('\n').filter { it.isNotBlank() }
        h3ImgSavedByRun[runId] = list
        return@withLock list
    }
    val out = ArrayList<String>()
    for (im in images) {
        val dl = H3Client.downloadBytes(prefs, im.url) ?: continue
        val uri = MediaSaver.saveImage(ctx, dl.first, H3_PROMPT_TAG + prompt.ifBlank { "H3 Studio 生圖" }, dl.second ?: "image/png")
            ?: continue
        out.add(uri)
    }
    if (out.isNotEmpty()) {
        h3ImgSavedByRun[runId] = out
        H3Jobs.update(ctx, runId) { it.copy(savedUri = out.joinToString("\n")) }
    }
    out
}

/**
 * v2.0.7 H3 生圖任務頁:每 5 秒輪詢 GET /api/app/images/{run_id},顯示狀態 / 機器 / 最後事件,可取消;
 * 完成後把成品下載進所有作品(標 [H3])並顯示,「儲存到相簿」「分享」「圖片動起來」同 Grok 成品做法。
 */
@Composable
fun H3ImageJobScreen(runId: String, onBack: () -> Unit, onAnimate: (String, String) -> Unit = { _, _ -> }) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val record = remember(runId) { H3Jobs.find(ctx, runId) }
    var status by remember { mutableStateOf<H3ImageStatus?>(null) }
    var pollError by remember { mutableStateOf<String?>(null) }
    var savedUris by remember {
        mutableStateOf(record?.savedUri?.split('\n')?.filter { it.isNotBlank() } ?: emptyList())
    }
    var saving by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    val prompt = record?.prompt.orEmpty()

    suspend fun saveAll(images: List<H3ImageOut>): List<String> {
        if (savedUris.isNotEmpty()) return savedUris
        saving = true
        val list = withContext(ImagineApp.appScope.coroutineContext) {
            saveH3ImagesToHistory(ctx.applicationContext, prefs, runId, images, prompt)
        }
        saving = false
        if (list.isNotEmpty()) savedUris = list
        return list
    }

    // 只在畫面可見(STARTED)時輪詢;401/403/404 停止輪詢
    val lifecycleOwner = LocalLifecycleOwner.current
    var pollStopped by remember(runId) { mutableStateOf(false) }
    LaunchedEffect(runId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive && !pollStopped) {
                when (val r = H3Client.imageRun(prefs, runId)) {
                    is H3Client.Result.Ok -> {
                        status = r.value
                        pollError = null
                        H3Jobs.update(ctx, runId) { it.copy(status = r.value.status) }
                        if (r.value.isFinished) {
                            pollStopped = true
                            if (r.value.status == "done" && r.value.images.isNotEmpty() && savedUris.isEmpty()) {
                                ImagineApp.appScope.launch(Dispatchers.Main) {
                                    if (saveAll(r.value.images).isEmpty()) pollError = "成品自動存到所有作品失敗，可按「儲存到相簿」重試"
                                }
                            }
                        }
                    }
                    is H3Client.Result.Err -> {
                        pollError = r.message
                        if (r.code == 401 || r.code == 403 || r.code == 404) pollStopped = true
                    }
                }
                if (!pollStopped) delay(5_000)
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("取消 H3 生圖？") },
            text = { Text("排隊中的任務會直接取消；已在跑的會在這批做完後停止（已開的機器費用不會退回）。") },
            confirmButton = {
                TextActionButton(label = "取消任務", color = MaterialTheme.colorScheme.error, onClick = {
                    confirmCancel = false
                    cancelling = true
                    scope.launch {
                        when (val r = H3Client.cancel(prefs, runId)) {
                            is H3Client.Result.Ok -> Toast.makeText(ctx, "已要求取消", Toast.LENGTH_SHORT).show()
                            is H3Client.Result.Err -> Toast.makeText(ctx, "取消失敗：${r.message}", Toast.LENGTH_LONG).show()
                        }
                        cancelling = false
                    }
                })
            },
            dismissButton = { TextActionButton(label = "不要取消", onClick = { confirmCancel = false }) },
        )
    }

    viewerIndex?.let { idx ->
        FullscreenImageViewer(
            urls = savedUris,
            startIndex = idx,
            onDismiss = { viewerIndex = null },
            actions = listOf(
                MediaAction.ANIMATE_IMAGE.viewer { url -> viewerIndex = null; onAnimate(url, prompt) },
                MediaAction.SAVE_TO_GALLERY.viewer { url ->
                    ImagineApp.appScope.launch {
                        val ok = MediaExporter.saveToGallery(ctx, url, isVideo = false)
                        withContext(Dispatchers.Main) { AppNotice.show(if (ok) "已存到相簿" else "儲存失敗，稍後再試") }
                    }
                },
                MediaAction.SHARE.viewer { url -> ImagineApp.appScope.launch { MediaExporter.share(ctx, url, isVideo = false) } },
            ),
        )
    }

    val s = status
    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "H3 生圖",
                showBack = true,
                onBackClick = onBack,
                trailing = { Box(modifier = Modifier.size(48.dp)) },
            )
        },
        bottomNav = null,
        bottomAction = if (s != null && !s.isFinished) {
            {
                OutlinedActionButton(
                    label = if (cancelling) "取消中…" else "取消任務",
                    icon = "close",
                    enabled = !cancelling,
                    onClick = { confirmCancel = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ImagineCard(pad = 16) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (s == null) "讀取中…" else H3Format.statusLabel(s.status),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.W700,
                        color = when (s?.status) {
                            "failed" -> MaterialTheme.colorScheme.error
                            "done" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    s?.lastEvent?.message?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (s != null && !s.isFinished) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "機器：${H3Format.routeShort(s?.route)}" + (record?.let { "・${it.meta}" } ?: ""),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    record?.let { rec ->
                        if (rec.estNtd > 0 || rec.estCu > 0) {
                            Text(
                                "預估費用：" + (if (rec.estNtd > 0) "NT$${H3Format.ntd(rec.estNtd)}" else "") +
                                    (if (rec.estCu > 0) "（${H3Format.cu(rec.estCu)} CU）" else ""),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    s?.error?.takeIf { it.isNotBlank() }?.let {
                        Text("錯誤：$it", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                    pollError?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    if (s != null && !s.isFinished) {
                        Text(
                            "每 5 秒自動更新。離開此頁任務仍會在雲端繼續，可從生圖頁「最近 H3 生圖」回來。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 不靠 s?.status 的 smart cast(安全呼叫比對不保證推出 s != null),明寫
            if (s != null && s.status == "done" && s.images.isNotEmpty()) {
                ImagineCard(pad = 0) {
                    Column {
                        if (savedUris.isEmpty()) {
                            Text(
                                if (saving) "正在把成品存進所有作品…" else "成品還沒存到本機，請稍候或按「儲存到相簿」重試",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        savedUris.forEachIndexed { i, uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = prompt,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { viewerIndex = i },
                            )
                        }
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (prompt.isNotBlank()) {
                                SelectionContainer {
                                    Text(prompt, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Text(
                                when {
                                    saving -> "正在存到所有作品…"
                                    savedUris.isNotEmpty() -> "已存到所有作品（標記 H3）・${savedUris.size} 張，點圖可放大"
                                    else -> "尚未存到所有作品"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MediaActionBar(
                                items = listOf(
                                    MediaActionItem(MediaAction.ANIMATE_IMAGE) {
                                        val first = savedUris.firstOrNull()
                                        if (first != null) onAnimate(first, prompt) else Toast.makeText(ctx, "成品還沒存到本機", Toast.LENGTH_SHORT).show()
                                    },
                                    MediaActionItem(MediaAction.SAVE_TO_GALLERY) {
                                        ImagineApp.appScope.launch {
                                            val locals = savedUris.ifEmpty { saveAll(s.images) }
                                            var ok = locals.isNotEmpty()
                                            for (u in locals) if (!MediaExporter.saveToGallery(ctx, u, isVideo = false)) ok = false
                                            withContext(Dispatchers.Main) {
                                                AppNotice.show(if (ok) "已存到相簿（${locals.size} 張）" else "儲存失敗，稍後再試")
                                            }
                                        }
                                    },
                                    MediaActionItem(MediaAction.SHARE) {
                                        ImagineApp.appScope.launch {
                                            val locals = savedUris.ifEmpty { saveAll(s.images) }
                                            if (locals.isNotEmpty()) MediaExporter.shareMultiple(ctx, locals, isVideo = false)
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
