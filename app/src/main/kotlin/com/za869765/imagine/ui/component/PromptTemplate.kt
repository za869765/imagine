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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.util.Clipboard
import kotlinx.coroutines.launch

// Prompt 範本 — 兩種用法,都讓使用者最後能在輸入框再打字改:
//   ① 現成範例: 已寫好、無方括號、可直接生成的完整 prompt,點「使用」直接填。
//   ② 自己組  : 每個條件一行「可搜尋下拉」(點開→搜尋框+清單),容納幾十個選項;
//               每行附 🎲 單欄隨機、上方「全部隨機」、下方即時預覽,填入後可再打字改。
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
    PromptExample(
        "婚紗",
        "主體：一位身穿白色長拖尾婚紗的新娘，手捧白玫瑰花束，回眸淺笑。" +
            "場景：黃昏海邊草坡，遠處夕陽沉入海平面，暖金色逆光。" +
            "構圖：全身遠景、低角度，新娘位於畫面左側三分之一，裙襬隨風揚起。" +
            "風格：唯美婚紗攝影，柔焦逆光，暖金色調，淺景深。",
    ),
    PromptExample(
        "校園青春",
        "主體：一位高中女學生，穿水手服制服，抱著課本燦爛大笑。" +
            "場景：午後灑滿陽光的教室走廊，窗外是綠樹與藍天。" +
            "構圖：胸上中景、微側 45 度，逆光帶些許耀光。" +
            "風格：日系青春寫實，柔和自然光，清新淡雅色調，淺景深。",
    ),
    PromptExample(
        "職場專業",
        "主體：一位三十多歲的女性主管，穿合身深藍西裝外套，自信淺笑、雙臂環抱。" +
            "場景：現代玻璃帷幕辦公室，背景虛化的城市天際線。" +
            "構圖：胸上中景、平視，人物居中。" +
            "風格：商業形象攝影，柔和棚燈，冷靜俐落色調，淺景深。",
    ),
    PromptExample(
        "旅遊風情",
        "主體：一位背著相機的年輕女子，戴草帽回頭微笑。" +
            "場景：希臘聖托里尼藍頂白牆小巷，正午陽光，遠處愛琴海湛藍。" +
            "構圖：七分身、平視，人物位於畫面右側，巷弄向遠方延伸。" +
            "風格：旅遊雜誌寫實，明亮飽和的地中海色調，高細節。",
    ),
    PromptExample(
        "雪景",
        "主體：一位穿米白色長大衣、圍紅圍巾的女子，呵氣望向遠方。" +
            "場景：靜謐的雪夜街道，路燈下細雪紛飛，店家暖黃櫥窗。" +
            "構圖：胸上中景、平視，雪花前景散景。" +
            "風格：電影感冬夜寫實，暖燈與冷藍對比，淺景深，膠片質感。",
    ),
    PromptExample(
        "夜市煙火",
        "主體：一位穿浴衣的少女，手持蘋果糖抬頭仰望。" +
            "場景：日本夏日祭典夜市，攤位燈籠林立，夜空綻放絢爛煙火。" +
            "構圖：胸上中景、低角度仰拍，背景煙火散景。" +
            "風格：日系夏夜寫實，霓虹與煙火高對比，暖色調，淺景深。",
    ),
    PromptExample(
        "運動瞬間",
        "主體：一名女子田徑選手，衝刺起跑的爆發瞬間，汗水飛濺。" +
            "場景：黃昏的紅色跑道體育場，逆光長影。" +
            "構圖：全身中景、低角度側面跟拍。" +
            "動作：前傾衝刺、肌肉緊繃，動態模糊強調速度感。" +
            "風格：運動攝影，高速快門凝結瞬間，暖金逆光，高對比。",
    ),
    PromptExample(
        "情侶雙人",
        "場景：秋日楓紅公園長椅，黃昏暖光，落葉紛飛。" +
            "角色一（左）：一位短髮女子，依偎著笑。" +
            "角色二（右）：一位高個男子，低頭溫柔看著她。" +
            "互動：兩人手牽手、額頭相觸。" +
            "構圖：胸上中景、平視，兩人居中。" +
            "風格：溫暖情侶寫真，柔和逆光，暖橘色調，淺景深。",
    ),
    PromptExample(
        "音樂現場",
        "主體：一位女歌手，閉眼忘情高歌，手握麥克風。" +
            "場景：昏暗 Live House 舞台，背後藍紫色聚光燈與煙霧。" +
            "構圖：胸上中景、低角度仰拍，光束穿過煙霧。" +
            "風格：演唱會現場攝影，高對比舞台光，藍紫冷調帶暖膚色，膠片顆粒。",
    ),
    PromptExample(
        "海島度假",
        "主體：一位穿草帽與淺色洋裝的女子，赤腳走在沙灘踢浪，開心回眸。" +
            "場景：熱帶海島白沙灘，碧綠海水與藍天白雲，正午陽光。" +
            "構圖：全身遠景、平視，人物位於畫面左側，海岸線延伸至遠方。" +
            "風格：度假寫真，明亮通透，飽和的海島色調，高細節。",
    ),
    PromptExample(
        "雨天街景",
        "主體：一位撐透明傘的女子，停下腳步望向櫥窗。" +
            "場景：入夜的都市街道，地面積水倒映霓虹招牌，細雨綿綿。" +
            "構圖：全身中景、平視，霓虹倒影為前景。" +
            "風格：都市夜雨電影感，霓虹高對比，青橙色調，淺景深，雨絲清晰。",
    ),
    PromptExample(
        "優雅長者",
        "主體：一位銀白短髮的優雅老婦人，戴珍珠耳環，溫柔淺笑看向鏡頭。" +
            "場景：灑入柔光的老宅書房，背景是木質書櫃與一盆蘭花。" +
            "構圖：臉部特寫、平視，窗邊側光。" +
            "風格：人文肖像攝影，柔和自然光，溫暖低飽和色調，皺紋與膚質細節真實。",
    ),
    PromptExample(
        "親子日常",
        "場景：午後灑滿陽光的客廳地毯，溫馨自然。" +
            "角色一：一位年輕媽媽，盤腿而坐溫柔微笑。" +
            "角色二：一個學步的幼兒，咯咯笑撲向媽媽懷裡。" +
            "互動：媽媽張開雙臂迎接孩子。" +
            "構圖：全身中景、平視，兩人居中。" +
            "風格：溫馨家庭生活攝影，柔和自然光，暖米色調，淺景深。",
    ),
    PromptExample(
        "時尚雜誌",
        "主體：一位高挑模特兒，穿前衛剪裁的黑色禮服，下巴微抬、神情冷豔。" +
            "場景：純色調棚拍背景，戲劇性側逆光勾勒輪廓。" +
            "構圖：七分身、低角度，留白構圖。" +
            "風格：高端時尚雜誌封面，棚燈硬光高對比，低飽和高級灰色調，高細節。",
    ),
    PromptExample(
        "黑白人像",
        "主體：一位中年男子，神情堅毅若有所思，臉部紋理與鬍渣清晰。" +
            "場景：深色背景，一束窗邊側光打在臉的一側，明暗對比強烈。" +
            "構圖：臉部特寫、微側，林布蘭光。" +
            "風格：經典黑白肖像攝影，高對比層次，膠片顆粒，質感細膩。",
    ),
)

// ── ② 條件選擇器欄位 (單一通用人物/場景 builder,每欄可搜尋下拉) ──
// 「(不指定)」放第一個的欄位 → 預設不帶入,使用者主動挑才加進 prompt;
// 其餘欄位預設第一個真值 → 一進來預覽就有一段完整可用的 prompt。
data class BuilderField(val label: String, val options: List<String>)

val BUILDER_FIELDS: List<BuilderField> = listOf(
    BuilderField(
        "風格類型",
        listOf(
            "電影感寫實", "自然光人像", "日系清新", "韓系雜誌", "古裝劇", "國風工筆",
            "日系動漫", "厚塗插畫", "水彩", "油畫質感", "黑白底片", "復古膠片",
            "賽博龐克", "蒸氣龐克", "奇幻史詩", "時尚雜誌", "紀實街拍", "夢幻柔焦",
            "極簡棚拍", "電影海報", "黑色電影", "唯美沙龍", "個人寫真", "雜誌跨頁",
        ),
    ),
    BuilderField(
        "主體對象",
        listOf(
            "一位女子", "一位少女", "一位輕熟女", "一位女學生", "一位 OL", "一位女神",
            "一位孕婦", "一位青年男子", "一位女性", "一位男性", "一對情侶", "一群好友",
            "一名孩童", "一位長者", "一隻橘貓", "一隻柴犬", "一名女戰士", "一名男劍客",
            "一名機械少女",
        ),
    ),
    BuilderField(
        "種族風格",
        listOf(
            "(不指定)", "東亞臉孔", "日系", "韓系", "華人古典", "東南亞", "歐美", "北歐",
            "拉丁", "中東", "混血",
        ),
    ),
    BuilderField(
        "年齡感",
        listOf(
            "(不指定)", "18-20 歲", "20 歲出頭", "25 歲上下", "接近 30 歲", "30 代初",
            "35 歲輕熟", "40 代", "45 歲成熟", "50 代優雅", "60 歲以上",
        ),
    ),
    BuilderField(
        "氣質類型",
        listOf(
            "(不指定)", "清純", "知性", "甜美", "冷豔", "英氣", "御姐", "古典", "文青",
            "活潑", "神秘", "高冷", "溫柔", "中性帥氣", "華麗", "鄰家", "個性", "颯爽",
            "慵懶", "清冷", "嫵媚", "純真", "幹練", "仙氣", "嬌憨", "嫻靜", "成熟韻味",
            "傲嬌", "陽光開朗", "極簡冷淡風",
        ),
    ),
    BuilderField(
        "職業",
        listOf(
            "(不指定)", "學生", "上班族", "醫師", "護理師", "教師", "咖啡師", "攝影師",
            "設計師", "舞者", "音樂家", "畫家", "作家", "運動員", "廚師", "空服員",
            "軍人", "警察", "律師", "科學家", "花藝師", "旅人", "模特兒", "主播",
            "記者", "瑜伽教練", "芭蕾舞者", "鋼琴家", "偵探", "太空人", "巫女", "女僕",
            "女祭司", "魔法師", "女騎士", "公主", "女王", "甜點師",
        ),
    ),
    BuilderField(
        "身形",
        listOf(
            "(不指定)", "高挑修長", "嬌小玲瓏", "標準勻稱", "健美結實", "纖細", "運動型",
            "豐腴", "微胖", "骨感", "沙漏曲線", "梨形身材", "蘋果形身材",
            "孕婦初期", "孕婦中期", "孕婦晚期孕肚明顯", "產後媽媽",
        ),
    ),
    BuilderField(
        "臉型五官",
        listOf(
            "(不指定)", "鵝蛋臉", "圓臉", "瓜子臉", "方臉", "長臉", "高顴骨", "立體五官",
            "柔和五官", "大眼", "丹鳳眼", "桃花眼", "雙眼皮", "高鼻樑", "櫻桃小嘴", "厚唇",
            "酒窩", "臥蠶", "雀斑", "美人痣", "濃眉",
        ),
    ),
    BuilderField(
        "膚色",
        listOf(
            "(不指定)", "白皙", "自然膚色", "透亮", "冷白皮", "暖黃皮", "小麥色", "古銅色",
        ),
    ),
    BuilderField(
        "髮型髮色",
        listOf(
            "(不指定)", "黑長直髮", "黑長捲髮", "及腰長髮", "棕色波浪捲", "俐落短髮",
            "高馬尾", "雙馬尾", "雙麻花辮", "公主切", "齊瀏海", "空氣瀏海", "妹妹頭",
            "中分長髮", "大波浪", "羊毛捲", "銀白髮", "亞麻金髮", "酒紅挑染", "粉色挑染",
            "漸層藍髮", "丸子頭", "編髮盤髮", "微卷鮑伯", "狼尾剪", "濕髮後梳", "蓬鬆捲髮",
        ),
    ),
    BuilderField(
        "妝容",
        listOf(
            "(不指定)", "裸妝", "氧氣裸妝", "清透淡妝", "韓系水光妝", "紅唇妝", "桃花妝",
            "煙燻眼妝", "歐美立體妝", "復古妝", "日系蜜桃妝", "古典花鈿妝", "新娘妝",
            "漸層唇妝", "舞台濃妝", "曬傷腮紅妝",
        ),
    ),
    BuilderField(
        "配件",
        listOf(
            "(不指定)", "細框眼鏡", "黑框眼鏡", "墨鏡", "珍珠耳環", "細項鍊", "細頸鍊",
            "寬簷帽", "貝雷帽", "緞帶髮飾", "髮夾", "蝴蝶結", "絲巾", "皮手套", "手環",
            "戒指", "耳機", "團扇", "口罩", "花冠", "草帽",
        ),
    ),
    BuilderField(
        "服飾",
        listOf(
            "月白絲質長裙", "靛藍棉麻長衫", "改良式漢服", "古風襦裙", "白色襯衫",
            "米色針織毛衣", "黑色皮衣", "卡其風衣", "牛仔外套", "紅色禮服", "學院制服",
            "運動套裝", "金屬感盔甲", "復古西裝", "旗袍", "和服", "波希米亞長裙",
            "醫師白袍", "太空裝", "連身洋裝", "公主澎裙", "針織連衣裙", "吊帶裙",
            "JK 制服", "露肩晚禮服", "毛呢大衣", "羽絨外套", "浴衣", "護士服", "女僕裝",
            "民族風服飾", "孕婦裝", "婚紗",
        ),
    ),
    BuilderField(
        "姿勢",
        listOf(
            "(不指定)", "自然站姿", "慵懶坐姿", "托腮沉思", "雙手插口袋", "單手叉腰",
            "撥動髮絲", "回頭凝望", "倚牆而立", "盤腿而坐", "雙手抱膝", "伸懶腰",
            "比 V 手勢", "雙手比心", "雙手托臉", "手扶帽簷", "撐傘回眸", "捧花",
            "回眸轉身", "輕撫孕肚",
        ),
    ),
    BuilderField(
        "情緒狀態",
        listOf(
            "含笑垂眸", "溫柔凝視鏡頭", "緊抿嘴唇眉心微蹙", "眼神決絕", "燦爛大笑",
            "落寞出神", "驚訝張口", "沉靜閉眼", "自信淺笑", "回眸一笑", "若有所思",
            "專注凝神", "嫣然一笑", "噘嘴", "挑眉", "害羞低頭", "冷笑", "淚眼婆娑",
        ),
    ),
    BuilderField(
        "場景地點",
        listOf(
            "古典庭院", "堆滿書卷的書房", "雨後石板街道", "灑入陽光的落地窗客廳",
            "午後咖啡廳", "霧氣繚繞的森林", "海邊礁岩", "櫻花樹下", "都市霓虹街頭",
            "圖書館長廊", "雪地森林", "花田", "廢墟教堂", "屋頂天台", "地鐵月台",
            "沙漠", "山頂雲海", "攝影棚純色背景", "教室", "神社", "遊樂園", "火車車廂",
            "楓葉林", "星空草原",
        ),
    ),
    BuilderField(
        "光線時辰",
        listOf(
            "黃昏暖橘光", "清晨薄霧光", "正午強光", "月夜冷藍光", "陰雨天散射光",
            "室內暖黃燈光", "霓虹夜光", "逆光剪影", "窗邊側光", "燭光", "棚燈柔光",
            "金色魔幻時刻",
        ),
    ),
    BuilderField(
        "構圖鏡頭",
        listOf(
            "臉部特寫", "胸上中景", "半身像", "七分身", "膝上景", "全身遠景", "過肩鏡頭",
            "大特寫眼神", "特寫手部", "廣角環境人像", "俯拍全身", "對稱構圖", "三分法構圖",
        ),
    ),
    BuilderField(
        "視角",
        listOf(
            "平視", "低角度仰拍", "高角度俯視", "微側 45 度", "背影回眸", "鳥瞰",
            "第一人稱視角", "廣角畸變",
        ),
    ),
    BuilderField(
        "色調",
        listOf(
            "暖橘色調", "冷藍色調", "高對比", "柔和粉彩", "復古褪色", "黑白",
            "莫蘭迪低飽和", "青橙電影調", "暖金色調", "冷峻銀藍", "蒂芬妮藍", "奶油色調",
            "賽博霓虹",
        ),
    ),
    BuilderField(
        "動作",
        listOf(
            "(不指定)", "緩緩轉頭看向鏡頭", "微風吹動髮絲", "回眸一笑", "緩步走向鏡頭",
            "伸手撥開髮絲", "裙襬隨風飛揚", "輕輕眨眼", "轉身離開", "抬頭仰望", "低頭淺笑",
            "揮手致意", "奔跑跳躍", "慢動作回頭", "鏡頭緩緩推近", "鏡頭環繞主體", "衣袂飄動",
        ),
    ),
    BuilderField(
        "聲音",
        listOf(
            "(不指定)", "輕柔鋼琴背景樂", "舒緩弦樂", "海浪聲", "雨聲淅瀝", "微風與鳥鳴",
            "城市環境音", "篝火劈啪聲", "腳步聲回響", "人聲輕語", "熱鬧人潮聲", "寂靜無聲",
        ),
    ),
    BuilderField(
        "字幕",
        listOf(
            "(不指定)", "無字幕", "繁體中文字幕置中", "繁體中文雙語字幕", "中文電影感字幕",
            "中文開場標題大字", "中文手寫風字幕", "純淨無文字",
        ),
    ),
)

// 把選好的欄位組成一段乾淨、無方括號、可直接生成的完整 prompt。
// 值為空字串或「(不指定)」一律略過。
fun assembleBuilderPrompt(sel: Map<String, String>): String {
    fun v(k: String): String {
        val s = sel[k].orEmpty().trim()
        return if (s.isEmpty() || s == "(不指定)") "" else s
    }
    val person = buildString {
        append(v("主體對象").ifEmpty { "一位人物" })
        val mods = listOf(
            v("種族風格"),
            v("年齡感"),
            v("氣質類型").let { if (it.isNotEmpty()) "${it}氣質" else "" },
            v("職業"),
            v("身形"),
            v("臉型五官"),
            v("膚色"),
            v("髮型髮色"),
            v("妝容"),
        ).filter { it.isNotEmpty() }
        if (mods.isNotEmpty()) append("，").append(mods.joinToString("，"))
        if (v("配件").isNotEmpty()) append("，配戴").append(v("配件"))
        if (v("服飾").isNotEmpty()) append("，穿").append(v("服飾"))
        if (v("姿勢").isNotEmpty()) append("，").append(v("姿勢"))
        if (v("情緒狀態").isNotEmpty()) append("，").append(v("情緒狀態"))
    }
    val scene = listOf(v("場景地點"), v("光線時辰")).filter { it.isNotEmpty() }.joinToString("，")
    val comp = listOf(v("構圖鏡頭"), v("視角")).filter { it.isNotEmpty() }.joinToString("，")
    val style = listOf(v("風格類型"), v("色調"), "淺景深").filter { it.isNotEmpty() }.joinToString("，")
    val motion = v("動作")
    val sound = v("聲音")
    val caption = v("字幕")
    return buildString {
        if (person.isNotEmpty()) append("主體：").append(person).append("。\n")
        if (scene.isNotEmpty()) append("場景：").append(scene).append("。\n")
        if (comp.isNotEmpty()) append("構圖：").append(comp).append("。\n")
        if (motion.isNotEmpty()) append("動態：").append(motion).append("。\n")
        if (sound.isNotEmpty()) append("聲音：").append(sound).append("。\n")
        if (caption.isNotEmpty()) append("字幕：").append(caption).append("。\n")
        if (style.isNotEmpty()) append("風格：").append(style).append("。")
    }.trim()
}

// 底部彈出範本面板。tab 切換「現成範例 / 自己組」,點「使用/填入」→ onUse(prompt) 並關閉。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplateSheet(
    onDismiss: () -> Unit,
    onUse: (String) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("ready") }

    // 條件選擇器狀態: 每欄預設選第一個選項 (modifier 欄第一個是「(不指定)」)
    val selected = remember {
        mutableStateMapOf<String, String>().apply {
            BUILDER_FIELDS.forEach { put(it.label, it.options.first()) }
        }
    }
    // 目前展開搜尋的欄位 (一次只展開一個,保持面板清爽)
    var expandedField by remember { mutableStateOf<String?>(null) }

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
                // ── ② 自己組 (可搜尋下拉) ──
                RandomBar(label = "🎲  全部隨機") {
                    BUILDER_FIELDS.forEach { selected[it.label] = it.options.random() }
                }

                BUILDER_FIELDS.forEach { field ->
                    SearchableField(
                        label = field.label,
                        value = selected[field.label].orEmpty(),
                        options = field.options,
                        expanded = expandedField == field.label,
                        onToggle = {
                            expandedField = if (expandedField == field.label) null else field.label
                        },
                        onPick = {
                            selected[field.label] = it
                            expandedField = null
                        },
                        onRandom = { selected[field.label] = field.options.random() },
                    )
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

// 單一條件的「可搜尋下拉」: 收合時一行(欄名+目前值+🎲+箭頭);展開時頂部搜尋框 +
// 過濾後的選項清單。清單 inline 渲染(不嵌 verticalScroll,避免無限高 crash),
// 只取前 12 筆並提示其餘,引導打字縮小範圍。
@Composable
private fun SearchableField(
    label: String,
    value: String,
    options: List<String>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (String) -> Unit,
    onRandom: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    // 收合時清掉搜尋字,下次展開乾淨
    LaunchedEffect(expanded) { if (!expanded) query = "" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.W500,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRandom)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(text = "🎲", fontSize = 16.sp)
            }
            ImagineIcon(
                name = if (expanded) "expand_less" else "expand_more",
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text(
                        "搜尋…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            val q = query.trim()
            val filtered = if (q.isEmpty()) options else options.filter { it.contains(q, ignoreCase = true) }
            // v1.0.64: 拿掉 12 筆上限,改全部 inline 渲染 → 靠外層 sheet 捲動就能看到所有選項
            // (修「選單無法下拉看到最後選項」)。搜尋框仍可打字縮小範圍。
            if (filtered.isEmpty()) {
                Text(
                    text = "找不到符合的選項，換個關鍵字",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                )
            } else {
                filtered.forEach { opt ->
                    val isSelected = opt == value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(opt) }
                            .padding(vertical = 11.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = opt,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.W700 else FontWeight.W500,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            ImagineIcon(
                                name = "check",
                                size = 18.dp,
                                fill = 1,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
