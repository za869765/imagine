package com.za869765.imagine.ui.edit

import android.net.Uri
import com.za869765.imagine.data.storage.MediaEncoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.repo.userFriendlyTag
import com.za869765.imagine.data.storage.MediaExporter
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.util.Clipboard
import com.za869765.imagine.data.work.VideoPollWorker
import com.za869765.imagine.ui.component.ConfirmHighRiskDialog
import com.za869765.imagine.ui.component.FullscreenImageViewer
import com.za869765.imagine.ui.component.FullscreenVideoPlayer
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.firstHighRiskTerm
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class EditMode { ImageEdit, VideoEdit, VideoExtend }

/**
 * EditPane — 編輯/延長的「內容」本體：自帶 prompt/來源/loading/結果 等 state、
 * pickMedia / encodeMedia / runExecute(含 editImage / editVideo / extendVideo API)、
 * Worker observer，以及「來源選取 + PromptInput + 執行鈕 + 結果」UI。
 *
 * mode 由外部參數控制(EditPane 內不放模式 SegmentedTab)；不含 Scaffold/AppBar/BottomNav，
 * 故可同時被 EditScreen wrapper 與 生成頁內嵌使用。同頁同時只應渲染一個 EditPane(模式互斥)，
 * 否則多個 Worker observer 會搶同一 trackedRequestId。
 */
@Composable
fun EditPane(
    mode: EditMode,
    initialMediaUri: Uri? = null,
    initialPrompt: String? = null,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }
    val focusManager = LocalFocusManager.current

    var prompt by rememberSaveable { mutableStateOf(initialPrompt.orEmpty()) }
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && initialPrompt != prompt) {
            prompt = initialPrompt
        }
    }
    // Uri 非 Saveable，存字串
    var sourceUriStr by rememberSaveable {
        mutableStateOf<String?>(initialMediaUri?.toString())
    }
    val sourceUri: Uri? = sourceUriStr?.let { Uri.parse(it) }
    LaunchedEffect(initialMediaUri) {
        if (initialMediaUri != null && initialMediaUri.toString() != sourceUriStr) {
            sourceUriStr = initialMediaUri.toString()
        }
    }
    // 角色參考(v1.7.2,僅 ImageEdit):最多 2 張,與來源共 3 張送 /images/edits —
    // super-i 雙參考圖法:來源當造型底,角色定妝圖鎖臉。Uri 非 Saveable,存字串。
    var charRefStrings by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var showCharPicker by remember { mutableStateOf(false) }
    // trackedRequestId 是 SSOT — 切走再回來時 LaunchedEffect 會恢復 loading 並重 observe。
    // loading 維持 remember,ImageEdit (scope-cancel 路徑) 切走時不會卡住。
    var loading by remember { mutableStateOf(false) }
    var trackedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableStateOf(0) }
    var resultImageUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var resultVideoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPrompt by rememberSaveable { mutableStateOf("") }
    // A2：送出前若偵測到高風險詞,先彈確認;非 null = 顯示對話框,值為命中的詞
    var pendingRiskTerm by remember { mutableStateOf<String?>(null) }
    val workManager = remember(ctx) { WorkManager.getInstance(ctx.applicationContext) }

    // mode 在圖片/影片間切換時,清掉來源與結果(舊 EditScreen SegmentedTab onSelected 的行為)。
    // 用 isImage 旗標比對:只有「圖↔影」越界才清,影片編輯↔影片延長互切保留來源。
    var lastIsImage by rememberSaveable { mutableStateOf(mode == EditMode.ImageEdit) }
    LaunchedEffect(mode) {
        val isImage = mode == EditMode.ImageEdit
        if (isImage != lastIsImage) {
            sourceUriStr = null
            charRefStrings = emptyList()
            resultImageUrl = null
            resultVideoUrl = null
            lastIsImage = isImage
        }
    }

    // Worker 完成事件接收 — 同 GenerateVideoScreen 模式
    LaunchedEffect(trackedRequestId) {
        val rid = trackedRequestId ?: return@LaunchedEffect
        loading = true   // process / Composable 重建後從 saveable 恢復狀態
        workManager.getWorkInfosForUniqueWorkFlow(VideoPollWorker.uniqueName(rid))
            .collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val url = info.outputData.getString(VideoPollWorker.KEY_VIDEO_URL)
                        if (url != null) resultVideoUrl = url
                        loading = false
                        trackedRequestId = null
                    }
                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(VideoPollWorker.KEY_ERROR)
                        if (!err.isNullOrBlank()) {
                            Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
                        }
                        loading = false
                        trackedRequestId = null
                    }
                    WorkInfo.State.CANCELLED -> {
                        // v1.0.54: 補 feedback，否則 user 看到 loading 突然消失沒任何訊息
                        Toast.makeText(ctx, "影片任務被取消 (可能 app 被系統殺，請重試)", Toast.LENGTH_LONG).show()
                        loading = false
                        trackedRequestId = null
                    }
                    else -> Unit
                }
            }
    }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> if (uri != null) sourceUriStr = uri.toString() }
    val launchPick: () -> Unit = {
        val type = if (mode == EditMode.ImageEdit)
            ActivityResultContracts.PickVisualMedia.ImageOnly
        else
            ActivityResultContracts.PickVisualMedia.VideoOnly
        pickMedia.launch(PickVisualMediaRequest(type))
    }

    LaunchedEffect(loading) {
        if (loading) {
            elapsed = 0
            while (isActive && loading) {
                delay(1000)
                elapsed++
            }
        }
    }

    // v1.0.49: 改 call MediaEncoder.encodeForApi — 圖會 downscale + JPEG recompress 避免 OOM;
    // 影片限 10MB 否則回 null (caller 已有「讀取來源失敗」toast)。
    suspend fun encodeMedia(uri: Uri): String? {
        val kind = if (mode == EditMode.ImageEdit) MediaEncoder.Kind.Image else MediaEncoder.Kind.Video
        return MediaEncoder.encodeForApi(ctx, uri, kind)
    }

    fun runExecute() {
        // 點執行 = 自動收鍵盤(避免 IME 佔走畫面看不到處理中/結果)
        focusManager.clearFocus()
        val src = sourceUri
        if (src == null) {
            Toast.makeText(ctx, "請先選擇來源", Toast.LENGTH_SHORT).show()
            return
        }
        // 空白 prompt 用 initialPrompt 兜底 — HistoryDetail 「編輯這段/延長影片」帶進來的
        // 原 prompt 可以直接拿來重跑
        val effectivePrompt = prompt.ifBlank { initialPrompt.orEmpty() }
        if (effectivePrompt.isBlank()) {
            Toast.makeText(ctx, "請輸入編輯說明", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            loading = true
            // v1.0.50: 整段包 try/catch，不讓任何未預期 throw 把 app 直接 crash
            try {
                val capturedPrompt = effectivePrompt
                val capturedMode = mode

                val encoded = encodeMedia(src)
                if (encoded == null) {
                    loading = false
                    Toast.makeText(ctx, "讀取來源失敗 — 試試小張一點的圖 / 短片", Toast.LENGTH_LONG).show()
                    return@launch
                }

                when (capturedMode) {
                    EditMode.ImageEdit -> {
                        // 角色參考(最多 2)接在來源後 — xAI /images/edits 最多 3 張輸入圖
                        val charEncoded = ArrayList<String>()
                        for (s in charRefStrings.take(2)) {
                            val e = MediaEncoder.encodeForApi(ctx, Uri.parse(s), MediaEncoder.Kind.Image)
                            if (e != null) charEncoded.add(e)
                        }
                        val r = repository.editImage(capturedPrompt, listOf(encoded) + charEncoded)
                        loading = false
                        // v1.0.54: 不再傳 scope (改用 appScope 內部 launch)
                        handleImageResult(r, capturedPrompt, ctx) {
                            resultImageUrl = it
                            lastPrompt = capturedPrompt
                        }
                    }
                    EditMode.VideoEdit, EditMode.VideoExtend -> {
                        val gen = if (capturedMode == EditMode.VideoEdit)
                            repository.editVideo(capturedPrompt, encoded)
                        else
                            repository.extendVideo(capturedPrompt, encoded)
                        if (gen is ApiResult.Error) {
                            loading = false
                            val tag = gen.kind.userFriendlyTag()
                            Toast.makeText(ctx, tag, Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val requestId = (gen as ApiResult.Success).value
                        lastPrompt = capturedPrompt
                        // 把 polling + 下載 + 存檔 + 通知 全部交給 VideoPollWorker
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
                        Toast.makeText(ctx, "影片背景處理中,完成會通知", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (oom: OutOfMemoryError) {
                loading = false
                com.za869765.imagine.data.storage.CrashLogger.record(ctx, "runExecute.OOM", oom)
                System.gc()
                Toast.makeText(ctx, "記憶體不足 — 試試小張一點的圖 / 短片", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                loading = false
                com.za869765.imagine.data.storage.CrashLogger.record(ctx, "runExecute.fail", t)
                Toast.makeText(ctx, "執行失敗: ${t.message?.take(120) ?: t::class.simpleName}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 不在此硬寫 padding — 由外層(wrapper / 生成頁的 padded Column)提供 16dp,
    // 內嵌時才不會與外層的 .padding(16.dp) 疊成雙倍邊距。需要邊距的 caller 自行從 modifier 帶。
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("來源")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .clickable(onClick = launchPick),
                contentAlignment = Alignment.Center,
            ) {
                val src = sourceUri
                if (src != null) {
                    if (mode == EditMode.ImageEdit) {
                        AsyncImage(
                            model = src,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp)),
                        )
                    } else {
                        VideoThumb(uri = src, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                sourceUriStr = null
                                resultImageUrl = null
                                resultVideoUrl = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        ImagineIcon(
                            name = "close", size = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            ImagineIcon(
                                name = "add_photo_alternate", size = 24.dp, fill = 1,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            text = when (mode) {
                                EditMode.ImageEdit -> "選擇圖片"
                                else -> "選擇影片"
                            },
                            fontSize = 15.sp, fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "點此從相簿選取",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 角色參考(僅圖片編輯):帶入角色資產定妝圖鎖臉 — 來源當造型底、角色圖鎖臉,
        // 與來源共 3 張送 API(雙參考圖法)。
        if (mode == EditMode.ImageEdit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    com.za869765.imagine.ui.component.ImagineChip(
                        label = "🎭 加角色參考（鎖臉）",
                        icon = "star",
                        variant = com.za869765.imagine.ui.component.ChipVariant.Tonal,
                        onClick = { showCharPicker = true },
                    )
                    if (charRefStrings.isNotEmpty()) {
                        Text(
                            "prompt 記得指名參考誰的臉",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (charRefStrings.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        charRefStrings.forEachIndexed { index, s ->
                            Box(modifier = Modifier.size(64.dp)) {
                                AsyncImage(
                                    model = Uri.parse(s),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable {
                                            charRefStrings = charRefStrings
                                                .toMutableList()
                                                .also { it.removeAt(index) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ImagineIcon(
                                        name = "close", size = 13.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader("編輯說明")
            if (mode == EditMode.ImageEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextActionButton(
                        label = "🎭 三相圖範本",
                        icon = "auto_awesome",
                        onClick = {
                            prompt = "將這張圖中的角色製作成三視圖角色設定表（turnaround model sheet）：由左至右【正面】【四分之三側面】【背面】三個視圖等高水平並排、共用同一條地平線、比例與身高一致；角色採中性站姿、雙臂自然下垂、表情自然；純淨中性灰 studio 背景、均勻柔和三點打光、正交視角無透視變形；完整保留原角色的臉、髮型、服裝、配色與配件，三視之間完全一致。畫面只有這一個角色的三個視圖，不得出現第二個角色、不得有文字浮水印。"
                        },
                    )
                }
            }
            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                label = "",
                placeholder = when (mode) {
                    EditMode.ImageEdit -> "把背景換成夕陽，加上暖色調濾鏡..."
                    EditMode.VideoEdit -> "把場景換成下雨夜晚..."
                    EditMode.VideoExtend -> "讓主角繼續往街道走..."
                },
                minHeight = 104,
                forVideo = mode != EditMode.ImageEdit,   // 影片編輯/延長要顯示 動作/聲音/字幕
            )
        }

        if (loading) {
            ImagineCard(pad = 20) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp,
                    )
                    Text(
                        when (mode) {
                            EditMode.ImageEdit -> "圖片編輯中…"
                            EditMode.VideoEdit -> "影片編輯中…"
                            EditMode.VideoExtend -> "影片延長中…"
                        },
                        fontSize = 14.sp, fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (mode != EditMode.ImageEdit) {
                        Text(
                            "%d:%02d".format(elapsed / 60, elapsed % 60),
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        } else {
            // 空白 prompt 也可送 — 用 initialPrompt 兜底
            val hasPrompt = prompt.isNotBlank() || !initialPrompt.isNullOrBlank()
            PrimaryButton(
                label = "執 行",
                icon = "edit",
                enabled = hasPrompt && sourceUri != null && prefs.isApiKeySet,
                onClick = {
                    val term = firstHighRiskTerm(prompt)
                    if (term != null) pendingRiskTerm = term else runExecute()
                },
            )
        }

        pendingRiskTerm?.let { term ->
            ConfirmHighRiskDialog(
                term = term,
                onConfirm = { pendingRiskTerm = null; runExecute() },
                onDismiss = { pendingRiskTerm = null },
            )
        }

        if (showCharPicker) {
            com.za869765.imagine.ui.component.CharacterPickerSheet(
                onDismiss = { showCharPicker = false },
                onPick = { name, uris ->
                    showCharPicker = false
                    if (uris.isEmpty()) {
                        Toast.makeText(ctx, "角色「$name」還沒有定妝圖", Toast.LENGTH_SHORT).show()
                    } else {
                        // 上限 2 張(與來源共 3);取角色前 2 張(通常是正面定妝)
                        charRefStrings = uris.take(2).map { it.toString() }
                        Toast.makeText(
                            ctx,
                            "已帶入「$name」${charRefStrings.size} 張當鎖臉參考",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        resultImageUrl?.let { url ->
            var showImg by remember { mutableStateOf(false) }
            SectionHeader("結果")
            ImagineCard(pad = 0) {
                Column {
                    AsyncImage(
                        model = url,
                        contentDescription = lastPrompt,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showImg = true },
                    )
                    ResultActionRow(url = url, prompt = lastPrompt, isVideo = false)
                }
            }
            if (showImg) {
                FullscreenImageViewer(urls = listOf(url), onDismiss = { showImg = false })
            }
        }
        resultVideoUrl?.let { url ->
            SectionHeader("結果")
            ImagineCard(pad = 0) {
                Column {
                    EditVideoPreview(
                        url = url,
                        allowFullscreen = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 460.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                    ResultActionRow(url = url, prompt = lastPrompt, isVideo = true)
                }
            }
        }
    }
}

// 編輯/延長結果卡的操作列:複製 prompt + 存到相簿 + 分享。
@Composable
private fun ResultActionRow(url: String, prompt: String, isVideo: Boolean) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
    ) {
        if (prompt.isNotBlank()) {
            TextActionButton(
                label = "複製",
                icon = "content_copy",
                onClick = { Clipboard.copy(ctx, prompt, toastMsg = "已複製 prompt") },
            )
        }
        TextActionButton(
            label = "存到相簿",
            icon = "download",
            onClick = {
                com.za869765.imagine.ImagineApp.appScope.launch {
                    val ok = MediaExporter.saveToGallery(ctx, url, isVideo = isVideo)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(
                            ctx,
                            if (ok) "已存到相簿" else "存相簿失敗，改用分享試試",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
        TextActionButton(
            label = "分享",
            icon = "share",
            onClick = {
                com.za869765.imagine.ImagineApp.appScope.launch {
                    MediaExporter.share(ctx, url, isVideo = isVideo)
                }
            },
        )
    }
}

/**
 * EditScreen — 給 HistoryDetail「編輯這張/這段/延長影片」入口用的獨立頁。
 * Scaffold + AppBar + BottomNav + 模式 SegmentedTab(圖片編輯/影片編輯/影片延長) + EditPane。
 * route / 對外簽章 / 行為皆不變。
 */
@Composable
fun EditScreen(
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    initialMediaUri: Uri? = null,
    initialPrompt: String? = null,
    initialEditMode: String? = null,    // 從 HistoryDetail 帶 "image" / "video" / "extend"
) {
    // rememberSaveable 讓切走再回來 state 保留 — EditMode 用字串存
    var modeStr by rememberSaveable {
        mutableStateOf(
            when (initialEditMode) {
                "video" -> "vid"
                "extend" -> "ext"
                "image" -> "img"
                else -> "img"
            }
        )
    }
    val mode = when (modeStr) {
        "vid" -> EditMode.VideoEdit
        "ext" -> EditMode.VideoExtend
        else -> EditMode.ImageEdit
    }
    // 接收新的 mode hint (HistoryDetail 再次帶不同檔案進來時要切換)
    LaunchedEffect(initialEditMode) {
        when (initialEditMode) {
            "video" -> modeStr = "vid"
            "extend" -> modeStr = "ext"
            "image" -> modeStr = "img"
        }
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("img", "圖片編輯"),
                    SegmentedOption("vid", "影片編輯"),
                    SegmentedOption("ext", "影片延長"),
                ),
                activeId = when (mode) {
                    EditMode.ImageEdit -> "img"
                    EditMode.VideoEdit -> "vid"
                    EditMode.VideoExtend -> "ext"
                },
                onSelected = {
                    modeStr = when (it) {
                        "img" -> "img"
                        "vid" -> "vid"
                        else -> "ext"
                    }
                },
            )
            // EditPane 無自帶 padding,靠外層 Column 的 padding + spacedBy 提供節奏
            EditPane(
                mode = mode,
                initialMediaUri = initialMediaUri,
                initialPrompt = initialPrompt,
            )
        }
    }
}

private fun handleImageResult(
    r: ApiResult<List<String>>,
    capturedPrompt: String,
    ctx: android.content.Context,
    onSuccess: (String) -> Unit,
) {
    when (r) {
        is ApiResult.Success -> {
            val url = r.value.firstOrNull()
            if (url != null) {
                onSuccess(url)
                // v1.0.54 B3: 改用 ImagineApp.appScope (process-lifecycle) 而非 Composable scope。
                // user 切走/鎖屏/back 時 Composable scope 會 cancel → MediaSaver 寫一半被砍
                // → 圖永遠不會出現在 History。appScope 不受 UI 影響，確保下載+存檔完成。
                com.za869765.imagine.ImagineApp.appScope.launch {
                    MediaSaver.saveImageFromUrl(ctx, url, capturedPrompt)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(ctx, "已存到相簿", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(ctx, "未收到結果", Toast.LENGTH_SHORT).show()
            }
        }
        is ApiResult.Error -> {
            val tag = r.kind.userFriendlyTag()
            Toast.makeText(ctx, tag, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun VideoThumb(uri: Uri, modifier: Modifier = Modifier) {
    // Show first frame using ExoPlayer paused for content uris too
    EditVideoPreview(url = uri.toString(), modifier = modifier)
}

@Composable
private fun EditVideoPreview(url: String, modifier: Modifier = Modifier, allowFullscreen: Boolean = false) {
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
    var fullscreen by remember { mutableStateOf(false) }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                // 真實比例(直/橫/方不變形);allowFullscreen 時控制列出現全螢幕鈕。
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                if (allowFullscreen) setFullscreenButtonClickListener {
                    player.pause()
                    fullscreen = true
                }
            }
        },
        modifier = modifier,
    )
    if (fullscreen) {
        FullscreenVideoPlayer(url = url, onDismiss = { fullscreen = false })
    }
}
