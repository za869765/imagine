package com.za869765.imagine.ui.generate

import com.za869765.imagine.ui.component.ImagineIcon
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
import com.za869765.imagine.data.h3.H3Estimate
import com.za869765.imagine.data.h3.H3Format
import com.za869765.imagine.data.h3.H3Images
import com.za869765.imagine.data.h3.H3Jobs
import com.za869765.imagine.data.h3.H3Submit
import com.za869765.imagine.data.h3.PendingH3
import com.za869765.imagine.data.prefs.DraftStore
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.CrashLogger
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
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.SourceSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val H3_MAX_IMAGES = 9
private const val H3_SOURCES_DRAFT = "h3_video_sources"

/**
 * v2.0.0 H3 Studio 生影(MiniMax H3 on Colab A100)。
 * ⚠️ 使用者最高原則:任何會花 Colab CU 的動作,都必須先顯示估算明細並由使用者勾選確認才送出。
 * 流程:估算(不花錢)→ 確認視窗(勾選才能按)→ 送出前再估一次(變了就重新確認)→ POST(confirm、est_cu、
 * client_request_id 冪等鍵)→ 任務頁輪詢。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun H3GenerateScreen(
    onSwitchToImage: () -> Unit,
    onSwitchToChat: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenImageJob: (String) -> Unit = {},
    engineSelector: @Composable () -> Unit,
    initialImageUri: Uri? = null,
    initialPrompt: String? = null,
    onGrokEdit: ((String, String) -> Unit)? = null,
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
    // v2.1.4 三級:超高畫質 bf16／高畫質 int8／低畫質 Q4;預設高畫質(經濟實用)
    var tier by rememberSaveable { mutableStateOf(prefs.h3VideoTier) }
    var blink by rememberSaveable { mutableStateOf(true) }
    var colabGpus by rememberSaveable { mutableStateOf(prefs.h3ColabGpus) }
    // v2.1.8 長片段數(1～4,每段接續上一段)與每段內容(空白＝導演大腦從主描述拆);資產庫角色(鎖臉鎖聲)
    var segCount by rememberSaveable { mutableStateOf(1) }
    var beats by rememberSaveable { mutableStateOf(List(4) { "" }) }
    var subjects by remember { mutableStateOf<List<com.za869765.imagine.data.h3.H3Subject>>(emptyList()) }
    var subjectId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (prefs.isH3TokenSet) (H3Client.subjects(prefs) as? H3Client.Result.Ok)?.value?.let { subjects = it.subjects }
    }
    // v2.1.9 機器看板＋素材小卡(預設收起)
    val board = rememberMachineBoard(prefs)
    var assetsOpen by rememberSaveable { mutableStateOf(true) }
    var advOpen by rememberSaveable { mutableStateOf(false) }  // v2.2.1 預設展開（使用者：縮起來會找不到上傳位置）
    var seconds by rememberSaveable { mutableStateOf(prefs.h3Seconds.coerceIn(4, 15)) }
    var aspect by rememberSaveable { mutableStateOf(prefs.h3Aspect) }

    var showLibraryPicker by remember { mutableStateOf(false) }
    var estimating by remember { mutableStateOf(false) }
    // 確認視窗:非 null = 顯示;估算當下的參數一起凍結,送出用的就是使用者看到的那份
    var pendingConfirm by remember { mutableStateOf<PendingH3?>(null) }
    var recentJobs by remember { mutableStateOf(H3Jobs.all(ctx).filter { it.kind != "image" }.take(5)) }
    // 送出結果不明(網路中斷/5xx)的那一筆:保留同一個 client_request_id,「以同一筆重新確認」時伺服器會去重
    var uncertainPending by remember { mutableStateOf<PendingH3?>(null) }

    // 草稿裡的相簿圖重開 App 後可能失去權限 → 進頁檢查,讀不到的移除並提示
    var droppedNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val readable = sourceStrings.filter { H3Images.isReadable(ctx, Uri.parse(it)) }
        val dropped = sourceStrings.size - readable.size
        if (dropped > 0) {
            sourceStrings = readable
            droppedNote = "已移除 $dropped 張無法再讀取的圖（重開 App 後相簿權限失效），請重新選取"
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null && sourceStrings.size < H3_MAX_IMAGES) {
            // 能拿就拿長期讀取權限,草稿重開 App 後仍讀得到
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            sourceStrings = sourceStrings + uri.toString()
        }
    }

    // retry != null:重新估算同一筆(沿用 client_request_id);null:依目前畫面參數開新的一筆(新 id)
    fun startEstimate(retry: PendingH3? = null) {
        if (estimating) return
        if (!prefs.isH3TokenSet) {
            Toast.makeText(ctx, "請先到設定填入 H3 Studio 權杖", Toast.LENGTH_LONG).show()
            return
        }
        if (retry == null && prompt.isBlank()) {
            Toast.makeText(ctx, "請先輸入提示詞", Toast.LENGTH_SHORT).show()
            return
        }
        if (retry == null && sources.isEmpty() && subjectId == null) {
            Toast.makeText(ctx, if (mode == "first") "請先選擇首幀圖" else "請先選擇參考圖（1～9 張）", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        val snapshot = retry?.copy(estimate = null) ?: PendingH3(
            prompt = prompt.trim(),
            sources = sourceStrings,
            seconds = if (tier == "low") seconds.coerceAtMost(8) else seconds,
            quality = H3Format.tierQuality(tier),
            mode = mode,
            aspect = aspect,
            route = H3Format.tierRoute(tier),
            blink = blink,
            gpus = colabGpus,
            segments = segCount,
            beats = beats.take(segCount),
            subjectId = subjectId,
            subjectName = subjects.firstOrNull { it.id == subjectId }?.name,
            clientRequestId = java.util.UUID.randomUUID().toString(),
            estimate = null,
        )
        estimating = true
        scope.launch {
            when (val r = H3Client.estimate(prefs, snapshot.seconds, snapshot.quality, snapshot.route, snapshot.segments)) {
                is H3Client.Result.Ok -> pendingConfirm = snapshot.copy(estimate = r.value)
                is H3Client.Result.Err -> Toast.makeText(ctx, "估算失敗：${r.message}", Toast.LENGTH_LONG).show()
            }
            estimating = false
        }
    }

    fun handleOutcome(pending: PendingH3, outcome: H3Submit.Outcome) {
        recentJobs = H3Jobs.all(ctx).filter { it.kind != "image" }.take(5)
        when (outcome) {
            is H3Submit.Outcome.Created -> {
                pendingConfirm = null
                if (uncertainPending?.clientRequestId == pending.clientRequestId) uncertainPending = null
                Toast.makeText(
                    ctx,
                    if (outcome.duplicate) "伺服器已有這筆任務（沒有重複扣費）" else "H3 任務已送出",
                    Toast.LENGTH_SHORT,
                ).show()
                onOpenJob(outcome.runId)
            }
            // 新物件 → 確認勾選(依 pending 記憶)自動重置,必須重新勾
            is H3Submit.Outcome.EstimateChanged -> {
                pendingConfirm = pending.copy(estimate = outcome.fresh)
                Toast.makeText(ctx, "估算已變動（CU 或 GPU 狀態），請重新確認", Toast.LENGTH_LONG).show()
            }
            is H3Submit.Outcome.Blocked -> {
                pendingConfirm = pending.copy(estimate = outcome.fresh)
                Toast.makeText(ctx, outcome.message, Toast.LENGTH_LONG).show()
            }
            is H3Submit.Outcome.Conflict -> {
                // 伺服器 409 = 沒建立任務;顯示伺服器訊息後回到新的估算重新確認
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
                Toast.makeText(ctx, "可能已送出，請先看最近任務（${outcome.message}）", Toast.LENGTH_LONG).show()
            }
            is H3Submit.Outcome.LocalError -> Toast.makeText(ctx, outcome.message, Toast.LENGTH_LONG).show()
        }
    }

    pendingConfirm?.let { pending ->
        H3ConfirmSheet(
            pending = pending,
            onDismiss = { pendingConfirm = null },
            onOutcome = { outcome -> handleOutcome(pending, outcome) },
            onRefreshed = { fresh -> pendingConfirm = pending.copy(estimate = fresh) },
        )
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "生成影片", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) },
        bottomAction = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            board.firstOrNull { it.route == H3Format.tierRoute(tier) }?.let { m ->
                Text("${H3Format.tierName(tier)}・${m.name}：${m.label}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
            PrimaryButton(
                label = if (estimating) "估算中…" else "估算並確認（H3）",
                icon = if (estimating) null else "movie",
                loading = estimating,
                enabled = !estimating && prefs.isH3TokenSet && prompt.isNotBlank() && sources.isNotEmpty(),
                onClick = { startEstimate() },
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

            // v2.1.9 下單前先看哪台空著:筆電(低畫質)、影片機(高畫質)、Colab(超高畫質)
            MachineChips(board, listOf("gputw", "local", "cloud"), highlight = H3Format.tierRoute(tier))

            // v2.0.4 AI 導演台:網頁版流程=一句需求→3 方案→分鏡微調→確認 CU 才開跑
            // v2.0.5 改切到 App 內「H3 Studio」分頁(內嵌網頁),不再開外部瀏覽器
            ImagineCard(pad = 14, onClick = { onNavSelected(NavTab.H3) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🎬 AI 導演台（多段短片）", fontSize = 15.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "寫一句需求＋放圖 → AI 給 3 個方案 → 分鏡用選單微調 → 確認 CU 才開跑。會切到「H3 Studio」分頁（第一次要用 Google 登入）。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    ImagineIcon(name = "chevron_right", size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

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
                            "上一次送出結果不明（可能已建立任務）：先看下方「最近 H3 任務」。若沒有，按「以同一筆重新確認」— 會重新估算並再次確認，伺服器認得同一筆，不會重複扣費。",
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

            // v2.1.9 角色、參考圖、素材庫收成一張小卡
            CollapsibleSection(
                title = "素材（角色・參考圖）",
                summary = "角色：" + (subjects.firstOrNull { it.id == subjectId }?.name ?: "不綁") +
                    "・${if (mode == "first") "首幀" else "參考圖"} ${sources.size} 張",
                expanded = assetsOpen,
                onToggle = { assetsOpen = !assetsOpen },
            ) {
            // v2.1.8 角色:綁資產庫角色＝臉部模型＋聲音每段都掛(嚴格鎖臉鎖聲),照片可不放
            ModePicker(
                label = "角色（鎖臉鎖聲）",
                options = listOf(ModeOption("none", "不綁角色", "只用下面的照片當參考")) + subjects.filter { it.faceLocked }.map {
                    ModeOption(it.id, it.name, "臉部模型" + (if (it.voice) "＋聲音模型" else "") + "，每一段都鎖同一張臉" + (if (it.voice) "、同一個聲音" else ""))
                },
                selectedId = subjectId ?: "none",
                onSelect = { subjectId = if (it == "none") null else it },
                footnote = "角色在 H3 Studio 資產庫建（要先按「重建臉鎖」做出臉部模型）。",
            )

            ModePicker(
                options = listOf(
                    ModeOption("ref", "參考圖（Ref2VA）", "1～9 張參考圖鎖角色／場景，不會被當成第一幀"),
                    ModeOption("first", "首幀", "第一張圖當影片第一幀，其餘當參考"),
                ),
                selectedId = mode,
                onSelect = { mode = it; prefs.h3Mode = it },
            )

            SourceSlot(
                title = if (subjectId != null) "補充照片（選填：場景、服裝）" else if (mode == "first") "首幀圖（第一張為第一幀）" else "參考圖（1～9 張）",
                uris = sources,
                maxCount = H3_MAX_IMAGES,
                emptyHint = "點此從素材庫或手機相簿選取",
                onPick = { showLibraryPicker = true },
                onRemove = { index ->
                    sourceStrings = sourceStrings.toMutableList().also { if (index < it.size) it.removeAt(index) }
                },
                note = droppedNote ?: "上傳前自動轉正、縮到長邊 1600px 並壓成 JPEG（每張 < 1.5MB）",
            )
            }

            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = "描述要怎麼動...",
                minHeight = 88,
                forVideo = true,
                videoHasImage = true,
                videoSourcePrompt = initialPrompt,
            )

            ModePicker(
                label = "畫質",
                options = H3Format.VIDEO_TIERS.map { ModeOption(it, H3Format.tierName(it), H3Format.tierDesc(it) + machineNote(board, H3Format.tierRoute(it))) },
                selectedId = tier,
                onSelect = { tier = it; prefs.h3VideoTier = it },
            )
            // v2.3.0 長度・比例・進階收成一行摘要（Claude Design v3）
            CollapsibleSection(
                title = "長度・比例・進階",
                summary = "${if (segCount > 1) "$segCount 段・每段" else "單段"} $seconds 秒・${if (aspect == "9:16") "直式" else "橫式"} $aspect" + (if (blink) "・眨眼" else "") + (if (tier == "ultra") "・GPU ${colabGpus.joinToString("/")}" else ""),
                expanded = advOpen,
                onToggle = { advOpen = !advOpen },
            ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                ParamPicker(
                    label = if (segCount > 1) "每段秒數" else "秒數",
                    value = seconds.toString(),
                    options = (4..(if (tier == "low") 8 else 15)).map { it.toString() },
                    onSelect = { seconds = it.toIntOrNull() ?: 5; prefs.h3Seconds = seconds },
                    displayName = { "$it 秒" },
                    modifier = Modifier.weight(1f),
                )
                ParamPicker(
                    label = "長度",
                    value = segCount.toString(),
                    options = (1..4).map { it.toString() },
                    onSelect = { segCount = it.toIntOrNull() ?: 1 },
                    displayName = { if (it == "1") "單段" else "$it 段接續（約 ${(it.toIntOrNull() ?: 1) * (if (tier == "low") seconds.coerceAtMost(8) else seconds)} 秒）" },
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

            // v2.1.8 長片每段內容:空白的段落由導演大腦從上面的描述拆;第 2 段起自動接續上一段結尾(畫面＋聲音)
            if (segCount > 1) {
                Text("每段要演什麼（選填；空白就由導演大腦從上面的描述拆，台詞原樣保留）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                (0 until segCount).forEach { k ->
                    androidx.compose.material3.OutlinedTextField(
                        value = beats.getOrElse(k) { "" },
                        onValueChange = { v -> beats = beats.toMutableList().also { it[k] = v } },
                        label = { Text(if (k == 0) "第 1 段" else "第 ${k + 1} 段（接續上一段）") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().clickable { blink = !blink },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = blink, onCheckedChange = { blink = it })
                Text("眨眼包（3～5 秒自然眨一次，不呆視）", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            // v2.5.3 影片一律只留在 APP，不再給勾選
            Text("成品只留在 APP：生成時會暫存於 Studio，存進手機後自動刪除（雲端硬碟檔移垃圾桶）；要上傳請在最近成品按「補上傳 Studio」", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // v2.1.5 超高畫質:勾可用的 Colab GPU 範圍,伺服器照 G4→H100→A100 自動挑(排不到不開機、不扣 CU)
            if (tier == "ultra") {
                Text("可用的 Colab GPU（照 G4 → H100 → A100 自動挑，排不到不扣 CU）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(
                    "G4" to "G4・RTX Pro 6000 96GB（最新、顯存最大）",
                    "H100" to "H100 80GB（運算最快）",
                    "A100" to "A100 80GB（最常有空機）",
                ).forEach { (id, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val next = if (id in colabGpus) colabGpus - id else colabGpus + id
                            if (next.isNotEmpty()) { colabGpus = next; prefs.h3ColabGpus = next }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = id in colabGpus, onCheckedChange = null)
                        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            }

            // v2.3.0 成品集中看：圖片＋影片縮圖、分類、點擊放大
            H3ResultsSection(prefs, onOpenImageJob = onOpenImageJob, onOpenVideoJob = onOpenJob, onReusePrompt = { prompt = it },
                onAnimate = { uri, _ -> if (sourceStrings.size < H3_MAX_IMAGES && uri !in sourceStrings) sourceStrings = sourceStrings + uri },
                onGrokEdit = onGrokEdit)

            if (showLibraryPicker) {
                LibraryImagePickerSheet(
                    onDismiss = { showLibraryPicker = false },
                    onPick = { entry ->
                        if (sourceStrings.size < H3_MAX_IMAGES) sourceStrings = sourceStrings + entry.uri.toString()
                        showLibraryPicker = false
                    },
                    onPickPose = { uri ->
                        if (sourceStrings.size < H3_MAX_IMAGES) sourceStrings = sourceStrings + uri.toString()
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
private fun H3ConfirmSheet(
    pending: PendingH3,
    onDismiss: () -> Unit,
    onOutcome: (H3Submit.Outcome) -> Unit,
    onRefreshed: (H3Estimate) -> Unit,
) {
    val e = pending.estimate ?: return
    val refreshScope = rememberCoroutineScope()
    var refreshing by remember(pending) { mutableStateOf(false) }
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 勾選綁定「這一份估算」:估算一換(新物件)就重置,必須重新勾
    // v2.2.3 免費(筆電)不用勾選,直接可送;要花錢的照舊要勾
    var agreed by remember(pending) { mutableStateOf(e.route == "local") }
    var submitting by remember(pending) { mutableStateOf(false) }
    var progressText by remember(pending) { mutableStateOf<String?>(null) }
    val block = H3Format.blockReason(e)

    fun submit() {
        if (!agreed || block != null || submitting) return
        submitting = true
        val app = ctx.applicationContext
        // 在 app 層 scope 跑:就算畫面被關掉,已建立的任務也一定會記進最近任務
        ImagineApp.appScope.launch {
            val outcome = try {
                H3Submit.run(app, prefs, pending) { msg ->
                    ImagineApp.appScope.launch(Dispatchers.Main) { progressText = msg }
                }
            } catch (t: Throwable) {
                CrashLogger.record(app, "H3Submit.fail", t)
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
            Text("確認 H3 生成", fontSize = 18.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
            ConfirmLine("機器與畫質", when (e.route) { "gputw" -> H3Format.tierName("high"); "local" -> H3Format.tierName("low"); else -> H3Format.tierName("ultra") })
            ConfirmLine("影片長度", "${pending.seconds} 秒（實際約 ${H3Format.minutes(e.videoSeconds)} 秒）")
            ConfirmLine(
                "冷啟動",
                if (e.gpuAlive) "GPU 已開機不用冷啟動" else "需要冷啟動（約 ${H3Format.minutes(e.coldMin)} 分鐘）",
            )
            ConfirmLine("GPU 時間", "約 ${H3Format.minutes(e.minutes)} 分鐘")
            if (e.etaAt != null) ConfirmLine("預計完成", H3Format.eta(e.etaAt, e.etaMin, now = rememberNowMs()) + "（含前面排隊）", strong = true)
            ConfirmLine("預估消耗", when (e.route) { "gputw" -> "約 NT$${H3Format.ntd(e.ntd)}（按分鐘扣台幣）"; "local" -> "免費（筆電）"; else -> "${H3Format.cu(e.cu)} CU（約 NT$${H3Format.ntd(e.ntd)}）" }, strong = true)
            ConfirmLine(
                "目前餘額",
                when (e.route) {
                    "gputw" -> e.ntdBalance?.let { "GPUtw NT$${H3Format.ntd(it)} → 跑完約 NT$${H3Format.ntd(it - e.ntd)}" } ?: "GPUtw 餘額未知"
                    "local" -> "不扣任何餘額"
                    else -> e.balance?.let { "${H3Format.cu(it)} CU（${H3Format.ago(e.balanceUpdatedAt)}）" } ?: "未知"
                },
            )
            if (e.route == "cloud") ConfirmLine("跑完後預估剩餘", e.after?.let { "${H3Format.cu(it)} CU" } ?: "未知")
            if (e.route == "cloud") ConfirmLine(
                "剩餘還能生成",
                e.affordableSecondsAfter?.let {
                    "正常版 ${H3Format.seconds(it.normal)}／快速版 ${H3Format.seconds(it.fast)}"
                } ?: "未知",
            )
            ConfirmLine("圖片", "${pending.sources.size} 張・${if (pending.mode == "first") "首幀" else "參考圖"}・${pending.aspect}")
            if (pending.segments > 1) ConfirmLine("長度", "${pending.segments} 段接續・每段 ${pending.seconds} 秒・約 ${pending.segments * pending.seconds} 秒")
            pending.subjectName?.let { ConfirmLine("角色", "$it（臉部模型＋聲音每段都鎖）") }

            if (block != null) {
                Text(
                    block,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.error,
                    lineHeight = 19.sp,
                )
                if (H3Format.balanceStale(e)) {
                    OutlinedActionButton(
                        label = if (refreshing) "查詢餘額中…（約 1 分鐘）" else "更新餘額（不花 CU）",
                        enabled = !refreshing && !submitting,
                        onClick = {
                            refreshing = true
                            refreshScope.launch {
                                val before = e.balanceUpdatedAt
                                val ask = H3Client.refreshCu(prefs)
                                if (ask is H3Client.Result.Err) {
                                    Toast.makeText(ctx, "更新失敗：${ask.message}", Toast.LENGTH_LONG).show()
                                    refreshing = false
                                    return@launch
                                }
                                // 每 10 秒看一次,最多 2.5 分鐘;餘額時間戳變新就換上新估算(勾選會重置)
                                repeat(15) {
                                    delay(10_000)
                                    val r = H3Client.estimate(prefs, pending.seconds, pending.quality)
                                    if (r is H3Client.Result.Ok && r.value.balanceUpdatedAt != null && r.value.balanceUpdatedAt != before) {
                                        refreshing = false
                                        onRefreshed(r.value)
                                        return@launch
                                    }
                                }
                                Toast.makeText(ctx, "餘額還沒回來，稍後再按一次", Toast.LENGTH_LONG).show()
                                refreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (e.route == "local") {
                Text("免費（筆電），不用勾選，直接送出", fontSize = 14.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.primary)
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !submitting) { agreed = !agreed },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = agreed, onCheckedChange = { if (!submitting) agreed = it })
                    Text(
                        "我已確認：" + when (e.route) { "gputw" -> "約 NT$${H3Format.ntd(e.ntd)}"; "local" -> "免費（筆電）"; else -> "預估消耗 ${H3Format.cu(e.cu)} CU（約 NT$${H3Format.ntd(e.ntd)}）" },
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
                "按「取消」不會有任何花費。送出前會再估算一次，有變動會請你重新確認。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ConfirmLine(label: String, value: String, strong: Boolean = false) {
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
