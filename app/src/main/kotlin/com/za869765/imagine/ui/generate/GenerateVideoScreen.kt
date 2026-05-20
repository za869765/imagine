package com.za869765.imagine.ui.generate

import android.net.Uri
import android.util.Base64
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.za869765.imagine.data.api.XaiClient
import com.za869765.imagine.data.billing.BillingState
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ErrorKind
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.util.Clipboard
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
    var duration by rememberSaveable { mutableStateOf(8) }
    var aspect by rememberSaveable { mutableStateOf("1:1") }
    var resolution by rememberSaveable { mutableStateOf("480p") }
    // sourceImages 是 List<Uri> — Uri 本身可序列化,但 List<Uri> 沒 Saver,改存字串 list
    var sourceImageStrings by rememberSaveable {
        mutableStateOf(initialImageUri?.let { listOf(it.toString()) } ?: emptyList())
    }
    val sourceImages = sourceImageStrings.map { Uri.parse(it) }

    var generating by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var resultVideoUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPrompt by rememberSaveable { mutableStateOf("") }
    var lastError by rememberSaveable { mutableStateOf("") }
    var lastErrorIsPolicy by rememberSaveable { mutableStateOf(false) }

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

    fun encodeImage(uri: Uri): String? = runCatching {
        // xAI REST 對 image.url 規格: 只接受 public https URL。
        // 從「動起來」/「當參考圖」入口帶進來的 uri 本來就是 xAI 圖片生成回傳的
        // https URL — 直接傳字串，不要 contentResolver re-encode (對 https uri
        // ContentResolver 會回 null，結果整個 image 欄位變 null 等於沒帶圖)。
        val scheme = uri.scheme?.lowercase()
        if (scheme == "http" || scheme == "https") return@runCatching uri.toString()
        val mime = ctx.contentResolver.getType(uri) ?: "image/png"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

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
        scope.launch {
            generating = true

            val capturedPrompt = prompt
            val capturedMode = mode
            val capturedDuration = duration

            val starting = if (capturedMode == VideoMode.Img2Vid) {
                sourceImages.firstOrNull()?.let { encodeImage(it) }
            } else null
            val refs = if (capturedMode == VideoMode.Ref) {
                sourceImages.mapNotNull { encodeImage(it) }.takeIf { it.isNotEmpty() }
            } else null

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
                    val tag = when (gen.kind) {
                        ErrorKind.Unauthorized -> "API Key 無效"
                        ErrorKind.RateLimited -> "請求太頻繁"
                        ErrorKind.ContentPolicy -> "🚨 內容審核被拒（HTTP 400）"
                        ErrorKind.Network -> "網路錯誤"
                        ErrorKind.Server -> "xAI 伺服器錯誤"
                        ErrorKind.Unknown -> "送出失敗"
                    }
                    lastError = "$tag\n${gen.message}"
                    lastErrorIsPolicy = (gen.kind == ErrorKind.ContentPolicy)
                    Toast.makeText(ctx, "$tag — ${gen.message.take(200)}", Toast.LENGTH_LONG).show()
                    BillingState.sync(prefs, scope)
                    return@launch
                }
                is ApiResult.Success -> {
                    val requestId = gen.value
                    var done = false
                    var attempts = 0
                    var pollErrors = 0
                    // pending 系列只有這幾個算「還在跑」；其他狀態（含 xAI 沒文件化的）都當失敗終止，
                    // 避免被審核擋下後 status 不是 "failed" 而是 "rejected" / "moderation_failed" 等
                    // 不在白名單就無限 pending。
                    val pendingStatuses = setOf(
                        "pending", "queued", "processing", "running",
                        "in_progress", "starting", "generating",
                    )
                    while (generating && !done && attempts < 60) {
                        delay(5000)
                        attempts++
                        when (val poll = repository.pollVideoStatus(requestId)) {
                            is ApiResult.Success -> {
                                pollErrors = 0
                                val s = poll.value
                                val status = s.status.lowercase()
                                when {
                                    status in setOf("done", "succeeded", "completed") -> {
                                        val url = s.video?.url
                                        if (url != null) {
                                            resultVideoUrl = url
                                            lastPrompt = capturedPrompt
                                            scope.launch {
                                                MediaSaver.saveVideoFromUrl(ctx, url, capturedPrompt)
                                                Toast.makeText(
                                                    ctx, "影片完成，已存到相簿", Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        } else {
                                            lastError = "影片回報 ${s.status} 但沒拿到 URL（費用以 xAI 後台為準）"
                                        }
                                        done = true
                                    }
                                    status in pendingStatuses -> { /* 還在跑 */ }
                                    else -> {
                                        val msg = s.error?.let { "\n$it" } ?: ""
                                        lastError = "影片任務 ${s.status}（費用以 xAI 後台為準）$msg"
                                        Toast.makeText(
                                            ctx, "影片失敗（${s.status}）${msg.take(180)}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                        done = true
                                    }
                                }
                            }
                            is ApiResult.Error -> {
                                pollErrors++
                                if (pollErrors >= 3 || poll.kind == ErrorKind.Unauthorized) {
                                    lastError = "輪詢失敗（${poll.kind}）\n${poll.message}"
                                    Toast.makeText(
                                        ctx,
                                        "輪詢失敗（${poll.kind}）：${poll.message.take(200)}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    done = true
                                }
                            }
                        }
                    }
                    if (!done && generating) {
                        lastError = "等待超時（5 分鐘）— 任務可能仍在 xAI 後台執行，費用以 xAI 後台為準"
                        Toast.makeText(
                            ctx, "等待超時（5 分鐘）— 費用以 xAI 後台為準",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    generating = false
                    BillingState.sync(prefs, scope)
                }
            }
        }
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.GENERATE, onTabSelected = onNavSelected) },
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
                }
            }

            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "描述要怎麼動...",
                minHeight = 88,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamPicker(
                    label = "秒數",
                    value = duration.toString(),
                    options = (1..15).map { it.toString() },
                    onSelect = { duration = it.toIntOrNull() ?: 8 },
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
                            "預計 30–90 秒，請保持畫面開啟",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedActionButton(
                            label = "取消生成",
                            onClick = { generating = false },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                PrimaryButton(
                    label = "生 成",
                    icon = "movie",
                    enabled = prompt.isNotBlank() && prefs.isApiKeySet,
                    onClick = ::runGenerate,
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
                        .padding(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (lastErrorIsPolicy) "🚨 審核被拒（HTTP 400）" else "錯誤訊息（可長按選取）",
                                fontSize = if (lastErrorIsPolicy) 14.sp else 11.sp,
                                fontWeight = FontWeight.W700,
                                letterSpacing = 0.08.sp,
                                color = if (lastErrorIsPolicy) cardFg else MaterialTheme.colorScheme.error,
                            )
                            OutlinedActionButton(
                                label = "清除",
                                onClick = { lastError = ""; lastErrorIsPolicy = false },
                            )
                        }
                        SelectionContainer {
                            Text(
                                lastError,
                                fontSize = if (lastErrorIsPolicy) 13.sp else 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = cardFg,
                                lineHeight = 20.sp,
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
