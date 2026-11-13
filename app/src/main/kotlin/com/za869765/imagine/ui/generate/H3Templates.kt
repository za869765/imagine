package com.za869765.imagine.ui.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
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
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.TextActionButton
import kotlinx.coroutines.launch

/**
 * v3.2.7 H3 生影頁的範本（取代這一頁原本的「套用範本」）。
 *
 * 每張範本是一整段英文提示詞，句子來自 2026-11-13 在筆電上逐支對照、使用者看過成品後定下來的寫法：
 *  - 膚色：白皙通透、霧面不油亮；高調的明亮環境（膚色白主要靠場景光線，不是靠形容詞）
 *  - 胸部：小而自然，乳暈乳頭小而淡粉（搭配「白皙」開關的胸部 LoRA）
 *  - 鏡頭：全身、固定、不拉近特寫
 *  - 臉：五官照第 1 張參考圖、素顏、頭髮照參考圖披著（改髮型或寫成「古裝劇女主角」臉會跑掉）
 *  - 台詞寫在動作進行中，不等動作做完才站著講
 *  - 人物全程面向鏡頭，不轉身離開再轉回來、不做又快又大的動作（後段臉會變、畫面會糊）
 * 這些提示詞要「原樣」送給模型才重現得出來，所以套用範本時生影頁會自動切到範本模式（不包裝、不翻譯、不加眨眼包）。
 * 參考圖：第 1 張＝臉，第 2 張＝頸部以下的身材；只有 1 張或沒有圖時句子會自動調整。
 */
internal data class H3Template(val group: String, val title: String, val desc: String, val build: (refCount: Int) -> String)

private const val WHO = "an adult woman with fair, translucent light-ivory skin with a soft peach-pink tint"

private fun host(refs: Int): String = when {
    refs >= 2 -> "<Picture 1> is the host, $WHO. <Picture 2> shows her body from the neck down; keep exactly this body shape and proportions. "
    refs == 1 -> "<Picture 1> is the host, $WHO; keep exactly her face, body shape and proportions. "
    else -> "The host is $WHO, in her twenties, with long black hair and a slim figure with long legs; keep exactly this body shape and proportions. "
}

private fun look(refs: Int): String =
    "Her bare tits are small and natural, with tiny pale pink nipples and tiny pale pink areolae. " +
        (if (refs >= 1) "Her face is exactly the face in <Picture 1> for the whole video: the same eyes and eyelids, the same nose, lips and jawline, a natural bare face. " else "") +
        "Her fair skin is bright, clear and evenly toned with a faint healthy rosy undertone and a clean, soft, velvety matte finish, like freshly powdered skin. " +
        "Her body is lean and toned, with clearly defined collarbones, a subtle abdominal line and toned arms and legs, and fine natural skin texture. " +
        "She has small natural breasts in proportion to her slim figure. Her areolae are tiny and barely visible, almost exactly the same pale colour as her fair skin, and her nipples are tiny, soft and pale pink. " +
        "The camera stays wide and static for the whole video and always shows her entire body from head to feet; there is never a close-up of any body part. "

/** 句首的主詞：有參考圖就明講是同一個人、頭髮照參考圖 */
private fun she(refs: Int): String =
    if (refs >= 1) "The exact same woman as <Picture 1>, identical face and hairstyle, her long black hair worn loose exactly as in <Picture 1>,"
    else "The host, her long black hair worn loose,"

private const val FACING = "Her face and body stay turned toward the camera for the whole video; she never turns away or bends down, and her movements are smooth and unhurried, with no sudden or fast motion. "
private const val TALK = " She keeps moving the whole time and speaks while the action is still going on; nobody stops or stands still to talk."
private const val END = " Natural lip movement, realistic fair, translucent skin with fine visible pores, no beauty filter, no airbrushing, bare face with no makeup."
private const val STAY_FAIR = " Her skin stays fair, light ivory with a soft pink tint for the whole video, never tanned, never yellow or orange."
private const val NUDE = "fully nude facing the camera, full frontal, relaxed and natural, at ease with her body"
private const val FRAME = "her whole body from head to feet is in frame"

private const val CAM = "Raw candid documentary-style video shot on a full-frame camera"
private const val LIGHT_ROOM = "in bright, soft, diffused daylight pouring in from large windows with sheer white curtains behind her, high-key and airy, low contrast, slightly overexposed, with bright cream-white surroundings, full-body front view, static camera"
private const val LIGHT_INN = "in bright, soft, diffused daylight pouring in through large paper lattice windows behind her, high-key and airy, low contrast, slightly overexposed, with pale wood and white plaster surroundings, full-body front view, static camera"
private const val LIGHT_HALL = "in bright, soft, diffused daylight coming through open wooden doors and paper windows behind her, a snow-covered courtyard outside, high-key and airy, low contrast, slightly overexposed, with pale wood and white surroundings, full-body front view, static camera"
private const val LIGHT_LAKE = "in bright, soft, overcast daylight with white mist drifting over a lake behind her, high-key and airy, low contrast, slightly overexposed, with pale stone and white mist surroundings, full-body front view, static camera"

private const val STUDIO = "a softly lit photo studio with a plain light grey backdrop, a clothing rack right beside her and a few red and gold Double 11 shopping festival gift boxes and shopping bags on the floor (no readable text)"
private const val BACKSTAGE = "the backstage fitting area of a fashion showroom decorated for a Double 11 shopping festival sale with red and gold ribbons and gift boxes (no readable text), a clothing rack in the background"
private const val INN = "a quiet upstairs room of an ancient Chinese inn with a wooden floor and a painted folding screen right beside her"
private const val HALL = "the dressing hall of a mountain sword sect with wooden pillars and a rack of swords in the background"
private const val LAKE = "an open pavilion of pale stone and light wood beside a misty lake, a wooden clothes stand right beside her within reach"

private const val ASSISTANTS = "Two adult women in their thirties, the assistants, wearing black shirts and black trousers, stand on either side of her. "
private const val MAIDS = "Two adult women in their thirties, the maids, wearing plain grey-blue hanfu, stand on either side of her. "
private const val SOUND_ROOM = " Rustle of fabric, quiet room ambience, no music."

private fun prompt(light: String, place: String, refs: Int, body: String, sound: String): String =
    "$CAM $light, $place. " + host(refs) + look(refs) + body + TALK + END + sound + STAY_FAIR

internal val H3_TEMPLATES: List<H3Template> = listOf(
    H3Template("雙 11 直播", "自己穿上洋裝", "攝影棚。一開始全裸正面 → 從衣架拿香檳色緞面洋裝套上、撫平 → 邊整理邊說台詞。") { r ->
        prompt(LIGHT_ROOM, STUDIO, r,
            "${she(r)} stands $NUDE, $FRAME. $FACING" +
                "She takes a champagne-colored satin evening dress from the clothing rack beside her, slips it on over her head and smooths it down; " +
                "while she is still smoothing the dress and putting one hand on her hip, she smiles at the camera and says in Mandarin Chinese: \"雙十一購物節，洋裝特價中！\"",
            SOUND_ROOM)
    },
    H3Template("雙 11 直播", "自己脫下禮服展示", "攝影棚。穿紅色晚禮服 → 拉開拉鍊脫下、全裸正面 → 把禮服舉在身旁展示，邊舉邊說台詞。") { r ->
        prompt(LIGHT_ROOM, STUDIO, r,
            "${she(r)} wearing an elegant red evening gown, stands facing the camera, $FRAME. $FACING" +
                "She unzips the gown at the side, slips the straps off her shoulders and lets it slide down, then steps out of it, now $NUDE; " +
                "while she lifts the red gown up beside her to show it, she smiles at the camera and says in Mandarin Chinese: \"雙十一購物節，禮服特價中！\"",
            SOUND_ROOM)
    },
    H3Template("雙 11 直播", "穿→脫→穿（自己換裝）", "攝影棚。穿黑色細肩帶洋裝 → 脫下全裸 → 從衣架拿白色洋裝穿上，邊穿邊說台詞。") { r ->
        prompt(LIGHT_ROOM, STUDIO, r,
            "${she(r)} wearing a loose black slip dress, stands facing the camera, $FRAME. $FACING" +
                "In one continuous unhurried sequence, she first slips the straps off her shoulders and lets the black dress slide down to the floor, and stands $NUDE; " +
                "then she takes a white satin dress from the clothing rack beside her, slips it on over her head and smooths it down. " +
                "While she is still putting on the white dress and smoothing it, she smiles at the camera and says in Mandarin Chinese: \"下一件是這款洋裝，雙十一特價！\"",
            SOUND_ROOM)
    },
    H3Template("雙 11 直播", "兩位助手幫穿禮服", "後台試衣區。全裸站中間 → 兩位助手撐開白色蕾絲禮服幫她穿上、拉拉鍊、整理裙擺 → 助手還在整理時說台詞。") { r ->
        prompt(LIGHT_ROOM, BACKSTAGE, r,
            "${she(r)} stands in the center $NUDE, $FRAME. $ASSISTANTS" +
                "Together they hold open a white lace evening gown, help her step into it and pull it up over her shoulders; one assistant zips it at the back while the other smooths the skirt. " +
                "While the assistants are still zipping and smoothing the gown, the host looks at the camera, smiles and says in Mandarin Chinese: \"雙十一購物節，這件禮服特價中！\" " +
                "Unhurried, natural movements like real backstage dressers, relaxed friendly mood.",
            SOUND_ROOM)
    },
    H3Template("雙 11 直播", "兩位助手幫脫再幫穿", "後台試衣區。穿綠色緞面禮服 → 助手幫脫下、全裸正面 → 助手幫穿上粉色洋裝，穿的時候說台詞。") { r ->
        prompt(LIGHT_ROOM, BACKSTAGE, r,
            "${she(r)} wearing an emerald green satin evening gown, stands in the center facing the camera, $FRAME. $ASSISTANTS" +
                "In one continuous unhurried sequence, one assistant unzips the gown at the back and both help her slip out of it and take it away, leaving her $NUDE; " +
                "then together they hold open a pink summer dress, help her put her arms through it and fasten it. " +
                "While the assistants are still dressing her, the host looks at the camera, smiles and says in Mandarin Chinese: \"下一件是這款洋裝，雙十一特價！\" " +
                "Unhurried, natural movements like real backstage dressers, relaxed friendly mood.",
            SOUND_ROOM)
    },
    H3Template("古裝武俠", "客棧：穿上白袍、拿劍", "古代客棧房間。全裸正面 → 從屏風拿白色寬袖長袍穿上、繫淺藍腰帶，邊繫邊說台詞 → 拿起靠在身旁的長劍。") { r ->
        prompt(LIGHT_INN, INN, r,
            "${she(r)} stands $NUDE, $FRAME. $FACING" +
                "She takes a flowing white hanfu robe with wide sleeves from the folding screen, slips her arms into it, closes it across her chest and ties a pale blue sash around her waist. " +
                "While she is still tying the sash, she looks at the camera with a calm confident smile and says in Mandarin Chinese: \"江湖路遠，我們後會有期！\" " +
                "As she finishes the line she takes a sheathed straight sword that leans against the screen beside her and holds it at her side.",
            " Rustle of silk, wind in the bamboo outside, no music.")
    },
    H3Template("古裝武俠", "客棧：穿→脫→穿", "古代客棧房間。穿黑袍 → 解紅腰帶讓黑袍滑落、全裸正面 → 穿上白袍繫腰帶，邊穿邊說台詞。") { r ->
        prompt(LIGHT_INN, INN, r,
            "${she(r)} wearing a loose black hanfu robe tied with a red sash, stands facing the camera, $FRAME. " +
                "In one continuous unhurried sequence, she first unties the red sash and lets the black robe slide off her shoulders to the floor, and stands $NUDE; " +
                "then she takes a flowing white hanfu robe with wide sleeves from the folding screen, slips her arms into it, closes it across her chest and ties a pale blue sash around her waist. " +
                "While she is still putting on the white robe and tying the sash, she looks at the camera with a warm natural smile and says in Mandarin Chinese: \"江湖路遠，我們後會有期！\"",
            " Rustle of silk, wind in the bamboo outside, no music.")
    },
    H3Template("古裝武俠", "門派大廳：侍女幫穿紅袍披風", "雪景大廳。全裸站中間 → 兩位侍女幫穿紅色繡金鳳長袍、繫腰帶、披白色毛邊披風，這時說台詞 → 接過長劍。") { r ->
        prompt(LIGHT_HALL, HALL, r,
            "${she(r)} stands in the center $NUDE, $FRAME. $MAIDS" +
                "Together they hold open a crimson hanfu robe embroidered with gold phoenix patterns, help her put her arms through the wide sleeves and close it in front; " +
                "one maid ties a black sash around her waist while the other drapes a white fur-trimmed cloak over her shoulders. " +
                "While the maids are still tying the sash and arranging the cloak, the host looks at the camera with a confident smile and says in Mandarin Chinese: \"今日一戰，我必全力以赴！\" " +
                "As she finishes the line a maid hands her a sheathed straight sword and she takes it. Natural movements, calm respectful mood.",
            " Rustle of silk, quiet winter air, no music.")
    },
    H3Template("古裝武俠", "門派大廳：侍女幫脫再幫穿", "雪景大廳。穿深藍旅行袍 → 侍女解腰帶幫脫下、全裸正面 → 侍女幫穿紅色繡金鳳長袍，穿的時候說台詞。") { r ->
        prompt(LIGHT_HALL, HALL, r,
            "${she(r)} wearing a dark blue travelling hanfu robe, stands in the center facing the camera, $FRAME. $MAIDS" +
                "In one continuous unhurried sequence, the maids first untie her sash and lift the dark blue robe off her shoulders and away, leaving her $NUDE; " +
                "then together they hold open a crimson hanfu robe embroidered with gold phoenix patterns, help her put her arms through the wide sleeves, close it in front and tie a black sash around her waist. " +
                "While the maids are still dressing her and tying the sash, the host looks at the camera with a confident smile and says in Mandarin Chinese: \"今日一戰，我必全力以赴！\" Natural movements, calm respectful mood.",
            " Rustle of silk, quiet winter air, no music.")
    },
    H3Template("古裝武俠", "湖邊亭：脫下勁裝、展示絲袍", "起霧的湖邊亭子。穿黑色勁裝 → 解腰帶脫下、全裸正面（裸體時間最長）→ 伸手拿起掛在身旁的淡綠絲袍舉著展示，邊拿邊說台詞。") { r ->
        prompt(LIGHT_LAKE, "$LAKE with a pale green silk hanfu robe hanging on it", r,
            "${she(r)} wearing a fitted black swordswoman outfit with a wide belt, stands facing the camera, $FRAME. $FACING" +
                "She unties the belt, slips the black outfit off her shoulders and lets it fall to the floor, now $NUDE; " +
                "still facing the camera, she reaches one hand to the side, smoothly lifts the pale green silk robe off the stand by its collar and holds it up beside her to show it, " +
                "and while she is lifting it she smiles at the camera and says in Mandarin Chinese: \"換上這身新衣，再闖江湖！\"",
            " Rustle of fabric, soft lapping water, a distant bird, no music.")
    },
)

/** 生影頁「套用範本」的面板：選一張 → 整段填進輸入框（之後可以直接改引號裡的台詞或任何字）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun H3TemplateSheet(refCount: Int, onApply: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val groups = remember { H3_TEMPLATES.map { it.group }.distinct() }
    var group by remember { mutableStateOf(groups.first()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("套用範本", fontSize = 16.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "每張範本是一整段測試過的英文提示詞，套用後可以直接改引號裡的台詞。" +
                    "建議：筆電、白皙開著、8 秒、單段。" +
                    when {
                        refCount >= 2 -> "目前選了 $refCount 張參考圖：第 1 張當臉，第 2 張當頸部以下的身材。"
                        refCount == 1 -> "目前只選 1 張參考圖：臉和身材都照這張；再加一張頸部以下的身材圖，體型會更穩。"
                        else -> "目前沒有參考圖：人物長相由模型決定；選 1 張臉、1 張身材圖，才會是你要的人。"
                    },
                fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { g ->
                    val on = g == group
                    Text(
                        g,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.W700 else FontWeight.W500,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { group = g }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            H3_TEMPLATES.filter { it.group == group }.forEach { t ->
                ImagineCard(pad = 14) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.W600, color = MaterialTheme.colorScheme.onSurface)
                            Text(t.desc, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextActionButton(label = "使用", onClick = {
                            onApply(t.build(refCount))
                            scope.launch { sheetState.hide(); onDismiss() }
                        })
                    }
                }
            }
        }
    }
}
