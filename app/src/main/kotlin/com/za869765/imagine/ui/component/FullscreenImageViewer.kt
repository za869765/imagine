package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil3.compose.AsyncImage

// 看圖器底部動作 — onClick 帶入「目前顯示中那張」的 url。destructive=true → 浮層內紅字(如移出)。
data class ViewerAction(
    val icon: String,
    val label: String,
    val destructive: Boolean = false,
    val onClick: (String) -> Unit,
)

// 共用全螢幕看圖器:雙指縮放 + 雙擊放大(1x↔2.5x) + 拖移 + 多張左右滑;底部動作鈕作用在當頁。
// 縮放中時關掉 pager 橫滑,讓拖移不會誤觸換頁。
@Composable
fun FullscreenImageViewer(
    urls: List<String>,
    startIndex: Int = 0,
    onDismiss: () -> Unit,
    actions: List<ViewerAction> = emptyList(),
) {
    if (urls.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        // decorFitsSystemWindows = false → 對話框 edge-to-edge,statusBarsPadding/
        // navigationBarsPadding 才會回正確 inset,把底部動作鈕推到手勢列上方(否則被擋住)。
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, urls.size - 1)) { urls.size }
        // 每頁的縮放倍率,用來決定 pager 是否可橫滑(縮放中=不可滑)。
        val scales = remember { mutableStateMapOf<Int, Float>() }
        val currentScale = scales[pagerState.currentPage] ?: 1f

        // 底部動作鈕離底距離 — 直接從 dialog 視窗 root insets 取真實導覽列高
        // (Compose 的 navigationBarsPadding 在 Dialog 內常回 0,三鍵導覽 48dp 會擋到鈕),
        // 再加 24dp 餘裕、保底 56dp,確保三鍵/手勢列都點得到。
        val view = LocalView.current
        val density = LocalDensity.current
        val actionsBottomPad = run {
            val px = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            (with(density) { px.toDp() } + 40.dp).coerceAtLeast(80.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= 1.01f,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomableImage(
                    url = urls[page],
                    onScaleChange = { s -> scales[page] = s },
                )
            }

            // 頂部:關閉 + 頁碼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ImagineIconButton(name = "close", tint = Color.White, onClick = onDismiss)
                if (urls.size > 1) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${urls.size}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W600,
                        )
                    }
                }
            }

            // 底部:主動作固定列(前 3 顆 + 更多)+ 次要動作收進「更多」浮層 — 修膠囊溢出按不到(痛點 #3/#4)。
            if (actions.isNotEmpty()) {
                val url = urls[pagerState.currentPage]
                val primary = if (actions.size > 4) actions.take(3) else actions
                val overflow = if (actions.size > 4) actions.drop(3) else emptyList<ViewerAction>()
                var showMore by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = actionsBottomPad),
                    horizontalAlignment = Alignment.End,
                ) {
                    // 「更多」浮層(在固定列上方,靠右對齊更多鈕)
                    if (showMore && overflow.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .width(210.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1A1B20))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                                .padding(6.dp),
                        ) {
                            overflow.forEachIndexed { i, a ->
                                val fg = if (a.destructive) Color(0xFFF0A0A0) else Color(0xFFECECF0)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(11.dp))
                                        .clickable { showMore = false; a.onClick(url) }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                                ) {
                                    ImagineIcon(name = a.icon, size = 20.dp, tint = fg)
                                    Text(text = a.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.W500)
                                }
                                if (i < overflow.lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .height(1.dp)
                                            .background(Color.White.copy(alpha = 0.07f)),
                                    )
                                }
                            }
                        }
                    }
                    // 固定主動作列
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF141418).copy(alpha = 0.92f))
                            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                            .padding(9.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        primary.forEach { a ->
                            ViewerActionSlot(
                                icon = a.icon,
                                label = a.label,
                                highlight = false,
                                modifier = Modifier.weight(1f),
                            ) { a.onClick(url) }
                        }
                        if (overflow.isNotEmpty()) {
                            ViewerActionSlot(
                                icon = "more_horiz",
                                label = "更多",
                                highlight = true,
                                modifier = Modifier.weight(1f),
                            ) { showMore = !showMore }
                        }
                    }
                }
            }

            // app 內浮層 host — Dialog 視窗蓋住 Activity 根層的那份,看圖器內的「存相簿」回饋靠這裡顯示
            AppNoticeHost()
        }
    }
}

// 固定動作列的單格:icon 疊 label;生圖藍/生影青/其餘白,配合主卡分區色。
@Composable
private fun ViewerActionSlot(
    icon: String,
    label: String,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = when (icon) {
        "image" -> Color(0xFF9DB0FF)
        "movie" -> Color(0xFF56E0D2)
        else -> Color.White
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ImagineIcon(name = icon, size = 22.dp, tint = tint)
        Text(
            text = label,
            color = Color(0xFFECECF0),
            fontSize = 11.sp,
            fontWeight = if (highlight) FontWeight.W700 else FontWeight.W600,
        )
    }
}

@Composable
private fun ZoomableImage(url: String, onScaleChange: (Float) -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
        onScaleChange(scale)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(state)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                            onScaleChange(scale)
                        },
                    )
                },
        )
    }
}
