package com.za869765.imagine.ui.hub

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.za869765.imagine.data.storage.HiddenSeed
import com.za869765.imagine.data.storage.MaterialLibrary
import com.za869765.imagine.data.storage.MaterialSeed
import com.za869765.imagine.data.storage.SeedReview
import com.za869765.imagine.data.storage.SeedUpdater
import com.za869765.imagine.ui.component.AppNotice
import com.za869765.imagine.ui.component.ChipVariant
import com.za869765.imagine.ui.component.FullscreenImageViewer
import com.za869765.imagine.ui.component.ImagineChip
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.TextActionButton
import com.za869765.imagine.ui.component.ViewerAction
import kotlinx.coroutines.launch

private const val ALL = "全部"
private const val ST_ALL = "全部"
private const val ST_UNDECIDED = "未決定"
private const val ST_KEPT = "保留"
private const val ST_DISCARDED = "丟棄"

/**
 * v1.8.0 素材總覽・去留審查 — 把 1000+ 張內建素材攤成一個可篩選的格子,逐張點一下決定去留:
 *   點一下循環:未決定 → ✓保留 → ✕丟棄 → 未決定(長按放大看)。
 *   丟棄 = HiddenSeed.hide(素材庫 / 課程圖庫同步消失,可再點回來復原);保留 = SeedReview 記錄(只為了
 *   之後能只看「未決定」把沒看過的挑完)。
 *   右上「從雲端更新素材」:拉 repo main 的最新 material_seed.json / tutorial_lessons.json(super-i 新課程
 *   由桌面端收錄後 push),不必重裝 APK。
 */
@Composable
fun MaterialReviewScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reloadKey by remember { mutableStateOf(0) }
    val seedAll = remember(reloadKey) { MaterialSeed.load(ctx) }
    var hidden by remember { mutableStateOf(HiddenSeed.all(ctx)) }
    var kept by remember { mutableStateOf(SeedReview.kept(ctx)) }
    var cat by rememberSaveable { mutableStateOf(ALL) }
    var status by rememberSaveable { mutableStateOf(ST_ALL) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var updating by remember { mutableStateOf(false) }
    var confirmBulkDiscard by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf(SeedUpdater.lastUpdatedAt(ctx)) }

    fun stateOf(url: String): String = when {
        url in hidden -> ST_DISCARDED
        url in kept -> ST_KEPT
        else -> ST_UNDECIDED
    }

    fun setState(urls: List<String>, target: String) {
        if (urls.isEmpty()) return
        when (target) {
            ST_KEPT -> { SeedReview.keep(ctx, urls); HiddenSeed.unhide(ctx, urls) }
            ST_DISCARDED -> { HiddenSeed.hide(ctx, urls); SeedReview.unkeep(ctx, urls) }
            else -> { HiddenSeed.unhide(ctx, urls); SeedReview.unkeep(ctx, urls) }
        }
        hidden = HiddenSeed.all(ctx)
        kept = SeedReview.kept(ctx)
    }

    fun cycle(url: String) {
        val next = when (stateOf(url)) {
            ST_UNDECIDED -> ST_KEPT
            ST_KEPT -> ST_DISCARDED
            else -> ST_UNDECIDED
        }
        setState(listOf(url), next)
    }

    val byCat = remember(seedAll, cat) { if (cat == ALL) seedAll else seedAll.filter { it.category == cat } }
    val filtered = remember(byCat, status, hidden, kept) {
        when (status) {
            ST_UNDECIDED -> byCat.filter { it.url !in hidden && it.url !in kept }
            ST_KEPT -> byCat.filter { it.url in kept && it.url !in hidden }
            ST_DISCARDED -> byCat.filter { it.url in hidden }
            else -> byCat
        }
    }
    val filteredUrls = remember(filtered) { filtered.map { it.url } }
    val nKept = remember(byCat, kept, hidden) { byCat.count { it.url in kept && it.url !in hidden } }
    val nHidden = remember(byCat, hidden) { byCat.count { it.url in hidden } }
    val nUndecided = byCat.size - nKept - nHidden

    fun runCloudUpdate() {
        if (updating) return
        updating = true
        scope.launch {
            val r = SeedUpdater.update(ctx)
            updating = false
            r.onSuccess {
                reloadKey++
                lastUpdated = SeedUpdater.lastUpdatedAt(ctx)
                AppNotice.show("素材已更新:素材 ${it.seedTotal}(新 ${it.seedAdded})・課程 ${it.lessonTotal}(新 ${it.lessonAdded})")
            }.onFailure {
                AppNotice.show("更新失敗:${it.message?.take(60) ?: "未知錯誤"}")
            }
        }
    }

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "素材總覽・去留",
                showBack = true,
                onBackClick = onBack,
                onSettingsClick = onSettingsClick,
            )
        },
        showBalanceBar = false,
        bottomNav = null,
        scroll = false,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 分類列
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf(ALL) + MaterialLibrary.CATEGORIES).forEach { c ->
                    val count = if (c == ALL) seedAll.size else seedAll.count { it.category == c }
                    FilterPill(label = "$c $count", selected = cat == c) { cat = c }
                }
            }
            // 狀態列
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPill(label = "$ST_ALL ${byCat.size}", selected = status == ST_ALL) { status = ST_ALL }
                FilterPill(label = "$ST_UNDECIDED $nUndecided", selected = status == ST_UNDECIDED) { status = ST_UNDECIDED }
                FilterPill(label = "✓ $ST_KEPT $nKept", selected = status == ST_KEPT, tint = Color(0xFF5BD47A)) { status = ST_KEPT }
                FilterPill(label = "✕ $ST_DISCARDED $nHidden", selected = status == ST_DISCARDED, tint = Color(0xFFFF7B7B)) { status = ST_DISCARDED }
            }
            // 動作列:雲端更新 / 批次
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (updating) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("更新中…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    TextActionButton(label = "從雲端更新素材", icon = "cloud_download", onClick = { runCloudUpdate() })
                }
                Text(
                    text = lastUpdated?.let { "上次 $it" } ?: "內建版",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (filtered.isNotEmpty() && status != ST_KEPT) {
                    ImagineChip(label = "全保留", icon = "thumb_up", variant = ChipVariant.Outlined, onClick = { setState(filteredUrls, ST_KEPT); AppNotice.show("已保留 ${filteredUrls.size} 張") })
                }
                if (filtered.isNotEmpty() && status != ST_DISCARDED) {
                    ImagineChip(label = "全丟棄", icon = "thumb_down", variant = ChipVariant.Outlined, onClick = { confirmBulkDiscard = true })
                }
            }
            Text(
                text = "點一下循環:未決定 → ✓保留 → ✕丟棄 → 未決定;長按放大看。丟棄=素材庫/課程圖庫同步隱藏,可點回復原。",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
            )

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (seedAll.isEmpty()) "沒有內建素材資料" else "此篩選沒有圖片",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = filtered, key = { it.url }) { entry ->
                        ReviewCell(
                            url = entry.url,
                            state = stateOf(entry.url),
                            onClick = { cycle(entry.url) },
                            onLongClick = { viewerIndex = filteredUrls.indexOf(entry.url).takeIf { it >= 0 } },
                        )
                    }
                }
            }
        }
    }

    if (confirmBulkDiscard) {
        AlertDialog(
            onDismissRequest = { confirmBulkDiscard = false },
            title = { Text("丟棄目前篩選的 ${filteredUrls.size} 張?") },
            text = { Text("會從素材庫與課程圖庫隱藏(可在「丟棄」篩選內點回復原)。") },
            confirmButton = {
                TextButton(onClick = {
                    setState(filteredUrls, ST_DISCARDED)
                    confirmBulkDiscard = false
                    AppNotice.show("已丟棄 ${filteredUrls.size} 張")
                }) { Text("丟棄") }
            },
            dismissButton = { TextButton(onClick = { confirmBulkDiscard = false }) { Text("取消") } },
        )
    }

    val vi = viewerIndex
    if (vi != null && vi in filteredUrls.indices) {
        FullscreenImageViewer(
            urls = filteredUrls,
            startIndex = vi,
            onDismiss = { viewerIndex = null },
            actions = listOf(
                ViewerAction("thumb_up", "保留") { url -> setState(listOf(url), ST_KEPT); AppNotice.show("已保留") },
                ViewerAction("thumb_down", "丟棄", destructive = true) { url -> setState(listOf(url), ST_DISCARDED); AppNotice.show("已丟棄") },
            ),
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, tint: Color? = null, onClick: () -> Unit) {
    val fg = tint ?: (if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.W700 else FontWeight.W500,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(100.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReviewCell(url: String, state: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                if (state == ST_UNDECIDED) 1.dp else 2.dp,
                when (state) {
                    ST_KEPT -> Color(0xFF5BD47A)
                    ST_DISCARDED -> Color(0xFFFF7B7B)
                    else -> Color.White.copy(alpha = 0.06f)
                },
                RoundedCornerShape(12.dp),
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (state == ST_DISCARDED) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        if (state != ST_UNDECIDED) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (state == ST_KEPT) Color(0xFF2E7D32) else Color(0xFFC62828)),
                contentAlignment = Alignment.Center,
            ) {
                ImagineIcon(name = if (state == ST_KEPT) "check" else "close", size = 15.dp, tint = Color.White)
            }
        }
    }
}
