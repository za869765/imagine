package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// 智慧建議 sheet：
//   上半 — 對目前輸入框的 prompt 做「5 要素」健康檢查(主體/場景/構圖/動作/風格)
//   下半 — 片語庫,點一下插入到目前游標位置
// 偵測是 keyword heuristic,僅供參考(綠勾不代表完美,缺項也不一定要補)。

private data class ElementCheck(val name: String, val present: Boolean, val tip: String)

private val SUBJECT_KW = listOf(
    "主體", "人物", "角色", "男", "女", "女子", "男子", "少女", "少年", "老人",
    "孩", "嬰", "貓", "狗", "鳥", "動物", "書生", "位 ", "一位", "1 位",
    "person", "people", "man", "woman", "girl", "boy", "character", "portrait", "cat", "dog",
)
private val SCENE_KW = listOf(
    "場景", "背景", "室內", "室外", "街", "房", "庭院", "森林", "城市", "山", "海",
    "天空", "街道", "房間", "書房", "客廳", "餐廳", "宮", "殿", "夜", "白天", "雨", "雪", "場",
    "scene", "background", "indoor", "outdoor", "street", "room", "forest", "city",
    "mountain", "sea", "sky", "night", "palace",
)
private val COMPOSITION_KW = listOf(
    "鏡頭", "特寫", "中景", "全身", "遠景", "近景", "視角", "俯視", "仰視",
    "低角度", "高角度", "構圖", "畫面", "前景", "佔畫面", "景深",
    "close", "wide", "shot", "angle", "composition", "framing", "foreground", "closeup",
)
private val ACTION_KW = listOf(
    "動作", "走", "跑", "坐", "站", "跳", "看", "笑", "哭", "轉", "揮", "拿",
    "飛", "奔", "姿勢", "表情", "情緒", "凝視", "垂眸",
    "running", "walk", "sit", "stand", "jump", "look", "smile", "cry", "pose",
    "emotion", "gaze", "gesture",
)
private val STYLE_KW = listOf(
    "風格", "電影感", "油畫", "動漫", "寫實", "水彩", "色調", "暖", "冷",
    "對比", "底片", "質感", "膠片", "粉彩",
    "cinematic", "anime", "realistic", "watercolor", "oil", "style", "color",
    "tone", "film", "35mm", "photoreal",
)

private fun analyzeElements(prompt: String): List<ElementCheck> {
    val p = prompt.lowercase()
    fun has(keys: List<String>) = keys.any { p.contains(it.lowercase()) }
    return listOf(
        ElementCheck("主體", has(SUBJECT_KW), "誰/什麼是主角？例如：1 位紅衣女子、一隻貓"),
        ElementCheck("場景", has(SCENE_KW), "在哪裡？例如：唐代街道、北歐客廳、森林"),
        ElementCheck("構圖", has(COMPOSITION_KW), "鏡頭怎麼擺？例如：中景平視、低角度特寫"),
        ElementCheck("動作", has(ACTION_KW), "在做什麼/什麼情緒？例如：奔跑、含笑垂眸"),
        ElementCheck("風格", has(STYLE_KW), "什麼畫風色調？例如：電影感暖橘調、動漫風"),
    )
}

// B2：給輸入框工具列「建議 n/5」徽章用 — 算目前涵蓋了幾個要素。
fun promptElementCoverage(prompt: String): Int = analyzeElements(prompt).count { it.present }

// B4：把現有描述(可空)擴寫成 5 要素骨架,缺的留【】佔位給使用者填。
// 已寫的字當「主體」種子塞進去,其餘四項留空待補,搭配「跳格」鈕逐格填。
fun buildScaffold(seed: String): String {
    val s = seed.trim()
    val subject = if (s.isEmpty()) "【主體：誰/什麼，穿什麼，什麼情緒】" else s
    return buildString {
        append(subject)
        append("，")
        append("【場景：在哪裡，什麼光線時辰】")
        append("，")
        append("【構圖：景別＋視角，例如中景平視】")
        append("，")
        append("【動作：在做什麼，什麼神態】")
        append("，")
        append("【風格：畫風＋色調，例如電影感暖橘】")
    }
}

// 「擇一」類別:同時出現多個多半互相打架(例:兩個色調 / 兩種風格)。只在這些類別偵測,
// 避免像「配件」這種本來就能戴多個的誤報。純提醒、不自動改字 (尊重使用者原意)。
private data class ConflictHit(val field: String, val terms: List<String>)

private val CONFLICT_FIELD_LABELS = listOf("風格類型", "色調", "構圖鏡頭", "視角", "光線時辰")

private fun detectConflicts(prompt: String): List<ConflictHit> {
    if (prompt.isBlank()) return emptyList()
    return CONFLICT_FIELD_LABELS.mapNotNull { label ->
        val field = BUILDER_FIELDS.firstOrNull { it.label == label } ?: return@mapNotNull null
        val hits = field.options.filter { it != "(不指定)" && isStandaloneOption(prompt, it) }
        if (hits.size >= 2) ConflictHit(label, hits) else null
    }
}

// 片語庫已改為衍生自 BUILDER_FIELDS (與「自己組」同步、單一真相源)，渲染見 PromptAdvisorSheet。

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptAdvisorSheet(
    currentPrompt: String,
    recentSnippets: List<String> = emptyList(),
    onDismiss: () -> Unit,
    // (片語, 同類型所有選項) — 呼叫端據此判斷:prompt 內已有同類型詞就替換、否則插入
    onInsert: (String, List<String>) -> Unit,
    onExpand: (String) -> Unit = {},
    onReplace: (String, String) -> Unit = { _, _ -> },
    forVideo: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val checks = analyzeElements(currentPrompt)
    val coveredCount = checks.count { it.present }
    // 圖片模式隱藏影片限定類別 (動作/聲音/字幕)
    val fields = if (forVideo) BUILDER_FIELDS else BUILDER_FIELDS.filter { it.label !in VIDEO_ONLY_FIELDS }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 標題 ──
            Text(
                text = "提示詞健康檢查",
                fontSize = 16.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "先處理高風險字詞與衝突，再用 5 要素補強（$coveredCount / 5）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            // ── 審核風險字詞 (置頂) ──────────────────────────────
            // blocked = 裸露/性/未成年/性暗示: 只標紅+說明,不給替代 (不做規避審核)。
            // artistic = 血腥/暴力/武器/恐怖: 給較溫和替代,點一下換掉。
            val (blockedTerms, artisticHits) = scanPromptRisks(currentPrompt)
            Text(
                text = "審核風險字詞",
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            if (currentPrompt.isBlank()) {
                Text(
                    text = "先輸入一些描述，這裡會標出可能被審核的字詞並提供替代。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (blockedTerms.isEmpty() && artisticHits.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ImagineIcon(
                        name = "check",
                        size = 18.dp,
                        fill = 1,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "目前沒偵測到高風險字詞",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                if (blockedTerms.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImagineIcon(
                                name = "warning",
                                size = 18.dp,
                                fill = 1,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = "幾乎一定被擋：" + blockedTerms.joinToString("、"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W700,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Text(
                            text = "xAI 後端審核是強制的，改寫也過不了 — 建議換個方向，別硬湊（送出仍會計費）。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                artisticHits.forEach { hit ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImagineIcon(
                                name = "warning",
                                size = 16.dp,
                                fill = 0,
                                tint = Color(0xFFE0A500),
                            )
                            Text(
                                text = "「${hit.term}」可能被誤判 — 點替代詞換掉",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            hit.alternatives.forEach { alt ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(20.dp),
                                        )
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable { onReplace(hit.term, alt) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = alt,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.W600,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 質感優化 (去油膩/去 AI 味) — 與審核風險分流 ──────────────
            // 油膩/塑料/堆精度詞 → 正向質感詞替換 (xAI 無負向 prompt,改正向措辭)。
            // 這些詞不會被審核擋,純粹讓成像更真實 — 點替代詞換掉。
            val qualityHits = scanQualityHits(currentPrompt)
            if (qualityHits.isNotEmpty()) {
                Text(
                    text = "質感優化",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
                qualityHits.forEach { hit ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImagineIcon(
                                name = "auto_awesome",
                                size = 16.dp,
                                fill = 1,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "「${hit.term}」偏油膩/堆精度 — 點換成正向質感詞",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            hit.alternatives.forEach { alt ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(20.dp),
                                        )
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                        .clickable { onReplace(hit.term, alt) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = alt,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.W600,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 可能衝突的設定 (同一「擇一」類別出現多個 → 提醒,不自動改) ──
            val conflicts = detectConflicts(currentPrompt)
            if (conflicts.isNotEmpty()) {
                Text(
                    text = "可能衝突的設定",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                )
                conflicts.forEach { c ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImagineIcon(name = "warning", size = 16.dp, fill = 0, tint = Color(0xFFE0A500))
                            Text(
                                text = "「${c.field}」出現多個 — 通常擇一",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = c.terms.joinToString("、"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            text = "這幾個同類設定可能互相打架，留一個方向通常更穩（這裡只提醒，不會自動改你的字）。",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            // ── 分隔 (風險區 ↔ 下方 5 要素健檢/片語庫) ──
            Box(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            // ── B4：一鍵擴寫成 5 要素骨架 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        onExpand(buildScaffold(currentPrompt))
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ImagineIcon(
                    name = "auto_awesome",
                    size = 20.dp,
                    fill = 1,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = if (currentPrompt.isBlank()) "產生 5 要素空白骨架" else "把現有描述擴寫成 5 要素骨架",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            // ── 5 要素檢查 ──
            checks.forEach { c ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ImagineIcon(
                        name = if (c.present) "check" else "warning",
                        size = 18.dp,
                        fill = if (c.present) 1 else 0,
                        tint = if (c.present) MaterialTheme.colorScheme.primary
                        else Color(0xFFE0A500),
                    )
                    Column {
                        Text(
                            text = c.name + if (c.present) "  已涵蓋" else "  建議補上",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (!c.present) {
                            Text(
                                text = c.tip,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                }
            }

            // ── 分隔 ──
            Box(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )

            Text(
                text = "片語庫 — 點類別展開，點片語插入游標處",
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            // expandedGroup 提前宣告(放在條件區塊之前,remember 槽位不受 recentSnippets 變動影響)
            var expandedGroup by remember { mutableStateOf<String?>(null) }

            // 最近用過 (置頂、常駐展開)
            if (recentSnippets.isNotEmpty()) {
                Text(
                    text = "最近用過",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recentSnippets.forEach { snippet ->
                        val sameType = BUILDER_FIELDS.firstOrNull { snippet in it.options }?.options
                            ?: emptyList()
                        SnippetChip(text = snippet, active = isStandaloneOption(currentPrompt, snippet)) {
                            onInsert(snippet, sameType)
                        }
                    }
                }
            }

            // 類別 — 與「自己組」同步,衍生自 BUILDER_FIELDS。
            // 點類別展開;點片語=智慧插入(prompt 內已有同類型詞就替換、否則插入游標處)。
            // 標題顯示目前該類型在 prompt 內的選擇(目前：X),展開時對應 chip 也高亮。
            fields.forEach { field ->
                val opts = field.options.filter { it != "(不指定)" }
                val active = opts.filter { isStandaloneOption(currentPrompt, it) }.maxByOrNull { it.length }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedGroup = if (expandedGroup == field.label) null else field.label
                        }
                        .padding(top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${field.label}  (${opts.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (active != null) {
                            Text(
                                text = "目前：$active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                    }
                    ImagineIcon(
                        name = if (expandedGroup == field.label) "expand_less" else "expand_more",
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (expandedGroup == field.label) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        opts.forEach { snippet ->
                            SnippetChip(text = snippet, active = snippet == active) {
                                onInsert(snippet, field.options)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 片語庫的可插入 chip — 點一下插入游標處。
@Composable
private fun SnippetChip(text: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(20.dp),
            )
            .background(
                if (active) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.W700 else FontWeight.W500,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
