package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

// v1.8.0 模型選擇器;v1.8.3 改「有 key 的供應商合併一份清單」(xAI 在前、OpenRouter 免費排前),
// 列版面改為名稱整行可讀(最多 2 行)+ 下方標記/價格,384dp 大字體不擠。

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
    val shape = RoundedCornerShape(6.dp)
    Text(
        text = badgeLabel(badge),
        fontSize = 10.sp,
        fontWeight = FontWeight.W700,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .let { if (badge == "paid") it.border(1.dp, MaterialTheme.colorScheme.outline, shape) else it }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// 供應商小標(xAI / OpenRouter),合併清單每列辨識用
@Composable
private fun ProviderTag(p: ApiProvider) {
    Text(
        text = p.shortLabel,
        fontSize = 10.sp,
        fontWeight = FontWeight.W600,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// 有 key 的供應商才列;兩家都沒 key 時全列(讓使用者知道能用什麼,生成鈕另外擋)
fun modelsFor(ctx: android.content.Context, mode: ModelMode, hasXai: Boolean, hasOpenRouter: Boolean): List<CatalogModel> {
    val xai = XaiCatalog.models(mode)
    val or = OpenRouterCatalog.sortedForPicker(OpenRouterCatalog.models(ctx, mode), mode)
    return when {
        hasXai && hasOpenRouter -> xai + or
        hasXai -> xai
        hasOpenRouter -> or
        else -> xai + or
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerRow(
    mode: ModelMode,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val scope = rememberCoroutineScope()
    val hasXai = prefs.isApiKeySet
    val hasOr = prefs.isOpenRouterKeySet
    var refreshKey by remember { mutableStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val models = remember(mode, hasXai, hasOr, refreshKey) { modelsFor(ctx, mode, hasXai, hasOr) }
    val fetchedAt = remember(refreshKey) { OpenRouterCatalog.load(ctx).fetchedAt }
    val selected = models.firstOrNull { it.id == selectedId } ?: CatalogModel(id = selectedId, name = selectedId)
    val selectedProvider = ApiProvider.ofModel(selectedId)
    var showSheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "模型 · ${selectedProvider.shortLabel}",
                fontSize = 11.sp,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = selected.displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.W600,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                BadgePill(selected.badge)
                Text(
                    text = selected.priceText(mode),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ImagineIcon(name = "expand_more", size = 22.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (mode) {
                                ModelMode.CHAT -> "選對話模型"
                                ModelMode.IMAGE -> "選生圖模型"
                                ModelMode.VIDEO -> "選生影模型"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.W700,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = buildString {
                                append(filtered.size).append(" 個")
                                if (hasXai || !hasOr) append(" · xAI 官方價")
                                if (hasOr || !hasXai) append(" · OpenRouter 快照 ").append(fetchedAt.ifBlank { "內建" })
                                if (!hasXai && !hasOr) append(" · 尚未設定 API Key")
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    if (hasOr) {
                        if (refreshing) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextActionButton(label = "更新", icon = "refresh", onClick = {
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
                    placeholder = { Text("搜尋名稱 / id / 免費", fontSize = 13.sp) },
                    leadingIcon = { ImagineIcon(name = "search", size = 18.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { m ->
                        val isSel = m.id == selectedId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable {
                                    onSelect(m.id)
                                    scope.launch {
                                        sheetState.hide()
                                        showSheet = false
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            // 第一行:名稱(整行,最多 2 行)+ 已選勾
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = m.displayName,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = if (isSel) FontWeight.W700 else FontWeight.W500,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSel) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ImagineIcon(name = "check", size = 18.dp, fill = 1, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            // 第二行:供應商 + 標記 + 價格
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                ProviderTag(ApiProvider.ofModel(m.id))
                                BadgePill(m.badge)
                                Text(
                                    text = m.priceText(mode),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // 第三行:id(+ctx)與標記說明,小字
                            Text(
                                text = m.id + (if (mode == ModelMode.CHAT && m.ctx > 0) " · ${m.ctx / 1000}K ctx" else ""),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            val hint = badgeHint(m.badge)
                            if (hint.isNotBlank()) {
                                Text(
                                    text = hint,
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
