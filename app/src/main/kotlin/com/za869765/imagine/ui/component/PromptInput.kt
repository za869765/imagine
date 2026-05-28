package com.za869765.imagine.ui.component

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromptInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "Prompt",
    placeholder: String = "描述你想生成的內容...",
    maxChars: Int = 1000,
    modifier: Modifier = Modifier,
    // S22U (6.8") 觸控區建議 ≥48dp，default 改 156dp 給三行可見高度 + 足夠拇指區
    minHeight: Int = 156,
    // 被審核擋下時設 true → 邊框/label/hint 都變紅,引導使用者改寫
    flagged: Boolean = false,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    var showTemplateSheet by remember { mutableStateOf(false) }
    var showAdvisorSheet by remember { mutableStateOf(false) }
    // 使用範本/插入片語後，下一次獲焦時略過「自動全選」，讓使用者能就地編輯方括號
    var suppressSelectAllOnce by remember { mutableStateOf(false) }
    // 範本「使用」後，待 sheet 關閉再聚焦輸入框
    var pendingFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val borderColor = when {
        flagged -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val borderWidth = if (focused || flagged) 2.dp else 1.dp
    val labelColor = when {
        flagged -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // 焦點時用 BringIntoViewRequester 推 input 到 IME 上方避免被擋
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // 用 TextFieldValue 才能在 focus 時控制 selection (全選現有內容)
    var tfValue by remember { mutableStateOf(TextFieldValue(value)) }
    // 外部 value 變動 (例如「貼上」按鈕觸發) → sync 內部 + cursor 移到尾
    LaunchedEffect(value) {
        if (tfValue.text != value) {
            tfValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    fun doPaste() {
        val pasted = Clipboard.paste(ctx)
        if (pasted.isNullOrBlank()) {
            Toast.makeText(ctx, "剪貼簿沒有文字", Toast.LENGTH_SHORT).show()
            return
        }
        // 貼上覆蓋既有內容 — 對「重做生成」場景最常用,要附加文字可手動 select+貼
        onValueChange(if (pasted.length > maxChars) pasted.take(maxChars) else pasted)
    }

    // 範本「使用」— 整段填入,游標移到尾、不全選,並待 sheet 關閉後聚焦
    fun applyTemplate(text: String) {
        val clipped = if (text.length > maxChars) text.take(maxChars) else text
        tfValue = TextFieldValue(clipped, selection = TextRange(clipped.length))
        onValueChange(clipped)
        suppressSelectAllOnce = true
        pendingFocus = true
    }

    // 片語庫 — 在目前游標處插入,前面已有內容且非標點時補一個「，」分隔
    fun insertAtCursor(snippet: String) {
        val cur = tfValue
        val start = cur.selection.start.coerceIn(0, cur.text.length)
        val end = cur.selection.end.coerceIn(0, cur.text.length)
        val before = cur.text.substring(0, start)
        val sepChars = setOf(' ', '\n', '，', ',', '、', '。', '【', '：', ':')
        val ins = if (before.isNotEmpty() && before.last() !in sepChars) "，$snippet" else snippet
        val newText = before + ins + cur.text.substring(end)
        if (newText.length > maxChars) {
            Toast.makeText(ctx, "已達字數上限", Toast.LENGTH_SHORT).show()
            return
        }
        tfValue = TextFieldValue(newText, selection = TextRange(start + ins.length))
        onValueChange(newText)
        suppressSelectAllOnce = true
    }

    // 範本「使用」後 sheet 關閉 → 聚焦輸入框 (游標待命)。requestFocus 包 runCatching 防未 attach
    LaunchedEffect(pendingFocus, showTemplateSheet, showAdvisorSheet) {
        if (pendingFocus && !showTemplateSheet && !showAdvisorSheet) {
            runCatching { focusRequester.requestFocus() }
            pendingFocus = false
        }
    }

    Box(modifier = modifier) {
        // Floating label
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.W500,
            color = labelColor,
            modifier = Modifier
                .padding(start = 12.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp)
                .align(Alignment.TopStart),
        )

        // 上排兩個 chip(TopEnd,跟 floating label 對稱)— 範本 + 貼上,蓋住 border line 視覺浮起
        Row(
            modifier = Modifier
                .padding(end = 12.dp)
                .align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 建議 chip — 點開 PromptAdvisorSheet (5要素檢查 + 片語庫)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showAdvisorSheet = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ImagineIcon(
                    name = "lightbulb",
                    size = 14.dp,
                    fill = 1,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "建議",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // 範本 chip — 點開 PromptTemplateSheet
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { showTemplateSheet = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ImagineIcon(
                    name = "auto_awesome",
                    size = 14.dp,
                    fill = 1,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "範本",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // 貼上 chip — 沿用既有功能
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { doPaste() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ImagineIcon(
                    name = "content_paste",
                    size = 14.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "貼上",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .heightIn(min = minHeight.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .bringIntoViewRequester(bringIntoView)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp),
        ) {
            BasicTextField(
                value = tfValue,
                onValueChange = { newTf ->
                    if (newTf.text.length <= maxChars) {
                        tfValue = newTf
                        if (newTf.text != value) onValueChange(newTf.text)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        val wasFocused = focused
                        focused = it.isFocused
                        if (it.isFocused) {
                            scope.launch { bringIntoView.bringIntoView() }
                            // 剛獲焦 + 有內容 → 全選,方便直接覆蓋(打字/貼上不混舊)。
                            // 但若剛「使用範本/插入片語」,suppressSelectAllOnce 會略過這次全選,
                            // 讓游標待命就地編輯而非整段被選起來
                            if (!wasFocused && tfValue.text.isNotEmpty() && !suppressSelectAllOnce) {
                                tfValue = tfValue.copy(
                                    selection = TextRange(0, tfValue.text.length)
                                )
                            }
                            suppressSelectAllOnce = false
                        }
                    },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,    // 從 15→16 對 S22U 高密度螢幕視覺更舒服
                    lineHeight = 24.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    // prompt 允許多行；不要強制 Done / 不要 auto capitalize
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Default,
                ),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                        )
                    }
                    inner()
                },
            )

            // 審核風險 hint (僅參考,不阻擋使用) — 只在有 hint 時顯示
            // flagged=true (已被 400 擋下) 優先壓過所有 keyword hint,顯示「建議改寫」
            val hint = remember(value, maxChars, flagged) {
                if (flagged) {
                    PromptHint("🚨", "已被審核擋下 — 建議改寫", HintColor.Red)
                } else {
                    evaluatePrompt(value, maxChars)
                }
            }
            hint?.let {
                Text(
                    text = "${it.emoji} ${it.msg}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W500,
                    color = when (it.color) {
                        HintColor.Red -> MaterialTheme.colorScheme.error
                        HintColor.Yellow -> Color(0xFFE0A500)   // amber
                        HintColor.Gray -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(top = 4.dp),
                )
            }
        }

        if (showTemplateSheet) {
            PromptTemplateSheet(
                onDismiss = { showTemplateSheet = false },
                onUse = { template -> applyTemplate(template) },
            )
        }

        if (showAdvisorSheet) {
            PromptAdvisorSheet(
                currentPrompt = value,
                onDismiss = { showAdvisorSheet = false },
                onInsert = { snippet -> insertAtCursor(snippet) },
            )
        }
    }
}

// ── 審核風險評估 (本地 heuristic) ────────────────────────────────
// xAI 實際審核是黑盒,這裡只是粗略 keyword + 長度 hint,**僅供參考**。
// 紅燈不代表一定擋,綠燈(無 hint)不代表一定過。

private enum class HintColor { Red, Yellow, Gray }
private data class PromptHint(val emoji: String, val msg: String, val color: HintColor)

private fun evaluatePrompt(p: String, maxChars: Int): PromptHint? {
    val s = p.trim()
    if (s.isEmpty()) return null
    val low = s.lowercase()

    // 高風險: 幾乎一定被審核擋下
    val high = listOf(
        // 英文 NSFW / 露骨 / 未成年
        "nude", "naked", "nudity", "nsfw", "porn", "pornographic",
        "explicit", "sexual", "erotic", "hentai",
        "underage", "minor child", "child porn", "loli", "lolicon",
        // 中文
        "裸體", "裸露", "性愛", "色情", "露胸", "露點", "露下體",
        "蘿莉", "未成年",
    )
    if (high.any { low.contains(it) }) {
        return PromptHint("🚨", "高機率被擋 (僅參考)", HintColor.Red)
    }

    // 中風險: 可能被擋
    val medium = listOf(
        "bikini", "topless", "lingerie", "sensual", "suggestive",
        "blood", "gore", "violence", "violent", "kill", "killing", "murder",
        "weapon", "gun", "knife", "scary", "horror", "fetish",
        "比基尼", "內衣", "血腥", "暴力", "武器", "殺戮", "恐怖", "戀物",
    )
    if (medium.any { low.contains(it) }) {
        return PromptHint("⚠️", "可能審核較嚴 (僅參考)", HintColor.Yellow)
    }

    // 太短: 描述不足模型發揮空間小
    if (s.length < 5) {
        return PromptHint("📝", "描述可以再詳細", HintColor.Gray)
    }

    // 接近字數上限
    if (s.length > maxChars - 50) {
        return PromptHint("⚠️", "接近字數上限", HintColor.Yellow)
    }

    return null
}
