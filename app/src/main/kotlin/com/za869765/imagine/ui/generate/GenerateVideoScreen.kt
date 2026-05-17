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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.za869765.imagine.data.api.XaiClient
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.data.usage.UsageTracker
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
import com.za869765.imagine.ui.theme.LocalBudgetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class VideoMode { T2V, Img2Vid, Ref }

@Composable
fun GenerateVideoScreen(
    onSwitchToImage: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    initialImageUri: Uri? = null,  // 從圖片頁「動起來」帶過來
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val budgetColors = LocalBudgetColors.current
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }
    val usageTracker = remember(prefs) { UsageTracker(prefs) }

    var prompt by remember { mutableStateOf("") }
    var mode by remember {
        mutableStateOf(if (initialImageUri != null) VideoMode.Img2Vid else VideoMode.T2V)
    }
    var duration by remember { mutableStateOf(8) }
    var aspect by remember { mutableStateOf("16:9") }
    var resolution by remember { mutableStateOf("480p") }
    var sourceImages by remember {
        mutableStateOf(initialImageUri?.let { listOf(it) } ?: emptyList())
    }

    var generating by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var resultVideoUrl by remember { mutableStateOf<String?>(null) }
    var lastPrompt by remember { mutableStateOf("") }
    var currentSpent by remember { mutableStateOf(prefs.spent) }

    val maxImages = if (mode == VideoMode.Img2Vid) 1 else 3
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null && sourceImages.size < maxImages) {
            sourceImages = sourceImages + uri
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
        val mime = ctx.contentResolver.getType(uri) ?: "image/png"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    val estimated = duration * 0.05
    val remaining = (prefs.budgetCap - currentSpent).coerceAtLeast(0.0)
    val affordable = remaining >= estimated

    fun runGenerate() {
        if (mode != VideoMode.T2V && sourceImages.isEmpty()) {
            Toast.makeText(
                ctx,
                if (mode == VideoMode.Img2Vid) "請先選擇起始圖" else "請先選擇至少 1 張參考圖",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        scope.launch {
            generating = true
            usageTracker.tentativeVideo(duration)
            currentSpent = prefs.spent

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
                    usageTracker.refundVideo(capturedDuration)
                    currentSpent = prefs.spent
                    Toast.makeText(
                        ctx,
                        "生成失敗：${gen.message.take(80)}",
                        Toast.LENGTH_LONG,
                    ).show()
                    return@launch
                }
                is ApiResult.Success -> {
                    val requestId = gen.value
                    var done = false
                    var attempts = 0
                    while (generating && !done && attempts < 60) {
                        delay(5000)
                        attempts++
                        val poll = repository.pollVideoStatus(requestId)
                        if (poll is ApiResult.Success) {
                            val s = poll.value
                            when (s.status) {
                                "done" -> {
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
                                    }
                                    done = true
                                }
                                "failed", "expired" -> {
                                    usageTracker.refundVideo(capturedDuration)
                                    currentSpent = prefs.spent
                                    Toast.makeText(
                                        ctx, "影片失敗（${s.status}），已退費",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    done = true
                                }
                                else -> { /* still pending, keep looping */ }
                            }
                        }
                    }
                    if (!done && generating) {
                        usageTracker.refundVideo(capturedDuration)
                        currentSpent = prefs.spent
                        Toast.makeText(ctx, "等待超時，已退費", Toast.LENGTH_LONG).show()
                    }
                    generating = false
                }
            }
        }
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.GENERATE, onTabSelected = onNavSelected) },
        spent = currentSpent,
        budgetCap = prefs.budgetCap,
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
                                    sourceImages = sourceImages
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

            ImagineCard(pad = 14) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            "預估費用", fontSize = 13.sp, fontWeight = FontWeight.W500,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "$" + "%.2f".format(estimated),
                                fontSize = 16.sp, fontWeight = FontWeight.W600,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                " (${duration}s × \$0.05)",
                                fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "剩餘預算", fontSize = 13.sp, fontWeight = FontWeight.W500,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$" + "%.2f".format(remaining),
                            fontSize = 16.sp, fontWeight = FontWeight.W600,
                            fontFamily = FontFamily.Monospace,
                            color = if (affordable) budgetColors.ok else budgetColors.high,
                        )
                    }
                }
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
                    enabled = prompt.isNotBlank() && affordable && prefs.isApiKeySet,
                    onClick = ::runGenerate,
                )
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
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "\"${lastPrompt.take(80)}${if (lastPrompt.length > 80) "..." else ""}\"",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
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
