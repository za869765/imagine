package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

// 看圖器底部動作 — onClick 帶入「目前顯示中那張」的 url。
data class ViewerAction(val icon: String, val label: String, val onClick: (String) -> Unit)

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
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, urls.size - 1)) { urls.size }
        // 每頁的縮放倍率,用來決定 pager 是否可橫滑(縮放中=不可滑)。
        val scales = remember { mutableStateMapOf<Int, Float>() }
        val currentScale = scales[pagerState.currentPage] ?: 1f

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

            // 底部:當頁動作鈕
            if (actions.isNotEmpty()) {
                val url = urls[pagerState.currentPage]
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions.forEach { a ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.pointerInput(a, url) {
                                detectTapGestures(onTap = { a.onClick(url) })
                            },
                        ) {
                            ImagineIcon(name = a.icon, size = 24.dp, tint = Color.White)
                            Text(text = a.label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
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
