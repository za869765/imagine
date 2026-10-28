package com.za869765.imagine.ui.pose

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.data.pose.PoseLibrary
import com.za869765.imagine.data.storage.MediaSaver
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.OutlinedActionButton
import com.za869765.imagine.ui.component.PrimaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v2.1.6 姿勢參考庫(anhao/ai-pose-gallery-zh 人偶線稿):體系分頁、分類篩選、搜尋、多視角,
 * 「當參考圖生圖」→ 存成素材並帶到 H3 生圖(照片改圖模式、提示詞預填姿勢名)。
 * 成人分類(雙人纏綿)預設隱藏,右上開關才顯示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoseLibraryScreen(
    onBack: () -> Unit,
    onUseAsReference: (uri: String, prompt: String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var poses by remember { mutableStateOf<List<PoseLibrary.Pose>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showAdult by rememberSaveable { mutableStateOf(false) }
    var system by rememberSaveable { mutableStateOf("全身單人・標準系") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var opened by remember { mutableStateOf<PoseLibrary.Pose?>(null) }

    fun reload(force: Boolean) {
        loading = true
        scope.launch {
            PoseLibrary.load(ctx, force).onSuccess { poses = it; error = null }.onFailure { error = it.message ?: "載入失敗" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload(false) }

    val systems = poses.map { it.system }.distinct().filter { showAdult || it != PoseLibrary.ADULT_SYSTEM }
        .sortedBy { listOf("全身單人・標準系", "上半身單人", "全身單人・性感系", PoseLibrary.ADULT_SYSTEM).indexOf(it) }
    val inSystem = poses.filter { it.system == system }
    val categories = inSystem.map { it.category }.distinct()
    val shown = inSystem.filter { (category == null || it.category == category) && (query.isBlank() || it.name.contains(query) || it.no.contains(query, true) || it.category.contains(query)) }

    opened?.let { p -> PoseSheet(pose = p, onDismiss = { opened = null }, onUse = { uri, prompt -> opened = null; onUseAsReference(uri, prompt) }) }

    ImagineScreen(
        appBar = { ImagineTopAppBar(title = "姿勢參考庫", showBack = true, onBackClick = onBack) },
        bottomNav = null,
        scroll = false,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 體系分頁(橫向捲動)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                systems.forEach { s ->
                    Chip(text = s.replace("全身單人・", "全身・") + "（" + poses.count { it.system == s } + "）", on = s == system) { system = s; category = null }
                }
            }
            // 搜尋＋成人開關
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (query.isEmpty()) Text("搜尋姿勢名稱，例：坐、跪、回頭", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth())
                }
                Text("成人", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = showAdult, onCheckedChange = { showAdult = it; if (!it && system == PoseLibrary.ADULT_SYSTEM) system = "全身單人・標準系" })
            }
            // 分類
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(text = "全部", on = category == null, small = true) { category = null }
                categories.forEach { c -> Chip(text = c, on = c == category, small = true) { category = c } }
            }
            when {
                loading && poses.isEmpty() -> Text("載入姿勢庫中…（第一次約 1～3 秒）", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error != null && poses.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error!!, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                    OutlinedActionButton(label = "重試", onClick = { reload(true) })
                }
                else -> LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.key }) { p ->
                        Column(modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).clickable { opened = p }) {
                            AsyncImage(model = p.cover.thumbUrl, contentDescription = p.name, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                            Text(p.name, fontSize = 11.sp, maxLines = 2, color = Color(0xFF222222), lineHeight = 14.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                        }
                    }
                    item(span = { GridItemSpan(3) }) {
                        Text("共 ${shown.size} 個姿勢。圖片來源：ギア（@giaaibusi）／中文版 anhao，僅供個人參考、不得商用。",
                            fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = if (small) 12.sp else 13.sp,
        fontWeight = if (on) FontWeight.W700 else FontWeight.W500,
        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 10.dp else 12.dp, vertical = if (small) 6.dp else 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoseSheet(pose: PoseLibrary.Pose, onDismiss: () -> Unit, onUse: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var shot by remember(pose) { mutableStateOf(pose.cover) }
    var busy by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun saveThen(useAfter: Boolean) {
        if (busy) return
        busy = true
        ImagineApp.appScope.launch {
            val bytes = PoseLibrary.downloadImage(shot.imageUrl)
            val label = "姿勢：${pose.name}（${shot.angle}）"
            val uri = bytes?.let { MediaSaver.saveImage(ctx, it, label, "image/png") }
            withContext(Dispatchers.Main) {
                busy = false
                if (uri == null) { Toast.makeText(ctx, "下載失敗，請檢查網路", Toast.LENGTH_SHORT).show(); return@withContext }
                if (useAfter) onUse(uri, "照參考圖的人偶姿勢：${pose.name}（${shot.angle}視角）。") else Toast.makeText(ctx, "已存到所有作品", Toast.LENGTH_SHORT).show()
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(pose.name, fontSize = 18.sp, fontWeight = FontWeight.W700, color = MaterialTheme.colorScheme.onSurface)
            Text("${pose.system}・${pose.category}・${pose.no}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(model = shot.imageUrl, contentDescription = pose.name, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(12.dp)).background(Color.White))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                pose.shots.forEach { s -> Chip(text = s.angle, on = s == shot, small = true) { shot = s } }
            }
            PrimaryButton(label = if (busy) "下載中…" else "當參考圖去生圖", loading = busy, onClick = { saveThen(true) }, modifier = Modifier.fillMaxWidth())
            OutlinedActionButton(label = "只存到所有作品", enabled = !busy, onClick = { saveThen(false) }, modifier = Modifier.fillMaxWidth())
        }
    }
}
