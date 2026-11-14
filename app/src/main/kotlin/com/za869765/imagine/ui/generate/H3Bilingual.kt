package com.za869765.imagine.ui.generate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.TextActionButton
import kotlinx.coroutines.launch

// ───────────────────────── v3.3.4 中英對照 ─────────────────────────
// 整段英文提示詞切成一句一句,旁邊顯示中文;點某一句可以改中文再翻回英文(或直接改英文),只換那一句,其他句子一個字都不動。
// 翻譯用設定裡選的 AI(預設筆電 Gemma)。翻過的句子記在這次開啟期間,重開面板不用再翻。

/** 英文句子 → 中文(這次開啟 APP 期間有效) */
private val biCache = HashMap<String, String>()

/** 以句號、分號、問號、驚嘆號後面的空白為界切句;句子用一個空白接回去就是原文(原文裡連續空白或換行會變成一個空白)。 */
internal fun h3SplitPrompt(text: String): List<String> =
    text.trim().split(Regex("(?<=[.;!?])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }

/** 把「編號|中文」幾行讀回來;編號對不上的略過 */
private fun parseNumbered(out: String, n: Int): Map<Int, String> =
    out.lines().mapNotNull { l ->
        val i = l.indexOf('|')
        val k = if (i > 0) l.substring(0, i).trim().toIntOrNull() else null
        if (k == null || k !in 1..n) null else k to l.substring(i + 1).trim()
    }.filter { it.second.isNotEmpty() }.toMap()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun H3BilingualSheet(prompt: String, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val original = remember(prompt) { h3SplitPrompt(prompt) }
    var en by remember(prompt) { mutableStateOf(original) }
    var zh by remember(prompt) { mutableStateOf(original.map { biCache[it].orEmpty() }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableIntStateOf(-1) }
    var draftZh by remember { mutableStateOf("") }
    var draftEn by remember { mutableStateOf("") }

    fun translateMissing() {
        val todo = en.indices.filter { zh[it].isBlank() }
        if (todo.isEmpty() || busy) return
        busy = true
        error = null
        scope.launch {
            // 一次最多 12 句,免得回覆太長被截斷
            for (chunk in todo.chunked(12)) {
                val text = chunk.mapIndexed { j, i -> "${j + 1}|${en[i]}" }.joinToString("\n")
                when (val r = H3Client.optimizePrompt(prefs, "en2zh", text, emptyMap(), prefs.h3OptimizeBrain)) {
                    is H3Client.Result.Ok -> {
                        val got = parseNumbered(r.value.optimized, chunk.size)
                        zh = zh.toMutableList().also { list ->
                            chunk.forEachIndexed { j, i -> got[j + 1]?.let { t -> list[i] = t; biCache[en[i]] = t } }
                        }
                        if (got.size < chunk.size) error = "有 ${chunk.size - got.size} 句沒翻到，按「補翻沒翻到的」再試"
                    }
                    is H3Client.Result.Err -> {
                        error = "翻譯失敗：${r.message}"
                        break
                    }
                }
            }
            busy = false
        }
    }
    LaunchedEffect(prompt) { translateMissing() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("中英對照", fontSize = 16.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "整段提示詞切成一句一句，上面是中文、下面是送給模型的英文。點任何一句可以改：改中文再按「翻成英文」，或直接改英文。" +
                    "只會換掉那一句，其他句子一個字都不動；改過的句子會標「已改」。最後按最下面的「套用到輸入框」才會生效。",
                fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) {
                Text("翻譯中…（本機正在生影的話要排隊，可能要幾分鐘）", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            if (!busy && zh.any { it.isBlank() }) TextActionButton(label = "補翻沒翻到的", onClick = { translateMissing() })

            en.forEachIndexed { i, e ->
                val changed = e != original.getOrNull(i)
                ImagineCard(pad = 12) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                                if (open == i) open = -1 else { open = i; draftZh = zh[i]; draftEn = e }
                            },
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "${i + 1}" + if (changed) "・已改" else "",
                                fontSize = 11.sp,
                                color = if (changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(zh[i].ifBlank { "（還沒翻）" }, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text(e, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (open == i) {
                            OutlinedTextField(value = draftZh, onValueChange = { draftZh = it }, label = { Text("中文（改這裡）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            TextActionButton(
                                label = if (busy) "翻譯中…" else "翻成英文（預覽在下面，還不會套用）",
                                enabled = !busy && draftZh.isNotBlank(),
                                onClick = {
                                    busy = true
                                    error = null
                                    scope.launch {
                                        when (val r = H3Client.optimizePrompt(prefs, "zh2en", "原英文: $e\n新中文: ${draftZh.trim()}", emptyMap(), prefs.h3OptimizeBrain)) {
                                            is H3Client.Result.Ok -> draftEn = r.value.optimized.lines().joinToString(" ") { it.trim() }.trim()
                                            is H3Client.Result.Err -> error = "翻譯失敗：${r.message}"
                                        }
                                        busy = false
                                    }
                                },
                            )
                            OutlinedTextField(value = draftEn, onValueChange = { draftEn = it }, label = { Text("英文（會送給模型的就是這句，也可以直接改）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextActionButton(
                                    label = "換掉這一句",
                                    enabled = !busy && draftEn.isNotBlank(),
                                    onClick = {
                                        val newEn = draftEn.trim()
                                        en = en.toMutableList().also { it[i] = newEn }
                                        zh = zh.toMutableList().also { it[i] = draftZh.trim() }
                                        if (draftZh.isNotBlank()) biCache[newEn] = draftZh.trim()
                                        open = -1
                                    },
                                )
                                if (changed) {
                                    TextActionButton(
                                        label = "還原這一句",
                                        onClick = {
                                            val o = original[i]
                                            en = en.toMutableList().also { it[i] = o }
                                            zh = zh.toMutableList().also { it[i] = biCache[o].orEmpty() }
                                            open = -1
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val edits = en.indices.count { en[it] != original.getOrNull(it) }
            TextActionButton(
                label = if (edits > 0) "套用到輸入框（改了 $edits 句）" else "沒有修改",
                enabled = edits > 0 && !busy,
                onClick = {
                    onApply(en.joinToString(" "))
                    scope.launch { sheetState.hide(); onDismiss() }
                },
            )
        }
    }
}
