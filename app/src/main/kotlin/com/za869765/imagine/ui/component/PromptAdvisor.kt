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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

// 片語庫 — 點一下插入游標處。標題 to 片語清單。
private val SNIPPET_GROUPS = listOf(
    "光線" to listOf("黃昏暖光", "月夜冷光", "逆光剪影", "柔和散射光", "戲劇性側光", "霓虹夜景光"),
    "鏡頭" to listOf("臉部特寫", "中景", "全身遠景", "低角度仰拍", "俯視角", "跟拍鏡頭", "淺景深"),
    "色調" to listOf("暖橘色調", "冷藍色調", "高對比", "柔和粉彩", "電影感分級", "復古褪色"),
    "情緒" to listOf("含笑垂眸", "焦急", "決絕眼神", "落寞神情", "驚訝", "溫柔凝視"),
    "風格" to listOf("電影感", "35mm 底片質感", "動漫風", "寫實攝影", "油畫質感", "水彩風"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromptAdvisorSheet(
    currentPrompt: String,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val checks = analyzeElements(currentPrompt)
    val coveredCount = checks.count { it.present }

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
                text = "對照 5 要素，缺哪個補哪個更穩（$coveredCount / 5）",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

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
                text = "片語庫 — 點一下插入游標處",
                fontSize = 14.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            // ── 片語庫 ──
            SNIPPET_GROUPS.forEach { (group, items) ->
                Text(
                    text = group,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { snippet ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(20.dp),
                                )
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { onInsert(snippet) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = snippet,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.W500,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
