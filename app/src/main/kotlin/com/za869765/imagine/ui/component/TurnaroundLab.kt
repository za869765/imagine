package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ui.util.Clipboard

// 三視圖工坊 — 參數化三視圖 prompt 生成器（仿 aizhuiguang 角色三視圖工坊的互動結構，
// 內容全原創、僅女角，輸出沿用本 app 三視圖包既定版式：正面/¾側/背面、photorealistic 真人寫實）。
// 資料字典 + 拼裝引擎 + Tab UI 全在此檔，不動 PromptTemplate.kt 的既有私有元件。

// ── 畫幅 ──
data class TurnRatio(val label: String, val en: String)

val TURN_RATIOS: List<TurnRatio> = listOf(
    TurnRatio("16:9 橫幅", "16:9 widescreen"),
    TurnRatio("21:9 寬銀幕", "21:9 cinematic widescreen"),
    TurnRatio("3:4 直幅", "3:4 portrait"),
    TurnRatio("9:16 直幅", "9:16 vertical portrait"),
    TurnRatio("1:1 方形", "1:1 square"),
)

// ── 人種／族裔 ──（[顯示, 中文描述, 英文描述]；「隨機」在生成時擲）
data class TurnEth(val label: String, val zh: String, val en: String)

val TURN_ETHS: List<TurnEth> = listOf(
    TurnEth("隨機", "", ""),
    TurnEth("東亞", "東亞面孔", "East Asian features"),
    TurnEth("日系", "日系面孔", "Japanese features"),
    TurnEth("韓系", "韓系面孔", "Korean features"),
    TurnEth("華人古典", "華人古典面孔", "classical Chinese features"),
    TurnEth("東南亞", "東南亞面孔", "Southeast Asian features"),
    TurnEth("南亞", "南亞面孔", "South Asian features"),
    TurnEth("中東", "中東面孔", "Middle Eastern features"),
    TurnEth("非洲", "非洲面孔", "African features"),
    TurnEth("歐美", "歐美面孔", "European features"),
    TurnEth("北歐", "北歐面孔", "Nordic Scandinavian features"),
    TurnEth("斯拉夫", "斯拉夫面孔", "Slavic features"),
    TurnEth("拉丁", "拉丁面孔", "Latina features"),
    TurnEth("混血", "混血面孔", "mixed-race features"),
)

// ── 自助細節 ──（每欄首項「隨機」；髮型不設欄位——角色原型自帶髮型避免互相打架）
data class TurnDetailField(val label: String, val options: List<String>)

val TURN_DETAILS: List<TurnDetailField> = listOf(
    TurnDetailField("年齡", listOf("隨機", "十六七歲少女", "二十歲出頭", "二十五歲上下", "三十歲輕熟", "四十歲成熟")),
    TurnDetailField("體型", listOf("隨機", "纖細", "嬌小玲瓏", "標準勻稱", "高挑修長", "健美結實", "豐腴")),
    TurnDetailField("氣質", listOf("隨機", "清冷", "甜美", "英氣", "冷豔", "溫柔", "神祕", "颯爽", "嫵媚", "傲嬌", "知性")),
    TurnDetailField("光線氛圍", listOf("隨機", "棚燈柔光", "霓虹夜色光", "冷藍側光", "暖金逆光", "硬光高反差", "晨霧柔光", "燭橙暖光")),
)

private val TURN_LIGHT_EN = mapOf(
    "棚燈柔光" to "soft studio lighting",
    "霓虹夜色光" to "neon night lighting",
    "冷藍側光" to "cold blue side lighting",
    "暖金逆光" to "warm golden backlight",
    "硬光高反差" to "hard dramatic lighting",
    "晨霧柔光" to "soft misty morning light",
    "燭橙暖光" to "warm candlelight glow",
)

// ── 角色原型庫 ──（全女角；zh 進中文主文、en 進英文結構詞）
data class TurnRole(val emoji: String, val name: String, val zh: String, val en: String)

data class TurnCat(val emoji: String, val name: String, val roles: List<TurnRole>)

val TURN_CATS: List<TurnCat> = listOf(
    TurnCat("🏙️", "現代都市", listOf(
        TurnRole("👩‍💼", "都市菁英 OL", "剪裁俐落的煙灰色西裝套裙、絲質襯衫、細高跟鞋、腕錶，低盤髮，幹練沉穩", "sharp smoke-gray tailored suit dress, silk blouse, stiletto heels, wristwatch, neat low bun, composed professional"),
        TurnRole("🧢", "街頭潮妹", "oversize 連帽衛衣配工裝短裙、老爹鞋、金屬耳環、漁夫帽下露出挑染髮絲，隨性不羈", "oversized hoodie with cargo skirt, chunky sneakers, metal earrings, bucket hat over streaked hair, effortlessly cool streetwear"),
        TurnRole("💪", "健身教練", "高支撐運動背心與緊身運動褲、勻稱結實的肌肉線條、運動手環、高馬尾，陽光自信", "athletic sports bra top and leggings, toned fit physique, fitness band, high ponytail, sunny confident"),
        TurnRole("🎸", "龐克主唱", "鉚釘皮衣與破洞網襪、煙燻眼妝、桃紅挑染凌亂短髮、背電吉他，頹廢桀驁", "studded leather jacket with ripped fishnets, smoky eyeliner, messy pink-streaked short hair, electric guitar, edgy rebellious rocker"),
        TurnRole("💃", "晚宴名媛", "緞面高開衩晚禮服、珍珠項鍊與耳墜、優雅盤髮、紅唇長手套，冷豔華貴", "satin high-slit evening gown, pearl necklace and earrings, elegant updo, red lips and long gloves, glamorous aloof"),
        TurnRole("🕵️", "便衣女刑警", "深色風衣配牛仔褲、肩掛槍套與警徽掛繩、俐落中馬尾，警覺銳利", "dark trench coat with jeans, shoulder holster and badge lanyard, neat mid ponytail, sharp vigilant detective"),
        TurnRole("🎮", "電競少女", "戰隊外套與百褶短裙、頸掛電競耳機、雙馬尾配藍色挑染、指尖貼膠布，元氣專注", "esports team jacket with pleated skirt, gaming headset around neck, twin tails with blue streaks, taped fingertips, energetic focused gamer"),
    )),
    TurnCat("⚔️", "古風武俠", listOf(
        TurnRole("🗡️", "江湖女俠", "素色勁裝束袖束腰、背負長劍、高馬尾繫紅繩、腕纏繃帶，颯爽孤傲", "plain martial arts outfit with bound sleeves, long sword on back, high ponytail with red cord, wrapped wrists, dashing lone swordswoman"),
        TurnRole("🌹", "紅衣劍客", "飄逸正紅勁裝長裙、軟劍纏腰、金步搖點綴高髻，明豔英氣", "flowing crimson martial robes, soft sword coiled at waist, golden hairpin in high bun, vivid heroic elegance"),
        TurnRole("👑", "宮廷貴妃", "織金曳地宮裝廣袖、點翠鳳釵珠翠滿頭、長指甲護甲套，雍容藏鋒", "gold-woven trailing palace gown with wide sleeves, kingfisher phoenix hairpins, ornate nail guards, regal and subtly cunning"),
        TurnRole("📜", "白衣醫女", "素白交領襦裙、肩背藥箱與銀針袋、低髻木簪，溫婉堅韌", "plain white cross-collar ruqun, medicine chest and silver needle pouch, low bun with wooden hairpin, gentle resilient healer"),
        TurnRole("🌑", "暗夜刺客", "玄黑夜行衣、半掩面紗、袖藏匕首、束緊長辮，冷厲殺氣", "black night-stalker garb, half veil, hidden sleeve dagger, tight long braid, cold lethal assassin"),
        TurnRole("🛡️", "女將軍", "銀白山文鎧甲配大紅披風、腰佩長劍、高束戰髻插翎，威嚴沙場氣", "silver scale armor with crimson cape, long sword at waist, high battle bun with plume, commanding battlefield general"),
        TurnRole("☁️", "世外仙子", "月白紗衣廣袖飄帶、腰懸玉佩、青絲及腰簪白玉蘭，超然出塵", "moon-white gauze robes with flowing ribbons, jade pendant, waist-length dark hair with magnolia hairpin, transcendent ethereal"),
    )),
    TurnCat("🚀", "科幻未來", listOf(
        TurnRole("💻", "賽博駭客", "機能風外套配半透明面罩、皮膚下發光義體紋路、神經接口線垂肩、不對稱短髮，神祕銳利", "techwear jacket with translucent visor, glowing cybernetic traces under skin, neural cables over shoulder, asymmetric short hair, sharp mysterious hacker"),
        TurnRole("🤖", "機甲駕駛員", "貼身駕駛服接能量管線、臂側部隊徽章、俐落短髮、腋下夾頭盔，堅毅冷靜", "skin-tight pilot suit with energy conduits, squad emblem on sleeve, cropped hair, helmet under arm, resolute calm mecha pilot"),
        TurnRole("🧬", "仿生人少女", "瓷白肌膚下隱約機械接縫、銀白齊肩直髮、素色貼身合成衣、過分平靜的眼神", "porcelain skin with faint mechanical seams, silver-white shoulder-length hair, plain fitted synth-suit, unnervingly calm android gaze"),
        TurnRole("🔫", "星際賞金獵人", "拼接護甲配能量手槍、風塵僕僕的傷痕、單側剃髮長辮，野性桀驁", "patchwork armor with energy pistol, weathered scars, side-shaved head with long braid, wild defiant bounty hunter"),
        TurnRole("✨", "全息偶像", "發光全息裙裝、懸浮光環髮飾、漸層粉紫長髮、舞台亮片妝，甜美虛幻", "glowing holographic dress, floating halo hair ornament, gradient pink-purple long hair, stage glitter makeup, sweet ethereal virtual idol"),
        TurnRole("🛸", "星艦艦長", "筆挺未來制服配肩章與徽記、利落盤髮、白手套，沉穩威嚴", "crisp futuristic uniform with epaulettes and insignia, neat updo, white gloves, composed authoritative starship captain"),
        TurnRole("🥋", "新中式機甲武者", "黑白主調貼身機甲綴雲紋甲片、細藍能量線點綴、高束髮配金屬髮飾，清冷克制的東方未來感", "sleek black-and-white form-fitting mecha armor with cloud-pattern plates, thin blue energy lines, high-tied hair with metal ornament, restrained neo-Chinese futurist warrior"),
    )),
    TurnCat("☢️", "末世廢土", listOf(
        TurnRole("🏜️", "廢土遊俠", "風衣式護甲、防毒面具掛頸、改裝步槍背身、護目鏡推上額頭，滄桑獨行", "armored trench coat, gas mask around neck, modified rifle on back, goggles pushed up, weathered lone wasteland ranger"),
        TurnRole("🔧", "拾荒少女", "拼湊裝備掛滿機械小工具、油污手套、亂翹短髮夾扳手髮夾，倔強機靈", "scavenged gear hung with mechanical trinkets, grease-stained gloves, messy short hair with wrench-shaped clip, stubborn clever scavenger"),
        TurnRole("🌪️", "沙暴行者", "防沙纏布與駝色斗篷、護目鏡、腰掛彎刀、纏布長辮，堅韌神祕", "sand-wrapped cloth and tan cloak, goggles, curved blade at hip, cloth-bound braid, enduring mysterious dune walker"),
        TurnRole("🦾", "機械義肢女", "鏽蝕金屬義臂外露管線、無袖工裝、短髮舊疤，冷漠強悍", "rusted prosthetic arm with exposed cabling, sleeveless workwear, short hair with old scar, cold hardened cyborg"),
        TurnRole("🎒", "末日倖存者", "層疊舊衣纏繃帶、沉重背包、掌心厚繭、眼神警覺疲憊", "layered tattered clothing with bandages, heavy backpack, calloused hands, wary exhausted survivor"),
        TurnRole("🐺", "馴獸女獵人", "獸皮披肩配骨飾項鍊、腰纏長鞭、部族紋面、粗獷編髮，兇悍原始", "hide mantle with bone necklace, long whip at waist, tribal face markings, rough braids, fierce primal beast tamer"),
    )),
    TurnCat("🐉", "奇幻種族", listOf(
        TurnRole("🔮", "元素法師", "華麗刺繡法袍、發光法杖、身側漂浮符文、及腰長捲髮，神祕強大", "ornate embroidered mage robes, glowing staff, floating runes, waist-length wavy hair, mysterious powerful elementalist"),
        TurnRole("🏹", "精靈遊俠", "輕甲配綠斗篷、長弓與箭袋、尖耳、銀白長髮編辮，優雅敏銳", "light armor with green cloak, longbow and quiver, pointed ears, braided silver hair, graceful keen elven ranger"),
        TurnRole("🛡️", "聖殿女騎士", "全身板甲配紋章披風、盾牌長劍、金髮束起，肅穆堅定", "full plate armor with heraldic cape, shield and longsword, tied golden hair, solemn steadfast paladin"),
        TurnRole("🌙", "森林女巫", "暗色長裙配尖頂帽、腰掛魔藥瓶、黑貓隨行、深綠長髮，妖異神祕", "dark gown with pointed hat, potion vials at belt, black cat companion, deep green hair, eerie mystical witch"),
        TurnRole("🐲", "龍裔少女", "局部鱗片肌膚、小型犄角、豎瞳、暗紅長髮、皮甲，威壓野性", "partial scaled skin, small horns, slit pupils, dark red hair, leather armor, intimidating feral dragonkin"),
        TurnRole("🍷", "吟遊詩人", "華麗繡花外套、魯特琴、插羽寬簷帽、棕色捲髮，靈動風流", "flamboyant embroidered coat, lute, feathered wide-brim hat, brown curls, charming spirited bard"),
        TurnRole("🧚", "月光祭司", "銀紗祭袍、月牙額飾、白髮及踝、懷抱法典，聖潔空靈", "silver gauze ceremonial robes, crescent forehead ornament, ankle-length white hair, holding a tome, holy ethereal moon priestess"),
    )),
    TurnCat("🎒", "校園青春", listOf(
        TurnRole("📚", "文學少女", "JK 制服配圓框眼鏡、懷抱文庫本、齊瀏海雙麻花辮，安靜書卷氣", "JK school uniform with round glasses, hugging a paperback, straight bangs and twin braids, quiet bookish"),
        TurnRole("🏃", "田徑少女", "運動短袖與跑步短褲、胸前號碼布、高馬尾、釘鞋，活力陽光", "track tee and running shorts, race bib, high ponytail, spikes, energetic sunny sprinter"),
        TurnRole("🎻", "音樂社大小姐", "制服外搭針織背心、手提小提琴盒、公主捲長髮繫緞帶，優雅嬌氣", "uniform with knit vest, violin case in hand, princess-curled hair with ribbon, elegant refined young lady"),
        TurnRole("🥋", "劍道部主將", "劍道服袴裝、竹刀拄地、束髮、護具挾腋，凜然英氣", "kendo gi and hakama, bamboo sword, tied-back hair, armor under arm, dignified valiant captain"),
        TurnRole("🎨", "美術社怪才", "沾滿顏料的圍裙、提畫具箱、丸子頭插著畫筆，古靈精怪", "paint-splattered apron, art supply case, messy bun with brushes stuck in, quirky imaginative artist"),
        TurnRole("👩‍🏫", "保健室大姊姊", "白袍內搭針織衫、聽診器掛頸、溫柔低馬尾，成熟溫柔", "white coat over knit sweater, stethoscope around neck, soft low ponytail, mature gentle school nurse"),
    )),
)

// ── 拼裝 ──（隨機欄位在呼叫當下擲定；同一角色重按=換細節重生）
fun buildTurnaroundPrompt(
    role: TurnRole,
    eth: TurnEth,
    details: Map<String, String>,
    ratio: TurnRatio,
    withEn: Boolean,
): String {
    fun resolve(field: TurnDetailField): String {
        val v = details[field.label] ?: "隨機"
        return if (v == "隨機") field.options.drop(1).random() else v
    }

    val e = if (eth.label == "隨機") TURN_ETHS.drop(1).random() else eth
    val age = resolve(TURN_DETAILS[0])
    val build = resolve(TURN_DETAILS[1])
    val vibe = resolve(TURN_DETAILS[2])
    val light = resolve(TURN_DETAILS[3])

    val zh = "${e.zh}，女性，$age，$build，${role.zh}，${vibe}氣質；" +
        "【角色設定三視圖】同一角色 — 正面全身、四分之三側面全身、背面全身，" +
        "三視等高並排、共用同一條地平線、比例與身高完全一致，中性站姿、雙臂自然放鬆；" +
        "純淨中性灰攝影棚背景，$light，均勻柔和三點打光、三視光源與色溫一致、無強投影；" +
        "photorealistic 照片級真人寫實、真人演員、電影級質感、超精細五官與服裝材質、自然真實膚質，8K；" +
        "正交平視、無透視變形，${ratio.label}。僅此一角色的三個視圖、無第二人、無文字浮水印，端莊不露。"

    if (!withEn) return zh

    val lightEn = TURN_LIGHT_EN[light] ?: "cinematic lighting"
    val en = "${e.en}, ${role.en}, character turnaround model sheet of the same character, " +
        "front full body + 3/4 side full body + back full body, consistent design across all views, " +
        "neutral A-pose, seamless neutral gray studio background, $lightEn, even three-point lighting, " +
        "photorealistic live-action, cinematic quality, ultra detailed face and outfit, 8K, " +
        "orthographic eye-level view, ${ratio.en}"
    return "$zh\n$en"
}

// ── Tab UI ──（掛在 PromptTemplateSheet 的「三視圖」分頁，父層已可垂直捲動）
@Composable
fun TurnaroundTab(onPick: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var ratio by remember { mutableStateOf(TURN_RATIOS.first()) }
    var eth by remember { mutableStateOf(TURN_ETHS.first()) }
    var catIndex by remember { mutableIntStateOf(0) }
    var role by remember { mutableStateOf<TurnRole?>(null) }
    var withEn by remember { mutableStateOf(true) }
    var expandedDetail by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf("") }
    val details = remember {
        mutableStateMapOf<String, String>().apply { TURN_DETAILS.forEach { put(it.label, "隨機") } }
    }

    fun regen() {
        role?.let { result = buildTurnaroundPrompt(it, eth, details, ratio, withEn) }
    }

    TurnSectionLabel("① 畫幅")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TURN_RATIOS.forEach { r ->
            TurnChip(label = r.label, selected = ratio == r) { ratio = r; regen() }
        }
    }

    TurnSectionLabel("② 人種／族裔 · 留「隨機」自動配")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TURN_ETHS.forEach { e ->
            TurnChip(label = e.label, selected = eth == e) { eth = e; regen() }
        }
    }

    TurnSectionLabel("③ 角色原型 · 點一張立即生成")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TURN_CATS.forEachIndexed { i, cat ->
            TurnChip(label = "${cat.emoji} ${cat.name}", selected = catIndex == i) { catIndex = i }
        }
    }
    Box(modifier = Modifier.padding(top = 8.dp))
    TURN_CATS[catIndex].roles.chunked(2).forEach { pair ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pair.forEach { r ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (role == r) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        )
                        .clickable { role = r; regen() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(text = "${r.emoji} ${r.name}", fontSize = 13.5.sp, fontWeight = FontWeight.W700,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = r.zh,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (pair.size == 1) Box(modifier = Modifier.weight(1f))
        }
    }

    TurnSectionLabel("④ 自助細節 · 留「隨機」每次擲新的")
    TURN_DETAILS.forEach { field ->
        val value = details[field.label] ?: "隨機"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expandedDetail = if (expandedDetail == field.label) null else field.label }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = field.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(modifier = Modifier.weight(1f))
            Text(
                text = (if (value == "隨機") "🎲 " else "") + value + if (expandedDetail == field.label) " ▲" else " ▼",
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expandedDetail == field.label) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                field.options.forEach { opt ->
                    TurnChip(label = opt, selected = value == opt) {
                        details[field.label] = opt
                        expandedDetail = null
                        regen()
                    }
                }
            }
        }
    }

    // 完全隨機一鍵出
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                catIndex = TURN_CATS.indices.random()
                eth = TURN_ETHS.first() // 隨機
                TURN_DETAILS.forEach { details[it.label] = "隨機" }
                role = TURN_CATS[catIndex].roles.random()
                regen()
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "🎲 完全隨機一鍵出",
            fontSize = 14.sp,
            fontWeight = FontWeight.W700,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }

    // 附英文結構詞開關
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "附英文結構詞（出圖更穩）",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(1f))
        Switch(checked = withEn, onCheckedChange = { withEn = it; regen() })
    }

    // 輸出
    Text(
        text = "生成的三視圖提示詞",
        fontSize = 12.sp,
        fontWeight = FontWeight.W600,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = result.ifEmpty { "（點上方任一角色原型，或按「🎲 完全隨機一鍵出」）" },
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
            label = "換細節重生",
            icon = "refresh",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { regen() },
            enabled = role != null,
        )
        TextActionButton(
            label = "複製",
            icon = "content_copy",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = { if (result.isNotEmpty()) Clipboard.copy(ctx, result, toastMsg = "已複製") },
            enabled = result.isNotEmpty(),
        )
        TextActionButton(
            label = "使用",
            icon = "check",
            onClick = { if (result.isNotEmpty()) onPick(result) },
            enabled = result.isNotEmpty(),
        )
    }
}

@Composable
private fun TurnSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.W600,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun TurnChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    )
}
