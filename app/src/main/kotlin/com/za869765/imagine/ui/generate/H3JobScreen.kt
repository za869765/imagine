package com.za869765.imagine.ui.generate

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3Format
import com.za869765.imagine.data.h3.H3JobStatus
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.MediaExporter
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.ui.component.AppNotice
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.MediaAction
import com.za869765.imagine.ui.component.MediaActionBar
import com.za869765.imagine.ui.component.MediaActionItem
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.TextActionButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

private val h3SaveMutex = Mutex()
private val h3SavedByRun = HashMap<String, String>()

/**
 * 下載成品存進所有作品(標 [H3])。全域互斥 + 已存紀錄(H3Jobs / 記憶體)→ 同一任務只下載、只入庫一次。
 */
internal suspend fun saveH3ToHistory(
    ctx: android.content.Context,
    prefs: SecurePrefs,
    runId: String,
    videoPath: String,
    prompt: String,
): String? = h3SaveMutex.withLock {
    (h3SavedByRun[runId] ?: H3Jobs.find(ctx, runId)?.savedUri)?.let { return@withLock it }
    val uri = H3Client.download(prefs, videoPath) { stream ->
        MediaSaver.saveVideo(ctx, stream, H3_PROMPT_TAG + prompt.ifBlank { "H3 Studio 影片" })
    }
    if (uri != null) {
        h3SavedByRun[runId] = uri
        H3Jobs.update(ctx, runId) { it.copy(savedUri = uri) }
    }
    uri
}

/** 存進所有作品時的提示詞前綴 — 歷史清單靠它辨識 H3 成品(可搜尋「H3」)。 */
const val H3_PROMPT_TAG = "[H3] "

/**
 * H3 任務頁:每 5 秒輪詢 GET /api/app/jobs/{run_id},顯示狀態 / 階段進度 / 已用 CU,可取消;
 * 完成後串流播放(帶權杖)並自動存進所有作品(標 [H3]),「下載」= 存到手機相簿(同 Grok 成品做法)。
 */
@Composable
fun H3JobScreen(runId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val record = remember(runId) { H3Jobs.find(ctx, runId) }
    var status by remember { mutableStateOf<H3JobStatus?>(null) }
    var pollError by remember { mutableStateOf<String?>(null) }
    var savedUri by remember { mutableStateOf(record?.savedUri) }
    var saving by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var cancelling by remember { mutableStateOf(false) }
    val prompt = record?.prompt.orEmpty()

    // 成品存進所有作品:走 app 層 scope + 全域互斥(自動存檔與「下載/分享」同時觸發也只會下載一次)
    suspend fun saveToHistory(videoPath: String): String? {
        savedUri?.let { return it }
        saving = true
        val uri = withContext(ImagineApp.appScope.coroutineContext) {
            saveH3ToHistory(ctx.applicationContext, prefs, runId, videoPath, prompt)
        }
        saving = false
        if (uri != null) savedUri = uri
        return uri
    }

    // 只在畫面可見(STARTED)時輪詢;401/403/404 停止輪詢(權杖無效或任務不存在,再問也沒用)
    val lifecycleOwner = LocalLifecycleOwner.current
    var pollStopped by remember(runId) { mutableStateOf(false) }
    LaunchedEffect(runId, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive && !pollStopped) {
                when (val r = H3Client.job(prefs, runId)) {
                    is H3Client.Result.Ok -> {
                        status = r.value
                        pollError = null
                        H3Jobs.update(ctx, runId) { it.copy(status = r.value.status, etaAt = r.value.eta?.etaAt ?: it.etaAt) }
                        if (r.value.isFinished) {
                            pollStopped = true
                            val v = r.value.videoUrl
                            if (r.value.status == "done" && v != null && savedUri == null) {
                                ImagineApp.appScope.launch(Dispatchers.Main) {
                                    if (saveToHistory(v) == null) pollError = "成品自動存到所有作品失敗，可按「儲存到相簿」重試"
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
            title = { Text("取消 H3 任務？") },
            text = { Text("排隊中的任務會直接取消；已在跑的任務會在目前這段跑完後停止（已用掉的 CU 不會退回）。") },
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

    val s = status
    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "H3 任務",
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
                    // v2.1.9 預計完成(台北時間;前面還有幾個)
                    s?.takeIf { !it.isFinished }?.eta?.let { eta ->
                        Text(
                            H3Format.eta(eta.etaAt, eta.etaMin, now = rememberNowMs()) + (if (eta.ahead > 0) "・前面還有 ${eta.ahead} 個" else ""),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val ev = s?.lastEvent
                    ev?.message?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    val phase = ev?.phase
                    val pct = ev?.pct
                    if (phase != null || pct != null) {
                        Text(
                            listOfNotNull(phase, pct?.let { "${it.toInt()}%" }).joinToString("・"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (s != null && !s.isFinished) {
                        if (pct != null) {
                            // pct 可能是 0~100 或 0~1
                            val frac = (if (pct > 1.0) pct / 100.0 else pct).coerceIn(0.0, 1.0).toFloat()
                            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    s?.worker?.let { w ->
                        Text(
                            "GPU：${w.gpu ?: "—"}（${w.state ?: "—"}）",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "已用 CU：" + (s?.cuUsed?.let { H3Format.cu(it) + " CU" } ?: "尚未記錄") +
                            (record?.let { "（預估 ${H3Format.cu(it.estCu)} CU）" } ?: ""),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    s?.error?.takeIf { it.isNotBlank() }?.let {
                        Text("錯誤：$it", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                    pollError?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                    if (s != null && !s.isFinished) {
                        Text(
                            "每 5 秒自動更新。離開此頁任務仍會在雲端繼續，可從 H3 生成頁「最近 H3 任務」回來。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val videoPath = s?.videoUrl
            if (s?.status == "done" && videoPath != null) {
                ImagineCard(pad = 0) {
                    Column {
                        // 已存到本機就播本機檔,否則帶權杖串流(只限站內 https 網址,其他不播、不帶權杖)
                        val playUrl = savedUri ?: H3Client.absoluteUrl(prefs, videoPath)
                        if (playUrl != null) {
                            H3VideoPlayer(
                                url = playUrl,
                                headers = H3Client.authHeaders(prefs),
                                userAgent = H3Client.userAgent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 280.dp, max = 520.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            Text(
                                "影片網址不在 H3 Studio 網站內（或網址不是 https），為保護權杖不播放",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(14.dp),
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
                                    savedUri != null -> "已存到所有作品（標記 H3）"
                                    else -> "尚未存到所有作品"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MediaActionBar(
                                items = listOf(
                                    // 「下載」= 存到手機相簿(同 Grok 成品);本機還沒有就先下載
                                    MediaActionItem(MediaAction.SAVE_TO_GALLERY) {
                                        ImagineApp.appScope.launch {
                                            val local = savedUri ?: saveToHistory(videoPath)
                                            val ok = local != null && MediaExporter.saveToGallery(ctx, local, isVideo = true)
                                            withContext(Dispatchers.Main) {
                                                AppNotice.show(if (ok) "已存到相簿" else "下載失敗，稍後再試")
                                            }
                                        }
                                    },
                                    MediaActionItem(MediaAction.SHARE) {
                                        ImagineApp.appScope.launch {
                                            val local = savedUri ?: saveToHistory(videoPath)
                                            if (local != null) MediaExporter.share(ctx, local, isVideo = true)
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
