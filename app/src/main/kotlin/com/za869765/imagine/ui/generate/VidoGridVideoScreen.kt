package com.za869765.imagine.ui.generate

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.data.h3.H3Images
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.vidogrid.VidoGrid
import com.za869765.imagine.data.vidogrid.VidoGridJobs
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineCard
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.ModeOption
import com.za869765.imagine.ui.component.ModePicker
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.PrimaryButton
import com.za869765.imagine.ui.component.SourceSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class VgModel(val id: String, val name: String, val desc: String, val res: List<String>, val secs: List<Int>, val maxImages: Int, val square: Boolean)

private val VG_MODELS = listOf(
    VgModel("grok-1.5-lite", "Grok 1.5 Lite（最便宜）", "0 張＝文生影；1 張＝當第一幀", listOf("480p", "720p", "1080p"), listOf(6, 10, 15), 1, true),
    VgModel("grok-1.5", "Grok 1.5", "0 張文生影；1 張當第一幀；2～5 張＝參考人物／物品（臉不鎖死）", listOf("480p", "720p", "1080p"), listOf(4, 5, 6, 7, 8, 9, 10, 15), 5, true),
    VgModel("veo-3.1-fast", "Veo 3.1 Fast", "Google Veo，快又便宜；首尾幀或素材圖", listOf("720p", "1080p"), listOf(4, 6, 8), 3, false),
    VgModel("veo-3.1", "Veo 3.1", "Google Veo 最高畫質", listOf("720p", "1080p"), listOf(4, 6, 8), 3, false),
)

// 方案頁標價(美元/支，充值送點期間實際更便宜);其他組合以送出後回傳的扣點為準
private val GROK15_USD = mapOf("480p6" to 0.038, "480p10" to 0.06, "480p15" to 0.09, "720p6" to 0.068, "720p10" to 0.113, "720p15" to 0.17, "1080p6" to 0.085, "1080p10" to 0.15, "1080p15" to 0.495)

/**
 * v3.1.3 VidoGrid 生影(第三方轉接站)。⚠️ 素材會送到 VidoGrid 伺服器;API 要付費帳號(免費試用會被 403 擋)。
 * 送出前一定跳確認;任務放 App 層(VidoGridJobs),切頁照樣查、做好自動存進作品。
 */
@Composable
fun VidoGridVideoScreen(
    onSettingsClick: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
    engineSelector: @Composable () -> Unit,
    initialImageUri: Uri? = null,
    initialPrompt: String? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { VidoGridJobs.load(ctx) }

    var key by remember { mutableStateOf(prefs.vidogridKey.orEmpty()) }
    var accountText by remember { mutableStateOf<String?>(null) }
    var modelId by rememberSaveable { mutableStateOf("grok-1.5-lite") }
    val m = VG_MODELS.first { it.id == modelId }
    var res by rememberSaveable { mutableStateOf("480p") }
    var aspect by rememberSaveable { mutableStateOf("9:16") }
    var secs by rememberSaveable { mutableStateOf(6) }
    var veoMode by rememberSaveable { mutableStateOf("frame") }
    var muted by rememberSaveable { mutableStateOf(false) }
    var prompt by rememberSaveable { mutableStateOf(initialPrompt.orEmpty()) }
    var images by rememberSaveable { mutableStateOf(listOfNotNull(initialImageUri?.toString())) }
    var confirming by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    // 換模型時把不支援的選項拉回合法值
    LaunchedEffect(modelId) {
        if (res !in m.res) res = m.res.first()
        if (secs !in m.secs) secs = m.secs.first { it >= 6 }
        if (!m.square && aspect == "1:1") aspect = "9:16"
    }
    val maxImg = if (m.id.startsWith("veo") && veoMode == "frame") 2 else m.maxImages
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u: Uri? ->
        if (u != null && images.size < maxImg) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            images = images + u.toString()
        }
    }
    val usd = if (m.id == "grok-1.5") GROK15_USD["$res$secs"] else null

    fun submit() {
        val spec = VidoGrid.Spec(m.id, prompt.trim(), res, aspect, secs, veoMode, muted)
        val uris = images.take(maxImg)
        val appCtx = ctx.applicationContext
        sending = true
        ImagineApp.appScope.launch(Dispatchers.Main) {
            val bytes = uris.mapIndexedNotNull { i, s -> H3Images.encode(appCtx, Uri.parse(s), i)?.base64?.let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) } }
            if (bytes.size != uris.size) { sending = false; Toast.makeText(appCtx, "有圖片讀不到，未送出（沒有花費）", Toast.LENGTH_LONG).show(); return@launch }
            when (val r = VidoGrid.submit(key, spec, bytes)) {
                is VidoGrid.R.Ok -> {
                    VidoGridJobs.add(appCtx, VidoGridJobs.Job(r.v.first, spec.prompt, "${m.name}・$res・${secs} 秒・$aspect" + (if (bytes.isNotEmpty()) "・圖 ${bytes.size} 張" else ""), System.currentTimeMillis(), r.v.second))
                    Toast.makeText(appCtx, "已送出" + (r.v.second?.let { "，預扣 $it 點" } ?: ""), Toast.LENGTH_SHORT).show()
                }
                is VidoGrid.R.Err -> Toast.makeText(appCtx, r.msg, Toast.LENGTH_LONG).show()
            }
            sending = false
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("送到 VidoGrid 生成？") },
            text = {
                Text(
                    "${m.name}・$res・${secs} 秒・$aspect・圖 ${images.size.coerceAtMost(maxImg)} 張\n\n" +
                        (usd?.let { "標價約 US$${"%.3f".format(it)}／支（實際扣點送出後顯示）\n" } ?: "實際扣幾點送出後顯示（失敗不扣）\n") +
                        "\n⚠️ 提示詞與圖片會送到 VidoGrid（第三方）伺服器。",
                    fontSize = 14.sp, lineHeight = 20.sp,
                )
            },
            confirmButton = { TextButton(onClick = { confirming = false; submit() }) { Text("確認送出") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } },
        )
    }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "生成影片", onSettingsClick = onSettingsClick) },
        bottomNav = { ImagineBottomNav(active = NavTab.MATERIAL, onTabSelected = onNavSelected) },
        bottomAction = {
            PrimaryButton(
                label = if (sending) "送出中…" else "確認後送出（VidoGrid）",
                icon = if (sending) null else "movie",
                loading = sending,
                enabled = !sending && key.isNotBlank() && prompt.isNotBlank(),
                onClick = { confirming = true },
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            engineSelector()
            ImagineCard(pad = 14) {
                Text("第三方轉接站：API 要付費帳號（免費試用會被擋），素材會經過 VidoGrid。數字人、正式宣傳片素材不要用這裡。",
                    fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                value = key, onValueChange = { key = it.trim(); prefs.vidogridKey = key.ifBlank { null } },
                label = { Text("VidoGrid API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedActionButton(label = "查帳戶（不扣點）", enabled = key.isNotBlank(), onClick = {
                    accountText = "查詢中…"
                    scope.launch { accountText = when (val r = VidoGrid.account(key)) { is VidoGrid.R.Ok -> r.v; is VidoGrid.R.Err -> r.msg } }
                })
                accountText?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
            }

            ModePicker(label = "模型", options = VG_MODELS.map { ModeOption(it.id, it.name, it.desc) }, selectedId = modelId, onSelect = { modelId = it })
            ModePicker(label = "解析度", options = m.res.map { ModeOption(it, it, "") }, selectedId = res, onSelect = { res = it })
            ModePicker(label = "秒數", options = m.secs.map { ModeOption(it.toString(), "$it 秒", "") }, selectedId = secs.toString(), onSelect = { secs = it.toInt() })
            ModePicker(label = "比例", options = listOf(ModeOption("9:16", "9:16 直式", ""), ModeOption("16:9", "16:9 橫式", "")) + if (m.square) listOf(ModeOption("1:1", "1:1 方形", "")) else emptyList(),
                selectedId = aspect, onSelect = { aspect = it })
            if (m.id.startsWith("veo")) ModePicker(label = "圖片用法",
                options = listOf(ModeOption("frame", "首尾幀", "1 張＝第一幀；2 張＝第一幀＋最後一幀"), ModeOption("ingredient", "素材圖", "1～3 張：人物、商品或風格放進新場景")),
                selectedId = veoMode, onSelect = { veoMode = it; if (images.size > (if (it == "frame") 2 else 3)) images = images.take(if (it == "frame") 2 else 3) })

            SourceSlot(
                title = "參考圖（選填，最多 $maxImg 張）",
                uris = images.take(maxImg).map { Uri.parse(it) },
                maxCount = maxImg,
                onPick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = { i -> images = images.filterIndexed { k, _ -> k != i } },
                note = if (m.id == "grok-1.5" && images.size >= 2) "多張時在描述裡用 @image1、@image2 指定誰是誰，效果比較好" else null,
            )
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("描述要怎麼動") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            if (m.id.startsWith("grok")) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { muted = !muted }) {
                Checkbox(checked = muted, onCheckedChange = { muted = it }); Text("無聲（不生成聲音）", fontSize = 13.sp)
            }

            if (VidoGridJobs.jobs.isNotEmpty()) {
                Text("最近 VidoGrid 任務", fontSize = 14.sp, fontWeight = FontWeight.W600)
                VidoGridJobs.jobs.take(10).forEach { j ->
                    ImagineCard(pad = 12) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(j.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text(j.prompt.take(60), fontSize = 13.sp)
                            Text(
                                when {
                                    j.savedUri != null -> "✅ 已存進作品"
                                    j.status == 2 -> "完成，存檔中…"
                                    j.status == 3 -> "❌ 失敗（不扣點）"
                                    j.status == -2 -> "⛔ 被內容審核擋下"
                                    j.status == 0 -> "排隊中…"
                                    else -> "生成中" + (j.pct?.let { " $it%" } ?: "…")
                                } + (j.credit?.let { "・$it 點" } ?: ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            j.error?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                            if (j.savedUri != null || j.status == 3 || j.status == -2) Text("移除這筆", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { VidoGridJobs.remove(ctx, j.uuid) }.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
