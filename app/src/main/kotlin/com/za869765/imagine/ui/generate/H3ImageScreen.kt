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
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3Format
import com.za869765.imagine.data.h3.H3ImageSubmit
import com.za869765.imagine.data.h3.H3Images
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.h3.H3Submit
import com.za869765.imagine.data.h3.PendingH3Image
import com.za869765.imagine.data.prefs.DraftStore
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.CrashLogger
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
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
import com.za869765.imagine.ui.component.SourceSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val H3_IMG_MAX_REFS = 10
private const val H3_IMG_REFS_DRAFT = "h3_image_refs"

/**
 * v2.0.7 H3 Studio 生圖(Qwen-Image 2.1,不走 Grok Imagine)。
 * 流程同 H3 生影:估算(不花錢)→ 確認視窗(勾選才能按)→ POST(confirm、client_request_id 冪等鍵)→ 任務頁輪詢。
 * 機器由 H3 Studio 派:筆電免費 → 開著的 GPUtw／製圖機順便做 → 開製圖機(只搬 Qwen 權重)→ 指定 Colab 才花 CU。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun H3ImageScreen(
    onSwitchToVideo: () -> Unit,
    onSwitchToChat: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onOpenJob: (String) -> Unit,
    engineSelector: @Composable () -> Unit,
    initialPrompt: String? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // 提示詞與 Grok 生圖共用草稿;參考圖另存
    var prompt by rememberSaveable {
        mutableStateOf(initialPrompt?.takeIf { it.isNotBlank() } ?: DraftStore.load(ctx, DraftStore.IMAGE_PROMPT).orEmpty())
    }
    LaunchedEffect(prompt) {
        delay(400)
        DraftStore.save(ctx, DraftStore.IMAGE_PROMPT, prompt)
    }
    var refStrings by rememberSaveable { mutableStateOf(DraftStore.loadList(ctx, H3_IMG_REFS_DRAFT)) }
    LaunchedEffect(refStrings) { DraftStore.saveList(ctx, H3_IMG_REFS_DRAFT, refStrings) }
    val refs = refStrings.map { Uri.parse(it) }

    var aspect by rememberSaveable { mutableStateOf(prefs.h3ImgAspect) }
    var mp by rememberSaveable { mutableStateOf(prefs.h3ImgMp) }
    var count by rememberSaveable { mutableStateOf(prefs.h3ImgCount.coerceIn(1, 4)) }
    var route by rememberSaveable { mutableStateOf(prefs.h3ImgRoute) }
    var allowText by rememberSaveable { mutableStateOf(false) }

    var showLibraryPicker by remember { mutableStateOf(false) }
    var estimating by remember { mutableStateOf(false) }
    // 確認視窗:非 null = 顯示;估算當下的參數一起凍結,送出用的就是使用者看到的那份
    var pendingConfirm by remember { mutableStateOf<PendingH3Image?>(null) }
    var recentJobs by remember { mutableStateOf(H3Jobs.all(ctx).filter { it.kind == "image" }.take(5)) }
    // 送出結果不明(網路中斷/5xx)的那一筆:保留同一個 client_request_id,重送伺服器會去重
    var uncertainPending by remember { mutableStateOf<PendingH3Image?>(null) }

    var droppedNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val readable = refStrings.filter { H3Images.isReadable(ctx, Uri.parse(it)) }
        val dropped = refStrings.size - readable.size
        if (dropped > 0) {
            refStrings = readable
            droppedNote = "已移除 $dropped 張無法再讀取的參考圖（重開 App 後相簿權限失效），請重新選取"
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null && refStrings.size < H3_IMG_MAX_REFS) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            refStrings = refStrings + uri.toString()
        }
    }

    // retry != null:重新估算同一筆(沿用 client_request_id);null:依目前畫面參數開新的一筆(新 id)
    fun startEstimate(retry: PendingH3Image? = null) {
        if (estimating) return
        if (!prefs.isH3TokenSet) {
            Toast.makeText(ctx, "請先到設定填入 H3 Studio 權杖", Toast.LENGTH_LONG).show()
            return
        }
        if (retry == null && prompt.isBlank()) {
            Toast.makeText(ctx, "請先輸入提示詞", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        val snapshot = retry?.copy(estimate = null) ?: PendingH3Image(
            prompt = prompt.trim(),
            refs = refStrings,
            aspect = aspect,
            mp = mp,
            count = count,
            route = route,
            allowText = allowText,
            clientRequestId = java.util.UUID.randomUUID().toString(),
            estimate = null,
        )
        estimating = true
        scope.launch {
            when (val r = H3Client.imageEstimate(prefs, snapshot.mp, snapshot.count, snapshot.route)) {
                is H3Client.Result.Ok -> pendingConfirm = snapshot.copy(estimate = r.value)
                is H3Client.Result.Err -> Toast.makeText(ctx, "估算失敗：${r.message}", Toast.LENGTH_LONG).show()
            }
            estimating = false
        }
    }

    fun handleOutcome(pending: PendingH3Image, outcome: H3Submit.Outcome) {
        recentJobs = H3Jobs.all(ctx).filter { it.kind == "image" }.take(5)
        when (outcome) {
            is H3Submit.Outcome.Created -> {
                pendingConfirm = null
                if (uncertainPending?.clientRequestId == pending.clientRequestId) uncertainPending = null
                Toast.makeText(
                    ctx,
                    if (outcome.duplicate) "伺服器已有這筆任務（沒有重複建單）" else "H3 生圖已送出",
                    Toast.LENGTH_SHORT,
                ).show()
                onOpenJob(outcome.runId)
            }
            is H3Submit.Outcome.Conflict -> {
                // 伺服器 409(餘額未知/不足)= 沒建立任務;顯示訊息後重新估算
                pendingConfirm = null
                Toast.makeText(ctx, outcome.message, Toast.LENGTH_LONG).show()
                startEstimate(pending)
            }
            is H3Submit.Outcome.Rejected -> {
                pendingConfirm = null
                Toast.makeText(ctx, "送出失敗：${outcome.message}", Toast.LENGTH_LONG).show()
            }
            is H3Submit.Outcome.Uncertain -> {
                pendingConfirm = null
                uncertainPending = pending
                Toast.makeText(ctx, "可能已送出，請先看最近生圖（${outcome.message}）", Toast.LENGTH_LONG).show()
            }
            is H3Submit.Outcome.LocalError -> Toast.makeText(ctx, outcome.message, Toast.LENGTH_LONG).show()
            // 生圖沒有送出前重估步驟,這兩種不會發生;保險起見關窗提示
            is H3Submit.Outcome.EstimateChanged, is H3Submit.Outcome.Blocked -> {
                pendingConfirm = null
                Toast.makeText(ctx, "估算已變動，請重新估算", Toast.LENGTH_LONG).show()
            }
        }
    }

    pendingConfirm?.let { pending ->
        H3ImageConfirmSheet(
            pending = pending,
            onDismiss = { pendingConfirm = null },
            onOutcome = { outcome -> handleOutcome(pending, outcome) },
        )
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "生成圖片", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) },
        bottomAction = {
            PrimaryButton(
                label = if (estimating) "估算中…" else "估算並確認（H3 生圖）",
                icon = if (estimating) null else "image",
                loading = estimating,
                enabled = !estimating && prefs.isH3TokenSet && prompt.isNotBlank(),
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
                activeId = "image",
                onSelected = {
                    when (it) {
                        "video" -> onSwitchToVideo()
                        "chat" -> onSwitchToChat()
                    }
                },
                activeColor = Color(0xFF2E3A6E),
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

            uncertainPending?.let { up ->
                ImagineCard(pad = 14) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "上一次送出結果不明（可能已建立任務）：先看下方「最近 H3 生圖」。若沒有，按「以同一筆重新確認」— 伺服器認得同一筆，不會重複建單。",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedActionButton(
                                label = "以同一筆重新確認",
                                enabled = !estimating,
                                onClick = { startEstimate(up) },
                            )
                            OutlinedActionButton(
                                label = "略過",
                                onClick = { uncertainPending = null },
                            )
                        }
                    }
                }
            }

            SourceSlot(
                title = "參考圖（選填，最多 $H3_IMG_MAX_REFS 張）",
                uris = refs,
                maxCount = H3_IMG_MAX_REFS,
                emptyHint = "同一個人／同一件商品要一致時放參考圖；純文生圖可不放",
                onPick = { showLibraryPicker = true },
                onRemove = { index ->
                    refStrings = refStrings.toMutableList().also { if (index < it.size) it.removeAt(index) }
                },
                note = droppedNote ?: "上傳前自動轉正、縮到長邊 1600px 並壓成 JPEG（每張 < 1.5MB）",
            )

            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "例：台灣衛生所大廳，明亮自然光，寫實照片，沒有人；或：這個人換成白袍站在診間，正面微笑",
                minHeight = 88,
                optimizeKind = "image",
                optimizeContext = mapOf("aspect" to aspect, "refs" to refs.size.toString()),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                ParamPicker(
                    label = "長寬比",
                    value = aspect,
                    options = listOf("9:16", "16:9", "1:1", "3:4", "4:3"),
                    onSelect = { aspect = it; prefs.h3ImgAspect = it },
                    displayName = {
                        when (it) {
                            "9:16" -> "直式 9:16"
                            "16:9" -> "橫式 16:9"
                            "1:1" -> "正方 1:1"
                            "3:4" -> "直式 3:4"
                            else -> "橫式 4:3"
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "畫質",
                    value = mp.toString(),
                    options = listOf("1", "4"),
                    onSelect = { mp = if (it == "4") 4 else 1; prefs.h3ImgMp = mp },
                    displayName = { if (it == "4") "2K（${H3Format.imageSize(aspect, 4)}，費時 4 倍）" else "1K（${H3Format.imageSize(aspect, 1)}）" },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "張數",
                    value = count.toString(),
                    options = (1..4).map { it.toString() },
                    onSelect = { count = it.toIntOrNull() ?: 1; prefs.h3ImgCount = count },
                    displayName = { "$it 張" },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "機器",
                    value = route,
                    options = H3Format.IMAGE_ROUTES,
                    onSelect = { route = it; prefs.h3ImgRoute = it },
                    displayName = { H3Format.routeLabel(it) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { allowText = !allowText },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allowText, onCheckedChange = { allowText = it })
                Column {
                    Text("允許畫面出現文字", fontSize = 14.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "沒勾會自動加「無文字、無浮水印」，避免 AI 亂寫字",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ImagineCard(pad = 14) {
                Text(
                    "Qwen-Image 2.1 照片級寫實。按下方按鈕只會先估算（不花錢）：筆電免費、開著的機器順便做、否則開製圖機（約 NT$7 起）；確認視窗勾選後才會真的開跑。成品會進 H3 Studio 的「Imagine APP 生圖」作品，也會自動存進這台手機的所有作品。",
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (recentJobs.isNotEmpty()) {
                SectionHeader("最近 H3 生圖")
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
                                "${H3Format.statusLabel(job.status)}・${job.meta}" +
                                    (if (job.estNtd > 0) "・預估 NT$${H3Format.ntd(job.estNtd)}" else "") +
                                    (if (job.estCu > 0) "・${H3Format.cu(job.estCu)} CU" else ""),
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
                        if (refStrings.size < H3_IMG_MAX_REFS) refStrings = refStrings + entry.uri.toString()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun H3ImageConfirmSheet(
    pending: PendingH3Image,
    onDismiss: () -> Unit,
    onOutcome: (H3Submit.Outcome) -> Unit,
) {
    val e = pending.estimate ?: return
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 勾選綁定「這一份估算」:估算一換(新物件)就重置,必須重新勾
    var agreed by remember(pending) { mutableStateOf(false) }
    var submitting by remember(pending) { mutableStateOf(false) }
    var progressText by remember(pending) { mutableStateOf<String?>(null) }
    val costText = when (e.route) {
        "cloud" -> "${H3Format.cu(e.cu)} CU（約 NT$${H3Format.ntd(e.ntd)}）"
        "local" -> "免費（筆電）"
        else -> "約 NT$${H3Format.ntd(e.ntd)}（GPUtw 按分鐘扣）"
    }
    // 阻擋原因;null = 可以送出(伺服器送出時還會再擋一次)
    val block: String? = when {
        e.route == "cloud" && e.cuBalance == null -> "CU 餘額未知，無法確認是否足夠 — 請先到 H3 Studio 更新餘額"
        e.route == "cloud" && e.cu > (e.cuBalance ?: 0.0) -> "預估 ${H3Format.cu(e.cu)} CU 超過目前餘額 ${H3Format.cu(e.cuBalance ?: 0.0)} CU"
        (e.route == "gputw" || e.route == "gputw_image") && e.ntdBalance != null && e.ntd > e.ntdBalance ->
            "預估 NT$${H3Format.ntd(e.ntd)} 超過 GPUtw 餘額 NT$${H3Format.ntd(e.ntdBalance)}"
        else -> null
    }

    fun submit() {
        if (!agreed || block != null || submitting) return
        submitting = true
        val app = ctx.applicationContext
        // 在 app 層 scope 跑:就算畫面被關掉,已建立的任務也一定會記進最近任務
        ImagineApp.appScope.launch {
            val outcome = try {
                H3ImageSubmit.run(app, prefs, pending) { msg ->
                    ImagineApp.appScope.launch(Dispatchers.Main) { progressText = msg }
                }
            } catch (t: Throwable) {
                CrashLogger.record(app, "H3ImageSubmit.fail", t)
                H3Submit.Outcome.Uncertain(t.message?.take(80) ?: "未預期錯誤")
            }
            withContext(Dispatchers.Main) {
                submitting = false
                progressText = null
                onOutcome(outcome)
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
            Text("確認 H3 生圖", fontSize = 18.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
            ConfirmLine("機器", "${H3Format.routeShort(e.route)}：${e.reason}")
            ConfirmLine(
                "規格",
                "${pending.aspect}・${if (pending.mp == 4) "2K" else "1K"}（${H3Format.imageSize(pending.aspect, pending.mp)}）・${pending.count} 張" +
                    (if (pending.refs.isNotEmpty()) "・參考 ${pending.refs.size} 張" else "") +
                    (if (pending.allowText) "・允許文字" else "・無文字"),
            )
            ConfirmLine("預估時間", "約 ${H3Format.minutes(e.minutes)} 分鐘")
            ConfirmLine("預估費用", costText, strong = true)
            if (e.text.isNotBlank()) ConfirmLine("說明", e.text)
            ConfirmLine("GPUtw 餘額", e.ntdBalance?.let { "NT$${H3Format.ntd(it)}" } ?: "未知")
            ConfirmLine("CU 餘額", e.cuBalance?.let { "${H3Format.cu(it)} CU" } ?: "未知")

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
                        "我已確認：$costText",
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
                "按「取消」不會有任何花費。機器由 H3 Studio 在送出當下決定，開著的機器會順便做、不另外開機。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
