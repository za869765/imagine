package com.za869765.imagine.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.api.OpenRouterClient
import com.za869765.imagine.data.api.XaiClient
import com.za869765.imagine.data.api.dto.ChatMessage
import com.za869765.imagine.data.catalog.ModelMode
import com.za869765.imagine.data.catalog.defaultModelFor
import com.za869765.imagine.data.prefs.ApiProvider
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.repo.ApiResult
import com.za869765.imagine.data.repo.ImagineRepository
import com.za869765.imagine.data.repo.OpenRouterRepository
import com.za869765.imagine.data.repo.userFriendlyTag
import com.za869765.imagine.ui.component.AppNotice
import com.za869765.imagine.ui.component.ChipVariant
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineChip
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.ModelPickerRow
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.launch
import java.util.Locale

// 一輪對話:role = user / assistant / error;meta = 模型 / 費用 / tokens 小字
data class ChatTurn(val role: String, val content: String, val meta: String = "")

private val chatTurnsSaver = listSaver<List<ChatTurn>, String>(
    save = { list -> list.flatMap { listOf(it.role, it.content, it.meta) } },
    restore = { flat -> flat.chunked(3).filter { it.size == 3 }.map { ChatTurn(it[0], it[1], it[2]) } },
)

private fun fmtUsd(d: Double): String {
    val s = String.format(Locale.US, "%.4f", d)
    return s.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

/**
 * v1.8.0 API 對話頁 — 直接打 chat completions(xAI 或 OpenRouter,依設定的供應商),
 * 非 WebView。頂部三段「對話｜生圖｜生影」與生圖/生影頁互切;模型列可換模型並看到 價格/免費 標記;
 * OpenRouter 每則回覆顯示本次花費(usage.cost),頂端顯示餘額;右上「帳單」開供應商後台。
 */
@Composable
fun ChatScreen(
    onSwitchToImage: () -> Unit,
    onSwitchToVideo: () -> Unit,
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val xaiRepo = remember(prefs) { ImagineRepository(XaiClient.build(prefs)) }
    val orRepo = remember(prefs) { OpenRouterRepository(OpenRouterClient.build(prefs)) }

    // v1.8.3 單一模型選擇(xAI / OpenRouter 合併清單),供應商由模型 id 判斷
    var model by rememberSaveable {
        mutableStateOf(prefs.chatModel ?: defaultModelFor(ModelMode.CHAT, prefs.isApiKeySet, prefs.isOpenRouterKeySet))
    }
    val provider = ApiProvider.ofModel(model)
    var turns by rememberSaveable(stateSaver = chatTurnsSaver) { mutableStateOf(emptyList<ChatTurn>()) }
    var input by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sessionCost by rememberSaveable { mutableStateOf(0.0) }
    var balanceText by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // OpenRouter 餘額(GET /credits)— 進頁 + 每次回覆後刷新
    var balanceTick by remember { mutableStateOf(0) }
    LaunchedEffect(provider, balanceTick) {
        if (provider == ApiProvider.OPENROUTER && prefs.isOpenRouterKeySet) {
            when (val r = orRepo.credits()) {
                is ApiResult.Success -> {
                    val remain = r.value.remaining
                    balanceText = if (remain != null) "餘額 $${fmtUsd(remain)}" else null
                }
                is ApiResult.Error -> balanceText = null
            }
        } else balanceText = null
    }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    fun openBilling() {
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(provider.billingUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        val history = turns + ChatTurn("user", text)
        turns = history
        input = ""
        sending = true
        val capturedModel = model
        val capturedProvider = provider
        scope.launch {
            try {
                val apiMsgs = history.filter { it.role == "user" || it.role == "assistant" }
                    .map { ChatMessage(it.role, it.content) }
                when (capturedProvider) {
                    ApiProvider.OPENROUTER -> when (val r = orRepo.chat(apiMsgs, capturedModel)) {
                        is ApiResult.Success -> {
                            val c = r.value
                            val cost = c.cost
                            if (cost != null) sessionCost += cost
                            val meta = buildString {
                                append(c.model ?: capturedModel)
                                if (cost != null) append(" · 本則 $").append(fmtUsd(cost))
                                if (c.promptTokens != null || c.completionTokens != null) {
                                    append(" · ").append(c.promptTokens ?: 0).append("+").append(c.completionTokens ?: 0).append(" tok")
                                }
                            }
                            turns = turns + ChatTurn("assistant", c.content, meta)
                            balanceTick++
                        }
                        is ApiResult.Error -> turns = turns + ChatTurn("error", "${r.kind.userFriendlyTag()}:${r.message}")
                    }
                    ApiProvider.XAI -> when (val r = xaiRepo.chat(apiMsgs, capturedModel)) {
                        is ApiResult.Success -> turns = turns + ChatTurn("assistant", r.value, "xAI · $capturedModel · 費用見 xAI 後台")
                        is ApiResult.Error -> turns = turns + ChatTurn("error", "${r.kind.userFriendlyTag()}:${r.message}")
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                turns = turns + ChatTurn("error", "失敗:${t.message?.take(120) ?: t::class.simpleName}")
            } finally {
                sending = false
            }
        }
    }

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "對話",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 帳單快捷鈕(跟 Grok 後台那顆同一套:系統瀏覽器開供應商用量/額度頁)
                        ImagineIconButton(name = "receipt_long", onClick = { openBilling() })
                        ImagineIconButton(name = "settings", onClick = onSettingsClick)
                    }
                },
            )
        },
        bottomNav = { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) },
        scroll = false,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SegmentedTab(
                options = listOf(
                    SegmentedOption("chat", "對話"),
                    SegmentedOption("image", "生圖"),
                    SegmentedOption("video", "生影"),
                ),
                activeId = "chat",
                onSelected = {
                    when (it) {
                        "image" -> onSwitchToImage()
                        "video" -> onSwitchToVideo()
                    }
                },
                activeColor = Color(0xFF4A2E6E),
            )

            ModelPickerRow(
                mode = ModelMode.CHAT,
                selectedId = model,
                onSelect = { model = it; prefs.chatModel = it },
            )

            if (!prefs.hasKeyFor(provider)) {
                ImagineCard(pad = 14, onClick = onSettingsClick) {
                    Text(
                        "未設定 ${provider.label} API Key — 點此到設定填入,或到設定切換供應商",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 19.sp,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = buildString {
                        if (provider == ApiProvider.OPENROUTER) {
                            append("本次對話 $").append(fmtUsd(sessionCost))
                            balanceText?.let { append(" · ").append(it) }
                        } else {
                            append("xAI 對話 · 費用以 console.x.ai 為準")
                        }
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                ImagineChip(label = "帳單", icon = "receipt_long", variant = ChipVariant.Outlined, onClick = { openBilling() })
                if (turns.isNotEmpty()) {
                    ImagineChip(label = "清除", icon = "delete", variant = ChipVariant.Outlined, onClick = {
                        turns = emptyList(); sessionCost = 0.0
                    })
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (turns.isEmpty()) {
                    item {
                        Text(
                            text = "直接用 API 對話(非網頁版)。選好模型後輸入訊息送出;OpenRouter 免費模型 $0,付費模型每則回覆會顯示花費。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                itemsIndexed(turns) { _, t ->
                    ChatBubble(t)
                }
                if (sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("思考中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("輸入訊息…", fontSize = 14.sp) },
                    maxLines = 5,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 140.dp),
                )
                val canSend = input.isNotBlank() && !sending && prefs.hasKeyFor(provider)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ImagineIconButton(
                        name = "send",
                        onClick = { if (canSend) send() },
                        tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(t: ChatTurn) {
    val ctx = LocalContext.current
    val isUser = t.role == "user"
    val isError = t.role == "error"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        isUser -> MaterialTheme.colorScheme.primaryContainer
                        isError -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            SelectionContainer {
                Text(
                    text = t.content,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = when {
                        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                        isError -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (t.meta.isNotBlank()) {
                        Text(
                            text = t.meta,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).padding(2.dp),
                    ) {
                        ImagineIconButton(
                            name = "content_copy",
                            size = 14.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { Clipboard.copy(ctx, t.content, toastMsg = "已複製") },
                        )
                    }
                }
            }
        }
    }
}
