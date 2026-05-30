package com.za869765.imagine.ui.generate

import android.net.Uri
import com.za869765.imagine.data.storage.MediaEncoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import com.za869765.imagine.data.api.XaiClient
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ErrorKind
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.repo.userFriendlyTag
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.data.work.VideoPollWorker
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.ChipVariant
import com.za869765.imagine.ui.component.ConfirmHighRiskDialog
import com.za869765.imagine.ui.component.ImagineChip
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.firstHighRiskTerm
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VideoMode { T2V, Img2Vid, Ref }

@Composable
fun GenerateVideoScreen(
    onSwitchToImage: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    initialImageUri: Uri? = null,    // 從圖片頁「動起來」帶過來
    initialPrompt: String? = null,    // 「動起來」時順帶把圖片的 prompt 預填 (對齊 grok-imagine console 行為)
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }
    val focusManager = LocalFocusManager.current

    var prompt by rememberSaveable { mutableStateOf(initialPrompt.orEmpty()) }
    // initialPrompt 變動 (例如使用者從 History 不同筆動起來) 時覆蓋已存 prompt
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && initialPrompt != prompt) {
            prompt = initialPrompt
        }
    }
    var mode by rememberSaveable {
        mutableStateOf(if (initialImageUri != null) VideoMode.Img2Vid else VideoMode.T2V)
    }
    var duration by rememberSaveable { mutableStateOf(5) }
    var aspect by rememberSaveable { mutableStateOf("1:1") }
    var resolution by rememberSaveable { mutableStateOf("480p") }
    // sourceImages 是 List<Uri> — Uri 本身可序列化,但 List<Uri> 沒 Saver,改存字串 list
    var sourceImageStrings by rememberSaveable {
        mutableStateOf(initialImageUri?.let { listOf(it.toString()) } ?: emptyList())
    }
    val sourceImages = sourceImageStrings.map { Uri.parse(it) }

    // trackedRequestId 是 SSOT — process / Composable 重建後從 saveable 恢復,LaunchedEffect
    // 自動重新 observe Worker 並把 generating 設回 true。generating 維持 remember,避免
    // observer 還沒跑就先擋住「生成」按鈕造成 deadlock。
    var generating by remember { mutableStateOf(false) }
    var trackedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableStateOf(0) }
    var resultVideoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPrompt by rememberSaveable { mutableStateOf("") }
    var lastError by rememberSaveable { mutableStateOf("") }
    var lastErrorIsPolicy by rememberSaveable { mutableStateOf(false) }
    // A2：送出前若偵測到高風險詞,先彈確認;非 null = 顯示對話框,值為命中的詞
    var pendingRiskTerm by remember { mutableStateOf<String?>(null) }
    // v1.0.63 bug#3: 影片生成成功(Worker SUCCEEDED)時設 true → 捲到底部把新影片帶進視野,
    // 避免「上次結果」已存在時新影片落在 fold 下方使用者看不到。
    val scrollState = rememberScrollState()
    var pendingScrollToResult by remember { mutableStateOf(false) }
    LaunchedEffect(pendingScrollToResult) {
        if (pendingScrollToResult) {
            try {
                scrollState.animateScrollTo(scrollState.maxValue)
            } finally {
                // 動畫被取消(使用者甩動)也要歸位,避免下次重組又回彈
                pendingScrollToResult = false
            }
        }
    }
    val workManager = remember(ctx) { WorkManager.getInstance(ctx.applicationContext) }

    // Worker 完成事件接收 — Worker 在後台 polling + 下載 + 存檔 + 發系統通知,
    // UI 只 observe state 更新預覽 / 錯誤訊息。trackedRequestId 改變時重 collect。
    LaunchedEffect(trackedRequestId) {
        val rid = trackedRequestId ?: return@LaunchedEffect
        generating = true   // process / Composable 重建後從 saveable 恢復狀態
        workManager.getWorkInfosForUniqueWorkFlow(VideoPollWorker.uniqueName(rid))
            .collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val url = info.outputData.getString(VideoPollWorker.KEY_VIDEO_URL)
                        if (url != null) {
                            resultVideoUrl = url
                            pendingScrollToResult = true  // bug#3: 捲到結果區讓新影片主動出現
                        }
                        generating = false
                        trackedRequestId = null
                    }
                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(VideoPollWorker.KEY_ERROR)
                        if (!err.isNullOrBlank()) lastError = err
                        generating = false
                        trackedRequestId = null
                    }
                    WorkInfo.State.CANCELLED -> {
                        // v1.0.54: 補 lastError + toast，否則 user 看到 spinner 突然消失沒任何反饋
                        // 以為「生成失敗」其實是 worker 被 cancel (常見原因：process death + 舊版
                        // recovery 機制；現在 v1.0.54 砍 recovery 後罕見，但保留 feedback)
                        lastError = "影片任務被取消 (可能 app 被系統殺，請重試)"
                        Toast.makeText(ctx, lastError, Toast.LENGTH_LONG).show()
                        generating = false
                        trackedRequestId = null
                    }
                    else -> Unit
                }
            }
    }

    val maxImages = if (mode == VideoMode.Img2Vid) 1 else 3
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null && sourceImageStrings.size < maxImages) {
            sourceImageStrings = sourceImageStrings + uri.toString()
        }
    }
    val launchPick: () -> Unit = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(generating) {
        if (generating) {
            elapsed = 0
            while (isActive && generating) {
                delay(1000)
                elapsed++
            }
        }
    }

    // v1.0.49: 改 call MediaEncoder.encodeForApi — 對 local file URI 會先 Bitmap
    // downscale 到 max 1024px + JPEG 85 recompress + base64，避免大檔 OOM 閃退。
    // https URL 直通不 re-encode (xAI 自家生的圖直接傳 URL)。
    suspend fun encodeImage(uri: Uri): String? =
        MediaEncoder.encodeForApi(ctx, uri, MediaEncoder.Kind.Image)

    // 把起始圖/參考圖第一張存到相簿 (相同 imagine 目錄)。Grok 風格：
    // 即使這次 video gen 失敗或還沒按生成，使用者仍能把原圖拿走收藏 / 之後再改 prompt 試。
    fun downloadFirstSourceImage() {
        val uri = sourceImages.firstOrNull() ?: run {
            Toast.makeText(ctx, "沒有原圖可下載", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    val scheme = uri.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") {
                        URL(uri.toString()).openStream().use { it.readBytes() }
                    } else {
                        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                Toast.makeText(ctx, "讀取原圖失敗", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val saved = MediaSaver.saveImage(ctx, bytes, prompt.ifBlank { "imagine source" })
            Toast.makeText(
                ctx,
                if (saved != null) "原圖已存到相簿 (Imagine)" else "存檔失敗",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun runGenerate() {
        if (mode != VideoMode.T2V && sourceImages.isEmpty()) {
            Toast.makeText(
                ctx,
                if (mode == VideoMode.Img2Vid) "請先選擇起始圖" else "請先選擇至少 1 張參考圖",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        // 本地圖警告: xAI REST 只接受 https URL，從手機相簿選的圖會被忽略，
        // 結果跟純文生影一樣。建議走「動起來」/「當參考圖」入口帶 xAI 回傳的 URL。
        if (mode != VideoMode.T2V) {
            val localPicked = sourceImages.any { uri ->
                val s = uri.scheme?.lowercase()
                s != "http" && s != "https"
            }
            if (localPicked) {
                Toast.makeText(
                    ctx,
                    "⚠️ 手機相簿選的圖 xAI 可能不會參考；建議從歷史頁/圖片頁「動起來」",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        // 點生成 = 自動收鍵盤(避免 IME 佔走畫面看不到生成中/結果)
        focusManager.clearFocus()
        // 點生成 = 開新一輪,先清掉上一輪的錯誤訊息(包含 400 審核紅卡),
        // 不用使用者再去點「清除」
        lastError = ""
        lastErrorIsPolicy = false
        scope.launch {
            generating = true
            // v1.0.50: 整段包 try/catch，不讓任何未預期 throw 把 app 直接 crash
            try {
                // 空白 prompt 時用 initialPrompt 兜底(從歷史/圖片頁「動起來」「延長」帶進來)，
                // 避免使用者按了沒反應、又得手動把預填的字再貼回去
                val capturedPrompt = prompt.ifBlank { initialPrompt.orEmpty() }
                val capturedMode = mode
                val capturedDuration = duration

                // v1.0.49: encodeImage 改成 suspend (內含 IO + Bitmap decode)，
                // 不能再用 .let / .mapNotNull (lambda type 非 suspend) — 改 for loop
                val firstSource = sourceImages.firstOrNull()
                val starting = if (capturedMode == VideoMode.Img2Vid && firstSource != null) {
                    encodeImage(firstSource)
                } else null
                val refs = if (capturedMode == VideoMode.Ref) {
                    val list = mutableListOf<String>()
                    for (uri in sourceImages) {
                        encodeImage(uri)?.let { list += it }
                    }
                    list.takeIf { it.isNotEmpty() }
                } else null

                // v1.0.50: 圖生影/參考圖模式 encode 失敗 → 不送 API，直接 toast
                if (capturedMode == VideoMode.Img2Vid && starting == null) {
                    generating = false
                    Toast.makeText(ctx, "讀取起始圖失敗 — 試試小張一點的圖", Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (capturedMode == VideoMode.Ref && refs.isNullOrEmpty()) {
                    generating = false
                    Toast.makeText(ctx, "讀取參考圖失敗 — 試試小張一點的圖", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val gen = repository.generateVideo(
                    prompt = capturedPrompt,
                    duration = capturedDuration,
                    resolution = resolution,
                    aspectRatio = aspect,
                    startingImageUrl = starting,
                    referenceImageUrls = refs,
                )
                when (gen) {
                    is ApiResult.Error -> {
                        generating = false
                        val tag = gen.kind.userFriendlyTag()
                        lastError = tag
                        lastErrorIsPolicy = (gen.kind == ErrorKind.ContentPolicy)
                        Toast.makeText(ctx, tag, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    is ApiResult.Success -> {
                        val requestId = gen.value
                        lastPrompt = capturedPrompt
                        // 把 polling + 下載 + 存檔 + 通知 全部交給 VideoPollWorker。
                        // Worker 跑前景服務,Composable 被 dispose / process 死也能完成。
                        val request = OneTimeWorkRequestBuilder<VideoPollWorker>()
                            .addTag(VideoPollWorker.TAG_VIDEO_POLL)  // v1.0.51: 給 crash-loop recovery 用
                            .setInputData(VideoPollWorker.inputDataOf(requestId, capturedPrompt))
                            .build()
                        workManager.enqueueUniqueWork(
                            VideoPollWorker.uniqueName(requestId),
                            ExistingWorkPolicy.KEEP,
                            request,
                        )
                        trackedRequestId = requestId
                        Toast.makeText(ctx, "影片背景生成中,完成會通知", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (oom: OutOfMemoryError) {
                generating = false
                com.za869765.imagine.data.storage.CrashLogger.record(ctx, "runGenerate.OOM", oom)
                System.gc()
                Toast.makeText(ctx, "記憶體不足 — 試試小張一點的圖", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                generating = false
                com.za869765.imagine.data.storage.CrashLogger.record(ctx, "runGenerate.fail", t)
                Toast.makeText(ctx, "生成失敗: ${t.message?.take(120) ?: t::class.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) },
        scrollState = scrollState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("image", "圖片"),
                    SegmentedOption("video", "影片"),
                ),
                activeId = "video",
                onSelected = { if (it == "image") onSwitchToImage() },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("模式")
                SegmentedTab(
                    options = listOf(
                        SegmentedOption("t2v", "文生影"),
                        SegmentedOption("img2vid", "圖生影"),
                        SegmentedOption("ref", "參考圖"),
                    ),
                    activeId = when (mode) {
                        VideoMode.T2V -> "t2v"
                        VideoMode.Img2Vid -> "img2vid"
                        VideoMode.Ref -> "ref"
                    },
                    onSelected = {
                        mode = when (it) {
                            "t2v" -> VideoMode.T2V
                            "img2vid" -> VideoMode.Img2Vid
                            else -> VideoMode.Ref
                        }
                    },
                )
            }

            if (mode != VideoMode.T2V) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        if (mode == VideoMode.Img2Vid) "起始圖" else "參考圖（最多 3 張）",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        sourceImages.forEachIndexed { index, uri ->
                            SelectedImageSlot(
                                uri = uri,
                                onRemove = {
                                    sourceImageStrings = sourceImageStrings
                                        .toMutableList()
                                        .also { it.removeAt(index) }
                                },
                            )
                        }
                        if (sourceImages.size < maxImages) {
                            AddImageSlot(onClick = launchPick)
                        }
                    }
                    // 起始圖 / 參考圖常駐 chip — Grok 風格：原圖跟 prompt 永遠能拿走，
                    // 不論還沒生成 / 生成中 / 成功 / 400 失敗。
                    if (sourceImages.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            if (prompt.isNotBlank()) {
                                ImagineChip(
                                    label = "複製 prompt",
                                    icon = "content_copy",
                                    variant = ChipVariant.Tonal,
                                    onClick = {
                                        Clipboard.copy(ctx, prompt, toastMsg = "已複製 prompt")
                                    },
                                )
                            }
                            ImagineChip(
                                label = "下載原圖",
                                icon = "download",
                                variant = ChipVariant.Tonal,
                                onClick = { downloadFirstSourceImage() },
                            )
                        }
                    }
                }
            }

            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "描述要怎麼動...",
                minHeight = 88,
                flagged = lastErrorIsPolicy,
                forVideo = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamPicker(
                    label = "秒數",
                    value = duration.toString(),
                    options = (1..15).map { it.toString() },
                    onSelect = { duration = it.toIntOrNull() ?: 5 },
                    displayName = { "$it 秒" },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "長寬比",
                    value = aspect,
                    options = listOf("16:9", "1:1", "9:16", "4:3", "3:4", "3:2", "2:3"),
                    onSelect = { aspect = it },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "解析度",
                    value = resolution,
                    options = listOf("480p", "720p"),
                    onSelect = { resolution = it },
                    modifier = Modifier.weight(1f),
                )
            }

            if (generating) {
                ImagineCard(pad = 24) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                        )
                        Text(
                            "影片生成中…",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "%d:%02d".format(elapsed / 60, elapsed % 60),
                            fontSize = 28.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "預計 30–90 秒；可切背景或鎖屏，完成會發系統通知",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // v1.0.54 (c): 提醒 user 別從最近應用滑掉，否則 process 死 worker 中斷
                        Text(
                            "⚠️ 請勿從「最近應用程式」往上滑掉 Imagine，否則背景工作會中斷",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        // v1.0.55: 砍「取消生成」按鈕 — xAI 沒提供 cancel API，credits 已扣
                        // 仍會跑完，imagine 端取消只是「停止本地等待」對 user 沒實質意義。
                        // 不顯示按鈕讓 user 自然等完 (完成會發系統通知)。
                    }
                }
            } else {
                // 空白 prompt 仍可送 — 若 initialPrompt 帶進來就用它生成
                val hasPrompt = prompt.isNotBlank() || !initialPrompt.isNullOrBlank()
                PrimaryButton(
                    label = "生 成",
                    icon = "movie",
                    enabled = hasPrompt && prefs.isApiKeySet,
                    onClick = {
                        val term = firstHighRiskTerm(prompt)
                        if (term != null) pendingRiskTerm = term else runGenerate()
                    },
                )
            }

            pendingRiskTerm?.let { term ->
                ConfirmHighRiskDialog(
                    term = term,
                    onConfirm = { pendingRiskTerm = null; runGenerate() },
                    onDismiss = { pendingRiskTerm = null },
                )
            }

            if (lastError.isNotBlank()) {
                val cardBg = if (lastErrorIsPolicy) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
                val cardFg = if (lastErrorIsPolicy) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lastError,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = cardFg,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!generating && prompt.isNotBlank()) {
                                ImagineChip(
                                    label = "重試",
                                    icon = "refresh",
                                    variant = ChipVariant.Tonal,
                                    onClick = { runGenerate() },
                                )
                            }
                            ImagineChip(
                                label = "清除",
                                variant = ChipVariant.Tonal,
                                onClick = { lastError = ""; lastErrorIsPolicy = false },
                            )
                        }
                    }
                }
            }

            resultVideoUrl?.let { url ->
                Text(
                    text = "上次結果",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.08.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ImagineCard(pad = 0) {
                    Column {
                        VideoPreview(
                            url = url,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp, max = 480.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // bug #4: prompt 區用 SelectionContainer 包起來可長按複製，不再截斷
                            SelectionContainer {
                                Text(
                                    lastPrompt,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextActionButton(
                                    label = "複製 prompt",
                                    icon = "content_copy",
                                    onClick = {
                                        Clipboard.copy(ctx, lastPrompt, toastMsg = "已複製 prompt")
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPreview(url: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(url) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun AddImageSlot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ImagineIcon(
            name = "add_photo_alternate",
            size = 28.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectedImageSlot(uri: Uri, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(80.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(11.dp))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            ImagineIcon(name = "close", size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
