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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
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
import com.za869765.imagine.data.api.OpenRouterClient
import com.za869765.imagine.data.api.XaiClient
import com.za869765.imagine.data.catalog.ModelMode
import com.za869765.imagine.data.catalog.OpenRouterCatalog
import com.za869765.imagine.data.catalog.XaiCatalog
import com.za869765.imagine.data.catalog.defaultModelFor
import com.za869765.imagine.data.prefs.ApiProvider
import com.za869765.imagine.data.prefs.DraftStore
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.PlaceholderConfirmDialog
import com.za869765.imagine.ui.component.ReplaceOrAppendDialog
import com.za869765.imagine.ui.component.appendPrompt
import com.za869765.imagine.ui.component.placeholderCount
import com.za869765.imagine.data.repo.OpenRouterRepository
import com.za869765.imagine.ui.component.ModelPickerRow
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ErrorKind
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.repo.userFriendlyTag
import com.za869765.imagine.data.storage.FailedJobs
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.storage.MediaExporter
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.data.work.VideoPollWorker
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.GenerateSettingsSummary
import com.za869765.imagine.ui.component.GeneratingCard
import com.za869765.imagine.ui.component.GENERATING_HINT_BACKGROUND
import com.za869765.imagine.ui.component.MediaAction
import com.za869765.imagine.ui.component.MediaActionBar
import com.za869765.imagine.ui.component.MediaActionItem
import com.za869765.imagine.ui.component.ModeOption
import com.za869765.imagine.ui.component.ModePicker
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.SourceSlot
import com.za869765.imagine.ui.component.aspectLabel
import com.za869765.imagine.ui.component.describeVideoSettings
import com.za869765.imagine.ui.component.estimateCost
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

enum class VideoMode { T2V, Img2Vid, Ref2Vid }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GenerateVideoScreen(
    onSwitchToImage: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onSwitchToChat: () -> Unit = {},
    initialImageUri: Uri? = null,    // 從圖片頁「動起來」帶過來
    initialPrompt: String? = null,    // 「動起來」時順帶把圖片的 prompt 預填 (對齊 grok-imagine console 行為)
    initialVideoMode: String? = null, // "i2v" → 即使沒帶圖也開在圖生影模式(教學 i2v 範本:純動作,需使用者再選來源圖)
    initialExtendBase: String? = null, // 組合延長:原片 file:// uri,生成成功後 Worker 自動把原片+新片串接
    onBack: (() -> Unit)? = null, // 組合延長獨立頁(非素材生成 tab)的返回鍵;一般 tab 進入時為 null
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }
    val focusManager = LocalFocusManager.current

    // v1.8.0 供應商 + 模型:xAI 兩款($0.05 / $0.08 每秒);OpenRouter 24 款生影模型(秒數/解析度/長寬比依模型)
    val orRepo = remember(prefs) { OpenRouterRepository(OpenRouterClient.build(prefs)) }
    // v1.8.3 單一模型選擇(xAI / OpenRouter 合併清單),供應商由模型 id 判斷
    var videoModel by rememberSaveable {
        mutableStateOf(prefs.videoModel ?: defaultModelFor(ModelMode.VIDEO, prefs.isApiKeySet, prefs.isOpenRouterKeySet))
    }
    val provider = ApiProvider.ofModel(videoModel)
    val orModel = videoModel
    val xaiModel = videoModel
    val modelInfo = remember(videoModel) {
        if (provider == ApiProvider.OPENROUTER) OpenRouterCatalog.find(ctx, ModelMode.VIDEO, videoModel)
        else XaiCatalog.models(ModelMode.VIDEO).firstOrNull { it.id == videoModel }
    }
    val durationOptions = modelInfo?.durations?.takeIf { it.isNotEmpty() }?.sorted() ?: (1..15).toList()
    val resolutionOptions = modelInfo?.resolutions?.takeIf { it.isNotEmpty() } ?: listOf("480p", "720p")
    val aspectOptions = modelInfo?.aspects?.takeIf { it.isNotEmpty() }
        ?: listOf("16:9", "1:1", "9:16", "4:3", "3:4", "3:2", "2:3")

    // 草稿(UI_REDESIGN_PLAN 0.2):提示詞 / 製作方式 / 來源圖 各自持久化;組合延長不還原(獨立流程)
    val useDraft = initialExtendBase == null && initialPrompt.isNullOrBlank() && initialImageUri == null && initialVideoMode == null
    var prompt by rememberSaveable {
        mutableStateOf(
            initialPrompt?.takeIf { it.isNotBlank() }
                ?: (if (useDraft) DraftStore.load(ctx, DraftStore.VIDEO_PROMPT) else null).orEmpty(),
        )
    }
    LaunchedEffect(prompt) {
        if (initialExtendBase == null) {
            kotlinx.coroutines.delay(400)
            DraftStore.save(ctx, DraftStore.VIDEO_PROMPT, prompt)
        }
    }
    // A2：送出前若偵測到高風險詞,先彈確認;非 null = 顯示對話框,值為命中的詞
    var pendingRiskTerm by remember { mutableStateOf<String?>(null) }
    // 帶 prompt 進來且草稿非空 → 問「取代 / 加入末尾 / 取消」,不再靜默覆蓋
    var pendingInitial by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && initialPrompt != prompt) {
            if (prompt.isBlank()) prompt = initialPrompt else pendingInitial = initialPrompt
        }
    }
    pendingInitial?.let { incoming ->
        ReplaceOrAppendDialog(
            onReplace = { pendingInitial = null; prompt = incoming },
            onAppend = { pendingInitial = null; prompt = appendPrompt(prompt, incoming) },
            onDismiss = { pendingInitial = null },
        )
    }
    var mode by rememberSaveable {
        mutableStateOf(
            when {
                initialVideoMode == "ref2v" -> VideoMode.Ref2Vid
                initialImageUri != null || initialVideoMode == "i2v" -> VideoMode.Img2Vid
                useDraft && DraftStore.load(ctx, DraftStore.VIDEO_MODE) == "i2v" -> VideoMode.Img2Vid
                useDraft && DraftStore.load(ctx, DraftStore.VIDEO_MODE) == "ref2v" -> VideoMode.Ref2Vid
                else -> VideoMode.T2V
            },
        )
    }
    LaunchedEffect(mode) {
        if (initialExtendBase == null) {
            DraftStore.save(ctx, DraftStore.VIDEO_MODE, when (mode) { VideoMode.Img2Vid -> "i2v"; VideoMode.Ref2Vid -> "ref2v"; else -> "t2v" })
        }
    }
    // 影片頁子功能：gen=生成(文生影/圖生影,用 mode 細分) / extend=影片延長 / edit=影片編輯。
    // extend / edit 內嵌 EditPane;VideoMode 僅在 gen 時有意義。
    var videoFn by rememberSaveable { mutableStateOf("gen") }
    // key 帶 prefs 預設值:設定改了影片預設後重進本頁會 re-init 成新預設(rememberSaveable 否則還原舊值)
    var duration by rememberSaveable(prefs.defVideoDuration) { mutableStateOf(prefs.defVideoDuration) }
    var aspect by rememberSaveable(prefs.defVideoAspect) { mutableStateOf(prefs.defVideoAspect) }
    var resolution by rememberSaveable(prefs.defVideoResolution) { mutableStateOf(prefs.defVideoResolution) }
    // 所選模型不支援目前的秒數/解析度/長寬比時,送出與顯示都用最接近的合法值(不改使用者存的偏好)
    val effDuration = if (duration in durationOptions) duration
        else (durationOptions.firstOrNull { it >= duration } ?: durationOptions.last())
    val effResolution = if (resolution in resolutionOptions) resolution else resolutionOptions.first()
    val effAspect = if (aspect in aspectOptions) aspect else aspectOptions.first()
    // sourceImages 是 List<Uri> — Uri 本身可序列化,但 List<Uri> 沒 Saver,改存字串 list
    var sourceImageStrings by rememberSaveable {
        mutableStateOf(
            initialImageUri?.let { listOf(it.toString()) }
                ?: (if (useDraft) DraftStore.loadList(ctx, DraftStore.VIDEO_SOURCES) else emptyList()),
        )
    }
    LaunchedEffect(sourceImageStrings) {
        if (initialExtendBase == null) DraftStore.saveList(ctx, DraftStore.VIDEO_SOURCES, sourceImageStrings)
    }
    val sourceImages = sourceImageStrings.map { Uri.parse(it) }
    // 圖生影「從素材庫選」：true 時彈出素材庫圖片 grid sheet
    var showLibraryPicker by remember { mutableStateOf(false) }
    // 教學 i2v 範本「使用範本」進來且尚無來源圖 → 直接開選圖(UI_REDESIGN_PLAN 3.4)
    LaunchedEffect(Unit) {
        if (initialVideoMode == "i2v" && initialImageUri == null && sourceImageStrings.isEmpty() && initialExtendBase == null) {
            showLibraryPicker = true
        }
    }
    // 參考圖生影「帶入角色」：true 時彈出角色資產 sheet(v1.7.2)
    var showCharacterPicker by remember { mutableStateOf(false) }

    // trackedRequestId 是 SSOT — process / Composable 重建後從 saveable 恢復,LaunchedEffect
    // 自動重新 observe Worker 並把 generating 設回 true。generating 維持 remember,避免
    // observer 還沒跑就先擋住「生成」按鈕造成 deadlock。
    var generating by remember { mutableStateOf(false) }
    var trackedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableStateOf(0) }
    // Worker 回報的真實階段(polling/downloading/merging;null=剛送出),UI_REDESIGN_PLAN 2.1
    var stage by remember { mutableStateOf<String?>(null) }
    var resultVideoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    // 成品本機檔(file://):延長/修改影片用本機檔而非 CDN URL
    var resultSavedUri by rememberSaveable { mutableStateOf<String?>(null) }
    // 結果卡「延長影片／修改影片」→ 切到 EditPane 並帶入來源
    var editInitialUri by rememberSaveable { mutableStateOf<String?>(null) }
    // 每次新結果 +1,讓 VideoPreview 重建播放器 → 避免 xAI 重用同一 URL 時看到上一支舊片。
    var resultVideoGen by remember { mutableStateOf(0) }
    var lastPrompt by rememberSaveable { mutableStateOf("") }
    var lastError by rememberSaveable { mutableStateOf("") }
    var lastErrorIsPolicy by rememberSaveable { mutableStateOf(false) }
    // 失敗後的下一步(UI_REDESIGN_PLAN 0.3):generate=建立新請求 / query=重新查詢既有任務 / download=只重做下載
    var retryKind by rememberSaveable { mutableStateOf("generate") }
    var retryRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var retryProvider by rememberSaveable { mutableStateOf(VideoPollWorker.PROVIDER_XAI) }
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
                            resultSavedUri = info.outputData.getString(VideoPollWorker.KEY_SAVED_URI)
                            resultVideoGen++   // A1: 重建播放器顯示這次的新片
                            pendingScrollToResult = true  // bug#3: 捲到結果區讓新影片主動出現
                        }
                        generating = false
                        trackedRequestId = null
                    }
                    WorkInfo.State.RUNNING -> {
                        stage = info.progress.getString(VideoPollWorker.KEY_STAGE)
                    }
                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(VideoPollWorker.KEY_ERROR)
                        if (!err.isNullOrBlank()) lastError = err
                        // 作品已生成只是下載失敗 → 「重新下載」(同 requestId 再輪詢一次即重抓,不建新請求);
                        // 其餘失敗(任務被拒/超時)→ 「重新生成」
                        retryKind = if (err?.contains("下載失敗") == true) "download" else "generate"
                        retryRequestId = rid
                        // 失敗也留記錄(UI_REDESIGN_PLAN 6.5);下載失敗不算生成失敗(作品在服務端,可重新下載)
                        if (retryKind == "generate") {
                            FailedJobs.add(ctx, true, lastPrompt.ifBlank { prompt }, describeVideoSettings(effDuration, effAspect, effResolution), err ?: "影片任務失敗")
                        }
                        resultVideoUrl = null // 失敗→清上次結果,避免誤會舊片是新結果
                        generating = false
                        trackedRequestId = null
                    }
                    WorkInfo.State.CANCELLED -> {
                        // v1.0.54: 補 lastError + toast，否則 user 看到 spinner 突然消失沒任何反饋
                        // 以為「生成失敗」其實是 worker 被 cancel;任務可能仍在後台 → 「重新查詢」而非重新生成
                        lastError = "尚未確認結果（背景工作被中斷），任務可能仍在後台執行"
                        Toast.makeText(ctx, lastError, Toast.LENGTH_LONG).show()
                        retryKind = "query"
                        retryRequestId = rid
                        resultVideoUrl = null // 取消→清上次結果
                        generating = false
                        trackedRequestId = null
                    }
                    else -> Unit
                }
            }
    }

    // UI_REDESIGN_PLAN 6.7:Android 13+ 未授權通知時 notify() 會被靜默丟 → 送出前請求一次;拒絕不擋送出,只提示
    val notifPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) com.za869765.imagine.ui.component.AppNotice.show("未允許通知：影片完成時不會收到系統通知，請留在 App 內查看")
    }
    fun ensureNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
        // v1.8.1: 舊的 rememberSaveable 模式可能繞過 UI 隱藏 → 送出前再擋一次
        if (provider == ApiProvider.OPENROUTER && mode == VideoMode.Img2Vid && modelInfo?.frameImages != true) {
            Toast.makeText(ctx, "此模型不支援圖生影(首幀),請改文生影 / 參考圖生影或換模型", Toast.LENGTH_LONG).show()
            return
        }
        if (mode != VideoMode.T2V && sourceImages.isEmpty()) {
            Toast.makeText(
                ctx,
                if (mode == VideoMode.Ref2Vid) "請先選擇參考圖" else "請先選擇起始圖",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        // 點生成 = 自動收鍵盤(避免 IME 佔走畫面看不到生成中/結果)
        focusManager.clearFocus()
        ensureNotificationPermission()
        // 點生成 = 開新一輪,先清掉上一輪的錯誤訊息(包含 400 審核紅卡),
        // 不用使用者再去點「清除」
        lastError = ""
        lastErrorIsPolicy = false
        scope.launch {
            generating = true
            stage = null
            // v1.0.50: 整段包 try/catch，不讓任何未預期 throw 把 app 直接 crash
            try {
                // 空白 prompt 時用 initialPrompt 兜底(從歷史/圖片頁「動起來」「延長」帶進來)，
                // 避免使用者按了沒反應、又得手動把預填的字再貼回去
                val capturedPrompt = prompt.ifBlank { initialPrompt.orEmpty() }
                val capturedMode = mode
                val capturedDuration = effDuration
                val capturedProvider = provider
                val capturedModel = if (capturedProvider == ApiProvider.OPENROUTER) orModel else xaiModel
                val capturedResolution = effResolution
                val capturedAspect = effAspect

                // v1.0.49: encodeImage 改成 suspend (內含 IO + Bitmap decode)，
                // 不能再用 .let / .mapNotNull (lambda type 非 suspend) — 改 for loop
                val firstSource = sourceImages.firstOrNull()
                val starting = if (capturedMode == VideoMode.Img2Vid && firstSource != null) {
                    encodeImage(firstSource)
                } else null
                // v1.0.50: 圖生影模式 encode 失敗 → 不送 API，直接 toast
                if (capturedMode == VideoMode.Img2Vid && starting == null) {
                    generating = false
                    Toast.makeText(ctx, "讀取起始圖失敗 — 試試小張一點的圖", Toast.LENGTH_LONG).show()
                    return@launch
                }
                // v1.5.2 參考圖生影:把所有來源圖 encode 成 reference_images。與首幀 image 互斥
                // (Ref2Vid 只送 reference_images、Img2Vid 只送 image),xAI 文件:參考圖
                // 「影響輸出但不會被當成第一幀」,適合三相圖/角色一致性生影。
                val references = if (capturedMode == VideoMode.Ref2Vid) {
                    val encoded = ArrayList<String>()
                    for (u in sourceImages) {
                        val e = encodeImage(u)
                        if (e != null) encoded.add(e)
                    }
                    encoded
                } else null
                if (capturedMode == VideoMode.Ref2Vid && references.isNullOrEmpty()) {
                    generating = false
                    Toast.makeText(ctx, "讀取參考圖失敗 — 試試小張一點的圖", Toast.LENGTH_LONG).show()
                    return@launch
                }
                val gen = if (capturedProvider == ApiProvider.OPENROUTER) {
                    // OpenRouter:POST /videos → job id;frame_images(首幀)與 input_references(參考圖)互斥同 xAI
                    orRepo.submitVideo(
                        model = capturedModel,
                        prompt = capturedPrompt,
                        duration = capturedDuration,
                        resolution = capturedResolution,
                        aspectRatio = capturedAspect,
                        firstFrameUrl = starting,
                        referenceUrls = references,
                    )
                } else {
                    repository.generateVideo(
                        prompt = capturedPrompt,
                        duration = capturedDuration,
                        resolution = capturedResolution,
                        aspectRatio = capturedAspect,
                        startingImageUrl = starting,
                        referenceImageUrls = references,
                        model = capturedModel,
                    )
                }
                when (gen) {
                    is ApiResult.Error -> {
                        generating = false
                        val tag = gen.kind.userFriendlyTag()
                        lastError = tag
                        lastErrorIsPolicy = (gen.kind == ErrorKind.ContentPolicy)
                        retryKind = "generate"   // 未取得 requestId → 確定要建立新請求
                        retryRequestId = null
                        FailedJobs.add(ctx, true, capturedPrompt, describeVideoSettings(capturedDuration, capturedAspect, capturedResolution), tag)
                        resultVideoUrl = null // 400/被審核擋下→清上次結果,避免誤會是新結果
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
                            .setInputData(
                                VideoPollWorker.inputDataOf(
                                    requestId, capturedPrompt, initialExtendBase,
                                    provider = if (capturedProvider == ApiProvider.OPENROUTER) VideoPollWorker.PROVIDER_OPENROUTER else VideoPollWorker.PROVIDER_XAI,
                                ),
                            )
                            .build()
                        workManager.enqueueUniqueWork(
                            VideoPollWorker.uniqueName(requestId),
                            ExistingWorkPolicy.KEEP,
                            request,
                        )
                        trackedRequestId = requestId
                        retryProvider = if (capturedProvider == ApiProvider.OPENROUTER) VideoPollWorker.PROVIDER_OPENROUTER else VideoPollWorker.PROVIDER_XAI
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

    // 重新查詢 / 重新下載:用既有 requestId 再排一次 Worker(輪詢→已完成→直接下載),不建立新請求、不重複扣費
    fun requeueExisting() {
        val rid = retryRequestId ?: return
        lastError = ""
        lastErrorIsPolicy = false
        stage = null
        generating = true
        val request = OneTimeWorkRequestBuilder<VideoPollWorker>()
            .addTag(VideoPollWorker.TAG_VIDEO_POLL)
            .setInputData(VideoPollWorker.inputDataOf(rid, lastPrompt.ifBlank { prompt }, initialExtendBase, provider = retryProvider))
            .build()
        workManager.enqueueUniqueWork(VideoPollWorker.uniqueName(rid), ExistingWorkPolicy.REPLACE, request)
        trackedRequestId = rid
    }
    // 送出前仍含【】佔位符 → 先確認(UI_REDESIGN_PLAN 5.2)
    var pendingPlaceholderSubmit by remember { mutableStateOf(false) }
    fun submitWithChecks() {
        if (placeholderCount(prompt) > 0) { pendingPlaceholderSubmit = true; return }
        val term = firstHighRiskTerm(prompt)
        if (term != null) pendingRiskTerm = term else runGenerate()
    }
    if (pendingPlaceholderSubmit) {
        PlaceholderConfirmDialog(
            count = placeholderCount(prompt),
            onConfirm = {
                pendingPlaceholderSubmit = false
                val term = firstHighRiskTerm(prompt)
                if (term != null) pendingRiskTerm = term else runGenerate()
            },
            onDismiss = { pendingPlaceholderSubmit = false },
        )
    }

    // 組合延長 = 進階獨立頁:返回鍵 + 不顯示底欄(不歸屬素材生成 tab)。
    // UI_REDESIGN_PLAN 1.3:組合延長不再是獨立 UI 分支,而是「製作方式鎖定 + 尾格來源鎖定」的同一流程。
    val isCombineExtend = initialExtendBase != null
    val isEditFn = videoFn == "extend" || videoFn == "edit"
    // 修改/延長影片模式:EditPane 把主按鈕狀態交給 handle,由底部 slot 渲染(位置固定)
    val editHandle = com.za869765.imagine.ui.edit.rememberEditActionHandle()

    // 製作方式清單(UI_REDESIGN_PLAN 1.2):id 對應既有 videoFn + VideoMode;
    // OpenRouter 不支援的方式直接不列(圖生影依模型 frameImages;延長/修改只有 xAI 有 API)。
    val modeOptions = if (isCombineExtend) {
        listOf(ModeOption("combine", "組合延長", "用原片尾格當起點生成續集，完成後自動接成一支長片"))
    } else {
        buildList {
            add(ModeOption("t2v", "文字生成影片", "只用提示詞生成新影片"))
            if (provider == ApiProvider.XAI || modelInfo?.frameImages == true) {
                add(ModeOption("i2v", "圖片動起來", "以一張圖片為起始格生成影片"))
            }
            add(ModeOption("ref2v", "參考圖生影", "多張參考圖鎖角色／場景，不會被當成第一幀"))
            if (provider == ApiProvider.XAI) {
                add(ModeOption("extend", "延長影片", "從影片尾格續接，生成後面的內容"))
                add(ModeOption("edit", "修改影片", "依說明改動既有影片的內容"))
            }
        }
    }
    val currentModeId = when {
        isCombineExtend -> "combine"
        videoFn == "extend" -> "extend"
        videoFn == "edit" -> "edit"
        mode == VideoMode.Img2Vid -> "i2v"
        mode == VideoMode.Ref2Vid -> "ref2v"
        else -> "t2v"
    }
    val modeFootnote = when {
        provider != ApiProvider.OPENROUTER -> null
        modelInfo?.frameImages == true ->
            "此模型可用圖片動起來；參考圖生影僅部分 OpenRouter 模型支援(Wan/Seedance/Kling 等),不支援會回錯誤。延長/修改影片只有 xAI 提供。"
        else -> "此模型不支援圖片動起來(首幀),已隱藏；參考圖生影僅部分模型支援,不支援會回錯誤。延長/修改影片只有 xAI 提供。"
    }

    // 組合延長:解析度自動沿用原片高度(自動串接需同解析度,否則 MediaMuxer 合成失敗→只剩兩段)
    if (initialExtendBase != null) {
        LaunchedEffect(initialExtendBase) {
            val h = withContext(Dispatchers.IO) {
                val r = android.media.MediaMetadataRetriever()
                try {
                    r.setDataSource(ctx, Uri.parse(initialExtendBase))
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()
                } catch (_: Throwable) {
                    null
                } finally {
                    runCatching { r.release() }
                }
            }
            if (h != null) resolution = if (h >= 720) "720p" else "480p"
        }
    }

    ImagineScreen(
        appBar = {
            if (isCombineExtend) {
                ImagineTopAppBar(
                    title = "組合延長",
                    showBack = true,
                    onBackClick = { onBack?.invoke() },
                    trailing = { Box(modifier = Modifier.size(40.dp)) },
                )
            } else {
                ImagineTopAppBar(title = "生成影片", onSettingsClick = onSettingsClick)
            }
        },
        bottomNav = if (isCombineExtend) {
            null
        } else {
            { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) }
        },
        scrollState = scrollState,
        // UI_REDESIGN_PLAN 1.6:主按鈕固定底部,文案依製作方式
        bottomAction = {
            if (isEditFn) {
                val label = if (videoFn == "extend") "延長影片" else "修改影片"
                PrimaryButton(
                    label = if (editHandle.loading) "處理中…" else label,
                    icon = if (editHandle.loading) null else "edit",
                    loading = editHandle.loading,
                    enabled = editHandle.enabled,
                    onClick = { editHandle.execute() },
                )
            } else {
                // 空白 prompt 仍可送 — 若 initialPrompt 帶進來就用它生成
                val hasPrompt = prompt.isNotBlank() || !initialPrompt.isNullOrBlank()
                PrimaryButton(
                    label = when {
                        generating -> "生成中…"
                        isCombineExtend -> "生成續集並接成長片"
                        else -> "生成影片"
                    },
                    icon = if (generating) null else "movie",
                    loading = generating,
                    enabled = hasPrompt && !generating && prefs.hasKeyFor(provider) &&
                        (!isCombineExtend || sourceImages.isNotEmpty()),
                    onClick = { submitWithChecks() },
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!isCombineExtend) {
                // v1.8.0 L1 三段:對話｜生圖｜生影
                SegmentedTab(
                    options = listOf(
                        SegmentedOption("chat", "對話"),
                        SegmentedOption("image", "生圖"),
                        SegmentedOption("video", "生影"),
                    ),
                    activeId = "video",
                    onSelected = {
                        when (it) {
                            "image" -> onSwitchToImage()
                            "chat" -> onSwitchToChat()
                        }
                    },
                    activeColor = Color(0xFF14463F),
                )
            }

            // 製作方式 N 選 1(取代橫滑 ModePill 列);組合延長時鎖定不可改
            ModePicker(
                options = modeOptions,
                selectedId = currentModeId,
                onSelect = { id ->
                    when (id) {
                        "extend" -> videoFn = "extend"
                        "edit" -> videoFn = "edit"
                        "i2v" -> { videoFn = "gen"; mode = VideoMode.Img2Vid }
                        "ref2v" -> { videoFn = "gen"; mode = VideoMode.Ref2Vid }
                        else -> { videoFn = "gen"; mode = VideoMode.T2V }
                    }
                },
                footnote = modeFootnote,
                enabled = !isCombineExtend,
            )
            if (isCombineExtend) {
                ImagineCard(pad = 14) {
                    Text(
                        "用原片尾格當起點,輸入新提示詞生成「續集」。完成後會自動把『原片 + 續集』接成一支長片,存到歷史的「組合延長」,不必再手動拼接。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!prefs.hasKeyFor(provider)) {
                ImagineCard(pad = 14, onClick = onSettingsClick) {
                    Text(
                        "未設定 ${provider.label} API Key — 點此到設定填入／匯入備份,或在模型清單改選另一家的模型",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 19.sp,
                    )
                }
            }

            if (isEditFn) {
                // 影片延長 / 影片編輯 → 內嵌 EditPane(自帶來源選取/執行/結果);主按鈕交 editHandle 到底部 slot。
                // 同頁同時只渲染一個 EditPane,故 worker observer 不會與生成流程衝突。
                com.za869765.imagine.ui.edit.EditPane(
                    mode = if (videoFn == "extend")
                        com.za869765.imagine.ui.edit.EditMode.VideoExtend
                    else
                        com.za869765.imagine.ui.edit.EditMode.VideoEdit,
                    initialMediaUri = editInitialUri?.let { Uri.parse(it) },
                    handle = editHandle,
                )
            } else {
                if (mode != VideoMode.T2V) {
                    // 來源區(UI_REDESIGN_PLAN 1.3):需要時才出現;組合延長尾格鎖定不可更換
                    SourceSlot(
                        title = when {
                            isCombineExtend -> "尾格（續接起點）"
                            mode == VideoMode.Ref2Vid -> "參考圖（可多張，不會被當成第一幀）"
                            else -> "起始圖"
                        },
                        uris = sourceImages,
                        maxCount = maxImages,
                        emptyHint = "點此從素材庫或手機相簿選取",
                        onPick = { showLibraryPicker = true },
                        onRemove = { index ->
                            sourceImageStrings = sourceImageStrings
                                .toMutableList()
                                .also { if (index < it.size) it.removeAt(index) }
                        },
                        locked = isCombineExtend,
                        note = if (isCombineExtend) "解析度自動沿用原片（$resolution）— 自動串接需與原片同解析度才能接成一支" else null,
                        extra = if (mode == VideoMode.Ref2Vid && !isCombineExtend) {
                            {
                                // 參考圖生影:一鍵帶入「角色資產」整組定妝圖(鎖臉/鎖造型,角色一致性)
                                ImagineChip(
                                    label = "🎭 帶入角色定妝圖",
                                    icon = "star",
                                    variant = ChipVariant.Tonal,
                                    onClick = { showCharacterPicker = true },
                                )
                            }
                        } else null,
                    )
                    // 起始圖 / 參考圖常駐 chip — Grok 風格:原圖跟 prompt 永遠能拿走,
                    // 不論還沒生成 / 生成中 / 成功 / 400 失敗。
                    if (sourceImages.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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

                if (!isCombineExtend && resultVideoUrl == null && prompt.isBlank() && lastError.isBlank() && !generating) {
                    ImagineCard(pad = 14) {
                        Text(
                            "描述畫面要怎麼動，或用提示詞欄上方的「套用範本」從現成範例開始；要讓圖片動起來，先在上方「製作方式」選「圖片動起來」。",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                PromptInput(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = if (isCombineExtend) "描述續集要怎麼動…(例:轉身拔劍、鏡頭拉遠)" else "描述要怎麼動...",
                    minHeight = 88,
                    flagged = lastErrorIsPolicy,
                    forVideo = true,
                    // 圖生影模式 → 「套用範本」改出純動作範本;傳來源圖原 prompt 供半智能排序
                    videoHasImage = mode == VideoMode.Img2Vid || mode == VideoMode.Ref2Vid,
                    videoSourcePrompt = initialPrompt,
                )

                // v1.8.0 模型列(價格 / 免費標記)+ 參數選項依模型
                ModelPickerRow(
                    mode = ModelMode.VIDEO,
                    selectedId = videoModel,
                    onSelect = { videoModel = it; prefs.videoModel = it },
                )
                // 參數收成一列摘要,點擊展開(UI_REDESIGN_PLAN 1.4);組合延長只能改秒數(解析度沿用原片)
                // 本次預估費用(UI_REDESIGN_PLAN 6.2):每秒單價 × 秒數;token/megapixel 計價的模型不顯示
                val costText = estimateCost(modelInfo?.min, modelInfo?.unit.orEmpty(), effDuration)
                val settingsSummary = (if (isCombineExtend) {
                    "$effDuration 秒・解析度沿用原片（$resolution）"
                } else {
                    describeVideoSettings(effDuration, effAspect, effResolution)
                }) + (costText?.let { "・$it" } ?: "")
                GenerateSettingsSummary(summary = settingsSummary) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 2,
                    ) {
                        ParamPicker(
                            label = "秒數",
                            value = effDuration.toString(),
                            options = durationOptions.map { it.toString() },
                            onSelect = { duration = it.toIntOrNull() ?: 5 },
                            displayName = { "$it 秒" },
                            modifier = Modifier.weight(1f),
                        )
                        if (!isCombineExtend) {
                            ParamPicker(
                                label = "長寬比",
                                value = effAspect,
                                options = aspectOptions,
                                onSelect = { aspect = it },
                                displayName = { aspectLabel(it) },
                                modifier = Modifier.weight(1f),
                            )
                            ParamPicker(
                                label = "解析度",
                                value = effResolution,
                                options = resolutionOptions,
                                onSelect = { resolution = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (generating) {
                    // 真實階段 + 已等待時間(不再估算百分比,UI_REDESIGN_PLAN 2.1);背景提示兩句保留
                    GeneratingCard(
                        stage = (if (isCombineExtend) "續集" else "影片") + VideoPollWorker.stageLabel(stage),
                        elapsedSec = elapsed,
                        hint = GENERATING_HINT_BACKGROUND,
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
                                if (!generating) {
                                    // 三種失敗各自只出現對應的一顆(UI_REDESIGN_PLAN 0.3)
                                    when {
                                        retryKind == "download" && retryRequestId != null -> ImagineChip(
                                            label = "重新下載", icon = "download", variant = ChipVariant.Tonal,
                                            onClick = { requeueExisting() },
                                        )
                                        retryKind == "query" && retryRequestId != null -> ImagineChip(
                                            label = "重新查詢", icon = "refresh", variant = ChipVariant.Tonal,
                                            onClick = { requeueExisting() },
                                        )
                                        prompt.isNotBlank() -> ImagineChip(
                                            label = "重新生成", icon = "refresh", variant = ChipVariant.Tonal,
                                            onClick = { submitWithChecks() },
                                        )
                                    }
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
                        text = if (isCombineExtend) "✅ 續集已生成 — 長片已自動接好,去歷史找「組合延長」" else "上次結果",
                        fontSize = if (isCombineExtend) 12.sp else 11.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 0.08.sp,
                        color = if (isCombineExtend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ImagineCard(pad = 0) {
                        Column {
                            VideoPreview(
                                url = url,
                                gen = resultVideoGen,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 280.dp, max = 480.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // bug #4: prompt 區用 SelectionContainer 包起來可長按複製,不再截斷
                                SelectionContainer {
                                    Text(
                                        lastPrompt,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                // 動作列(UI_REDESIGN_PLAN 2.2):主要 延長影片 / 儲存到相簿;延長/修改只有 xAI 有 API
                                val editSrc = resultSavedUri ?: url
                                MediaActionBar(
                                    items = buildList {
                                        if (provider == ApiProvider.XAI) {
                                            add(MediaActionItem(MediaAction.EXTEND_VIDEO) { editInitialUri = editSrc; videoFn = "extend" })
                                        }
                                        add(MediaActionItem(MediaAction.SAVE_TO_GALLERY) {
                                            com.za869765.imagine.ImagineApp.appScope.launch {
                                                val ok = MediaExporter.saveToGallery(ctx, url, isVideo = true)
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    com.za869765.imagine.ui.component.AppNotice.show(if (ok) "已存到相簿" else "存相簿失敗，改用分享試試")
                                                }
                                            }
                                        })
                                        if (provider == ApiProvider.XAI) {
                                            add(MediaActionItem(MediaAction.EDIT_VIDEO) { editInitialUri = editSrc; videoFn = "edit" })
                                        }
                                        add(MediaActionItem(MediaAction.SHARE) {
                                            com.za869765.imagine.ImagineApp.appScope.launch {
                                                MediaExporter.share(ctx, url, isVideo = true)
                                            }
                                        })
                                        add(MediaActionItem(MediaAction.SHARE_WITH_PROMPT) {
                                            com.za869765.imagine.ImagineApp.appScope.launch {
                                                MediaExporter.share(ctx, url, isVideo = true, text = lastPrompt)
                                            }
                                        })
                                        add(MediaActionItem(MediaAction.COPY_PROMPT) {
                                            Clipboard.copy(ctx, lastPrompt, toastMsg = "已複製提示詞")
                                        })
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (showLibraryPicker) {
                LibraryImagePickerSheet(
                    onDismiss = { showLibraryPicker = false },
                    onPick = { entry ->
                        // 圖生影一次一張 → 直接取代來源;參考圖生影未達上限 → 新增(UI_REDESIGN_PLAN 1.3)
                        sourceImageStrings = if (mode == VideoMode.Ref2Vid && sourceImageStrings.size < maxImages) {
                            sourceImageStrings + entry.uri.toString()
                        } else {
                            listOf(entry.uri.toString())
                        }
                        showLibraryPicker = false
                    },
                    onPickFromGallery = {
                        showLibraryPicker = false
                        launchPick()
                    },
                )
            }

            if (showCharacterPicker) {
                com.za869765.imagine.ui.component.CharacterPickerSheet(
                    onDismiss = { showCharacterPicker = false },
                    onPick = { name, uris ->
                        showCharacterPicker = false
                        if (uris.isEmpty()) {
                            Toast.makeText(ctx, "角色「$name」還沒有定妝圖", Toast.LENGTH_SHORT).show()
                        } else {
                            // 整組取代目前參考圖;超過上限(3)只取前面的,並提示
                            sourceImageStrings = uris.take(maxImages).map { it.toString() }
                            if (uris.size > maxImages) {
                                Toast.makeText(ctx, "「$name」有 ${uris.size} 張,已帶入前 $maxImages 張", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(ctx, "已帶入「$name」${uris.size} 張定妝圖", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

// 從素材庫(filesDir/media)挑圖片當圖生影來源。沿用 HistoryScreen 的 load 與
// LazyVerticalGrid(3 欄)+AsyncImage 縮圖法；只列圖片(!isVideo)。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryImagePickerSheet(
    onDismiss: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPick: (MediaEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var images by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        images = MediaHistory.loadAll(ctx).filter { !it.isVideo }
        loaded = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            SectionHeader("從素材庫選圖")
            // 手機相簿入口：app 生成圖在 filesDir/media(PhotoPicker 看不到)走下方縮圖；
            // 要選手機相簿/外部圖則點此關閉 sheet 後叫系統 PhotoPicker。
            OutlinedActionButton(
                label = "從手機相簿選",
                icon = "image",
                onClick = onPickFromGallery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            if (loaded && images.isEmpty()) {
                Text(
                    text = "素材庫還沒有圖片 — 先去生成幾張，或改用上方「+」從手機相簿選。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = images, key = { it.uri.toString() }) { entry ->
                        AsyncImage(
                            model = entry.uri,
                            contentDescription = entry.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onPick(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPreview(url: String, gen: Int = 0, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(url, gen) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }
    // key 要跟 remember(url, gen) 一致,否則 gen 變(同 url 重生)時舊 player 不會 release(洩漏)
    DisposableEffect(url, gen) {
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
        // factory 只跑一次;gen 變→remember 建新 player,要靠 update 重新綁定到 PlayerView,否則畫面停在舊片
        update = { it.player = player },
        modifier = modifier,
    )
}
