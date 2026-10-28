package com.za869765.imagine.ui.generate

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.h3.H3Format
import com.za869765.imagine.data.h3.H3ImageEstimate
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
import com.za869765.imagine.ui.component.ImagineChip
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val H3_IMG_MAX_REFS = 10
private const val H3_IMG_REFS_DRAFT = "h3_image_refs"

// 提醒色(被改派／筆電版本舊):設計稿的琥珀
private val Amber = Color(0xFFF2C14E)
private val AmberBg = Color(0xFF3A2E12)
private val Mint = Color(0xFF7FD9A6)

/**
 * v2.1.0 「在哪台機器生成」選單:每個選項寫費用與目前狀態(狀態來自開頁時一次不花錢的估算)。
 * st 為 null(還沒查到)時只寫費用。
 */
private fun machineOptions(st: H3ImageEstimate?): List<ModeOption> {
    val laptop = when {
        st == null -> ""
        !st.localAlive -> "・現在離線"
        st.caps?.image != true -> "・在線但工人版本舊，還不能生圖（到筆電更新一次）"
        else -> "・在線，可以免費做"
    }
    val gi = when {
        st == null -> ""
        st.gputwImageAlive -> "・現在開著，只多幾十秒"
        else -> "・現在關著，開機約 14 分"
    }
    val g = when {
        st == null -> ""
        st.gputwAlive -> "・現在開著，順便做"
        else -> "・現在關著（要搬 52 GB 權重，不建議只為生圖開）"
    }
    val cu = st?.cuBalance?.let { "・CU 餘額 ${H3Format.cu(it)}" } ?: ""
    val autoNow = st?.let { "・現在會派到：${H3Format.routeShort(it.route)}" } ?: ""
    return listOf(
        ModeOption("auto", "自動（建議）", "筆電有空就免費 → 開著的機器順便做 → 再不然開製圖機約 NT$8$autoNow"),
        ModeOption("local", "筆電 3070", "免費・一張約 1～2 分・筆電要開著$laptop"),
        ModeOption("gputw_image", "製圖機（GPUtw）", "約 NT$8／次（含開機 14 分）$gi"),
        ModeOption("gputw", "GPUtw 5090（影片機）", "影片機開著時順便做・同製圖機費率$g"),
        ModeOption("cloud", "Colab A100", "花 CU・1 張約 1.04 CU ≈ NT$4・冷啟動 6 分$cu"),
    )
}

/**
 * v2.0.7 H3 Studio 生圖(Qwen-Image 2.1,不走 Grok Imagine)。
 * 流程同 H3 生影:估算(不花錢)→ 確認視窗(勾選才能按)→ POST(confirm、client_request_id 冪等鍵)→ 任務頁輪詢。
 * 機器由 H3 Studio 派:筆電免費 → 開著的 GPUtw／製圖機順便做 → 開製圖機(只搬 Qwen 權重)→ 指定 Colab 才花 CU。
 * v2.1.0:開頁先查一次機器狀態(不花錢)寫進選單;確認視窗機器名＋費用放大、被改派時琥珀提醒＋一鍵換機器、只顯示會扣的餘額。
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

    // v2.1.0 機器狀態:開頁查一次「自動」會派到哪(不花錢),之後每次估算也更新
    var status by remember { mutableStateOf<H3ImageEstimate?>(null) }
    LaunchedEffect(Unit) {
        if (prefs.isH3TokenSet) {
            when (val r = H3Client.imageEstimate(prefs, 1, 1, "auto")) {
                is H3Client.Result.Ok -> status = r.value
                is H3Client.Result.Err -> {}
            }
        }
    }

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

    // retry != null:重新估算同一筆(沿用 client_request_id;換機器也走這裡);null:依目前畫面參數開新的一筆(新 id)
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
                is H3Client.Result.Ok -> {
                    pendingConfirm = snapshot.copy(estimate = r.value)
                    if (snapshot.route == "auto") status = r.value
                }
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
            // 換機器:關窗、同一筆改機器重估(畫面上的選單也跟著換)
            onChangeRoute = { r ->
                pendingConfirm = null
                route = r
                prefs.h3ImgRoute = r
                startEstimate(pending.copy(route = r))
            },
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

            // v2.1.0 機器狀態一眼看:筆電能不能免費做、製圖機開著沒、兩種餘額
            status?.let { st ->
                val laptopOk = st.localAlive && st.caps?.image == true
                ImagineCard(pad = 12) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("機器狀態", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when {
                                !st.localAlive -> "筆電：離線（免費路線不能用）"
                                !laptopOk -> "筆電：在線，但工人版本舊、還不能生圖（到筆電更新一次）"
                                else -> "筆電：在線，可以免費做" + (st.caps?.ver?.let { "（工人 $it）" } ?: "")
                            },
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = if (laptopOk) Mint else Amber,
                        )
                        Text(
                            "製圖機：" + when {
                                st.gputwImageIdleLeft != null -> "開著，待命還剩約 ${st.gputwImageIdleLeft.toInt().coerceAtLeast(1)} 分（現在下單免開機費）"
                                st.gputwImageAlive -> "開著（正在做圖，排後面免開機費）"
                                else -> "關著（開一次約 NT$4～5，做完待命 8 分）"
                            } + "・影片機：${if (st.gputwAlive) "開著" else "關著"}",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = if (st.gputwImageAlive) Mint else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "GPUtw ${st.ntdBalance?.let { "NT$" + H3Format.ntd(it) } ?: "未知"}・CU ${st.cuBalance?.let { H3Format.cu(it) } ?: "未知"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                maxItemsInEachRow = 3,
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
            }

            // v2.1.0 在哪台機器生成:每個選項寫費用與目前狀態(ModePicker 名稱＋說明兩行)
            ModePicker(
                label = "在哪台機器生成",
                options = machineOptions(status),
                selectedId = route,
                onSelect = { route = it; prefs.h3ImgRoute = it },
                footnote = "狀態是開頁時向 H3 Studio 查的，不花錢；費用都是預估，確認視窗會再算一次。",
            )

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
                    "Qwen-Image 2.1 照片級寫實。按下方按鈕只會先估算（不花錢），確認視窗勾選後才會真的開跑。成品會進 H3 Studio 的「Imagine APP 生圖」作品，也會自動存進這台手機的所有作品。",
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

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun H3ImageConfirmSheet(
    pending: PendingH3Image,
    onDismiss: () -> Unit,
    onOutcome: (H3Submit.Outcome) -> Unit,
    onChangeRoute: (String) -> Unit,
) {
    val e = pending.estimate ?: return
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 勾選綁定「這一份估算」:估算一換(新物件)就重置,必須重新勾
    var agreed by remember(pending) { mutableStateOf(false) }
    var submitting by remember(pending) { mutableStateOf(false) }
    var progressText by remember(pending) { mutableStateOf<String?>(null) }
    val name = H3Format.routeName(e.route)
    val costText = when (e.route) {
        "cloud" -> "${H3Format.cu(e.cu)} CU ≈ NT$${H3Format.ntd(e.ntd)}"
        "local" -> "免費"
        else -> "約 NT$${H3Format.ntd(e.ntd)}（按分鐘扣）"
    }
    // 你明確選了某台,伺服器卻派到別台 → 要讓你一眼看到;筆電版本舊的原因也要讓你知道
    val rerouted = pending.route != "auto" && e.route != pending.route
    val oldWorker = e.reason.contains("版本舊")
    val balanceLine = when (e.route) {
        "cloud" -> e.cuBalance?.let { "CU ${H3Format.cu(it)} → 跑完約 ${H3Format.cu(it - e.cu)}" } ?: "CU 餘額未知"
        "local" -> "免費，不扣任何餘額"
        else -> e.ntdBalance?.let { "GPUtw NT$${H3Format.ntd(it)} → 跑完約 NT$${H3Format.ntd(it - e.ntd)}" } ?: "GPUtw 餘額未知"
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

            // 機器卡:名稱＋費用放大;被改派／筆電版本舊 → 琥珀提醒＋一鍵換機器
            ImagineCard(pad = 14) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("這次用的機器", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(name, fontSize = 20.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
                        when (e.route) {
                            "cloud" -> Tag("花 CU", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                            "local" -> Tag("免費", Mint, Color(0xFF0F2A1C))
                            else -> Tag("扣台幣", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Text(
                        "約 ${H3Format.minutes(e.minutes)} 分鐘 · $costText",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.W700,
                        color = if (e.route == "local") Mint else MaterialTheme.colorScheme.primary,
                    )
                    // v2.1.1 開機費要有效益:要開機時提醒一次多做幾張;機器開著時告訴你免開機費
                    if (e.route == "gputw_image" || e.route == "gputw") {
                        val alive = if (e.route == "gputw_image") e.gputwImageAlive else e.gputwAlive
                        Text(
                            if (alive) "機器開著，這張不用付開機費。"
                            else "這次要開機（約 NT$4～5）。開機費一次就好：張數選 4 張，或做完 8 分鐘內再下單，都不用再付。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = if (alive) Mint else Amber,
                        )
                    }
                    if (!rerouted && !oldWorker && e.reason.isNotBlank()) {
                        Text(e.reason, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (rerouted || oldWorker) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmberBg)
                                .border(1.dp, Amber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                if (rerouted) "你選的是「${H3Format.routeName(pending.route)}」，這次改派 $name" else "注意",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W700,
                                color = Amber,
                            )
                            Text(e.reason, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFFF6E3A6))
                            if (oldWorker) {
                                Text(
                                    "免費：到筆電更新工人（30 秒）。省錢：改選製圖機，機器開著時只多幾十秒。",
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFF6E3A6),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (e.route != "gputw_image") ImagineChip(label = "改選製圖機 · 約 NT$8", onClick = { if (!submitting) onChangeRoute("gputw_image") })
                        if (pending.route != "auto") ImagineChip(label = "改選自動", onClick = { if (!submitting) onChangeRoute("auto") })
                        if (e.route != "local" && e.localAlive && e.caps?.image == true) {
                            ImagineChip(label = "改選筆電（免費）", onClick = { if (!submitting) onChangeRoute("local") })
                        }
                    }
                }
            }

            ConfirmLine(
                "規格",
                "${pending.aspect}・${if (pending.mp == 4) "2K" else "1K"}（${H3Format.imageSize(pending.aspect, pending.mp)}）・${pending.count} 張" +
                    (if (pending.refs.isNotEmpty()) "・參考 ${pending.refs.size} 張" else "") +
                    (if (pending.allowText) "・允許文字" else "・無文字"),
            )
            ConfirmLine("餘額", balanceLine)

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
                "按「取消」不會有任何花費。勾選後「確認開跑」才會亮；機器由 H3 Studio 在送出當下決定，開著的機器會順便做。",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
