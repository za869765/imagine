package com.za869765.imagine.ui.generate

import android.net.Uri
import com.za869765.imagine.data.storage.MediaEncoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.OpenRouterRepository
import com.za869765.imagine.ui.component.ModelPickerRow
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ErrorKind
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.repo.userFriendlyTag
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.storage.MediaExporter
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

    var prompt by rememberSaveable { mutableStateOf(initialPrompt.orEmpty()) }
    // initialPrompt 變動 (例如使用者從 History 不同筆動起來) 時覆蓋已存 prompt
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && initialPrompt != prompt) {
            prompt = initialPrompt
        }
    }
    var mode by rememberSaveable {
        mutableStateOf(
            when {
                initialVideoMode == "ref2v" -> VideoMode.Ref2Vid
                initialImageUri != null || initialVideoMode == "i2v" -> VideoMode.Img2Vid
                else -> VideoMode.T2V
            },
        )
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
        mutableStateOf(initialImageUri?.let { listOf(it.toString()) } ?: emptyList())
    }
    val sourceImages = sourceImageStrings.map { Uri.parse(it) }
    // 圖生影「從素材庫選」：true 時彈出素材庫圖片 grid sheet
    var showLibraryPicker by remember { mutableStateOf(false) }
    // 參考圖生影「帶入角色」：true 時彈出角色資產 sheet(v1.7.2)
    var showCharacterPicker by remember { mutableStateOf(false) }

    // trackedRequestId 是 SSOT — process / Composable 重建後從 saveable 恢復,LaunchedEffect
    // 自動重新 observe Worker 並把 generating 設回 true。generating 維持 remember,避免
    // observer 還沒跑就先擋住「生成」按鈕造成 deadlock。
    var generating by remember { mutableStateOf(false) }
    var trackedRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableStateOf(0) }
    var resultVideoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    // 每次新結果 +1,讓 VideoPreview 重建播放器 → 避免 xAI 重用同一 URL 時看到上一支舊片。
    var resultVideoGen by remember { mutableStateOf(0) }
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
                            resultVideoGen++   // A1: 重建播放器顯示這次的新片
                            pendingScrollToResult = true  // bug#3: 捲到結果區讓新影片主動出現
                        }
                        generating = false
                        trackedRequestId = null
                    }
                    WorkInfo.State.FAILED -> {
                        val err = info.outputData.getString(VideoPollWorker.KEY_ERROR)
                        if (!err.isNullOrBlank()) lastError = err
                        resultVideoUrl = null // 失敗→清上次結果,避免誤會舊片是新結果
                        generating = false
                        trackedRequestId = null
                    }
                    WorkInfo.State.CANCELLED -> {
                        // v1.0.54: 補 lastError + toast，否則 user 看到 spinner 突然消失沒任何反饋
                        // 以為「生成失敗」其實是 worker 被 cancel (常見原因：process death + 舊版
                        // recovery 機制；現在 v1.0.54 砍 recovery 後罕見，但保留 feedback)
                        lastError = "影片任務被取消 (可能 app 被系統殺，請重試)"
                        Toast.makeText(ctx, lastError, Toast.LENGTH_LONG).show()
                        resultVideoUrl = null // 取消→清上次結果
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

    // 組合延長 = 進階獨立頁:返回鍵 + 不顯示底欄(不歸屬素材生成 tab)。一般影片頁照舊。
    val isCombineExtend = initialExtendBase != null
    ImagineScreen(
        appBar = {
            if (isCombineExtend) {
                ImagineTopAppBar(
                    title = "🔗 組合延長",
                    showBack = true,
                    onBackClick = { onBack?.invoke() },
                    trailing = { Box(modifier = Modifier.size(40.dp)) },
                )
            } else {
                ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick)
            }
        },
        bottomNav = if (isCombineExtend) {
            null
        } else {
            { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) }
        },
        scrollState = scrollState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // A2: 組合延長專屬精簡流程 — 不進完整圖生影頁,只露尾格+新提示詞+一鍵生成。
            // 尾格已由 VideoFramePicker 帶入為來源圖;成功後 VideoPollWorker 依 initialExtendBase
            // 自動把「原片+續集」串成一支長片存進歷史(組合延長)。
            if (initialExtendBase != null) {
                // 解析度自動沿用原片高度(自動串接需同解析度,否則 MediaMuxer 合成失敗→只剩兩段)
                LaunchedEffect(initialExtendBase) {
                    val h = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val r = android.media.MediaMetadataRetriever()
                        try {
                            r.setDataSource(ctx, android.net.Uri.parse(initialExtendBase))
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F5E57))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "🔗  組合延長",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.White,
                    )
                }
                ImagineCard(pad = 14) {
                    Text(
                        "用原片尾格當起點,輸入新提示詞生成「續集」。完成後會自動把『原片 + 續集』接成一支長片,存到歷史的「組合延長」,不必再手動拼接。",
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sourceImages.firstOrNull()?.let { uri ->
                    SectionHeader("尾格（續接起點）")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectedImageSlot(uri = uri)
                    }
                }
                PromptInput(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = "描述續集要怎麼動…(例:轉身拔劍、鏡頭拉遠)",
                    minHeight = 88,
                    flagged = lastErrorIsPolicy,
                    forVideo = true,
                    videoHasImage = true,
                    videoSourcePrompt = initialPrompt,
                )
                ParamPicker(
                    label = "秒數",
                    value = duration.toString(),
                    options = (1..15).map { it.toString() },
                    onSelect = { duration = it.toIntOrNull() ?: 5 },
                    displayName = { "$it 秒" },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "解析度自動沿用原片（$resolution）— 自動串接需與原片同解析度才能接成一支",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                                "續集生成中…完成會自動接成長片",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            val estSec = (30 + duration * 8).coerceIn(30, 180)
                            val pct = ((elapsed.toFloat() / estSec).coerceIn(0.03f, 0.97f) * 100).toInt()
                            // 大字 = 估算完成百分比(主);經過秒數縮成小字
                            Text(
                                "$pct%",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.W700,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "已 ${"%d:%02d".format(elapsed / 60, elapsed % 60)}（估算,非真實完成率）",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "⚠️ 請勿從「最近應用程式」滑掉 Imagine,否則背景生成與串接會中斷",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                } else {
                    val hasPrompt = prompt.isNotBlank() || !initialPrompt.isNullOrBlank()
                    PrimaryButton(
                        label = "生成續集 → 自動接成長片",
                        icon = "movie",
                        enabled = hasPrompt && sourceImages.isNotEmpty() && prefs.hasKeyFor(provider),
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
                    ImagineCard(pad = 12) {
                        Text(
                            lastError,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                resultVideoUrl?.let { url ->
                    Text(
                        text = "✅ 續集已生成 — 長片已自動接好,去歷史找「組合延長」",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ImagineCard(pad = 0) {
                        VideoPreview(
                            url = url,
                            gen = resultVideoGen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp, max = 480.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }
                return@Column
            }

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

            // 模式 4 選 1:單排可橫滑膠囊(取代原兩排各 2 段+「只有一排高亮」的妥協,痛點 #1)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionHeader(if (provider == ApiProvider.OPENROUTER) "模式・3 選 1" else "模式・5 選 1")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModePill("文生影", videoFn == "gen" && mode == VideoMode.T2V) {
                        videoFn = "gen"; mode = VideoMode.T2V
                    }
                    // 圖生影(首幀):OpenRouter 依模型 supported_frame_images 含 first_frame 才顯示
                    if (provider == ApiProvider.XAI || modelInfo?.frameImages == true) {
                        ModePill("圖生影", videoFn == "gen" && mode == VideoMode.Img2Vid) {
                            videoFn = "gen"; mode = VideoMode.Img2Vid
                        }
                    }
                    ModePill("參考圖生影", videoFn == "gen" && mode == VideoMode.Ref2Vid) {
                        videoFn = "gen"; mode = VideoMode.Ref2Vid
                    }
                    // 影片延長 / 影片編輯 只有 xAI 有 API;OpenRouter 模式下不顯示
                    if (provider == ApiProvider.XAI) {
                        ModePill("影片延長", videoFn == "extend") { videoFn = "extend" }
                        ModePill("影片編輯", videoFn == "edit") { videoFn = "edit" }
                    }
                }
                if (provider == ApiProvider.OPENROUTER) {
                    Text(
                        text = if (modelInfo?.frameImages == true)
                            "參考圖生影(input_references)僅部分 OpenRouter 模型支援(Wan/Seedance/Kling 等),不支援會回錯誤。"
                        else "此模型不支援圖生影(首幀);參考圖生影僅部分模型支援,不支援會回錯誤。",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 影片延長 / 影片編輯 → 內嵌 EditPane(自帶來源選取/執行/結果),其餘生成 UI 不渲染。
            // 同頁同時只渲染一個 EditPane,故 worker observer 不會與生成流程衝突。
            if (videoFn == "extend" || videoFn == "edit") {
                com.za869765.imagine.ui.edit.EditPane(
                    mode = if (videoFn == "extend")
                        com.za869765.imagine.ui.edit.EditMode.VideoExtend
                    else
                        com.za869765.imagine.ui.edit.EditMode.VideoEdit,
                )
                return@Column
            }

            if (mode != VideoMode.T2V) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(
                        if (mode == VideoMode.Ref2Vid) "參考圖（可多張，不會被當成第一幀）" else "起始圖",
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
                            // 點＋直接開素材庫 picker sheet(內含「從手機相簿選」入口 + 素材庫縮圖)
                            AddImageSlot(onClick = { showLibraryPicker = true })
                        }
                    }
                    // 參考圖生影:一鍵帶入「角色資產」整組定妝圖(鎖臉/鎖造型,角色一致性)
                    if (mode == VideoMode.Ref2Vid) {
                        ImagineChip(
                            label = "🎭 帶入角色定妝圖",
                            icon = "star",
                            variant = ChipVariant.Tonal,
                            modifier = Modifier.padding(top = 4.dp),
                            onClick = { showCharacterPicker = true },
                        )
                    }
                    // 起始圖 / 參考圖常駐 chip — Grok 風格：原圖跟 prompt 永遠能拿走，
                    // 不論還沒生成 / 生成中 / 成功 / 400 失敗。
                    if (sourceImages.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamPicker(
                    label = "秒數",
                    value = effDuration.toString(),
                    options = durationOptions.map { it.toString() },
                    onSelect = { duration = it.toIntOrNull() ?: 5 },
                    displayName = { "$it 秒" },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "長寬比",
                    value = effAspect,
                    options = aspectOptions,
                    onSelect = { aspect = it },
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
                        // B3: 估算進度 — 依秒數粗估,非 xAI 真實完成率;封頂 97% 等實際完成。
                        // 大字 = 估算完成 %(主),經過秒數縮成小字。
                        val estSec = (30 + duration * 8).coerceIn(30, 180)
                        val pct = ((elapsed.toFloat() / estSec).coerceIn(0.03f, 0.97f) * 100).toInt()
                        Text(
                            "$pct%",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.W700,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "已 ${"%d:%02d".format(elapsed / 60, elapsed % 60)} · 預估約 $estSec 秒（估算,非真實完成率）;可切背景/鎖屏,完成發通知",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        LinearProgressIndicator(
                            progress = { pct / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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
                    enabled = hasPrompt && prefs.hasKeyFor(provider),
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
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            ) {
                                TextActionButton(
                                    label = "複製",
                                    icon = "content_copy",
                                    onClick = {
                                        Clipboard.copy(ctx, lastPrompt, toastMsg = "已複製 prompt")
                                    },
                                )
                                TextActionButton(
                                    label = "存到相簿",
                                    icon = "download",
                                    onClick = {
                                        com.za869765.imagine.ImagineApp.appScope.launch {
                                            val ok = MediaExporter.saveToGallery(ctx, url, isVideo = true)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                com.za869765.imagine.ui.component.AppNotice.show(if (ok) "已存到相簿" else "存相簿失敗，改用分享試試")
                                            }
                                        }
                                    },
                                )
                                TextActionButton(
                                    label = "分享",
                                    icon = "share",
                                    onClick = {
                                        com.za869765.imagine.ImagineApp.appScope.launch {
                                            MediaExporter.share(ctx, url, isVideo = true)
                                        }
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
                    onPickFromGallery = {
                        showLibraryPicker = false
                        launchPick()
                    },
                    onPick = { entry ->
                        // 圖生影一次一張 → 直接取代來源；用 app 生成的 file URI(xAI 可解析)
                        sourceImageStrings = listOf(entry.uri.toString())
                        showLibraryPicker = false
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

// 影片模式 4 選 1 膠囊(青綠 active + check)。
@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) Color(0xFF16433D) else Color.Transparent)
            .border(
                1.dp,
                if (selected) Color(0xFF56E0D2).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(100.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (selected) {
            ImagineIcon(name = "check", size = 15.dp, fill = 1, tint = Color(0xFF7FE9DD))
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            color = if (selected) Color(0xFF7FE9DD) else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SelectedImageSlot(uri: Uri, onRemove: (() -> Unit)? = null) {
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
        // onRemove == null → 不顯示移除 X(組合延長尾格不可刪,避免無來源圖死路)
        if (onRemove != null) {
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
}
