package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// Prompt 撰寫範本資料 + 底部彈出 sheet。
// 來源：4 支 AI 生圖教學影片提煉的 5 元素公式
//   主體 + 場景 + 構圖 + 動作 + 風格。
// 使用者點 PromptInput 右上的「範本」按鈕即彈此 sheet。

data class PromptTemplate(
    val title: String,
    val subtitle: String,
    val prompt: String,
)

// 4 個常用範本。內容用全形方括號 [...] 包預留欄位,
// 使用者填完後直接送 xAI Imagine API。
val DefaultPromptTemplates: List<PromptTemplate> = listOf(
    PromptTemplate(
        title = "古裝人物 · 狀態+妝容",
        subtitle = "重點：寫情緒狀態而非只寫長相",
        prompt = """
            【主體】1 位 [年齡] 的 [身份 例如 書生 / 宮中女子]，
            穿 [朝代 例如 唐 / 宋 / 漢] [服飾顏色與材質]，
            [髮型/頭飾 例如 高髻配步搖、半束髮]，
            當下狀態：[情緒 例如 緊抿嘴唇眉心微蹙 / 含笑垂眸]。

            【場景】[地點 例如 庭院 / 書房 / 街道]，
            [時辰光線 例如 黃昏暖橘光 / 月夜冷藍光]，
            [關鍵道具 例如 油燈 / 雨傘 / 卷軸]，
            [氛圍 例如 雨後潮濕 / 晨霧未散]。

            【構圖】[鏡頭距離 中景 / 特寫]，[視角 平視 / 低角度]，
            人物位於畫面 [位置]，佔畫面寬度約 [1/3 或 1/2]。

            【動作】因 [情緒動機] 而 [具體動作]。

            【風格】電影感古裝劇，淺景深，35mm 質感，
            色調 [暖橘 / 冷藍]。
        """.trimIndent(),
    ),
    PromptTemplate(
        title = "室內合成 · 人物進場景",
        subtitle = "重點：寫死比例與站位（身高 = 沙發椅背的幾倍）",
        prompt = """
            【主體】1 位 [人物簡述 配合參考圖]，自然融入場景。

            【場景】[室內類型 客廳 / 書房 / 餐廳]，
            [關鍵家具 例如 米色布沙發 / 木質長桌 / 復古檯燈]，
            [牆面/窗戶 例如 落地窗灑入自然光]，
            [氛圍 例如 北歐簡約 / 復古暖調]。

            【構圖】人物身高約 [參照物 例如 沙發椅背的 1.3 倍]，
            位於 [畫面位置 例如 沙發右側中段]，
            佔畫面寬度約 1/4 至 1/3。

            【動作】[一句話 例如 坐在沙發看書 / 站在窗邊望向窗外 /
            走向桌邊伸手拿杯子]。

            【風格】室內生活感攝影，柔和自然光，
            色調 [暖米 / 北歐冷調]，淺景深。
        """.trimIndent(),
    ),
    PromptTemplate(
        title = "動作場景 · 情緒驅動",
        subtitle = "重點：動作要有情緒因 (因 X 而 Y)",
        prompt = """
            【主體】1 位 [角色簡述 含服飾]，
            當下狀態：[情緒 例如 焦急 / 決絕 / 欣喜]。

            【場景】[地點與時間]，[氛圍細節 例如 紅燈籠搖晃、雨絲斜飄]。

            【構圖】[鏡頭距離 中景 / 全身]，[視角 平視 / 低角度]，
            [鏡頭運動 跟拍後退 / 手持搖晃 / 穩定推軌]。

            【動作】因 [情緒原因] → [具體動作含身體語言]，
            [衣物/頭髮動態 例如 紅色裙襬翻飛、髮絲被風吹起、
            白色衣袖隨步伐擺動]。

            【風格】[參考片型 c-drama / 韓劇 / 日影]，
            淺景深，[色調]，自然動作感不僵硬。
        """.trimIndent(),
    ),
    PromptTemplate(
        title = "多人物 · 編號標位",
        subtitle = "重點：多人物一定要編號 + 位置 + 各自動作",
        prompt = """
            【場景】[地點 例如 宮殿大殿 / 街市] ·
            [時辰光線] · [氛圍]。

            【角色 1 — 主體 畫面中央前景】
            [外觀] · [服飾] · 當下狀態：[情緒]。

            【角色 2 — 畫面左側中景】
            [外觀] · [服飾] · 動作：[做什麼]。

            【角色 3 — 畫面右側中景】
            [外觀] · [服飾] · 動作：[做什麼]。

            【角色間互動】[誰看向誰 / 誰朝誰移動 / 武器/手勢指向]。

            【構圖】[鏡頭距離 中景 / 全景]，[視角]，
            主角佔畫面寬度約 1/3，左右角色各佔 1/4。

            【風格】電影感，[色調]，淺景深聚焦主角。
        """.trimIndent(),
    ),
)

// 底部彈出範本選單。點任一範本 → 呼叫 onPick(prompt) → 自動關閉。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplateSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    templates: List<PromptTemplate> = DefaultPromptTemplates,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Prompt 範本",
                fontSize = 16.sp,
                fontWeight = FontWeight.W700,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
            Text(
                text = "點選填入後，把【】內方括號裡的提示換成你的內容",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )

            templates.forEach { tpl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            onPick(tpl.prompt)
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Column {
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
                                text = tpl.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W600,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = tpl.subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        // 預覽前 60 字,讓使用者看出大致內容再決定要不要填入
                        val preview = tpl.prompt
                            .lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString(" ")
                            .take(60) + "…"
                        Text(
                            text = preview,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
