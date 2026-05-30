package com.za869765.imagine.ui.component

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.za869765.imagine.data.prefs.SecurePrefs
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
    // 影片頁傳 true → 範本/建議才顯示影片限定欄位 (動作/聲音/字幕);圖片頁 false 隱藏
    forVideo: Boolean = false,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    // B2: 5 要素涵蓋數,顯示在「建議」chip 上 (例如 建議 3/5)
    val coverage = remember(value) { promptElementCoverage(value) }
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
        // B3: 記錄最近用過 (去重置頂, 上限 6)
        val recent = prefs.recentSnippets.toMutableList()
        recent.remove(snippet)
        recent.add(0, snippet)
        prefs.recentSnippets = recent.take(6)
    }

    // 建議-風險: 把命中的高風險詞換成使用者點選的較溫和替代 (所有出現處,不分大小寫)。
    // 不關 sheet — 換完 value 變動會讓 sheet 重新掃描,使用者可接著處理下一個。
    fun replaceTerm(oldTerm: String, newTerm: String) {
        val cur = tfValue.text
        val replaced = cur.replace(oldTerm, newTerm, ignoreCase = true)
        if (replaced == cur) return
        val clipped = if (replaced.length > maxChars) replaced.take(maxChars) else replaced
        tfValue = TextFieldValue(clipped, selection = TextRange(clipped.length))
        onValueChange(clipped)
        suppressSelectAllOnce = true
    }

    // 片語庫「智慧插入」: 若 prompt 內已有同一類型(同欄位)的詞 → 換掉它避免衝突
    //   (例如已有「暖橘色調」再點「冷藍色調」就替換,不會兩個並存);否則插入游標處。
    // 取最長命中當作既有值 (避免「大波浪」誤判),並用 guard 排除「短詞是更長詞的子字串」
    //   情況 (例如色調「黑白」其實是風格「黑白底片」的一部分 → 不替換、改插入,避免砍壞長詞)。
    fun smartInsert(option: String, sameTypeOptions: List<String>) {
        val text = tfValue.text
        if (isStandaloneOption(text, option)) return  // 已「獨立」存在才算重複;被更長詞包住(子字串)不算
        val present = BUILDER_FIELDS.flatMap { it.options }
            .filter { it != "(不指定)" && text.contains(it) }
        val existing = sameTypeOptions
            .filter { it != option && it != "(不指定)" && text.contains(it) }
            .maxByOrNull { it.length }
            ?.takeIf { ex -> present.none { it != ex && it.length > ex.length && it.contains(ex) } }
        if (existing != null) {
            replaceTerm(existing, option)
            val recent = prefs.recentSnippets.toMutableList()
            recent.remove(option)
            recent.add(0, option)
            prefs.recentSnippets = recent.take(6)
        } else {
            insertAtCursor(option)
        }
    }

    // B1: 跳到下一個【…】佔位符並選取,直接打字覆蓋;到底繞回第一個。
    fun jumpToNextPlaceholder() {
        val text = tfValue.text
        val cursor = tfValue.selection.end.coerceIn(0, text.length)
        val matches = Regex("【[^】]*】").findAll(text).toList()
        if (matches.isEmpty()) {
            Toast.makeText(ctx, "沒有【】可填", Toast.LENGTH_SHORT).show()
            return
        }
        val next = matches.firstOrNull { it.range.first >= cursor } ?: matches.first()
        suppressSelectAllOnce = true
        tfValue = tfValue.copy(selection = TextRange(next.range.first, next.range.last + 1))
        pendingFocus = true
    }

    // 範本「使用」後 sheet 關閉 → 聚焦輸入框 (游標待命)。requestFocus 包 runCatching 防未 attach
    LaunchedEffect(pendingFocus, showTemplateSheet, showAdvisorSheet) {
        if (pendingFocus && !showTemplateSheet && !showAdvisorSheet) {
            runCatching { focusRequester.requestFocus() }
            pendingFocus = false
        }
    }

    Column(modifier = modifier) {
        // 工具列：label 在左、三顆功能鈕在右,獨立一排清楚可見。
        // (v1.0.59 以前是「浮」在輸入框邊框上,被輸入框背景蓋住完全看不到 → v1.0.60 改成這排)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = labelColor,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val advisorLabel = if (value.isBlank()) "建議" else "建議 $coverage/5"
                PromptToolChip(icon = "lightbulb", label = advisorLabel) { showAdvisorSheet = true }
                PromptToolChip(icon = "auto_awesome", label = "範本") { showTemplateSheet = true }
                PromptToolChip(icon = "content_paste", label = "貼上") { doPaste() }
                // B1: 只有當內容含【】(套了範本/擴寫) 才顯示「跳格」
                if (value.contains("【")) {
                    PromptToolChip(icon = "edit", label = "跳格") { jumpToNextPlaceholder() }
                }
            }
        }

        Box(
            modifier = Modifier
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
                forVideo = forVideo,
            )
        }

        if (showAdvisorSheet) {
            PromptAdvisorSheet(
                currentPrompt = value,
                recentSnippets = prefs.recentSnippets,
                onDismiss = { showAdvisorSheet = false },
                onInsert = { option, sameType -> smartInsert(option, sameType) },
                onExpand = { scaffold -> applyTemplate(scaffold) },
                onReplace = { oldTerm, newTerm -> replaceTerm(oldTerm, newTerm) },
                forVideo = forVideo,
            )
        }
    }
}

// 工具列上的小功能鈕(建議/範本/貼上)。tonal 底色 + primary 字,清楚可見可點。
@Composable
private fun PromptToolChip(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ImagineIcon(
            name = icon,
            size = 15.dp,
            fill = 1,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ── 審核風險評估 (本地 heuristic) ────────────────────────────────
// xAI 實際審核是黑盒,這裡只是粗略 keyword + 長度 hint,**僅供參考**。
// 紅燈不代表一定擋,綠燈(無 hint)不代表一定過。

private enum class HintColor { Red, Yellow, Gray }
private data class PromptHint(val emoji: String, val msg: String, val color: HintColor)

// 高風險詞: 幾乎一定被審核擋下。輸入框 hint 與 A2「送出前確認」共用同一份 (單一真相源)。
private val HIGH_RISK_TERMS = listOf(
    // 英文 NSFW / 露骨 / 未成年
    "nude", "naked", "nudity", "nsfw", "porn", "pornographic",
    "explicit", "sexual", "erotic", "hentai",
    "underage", "minor child", "child porn", "loli", "lolicon",
    // 中文
    "裸體", "裸露", "性愛", "色情", "露胸", "露點", "露下體",
    "蘿莉", "未成年",
)

// 純 a-z 的英文詞用詞界比對,避免 sexual⊂homosexual / loli⊂Lolita 之類誤判;
// 含空格或中文的詞用單純 contains。
private fun matchesTerm(low: String, term: String): Boolean =
    if (term.all { it.code in 97..122 }) {
        Regex("(?<![a-z])${Regex.escape(term)}(?![a-z])").containsMatchIn(low)
    } else {
        low.contains(term)
    }

// A2: 回傳第一個命中的高風險詞 (給「送出前確認」對話框顯示),沒有則 null。
fun firstHighRiskTerm(prompt: String): String? {
    val low = prompt.lowercase()
    return HIGH_RISK_TERMS.firstOrNull { matchesTerm(low, it) }
}

// ── 「建議」鈕的審核風險字詞偵測 (與 A2 共用同一份字庫,單一真相源) ──────────
// 兩類詞處理方式刻意不同:
//   ① blocked  — 裸露/性/未成年/性暗示。xAI 後端審核是強制的,改寫也過不了,
//      所以這裡「只標紅 + 說明」,不提供任何「替代詞」。本工具不做規避審核用途;
//      給假替代只會讓使用者照樣被擋還白花錢 ($0.05/張)。
//   ② artistic — 血腥/暴力/武器/恐怖 這類「常被誤判」的藝術/歷史/恐怖帶。
//      提供幾個較溫和、保留創作意圖的改寫,點一下就換掉 → 提高過審率、省錢。
data class RiskHit(val term: String, val alternatives: List<String>)

// 性暗示帶: 跟 HIGH_RISK_TERMS 同樣只標紅、不給替代。
private val SEXUAL_ADJACENT = listOf(
    "bikini", "topless", "lingerie", "sensual", "suggestive", "fetish",
    "比基尼", "內衣", "戀物",
)

// 藝術帶: 詞 → 幾個較溫和、保留意圖的改寫。
private val ARTISTIC_ALTERNATIVES: List<Pair<String, List<String>>> = listOf(
    "血腥" to listOf("暗紅色潑灑", "戲劇性紅色光影", "濃烈紅色調"),
    "鮮血" to listOf("暗紅色液體", "猩紅顏料感", "紅色潑墨"),
    "blood" to listOf("dark red splashes", "crimson tone"),
    "gore" to listOf("dramatic dark-red imagery", "intense crimson mood"),
    "暴力" to listOf("激烈動作場面", "緊張對峙張力", "武打動作編排"),
    "violence" to listOf("intense action scene", "dramatic confrontation"),
    "violent" to listOf("intense", "high-energy"),
    "武器" to listOf("古代兵器道具", "道具刀劍", "器械道具"),
    "weapon" to listOf("prop weapon", "ceremonial blade"),
    "gun" to listOf("prop firearm", "sci-fi blaster prop"),
    "knife" to listOf("prop blade", "kitchen utensil"),
    "殺戮" to listOf("對決場面", "衝突高潮"),
    "kill" to listOf("confront", "defeat in a duel"),
    "murder" to listOf("stylized mystery scene", "dramatic confrontation"),
    "恐怖" to listOf("懸疑詭譎氛圍", "暗黑奇幻風格", "哥德式陰森美學"),
    "horror" to listOf("dark fantasy mood", "gothic eerie aesthetic"),
    "scary" to listOf("eerie", "suspenseful"),
    "屍體" to listOf("倒臥的身影", "靜止的人形"),
    "corpse" to listOf("fallen figure", "still silhouette"),
)

// 掃描 prompt → (blocked 命中詞清單, artistic 命中詞+替代清單)。給「建議」sheet 用。
fun scanPromptRisks(prompt: String): Pair<List<String>, List<RiskHit>> {
    if (prompt.isBlank()) return emptyList<String>() to emptyList<RiskHit>()
    val low = prompt.lowercase()
    val blocked = (HIGH_RISK_TERMS + SEXUAL_ADJACENT).filter { matchesTerm(low, it) }.distinct()
    val artistic = ARTISTIC_ALTERNATIVES
        .filter { matchesTerm(low, it.first) }
        .map { RiskHit(it.first, it.second) }
    return blocked to artistic
}

// A2: 送出前的軟確認對話框。只是提醒+省錢,不是硬擋 (誤判頂多多按一下)。
@Composable
fun ConfirmHighRiskDialog(
    term: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            ImagineIcon(name = "warning", fill = 1, tint = MaterialTheme.colorScheme.error)
        },
        title = { Text("可能被審核擋下", fontWeight = FontWeight.W700) },
        text = {
            Text(
                "偵測到「$term」這類詞。xAI 後端審核是強制的、負向 prompt 解不開,這次很可能被擋 — 且不論成敗都可能計費 (\$0.05/張)。仍要送出嗎?",
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("仍要送出") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("返回修改") } },
    )
}

private fun evaluatePrompt(p: String, maxChars: Int): PromptHint? {
    val s = p.trim()
    if (s.isEmpty()) return null
    val low = s.lowercase()

    if (HIGH_RISK_TERMS.any { matchesTerm(low, it) }) {
        return PromptHint("🚨", "高機率被擋 (僅參考)", HintColor.Red)
    }

    // 中風險: 可能被擋
    val medium = listOf(
        "bikini", "topless", "lingerie", "sensual", "suggestive",
        "blood", "gore", "violence", "violent", "kill", "killing", "murder",
        "weapon", "gun", "knife", "scary", "horror", "fetish",
        "比基尼", "內衣", "血腥", "暴力", "武器", "殺戮", "恐怖", "戀物",
    )
    if (medium.any { matchesTerm(low, it) }) {
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
