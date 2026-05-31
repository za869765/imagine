package com.za869765.imagine.ui.generate

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
import com.za869765.imagine.ui.component.ConfirmHighRiskDialog
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineChip
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.firstHighRiskTerm
import com.za869765.imagine.ui.theme.ImagineCustomShapes
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun GenerateImageScreen(
    onSwitchToVideo: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onAnimateImage: (String, String) -> Unit = { _, _ -> },
    onEditImage: (String, String) -> Unit = { _, _ -> },
    onOpenImageEdit: () -> Unit = {},
    initialPrompt: String? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }
    val focusManager = LocalFocusManager.current

    var prompt by rememberSaveable { mutableStateOf(initialPrompt.orEmpty()) }
    // 從 History「使用此提示詞」帶進來 → 覆蓋目前 prompt
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && initialPrompt != prompt) {
            prompt = initialPrompt
        }
    }
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
    // A2：送出前若偵測到高風險詞,先彈確認;非 null = 顯示對話框,值為命中的詞
    var pendingRiskTerm by remember { mutableStateOf<String?>(null) }
    // v1.0.63 bug#3: 新一輪生成成功時設 true → LaunchedEffect 把畫面捲到底部結果區,
    // 避免「上次結果」已存在時新圖落在 fold 下方使用者看不到。只在「這次生成成功」時觸發,
    // 不用 rememberSaveable 以免進畫面從存檔還原 resultUrls 也跟著亂捲。
    val scrollState = rememberScrollState()
    var pendingScrollToResult by remember { mutableStateOf(false) }
    LaunchedEffect(pendingScrollToResult) {
        if (pendingScrollToResult) {
            try {
                scrollState.animateScrollTo(scrollState.maxValue)
                // 結果圖是非同步載入,高度稍後才長出 → 等高度增加(最多 4 秒)再捲一次,真正落在結果上
                val base = scrollState.maxValue
                withTimeoutOrNull(4000) {
                    snapshotFlow { scrollState.maxValue }.first { it > base }
                }
                scrollState.animateScrollTo(scrollState.maxValue)
            } finally {
                pendingScrollToResult = false
            }
        }
    }

    fun runGenerate() {
        // 點生成 = 自動收鍵盤(避免 IME 佔走畫面看不到生成中/結果)
        focusManager.clearFocus()
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
                        pendingScrollToResult = true  // bug#3: 捲到結果區讓新圖主動出現
                        // v1.0.54 B3: 改用 ImagineApp.appScope (process-lifecycle) — user
                        // 切走/鎖屏時 Composable scope 會 cancel，下載到一半被砍 → History 看不到
                        result.value.forEach { url ->
                            com.za869765.imagine.ImagineApp.appScope.launch {
                                MediaSaver.saveImageFromUrl(ctx, url, capturedPrompt)
                            }
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
                    // 只存 tag,body 太長使用者不需要 — 真要 debug 從 logcat 看
                    lastError = tag
                    lastErrorIsPolicy = (result.kind == ErrorKind.ContentPolicy)
                    Toast.makeText(ctx, tag, Toast.LENGTH_SHORT).show()
                }
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
            // 模式色彩暗示 — 圖片頁藍系細色條(對齊 Hub 圖片卡配色),一眼分辨在哪個模式
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(Color(0xFF23408A)),
            )

            com.za869765.imagine.ui.component.SegmentedTab(
                options = listOf(
                    com.za869765.imagine.ui.component.SegmentedOption("image", "圖片"),
                    com.za869765.imagine.ui.component.SegmentedOption("video", "影片"),
                ),
                activeId = "image",
                onSelected = { if (it == "video") onSwitchToVideo() },
            )

            OutlinedActionButton(
                label = "圖片編輯",
                icon = "edit",
                onClick = onOpenImageEdit,
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

            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                flagged = lastErrorIsPolicy,
            )

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
                onClick = {
                    val term = firstHighRiskTerm(prompt)
                    if (term != null) pendingRiskTerm = term else runGenerate()
                },
            )

            pendingRiskTerm?.let { term ->
                ConfirmHighRiskDialog(
                    term = term,
                    onConfirm = { pendingRiskTerm = null; runGenerate() },
                    onDismiss = { pendingRiskTerm = null },
                )
            }

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
                        ImagineChip(
                            label = "清除",
                            variant = ChipVariant.Tonal,
                            onClick = { lastError = ""; lastErrorIsPolicy = false },
                        )
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
                                        // v1.0.54 B3: 同上改 appScope
                                        com.za869765.imagine.ImagineApp.appScope.launch {
                                            resultUrls.forEach { url ->
                                                MediaSaver.saveImageFromUrl(ctx, url, lastPrompt)
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Toast.makeText(ctx, "已重新存到相簿", Toast.LENGTH_SHORT).show()
                                            }
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

