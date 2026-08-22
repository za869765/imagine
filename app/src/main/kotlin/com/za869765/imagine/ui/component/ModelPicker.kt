package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.za869765.imagine.data.catalog.CatalogModel
import com.za869765.imagine.data.catalog.ModelMode
import com.za869765.imagine.data.catalog.OpenRouterCatalog
import com.za869765.imagine.data.catalog.XaiCatalog
import com.za869765.imagine.data.catalog.badgeHint
import com.za869765.imagine.data.catalog.badgeLabel
import com.za869765.imagine.data.catalog.priceText
import com.za869765.imagine.data.prefs.ApiProvider
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.coroutines.launch

// v1.8.0 模型選擇器:一列(目前模型 + 免費/價格標記),點開底部清單(可搜尋、免費排前、每筆標價)。
// OpenRouter 清單來自 OpenRouterCatalog(內建快照 / 使用者按「更新清單」重抓);xAI 為固定清單。

// 標記膠囊顏色:免費綠 / 限時免費橘 / 條件免費藍 / 浮動灰 / 付費灰框
@Composable
fun BadgePill(badge: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when (badge) {
        "free" -> Color(0xFF2E7D32).copy(alpha = 0.28f) to Color(0xFF8BE08F)
        "limited_free" -> Color(0xFFE65100).copy(alpha = 0.30f) to Color(0xFFFFB74D)
        "conditional_free" -> Color(0xFF1565C0).copy(alpha = 0.30f) to Color(0xFF7EB8F5)
        "variable" -> Color(0xFF616161).copy(alpha = 0.30f) to Color(0xFFCFCFCF)
        else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(7.dp)
    Text(
        text = badgeLabel(badge),
        fontSize = 10.sp,
        fontWeight = FontWeight.W700,
        color = fg,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .let { if (badge == "paid") it.border(1.dp, MaterialTheme.colorScheme.outline, shape) else it }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

fun modelsFor(ctx: android.content.Context, provider: ApiProvider, mode: ModelMode): List<CatalogModel> =
    if (provider == ApiProvider.XAI) XaiCatalog.models(mode)
    else OpenRouterCatalog.sortedForPicker(OpenRouterCatalog.models(ctx, mode), mode)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerRow(
    mode: ModelMode,
    provider: ApiProvider,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val models = remember(provider, mode, refreshKey) { modelsFor(ctx, provider, mode) }
    val fetchedAt = remember(provider, refreshKey) {
        if (provider == ApiProvider.OPENROUTER) OpenRouterCatalog.load(ctx).fetchedAt else ""
    }
    val selected = models.firstOrNull { it.id == selectedId } ?: CatalogModel(id = selectedId, name = selectedId)
    var showSheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(
                text = "模型 · ${provider.label}",
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            ) {
                Text(
                    text = selected.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                BadgePill(selected.badge)
                Spacer(modifier = Modifier.weight(1f))
                ImagineIcon(name = "expand_more", size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = selected.priceText(mode),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    if (showSheet) {
        val filtered = remember(models, query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) models
            else models.filter { it.id.lowercase().contains(q) || it.name.lowercase().contains(q) || badgeLabel(it.badge).contains(q) }
        }
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.92f).padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (mode) {
                                ModelMode.CHAT -> "選對話模型"
                                ModelMode.IMAGE -> "選生圖模型"
                                ModelMode.VIDEO -> "選生影模型"
                            } + " · ${provider.label}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W600,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (provider == ApiProvider.OPENROUTER)
                                "${filtered.size} 個 · 價格快照 ${fetchedAt.ifBlank { "內建" }} · 免費排最前"
                            else "${filtered.size} 個 · xAI 官方統一價",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (provider == ApiProvider.OPENROUTER) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp).width(20.dp).heightIn(max = 20.dp), strokeWidth = 2.dp)
                        } else {
                            TextActionButton(label = "更新清單", icon = "refresh", onClick = {
                                refreshing = true
                                scope.launch {
                                    val r = OpenRouterCatalog.refresh(ctx, prefs.openRouterKey)
                                    refreshing = false
                                    r.onSuccess {
                                        refreshKey++
                                        AppNotice.show("已更新:對話 ${it.chat.size}／生圖 ${it.image.size}／生影 ${it.video.size}")
                                    }.onFailure {
                                        AppNotice.show("更新失敗:${it.message?.take(60) ?: "未知錯誤"}")
                                    }
                                }
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("搜尋模型名稱 / id / 免費", fontSize = 13.sp) },
                    leadingIcon = { ImagineIcon(name = "search", size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // 圖例
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BadgePill("free"); BadgePill("limited_free"); BadgePill("conditional_free"); BadgePill("paid")
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { m ->
                        val isSel = m.id == selectedId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(m.id)
                                    scope.launch {
                                        sheetState.hide()
                                        showSheet = false
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = m.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSel) FontWeight.W700 else FontWeight.W500,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                BadgePill(m.badge)
                                Spacer(modifier = Modifier.weight(1f))
                                if (isSel) ImagineIcon(name = "check", size = 18.dp, fill = 1, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                text = m.id + (if (mode == ModelMode.CHAT && m.ctx > 0) " · ${m.ctx / 1000}K ctx" else ""),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = m.priceText(mode),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            )
                            val hint = badgeHint(m.badge)
                            if (hint.isNotBlank()) {
                                Text(
                                    text = hint,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
