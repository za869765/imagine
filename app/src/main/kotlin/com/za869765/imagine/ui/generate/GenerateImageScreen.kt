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
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.data.usage.UsageTracker
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
import com.za869765.imagine.ui.theme.LocalBudgetColors
import kotlinx.coroutines.launch

@Composable
fun GenerateImageScreen(
    onSwitchToVideo: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onAnimateImage: (String) -> Unit = {},
    onEditImage: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val budgetColors = LocalBudgetColors.current
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }
    val usageTracker = remember(prefs) { UsageTracker(prefs) }

    var prompt by rememberSaveable { mutableStateOf("") }
    var resolution by rememberSaveable { mutableStateOf("1k") }
    var aspectRatio by rememberSaveable { mutableStateOf("1:1") }
    var n by rememberSaveable { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }

    var currentSpent by remember { mutableStateOf(prefs.spent) }
    var resultUrls by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var lastPrompt by rememberSaveable { mutableStateOf("") }
    var lastMeta by rememberSaveable { mutableStateOf("") }
    var lastError by rememberSaveable { mutableStateOf("") }

    val estimated = 0.05 * n
    val remaining = (prefs.budgetCap - currentSpent).coerceAtLeast(0.0)
    val affordable = remaining >= estimated

    fun runGenerate() {
        scope.launch {
            loading = true
            usageTracker.tentativeImage(n)
            currentSpent = prefs.spent
            val capturedPrompt = prompt
            val capturedRes = resolution
            val capturedAr = aspectRatio
            val capturedN = n
            val result = repository.generateImage(
                prompt = capturedPrompt,
                n = capturedN,
                resolution = capturedRes,
                aspectRatio = capturedAr.takeIf { it != "auto" },
            )
            loading = false
            when (result) {
                is ApiResult.Success -> {
                    resultUrls = result.value
                    lastPrompt = capturedPrompt
                    lastMeta = "$capturedAr · $capturedRes · ${capturedN} 張"
                    lastError = ""
                    result.value.forEach { url ->
                        scope.launch { MediaSaver.saveImageFromUrl(ctx, url, capturedPrompt) }
                    }
                    Toast.makeText(
                        ctx,
                        "已生成 ${result.value.size} 張，已存到相簿",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is ApiResult.Error -> {
                    if (result.kind == ErrorKind.Network) {
                        usageTracker.refundImage(capturedN)
                        currentSpent = prefs.spent
                    }
                    val tag = when (result.kind) {
                        ErrorKind.Unauthorized -> "API Key 無效"
                        ErrorKind.RateLimited -> "請求太頻繁"
                        ErrorKind.ContentPolicy -> "審核或請求被拒（費用以 xAI 後台為準）"
                        ErrorKind.Network -> "網路錯誤（已退費）"
                        ErrorKind.Server -> "xAI 伺服器錯誤"
                        ErrorKind.Unknown -> "失敗"
                    }
                    lastError = "$tag\n${result.message}"
                    Toast.makeText(ctx, "$tag — ${result.message.take(200)}", Toast.LENGTH_LONG).show()
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
            com.za869765.imagine.ui.component.SegmentedTab(
                options = listOf(
                    com.za869765.imagine.ui.component.SegmentedOption("image", "圖片"),
                    com.za869765.imagine.ui.component.SegmentedOption("video", "影片"),
                ),
                activeId = "image",
                onSelected = { if (it == "video") onSwitchToVideo() },
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
                    options = (1..4).map { it.toString() },
                    onSelect = { n = it.toIntOrNull() ?: 1 },
                    modifier = Modifier.weight(1f),
                )
            }

            ImagineCard(pad = 14) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("預估費用", fontSize = 13.sp, fontWeight = FontWeight.W500, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$" + "%.2f".format(estimated),
                            fontSize = 16.sp, fontWeight = FontWeight.W600,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("剩餘預算", fontSize = 13.sp, fontWeight = FontWeight.W500, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$" + "%.2f".format(remaining),
                            fontSize = 16.sp, fontWeight = FontWeight.W600,
                            fontFamily = FontFamily.Monospace,
                            color = if (affordable) budgetColors.ok else budgetColors.high,
                        )
                    }
                }
            }

            PrimaryButton(
                label = if (loading) "生成中…" else "生 成",
                icon = if (loading) null else "auto_awesome",
                loading = loading,
                enabled = prompt.isNotBlank() && affordable && !loading && prefs.isApiKeySet,
                onClick = ::runGenerate,
            )

            if (lastError.isNotBlank()) {
                ImagineCard(pad = 14) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "錯誤訊息（可長按選取）",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.W600,
                                letterSpacing = 0.08.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                            ImagineChip(
                                label = "清除",
                                variant = ChipVariant.Tonal,
                                onClick = { lastError = "" },
                            )
                        }
                        SelectionContainer {
                            Text(
                                lastError,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp,
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
                            Text(
                                text = "\"${lastPrompt.take(80)}${if (lastPrompt.length > 80) "..." else ""}\"",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp,
                            )
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
                                    onClick = { onEditImage(resultUrls.first()) },
                                )
                                ImagineChip(
                                    label = "動起來",
                                    icon = "movie",
                                    variant = ChipVariant.Tonal,
                                    onClick = { onAnimateImage(resultUrls.first()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

