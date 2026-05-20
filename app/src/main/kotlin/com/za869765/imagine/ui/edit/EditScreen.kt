package com.za869765.imagine.ui.edit

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
import com.za869765.imagine.data.billing.BillingState
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class EditMode { ImageEdit, VideoEdit, VideoExtend }

@Composable
fun EditScreen(
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    initialMediaUri: Uri? = null,
    initialPrompt: String? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }

    var mode by remember { mutableStateOf(EditMode.ImageEdit) }
    var prompt by remember { mutableStateOf(initialPrompt.orEmpty()) }
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && initialPrompt != prompt) {
            prompt = initialPrompt
        }
    }
    var sourceUri by remember { mutableStateOf<Uri?>(initialMediaUri) }
    var loading by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0) }
    var resultImageUrl by remember { mutableStateOf<String?>(null) }
    var resultVideoUrl by remember { mutableStateOf<String?>(null) }
    var lastPrompt by remember { mutableStateOf("") }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> if (uri != null) sourceUri = uri }
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

    fun encodeMedia(uri: Uri): String? = runCatching {
        if (uri.scheme == "https" || uri.scheme == "http") return@runCatching uri.toString()
        val mime = ctx.contentResolver.getType(uri)
            ?: if (mode == EditMode.ImageEdit) "image/png" else "video/mp4"
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching null
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrNull()

    fun runExecute() {
        val src = sourceUri
        if (src == null) {
            Toast.makeText(ctx, "請先選擇來源", Toast.LENGTH_SHORT).show()
            return
        }
        if (prompt.isBlank()) {
            Toast.makeText(ctx, "請輸入編輯說明", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            loading = true
            val capturedPrompt = prompt
            val capturedMode = mode

            val encoded = encodeMedia(src)
            if (encoded == null) {
                loading = false
                Toast.makeText(ctx, "讀取來源失敗", Toast.LENGTH_LONG).show()
                return@launch
            }

            when (capturedMode) {
                EditMode.ImageEdit -> {
                    val r = repository.editImage(capturedPrompt, listOf(encoded))
                    loading = false
                    handleImageResult(r, capturedPrompt, ctx, scope) {
                        resultImageUrl = it
                        lastPrompt = capturedPrompt
                    }
                    BillingState.sync(prefs, scope)
                }
                EditMode.VideoEdit, EditMode.VideoExtend -> {
                    val gen = if (capturedMode == EditMode.VideoEdit)
                        repository.editVideo(capturedPrompt, encoded)
                    else
                        repository.extendVideo(capturedPrompt, encoded)
                    if (gen is ApiResult.Error) {
                        loading = false
                        Toast.makeText(ctx, "失敗：${gen.message.take(200)}", Toast.LENGTH_LONG).show()
                        BillingState.sync(prefs, scope)
                        return@launch
                    }
                    val requestId = (gen as ApiResult.Success).value
                    var done = false
                    var attempts = 0
                    while (loading && !done && attempts < 60) {
                        delay(5000)
                        attempts++
                        val poll = repository.pollVideoStatus(requestId)
                        if (poll is ApiResult.Success) {
                            when (poll.value.status) {
                                "done" -> {
                                    val url = poll.value.video?.url
                                    if (url != null) {
                                        resultVideoUrl = url
                                        lastPrompt = capturedPrompt
                                        scope.launch {
                                            MediaSaver.saveVideoFromUrl(ctx, url, capturedPrompt)
                                            Toast.makeText(ctx, "影片完成，已存到相簿", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    done = true
                                }
                                "failed", "expired" -> {
                                    Toast.makeText(ctx, "影片失敗（${poll.value.status}，費用以 xAI 後台為準）", Toast.LENGTH_LONG).show()
                                    done = true
                                }
                                else -> {}
                            }
                        }
                    }
                    if (!done && loading) {
                        Toast.makeText(ctx, "等待超時(5 分鐘) — 費用以 xAI 後台為準", Toast.LENGTH_LONG).show()
                    }
                    loading = false
                    BillingState.sync(prefs, scope)
                }
            }
        }
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "Imagine", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.EDIT, onTabSelected = onNavSelected) },
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
                    val newMode = when (it) {
                        "img" -> EditMode.ImageEdit
                        "vid" -> EditMode.VideoEdit
                        else -> EditMode.VideoExtend
                    }
                    if ((newMode == EditMode.ImageEdit) != (mode == EditMode.ImageEdit)) {
                        sourceUri = null
                        resultImageUrl = null
                        resultVideoUrl = null
                    }
                    mode = newMode
                },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("來源")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            if (sourceUri == null) 1.5.dp else 0.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(12.dp),
                        )
                        .background(MaterialTheme.colorScheme.surfaceDim)
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
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            VideoThumb(uri = src, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)))
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable {
                                    sourceUri = null
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

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("編輯說明")
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
                PrimaryButton(
                    label = "執 行",
                    icon = "edit",
                    enabled = prompt.isNotBlank() && sourceUri != null && prefs.isApiKeySet,
                    onClick = ::runExecute,
                )
            }

            resultImageUrl?.let { url ->
                Text(
                    "結果",
                    fontSize = 11.sp, fontWeight = FontWeight.W600,
                    letterSpacing = 0.08.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ImagineCard(pad = 0) {
                    AsyncImage(
                        model = url,
                        contentDescription = lastPrompt,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            resultVideoUrl?.let { url ->
                Text(
                    "結果",
                    fontSize = 11.sp, fontWeight = FontWeight.W600,
                    letterSpacing = 0.08.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                ImagineCard(pad = 0) {
                    EditVideoPreview(
                        url = url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }
}

private suspend fun handleImageResult(
    r: ApiResult<List<String>>,
    capturedPrompt: String,
    ctx: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: (String) -> Unit,
) {
    when (r) {
        is ApiResult.Success -> {
            val url = r.value.firstOrNull()
            if (url != null) {
                onSuccess(url)
                scope.launch {
                    MediaSaver.saveImageFromUrl(ctx, url, capturedPrompt)
                    Toast.makeText(ctx, "已存到相簿", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(ctx, "未收到結果", Toast.LENGTH_SHORT).show()
            }
        }
        is ApiResult.Error -> {
            Toast.makeText(ctx, "失敗：${r.message.take(200)}", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun VideoThumb(uri: Uri, modifier: Modifier = Modifier) {
    // Show first frame using ExoPlayer paused for content uris too
    EditVideoPreview(url = uri.toString(), modifier = modifier)
}

@Composable
private fun EditVideoPreview(url: String, modifier: Modifier = Modifier) {
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
