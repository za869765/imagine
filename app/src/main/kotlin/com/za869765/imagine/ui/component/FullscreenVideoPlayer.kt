package com.za869765.imagine.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

// 共用全螢幕影片播放器 — 黑底鋪滿 + RESIZE_MODE_FIT 按真實比例(直/橫/方都不變形) + 控制列(時間軸/自動隱藏)。
// 用自己的 ExoPlayer 從頭播(playWhenReady=true);關閉即釋放。
// UI_REDESIGN_PLAN 6.6:右上加 靜音 / 循環 開關。
@Composable
fun FullscreenVideoPlayer(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val ctx = LocalContext.current
        var muted by remember { mutableStateOf(false) }
        var loop by remember { mutableStateOf(false) }
        val player = remember(url) {
            ExoPlayer.Builder(ctx).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(url) { onDispose { player.release() } }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ImagineIconButton(name = "close", tint = Color.White, onClick = onDismiss)
                Row {
                    ImagineIconButton(
                        name = if (muted) "volume_off" else "volume_up",
                        tint = if (muted) Color(0xFFFFB74D) else Color.White,
                        onClick = {
                            muted = !muted
                            player.volume = if (muted) 0f else 1f
                        },
                    )
                    ImagineIconButton(
                        name = "repeat",
                        fill = if (loop) 1 else 0,
                        tint = if (loop) Color(0xFF7FE9DD) else Color.White,
                        onClick = {
                            loop = !loop
                            player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        },
                    )
                }
            }
        }
    }
}
