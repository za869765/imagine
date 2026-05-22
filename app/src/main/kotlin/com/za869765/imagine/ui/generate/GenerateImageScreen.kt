package com.za869765.imagine.ui.generate

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import com.za869765.imagine.data.api.XaiClient
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ErrorKind
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.repo.userFriendlyTag
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.ui.component.CardVariant
import com.za869765.imagine.ui.component.ChipVariant
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineChip
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.theme.ImagineCustomShapes
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.launch

@Composable
fun GenerateImageScreen(
    onSwitchToVideo: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onAnimateImage: (String, String) -> Unit = { _, _ -> },
    onEditImage: (String, String) -> Unit = { _, _ -> },
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }

    var prompt by rememberSaveable { mutableStateOf("") }
    var resolution by rememberSaveable { mutableStateOf("1k") }
    var aspectRatio by rememberSaveable { mutableStateOf("1:1") }
    var n by rememberSaveable { mutableStateOf(1) }
    var quality by rememberSaveable { mutableStateOf("rapid") }  // rapid (快) / quality (好)
    var loading by remember { mutableStateOf(false) }

    var resultUrls by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var lastPrompt by rememberSaveable { mutableStateOf("") }
    var lastMeta by rememberSaveable { mutableStateOf("") }
    var lastError by rememberSaveable { mutableStateOf("") }
    var lastErrorIsPolicy by rememberSaveable { mutableStateOf(false) }

    fun runGenerate() {
        // 點生成 = 開新一輪,先清掉上一輪的錯誤訊息(包含 400 審核紅卡),
        // 不用使用者再去點「清除」
        lastError = ""
        lastErrorIsPolicy = false
        scope.launch {
            loading = true
            val capturedPrompt = prompt
            val capturedRes = resolution
            val capturedAr = aspectRatio
            val capturedN = n
            val capturedModel = if (quality == "quality") "grok-imagine-image-quality" else "grok-imagine-image"
            val result = repository.generateImage(
                prompt = capturedPrompt,
                n = capturedN,
                resolution = capturedRes,
                aspectRatio = capturedAr.takeIf { it != "auto" },
                model = capturedModel,
            )
            loading = false
            when (result) {
                is ApiResult.Success -> {
                    if (result.value.isEmpty()) {
                        // server 回 200 但 data 是空陣列 — 多半是審核擋下卻不回 4xx。
                        // 不寫 lastError 的話 UI 看起來像「成功 0 張」沒解釋。
                        resultUrls = emptyList()
                        lastError = "未收到任何圖片 — 可能被審核擋下（費用以 xAI 後台為準）"
                        Toast.makeText(
                            ctx, "未收到任何圖片（可能被審核擋下）",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        resultUrls = result.value
                        lastPrompt = capturedPrompt
                        lastMeta = "$capturedAr · $capturedRes · ${capturedN} 張"
                        lastError = ""
                        lastErrorIsPolicy = false
                        result.value.forEach { url ->
                            scope.launch { MediaSaver.saveImageFromUrl(ctx, url, capturedPrompt) }
                        }
                        Toast.makeText(
                            ctx,
                            "已生成 ${result.value.size} 張，已存到相簿",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                is ApiResult.Error -> {
                    val tag = result.kind.userFriendlyTag()
                    lastError = "$tag\n${result.message}"
                    lastErrorIsPolicy = (result.kind == ErrorKind.ContentPolicy)
                    Toast.makeText(ctx, "$tag — ${result.message.take(200)}", Toast.LENGTH_LONG).show()
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
            com.za869765.imagine.ui.component.SegmentedTab(
                options = listOf(
                    com.za869765.imagine.ui.component.SegmentedOption("image", "圖片"),
                    com.za869765.imagine.ui.component.SegmentedOption("video", "影片"),
                ),
                activeId = "image",
                onSelected = { if (it == "video") onSwitchToVideo() },
            )

            if (!prefs.isApiKeySet) {
                ImagineCard(pad = 14, onClick = onSettingsClick) {
                    Text(
                        "未設定 API Key — 點此到設定填入或從 Keys 備份匯入",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 19.sp,
                    )
                }
            }

            com.za869765.imagine.ui.component.SegmentedTab(
                options = listOf(
                    com.za869765.imagine.ui.component.SegmentedOption("rapid", "Rapid 快速"),
                    com.za869765.imagine.ui.component.SegmentedOption("quality", "Quality 高品質"),
                ),
                activeId = quality,
                onSelected = { quality = it },
            )

            PromptInput(value = prompt, onValueChange = { prompt = it })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParamPicker(
                    label = "解析度",
                    value = resolution,
                    options = listOf("1k", "2k"),
                    onSelect = { resolution = it },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "長寬比",
                    value = aspectRatio,
                    options = listOf("16:9", "1:1", "9:16", "4:3", "3:4", "3:2", "2:3", "auto"),
                    onSelect = { aspectRatio = it },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "數量",
                    value = n.toString(),
                    options = (1..10).map { it.toString() },
                    onSelect = { n = it.toIntOrNull() ?: 1 },
                    modifier = Modifier.weight(1f),
                )
            }

            PrimaryButton(
                label = if (loading) "生成中…" else "生 成",
                icon = if (loading) null else "auto_awesome",
                loading = loading,
                enabled = prompt.isNotBlank() && !loading && prefs.isApiKeySet,
                onClick = ::runGenerate,
            )

            if (lastError.isNotBlank()) {
                // 審核被拒 (HTTP 400 content policy) 用紅色 errorContainer 突出，
                // 一般錯誤維持 default card style 避免眼花
                val cardBg = if (lastErrorIsPolicy) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
                val cardFg = if (lastErrorIsPolicy) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // tag 已 prefix 在 lastError 第一行,UI title 用通用標籤即可
                            Text(
                                if (lastErrorIsPolicy) "內容審核" else "錯誤訊息(可長按選取)",
                                fontSize = if (lastErrorIsPolicy) 14.sp else 11.sp,
                                fontWeight = FontWeight.W700,
                                letterSpacing = 0.08.sp,
                                color = if (lastErrorIsPolicy) cardFg else MaterialTheme.colorScheme.error,
                            )
                            ImagineChip(
                                label = "清除",
                                variant = ChipVariant.Tonal,
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

            if (resultUrls.isNotEmpty()) {
                Text(
                    text = "上次結果",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W600,
                    letterSpacing = 0.08.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                ImagineCard(pad = 0, variant = CardVariant.Filled) {
                    Column {
                        resultUrls.forEach { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = lastPrompt,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ImagineCustomShapes.Media)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // bug #3: prompt 區用 SelectionContainer 包起來可長按複製，不再截斷
                            SelectionContainer {
                                Text(
                                    text = lastPrompt,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp,
                                )
                            }
                            Text(
                                text = lastMeta,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                ImagineChip(
                                    label = "複製 prompt",
                                    icon = "content_copy",
                                    variant = ChipVariant.Tonal,
                                    onClick = {
                                        Clipboard.copy(ctx, lastPrompt, toastMsg = "已複製 prompt")
                                    },
                                )
                                ImagineChip(
                                    label = "下載",
                                    icon = "download",
                                    variant = ChipVariant.Tonal,
                                    onClick = {
                                        scope.launch {
                                            resultUrls.forEach { url ->
                                                MediaSaver.saveImageFromUrl(ctx, url, lastPrompt)
                                            }
                                            Toast.makeText(ctx, "已重新存到相簿", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                                ImagineChip(
                                    label = "編輯",
                                    icon = "edit",
                                    variant = ChipVariant.Tonal,
                                    onClick = { onEditImage(resultUrls.first(), lastPrompt) },
                                )
                                ImagineChip(
                                    label = "動起來",
                                    icon = "movie",
                                    variant = ChipVariant.Tonal,
                                    onClick = { onAnimateImage(resultUrls.first(), lastPrompt) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

