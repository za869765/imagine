package com.za869765.imagine.ui.generate

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3Estimate
import com.za869765.imagine.data.h3.H3Format
import com.za869765.imagine.data.h3.H3Images
import com.za869765.imagine.data.h3.H3JobImage
import com.za869765.imagine.data.h3.H3JobRequest
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.prefs.DraftStore
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.ModeOption
import com.za869765.imagine.ui.component.ModePicker
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.SourceSlot
import kotlinx.coroutines.launch

private const val H3_MAX_IMAGES = 9
private const val H3_SOURCES_DRAFT = "h3_video_sources"

/**
 * v2.0.0 H3 Studio 生影(MiniMax H3 on Colab A100)。
 * ⚠️ 使用者最高原則:任何會花 Colab CU 的動作,都必須先顯示估算明細並由使用者勾選確認才送出。
 * 流程:估算(不花錢)→ 確認視窗(勾選才能按)→ POST(confirm=true, est_cu)→ 任務頁輪詢。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun H3GenerateScreen(
    onSwitchToImage: () -> Unit,
    onSwitchToChat: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onOpenJob: (String) -> Unit,
    engineSelector: @Composable () -> Unit,
    initialImageUri: Uri? = null,
    initialPrompt: String? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 提示詞與 Grok 引擎共用草稿;來源圖另存(H3 可到 9 張,Grok 參考圖上限 3)
    var prompt by rememberSaveable {
        mutableStateOf(initialPrompt?.takeIf { it.isNotBlank() } ?: DraftStore.load(ctx, DraftStore.VIDEO_PROMPT).orEmpty())
    }
    LaunchedEffect(prompt) {
        kotlinx.coroutines.delay(400)
        DraftStore.save(ctx, DraftStore.VIDEO_PROMPT, prompt)
    }
    var sourceStrings by rememberSaveable {
        mutableStateOf(initialImageUri?.let { listOf(it.toString()) } ?: DraftStore.loadList(ctx, H3_SOURCES_DRAFT))
    }
    LaunchedEffect(sourceStrings) { DraftStore.saveList(ctx, H3_SOURCES_DRAFT, sourceStrings) }
    val sources = sourceStrings.map { Uri.parse(it) }

    var mode by rememberSaveable { mutableStateOf(prefs.h3Mode) }
    var quality by rememberSaveable { mutableStateOf(prefs.h3Quality) }
    var seconds by rememberSaveable { mutableStateOf(prefs.h3Seconds.coerceIn(4, 15)) }
    var aspect by rememberSaveable { mutableStateOf(prefs.h3Aspect) }

    var showLibraryPicker by remember { mutableStateOf(false) }
    var estimating by remember { mutableStateOf(false) }
    // 確認視窗:非 null = 顯示;估算當下的參數一起凍結,送出用的就是使用者看到的那份
    var pendingConfirm by remember { mutableStateOf<PendingH3?>(null) }
    var recentJobs by remember { mutableStateOf(H3Jobs.all(ctx).take(5)) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null && sourceStrings.size < H3_MAX_IMAGES) sourceStrings = sourceStrings + uri.toString()
    }

    fun startEstimate() {
        if (!prefs.isH3TokenSet) {
            Toast.makeText(ctx, "請先到設定填入 H3 Studio 權杖", Toast.LENGTH_LONG).show()
            return
        }
        if (prompt.isBlank()) {
            Toast.makeText(ctx, "請先輸入提示詞", Toast.LENGTH_SHORT).show()
            return
        }
        if (sources.isEmpty()) {
            Toast.makeText(ctx, if (mode == "first") "請先選擇首幀圖" else "請先選擇參考圖（1～9 張）", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        val snapshot = PendingH3(prompt.trim(), sourceStrings, seconds, quality, mode, aspect, null)
        estimating = true
        scope.launch {
            when (val r = H3Client.estimate(prefs, snapshot.seconds, snapshot.quality)) {
                is H3Client.Result.Ok -> pendingConfirm = snapshot.copy(estimate = r.value)
                is H3Client.Result.Err -> Toast.makeText(ctx, "估算失敗：${r.message}", Toast.LENGTH_LONG).show()
            }
            estimating = false
        }
    }

    pendingConfirm?.let { pending ->
        H3ConfirmSheet(
            pending = pending,
            onDismiss = { pendingConfirm = null },
            onSubmitted = { runId ->
                pendingConfirm = null
                recentJobs = H3Jobs.all(ctx).take(5)
                onOpenJob(runId)
            },
        )
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "生成影片", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) },
        bottomAction = {
            PrimaryButton(
                label = if (estimating) "估算中…" else "估算並確認（H3）",
                icon = if (estimating) null else "movie",
                loading = estimating,
                enabled = !estimating && prefs.isH3TokenSet && prompt.isNotBlank() && sources.isNotEmpty(),
                onClick = { startEstimate() },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
            engineSelector()

            if (!prefs.isH3TokenSet) {
                ImagineCard(pad = 14, onClick = onSettingsClick) {
                    Text(
                        "未設定 H3 Studio 權杖 — 點此到「設定 › API 與用量」填入",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 19.sp,
                    )
                }
            }

            ModePicker(
                options = listOf(
                    ModeOption("ref", "參考圖（Ref2VA）", "1～9 張參考圖鎖角色／場景，不會被當成第一幀"),
                    ModeOption("first", "首幀", "第一張圖當影片第一幀，其餘當參考"),
                ),
                selectedId = mode,
                onSelect = { mode = it; prefs.h3Mode = it },
            )

            SourceSlot(
                title = if (mode == "first") "首幀圖（第一張為第一幀）" else "參考圖（1～9 張）",
                uris = sources,
                maxCount = H3_MAX_IMAGES,
                emptyHint = "點此從素材庫或手機相簿選取",
                onPick = { showLibraryPicker = true },
                onRemove = { index ->
                    sourceStrings = sourceStrings.toMutableList().also { if (index < it.size) it.removeAt(index) }
                },
                note = "上傳前自動縮到長邊 1600px 並壓成 JPEG（每張 < 1.5MB）",
            )

            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "描述要怎麼動...",
                minHeight = 88,
                forVideo = true,
                videoHasImage = true,
                videoSourcePrompt = initialPrompt,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                ParamPicker(
                    label = "品質",
                    value = quality,
                    options = listOf("normal", "fast"),
                    onSelect = { quality = it; prefs.h3Quality = it },
                    displayName = { H3Format.qualityLabel(it) },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "秒數",
                    value = seconds.toString(),
                    options = (4..15).map { it.toString() },
                    onSelect = { seconds = it.toIntOrNull() ?: 5; prefs.h3Seconds = seconds },
                    displayName = { "$it 秒" },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "長寬比",
                    value = aspect,
                    options = listOf("9:16", "16:9"),
                    onSelect = { aspect = it; prefs.h3Aspect = it },
                    displayName = { if (it == "9:16") "直式 9:16" else "橫式 16:9" },
                    modifier = Modifier.weight(1f),
                )
            }

            ImagineCard(pad = 14) {
                Text(
                    "H3 在 Colab A100 上跑，會消耗 Colab CU（付費）。按下方按鈕只會先估算（不花錢），確認視窗勾選後才會真的開跑。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (recentJobs.isNotEmpty()) {
                SectionHeader("最近 H3 任務")
                recentJobs.forEach { job ->
                    ImagineCard(pad = 12, onClick = { onOpenJob(job.runId) }) {
                        Column {
                            Text(
                                job.prompt,
                                fontSize = 13.sp,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "${H3Format.statusLabel(job.status)}・${H3Format.qualityLabel(job.quality)}・${job.seconds} 秒・預估 ${H3Format.cu(job.estCu)} CU",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }

            if (showLibraryPicker) {
                LibraryImagePickerSheet(
                    onDismiss = { showLibraryPicker = false },
                    onPick = { entry ->
                        if (sourceStrings.size < H3_MAX_IMAGES) sourceStrings = sourceStrings + entry.uri.toString()
                        showLibraryPicker = false
                    },
                    onPickFromGallery = {
                        showLibraryPicker = false
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
        }
    }
}

/** 估算當下凍結的送出參數(確認視窗看到什麼就送什麼)。 */
data class PendingH3(
    val prompt: String,
    val sources: List<String>,
    val seconds: Int,
    val quality: String,
    val mode: String,
    val aspect: String,
    val estimate: H3Estimate?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun H3ConfirmSheet(
    pending: PendingH3,
    onDismiss: () -> Unit,
    onSubmitted: (String) -> Unit,
) {
    val e = pending.estimate ?: return
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var agreed by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    val block = H3Format.blockReason(e)

    fun submit() {
        if (!agreed || block != null || submitting) return
        submitting = true
        scope.launch {
            try {
                val images = ArrayList<H3JobImage>()
                for ((i, s) in pending.sources.withIndex()) {
                    progressText = "壓縮圖片 ${i + 1}/${pending.sources.size}…"
                    val img = H3Images.encode(ctx, Uri.parse(s), i)
                    if (img == null) {
                        Toast.makeText(ctx, "第 ${i + 1} 張圖讀取或壓縮失敗，未送出（沒有花費）", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    images.add(img)
                }
                progressText = "送出中…"
                val req = H3JobRequest(
                    prompt = pending.prompt,
                    images = images,
                    seconds = pending.seconds,
                    quality = pending.quality,
                    mode = pending.mode,
                    aspect = pending.aspect,
                    confirm = true,
                    estCu = e.cu,
                )
                when (val r = H3Client.submit(prefs, req)) {
                    is H3Client.Result.Ok -> {
                        H3Jobs.add(
                            ctx,
                            H3Jobs.Job(
                                runId = r.value.runId,
                                prompt = pending.prompt,
                                quality = pending.quality,
                                seconds = pending.seconds,
                                estCu = e.cu,
                                createdAtMs = System.currentTimeMillis(),
                            ),
                        )
                        Toast.makeText(ctx, "H3 任務已送出", Toast.LENGTH_SHORT).show()
                        onSubmitted(r.value.runId)
                    }
                    is H3Client.Result.Err -> Toast.makeText(ctx, "送出失敗：${r.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                submitting = false
                progressText = null
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!submitting) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("確認 H3 生成", fontSize = 18.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
            ConfirmLine("品質", H3Format.qualityLabel(e.quality))
            ConfirmLine("影片長度", "${pending.seconds} 秒（實際約 ${H3Format.minutes(e.videoSeconds)} 秒）")
            ConfirmLine(
                "冷啟動",
                if (e.gpuAlive) "GPU 已開機不用冷啟動" else "需要冷啟動（約 ${H3Format.minutes(e.coldMin)} 分鐘）",
            )
            ConfirmLine("GPU 時間", "約 ${H3Format.minutes(e.minutes)} 分鐘")
            ConfirmLine("預估消耗", "${H3Format.cu(e.cu)} CU（約 NT$${H3Format.ntd(e.ntd)}）", strong = true)
            ConfirmLine(
                "目前餘額",
                e.balance?.let { "${H3Format.cu(it)} CU（${H3Format.ago(e.balanceUpdatedAt)}）" } ?: "未知",
            )
            ConfirmLine("跑完後預估剩餘", e.after?.let { "${H3Format.cu(it)} CU" } ?: "未知")
            ConfirmLine(
                "剩餘還能生成",
                e.affordableSecondsAfter?.let {
                    "正常版 ${H3Format.seconds(it.normal)}／快速版 ${H3Format.seconds(it.fast)}"
                } ?: "未知",
            )
            ConfirmLine("圖片", "${pending.sources.size} 張・${if (pending.mode == "first") "首幀" else "參考圖"}・${pending.aspect}")

            if (block != null) {
                Text(
                    block,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 19.sp,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !submitting) { agreed = !agreed },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = agreed, onCheckedChange = { if (!submitting) agreed = it })
                    Text(
                        "我已確認：預估消耗 ${H3Format.cu(e.cu)} CU（約 NT$${H3Format.ntd(e.ntd)}）",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            progressText?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedActionButton(
                    label = "取消",
                    onClick = onDismiss,
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                PrimaryButton(
                    label = "確認開跑",
                    loading = submitting,
                    enabled = agreed && block == null,
                    onClick = { submit() },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "按「取消」不會有任何花費。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmLine(label: String, value: String, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp),
        )
        Text(
            value,
            fontSize = if (strong) 15.sp else 13.sp,
            fontWeight = if (strong) FontWeight.W700 else FontWeight.W500,
            color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
