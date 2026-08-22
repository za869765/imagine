package com.za869765.imagine.ui.generate

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.za869765.imagine.data.api.OpenRouterClient
import com.za869765.imagine.data.api.XaiClient
import com.za869765.imagine.data.catalog.ModelMode
import com.za869765.imagine.data.catalog.OpenRouterCatalog
import com.za869765.imagine.data.prefs.ApiProvider
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.OpenRouterRepository
import com.za869765.imagine.ui.component.ModelPickerRow
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ErrorKind
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.repo.userFriendlyTag
import com.za869765.imagine.data.storage.MaterialLibrary
import com.za869765.imagine.data.storage.MediaExporter
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.ui.component.CardVariant
import com.za869765.imagine.ui.component.ChipVariant
import com.za869765.imagine.ui.component.FullscreenImageViewer
import com.za869765.imagine.ui.component.ViewerAction
import com.za869765.imagine.ui.component.ConfirmHighRiskDialog
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineChip
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.ParamPicker
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.PromptInput
import com.za869765.imagine.ui.component.firstHighRiskTerm
import com.za869765.imagine.ui.theme.ImagineCustomShapes
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// L2 子模式底線分頁(生圖/圖片編輯)— 與 L1 pill 視覺區隔(痛點 #1)。
@Composable
private fun UnderlineTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateImageScreen(
    onSwitchToVideo: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    onSwitchToChat: () -> Unit = {},
    onAnimateImage: (String, String) -> Unit = { _, _ -> },
    onEditImage: (String, String) -> Unit = { _, _ -> },
    initialPrompt: String? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val repository = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }
    val focusManager = LocalFocusManager.current

    // v1.8.0 供應商:xAI 照舊(品質→模型);OpenRouter 走 /images(回 base64,直接落地)+ 模型可選,
    // 解析度 / 長寬比 / 數量選項依所選模型的 supported_parameters。
    val provider = prefs.provider
    val orRepo = remember(prefs) { OpenRouterRepository(OpenRouterClient.build(prefs)) }
    var orModel by rememberSaveable(prefs.orImageModel) { mutableStateOf(prefs.orImageModel) }
    val orModelInfo = remember(orModel) { OpenRouterCatalog.find(ctx, ModelMode.IMAGE, orModel) }
    val orResolutions = orModelInfo?.resolutions?.takeIf { it.isNotEmpty() } ?: listOf("1K", "2K")
    val orAspects = orModelInfo?.aspects?.takeIf { it.isNotEmpty() }
        ?: listOf("1:1", "16:9", "9:16", "4:3", "3:4", "3:2", "2:3")
    val orNMax = (orModelInfo?.nMax ?: 1).coerceIn(1, 10)
    var orResolution by rememberSaveable(orModel) { mutableStateOf(orResolutions.first()) }
    var orAspect by rememberSaveable(orModel) {
        mutableStateOf(if (prefs.defImageAspect in orAspects) prefs.defImageAspect else orAspects.first())
    }

    var prompt by rememberSaveable { mutableStateOf(initialPrompt.orEmpty()) }
    // 從 History「使用此提示詞」帶進來 → 覆蓋目前 prompt
    LaunchedEffect(initialPrompt) {
        if (!initialPrompt.isNullOrBlank() && initialPrompt != prompt) {
            prompt = initialPrompt
        }
    }
    // key 帶 prefs 預設值:在設定改了預設後重進本頁會 re-init 成新預設(否則 rememberSaveable
    // 還原舊的已存值,改設定看不出變化)。手動改參數在同一預設下仍會保留。
    var resolution by rememberSaveable(prefs.defImageResolution) { mutableStateOf(prefs.defImageResolution) }
    var aspectRatio by rememberSaveable(prefs.defImageAspect) { mutableStateOf(prefs.defImageAspect) }
    var n by rememberSaveable(prefs.defImageCount) { mutableStateOf(prefs.defImageCount) }
    var quality by rememberSaveable(prefs.defImageQuality) { mutableStateOf(prefs.defImageQuality) }  // rapid (快) / quality (好)
    var loading by remember { mutableStateOf(false) }
    // 圖片頁子模式：gen=生圖 / edit=圖片編輯(內嵌 EditPane)。原本只有生圖,無模式列。
    var imageFn by rememberSaveable { mutableStateOf("gen") }

    var resultUrls by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    // 點結果圖開全螢幕看圖器的起始索引(null=未開);動作作用在當頁那張,修掉「永遠只動第 1 張」
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    // 每次成功生成 +1,讓結果圖 Coil 快取 key 變動 → 避免 xAI 重用同一 URL 時看到上一張舊圖。
    // 用 rememberSaveable 跟 resultUrls 一致(process death 還原後不重置回 0 撞到舊 disk 快取)。
    var resultGen by rememberSaveable { mutableStateOf(0) }
    // 這批生成存檔後的本機檔名(依序對齊 resultUrls);給「設為素材庫」整批標分類用。
    // v1.7.2: 改 rememberSaveable("" = 還沒存好) — 否則切頁回來 resultUrls 還原了
    // 但 savedNames 空掉,「存成角色資產」永遠卡在「圖片儲存中」。
    var savedNames by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var lastPrompt by rememberSaveable { mutableStateOf("") }
    var lastMeta by rememberSaveable { mutableStateOf("") }
    var lastError by rememberSaveable { mutableStateOf("") }
    var lastErrorIsPolicy by rememberSaveable { mutableStateOf(false) }
    // A2：送出前若偵測到高風險詞,先彈確認;非 null = 顯示對話框,值為命中的詞
    var pendingRiskTerm by remember { mutableStateOf<String?>(null) }
    // 角色資產:存成角色的命名對話框(v1.7.2)。非 null=開啟,值=開啟當下快照的檔名
    // (確認時不能重讀 savedNames — 對話框開著時新批完成會把它換掉,寫進錯批的圖)
    var saveCharacterNames by remember { mutableStateOf<List<String>?>(null) }
    // 批次存檔帳本:appScope 下載完成順手寫 prefs(不依賴 composition 存活)。
    // 切頁時 in-flight 的存檔寫不回已死的 state,回來只還原 "" 佔位 → 點按鈕時從帳本對帳回填。
    val batchPrefs = remember {
        ctx.getSharedPreferences("imagine_batch_saved", android.content.Context.MODE_PRIVATE)
    }
    // 批次識別用 UUID — resultGen 只在單一畫面 instance 內單調,重進頁會從頭數,
    // 舊 instance 晚到的下載可能撞同號鍵把錯圖對帳進新批;UUID 跨 instance 唯一。
    var batchToken by rememberSaveable { mutableStateOf("") }
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
            val capturedProvider = provider
            val capturedModel = when {
                capturedProvider == ApiProvider.OPENROUTER -> orModel
                quality == "quality" -> "grok-imagine-image-quality"
                else -> "grok-imagine-image"
            }
            val capturedOrRes = orResolution
            val capturedOrAr = orAspect
            var orCost: Double? = null
            var orSavedNames: List<String> = emptyList()
            val result: ApiResult<List<String>> = if (capturedProvider == ApiProvider.OPENROUTER) {
                when (val r = orRepo.generateImage(
                    model = capturedModel,
                    prompt = capturedPrompt,
                    n = capturedN.coerceIn(1, orNMax),
                    aspectRatio = capturedOrAr.takeIf { it != "auto" },
                    resolution = capturedOrRes,
                )) {
                    is ApiResult.Error -> r
                    is ApiResult.Success -> {
                        orCost = r.value.cost
                        // OpenRouter 回 base64 → 直接存進 app 內 media(與 xAI 下載同一目錄),結果以 file:// 顯示
                        val uris = ArrayList<String>()
                        for (img in r.value.images) {
                            val bytes = img.bytes
                            val url = img.url
                            val saved = when {
                                bytes != null -> MediaSaver.saveImage(ctx, bytes, capturedPrompt)
                                !url.isNullOrBlank() -> MediaSaver.saveImageFromUrl(ctx, url, capturedPrompt)
                                else -> null
                            }
                            if (saved != null) uris.add(saved)
                        }
                        orSavedNames = uris.map { it.substringAfterLast('/') }
                        ApiResult.Success(uris)
                    }
                }
            } else {
                repository.generateImage(
                    prompt = capturedPrompt,
                    n = capturedN,
                    resolution = capturedRes,
                    aspectRatio = capturedAr.takeIf { it != "auto" },
                    model = capturedModel,
                )
            }
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
                        lastMeta = if (capturedProvider == ApiProvider.OPENROUTER) {
                            "$capturedOrAr · $capturedOrRes · ${result.value.size} 張 · $capturedModel" +
                                (orCost?.let { " · 本次 $" + String.format(java.util.Locale.US, "%.4f", it).trimEnd('0').trimEnd('.') } ?: "")
                        } else {
                            "$capturedAr · $capturedRes · ${capturedN} 張"
                        }
                        lastError = ""
                        lastErrorIsPolicy = false
                        pendingScrollToResult = true  // bug#3: 捲到結果區讓新圖主動出現
                        resultGen++                   // A1: 換快取 key,強制顯示這次的新圖
                        // v1.0.54 B3: 改用 ImagineApp.appScope (process-lifecycle) — user
                        // 切走/鎖屏時 Composable scope 會 cancel，下載到一半被砍 → History 看不到
                        // v1.7.2: 這批專屬 token — 上一批(甚至上一個畫面 instance)較晚完成的
                        // 下載不可寫進新批的 savedNames/帳本鍵(索引對不上會把錯的圖存進角色資產)
                        val tok = java.util.UUID.randomUUID().toString()
                        batchToken = tok
                        batchPrefs.edit().clear().apply()  // 帳本只留當前批(舊批晚到寫入=孤兒鍵,無害)
                        if (capturedProvider == ApiProvider.OPENROUTER) {
                            // 已在 runGenerate 內同步落地 → 檔名直接就緒(角色資產 / 分類鈕立即可用)
                            savedNames = orSavedNames
                            orSavedNames.forEachIndexed { i, name -> batchPrefs.edit().putString("b${tok}_$i", name).apply() }
                        } else {
                        savedNames = List(result.value.size) { "" }
                        result.value.forEachIndexed { i, url ->
                            com.za869765.imagine.ImagineApp.appScope.launch {
                                val savedUri = MediaSaver.saveImageFromUrl(ctx, url, capturedPrompt)
                                val name = savedUri?.substringAfterLast('/')
                                if (name != null) {
                                    // 先記帳本(切頁後 state 死了也留得下),再更新 UI state
                                    batchPrefs.edit().putString("b${tok}_$i", name).apply()
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (batchToken == tok) {
                                            savedNames = savedNames.toMutableList()
                                                .also { if (i < it.size) it[i] = name }
                                        }
                                    }
                                }
                            }
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
                    resultUrls = emptyList() // 400/被審核擋下→清上次結果,避免誤會是新結果
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
            // v1.8.0 L1 三段:對話｜生圖｜生影
            com.za869765.imagine.ui.component.SegmentedTab(
                options = listOf(
                    com.za869765.imagine.ui.component.SegmentedOption("chat", "對話"),
                    com.za869765.imagine.ui.component.SegmentedOption("image", "生圖"),
                    com.za869765.imagine.ui.component.SegmentedOption("video", "生影"),
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

            // L2 子模式:底線分頁(生圖/圖片編輯),與 L1 pill 視覺區隔
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                UnderlineTab("生圖", imageFn == "gen") { imageFn = "gen" }
                UnderlineTab("圖片編輯", imageFn == "edit") { imageFn = "edit" }
            }
            if (provider == ApiProvider.OPENROUTER && imageFn == "edit") {
                Text(
                    "圖片編輯目前只接 xAI(用 xAI key);OpenRouter 的圖生圖請在「生圖」改用支援參考圖的模型。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!prefs.isActiveKeySet) {
                ImagineCard(pad = 14, onClick = onSettingsClick) {
                    Text(
                        "未設定 ${provider.label} API Key — 點此到設定填入、匯入備份,或切換供應商",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 19.sp,
                    )
                }
            }

            if (imageFn == "edit") {
                // 圖片編輯內嵌 — EditPane 自帶來源選取 / 執行 / 結果，不帶 initial media(使用者自選)。
                // EditPane 無自帶 padding,靠這個 padded Column 提供節奏。
                com.za869765.imagine.ui.edit.EditPane(
                    mode = com.za869765.imagine.ui.edit.EditMode.ImageEdit,
                )
                return@Column
            }

            PromptInput(
                value = prompt,
                onValueChange = { prompt = it },
                flagged = lastErrorIsPolicy,
            )

            // v1.8.0 模型列(價格 / 免費標記)— xAI:快速/高品質兩款($0.05/張);OpenRouter:43 款生圖模型
            if (provider == ApiProvider.OPENROUTER) {
                ModelPickerRow(
                    mode = ModelMode.IMAGE,
                    provider = provider,
                    selectedId = orModel,
                    onSelect = { orModel = it; prefs.orImageModel = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamPicker(
                        label = "解析度",
                        value = if (orResolution in orResolutions) orResolution else orResolutions.first(),
                        options = orResolutions,
                        onSelect = { orResolution = it },
                        modifier = Modifier.weight(1f),
                    )
                    ParamPicker(
                        label = "長寬比",
                        value = if (orAspect in orAspects) orAspect else orAspects.first(),
                        options = orAspects,
                        onSelect = { orAspect = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ParamPicker(
                        label = "數量",
                        value = n.coerceIn(1, orNMax).toString(),
                        options = (1..orNMax).map { it.toString() },
                        onSelect = { n = it.toIntOrNull() ?: 1 },
                        displayName = { "$it 張" + (if (orNMax == 1) "（此模型一次 1 張）" else "") },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
            // 參數 2×2:解析度/長寬比 + 數量/品質。品質從獨立分段控制併進來,少一條堆疊橫條(痛點 #2)。
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ParamPicker(
                    label = "數量",
                    value = n.toString(),
                    options = (1..10).map { it.toString() },
                    onSelect = { n = it.toIntOrNull() ?: 1 },
                    modifier = Modifier.weight(1f),
                )
            }
            // 品質 = 模型(快速 grok-imagine-image / 高品質 grok-imagine-image-quality),改用模型列顯示價格
            ModelPickerRow(
                mode = ModelMode.IMAGE,
                provider = provider,
                selectedId = if (quality == "quality") "grok-imagine-image-quality" else "grok-imagine-image",
                onSelect = { quality = if (it.endsWith("-quality")) "quality" else "rapid" },
            )
            }

            PrimaryButton(
                label = if (loading) "生成中…" else "生 成",
                icon = if (loading) null else "auto_awesome",
                loading = loading,
                enabled = prompt.isNotBlank() && !loading && prefs.isActiveKeySet,
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
                        resultUrls.forEachIndexed { i, url ->
                            AsyncImage(
                                model = coil3.request.ImageRequest.Builder(ctx)
                                    .data(url)
                                    .memoryCacheKey("$url@$resultGen")
                                    .diskCacheKey("$url@$resultGen")
                                    .build(),
                                contentDescription = lastPrompt,
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ImagineCustomShapes.Media)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { viewerIndex = i },
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
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                    label = "存到相簿",
                                    icon = "download",
                                    variant = ChipVariant.Tonal,
                                    onClick = {
                                        // 真正匯出到系統相簿 (MediaExporter)，不再只是重存私有沙盒
                                        com.za869765.imagine.ImagineApp.appScope.launch {
                                            var ok = 0
                                            resultUrls.forEach { url ->
                                                if (MediaExporter.saveToGallery(ctx, url, isVideo = false)) ok++
                                            }
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                com.za869765.imagine.ui.component.AppNotice.show(if (ok > 0) "已存 $ok 張到相簿" else "存相簿失敗，改用分享試試")
                                            }
                                        }
                                    },
                                )
                                ImagineChip(
                                    label = "分享",
                                    icon = "share",
                                    variant = ChipVariant.Tonal,
                                    onClick = {
                                        com.za869765.imagine.ImagineApp.appScope.launch {
                                            MediaExporter.share(ctx, resultUrls.first(), isVideo = false)
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
                            // 一鍵把整批結果存成「角色資產」(名字+定妝圖組;之後生成可一鍵帶入
                            // 整組當參考圖,角色一致性)。同時標進素材庫「角色」分類。
                            ImagineChip(
                                label = "🎭 存成角色資產",
                                icon = "star",
                                variant = ChipVariant.Tonal,
                                modifier = Modifier.padding(top = 6.dp),
                                onClick = {
                                    // 先跟帳本對帳回填(切頁期間完成的存檔只在帳本裡)
                                    val filled = savedNames.mapIndexed { i, n ->
                                        n.ifEmpty { batchPrefs.getString("b${batchToken}_$i", "") ?: "" }
                                    }
                                    if (filled != savedNames) savedNames = filled
                                    // 全批存好才能開命名對話框 — 部分完成就寫入會漏圖進角色
                                    val done = filled.count { it.isNotEmpty() }
                                    if (filled.isEmpty() || done < filled.size) {
                                        Toast.makeText(ctx, "圖片儲存中($done/${filled.size}),請稍候再試", Toast.LENGTH_SHORT).show()
                                    } else {
                                        saveCharacterNames = filled  // 快照,對話框開著時不受新批影響
                                    }
                                },
                            )
                            Text(
                                text = "或設為其他分類",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MaterialLibrary.CATEGORIES.forEach { c ->
                                    ImagineChip(
                                        label = c,
                                        variant = ChipVariant.Tonal,
                                        onClick = {
                                            val names = savedNames.filter { it.isNotEmpty() }
                                            if (names.isEmpty()) {
                                                Toast.makeText(ctx, "圖片儲存中,請稍候再試", Toast.LENGTH_SHORT).show()
                                            } else {
                                                names.forEach { MaterialLibrary.setCategory(ctx, it, c) }
                                                Toast.makeText(ctx, "已把 ${names.size} 張設為「$c」素材", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 角色資產命名對話框:確認後 加進 CharacterAssets + 標素材庫「角色」分類。
        // 用開啟當下的快照(saveCharacterNames),不重讀 savedNames — 防對話框開著時被新批換掉。
        saveCharacterNames?.let { snapshot ->
            com.za869765.imagine.ui.component.SaveToCharacterDialog(
                onDismiss = { saveCharacterNames = null },
                onConfirm = { charName ->
                    com.za869765.imagine.data.storage.CharacterAssets.addImages(ctx, charName, snapshot)
                    snapshot.forEach { MaterialLibrary.setCategory(ctx, it, MaterialLibrary.CHARACTER) }
                    saveCharacterNames = null
                    Toast.makeText(ctx, "已把 ${snapshot.size} 張存進角色「$charName」", Toast.LENGTH_SHORT).show()
                },
            )
        }

        // 點結果圖 → 全螢幕看圖器(雙指縮放/多張左右滑);底部動作作用在當頁那張。
        val vi = viewerIndex
        if (vi != null && vi in resultUrls.indices) {
            FullscreenImageViewer(
                urls = resultUrls,
                startIndex = vi,
                onDismiss = { viewerIndex = null },
                actions = listOf(
                    ViewerAction("download", "存相簿") { url ->
                        com.za869765.imagine.ImagineApp.appScope.launch {
                            val ok = MediaExporter.saveToGallery(ctx, url, isVideo = false)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                com.za869765.imagine.ui.component.AppNotice.show(if (ok) "已存到相簿" else "存相簿失敗，改用分享試試")
                            }
                        }
                    },
                    ViewerAction("share", "分享") { url ->
                        com.za869765.imagine.ImagineApp.appScope.launch {
                            MediaExporter.share(ctx, url, isVideo = false)
                        }
                    },
                    ViewerAction("edit", "編輯") { url -> viewerIndex = null; onEditImage(url, lastPrompt) },
                    ViewerAction("movie", "動起來") { url -> viewerIndex = null; onAnimateImage(url, lastPrompt) },
                ),
            )
        }
    }
}

