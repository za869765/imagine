package com.za869765.imagine.ui.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.za869765.imagine.ui.component.SegmentedOption
import com.za869765.imagine.ui.component.SegmentedTab

const val ENGINE_GROK = "grok"
const val ENGINE_H3 = "h3"

/** v2.0.7 生圖頁:「生成引擎：Grok Imagine ｜ H3 Studio（Qwen 生圖）」,同一組 id(grok / h3)。 */
@Composable
fun ImageEngineSelector(engine: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) =
    VideoEngineSelector(engine = engine, onSelect = onSelect, modifier = modifier, h3Label = "H3 Studio（Qwen 生圖）")

/** 生影頁 / 設定頁共用:「生成引擎：Grok Imagine ｜ H3 Studio」。 */
@Composable
fun VideoEngineSelector(engine: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, h3Label: String = "H3 Studio") {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "生成引擎",
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedTab(
            options = listOf(
                SegmentedOption(ENGINE_GROK, "Grok Imagine"),
                SegmentedOption(ENGINE_H3, h3Label),
            ),
            activeId = engine,
            onSelected = onSelect,
            activeColor = if (engine == ENGINE_H3) Color(0xFF5B3FA8) else Color(0xFF14463F),
        )
    }
}

/**
 * 播放 H3 成品:遠端串流需帶 Authorization(伺服器支援 HTTP Range);本機 file:// 直接播。
 */
@UnstableApi
@Composable
fun H3VideoPlayer(url: String, headers: Map<String, String>, userAgent: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(url) {
        val builder = ExoPlayer.Builder(ctx)
        if (url.startsWith("http")) {
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(headers)
                .setAllowCrossProtocolRedirects(true)
            builder.setMediaSourceFactory(DefaultMediaSourceFactory(http))
        }
        builder.build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(url) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { c ->
            PlayerView(c).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
