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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.launch

// Prompt 範本 — 兩種用法,都讓使用者最後能在輸入框再打字改:
//   ① 現成範例: 已寫好、無方括號、可直接生成的完整 prompt,點「使用」直接填。
//   ② 自己組  : 9 個欄位各一排 chips,點選或「全部隨機」,即時組成乾淨完整 prompt。
// 公式骨架仍是 5 元素: 主體 + 場景 + 構圖 + (動作) + 風格。

data class PromptExample(val tag: String, val text: String)

// ── ① 現成完整範例 (無方括號,可直接送 xAI Imagine) ──
val READY_PROMPTS: List<PromptExample> = listOf(
    PromptExample(
        "古裝人物",
        "主體：一位二十出頭的宮中女子，穿月白色絲質襦裙、高髻配步搖，含笑垂眸。" +
            "場景：黃昏的古典庭院，暖橘色斜光灑落，石桌上一盞油燈。" +
            "構圖：胸上中景、平視，人物位於畫面中央偏右。" +
            "風格：電影感古裝劇，淺景深，35mm 底片質感，暖橘色調。",
    ),
    PromptExample(
        "現代人像",
        "主體：一位三十歲的都會女性，穿米色針織毛衣，溫柔凝視鏡頭。" +
            "場景：午後灑入陽光的落地窗咖啡廳，木質桌上一杯拿鐵。" +
            "構圖：臉部特寫、微側 45 度。" +
            "風格：日系清新寫實，柔和自然光，淺景深，暖米色調。",
    ),
    PromptExample(
        "動作場景",
        "主體：一名紅衣女俠，眼神決絕。" +
            "場景：夜晚搖晃紅燈籠的古鎮街道，雨絲斜飄。" +
            "構圖：全身中景、低角度仰拍，跟拍後退。" +
            "動作：因追擊而疾奔，紅色裙襬與髮絲隨風翻飛。" +
            "風格：武俠電影感，高對比冷藍調，自然動作感不僵硬。",
    ),
    PromptExample(
        "室內人物",
        "主體：一位青年男子自然融入場景，坐在沙發看書。" +
            "場景：北歐簡約客廳，米色布沙發、木質長桌、復古檯燈，落地窗灑入自然光。" +
            "構圖：人物身高約沙發椅背的 1.3 倍，位於沙發右側中段，佔畫面約 1/3。" +
            "風格：室內生活感攝影，柔和自然光，暖米色調，淺景深。",
    ),
    PromptExample(
        "多人物",
        "場景：宮殿大殿，正午頂光，莊嚴肅穆。" +
            "角色一（畫面中央前景）：身著明黃龍袍的帝王，神情威嚴。" +
            "角色二（左側中景）：躬身稟報的青衣文官。" +
            "角色三（右側中景）：按劍而立的銀甲武將。" +
            "互動：兩名臣子皆望向中央帝王。" +
            "構圖：全景平視，主角佔畫面約 1/3，左右各佔 1/4。" +
            "風格：電影感，沉穩暖金色調，淺景深聚焦主角。",
    ),
    PromptExample(
        "風景",
        "主體：晨霧中的層疊遠山與一棵孤松。" +
            "場景：清晨高山，薄霧在山谷間流動，第一道金光打在山尖。" +
            "構圖：廣角全景、平視，孤松位於畫面右側三分之一處。" +
            "風格：大景風光攝影，柔和晨光，冷藍到暖金的漸層色調，高細節。",
    ),
    PromptExample(
        "動物",
        "主體：一隻蓬鬆的橘貓，琥珀色眼睛好奇直視。" +
            "場景：窗邊灑入午後陽光的木地板，旁邊一盆綠植。" +
            "構圖：臉部特寫、平視，淺景深虛化背景。" +
            "風格：溫暖寫實寵物攝影，自然光，暖橘色調，毛髮細節清晰。",
    ),
    PromptExample(
        "美食",
        "主體：一碗熱氣騰騰的日式拉麵，半熟蛋與叉燒擺放整齊。" +
            "場景：木質餐桌，背景虛化的暖色居酒屋燈光。" +
            "構圖：俯視 45 度特寫，蒸氣上升。" +
            "風格：日系美食攝影，暖黃打光，高對比，淺景深，食物質感誘人。",
    ),
    PromptExample(
        "科幻",
        "主體：一名身著流線型銀色動力裝甲的女戰士，面罩泛著藍光。" +
            "場景：霓虹閃爍的賽博龐克都市雨夜，高樓全像廣告倒映在濕地面。" +
            "構圖：七分身中景、低角度仰拍。" +
            "風格：賽博龐克電影感，藍紫霓虹高對比，淺景深，膠片顆粒。",
    ),
)

// ── ② 條件選擇器欄位 (單一通用人物/場景 builder) ──
data class BuilderField(val label: String, val options: List<String>)

val BUILDER_FIELDS: List<BuilderField> = listOf(
    BuilderField("風格類型", listOf("電影感寫實", "古裝劇", "日系動漫", "韓系清新", "油畫質感", "黑白底片")),
    BuilderField("主體", listOf("二十出頭的女子", "少年書生", "中年男子", "白髮老者", "一隻橘貓", "一名女戰士")),
    BuilderField("服飾", listOf("月白色絲質長裙", "靛藍棉麻長衫", "黑色皮衣", "米色針織毛衣", "金屬感盔甲", "復古西裝")),
    BuilderField("情緒狀態", listOf("含笑垂眸", "緊抿嘴唇眉心微蹙", "眼神決絕", "落寞出神", "驚訝張口", "溫柔凝視")),
    BuilderField("場景地點", listOf("古典庭院", "堆滿書卷的書房", "雨後石板街道", "灑入陽光的落地窗客廳", "霧氣繚繞的森林", "海邊礁岩")),
    BuilderField("光線時辰", listOf("黃昏暖橘光", "月夜冷藍光", "正午強光", "陰雨天散射光", "清晨薄霧光", "室內暖黃燈光")),
    BuilderField("構圖鏡頭", listOf("臉部特寫", "胸上中景", "七分身", "全身遠景", "過肩鏡頭")),
    BuilderField("視角", listOf("平視", "低角度仰拍", "高角度俯視", "微側 45 度")),
    BuilderField("色調", listOf("暖橘色調", "冷藍色調", "高對比", "柔和粉彩", "復古褪色")),
)

// 把選好的欄位組成一段乾淨、無方括號、可直接生成的完整 prompt。
fun assembleBuilderPrompt(sel: Map<String, String>): String {
    fun v(k: String) = sel[k].orEmpty().trim()
    val subject = buildString {
        append(v("主體"))
        if (v("服飾").isNotEmpty()) append("，穿").append(v("服飾"))
        if (v("情緒狀態").isNotEmpty()) append("，").append(v("情緒狀態"))
    }
    val scene = listOf(v("場景地點"), v("光線時辰")).filter { it.isNotEmpty() }.joinToString("，")
    val comp = listOf(v("構圖鏡頭"), v("視角")).filter { it.isNotEmpty() }.joinToString("，")
    val style = listOf(v("風格類型"), v("色調"), "淺景深").filter { it.isNotEmpty() }.joinToString("，")
    return buildString {
        if (subject.isNotEmpty()) append("主體：").append(subject).append("。\n")
        if (scene.isNotEmpty()) append("場景：").append(scene).append("。\n")
        if (comp.isNotEmpty()) append("構圖：").append(comp).append("。\n")
        if (style.isNotEmpty()) append("風格：").append(style).append("。")
    }.trim()
}

// 底部彈出範本面板。tab 切換「現成範例 / 自己組」,點「使用/填入」→ onUse(prompt) 並關閉。
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptTemplateSheet(
    onDismiss: () -> Unit,
    onUse: (String) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("ready") }

    // 條件選擇器狀態: 每欄預設選第一個選項
    val selected = remember {
        mutableStateMapOf<String, String>().apply {
            BUILDER_FIELDS.forEach { put(it.label, it.options.first()) }
        }
    }

    fun pick(prompt: String) {
        onUse(prompt)
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Prompt 範本",
                fontSize = 16.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )
            Text(
                text = "直接挑一條現成的，或用條件自己組一條；填入後都能再打字改。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            SegmentedTab(
                options = listOf(
                    SegmentedOption("ready", "現成範例"),
                    SegmentedOption("build", "自己組"),
                ),
                activeId = tab,
                onSelected = { tab = it },
            )

            Box(modifier = Modifier.padding(top = 14.dp))

            if (tab == "ready") {
                // ── ① 現成範例 ──
                RandomBar(label = "🎲  隨機一條") { pick(READY_PROMPTS.random().text) }
                READY_PROMPTS.forEach { ex ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImagineIcon(
                                name = "auto_awesome",
                                size = 15.dp,
                                fill = 1,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = ex.tag,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.W700,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = ex.text,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextActionButton(
                                label = "複製",
                                icon = "content_copy",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { Clipboard.copy(ctx, ex.text, toastMsg = "已複製範例") },
                            )
                            TextActionButton(
                                label = "使用",
                                icon = "check",
                                onClick = { pick(ex.text) },
                            )
                        }
                    }
                }
            } else {
                // ── ② 自己組 (條件選擇器) ──
                RandomBar(label = "🎲  全部隨機") {
                    BUILDER_FIELDS.forEach { selected[it.label] = it.options.random() }
                }

                BUILDER_FIELDS.forEach { field ->
                    Text(
                        text = field.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W600,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        field.options.forEach { opt ->
                            SelectChip(
                                text = opt,
                                active = selected[field.label] == opt,
                                onClick = { selected[field.label] = opt },
                            )
                        }
                    }
                }

                // 即時預覽
                Text(
                    text = "預覽",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
                val preview = assembleBuilderPrompt(selected)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = preview,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextActionButton(
                        label = "複製",
                        icon = "content_copy",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { Clipboard.copy(ctx, preview, toastMsg = "已複製") },
                    )
                    TextActionButton(
                        label = "填入",
                        icon = "check",
                        onClick = { pick(preview) },
                    )
                }
            }
        }
    }
}

// 「隨機」橫條鈕 (tonal,清楚可點)。
@Composable
private fun RandomBar(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

// 條件選擇器的可選 chip,選中時 primary 高亮。
@Composable
private fun SelectChip(text: String, active: Boolean, onClick: () -> Unit) {
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
                else MaterialTheme.colorScheme.surface
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
