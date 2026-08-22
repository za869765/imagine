package com.za869765.imagine.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.prefs.ApiProvider
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.AppNotice
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.SectionHeader
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.theme.LocalBudgetColors
import com.za869765.imagine.ui.util.Clipboard
import java.time.LocalDate

/**
 * v1.8.0 API Key 頁:上方分段切 xAI / OpenRouter,各自一把 key(輸入 / 貼上 / 顯示 / 複製 / 移除),
 * 「目前使用」區決定生成 / 對話走哪家(記住選擇)。移除後由本頁自行清 prefs,onRemove 只負責離開。
 */
@Composable
fun ApiKeyEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onRemove: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val budgetColors = LocalBudgetColors.current

    var tabId by rememberSaveable { mutableStateOf(prefs.providerId) }
    val p = ApiProvider.fromId(tabId)
    var activeId by remember { mutableStateOf(prefs.providerId) }
    // 每個供應商各自的編輯狀態(切 tab 重設)
    var showFull by remember(tabId) { mutableStateOf(false) }
    var newKey by remember(tabId) { mutableStateOf("") }
    var keyTick by remember { mutableStateOf(0) } // 存/移除後刷新顯示
    val currentKey = remember(tabId, keyTick) { prefs.keyFor(p) }
    val verifiedAt = remember(tabId, keyTick) {
        if (p == ApiProvider.XAI) prefs.apiKeyVerifiedAt else prefs.openRouterKeyVerifiedAt
    }
    val canSave = newKey.startsWith(p.keyPrefix, ignoreCase = true) && newKey.length > 8

    fun openUrl(url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "API Key",
                showBack = true,
                onBackClick = onBack,
                trailing = { Box(modifier = Modifier.size(48.dp)) },
            )
        },
        showBalanceBar = false,
        bottomNav = null,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SegmentedTab(
                options = ApiProvider.entries.map { SegmentedOption(it.id, it.label) },
                activeId = tabId,
                onSelected = { tabId = it },
            )

            // 目前使用哪家
            ImagineCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "目前使用",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 0.08.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            ApiProvider.fromId(activeId).label,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.W700,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (activeId != p.id) {
                            OutlinedActionButton(
                                label = "改用 ${p.label}",
                                icon = "swap_horiz",
                                onClick = {
                                    prefs.providerId = p.id
                                    activeId = p.id
                                    AppNotice.show("生成 / 對話改走 ${p.label}")
                                },
                            )
                        } else {
                            Text("✓ 生成與對話走這家", fontSize = 12.sp, color = budgetColors.ok)
                        }
                    }
                    Text(
                        text = if (p == ApiProvider.OPENROUTER)
                            "OpenRouter:一把 key 通吃 400+ 模型(含免費款),對話 / 生圖 / 生影皆可;費用從 OpenRouter 餘額扣。"
                        else "xAI:Grok Imagine 圖 $0.05/張、影片 $0.05/秒;圖片編輯 / 影片延長 / 影片編輯 目前只有 xAI 支援。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 目前 Key Card
            ImagineCard {
                Column {
                    Text(
                        "${p.label} KEY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 0.08.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (showFull) currentKey ?: "未設定" else maskKey(currentKey),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.02.sp,
                    )
                    if (!currentKey.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            ImagineIcon(name = "check", size = 14.dp, fill = 1, tint = budgetColors.ok)
                            Text("已設定 · ${verifiedAt ?: "—"}", fontSize = 12.sp, color = budgetColors.ok)
                        }
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextActionButton(label = "複製", icon = "content_copy", onClick = {
                                copyToClipboard(ctx, currentKey)
                            })
                            TextActionButton(
                                label = if (showFull) "遮蔽" else "顯示完整",
                                icon = "visibility",
                                onClick = { showFull = !showFull },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextActionButton(label = "取得 Key", icon = "open_in_new", onClick = { openUrl(p.keysUrl) })
                        TextActionButton(label = "查帳單 / 用量", icon = "receipt_long", onClick = { openUrl(p.billingUrl) })
                    }
                }
            }

            // 變更為新 Key
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("變更為新 Key（${p.label}）")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = newKey,
                        onValueChange = { newKey = it.trim() },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (newKey.isEmpty()) {
                                Text(
                                    p.keyHint,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                )
                            }
                            inner()
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        TextActionButton(
                            label = "從剪貼簿貼上",
                            icon = "content_paste",
                            onClick = {
                                val raw = Clipboard.paste(ctx)?.trim().orEmpty()
                                val extracted = extractKeyFrom(raw, p.keyPrefix)
                                if (extracted != null) {
                                    newKey = extracted
                                    Toast.makeText(ctx, "✓ 已從剪貼簿載入 ${p.keyPrefix} key", Toast.LENGTH_SHORT).show()
                                } else {
                                    val preview = if (raw.isBlank()) "<空>" else raw.take(40)
                                    Toast.makeText(ctx, "剪貼簿沒有 ${p.keyPrefix} key (讀到: $preview)", Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                        Text(
                            "只讀「最近一次」複製內容；Samsung 剪貼簿歷史第三方 app 讀不到",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp, top = 2.dp),
                        )
                    }
                }
            }

            PrimaryButton(
                label = "儲存 ${p.label} Key",
                icon = "check",
                enabled = canSave,
                onClick = {
                    val today = LocalDate.now().toString()
                    if (p == ApiProvider.XAI) {
                        prefs.apiKey = newKey
                        prefs.apiKeyVerifiedAt = today
                    } else {
                        prefs.openRouterKey = newKey
                        prefs.openRouterKeyVerifiedAt = today
                    }
                    // 目前使用的那家沒 key 時,自動切到剛存好的這家(第一次只填 OpenRouter 的人不用再多點一下)
                    if (!prefs.hasKeyFor(prefs.provider)) {
                        prefs.providerId = p.id
                        activeId = p.id
                    }
                    keyTick++
                    newKey = ""
                    AppNotice.show("${p.label} Key 已儲存")
                    onSaved()
                },
            )

            // 危險區 - 移除 Key
            if (!currentKey.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ImagineIcon(name = "key", size = 22.dp, fill = 1, tint = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "移除 ${p.label} API Key",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "移除後這家的功能無法使用;若另一家有 key 會自動改用它",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Box(
                            modifier = Modifier.clickable(onClick = {
                                if (p == ApiProvider.XAI) {
                                    prefs.apiKey = null
                                    prefs.apiKeyVerifiedAt = null
                                } else {
                                    prefs.openRouterKey = null
                                    prefs.openRouterKeyVerifiedAt = null
                                }
                                val other = if (p == ApiProvider.XAI) ApiProvider.OPENROUTER else ApiProvider.XAI
                                if (prefs.provider == p && prefs.hasKeyFor(other)) {
                                    prefs.providerId = other.id
                                    activeId = other.id
                                }
                                keyTick++
                                AppNotice.show("已移除 ${p.label} Key")
                                onRemove()
                            }),
                        ) {
                            ImagineIcon(name = "chevron_right", size = 22.dp, tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            Text(
                text = "Key 由 Android Keystore 加密儲存於本機，\n只會送到對應的 API 伺服器（api.x.ai / openrouter.ai）。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private fun maskKey(key: String?): String {
    if (key.isNullOrBlank()) return "未設定"
    if (key.length <= 8) return key
    return key.take(4) + "•".repeat(13) + key.takeLast(3)
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cb.setPrimaryClip(android.content.ClipData.newPlainText("API Key", text))
}

// 從剪貼簿全文 extract key 子字串(依供應商前綴),避免使用者貼到「我的 key 是 xai-abc...」整段文字。
// IGNORE_CASE 防 IME 自動把開頭字母大寫。
private fun extractKeyFrom(raw: String, prefix: String): String? {
    if (raw.isBlank()) return null
    if (raw.startsWith(prefix, ignoreCase = true) && raw.none { it.isWhitespace() }) return raw
    val re = Regex(Regex.escape(prefix) + "[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE)
    return re.find(raw)?.value
}
